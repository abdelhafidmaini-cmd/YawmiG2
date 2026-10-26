package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import com.example.data.model.FoodLog
import com.example.data.model.HealthRecord
import com.example.data.model.SPORTS_ENCYCLOPEDIA
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutSession
import com.example.data.model.YawmiItem
import com.example.data.model.calculateBodyMetrics
import com.example.data.model.estimateWorkoutCalories
import com.example.ui.components.AnimatedCircularGoalRing
import com.example.ui.components.AnimatedHealthBarChart
import com.example.ui.components.AnimatedMacroProgressBar
import com.example.ui.components.ChartBarPoint
import com.example.ui.components.PulsingHeartBadge
import com.example.viewmodel.YawmiViewModel
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun HomeDashboardTab(
    viewModel: YawmiViewModel,
    profile: UserProfile,
    healthRecords: List<HealthRecord>,
    foodLogs: List<FoodLog>,
    workouts: List<WorkoutSession>,
    yawmiItems: List<YawmiItem>,
    isArabic: Boolean,
    onNavigateTab: (Int) -> Unit
) {
    val today = LocalDate.now().toString()
    val bm = remember(profile, isArabic) { calculateBodyMetrics(profile, isArabic) }
    val primaryRecords = remember(healthRecords) { healthRecords.filter { it.dedupStatus == "unique_primary" } }

    val todaySteps = remember(primaryRecords, today) {
        primaryRecords.filter { it.metricType == "steps" && it.localDate == today }.sumOf { it.value }.roundToInt()
    }
    val latestHr = remember(primaryRecords) {
        primaryRecords.firstOrNull { it.metricType == "heart_rate" }?.value?.roundToInt()
    }
    val todayMeals = remember(foodLogs, today) { foodLogs.filter { it.localDate == today } }
    val eatenKcal = remember(todayMeals) { todayMeals.sumOf { it.calories }.roundToInt() }
    val burnedWorkoutKcal = remember(workouts, today) {
        workouts.filter { it.localDate == today }.sumOf { it.burnedKcal }.toInt()
    }
    val totalBurnedKcal = burnedWorkoutKcal + (todaySteps * 0.04).roundToInt()

    val tasks = remember(yawmiItems) { yawmiItems.filter { it.itemType == "task" || it.itemType == "habit" } }
    val completedTasks = tasks.count { it.completed }
    val bleState by viewModel.bluetoothManager.state.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            // Hero Dashboard Banner with Animated Progress Rings
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF0F172A),
                                Color(0xFF172554),
                                Color(0xFF1E1B4B)
                            )
                        )
                    )
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = today,
                                fontSize = 12.sp,
                                color = Color(0xFF93C5FD),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isArabic) "يومك بخير 👋 | YAWMI" else "Welcome back 👋 | YAWMI",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        PulsingHeartBadge(bpm = bleState.liveHeartRateBpm ?: latestHr)
                    }

                    // Dual Animated Circular Goal Rings (Steps + Daily Calories)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedCircularGoalRing(
                            progress = todaySteps.toFloat() / profile.dailyStepGoal.coerceAtLeast(1L).toFloat(),
                            currentValue = todaySteps,
                            targetValue = profile.dailyStepGoal.toInt(),
                            label = if (isArabic) "خطوات اليوم" else "Daily Steps",
                            unitLabel = if (isArabic) "خطوة" else "steps",
                            primaryColor = Color(0xFF10B981),
                            secondaryColor = Color(0xFF38BDF8),
                            modifier = Modifier.clickable { onNavigateTab(1) }
                        )

                        AnimatedCircularGoalRing(
                            progress = eatenKcal.toFloat() / bm.targetKcal.coerceAtLeast(1).toFloat(),
                            currentValue = eatenKcal,
                            targetValue = bm.targetKcal,
                            label = if (isArabic) "سعرات الأكل" else "Food Intake",
                            unitLabel = "kcal",
                            primaryColor = Color(0xFFF59E0B),
                            secondaryColor = Color(0xFFEF4444),
                            modifier = Modifier.clickable { onNavigateTab(2) }
                        )
                    }

                    // Quick KPI Strip inside Hero
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HeroMiniKpi(
                            title = if (isArabic) "🔥 المحروق" else "🔥 Burned",
                            value = "$totalBurnedKcal kcal",
                            modifier = Modifier.weight(1f)
                        )
                        HeroMiniKpi(
                            title = if (isArabic) "💧 شرب الماء" else "💧 Hydration",
                            value = "${profile.waterDrankMl}/${profile.targetWaterMl} ml",
                            modifier = Modifier.weight(1f)
                        )
                        HeroMiniKpi(
                            title = if (isArabic) "✅ المهام" else "✅ Tasks",
                            value = "$completedTasks/${tasks.size}",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Quick Launch Action Strip
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionButton(
                    emoji = "⌚",
                    label = if (isArabic) "مزامنة السوار" else "BLE & Fit",
                    onClick = { onNavigateTab(1) },
                    modifier = Modifier.weight(1f).testTag("quick_ble_sync_btn")
                )
                QuickActionButton(
                    emoji = "📷",
                    label = if (isArabic) "تصوير وجبة AI" else "AI Food Cam",
                    onClick = { onNavigateTab(2) },
                    modifier = Modifier.weight(1f).testTag("quick_ai_food_btn")
                )
                QuickActionButton(
                    emoji = "🤖",
                    label = if (isArabic) "تحليل ذكي PDF" else "AI & PDF",
                    onClick = { onNavigateTab(3) },
                    modifier = Modifier.weight(1f).testTag("quick_ai_pdf_btn")
                )
                QuickActionButton(
                    emoji = "🪙",
                    label = if (isArabic) "الدورو والمهام" else "DORO & Tasks",
                    onClick = { onNavigateTab(4) },
                    modifier = Modifier.weight(1f).testTag("quick_doro_tasks_btn")
                )
            }
        }

        // Hydration Animated Quick Tracker Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AnimatedMacroProgressBar(
                        label = if (isArabic) "💧 ترطيب الجسم وشرب الماء اليوم" else "💧 Daily Hydration Progress",
                        currentGrams = profile.waterDrankMl.toDouble(),
                        targetGrams = profile.targetWaterMl.toInt(),
                        barColor = Color(0xFF38BDF8)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.updateWaterIntake(250L) },
                            modifier = Modifier.weight(1f).testTag("add_water_250_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.WaterDrop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+250 ml")
                        }
                        OutlinedButton(
                            onClick = { viewModel.updateWaterIntake(500L) },
                            modifier = Modifier.weight(1f).testTag("add_water_500_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("+500 ml 🍼")
                        }
                        OutlinedButton(
                            onClick = { viewModel.updateWaterIntake(-250L) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("-250 ml")
                        }
                    }
                }
            }
        }

        // DORO Smart Finance Summary Card
        item {
            val totalDzd = (profile.doroCashMinor + profile.doroWalletMinor) / 100
            val totalDoro = (profile.doroCashMinor + profile.doroWalletMinor) / 5
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateTab(4) }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isArabic) "🪙 الدورو DORO — ملخص الأموال" else "🪙 DORO Smart Finance Overview",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$totalDzd DZD ($totalDoro Doro)",
                            color = Color(0xFFFBBF24),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "💵 Cash: ${profile.doroCashMinor / 100} DZD",
                            fontSize = 13.sp,
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "👛 Wallet/CCP: ${profile.doroWalletMinor / 100} DZD",
                            fontSize = 13.sp,
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HeroMiniKpi(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.09f))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, fontSize = 11.sp, color = Color(0xFFCBD5E1))
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
    }
}

@Composable
private fun QuickActionButton(
    emoji: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = emoji, fontSize = 20.sp)
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun HealthAndWearablesTab(
    viewModel: YawmiViewModel,
    profile: UserProfile,
    healthRecords: List<HealthRecord>,
    workouts: List<WorkoutSession>,
    isArabic: Boolean
) {
    val bleState by viewModel.bluetoothManager.state.collectAsState()
    val hcState by viewModel.healthConnectManager.syncState.collectAsState()
    val timerSeconds by viewModel.workoutTimerSeconds.collectAsState()
    val timerRunning by viewModel.isWorkoutTimerRunning.collectAsState()

    var selectedMetricChart by remember { mutableStateOf("steps") }
    var showAddVitalDialog by remember { mutableStateOf(false) }
    var showAddWorkoutDialog by remember { mutableStateOf(false) }

    // Runtime permission launchers for BLE, Health Connect, and Activity Recognition
    val blePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.bluetoothManager.refreshState()
        viewModel.startBleScan()
    }

    val healthConnectPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) {
        viewModel.syncWithHealthConnectAndGoogleFit()
    }

    val activityRecognitionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.healthConnectManager.togglePhoneStepSensor(true)
    }

    val today = LocalDate.now().toString()
    val primaryRecords = remember(healthRecords) { healthRecords.filter { it.dedupStatus == "unique_primary" } }
    val stepsToday = primaryRecords.filter { it.metricType == "steps" && it.localDate == today }.sumOf { it.value }.roundToInt()
    val distanceToday = primaryRecords.filter { it.metricType == "distance" && it.localDate == today }.sumOf { it.value }
    val caloriesToday = primaryRecords.filter { it.metricType == "calories" && it.localDate == today }.sumOf { it.value }.roundToInt()
    val latestHr = bleState.liveHeartRateBpm ?: primaryRecords.firstOrNull { it.metricType == "heart_rate" }?.value?.roundToInt()
    val sleepMin = primaryRecords.filter { it.metricType == "sleep" && it.localDate == today }.sumOf { it.value }.roundToInt()
    val latestSpo2 = primaryRecords.firstOrNull { it.metricType == "spo2" }?.value?.roundToInt()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            // 1. Bluetooth Low Energy (BLE) External Health Device Sync Card
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = if (bleState.connectionStatus == "connected") Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                                contentDescription = "Bluetooth Wearable",
                                tint = Color(0xFF38BDF8)
                            )
                            Column {
                                Text(
                                    text = if (isArabic) "اتصال البلوتوث للأساور الصحية (BLE)" else "Bluetooth Wearable Connectivity (BLE)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Xiaomi Smart Band 10 • HONOR Band 4 • BLE Heart Rate (0x180D)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Button(
                            onClick = {
                                if (bleState.isScanning) {
                                    viewModel.stopBleScan()
                                } else {
                                    val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                                    } else {
                                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                                    }
                                    blePermissionsLauncher.launch(perms)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("ble_scan_button")
                        ) {
                            Text(if (bleState.isScanning) "Stop Scan" else if (isArabic) "بحث بلوتوث" else "Scan BLE")
                        }
                    }

                    if (!bleState.statusMessage.isNullOrBlank()) {
                        Text(
                            text = bleState.statusMessage!!,
                            fontSize = 12.sp,
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (bleState.connectionStatus == "connected") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "✓ Connected: ${bleState.connectedName ?: "BLE Wearable"}",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                                Text(
                                    text = "Battery: ${bleState.batteryLevel?.let { "$it%" } ?: "Reading..."} • Live HR: ${bleState.liveHeartRateBpm?.let { "$it bpm" } ?: "Listening..."}",
                                    fontSize = 12.sp
                                )
                            }
                            TextButton(onClick = { viewModel.disconnectBleDevice() }) {
                                Text("Disconnect")
                            }
                        }
                    }

                    bleState.discoveredDevices.take(5).forEach { dev ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "⌚ ${dev.name}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    text = "${dev.address} • Signal ${dev.rssi} dBm • ${dev.family}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = { viewModel.connectBleDevice(dev) },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(if (isArabic) "اتصال" else "Connect", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        item {
            // 2. Health Connect & Google Fit Centralizer Card + Hardware Step Sensor
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = Icons.Default.HealthAndSafety,
                                contentDescription = "Health Connect & Google Fit",
                                tint = Color(0xFF10B981)
                            )
                            Column {
                                Text(
                                    text = if (isArabic) "مركز Health Connect و Google Fit" else "Health Connect & Google Fit Hub",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = hcState.statusMessage,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                try {
                                    healthConnectPermissionLauncher.launch(viewModel.healthConnectManager.requiredPermissions)
                                } catch (_: Exception) {
                                    viewModel.healthConnectManager.openHealthConnectSettingsOrStore()
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("health_connect_sync_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isArabic) "مزامنة Health Connect / Fit" else "Sync Health Connect & Fit", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { showAddVitalDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("manual_vital_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isArabic) "+ قياس" else "+ Vital", fontSize = 12.sp)
                        }
                    }

                    // On-Device Step Counter Row
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
                                text = if (isArabic) "🚶 مستشعر خطوات الهاتف: ${hcState.phoneSessionSteps} خطوة" else "🚶 Phone Step Sensor: ${hcState.phoneSessionSteps} steps",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = if (isArabic) "منع ازدواجية العد التلقائي مع السوار و Google Fit" else "Auto-deduplicated against Wearable & Google Fit",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                onClick = {
                                    if (hcState.isPhoneStepSensorActive) {
                                        viewModel.healthConnectManager.togglePhoneStepSensor(false)
                                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                                    } else {
                                        viewModel.healthConnectManager.togglePhoneStepSensor(true)
                                    }
                                }
                            ) {
                                Text(if (hcState.isPhoneStepSensorActive) "Stop" else "Start")
                            }
                            if (hcState.phoneSessionSteps > 0) {
                                Button(
                                    onClick = { viewModel.syncPhoneSensorStepsToCloud() },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Save")
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Animated Vital Metrics Grid
        item {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        VitalMetricCard(
                            emoji = "👣",
                            title = if (isArabic) "الخطوات" else "Steps",
                            value = if (stepsToday > 0) "$stepsToday" else "No data",
                            sub = "Goal: ${profile.dailyStepGoal}",
                            accent = Color(0xFF10B981),
                            modifier = Modifier.weight(1f).clickable { selectedMetricChart = "steps" }
                        )
                        VitalMetricCard(
                            emoji = "📍",
                            title = if (isArabic) "المسافة" else "Distance",
                            value = if (distanceToday > 0) "${distanceToday} km" else "${((stepsToday * 0.00072) * 100).roundToInt() / 100.0} km",
                            sub = "Google Fit / HC",
                            accent = Color(0xFF38BDF8),
                            modifier = Modifier.weight(1f).clickable { selectedMetricChart = "distance" }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        VitalMetricCard(
                            emoji = "❤️",
                            title = if (isArabic) "نبض القلب" else "Heart Rate",
                            value = latestHr?.let { "$it bpm" } ?: "No data",
                            sub = if (bleState.liveHeartRateBpm != null) "BLE Live Stream" else "Latest Sync",
                            accent = Color(0xFFEF4444),
                            modifier = Modifier.weight(1f).clickable { selectedMetricChart = "heart_rate" }
                        )
                        VitalMetricCard(
                            emoji = "🌙",
                            title = if (isArabic) "النوم" else "Sleep",
                            value = if (sleepMin > 0) "${sleepMin / 60}h ${sleepMin % 60}m" else "No data",
                            sub = "Sleep Stages",
                            accent = Color(0xFF8B5CF6),
                            modifier = Modifier.weight(1f).clickable { selectedMetricChart = "sleep" }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        VitalMetricCard(
                            emoji = "🫁",
                            title = if (isArabic) "الأكسجين SpO2" else "Blood Oxygen",
                            value = latestSpo2?.let { "$it%" } ?: "No data",
                            sub = "Wearable / HC",
                            accent = Color(0xFF06B6D4),
                            modifier = Modifier.weight(1f)
                        )
                        VitalMetricCard(
                            emoji = "🔥",
                            title = if (isArabic) "حرق النشاط" else "Active Burn",
                            value = "${caloriesToday + workouts.filter { it.localDate == today }.sumOf { it.burnedKcal }} kcal",
                            sub = "${workouts.count { it.localDate == today }} workout(s) today",
                            accent = Color(0xFFF59E0B),
                            modifier = Modifier.weight(1f).clickable { selectedMetricChart = "calories" }
                        )
                    }
                }
            }
        }

        // 4. Interactive Animated Time-Series Chart
        item {
            val chartPoints = remember(primaryRecords, selectedMetricChart) {
                val last7Days = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()).toString() }
                last7Days.map { dateStr ->
                    val dayRecs = primaryRecords.filter { it.localDate == dateStr && it.metricType == selectedMetricChart }
                    val v = if (selectedMetricChart == "heart_rate") {
                        dayRecs.map { it.value }.average().takeIf { !it.isNaN() }?.toFloat() ?: 0f
                    } else {
                        dayRecs.sumOf { it.value }.toFloat()
                    }
                    ChartBarPoint(dateLabel = dateStr.takeLast(5), value = v)
                }
            }
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
                        text = if (isArabic) "📈 الرسم البياني المتحرك (آخر 7 أيام)" else "📈 Animated 7-Day Progress Chart",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "steps" to "👣 Steps",
                            "calories" to "🔥 Calories",
                            "heart_rate" to "❤️ Heart Rate",
                            "sleep" to "🌙 Sleep"
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = selectedMetricChart == key,
                                onClick = { selectedMetricChart = key },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }
                    AnimatedHealthBarChart(
                        points = chartPoints,
                        unitLabel = selectedMetricChart,
                        barColor = Color(0xFF38BDF8)
                    )
                }
            }
        }

        // 5. Live Workout Timer & 34-Sport MET Logger
        item {
            val mm = String.format("%02d", timerSeconds / 60)
            val ss = String.format("%02d", timerSeconds % 60)
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.DirectionsRun, contentDescription = null, tint = Color(0xFFF59E0B))
                            Text(
                                text = if (isArabic) "⏱️ مؤقت التمرين وحاسبة الرياضات" else "⏱️ Live Workout Timer & Sports",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Button(
                            onClick = { showAddWorkoutDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("open_add_workout_dialog_btn")
                        ) {
                            Text(if (isArabic) "+ تمرين" else "+ Log Sport")
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$mm:$ss",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF38BDF8)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.toggleWorkoutTimer() },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(if (timerRunning) "Pause" else "Start")
                            }
                            if (timerSeconds > 0) {
                                OutlinedButton(
                                    onClick = {
                                        val mins = (timerSeconds / 60).coerceAtLeast(1)
                                        val burned = estimateWorkoutCalories("brisk_walk", mins, "moderate", profile.weightKg)
                                        viewModel.logWorkoutSession("brisk_walk", "Brisk Walk / Cardio", mins, "moderate", burned)
                                        viewModel.resetWorkoutTimer()
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("Finish")
                                }
                                IconButton(onClick = { viewModel.resetWorkoutTimer() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Reset Timer")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Logged Workouts List
        items(workouts.take(6), key = { it.id }) { wk ->
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
                        Text(text = "🏅 ${wk.sportName}", fontWeight = FontWeight.Bold)
                        Text(
                            text = "${wk.durationMin} min • ${wk.intensity} • -${wk.burnedKcal} kcal • ${wk.localDate}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewModel.deleteWorkout(wk.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Workout", tint = Color(0xFFF87171))
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    if (showAddVitalDialog) {
        AddVitalDialog(
            onDismiss = { showAddVitalDialog = false },
            onSave = { metric, value, unit, source ->
                viewModel.logManualVital(metric, value, unit, source)
                showAddVitalDialog = false
            }
        )
    }

    if (showAddWorkoutDialog) {
        AddWorkoutDialog(
            weightKg = profile.weightKg,
            onDismiss = { showAddWorkoutDialog = false },
            onSave = { sportId, sportName, mins, intensity, burned ->
                viewModel.logWorkoutSession(sportId, sportName, mins, intensity, burned)
                showAddWorkoutDialog = false
            }
        )
    }
}

@Composable
private fun VitalMetricCard(
    emoji: String,
    title: String,
    value: String,
    sub: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "$emoji $title", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
            Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = accent)
            Text(text = sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AddVitalDialog(
    onDismiss: () -> Unit,
    onSave: (metricType: String, value: Double, unit: String, sourceName: String) -> Unit
) {
    val metrics = listOf(
        Triple("steps", "Steps (خطوات)", "steps"),
        Triple("heart_rate", "Heart Rate (نبض القلب)", "bpm"),
        Triple("sleep", "Sleep Duration (نوم بالدقائق)", "min"),
        Triple("spo2", "Blood Oxygen SpO2 (أكسجين)", "%"),
        Triple("weight", "Body Weight (الوزن)", "kg"),
        Triple("calories", "Active Calories (سعرات)", "kcal")
    )
    var selectedIdx by remember { mutableStateOf(0) }
    var valueText by remember { mutableStateOf("8500") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Verified Health Vital") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    metrics.forEachIndexed { idx, item ->
                        AssistChip(
                            onClick = { selectedIdx = idx },
                            label = { Text(item.second, fontSize = 11.sp) }
                        )
                    }
                }
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    label = { Text("Value (${metrics[selectedIdx].third})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val v = valueText.toDoubleOrNull() ?: return@Button
                    val m = metrics[selectedIdx]
                    onSave(m.first, v, m.third, "Manual / External Device")
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AddWorkoutDialog(
    weightKg: Double,
    onDismiss: () -> Unit,
    onSave: (sportId: String, sportName: String, durationMin: Int, intensity: String, burnedKcal: Int) -> Unit
) {
    var selectedSport by remember { mutableStateOf(SPORTS_ENCYCLOPEDIA.first()) }
    var durationText by remember { mutableStateOf("35") }
    var intensity by remember { mutableStateOf("moderate") }

    val mins = durationText.toIntOrNull()?.coerceAtLeast(1) ?: 30
    val burned = estimateWorkoutCalories(selectedSport.id, mins, intensity, weightKg)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🏋️ Log Sport & Burn Calculator") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SPORTS_ENCYCLOPEDIA.forEach { sp ->
                        FilterChip(
                            selected = selectedSport.id == sp.id,
                            onClick = { selectedSport = sp },
                            label = { Text("${sp.icon} ${sp.nameEn}", fontSize = 11.sp) }
                        )
                    }
                }
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it },
                    label = { Text("Duration (minutes)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("light", "moderate", "vigorous").forEach { level ->
                        FilterChip(
                            selected = intensity == level,
                            onClick = { intensity = level },
                            label = { Text(level.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                Text(
                    text = "🔥 Estimated Burn: $burned kcal (${selectedSport.musclesAr})",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(selectedSport.id, "${selectedSport.nameAr} / ${selectedSport.nameEn}", mins, intensity, burned)
                }
            ) {
                Text("Log Workout")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
