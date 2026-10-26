package com.example.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.FoodLog
import com.example.data.model.HealthRecord
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutSession
import com.example.data.model.YawmiItem
import com.example.data.model.calculateBodyMetrics
import com.example.data.repository.YawmiRepository
import com.example.service.BluetoothHealthManager
import com.example.service.DiscoveredBleDevice
import com.example.service.HealthConnectManager
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.roundToInt

private const val TAG = "YawmiViewModel"

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class AiFoodEstimation(
    val dishName: String,
    val estimatedGrams: Double,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double,
    val healthTip: String
)

data class AiHealthInsightsResult(
    val dailyScore: Int,
    val summary: String,
    val nutritionAdvice: String,
    val workoutRecommendation: String,
    val recoveryTip: String
)

sealed interface AiTaskState<out T> {
    data object Idle : AiTaskState<Nothing>
    data object Loading : AiTaskState<Nothing>
    data class Success<T>(val data: T) : AiTaskState<T>
    data class Error(val message: String) : AiTaskState<Nothing>
}

class YawmiViewModel(
    private val repository: YawmiRepository,
    val bluetoothManager: BluetoothHealthManager,
    val healthConnectManager: HealthConnectManager
) : ViewModel() {

    val profileState: StateFlow<UiState<UserProfile>> = repository.observeUserProfile()
        .map<UserProfile, UiState<UserProfile>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing profile", e)
            emit(UiState.Error(e.message ?: "Failed to load profile"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val healthRecordsState: StateFlow<UiState<List<HealthRecord>>> = repository.observeHealthRecords()
        .map<List<HealthRecord>, UiState<List<HealthRecord>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing health records", e)
            emit(UiState.Error(e.message ?: "Failed to load health records"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val foodLogsState: StateFlow<UiState<List<FoodLog>>> = repository.observeFoodLogs()
        .map<List<FoodLog>, UiState<List<FoodLog>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing food logs", e)
            emit(UiState.Error(e.message ?: "Failed to load food logs"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val workoutsState: StateFlow<UiState<List<WorkoutSession>>> = repository.observeWorkouts()
        .map<List<WorkoutSession>, UiState<List<WorkoutSession>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing workouts", e)
            emit(UiState.Error(e.message ?: "Failed to load workouts"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val yawmiItemsState: StateFlow<UiState<List<YawmiItem>>> = repository.observeYawmiItems()
        .map<List<YawmiItem>, UiState<List<YawmiItem>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing tasks/items", e)
            emit(UiState.Error(e.message ?: "Failed to load items"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _aiFoodState = MutableStateFlow<AiTaskState<AiFoodEstimation>>(AiTaskState.Idle)
    val aiFoodState: StateFlow<AiTaskState<AiFoodEstimation>> = _aiFoodState.asStateFlow()

    private val _aiInsightsState = MutableStateFlow<AiTaskState<AiHealthInsightsResult>>(AiTaskState.Idle)
    val aiInsightsState: StateFlow<AiTaskState<AiHealthInsightsResult>> = _aiInsightsState.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    // Live Workout Timer State
    private val _workoutTimerSeconds = MutableStateFlow(0)
    val workoutTimerSeconds: StateFlow<Int> = _workoutTimerSeconds.asStateFlow()
    private val _isWorkoutTimerRunning = MutableStateFlow(false)
    val isWorkoutTimerRunning: StateFlow<Boolean> = _isWorkoutTimerRunning.asStateFlow()
    private var timerJob: Job? = null

    private val foodEstimationSchema = Schema.obj(
        properties = mapOf(
            "dishName" to Schema.string(description = "Recognized name of the dish or food item"),
            "estimatedGrams" to Schema.double(description = "Estimated portion weight in grams"),
            "calories" to Schema.double(description = "Estimated total kilocalories (kcal) for this portion"),
            "proteinG" to Schema.double(description = "Estimated protein in grams"),
            "carbsG" to Schema.double(description = "Estimated carbohydrates in grams"),
            "fatG" to Schema.double(description = "Estimated fat in grams"),
            "fiberG" to Schema.double(description = "Estimated dietary fiber in grams"),
            "healthTip" to Schema.string(description = "Actionable nutritional tip for this dish")
        )
    )

    private val healthInsightsSchema = Schema.obj(
        properties = mapOf(
            "dailyScore" to Schema.integer(description = "Overall daily health and fitness score from 1 to 100"),
            "summary" to Schema.string(description = "Concise personalized summary of current health and goal progress"),
            "nutritionAdvice" to Schema.string(description = "Specific macronutrient, calorie, and hydration recommendation"),
            "workoutRecommendation" to Schema.string(description = "Recommended sport, duration, and intensity based on user history"),
            "recoveryTip" to Schema.string(description = "Sleep, heart rate, and recovery guidance")
        )
    )

    private val foodAiModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = foodEstimationSchema
            }
        )
    }

    private val insightsAiModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = healthInsightsSchema
            }
        )
    }

    init {
        healthConnectManager.checkAvailability()
        bluetoothManager.setVitalCallback { metricType, value, unit, deviceName ->
            viewModelScope.launch {
                val today = LocalDate.now().toString()
                repository.addHealthRecord(
                    metricType = metricType,
                    value = value,
                    unit = unit,
                    localDate = today,
                    sourceType = "ble_wearable",
                    sourceName = "$deviceName (BLE)",
                    deviceModel = deviceName,
                    customDocId = "ble_${metricType}_$today"
                )
            }
        }
    }

    fun showMessage(msg: String?) {
        _bannerMessage.value = msg
    }

    // --- Bluetooth & Health Connect Actions ---
    fun startBleScan() = bluetoothManager.startScan()
    fun stopBleScan() = bluetoothManager.stopScan()
    fun connectBleDevice(device: DiscoveredBleDevice) = bluetoothManager.connectToDevice(device)
    fun disconnectBleDevice() = bluetoothManager.disconnect()

    fun syncWithHealthConnectAndGoogleFit() {
        viewModelScope.launch {
            val entries = healthConnectManager.readTodayMetricsFromHealthConnect()
            val currentRecords = (healthRecordsState.value as? UiState.Success)?.data.orEmpty()

            for (entry in entries) {
                val docId = "hc_${entry.metricType}_${entry.localDate}"
                repository.addHealthRecord(
                    metricType = entry.metricType,
                    value = entry.value,
                    unit = entry.unit,
                    localDate = entry.localDate,
                    sourceType = entry.sourceType,
                    sourceName = entry.sourceName,
                    deviceModel = entry.deviceModel,
                    dedupStatus = "unique_primary",
                    customDocId = docId
                )
                // If phone sensor steps exist for the same day, mark them suppressed_duplicate to avoid double-counting
                if (entry.metricType == "steps") {
                    currentRecords.filter {
                        it.metricType == "steps" &&
                            it.localDate == entry.localDate &&
                            it.sourceType == "phone_sensor" &&
                            it.dedupStatus == "unique_primary"
                    }.forEach { phoneRec ->
                        repository.addHealthRecord(
                            metricType = phoneRec.metricType,
                            value = phoneRec.value,
                            unit = phoneRec.unit,
                            localDate = phoneRec.localDate,
                            sourceType = phoneRec.sourceType,
                            sourceName = phoneRec.sourceName,
                            deviceModel = phoneRec.deviceModel,
                            dedupStatus = "suppressed_duplicate",
                            customDocId = phoneRec.id
                        )
                    }
                }
            }
            if (entries.isNotEmpty()) {
                _bannerMessage.value = "Synced ${entries.size} metric(s) from Health Connect / Google Fit ✓"
            }
        }
    }

    fun syncPhoneSensorStepsToCloud() {
        viewModelScope.launch {
            val steps = healthConnectManager.syncState.value.phoneSessionSteps
            if (steps <= 0) {
                _bannerMessage.value = "Walk with your phone while the sensor is active to record steps first."
                return@launch
            }
            val today = LocalDate.now().toString()
            val currentRecords = (healthRecordsState.value as? UiState.Success)?.data.orEmpty()
            val hasHigherPrioritySteps = currentRecords.any {
                it.metricType == "steps" &&
                    it.localDate == today &&
                    it.sourceType != "phone_sensor" &&
                    it.dedupStatus == "unique_primary"
            }
            val dedup = if (hasHigherPrioritySteps) "suppressed_duplicate" else "unique_primary"
            repository.addHealthRecord(
                metricType = "steps",
                value = steps.toDouble(),
                unit = "steps",
                localDate = today,
                sourceType = "phone_sensor",
                sourceName = "Phone Motion Sensor",
                deviceModel = "On-Device Accelerometer/Step Counter",
                dedupStatus = dedup,
                customDocId = "phone_steps_$today"
            )
            _bannerMessage.value = if (dedup == "unique_primary") {
                "Saved $steps steps from phone sensor ✓"
            } else {
                "Saved $steps phone steps (marked duplicate since Wearable/Health Connect is primary)"
            }
        }
    }

    fun logManualVital(metricType: String, value: Double, unit: String, sourceName: String, notes: String? = null) {
        viewModelScope.launch {
            val today = LocalDate.now().toString()
            repository.addHealthRecord(
                metricType = metricType,
                value = value,
                unit = unit,
                localDate = today,
                sourceType = "manual_verified",
                sourceName = sourceName,
                notes = notes
            ).onSuccess {
                _bannerMessage.value = "Saved $metricType ($value $unit) ✓"
            }.onFailure { e ->
                _bannerMessage.value = e.localizedMessage ?: "Failed to save vital"
            }
        }
    }

    fun deleteHealthRecord(id: String) {
        viewModelScope.launch {
            repository.deleteHealthRecord(id)
        }
    }

    // --- Profile, Hydration & DORO Balances ---
    fun updateWaterIntake(deltaMl: Long) {
        val currentProfile = (profileState.value as? UiState.Success)?.data ?: UserProfile()
        val nextWater = (currentProfile.waterDrankMl + deltaMl).coerceIn(0L, 12000L)
        viewModelScope.launch {
            repository.saveUserProfile(currentProfile.copy(waterDrankMl = nextWater))
        }
    }

    fun saveProfile(updated: UserProfile) {
        viewModelScope.launch {
            repository.saveUserProfile(updated).onSuccess {
                _bannerMessage.value = "Body profile & daily goals updated ✓"
            }
        }
    }

    // --- AI Food Photo Recognition ---
    fun analyzeFoodPhotoWithAi(bitmap: Bitmap, isArabic: Boolean) {
        viewModelScope.launch {
            _aiFoodState.value = AiTaskState.Loading
            try {
                val scaledBitmap = scaleBitmapUnderMaxDim(bitmap, 1024)
                val langInstruction = if (isArabic) "Respond with dishName and healthTip in Arabic." else "Respond in English."
                val response = withTimeout(60_000L) {
                    foodAiModel.generateContent(
                        content {
                            image(scaledBitmap)
                            text(
                                "Identify the food or dish in this photo (including Algerian/North African or international dishes), " +
                                    "estimate the visible portion weight in grams, and calculate the total calories (kcal) and macronutrients " +
                                    "(proteinG, carbsG, fatG, fiberG in grams) for that portion. $langInstruction"
                            )
                        }
                    )
                }
                val jsonString = response.text ?: throw IllegalStateException("Model returned empty response.")
                val json = JSONObject(jsonString)
                val estimation = AiFoodEstimation(
                    dishName = json.optString("dishName", if (isArabic) "وجبة غذائية" else "Recognized Meal"),
                    estimatedGrams = json.optDouble("estimatedGrams", 250.0).coerceIn(10.0, 3000.0),
                    calories = json.optDouble("calories", 350.0).coerceIn(5.0, 5000.0),
                    proteinG = ((json.optDouble("proteinG", 15.0).coerceAtLeast(0.0)) * 10.0).roundToInt() / 10.0,
                    carbsG = ((json.optDouble("carbsG", 40.0).coerceAtLeast(0.0)) * 10.0).roundToInt() / 10.0,
                    fatG = ((json.optDouble("fatG", 12.0).coerceAtLeast(0.0)) * 10.0).roundToInt() / 10.0,
                    fiberG = ((json.optDouble("fiberG", 4.0).coerceAtLeast(0.0)) * 10.0).roundToInt() / 10.0,
                    healthTip = json.optString("healthTip", "")
                )
                _aiFoodState.value = AiTaskState.Success(estimation)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI food photo recognition timed out", e)
                _aiFoodState.value = AiTaskState.Error("Food photo analysis timed out after 60s. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI food photo recognition failed", e)
                _aiFoodState.value = AiTaskState.Error(e.localizedMessage ?: "AI photo analysis failed.")
            }
        }
    }

    fun resetAiFoodState() {
        _aiFoodState.value = AiTaskState.Idle
    }

    fun logFoodMeal(
        mealSlot: String,
        foodName: String,
        grams: Double,
        calories: Double,
        proteinG: Double,
        carbsG: Double,
        fatG: Double,
        fiberG: Double,
        isAiRecognized: Boolean,
        healthTip: String?
    ) {
        viewModelScope.launch {
            val today = LocalDate.now().toString()
            repository.addFoodLog(
                mealSlot = mealSlot,
                foodName = foodName,
                grams = grams,
                calories = calories,
                proteinG = proteinG,
                carbsG = carbsG,
                fatG = fatG,
                fiberG = fiberG,
                localDate = today,
                isAiRecognized = isAiRecognized,
                healthTip = healthTip
            ).onSuccess {
                _bannerMessage.value = "Logged $foodName (${calories.roundToInt()} kcal) ✓"
                _aiFoodState.value = AiTaskState.Idle
            }.onFailure { e ->
                _bannerMessage.value = e.localizedMessage ?: "Failed to log meal"
            }
        }
    }

    fun deleteFoodLog(logId: String) {
        viewModelScope.launch {
            repository.deleteFoodLog(logId)
        }
    }

    // --- AI Personalized Health & Fitness Insights ---
    fun generatePersonalizedHealthInsights(isArabic: Boolean) {
        viewModelScope.launch {
            _aiInsightsState.value = AiTaskState.Loading
            try {
                val profile = (profileState.value as? UiState.Success)?.data ?: UserProfile()
                val bm = calculateBodyMetrics(profile, isArabic)
                val today = LocalDate.now().toString()
                val records = (healthRecordsState.value as? UiState.Success)?.data.orEmpty()
                val meals = (foodLogsState.value as? UiState.Success)?.data.orEmpty().filter { it.localDate == today }
                val workouts = (workoutsState.value as? UiState.Success)?.data.orEmpty().filter { it.localDate == today }

                val stepsToday = records.filter { it.metricType == "steps" && it.localDate == today && it.dedupStatus == "unique_primary" }
                    .sumOf { it.value }.roundToInt()
                val latestHr = records.firstOrNull { it.metricType == "heart_rate" }?.value?.roundToInt()
                val sleepMin = records.filter { it.metricType == "sleep" && it.localDate == today }
                    .sumOf { it.value }.roundToInt()

                val eatenKcal = meals.sumOf { it.calories }.roundToInt()
                val proteinG = meals.sumOf { it.proteinG }.roundToInt()
                val carbsG = meals.sumOf { it.carbsG }.roundToInt()
                val fatG = meals.sumOf { it.fatG }.roundToInt()
                val workoutBurn = workouts.sumOf { it.burnedKcal }

                val langPrompt = if (isArabic) "Write all fields in clear, motivating Arabic." else "Write all fields in clear, motivating English."
                val prompt = """
                    Analyze this user's real health and fitness history for today ($today) and provide personalized insights:
                    - Profile: Gender=${profile.gender}, Age=${profile.age}, Height=${profile.heightCm}cm, Weight=${profile.weightKg}kg, BMI=${bm.bmi} (${bm.bmiCategory}), Goal=${profile.goalType}
                    - Daily Targets: ${bm.targetKcal} kcal, Protein=${bm.targetProteinG}g, Carbs=${bm.targetCarbsG}g, Fat=${bm.targetFatG}g, Step Goal=${profile.dailyStepGoal}, Water Goal=${profile.targetWaterMl}ml
                    - Today's Logged Nutrition: ${eatenKcal} kcal eaten (Protein=${proteinG}g, Carbs=${carbsG}g, Fat=${fatG}g), Water drank=${profile.waterDrankMl}ml
                    - Today's Activity & Vitals: Steps=$stepsToday, Workout Calories Burned=$workoutBurn kcal (${workouts.size} sessions), Latest Heart Rate=${latestHr ?: "N/A"} bpm, Sleep=${sleepMin} min.
                    $langPrompt
                """.trimIndent()

                val response = withTimeout(60_000L) {
                    insightsAiModel.generateContent(prompt)
                }
                val jsonString = response.text ?: throw IllegalStateException("Empty AI response.")
                val json = JSONObject(jsonString)
                val result = AiHealthInsightsResult(
                    dailyScore = json.optInt("dailyScore", 82).coerceIn(1, 100),
                    summary = json.optString("summary", ""),
                    nutritionAdvice = json.optString("nutritionAdvice", ""),
                    workoutRecommendation = json.optString("workoutRecommendation", ""),
                    recoveryTip = json.optString("recoveryTip", "")
                )
                _aiInsightsState.value = AiTaskState.Success(result)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI insights timed out", e)
                _aiInsightsState.value = AiTaskState.Error("AI Insights generation timed out after 60s.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI insights failed", e)
                _aiInsightsState.value = AiTaskState.Error(e.localizedMessage ?: "Failed to generate AI insights.")
            }
        }
    }

    // --- Workouts & Live Timer ---
    fun toggleWorkoutTimer() {
        if (_isWorkoutTimerRunning.value) {
            _isWorkoutTimerRunning.value = false
            timerJob?.cancel()
        } else {
            _isWorkoutTimerRunning.value = true
            timerJob?.cancel()
            timerJob = viewModelScope.launch {
                while (_isWorkoutTimerRunning.value) {
                    delay(1000L)
                    _workoutTimerSeconds.update { it + 1 }
                }
            }
        }
    }

    fun resetWorkoutTimer() {
        _isWorkoutTimerRunning.value = false
        timerJob?.cancel()
        _workoutTimerSeconds.value = 0
    }

    fun logWorkoutSession(sportId: String, sportName: String, durationMin: Int, intensity: String, burnedKcal: Int, notes: String? = null) {
        viewModelScope.launch {
            val today = LocalDate.now().toString()
            repository.addWorkoutSession(
                sportId = sportId,
                sportName = sportName,
                durationMin = durationMin.toLong().coerceAtLeast(1L),
                intensity = intensity,
                burnedKcal = burnedKcal.toLong().coerceAtLeast(0L),
                localDate = today,
                notes = notes
            ).onSuccess {
                _bannerMessage.value = "Workout logged: $sportName (-$burnedKcal kcal) ✓"
            }
        }
    }

    fun deleteWorkout(id: String) {
        viewModelScope.launch {
            repository.deleteWorkoutSession(id)
        }
    }

    // --- YAWMI Tasks, Habits & DORO Finance ---
    fun addYawmiItem(
        itemType: String,
        title: String,
        subtitle: String? = null,
        priority: String = "medium",
        amountMinor: Long = 0L
    ) {
        viewModelScope.launch {
            val today = LocalDate.now().toString()
            repository.addYawmiItem(
                itemType = itemType,
                title = title,
                subtitle = subtitle,
                priority = priority,
                completed = false,
                amountMinor = amountMinor,
                localDate = today
            ).onSuccess {
                _bannerMessage.value = "Added $title ✓"
            }
        }
    }

    fun toggleYawmiItem(item: YawmiItem) {
        viewModelScope.launch {
            repository.toggleYawmiItemCompleted(item)
        }
    }

    fun deleteYawmiItem(id: String) {
        viewModelScope.launch {
            repository.deleteYawmiItem(id)
        }
    }

    private fun scaleBitmapUnderMaxDim(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = w.toFloat() / h.toFloat()
        val (newW, newH) = if (w > h) {
            maxDim to (maxDim / ratio).roundToInt().coerceAtLeast(1)
        } else {
            (maxDim * ratio).roundToInt().coerceAtLeast(1) to maxDim
        }
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }
}
