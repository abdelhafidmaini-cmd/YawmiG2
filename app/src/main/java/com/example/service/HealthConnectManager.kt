package com.example.service

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class SyncedVitalEntry(
    val metricType: String,
    val value: Double,
    val unit: String,
    val localDate: String,
    val sourceType: String,
    val sourceName: String,
    val deviceModel: String? = null
)

data class HealthPlatformSyncState(
    val sdkStatus: Int = HealthConnectClient.SDK_UNAVAILABLE,
    val uiStateCode: String = "unlinked", // unlinked | checking | needs_permissions | reading_data | read_success | no_data | connection_failed | permission_revoked
    val grantedPermissionsCount: Int = 0,
    val googleFitDetected: Boolean = false,
    val miFitnessDetected: Boolean = false,
    val huaweiHealthDetected: Boolean = false,
    val detectedSources: List<String> = emptyList(),
    val lastSyncTimestamp: String? = null,
    val statusMessage: String = "Ready to sync with Health Connect & Google Fit",
    val isPhoneStepSensorActive: Boolean = false,
    val phoneSessionSteps: Int = 0
)

class HealthConnectManager(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private var initialStepCounterValue: Float? = null
    private var lastAccelMagnitude = 9.8f
    private var lastPeakTimeMs = 0L

    private val _syncState = MutableStateFlow(HealthPlatformSyncState())
    val syncState: StateFlow<HealthPlatformSyncState> = _syncState.asStateFlow()

    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class)
    )

    fun checkAvailability(): Int {
        val status = try {
            HealthConnectClient.getSdkStatus(context)
        } catch (e: Exception) {
            HealthConnectClient.SDK_UNAVAILABLE
        }
        val installedSources = detectInstalledHealthApps()
        _syncState.update {
            it.copy(
                sdkStatus = status,
                googleFitDetected = installedSources.contains("Google Fit"),
                miFitnessDetected = installedSources.contains("Mi Fitness (Xiaomi)"),
                huaweiHealthDetected = installedSources.contains("Huawei / HONOR Health"),
                detectedSources = installedSources,
                uiStateCode = when (status) {
                    HealthConnectClient.SDK_AVAILABLE -> if (it.grantedPermissionsCount > 0) "read_success" else "needs_permissions"
                    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "connection_failed"
                    else -> "unlinked"
                },
                statusMessage = when (status) {
                    HealthConnectClient.SDK_AVAILABLE -> "Health Connect is available on this device."
                    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Health Connect provider update required from Google Play."
                    else -> "Health Connect is not installed on this device/emulator."
                }
            )
        }
        return status
    }

    private fun detectInstalledHealthApps(): List<String> {
        val pm = context.packageManager
        val list = mutableListOf<String>()
        val packages = listOf(
            "com.google.android.apps.fitness" to "Google Fit",
            "com.google.android.apps.healthdata" to "Health Connect",
            "com.xiaomi.wearable" to "Mi Fitness (Xiaomi)",
            "com.huawei.health" to "Huawei / HONOR Health"
        )
        for ((pkg, label) in packages) {
            try {
                pm.getPackageInfo(pkg, 0)
                list.add(label)
            } catch (_: Exception) {
            }
        }
        return list
    }

    suspend fun hasGrantedPermissions(): Boolean {
        val status = checkAvailability()
        if (status != HealthConnectClient.SDK_AVAILABLE) return false
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            val count = granted.intersect(requiredPermissions).size
            _syncState.update {
                it.copy(
                    grantedPermissionsCount = count,
                    uiStateCode = if (count > 0) "read_success" else "needs_permissions"
                )
            }
            count > 0
        } catch (e: Exception) {
            false
        }
    }

    fun openHealthConnectSettingsOrStore() {
        try {
            val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val playIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(playIntent)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Reads and aggregates today's metrics from Health Connect (including Google Fit, Mi Fitness,
     * and wearable origins) without double-counting overlapping step intervals.
     */
    suspend fun readTodayMetricsFromHealthConnect(): List<SyncedVitalEntry> {
        val status = checkAvailability()
        if (status != HealthConnectClient.SDK_AVAILABLE) {
            _syncState.update {
                it.copy(
                    uiStateCode = "connection_failed",
                    statusMessage = "Health Connect SDK is not available on this device."
                )
            }
            return emptyList()
        }

        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        val grantedRequired = granted.intersect(requiredPermissions)
        if (grantedRequired.isEmpty()) {
            _syncState.update {
                it.copy(
                    grantedPermissionsCount = 0,
                    uiStateCode = "needs_permissions",
                    statusMessage = "Please grant Health Connect read permissions first."
                )
            }
            return emptyList()
        }

        _syncState.update {
            it.copy(
                grantedPermissionsCount = grantedRequired.size,
                uiStateCode = "reading_data",
                statusMessage = "Reading & deduplicating Health Connect & Google Fit metrics..."
            )
        }

        val results = mutableListOf<SyncedVitalEntry>()
        val originsFound = mutableSetOf<String>()
        val today = LocalDate.now()
        val todayStr = today.toString()
        val zoneId = ZoneId.systemDefault()
        val startOfDay: Instant = today.atStartOfDay(zoneId).toInstant()
        val nowInstant: Instant = Instant.now()
        val timeRange = TimeRangeFilter.between(startOfDay, nowInstant)

        try {
            // 1. Aggregated Steps, Distance, and Calories (prevents double counting across Google Fit + watch)
            val aggregateResponse = client.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        StepsRecord.COUNT_TOTAL,
                        DistanceRecord.DISTANCE_TOTAL,
                        ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                        TotalCaloriesBurnedRecord.ENERGY_TOTAL
                    ),
                    timeRangeFilter = timeRange
                )
            )
            aggregateResponse.dataOrigins.forEach { origin ->
                originsFound.add(mapPackageToSourceLabel(origin.packageName))
            }

            val primaryOriginLabel = originsFound.firstOrNull() ?: "Health Connect"
            val primarySourceType = if (originsFound.any { it.contains("Google Fit") }) "google_fit_hc" else "health_connect"

            val totalSteps = aggregateResponse[StepsRecord.COUNT_TOTAL]
            if (totalSteps != null && totalSteps > 0) {
                results.add(
                    SyncedVitalEntry(
                        metricType = "steps",
                        value = totalSteps.toDouble(),
                        unit = "steps",
                        localDate = todayStr,
                        sourceType = primarySourceType,
                        sourceName = primaryOriginLabel
                    )
                )
            }

            val totalDistanceMeters = aggregateResponse[DistanceRecord.DISTANCE_TOTAL]?.inMeters
            if (totalDistanceMeters != null && totalDistanceMeters > 0) {
                val km = ((totalDistanceMeters / 1000.0) * 100.0).roundToInt() / 100.0
                results.add(
                    SyncedVitalEntry(
                        metricType = "distance",
                        value = km,
                        unit = "km",
                        localDate = todayStr,
                        sourceType = primarySourceType,
                        sourceName = primaryOriginLabel
                    )
                )
            }

            val activeKcal = aggregateResponse[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
                ?: aggregateResponse[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
            if (activeKcal != null && activeKcal > 0) {
                results.add(
                    SyncedVitalEntry(
                        metricType = "calories",
                        value = activeKcal.roundToInt().toDouble(),
                        unit = "kcal",
                        localDate = todayStr,
                        sourceType = primarySourceType,
                        sourceName = primaryOriginLabel
                    )
                )
            }

            // 2. Heart Rate Records
            if (granted.contains(HealthPermission.getReadPermission(HeartRateRecord::class))) {
                val hrResponse = client.readRecords(
                    ReadRecordsRequest(
                        recordType = HeartRateRecord::class,
                        timeRangeFilter = timeRange
                    )
                )
                val latestSample = hrResponse.records.flatMap { it.samples }.maxByOrNull { it.time }
                val latestRecord = hrResponse.records.lastOrNull()
                if (latestSample != null) {
                    val pkg = latestRecord?.metadata?.dataOrigin?.packageName.orEmpty()
                    val label = mapPackageToSourceLabel(pkg)
                    originsFound.add(label)
                    results.add(
                        SyncedVitalEntry(
                            metricType = "heart_rate",
                            value = latestSample.beatsPerMinute.toDouble(),
                            unit = "bpm",
                            localDate = todayStr,
                            sourceType = if (pkg.contains("fitness")) "google_fit_hc" else "health_connect",
                            sourceName = label,
                            deviceModel = latestRecord?.metadata?.device?.model
                        )
                    )
                }
            }

            // 3. Sleep Sessions (last 24h)
            if (granted.contains(HealthPermission.getReadPermission(SleepSessionRecord::class))) {
                val sleepRange = TimeRangeFilter.between(nowInstant.minusSeconds(86_400L), nowInstant)
                val sleepResponse = client.readRecords(
                    ReadRecordsRequest(
                        recordType = SleepSessionRecord::class,
                        timeRangeFilter = sleepRange
                    )
                )
                val totalSleepMin = sleepResponse.records.sumOf { rec ->
                    java.time.Duration.between(rec.startTime, rec.endTime).toMinutes().coerceAtLeast(0L)
                }
                if (totalSleepMin > 0) {
                    val pkg = sleepResponse.records.lastOrNull()?.metadata?.dataOrigin?.packageName.orEmpty()
                    val label = mapPackageToSourceLabel(pkg)
                    originsFound.add(label)
                    results.add(
                        SyncedVitalEntry(
                            metricType = "sleep",
                            value = totalSleepMin.toDouble(),
                            unit = "min",
                            localDate = todayStr,
                            sourceType = "health_connect",
                            sourceName = label
                        )
                    )
                }
            }

            // 4. Weight Records (last 30 days)
            if (granted.contains(HealthPermission.getReadPermission(WeightRecord::class))) {
                val weightRange = TimeRangeFilter.between(nowInstant.minusSeconds(30L * 86_400L), nowInstant)
                val weightResponse = client.readRecords(
                    ReadRecordsRequest(
                        recordType = WeightRecord::class,
                        timeRangeFilter = weightRange
                    )
                )
                val latestWeight = weightResponse.records.maxByOrNull { it.time }
                if (latestWeight != null) {
                    val kg = ((latestWeight.weight.inKilograms) * 10.0).roundToInt() / 10.0
                    val pkg = latestWeight.metadata.dataOrigin.packageName
                    val label = mapPackageToSourceLabel(pkg)
                    originsFound.add(label)
                    results.add(
                        SyncedVitalEntry(
                            metricType = "weight",
                            value = kg,
                            unit = "kg",
                            localDate = todayStr,
                            sourceType = "health_connect",
                            sourceName = label
                        )
                    )
                }
            }

            // 5. Oxygen Saturation (SpO2)
            if (granted.contains(HealthPermission.getReadPermission(OxygenSaturationRecord::class))) {
                val spo2Response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = OxygenSaturationRecord::class,
                        timeRangeFilter = timeRange
                    )
                )
                val latestSpo2 = spo2Response.records.maxByOrNull { it.time }
                if (latestSpo2 != null) {
                    val pct = latestSpo2.percentage.value
                    val pkg = latestSpo2.metadata.dataOrigin.packageName
                    val label = mapPackageToSourceLabel(pkg)
                    originsFound.add(label)
                    results.add(
                        SyncedVitalEntry(
                            metricType = "spo2",
                            value = pct,
                            unit = "%",
                            localDate = todayStr,
                            sourceType = "health_connect",
                            sourceName = label
                        )
                    )
                }
            }

            val allSources = (detectInstalledHealthApps() + originsFound).distinct()
            _syncState.update {
                it.copy(
                    uiStateCode = if (results.isNotEmpty()) "read_success" else "no_data",
                    detectedSources = allSources,
                    googleFitDetected = allSources.any { s -> s.contains("Google Fit") },
                    lastSyncTimestamp = LocalDate.now().toString(),
                    statusMessage = if (results.isNotEmpty()) {
                        "Synced ${results.size} metric(s) from Health Connect / Google Fit."
                    } else {
                        "Connected to Health Connect (no records written for today yet)."
                    }
                )
            }
        } catch (e: Exception) {
            _syncState.update {
                it.copy(
                    uiStateCode = "connection_failed",
                    statusMessage = "Health Connect read error: ${e.localizedMessage}"
                )
            }
        }
        return results
    }

    private fun mapPackageToSourceLabel(pkg: String): String {
        return when {
            pkg.contains("com.google.android.apps.fitness") -> "Google Fit (via Health Connect)"
            pkg.contains("com.xiaomi.wearable") || pkg.contains("com.mi.health") -> "Mi Fitness (Xiaomi Band)"
            pkg.contains("com.huawei.health") || pkg.contains("com.hihonor.health") -> "Huawei / HONOR Health"
            pkg.isNotBlank() -> "Health Connect ($pkg)"
            else -> "Android Health Connect"
        }
    }

    // --- On-Device Hardware Step Counter Sensor ---
    fun togglePhoneStepSensor(enable: Boolean) {
        if (!enable) {
            sensorManager?.unregisterListener(this)
            _syncState.update {
                it.copy(
                    isPhoneStepSensorActive = false,
                    statusMessage = "Phone step sensor stopped."
                )
            }
            return
        }
        val stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val registered = when {
            stepSensor != null -> sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI)
            accelSensor != null -> sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_GAME)
            else -> false
        }
        _syncState.update {
            it.copy(
                isPhoneStepSensorActive = registered,
                statusMessage = if (registered) {
                    "On-device motion sensor active. Walk with your phone to capture steps."
                } else {
                    "Hardware step sensor not available on this device."
                }
            )
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val totalSinceBoot = event.values.firstOrNull() ?: return
            if (initialStepCounterValue == null) {
                initialStepCounterValue = totalSinceBoot
            }
            val sessionSteps = (totalSinceBoot - (initialStepCounterValue ?: totalSinceBoot)).toInt().coerceAtLeast(0)
            _syncState.update { it.copy(phoneSessionSteps = sessionSteps) }
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values.getOrNull(0) ?: 0f
            val y = event.values.getOrNull(1) ?: 0f
            val z = event.values.getOrNull(2) ?: 0f
            val mag = sqrt(x * x + y * y + z * z)
            val now = System.currentTimeMillis()
            if (mag > 12.2f && lastAccelMagnitude <= 12.2f && now - lastPeakTimeMs > 340L) {
                lastPeakTimeMs = now
                _syncState.update { it.copy(phoneSessionSteps = it.phoneSessionSteps + 1) }
            }
            lastAccelMagnitude = mag
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
