package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FOOD_ENCYCLOPEDIA
import com.example.data.model.FoodLog
import com.example.data.model.HealthRecord
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutSession
import com.example.data.model.YawmiItem
import com.example.data.model.calculateBodyMetrics
import com.example.service.PdfReportExporter
import com.example.ui.components.AnimatedMacroProgressBar
import com.example.viewmodel.AiTaskState
import com.example.viewmodel.YawmiViewModel
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun FoodAndAiNutritionTab(
    viewModel: YawmiViewModel,
    profile: UserProfile,
    foodLogs: List<FoodLog>,
    isArabic: Boolean
) {
    val context = LocalContext.current
    val aiFoodState by viewModel.aiFoodState.collectAsState()
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedMealSlot by remember { mutableStateOf("lunch") }
    var searchQuery by remember { mutableStateOf("") }

    val bm = remember(profile, isArabic) { calculateBodyMetrics(profile, isArabic) }
    val today = LocalDate.now().toString()
    val todayMeals = remember(foodLogs, today) { foodLogs.filter { it.localDate == today } }

    val totalProtein = todayMeals.sumOf { it.proteinG }
    val totalCarbs = todayMeals.sumOf { it.carbsG }
    val totalFat = todayMeals.sumOf { it.fatG }
    val totalFiber = todayMeals.sumOf { it.fiberG }

    // Zero-permission Android Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                selectedBitmap = bmp
                viewModel.analyzeFoodPhotoWithAi(bmp, isArabic)
            } catch (e: Exception) {
                viewModel.showMessage("Could not decode photo: ${e.localizedMessage}")
            }
        }
    }

    // Camera Thumbnail Preview Launcher (no storage permission needed)
    val cameraPreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            selectedBitmap = bmp
            viewModel.analyzeFoodPhotoWithAi(bmp, isArabic)
        }
    }

    val filteredCatalog = remember(searchQuery) {
        if (searchQuery.isBlank()) FOOD_ENCYCLOPEDIA
        else FOOD_ENCYCLOPEDIA.filter {
            it.nameAr.contains(searchQuery, ignoreCase = true) ||
                it.nameEn.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            // 1. AI Photo Recognition Food Logger Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFFBBF24))
                        Column {
                            Text(
                                text = if (isArabic) "📷 التعرف على الأكل وحساب الماكروز بالذكاء الاصطناعي" else "📷 AI Food Photo Recognition & Macro Estimator",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (isArabic) "التقط صورة أو اختر من المعرض ليقدّر الذكاء الاصطناعي السعرات والبروتين والكارب والدهون تلقائيًا" else "Snap or pick a meal photo to estimate calories & macronutrients automatically with AI.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Meal Slot Selector
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "breakfast" to "🌅 Breakfast",
                            "lunch" to "☀️ Lunch",
                            "dinner" to "🌇 Dinner",
                            "snack" to "🍎 Snack"
                        ).forEach { (slot, label) ->
                            FilterChip(
                                selected = selectedMealSlot == slot,
                                onClick = { selectedMealSlot = slot },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { cameraPreviewLauncher.launch(null) },
                            modifier = Modifier.weight(1f).testTag("take_food_photo_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isArabic) "كاميرا الأكل" else "Snap Meal")
                        }
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("pick_food_photo_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isArabic) "معرض الصور" else "Pick Photo")
                        }
                    }

                    if (selectedBitmap != null) {
                        Image(
                            bitmap = selectedBitmap!!.asImageBitmap(),
                            contentDescription = "Selected Meal Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                    }

                    when (val st = aiFoodState) {
                        is AiTaskState.Loading -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                                Text(
                                    text = if (isArabic) "جارٍ تحليل صورة الطبق وحساب الماكروز..." else "Analyzing dish photo & calculating macronutrients...",
                                    fontSize = 13.sp,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                        }
                        is AiTaskState.Error -> {
                            Text(
                                text = "⚠️ ${st.message}",
                                color = Color(0xFFF87171),
                                fontSize = 12.sp
                            )
                        }
                        is AiTaskState.Success -> {
                            val est = st.data
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF10B981).copy(alpha = 0.14f))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "✨ ${est.dishName} (~${est.estimatedGrams.roundToInt()}g) — ${est.calories.roundToInt()} kcal",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF10B981)
                                )
                                Text(
                                    text = "🥩 Protein: ${est.proteinG}g  •  🍞 Carbs: ${est.carbsG}g  •  🥑 Fat: ${est.fatG}g  •  🌿 Fiber: ${est.fiberG}g",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (est.healthTip.isNotBlank()) {
                                    Text(
                                        text = "💡 ${est.healthTip}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = {
                                        viewModel.logFoodMeal(
                                            mealSlot = selectedMealSlot,
                                            foodName = est.dishName,
                                            grams = est.estimatedGrams,
                                            calories = est.calories,
                                            proteinG = est.proteinG,
                                            carbsG = est.carbsG,
                                            fatG = est.fatG,
                                            fiberG = est.fiberG,
                                            isAiRecognized = true,
                                            healthTip = est.healthTip
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("confirm_log_ai_meal_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(if (isArabic) "✓ اعتماد وإضافة لسجل اليوم" else "✓ Log AI-Recognized Meal")
                                }
                            }
                        }
                        AiTaskState.Idle -> Unit
                    }
                }
            }
        }

        // 2. Animated Macronutrients Progress Dashboard
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (isArabic) "📊 تتبع الماكروز اليومي (بروتين • كارب • دهون • ألياف)" else "📊 Daily Macronutrient Progress",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    AnimatedMacroProgressBar(
                        label = if (isArabic) "🥩 البروتين (Protein)" else "🥩 Protein",
                        currentGrams = totalProtein,
                        targetGrams = bm.targetProteinG,
                        barColor = Color(0xFF10B981)
                    )
                    AnimatedMacroProgressBar(
                        label = if (isArabic) "🍞 الكربوهيدرات (Carbs)" else "🍞 Carbohydrates",
                        currentGrams = totalCarbs,
                        targetGrams = bm.targetCarbsG,
                        barColor = Color(0xFF38BDF8)
                    )
                    AnimatedMacroProgressBar(
                        label = if (isArabic) "🥑 الدهون الصحية (Fats)" else "🥑 Healthy Fats",
                        currentGrams = totalFat,
                        targetGrams = bm.targetFatG,
                        barColor = Color(0xFFF59E0B)
                    )
                    AnimatedMacroProgressBar(
                        label = if (isArabic) "🌿 الألياف الغذائية (Fiber)" else "🌿 Dietary Fiber",
                        currentGrams = totalFiber,
                        targetGrams = 30,
                        barColor = Color(0xFF8B5CF6)
                    )
                }
            }
        }

        // 3. Today's Logged Meals
        if (todayMeals.isNotEmpty()) {
            item {
                Text(
                    text = if (isArabic) "🍽️ وجباتك المسجلة اليوم (${todayMeals.size})" else "🍽️ Today's Logged Meals (${todayMeals.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            items(todayMeals, key = { it.id }) { meal ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${if (meal.isAiRecognized) "📷 AI" else "🍲"} ${meal.foodName} (${meal.grams.roundToInt()}g)",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${meal.calories.roundToInt()} kcal • P: ${meal.proteinG}g • C: ${meal.carbsG}g • F: ${meal.fatG}g",
                                fontSize = 12.sp,
                                color = Color(0xFFFBBF24)
                            )
                        }
                        IconButton(onClick = { viewModel.deleteFoodLog(meal.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Meal", tint = Color(0xFFF87171))
                        }
                    }
                }
            }
        }

        // 4. Algerian & Global Food Encyclopedia Quick-Add
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Restaurant, contentDescription = null, tint = Color(0xFFF97316))
                        Text(
                            text = if (isArabic) "📖 موسوعة الأكلات السريعة" else "📖 Quick-Log Dish Encyclopedia",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(if (isArabic) "ابحث عن أكلة (كسكس، دجاج، شوربة، شوفان...)" else "Search dish (Couscous, Chicken, Oats...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    filteredCatalog.take(8).forEach { food ->
                        val ratio = food.defaultGrams / 100.0
                        val kcal = (food.kcalPer100g * ratio).roundToInt()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${food.icon} ${if (isArabic) food.nameAr else food.nameEn}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "${food.defaultGrams}g • $kcal kcal • P: ${(food.protein * ratio).roundToInt()}g • C: ${(food.carbs * ratio).roundToInt()}g",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    viewModel.logFoodMeal(
                                        mealSlot = selectedMealSlot,
                                        foodName = if (isArabic) food.nameAr else food.nameEn,
                                        grams = food.defaultGrams.toDouble(),
                                        calories = kcal.toDouble(),
                                        proteinG = ((food.protein * ratio) * 10).roundToInt() / 10.0,
                                        carbsG = ((food.carbs * ratio) * 10).roundToInt() / 10.0,
                                        fatG = ((food.fat * ratio) * 10).roundToInt() / 10.0,
                                        fiberG = ((food.fiber * ratio) * 10).roundToInt() / 10.0,
                                        isAiRecognized = false,
                                        healthTip = if (isArabic) food.healthTipAr else food.healthTipEn
                                    )
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("+ Log", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun AiCoachAndPdfReportTab(
    viewModel: YawmiViewModel,
    profile: UserProfile,
    healthRecords: List<HealthRecord>,
    foodLogs: List<FoodLog>,
    workouts: List<WorkoutSession>,
    isArabic: Boolean
) {
    val context = LocalContext.current
    val aiInsightsState by viewModel.aiInsightsState.collectAsState()
    val bm = remember(profile, isArabic) { calculateBodyMetrics(profile, isArabic) }

    var weightText by remember(profile.weightKg) { mutableStateOf(profile.weightKg.toString()) }
    var heightText by remember(profile.heightCm) { mutableStateOf(profile.heightCm.toString()) }
    var stepGoalText by remember(profile.dailyStepGoal) { mutableStateOf(profile.dailyStepGoal.toString()) }
    var selectedGoalType by remember(profile.goalType) { mutableStateOf(profile.goalType) }

    // CreateDocument PDF exporter
    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            try {
                val aiSummary = (aiInsightsState as? AiTaskState.Success)?.data?.let {
                    "Score ${it.dailyScore}/100: ${it.summary} | Nutrition: ${it.nutritionAdvice} | Workout: ${it.workoutRecommendation}"
                }
                context.contentResolver.openOutputStream(uri)?.use { outStream ->
                    PdfReportExporter.writeHealthAndNutritionPdf(
                        outputStream = outStream,
                        profile = profile,
                        healthRecords = healthRecords,
                        foodLogs = foodLogs,
                        workouts = workouts,
                        aiInsightSummary = aiSummary
                    )
                }
                viewModel.showMessage("PDF Report exported successfully ✓")
            } catch (e: Exception) {
                viewModel.showMessage("PDF export failed: ${e.localizedMessage}")
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            // 1. AI-Powered Health & Fitness Insights Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF38BDF8))
                            Column {
                                Text(
                                    text = if (isArabic) "🤖 توصيات الذكاء الاصطناعي الصحية" else "🤖 AI-Powered Health & Fitness Insights",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = if (isArabic) "مبنية على سجل خطواتك، نبضك، نومك، وماكروز وجباتك" else "Personalized from your steps, vitals, workouts & macros",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Button(
                            onClick = { viewModel.generatePersonalizedHealthInsights(isArabic) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("generate_ai_insights_button")
                        ) {
                            Text(if (isArabic) "توليد نصائح AI" else "Analyze Now")
                        }
                    }

                    when (val st = aiInsightsState) {
                        is AiTaskState.Loading -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                                Text(
                                    text = if (isArabic) "يحلل المدرب الذكي سجل صحتك وتغذيتك..." else "Generating personalized AI health & fitness insights...",
                                    fontSize = 13.sp,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                        }
                        is AiTaskState.Error -> {
                            Text(text = "⚠️ ${st.message}", color = Color(0xFFF87171), fontSize = 12.sp)
                        }
                        is AiTaskState.Success -> {
                            val ins = st.data
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "🌟 Daily Health Score: ${ins.dailyScore} / 100",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    color = Color(0xFFFBBF24)
                                )
                                Text(text = "📋 ${ins.summary}", fontSize = 13.sp)
                                Text(text = "🥗 Nutrition: ${ins.nutritionAdvice}", fontSize = 13.sp, color = Color(0xFF10B981))
                                Text(text = "🏋️ Workout: ${ins.workoutRecommendation}", fontSize = 13.sp, color = Color(0xFF38BDF8))
                                Text(text = "🌙 Recovery: ${ins.recoveryTip}", fontSize = 13.sp, color = Color(0xFFC084FC))
                            }
                        }
                        AiTaskState.Idle -> {
                            Text(
                                text = if (isArabic) "اضغط «توليد نصائح AI» للحصول على خطة تغذية وتمرين واستشفاء مخصصة لسجلك اليوم." else "Tap 'Analyze Now' to generate a custom nutrition, workout, and recovery plan from your logged history.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 2. Export Comprehensive PDF Report Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFEF4444))
                        Column {
                            Text(
                                text = if (isArabic) "📄 تصدير التقرير الصحي والغذائي الشامل (PDF)" else "📄 Export Health, Macros & AI Report to PDF",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (isArabic) "يشمل مؤشرات السوار و Health Connect، سجل الأكلات والماكروز، التمارين، وتوصيات الذكاء الاصطناعي" else "Includes BLE/Health Connect vitals, AI meal logs, macronutrients, workouts, and AI insights.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Button(
                        onClick = {
                            val fileName = "YAWMI_Health_Report_${LocalDate.now()}.pdf"
                            createPdfLauncher.launch(fileName)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("export_pdf_report_button")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isArabic) "تصدير التقرير بصيغة PDF الآن" else "Export Full Report to PDF",
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // 3. Body Calculator (BMI • BMR • TDEE) & Goal Editor
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (isArabic) "🧬 حاسبة الجسم والأهداف اليومية (BMI • BMR • TDEE)" else "🧬 Body Metrics & Daily Goals Calculator",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "BMI: ${bm.bmi} (${bm.bmiCategory}) • BMR: ${bm.bmr} kcal • TDEE: ${bm.tdee} kcal • Ideal: ${bm.idealMinKg}–${bm.idealMaxKg} kg",
                        fontSize = 12.sp,
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = weightText,
                            onValueChange = { weightText = it },
                            label = { Text("Weight (kg)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = heightText,
                            onValueChange = { heightText = it },
                            label = { Text("Height (cm)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = stepGoalText,
                            onValueChange = { stepGoalText = it },
                            label = { Text("Step Goal") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("lose" to "🔥 Cut (-450)", "maintain" to "⚖️ Maintain", "gain" to "💪 Bulk (+350)").forEach { (g, label) ->
                            FilterChip(
                                selected = selectedGoalType == g,
                                onClick = { selectedGoalType = g },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }
                    Button(
                        onClick = {
                            val w = weightText.toDoubleOrNull()?.coerceIn(30.0, 250.0) ?: profile.weightKg
                            val h = heightText.toDoubleOrNull()?.coerceIn(120.0, 230.0) ?: profile.heightCm
                            val steps = stepGoalText.toLongOrNull()?.coerceIn(1000L, 100000L) ?: profile.dailyStepGoal
                            viewModel.saveProfile(
                                profile.copy(
                                    weightKg = w,
                                    heightCm = h,
                                    dailyStepGoal = steps,
                                    goalType = selectedGoalType
                                )
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isArabic) "حفظ بيانات الجسم والأهداف" else "Save Body Profile & Goals")
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun PlanningAndDoroFinanceTab(
    viewModel: YawmiViewModel,
    profile: UserProfile,
    yawmiItems: List<YawmiItem>,
    isArabic: Boolean
) {
    var newTitle by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("task") }
    var amountDzdText by remember { mutableStateOf("") }
    var doroUnit by remember { mutableStateOf("dzd") } // dzd | centime | doro

    fun formatDoro(minorUnits: Long): String {
        return when (doroUnit) {
            "centime" -> "$minorUnits Centimes"
            "doro" -> "${minorUnits / 5} Doro"
            else -> "${minorUnits / 100} DZD"
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            // DORO Smart Expense & Algerian Currency Converter Banner
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isArabic) "🪙 الدورو DORO — الأرصدة والمحول" else "🪙 DORO Smart Expense & Converter",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFBBF24)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("dzd" to "DZD", "centime" to "سنتيم", "doro" to "دورو").forEach { (u, label) ->
                                FilterChip(
                                    selected = doroUnit == u,
                                    onClick = { doroUnit = u },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("💵 Cash", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatDoro(profile.doroCashMinor), fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                        }
                        Column {
                            Text("👛 Wallet / CCP", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatDoro(profile.doroWalletMinor), fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                        }
                        Column {
                            Text("💰 Total", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatDoro(profile.doroCashMinor + profile.doroWalletMinor), fontWeight = FontWeight.ExtraBold, color = Color(0xFFFBBF24))
                        }
                    }
                }
            }
        }

        // Quick Add Task / Habit / Expense Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "task" to "✅ Task",
                            "habit" to "🎯 Habit",
                            "expense" to "📤 Expense",
                            "planned_purchase" to "🛒 Planned"
                        ).forEach { (t, label) ->
                            FilterChip(
                                selected = selectedType == t,
                                onClick = { selectedType = t },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text(if (isArabic) "العنوان (مهمة / عادة / مصروف)" else "Title (Task / Habit / Expense)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_yawmi_item_input")
                    )
                    if (selectedType == "expense" || selectedType == "planned_purchase") {
                        OutlinedTextField(
                            value = amountDzdText,
                            onValueChange = { amountDzdText = it },
                            label = { Text("Amount in DZD (1 DZD = 20 Doro = 100 Centimes)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Button(
                        onClick = {
                            if (newTitle.isNotBlank()) {
                                val minor = ((amountDzdText.toDoubleOrNull() ?: 0.0) * 100).roundToInt().toLong()
                                viewModel.addYawmiItem(
                                    itemType = selectedType,
                                    title = newTitle.trim(),
                                    amountMinor = minor
                                )
                                newTitle = ""
                                amountDzdText = ""
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("add_yawmi_item_button")
                    ) {
                        Text(if (isArabic) "+ حفظ في يومي" else "+ Add to YAWMI")
                    }
                }
            }
        }

        items(yawmiItems, key = { it.id }) { item ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Checkbox(
                            checked = item.completed,
                            onCheckedChange = { viewModel.toggleYawmiItem(item) }
                        )
                        Column {
                            Text(
                                text = item.title,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${item.itemType.uppercase()} • ${item.localDate}" +
                                    if (item.amountMinor > 0) " • ${formatDoro(item.amountMinor)}" else "",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.deleteYawmiItem(item.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFF87171))
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}
