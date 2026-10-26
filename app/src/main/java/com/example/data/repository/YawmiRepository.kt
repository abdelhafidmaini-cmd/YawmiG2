package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.FoodLog
import com.example.data.model.HealthRecord
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutSession
import com.example.data.model.YawmiItem
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class YawmiRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeUserProfile(): Flow<UserProfile> = flow {
        val uid = requireUserId()
        val path = "users/$uid/profile/settings"
        emitAll(
            db.collection("users").document(uid).collection("profile").document("settings")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: UserProfile()
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
                    throw error
                }
        )
    }

    suspend fun saveUserProfile(profile: UserProfile): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("profile").document("settings")
        try {
            val existing = docRef.get().await()
            val payload = mutableMapOf<String, Any>(
                "gender" to profile.gender,
                "age" to profile.age,
                "heightCm" to profile.heightCm,
                "weightKg" to profile.weightKg,
                "activityLevel" to profile.activityLevel,
                "goalType" to profile.goalType,
                "targetWaterMl" to profile.targetWaterMl,
                "waterDrankMl" to profile.waterDrankMl,
                "dailyStepGoal" to profile.dailyStepGoal,
                "doroCashMinor" to profile.doroCashMinor,
                "doroWalletMinor" to profile.doroWalletMinor,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (existing.exists() && existing.getTimestamp("createdAt") != null) {
                payload["createdAt"] = existing.getTimestamp("createdAt")!!
            } else {
                payload["createdAt"] = FieldValue.serverTimestamp()
            }
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    fun observeHealthRecords(): Flow<List<HealthRecord>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/health_records"
        emitAll(
            db.collection("users").document(uid).collection("health_records")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(HealthRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.localDate }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun addHealthRecord(
        metricType: String,
        value: Double,
        unit: String,
        localDate: String,
        sourceType: String,
        sourceName: String,
        deviceModel: String? = null,
        dedupStatus: String = "unique_primary",
        notes: String? = null,
        customDocId: String? = null
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("health_records")
        val docRef = if (!customDocId.isNullOrBlank()) colRef.document(customDocId) else colRef.document()
        try {
            val payload = mutableMapOf<String, Any>(
                "metricType" to metricType,
                "value" to value,
                "unit" to unit,
                "localDate" to localDate,
                "sourceType" to sourceType,
                "sourceName" to sourceName,
                "dedupStatus" to dedupStatus,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!deviceModel.isNullOrBlank()) payload["deviceModel"] = deviceModel
            if (!notes.isNullOrBlank()) payload["notes"] = notes
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun getHealthRecordById(targetUserId: String, recordId: String): Result<HealthRecord?> = runCatching {
        val docRef = db.collection("users").document(targetUserId).collection("health_records").document(recordId)
        try {
            docRef.get().await().toObject(HealthRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun deleteHealthRecord(recordId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("health_records").document(recordId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    fun observeFoodLogs(): Flow<List<FoodLog>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/food_logs"
        emitAll(
            db.collection("users").document(uid).collection("food_logs")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(FoodLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun addFoodLog(
        mealSlot: String,
        foodName: String,
        grams: Double,
        calories: Double,
        proteinG: Double,
        carbsG: Double,
        fatG: Double,
        fiberG: Double,
        localDate: String,
        isAiRecognized: Boolean = false,
        healthTip: String? = null
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("food_logs")
        val docRef = colRef.document()
        try {
            val payload = mutableMapOf<String, Any>(
                "mealSlot" to mealSlot,
                "foodName" to foodName.take(150),
                "grams" to grams,
                "calories" to calories,
                "proteinG" to proteinG,
                "carbsG" to carbsG,
                "fatG" to fatG,
                "fiberG" to fiberG,
                "localDate" to localDate,
                "isAiRecognized" to isAiRecognized,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!healthTip.isNullOrBlank()) payload["healthTip"] = healthTip.take(500)
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteFoodLog(logId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("food_logs").document(logId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    fun observeWorkouts(): Flow<List<WorkoutSession>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/workouts"
        emitAll(
            db.collection("users").document(uid).collection("workouts")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(WorkoutSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun addWorkoutSession(
        sportId: String,
        sportName: String,
        durationMin: Long,
        intensity: String,
        burnedKcal: Long,
        localDate: String,
        notes: String? = null
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("workouts")
        val docRef = colRef.document()
        try {
            val payload = mutableMapOf<String, Any>(
                "sportId" to sportId,
                "sportName" to sportName.take(150),
                "durationMin" to durationMin,
                "intensity" to intensity,
                "burnedKcal" to burnedKcal,
                "localDate" to localDate,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!notes.isNullOrBlank()) payload["notes"] = notes.take(500)
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteWorkoutSession(workoutId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("workouts").document(workoutId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    fun observeYawmiItems(): Flow<List<YawmiItem>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/tasks"
        emitAll(
            db.collection("users").document(uid).collection("tasks")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(YawmiItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun addYawmiItem(
        itemType: String,
        title: String,
        subtitle: String? = null,
        priority: String = "medium",
        completed: Boolean = false,
        amountMinor: Long = 0L,
        localDate: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("tasks")
        val docRef = colRef.document()
        try {
            val payload = mutableMapOf<String, Any>(
                "itemType" to itemType,
                "title" to title.take(200),
                "priority" to priority,
                "completed" to completed,
                "amountMinor" to amountMinor,
                "localDate" to localDate,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!subtitle.isNullOrBlank()) payload["subtitle"] = subtitle.take(400)
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun toggleYawmiItemCompleted(item: YawmiItem): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(item.id.isNotBlank()) { "Item ID must not be blank" }
        requireNotNull(item.createdAt) { "Item createdAt must not be null" }
        val docRef = db.collection("users").document(uid).collection("tasks").document(item.id)
        try {
            val payload = mutableMapOf<String, Any>(
                "itemType" to item.itemType,
                "title" to item.title,
                "priority" to item.priority,
                "completed" to !item.completed,
                "amountMinor" to item.amountMinor,
                "localDate" to item.localDate,
                "createdAt" to item.createdAt,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!item.subtitle.isNullOrBlank()) payload["subtitle"] = item.subtitle
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteYawmiItem(itemId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("tasks").document(itemId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }
}
