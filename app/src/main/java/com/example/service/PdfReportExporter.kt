package com.example.service

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.model.FoodLog
import com.example.data.model.HealthRecord
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutSession
import com.example.data.model.calculateBodyMetrics
import java.io.OutputStream
import java.time.LocalDate
import kotlin.math.roundToInt

object PdfReportExporter {

    fun writeHealthAndNutritionPdf(
        outputStream: OutputStream,
        profile: UserProfile,
        healthRecords: List<HealthRecord>,
        foodLogs: List<FoodLog>,
        workouts: List<WorkoutSession>,
        aiInsightSummary: String?
    ) {
        val document = PdfDocument()
        val pageWidth = 595 // A4 width in points
        val pageHeight = 842 // A4 height in points
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#93C5FD")
            textSize = 11f
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E3A8A")
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 10.5f
        }
        val boldBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 9.5f
        }
        val bannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
        }
        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F1F5F9")
        }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0EA5E9")
        }

        // 1. Header Banner
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 92f, bannerPaint)
        canvas.drawText("YAWMI | Comprehensive Health, AI Nutrition & Fitness Report", 32f, 38f, titlePaint)
        val todayStr = LocalDate.now().toString()
        val bm = calculateBodyMetrics(profile, isArabic = false)
        canvas.drawText(
            "Generated: $todayStr  •  BMI: ${bm.bmi} (${bm.bmiCategory})  •  Target: ${bm.targetKcal} kcal/day",
            32f,
            62f,
            subHeaderPaint
        )
        canvas.drawText(
            "Weight: ${profile.weightKg} kg  •  Height: ${profile.heightCm} cm  •  Step Goal: ${profile.dailyStepGoal}  •  Water: ${profile.waterDrankMl}/${profile.targetWaterMl} ml",
            32f,
            80f,
            subHeaderPaint
        )

        var y = 118f

        // 2. Daily Vitals & Multi-Platform Sync Summary
        canvas.drawText("1. Synchronized Health & Wearable Vitals (BLE / Health Connect / Google Fit)", 32f, y, sectionPaint)
        y += 12f
        canvas.drawRoundRect(RectF(32f, y, pageWidth - 32f, y + 84f), 8f, 8f, cardBgPaint)

        val primaryRecords = healthRecords.filter { it.dedupStatus == "unique_primary" }
        val stepsTotal = primaryRecords.filter { it.metricType == "steps" && it.localDate == todayStr }.sumOf { it.value }.roundToInt()
        val latestHr = primaryRecords.firstOrNull { it.metricType == "heart_rate" }
        val sleepMin = primaryRecords.filter { it.metricType == "sleep" && it.localDate == todayStr }.sumOf { it.value }.roundToInt()
        val latestSpo2 = primaryRecords.firstOrNull { it.metricType == "spo2" }

        canvas.drawText("• Steps Today: $stepsTotal / ${profile.dailyStepGoal} steps", 46f, y + 22f, boldBodyPaint)
        canvas.drawText("• Heart Rate: ${latestHr?.let { "${it.value.roundToInt()} bpm (${it.sourceName})" } ?: "No reading recorded"}", 300f, y + 22f, boldBodyPaint)
        canvas.drawText("• Sleep Duration: ${if (sleepMin > 0) "${sleepMin / 60}h ${sleepMin % 60}m" else "No sleep session logged"}", 46f, y + 44f, bodyPaint)
        canvas.drawText("• Blood Oxygen (SpO2): ${latestSpo2?.let { "${it.value.roundToInt()}%" } ?: "Not recorded"}", 300f, y + 44f, bodyPaint)
        val distinctSources = primaryRecords.map { it.sourceName }.distinct().take(4).joinToString(", ").ifBlank { "Local / Manual" }
        canvas.drawText("• Active Data Sources: $distinctSources", 46f, y + 66f, mutedPaint)
        y += 106f

        // 3. Nutrition & AI Food Photo Recognition Logs
        val totalEatenKcal = foodLogs.sumOf { it.calories }.roundToInt()
        val totalProt = (foodLogs.sumOf { it.proteinG } * 10).roundToInt() / 10.0
        val totalCarbs = (foodLogs.sumOf { it.carbsG } * 10).roundToInt() / 10.0
        val totalFat = (foodLogs.sumOf { it.fatG } * 10).roundToInt() / 10.0

        canvas.drawText("2. Food Logging & Macronutrient Breakdown", 32f, y, sectionPaint)
        y += 16f
        canvas.drawText(
            "Total Intake: $totalEatenKcal / ${bm.targetKcal} kcal   |   Protein: ${totalProt}g / ${bm.targetProteinG}g   |   Carbs: ${totalCarbs}g / ${bm.targetCarbsG}g   |   Fat: ${totalFat}g / ${bm.targetFatG}g",
            32f,
            y,
            boldBodyPaint
        )
        y += 16f

        if (foodLogs.isEmpty()) {
            canvas.drawText("No meals logged yet.", 40f, y, mutedPaint)
            y += 20f
        } else {
            foodLogs.take(8).forEach { meal ->
                val aiTag = if (meal.isAiRecognized) "[AI Photo]" else "[Catalog]"
                val line = "• [${meal.mealSlot.uppercase()}] ${meal.foodName} (${meal.grams.roundToInt()}g) — ${meal.calories.roundToInt()} kcal " +
                    "(P: ${meal.proteinG}g, C: ${meal.carbsG}g, F: ${meal.fatG}g) $aiTag"
                canvas.drawText(line.take(95), 40f, y, bodyPaint)
                y += 16f
            }
        }
        y += 10f

        // 4. Workouts & Exercise Sessions
        val totalBurnedWorkout = workouts.sumOf { it.burnedKcal }
        canvas.drawText("3. Workouts & Exercise Sessions (Total Burned: $totalBurnedWorkout kcal)", 32f, y, sectionPaint)
        y += 16f
        if (workouts.isEmpty()) {
            canvas.drawText("No workout sessions logged yet.", 40f, y, mutedPaint)
            y += 20f
        } else {
            workouts.take(6).forEach { wk ->
                val line = "• ${wk.sportName} — ${wk.durationMin} min (${wk.intensity}) • Burned: ${wk.burnedKcal} kcal (${wk.localDate})"
                canvas.drawText(line.take(95), 40f, y, bodyPaint)
                y += 16f
            }
        }
        y += 10f

        // 5. AI-Powered Personalized Insights
        canvas.drawText("4. AI-Powered Health & Fitness Insights", 32f, y, sectionPaint)
        y += 12f
        val insightBoxHeight = 140f
        canvas.drawRoundRect(RectF(32f, y, pageWidth - 32f, y + insightBoxHeight), 8f, 8f, cardBgPaint)
        canvas.drawRect(32f, y, 37f, y + insightBoxHeight, accentPaint)

        val insightText = (aiInsightSummary ?: "Maintain your daily step goal of ${profile.dailyStepGoal} steps, hit ${bm.targetProteinG}g of protein for muscle recovery, and drink ${profile.targetWaterMl}ml of water daily.")
            .replace("\n", " ")
        val wrappedLines = wrapText(insightText, 82)
        var textY = y + 20f
        wrappedLines.take(7).forEach { line ->
            canvas.drawText(line, 48f, textY, bodyPaint)
            textY += 16f
        }

        // Footer
        canvas.drawText(
            "YAWMI Health & Personal Assistant • Medical Disclaimer: Consumer wearable & AI estimates are informational only.",
            32f,
            pageHeight - 24f,
            mutedPaint
        )

        document.finishPage(page)
        document.writeTo(outputStream)
        document.close()
    }

    private fun wrapText(text: String, maxCharsPerLine: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            if (current.length + word.length + 1 > maxCharsPerLine) {
                lines.add(current.toString())
                current = StringBuilder(word)
            } else {
                if (current.isNotEmpty()) current.append(" ")
                current.append(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }
}
