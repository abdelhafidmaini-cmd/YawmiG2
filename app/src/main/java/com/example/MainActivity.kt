package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.UserProfile
import com.example.data.repository.YawmiRepository
import com.example.service.BluetoothHealthManager
import com.example.service.HealthConnectManager
import com.example.ui.screens.AiCoachAndPdfReportTab
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.FoodAndAiNutritionTab
import com.example.ui.screens.HealthAndWearablesTab
import com.example.ui.screens.HomeDashboardTab
import com.example.ui.screens.PlanningAndDoroFinanceTab
import com.example.ui.screens.signOutUser
import com.example.ui.theme.MyApplicationTheme
import com.example.util.configureAppCheck
import com.example.viewmodel.UiState
import com.example.viewmodel.YawmiViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            var isDarkTheme by rememberSaveable { mutableStateOf(true) }
            var isArabic by rememberSaveable { mutableStateOf(true) }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AppNavigation(
                            isDarkTheme = isDarkTheme,
                            onToggleTheme = { isDarkTheme = !isDarkTheme },
                            isArabic = isArabic,
                            onToggleLanguage = { isArabic = !isArabic }
                        )
                    }
                }
            }
        }
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    isArabic: Boolean,
    onToggleLanguage: () -> Unit,
    auth: FirebaseAuth = Firebase.auth
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        YawmiAuthenticatedShell(
            currentUserId = user.uid,
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            isArabic = isArabic,
            onToggleLanguage = onToggleLanguage
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YawmiAuthenticatedShell(
    currentUserId: String,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    isArabic: Boolean,
    onToggleLanguage: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    if (selectedTab != 0) {
        BackHandler {
            selectedTab = 0
        }
    }

    val viewModel: YawmiViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                YawmiViewModel(
                    repository = YawmiRepository(db),
                    bluetoothManager = BluetoothHealthManager(app),
                    healthConnectManager = HealthConnectManager(app)
                )
            }
        }
    )

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val healthRecordsState by viewModel.healthRecordsState.collectAsStateWithLifecycle()
    val foodLogsState by viewModel.foodLogsState.collectAsStateWithLifecycle()
    val workoutsState by viewModel.workoutsState.collectAsStateWithLifecycle()
    val yawmiItemsState by viewModel.yawmiItemsState.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()

    LaunchedEffect(bannerMessage) {
        if (!bannerMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(bannerMessage!!)
            viewModel.showMessage(null)
        }
    }

    val profile = (profileState as? UiState.Success)?.data ?: UserProfile()
    val healthRecords = (healthRecordsState as? UiState.Success)?.data.orEmpty()
    val foodLogs = (foodLogsState as? UiState.Success)?.data.orEmpty()
    val workouts = (workoutsState as? UiState.Success)?.data.orEmpty()
    val yawmiItems = (yawmiItemsState as? UiState.Success)?.data.orEmpty()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF38BDF8).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (isArabic) "يومي | YAWMI" else "YAWMI | يومي",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            )
                            Text(
                                text = if (isArabic) "الصحة • الأساور • التغذية الذكية • الدورو" else "BLE • Health Connect • AI Macros • PDF",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onToggleLanguage,
                        modifier = Modifier.testTag("toggle_language_button")
                    ) {
                        Icon(Icons.Default.Language, contentDescription = "Switch Language")
                    }
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier.testTag("toggle_theme_button")
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme"
                        )
                    }
                    IconButton(
                        onClick = {
                            signOutUser(context, onSignOutComplete = {}, scope = scope)
                        },
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign Out")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text(if (isArabic) "الرئيسية" else "Home", fontSize = 11.sp) },
                    modifier = Modifier.testTag("nav_home")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Favorite, contentDescription = "Health & BLE") },
                    label = { Text(if (isArabic) "الصحة" else "Health", fontSize = 11.sp) },
                    modifier = Modifier.testTag("nav_health")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Restaurant, contentDescription = "Food & AI") },
                    label = { Text(if (isArabic) "الأكل AI" else "Food AI", fontSize = 11.sp) },
                    modifier = Modifier.testTag("nav_food")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "AI & PDF") },
                    label = { Text(if (isArabic) "مدرب PDF" else "AI & PDF", fontSize = 11.sp) },
                    modifier = Modifier.testTag("nav_ai_pdf")
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Tasks & DORO") },
                    label = { Text(if (isArabic) "التخطيط" else "DORO", fontSize = 11.sp) },
                    modifier = Modifier.testTag("nav_doro")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> HomeDashboardTab(
                    viewModel = viewModel,
                    profile = profile,
                    healthRecords = healthRecords,
                    foodLogs = foodLogs,
                    workouts = workouts,
                    yawmiItems = yawmiItems,
                    isArabic = isArabic,
                    onNavigateTab = { selectedTab = it }
                )
                1 -> HealthAndWearablesTab(
                    viewModel = viewModel,
                    profile = profile,
                    healthRecords = healthRecords,
                    workouts = workouts,
                    isArabic = isArabic
                )
                2 -> FoodAndAiNutritionTab(
                    viewModel = viewModel,
                    profile = profile,
                    foodLogs = foodLogs,
                    isArabic = isArabic
                )
                3 -> AiCoachAndPdfReportTab(
                    viewModel = viewModel,
                    profile = profile,
                    healthRecords = healthRecords,
                    foodLogs = foodLogs,
                    workouts = workouts,
                    isArabic = isArabic
                )
                4 -> PlanningAndDoroFinanceTab(
                    viewModel = viewModel,
                    profile = profile,
                    yawmiItems = yawmiItems,
                    isArabic = isArabic
                )
            }
        }
    }
}
