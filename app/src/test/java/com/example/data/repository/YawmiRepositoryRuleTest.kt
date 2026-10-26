package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertThrows
import org.junit.Test

class YawmiRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun addAndObserveHealthRecords_authenticatedOwner_succeeds(): Unit = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = YawmiRepository(firestore)

        val recordId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addHealthRecord(
                metricType = "steps",
                value = 9450.0,
                unit = "steps",
                localDate = "2026-10-26",
                sourceType = "health_connect",
                sourceName = "Health Connect (Google Fit)",
                deviceModel = "Xiaomi Smart Band 10"
            ).getOrThrow()
        }
        assertThat(recordId).isNotEmpty()

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeHealthRecords().first { list -> list.any { it.id == recordId } }
        }
        assertThat(emitted.map { it.id }).contains(recordId)
    }

    @Test
    fun addAndObserveFoodLog_authenticatedOwner_succeeds(): Unit = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = YawmiRepository(firestore)

        val logId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addFoodLog(
                mealSlot = "lunch",
                foodName = "كسكس باللحم والخضار",
                grams = 300.0,
                calories = 495.0,
                proteinG = 22.5,
                carbsG = 69.0,
                fatG = 13.5,
                fiberG = 8.4,
                localDate = "2026-10-26",
                isAiRecognized = true,
                healthTip = "Rich in protein and fiber"
            ).getOrThrow()
        }
        assertThat(logId).isNotEmpty()

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeFoodLogs().first { list -> list.any { it.id == logId } }
        }
        assertThat(emitted.map { it.id }).contains(logId)
    }

    @Test
    fun getHealthRecordById_crossUserAccess_failsWithPermissionDenied(): Unit = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = YawmiRepository(firestore)
        val recordId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.addHealthRecord(
                metricType = "heart_rate",
                value = 72.0,
                unit = "bpm",
                localDate = "2026-10-26",
                sourceType = "xiaomi_band_10_ble",
                sourceName = "Xiaomi Smart Band 10"
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = YawmiRepository(firestore)
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getHealthRecordById(aliceUid, recordId)
        }
        assertThat(result.isFailure).isTrue()
        val rootEx = result.exceptionOrNull()?.unwrapFirestoreException()
        assertThat(rootEx?.code).isEqualTo(FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }

    @Test
    fun observeHealthRecords_unauthenticatedUser_failsFast(): Unit = runBlocking {
        auth.signOut()
        val repository = YawmiRepository(firestore)

        val thrown = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                withTimeout(FLOW_TIMEOUT_MS) {
                    repository.observeHealthRecords().first()
                }
            }
        }
        assertThat(thrown).isNotNull()
    }

    private companion object {
        const val ALICE_EMAIL = "alice@yawmi.test"
        const val BOB_EMAIL = "bob@yawmi.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
