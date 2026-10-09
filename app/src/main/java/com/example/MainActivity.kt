package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import com.example.data.local.PlannerDatabase
import com.example.data.repository.PlannerRepository
import com.example.network.AppUpdateResult
import com.example.ui.components.OptionalUpdateDialog
import com.example.ui.components.ForceUpdateScreen
import com.example.ui.components.DuplicateContributionDialog
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AuthState
import com.example.ui.viewmodel.PlannerViewModel
import com.example.ui.viewmodel.PlannerViewModelFactory
import com.example.ui.viewmodel.Screen

class MainActivity : ComponentActivity() {
    private lateinit var database: PlannerDatabase
    private lateinit var repository: PlannerRepository
    private lateinit var viewModel: PlannerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Set college timezone (Indian Standard Time, Asia/Kolkata) as standard for Indian medical institutions
        java.util.TimeZone.setDefault(com.example.util.AttendanceTimeValidator.COLLEGE_TIMEZONE)

        // Initialize high-priority notification channels for assignments and assessments
        com.example.util.NotificationHelper.initChannels(applicationContext)

        // Request POST_NOTIFICATIONS permission on Android 13+ (API 33+) to allow system status bar alerts
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        // 1. Initialize Database & Repository locally (robust context-aware creation)
        database = Room.databaseBuilder(
            applicationContext,
            PlannerDatabase::class.java,
            "acuity_planner_db"
        )
        .addMigrations(PlannerDatabase.MIGRATION_9_10)
        .fallbackToDestructiveMigration()
        .build()

        repository = PlannerRepository(database.plannerDao())

        // 2. Instantiate ViewModel using custom Factory
        val factory = PlannerViewModelFactory(application, repository)
        viewModel = ViewModelProvider(this, factory)[PlannerViewModel::class.java]

        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
            MyApplicationTheme(darkTheme = isDarkTheme) {
                val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
                val authState by viewModel.authState.collectAsStateWithLifecycle()
                val authLoadingMessage by viewModel.authLoadingMessage.collectAsStateWithLifecycle()
                val authError by viewModel.authError.collectAsStateWithLifecycle()
                val updateAvailable by viewModel.updateAvailable.collectAsStateWithLifecycle()
                val updateResult by viewModel.updateResult.collectAsStateWithLifecycle()
                val isUpdateDialogDismissed by viewModel.isUpdateDialogDismissed.collectAsStateWithLifecycle()
                val downloadProgress by viewModel.updateDownloadProgress.collectAsStateWithLifecycle()
                val downloadState by viewModel.updateDownloadState.collectAsStateWithLifecycle()
                val duplicateDialogInfo by viewModel.duplicateContributionDialog.collectAsStateWithLifecycle()

                Box(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when (authState) {
                            AuthState.LOADING -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        if (authError == null) {
                                            CircularProgressIndicator(
                                                color = MaterialTheme.colorScheme.primary,
                                                strokeWidth = 3.dp
                                            )
                                            Text(
                                                text = authLoadingMessage,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.CloudOff,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Text(
                                                text = "Cloud Sync Error",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = authError ?: "Unable to connect to academic profile.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                            Button(
                                                onClick = { viewModel.retryAuthCheck() },
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Retry")
                                            }
                                            TextButton(onClick = { viewModel.signOut() }) {
                                                Text("Sign out / Try different account")
                                            }
                                        }
                                    }
                                }
                            }
                            AuthState.UNAUTHENTICATED, AuthState.AUTHENTICATED_PROFILE_INCOMPLETE -> {
                                WelcomeScreen(viewModel = viewModel)
                            }
                            AuthState.AUTHENTICATED_PROFILE_COMPLETE -> {
                                val effectiveScreen = if (currentScreen == Screen.Welcome) Screen.Dashboard else currentScreen
                                MainAppLayout(viewModel = viewModel, currentScreen = effectiveScreen)
                            }
                        }
                    }

                    // Duplicate Item Dialog with Student Contribution
                    duplicateDialogInfo?.let { info ->
                        DuplicateContributionDialog(
                            info = info,
                            onDismiss = { viewModel.dismissDuplicateDialog() }
                        )
                    }

                    // Handle In-App Update System Overlays and Dialogs
                    if (updateAvailable && !isUpdateDialogDismissed) {
                        (updateResult as? AppUpdateResult.UpdateAvailable)?.let { result ->
                            if (result.isForce) {
                                // Fullscreen non-dismissible critical force update overlay
                                ForceUpdateScreen(
                                    config = result.config,
                                    downloadProgress = downloadProgress,
                                    downloadState = downloadState,
                                    onUpdateClick = {
                                        viewModel.downloadAndInstallUpdate(result.config.apkUrl)
                                    },
                                    onInstallClick = {
                                        viewModel.installDownloadedUpdate()
                                    }
                                )
                            } else {
                                // Material 3 optional update AlertDialog
                                OptionalUpdateDialog(
                                    config = result.config,
                                    downloadProgress = downloadProgress,
                                    downloadState = downloadState,
                                    onUpdateClick = {
                                        viewModel.downloadAndInstallUpdate(result.config.apkUrl)
                                    },
                                    onDismissClick = {
                                        viewModel.dismissUpdateDialog()
                                    },
                                    onInstallClick = {
                                        viewModel.installDownloadedUpdate()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainAppLayout(
    viewModel: PlannerViewModel,
    currentScreen: Screen
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp
        val isCompactWidth = maxWidth < 380.dp

        if (isWideScreen) {
            // Expanded / Foldable / Tablet / Landscape mode: Side NavigationRail layout
            Row(modifier = Modifier.fillMaxSize()) {
                AppNavigationRail(
                    currentScreen = currentScreen,
                    onNavigate = { viewModel.navigateTo(it) }
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    ScreenRouter(
                        currentScreen = currentScreen,
                        viewModel = viewModel,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp)
                    )
                }
            }
        } else {
            // Standard / Compact Mobile Portrait mode: Bottom NavigationBar layout
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                bottomBar = {
                    BottomNavigationBar(
                        currentScreen = currentScreen,
                        isCompact = isCompactWidth,
                        onNavigate = { viewModel.navigateTo(it) }
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = innerPadding.calculateBottomPadding()),
                    contentAlignment = Alignment.TopCenter
                ) {
                    ScreenRouter(
                        currentScreen = currentScreen,
                        viewModel = viewModel,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 600.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ScreenRouter(
    currentScreen: Screen,
    viewModel: PlannerViewModel,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "ScreenTransition",
        modifier = modifier
    ) { targetScreen ->
        when (targetScreen) {
            Screen.Dashboard -> DashboardScreen(viewModel = viewModel)
            Screen.Timetable -> TimetableScreen(viewModel = viewModel)
            Screen.Planner -> PlannerScreen(viewModel = viewModel)
            Screen.Finished -> FinishedScreen(viewModel = viewModel)
            Screen.Calendar -> CalendarScreen(viewModel = viewModel)
            Screen.AIChat -> AIChatScreen(viewModel = viewModel)
            Screen.Settings -> SettingsScreen(viewModel = viewModel)
            Screen.Attendance -> AttendanceScreen(
                viewModel = viewModel,
                onBack = { viewModel.navigateTo(Screen.Dashboard) }
            )
            else -> DashboardScreen(viewModel = viewModel)
        }
    }
}

@Composable
fun BottomNavigationBar(
    currentScreen: Screen,
    isCompact: Boolean = false,
    onNavigate: (Screen) -> Unit
) {
    NavigationBar(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("bottom_nav_bar"),
        tonalElevation = 8.dp
    ) {
        val navItems = listOf(
            Triple(Screen.Dashboard, "Home", Pair(Icons.Default.Home, Icons.Outlined.Home)),
            Triple(Screen.Timetable, "Classes", Pair(Icons.Default.School, Icons.Outlined.School)),
            Triple(Screen.Planner, "Planner", Pair(Icons.Default.Assignment, Icons.Outlined.Assignment)),
            Triple(Screen.Finished, "Finished", Pair(Icons.Default.CheckCircle, Icons.Outlined.CheckCircle)),
            Triple(Screen.Calendar, "Calendar", Pair(Icons.Default.CalendarToday, Icons.Outlined.CalendarToday)),
            Triple(Screen.AIChat, "AI", Pair(Icons.Default.AutoAwesome, Icons.Outlined.AutoAwesome))
        )

        navItems.forEach { (screen, label, icons) ->
            val isSelected = currentScreen == screen
            val tag = when (screen) {
                Screen.Dashboard -> "nav_dashboard"
                Screen.Timetable -> "nav_timetable"
                Screen.Planner -> "nav_planner"
                Screen.Finished -> "nav_finished"
                Screen.Calendar -> "nav_calendar"
                Screen.AIChat -> "nav_ai"
                else -> "nav_${label.lowercase()}"
            }

            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(screen) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) icons.first else icons.second,
                        contentDescription = label
                    )
                },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = if (isCompact) 9.sp else 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        ),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                alwaysShowLabel = !isCompact || isSelected,
                modifier = Modifier.testTag(tag)
            )
        }
    }
}

@Composable
fun AppNavigationRail(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit
) {
    NavigationRail(
        modifier = Modifier
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("app_nav_rail"),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        val navItems = listOf(
            Triple(Screen.Dashboard, "Home", Pair(Icons.Default.Home, Icons.Outlined.Home)),
            Triple(Screen.Timetable, "Classes", Pair(Icons.Default.School, Icons.Outlined.School)),
            Triple(Screen.Planner, "Planner", Pair(Icons.Default.Assignment, Icons.Outlined.Assignment)),
            Triple(Screen.Finished, "Finished", Pair(Icons.Default.CheckCircle, Icons.Outlined.CheckCircle)),
            Triple(Screen.Calendar, "Calendar", Pair(Icons.Default.CalendarToday, Icons.Outlined.CalendarToday)),
            Triple(Screen.AIChat, "AI", Pair(Icons.Default.AutoAwesome, Icons.Outlined.AutoAwesome))
        )

        Spacer(modifier = Modifier.weight(1f))

        navItems.forEach { (screen, label, icons) ->
            val isSelected = currentScreen == screen
            val tag = when (screen) {
                Screen.Dashboard -> "nav_dashboard"
                Screen.Timetable -> "nav_timetable"
                Screen.Planner -> "nav_planner"
                Screen.Finished -> "nav_finished"
                Screen.Calendar -> "nav_calendar"
                Screen.AIChat -> "nav_ai"
                else -> "nav_${label.lowercase()}"
            }

            NavigationRailItem(
                selected = isSelected,
                onClick = { onNavigate(screen) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) icons.first else icons.second,
                        contentDescription = label
                    )
                },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .testTag(tag)
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}


