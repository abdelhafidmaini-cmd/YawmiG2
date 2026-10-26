package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlin.math.roundToInt

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "settings",
    val gender: String = "male",
    val age: Long = 30L,
    val heightCm: Double = 175.0,
    val weightKg: Double = 75.0,
    val activityLevel: String = "moderate",
    val goalType: String = "maintain",
    val targetWaterMl: Long = 2600L,
    val waterDrankMl: Long = 0L,
    val dailyStepGoal: Long = 8000L,
    val doroCashMinor: Long = 1500000L, // 15,000 DZD
    val doroWalletMinor: Long = 3200000L, // 32,000 DZD
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class HealthRecord(
    @DocumentId val id: String = "",
    val metricType: String = "steps",
    val value: Double = 0.0,
    val unit: String = "steps",
    val localDate: String = "",
    val sourceType: String = "manual_verified",
    val sourceName: String = "Manual Verified",
    val deviceModel: String? = null,
    val dedupStatus: String = "unique_primary",
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FoodLog(
    @DocumentId val id: String = "",
    val mealSlot: String = "lunch",
    val foodName: String = "",
    val grams: Double = 250.0,
    val calories: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val fiberG: Double = 0.0,
    val healthTip: String? = null,
    val localDate: String = "",
    val isAiRecognized: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class WorkoutSession(
    @DocumentId val id: String = "",
    val sportId: String = "brisk_walk",
    val sportName: String = "",
    val durationMin: Long = 30L,
    val intensity: String = "moderate",
    val burnedKcal: Long = 180L,
    val localDate: String = "",
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class YawmiItem(
    @DocumentId val id: String = "",
    val itemType: String = "task", // task | habit | event | expense | income | planned_purchase
    val title: String = "",
    val subtitle: String? = null,
    val priority: String = "medium", // low | medium | high | urgent
    val completed: Boolean = false,
    val amountMinor: Long = 0L,
    val localDate: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class BodyMetricsResult(
    val bmi: Double,
    val bmiCategory: String,
    val idealMinKg: Double,
    val idealMaxKg: Double,
    val bmr: Int,
    val tdee: Int,
    val targetKcal: Int,
    val targetProteinG: Int,
    val targetCarbsG: Int,
    val targetFatG: Int
)

fun calculateBodyMetrics(profile: UserProfile, isArabic: Boolean = true): BodyMetricsResult {
    val hM = (profile.heightCm.coerceIn(120.0, 230.0)) / 100.0
    val wKg = profile.weightKg.coerceIn(30.0, 250.0)
    val bmi = ((wKg / (hM * hM)) * 10.0).roundToInt() / 10.0
    val bmiCat = when {
        bmi < 18.5 -> if (isArabic) "نحافة" else "Underweight"
        bmi < 25.0 -> if (isArabic) "وزن مثالي وصحي ✓" else "Healthy Weight ✓"
        bmi < 30.0 -> if (isArabic) "زيادة في الوزن" else "Overweight"
        else -> if (isArabic) "سمنة (ينصح بالتنشيف)" else "Obese (Cut Recommended)"
    }
    val idealMin = ((18.5 * hM * hM) * 10.0).roundToInt() / 10.0
    val idealMax = ((24.9 * hM * hM) * 10.0).roundToInt() / 10.0
    val baseBmr = 10.0 * wKg + 6.25 * profile.heightCm - 5.0 * profile.age
    val bmr = (if (profile.gender == "female") baseBmr - 161 else baseBmr + 5).roundToInt()
    val mult = when (profile.activityLevel) {
        "sedentary" -> 1.2
        "light" -> 1.375
        "active" -> 1.725
        "very_active" -> 1.9
        else -> 1.55
    }
    val tdee = (bmr * mult).roundToInt()
    val targetKcal = when (profile.goalType) {
        "lose" -> (tdee - 450).coerceAtLeast(1200)
        "gain" -> tdee + 350
        else -> tdee
    }
    val protRatio = when (profile.goalType) {
        "lose" -> 1.9
        "gain" -> 2.0
        else -> 1.6
    }
    val targetProteinG = (wKg * protRatio).roundToInt()
    val targetFatG = ((targetKcal * 0.27) / 9.0).roundToInt()
    val remForCarbs = (targetKcal - (targetProteinG * 4 + targetFatG * 9)).coerceAtLeast(400)
    val targetCarbsG = (remForCarbs / 4.0).roundToInt()

    return BodyMetricsResult(
        bmi = bmi,
        bmiCategory = bmiCat,
        idealMinKg = idealMin,
        idealMaxKg = idealMax,
        bmr = bmr,
        tdee = tdee,
        targetKcal = targetKcal,
        targetProteinG = targetProteinG,
        targetCarbsG = targetCarbsG,
        targetFatG = targetFatG
    )
}

data class FoodCatalogItem(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val category: String,
    val icon: String,
    val kcalPer100g: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    val defaultGrams: Int,
    val healthTipAr: String,
    val healthTipEn: String
)

val FOOD_ENCYCLOPEDIA = listOf(
    FoodCatalogItem("couscous_meat", "كسكس باللحم والخضار", "Couscous with Meat & Veggies", "algerian", "🍲", 165, 7.5, 23.0, 4.5, 2.8, 300, "غني بالألياف والبروتين؛ يُنصح بزيادة الخضار والمرق.", "Rich in fiber and protein; add extra vegetables and broth."),
    FoodCatalogItem("chorba_frik", "شوربة فريك باللحم", "Algerian Chorba Frik", "algerian", "🥣", 92, 5.2, 11.5, 2.8, 2.1, 280, "وجبة متوازنة جدًا ومشبعة بفضل القمح الأخضر (الفريك).", "Highly balanced and filling thanks to green Frik wheat."),
    FoodCatalogItem("chakhchoukha", "شخشوخة جزائرية باللحم والحمص", "Algerian Chakhchoukha", "algerian", "🍛", 210, 8.4, 29.0, 6.5, 2.5, 300, "وجبة عالية الطاقة ممتازة بعد التمارين الشاقة.", "High-energy traditional dish ideal post-workout."),
    FoodCatalogItem("rechta_chicken", "رشتة عاصمية بالدجاج واللفت", "Algiers Rechta with Chicken", "algerian", "🍜", 175, 8.8, 24.5, 4.6, 2.2, 280, "تجمع بين الكربوهيدرات المعقدة وبروتين الدجاج النظيف.", "Combines complex carbs with lean chicken protein."),
    FoodCatalogItem("tajine_zitoune", "طاجين زيتون بالدجاج والجزر", "Tajine Zitoune (Olives & Chicken)", "algerian", "🫒", 138, 11.2, 6.5, 7.4, 1.9, 250, "منخفض الكربوهيدرات وغني بالبروتين والدهون الصحية.", "Low-carb dish rich in protein and healthy olive fats."),
    FoodCatalogItem("mhadjeb_hot", "محاجب جزائرية بالتكتوكة", "Algerian Mhadjeb", "algerian", "🥞", 235, 6.2, 34.0, 8.2, 2.1, 200, "لنسخة صحية أكثر، حضّرها بكمية زيت أقل وبصل وطماطم أكثر.", "Use minimal oil on the griddle for a lighter meal."),
    FoodCatalogItem("garantita", "كرانتيكا / غرانتيتا بالحمص", "Karantika (Chickpea Bake)", "algerian", "🥧", 192, 8.5, 19.5, 8.8, 3.4, 200, "مصدر نباتي غني بالبروتين والألياف.", "Plant-based protein and fiber powerhouse."),
    FoodCatalogItem("doubara_biskra", "دوبارة بسكرية بزيت الزيتون", "Biskra Doubara (Chickpeas & Fava)", "algerian", "🍲", 158, 8.9, 21.5, 4.2, 5.5, 300, "قنبلة غذائية من البروتين النباتي والألياف والحديد.", "Loaded with plant protein, iron, and dietary fiber."),
    FoodCatalogItem("grilled_chicken", "صدر دجاج مشوي", "Grilled Chicken Breast", "protein", "🍗", 165, 31.0, 0.0, 3.6, 0.0, 180, "أفضل مصدر بروتين صافي لبناء العضلات وحرق الدهون.", "Top lean protein source for muscle synthesis and fat loss."),
    FoodCatalogItem("grilled_fish", "سمك مشوي / سردين جزائري", "Grilled Fish / Sardines", "protein", "🐟", 185, 22.5, 0.0, 10.2, 0.0, 200, "غني جدًا بأحماض أوميغا-3 الضرورية لصحة القلب والدماغ.", "Packed with heart-healthy Omega-3 fatty acids."),
    FoodCatalogItem("boiled_eggs", "بيض مسلوق / أومليت خفيف", "Boiled Eggs / Light Omelette", "protein", "🥚", 155, 12.6, 1.1, 10.6, 0.0, 110, "بروتين كامل الامتصاص يمنح شبعًا طويلًا في الفطور.", "Complete bioavailable protein for sustained morning satiety."),
    FoodCatalogItem("canned_tuna", "تونة طبيعية مصفاة", "Drained Canned Tuna", "protein", "🥫", 132, 28.0, 0.0, 1.8, 0.0, 120, "وجبة سريعة خارقة البروتين ومنخفضة السعرات بعد التمرين.", "Ultra-lean high-protein recovery meal."),
    FoodCatalogItem("lentils_stew", "عدس جزائري مطبوخ بالخضار", "Algerian Lentil Stew", "protein", "🍲", 116, 9.0, 20.0, 0.8, 7.5, 280, "غني جدًا بالبروتين النباتي والألياف والحديد.", "Rich in iron, slow-digesting carbs, and plant protein."),
    FoodCatalogItem("oatmeal_milk", "شوفان مطبوخ بالحليب والقرفة", "Oatmeal with Milk & Cinnamon", "grains", "🥣", 118, 5.4, 18.5, 2.4, 3.0, 250, "أفضل فطور صباحي للرياضيين؛ غني بألياف بيتا-غلوكان.", "Ideal athlete breakfast rich in beta-glucan fiber."),
    FoodCatalogItem("cooked_rice", "أرز بسمتي مطبوخ", "Cooked Basmati Rice", "grains", "🍚", 130, 2.7, 28.0, 0.3, 0.6, 200, "مصدر كربوهيدرات نظيف وسريع الهضم مع البروتين.", "Clean, easily digestible carbohydrate source."),
    FoodCatalogItem("green_salad", "سلطة خضراء بزيت الزيتون", "Fresh Green Salad with Olive Oil", "produce", "🥗", 68, 1.4, 5.2, 4.8, 2.5, 200, "ابدأ وجبتك بصحن سلطة لتقليل الجوع وتحسين الهضم.", "Start your meal with salad to boost micronutrients and satiety."),
    FoodCatalogItem("deglet_nour", "تمر دقلة نور جزائري", "Algerian Deglet Nour Dates", "produce", "🌴", 282, 2.5, 75.0, 0.4, 8.0, 50, "أفضل وقود طبيعي سريع قبل التمرين بـ 30 دقيقة.", "Natural pre-workout energy boost 30 mins before training."),
    FoodCatalogItem("banana_fruit", "موز طازج", "Fresh Banana", "produce", "🍌", 89, 1.1, 22.8, 0.3, 2.6, 120, "غني بالبوتاسيوم الذي يمنع التشنجات العضلية.", "High in potassium to prevent muscle cramps."),
    FoodCatalogItem("raw_nuts", "مكسرات نيئة (لوز وجوز)", "Raw Almonds & Walnuts", "produce", "🥜", 585, 20.0, 19.0, 51.0, 9.0, 30, "وجبة خفيفة صحية جدًا للقلب والدماغ (حفنة 30غ).", "Nutrient-dense brain and heart snack (stick to 30g)."),
    FoodCatalogItem("shawarma_tacos", "ساندويتش شاورما / تاكوس دجاج", "Chicken Shawarma / Tacos", "snacks", "🌯", 235, 13.5, 24.0, 9.5, 1.8, 260, "طلب الشاورما بدون صلصة مايونيز كثيفة يوفر 180 سعرة.", "Skip heavy mayo sauces to save ~180 kcal.")
)

data class SportCatalogItem(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val category: String,
    val icon: String,
    val metLight: Double,
    val metModerate: Double,
    val metVigorous: Double,
    val defaultMin: Int,
    val musclesAr: String,
    val benefitsAr: String
)

val SPORTS_ENCYCLOPEDIA = listOf(
    SportCatalogItem("brisk_walk", "المشي السريع (الهرولة الخفيفة)", "Brisk Walking", "cardio", "🏃‍♂️", 3.8, 4.8, 5.8, 35, "الفخذين، الأرداف، السمانة، القلب", "أفضل رياضة يومية لحرق الدهون الصافي ورفع اللياقة القلبية بأمان."),
    SportCatalogItem("outdoor_running", "الجري والركض الخارجي", "Outdoor Running", "cardio", "🏃", 7.0, 9.2, 11.5, 30, "كامل عضلات الأرجل، الجذع، الرئتين والقلب", "حرق سعرات عالٍ جدًا ورفع السعة الهوائية VO2 Max."),
    SportCatalogItem("cycling", "ركوب الدراجة الهوائية / الثابتة", "Cycling / Spinning", "cardio", "🚴", 5.0, 7.5, 10.0, 40, "عضلات الفخذ الأمامية والخلفية، القلب", "رياضة ممتازة لحرق السعرات بدون ضغط على الركبتين."),
    SportCatalogItem("swimming", "السباحة الحرة والمتنوعة", "Swimming Laps", "cardio", "🏊", 5.8, 8.0, 10.3, 40, "كامل عضلات الجسم (الظهر، الأكتاف، الصدر، الأرجل)", "تشغيل جميع عضلات الجسم مع استشفاء رائع للمفاصل."),
    SportCatalogItem("jump_rope", "القفز بالحبل (نط الحبل)", "Jump Rope", "cardio", "⚡", 8.0, 10.5, 12.3, 15, "السمانة، الأرجل، الأكتاف، القلب", "من أعلى التمارين حرقًا للسعرات في الدقيقة الواحدة."),
    SportCatalogItem("gym_bodybuilding", "تمارين الحديد وكمال الأجسام (Gym)", "Gym Weightlifting", "strength", "🏋️", 3.8, 5.5, 7.0, 45, "الصدر، الظهر، الأكتاف، الذراعين، الأرجل", "بناء الكتلة العضلية ورفع معدل الحرق الأساسي BMR."),
    SportCatalogItem("calisthenics", "تمارين الكاليسثنكس والوزن الذاتي", "Calisthenics & Bodyweight", "strength", "🤸", 4.2, 6.0, 8.2, 35, "الجزء العلوي، الجذع، الأكتاف، الذراعين", "تحكم كامل بوزن الجسم وقوة وظيفية عالية."),
    SportCatalogItem("hiit_fat_burn", "تمارين متقطعة عالية الشدة (HIIT)", "HIIT Fat Burn Circuit", "hiit", "🔥", 7.0, 9.5, 12.0, 20, "كامل الجسم والقلب والأوعية", "أعلى معدل حرق سعرات في وقت قصير مع استمرار الحرق بعد التمرين."),
    SportCatalogItem("football_soccer", "كرة القدم", "Football / Soccer", "team", "⚽", 6.5, 8.5, 10.5, 60, "الأرجل، القلب والرئتين، الجذع، الرشاقة", "حرق سعرات هائل وتقوية التحمل والسرعات المتقطعة."),
    SportCatalogItem("boxing_martial", "الملاكمة والفنون القتالية", "Boxing & Martial Arts", "team", "🥊", 6.5, 8.8, 11.5, 30, "الأكتاف، الذراعين، الظهر، الجذع، الأرجل", "تفريغ كامل للتوتر مع حرق سعرات مرتفع جدًا."),
    SportCatalogItem("yoga_mobility", "اليوغا والإطالة ومرونة الظهر", "Yoga & Mobility Stretching", "hiit", "🧘", 2.3, 3.2, 4.2, 25, "العمود الفقري، المفاصل، عضلات التوازن", "خفض التوتر، تحسين جودة النوم، وتسريع الاستشفاء العضلي.")
)

fun estimateWorkoutCalories(sportId: String, durationMin: Int, intensity: String, weightKg: Double): Int {
    val sport = SPORTS_ENCYCLOPEDIA.find { it.id == sportId } ?: SPORTS_ENCYCLOPEDIA.first()
    val met = when (intensity) {
        "light" -> sport.metLight
        "vigorous" -> sport.metVigorous
        else -> sport.metModerate
    }
    return (met * weightKg.coerceAtLeast(35.0) * (durationMin.coerceAtLeast(1) / 60.0)).roundToInt()
}
