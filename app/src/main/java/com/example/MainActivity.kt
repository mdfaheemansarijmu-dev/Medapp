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
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
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

        // 1. Initialize Database & Repository locally (robust context-aware creation)
        database = Room.databaseBuilder(
            applicationContext,
            PlannerDatabase::class.java,
            "acuity_planner_db"
        ).fallbackToDestructiveMigration().build()

        repository = PlannerRepository(database.plannerDao())

        // 2. Instantiate ViewModel using custom Factory
        val factory = PlannerViewModelFactory(application, repository)
        viewModel = ViewModelProvider(this, factory)[PlannerViewModel::class.java]

        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
            MyApplicationTheme(darkTheme = isDarkTheme) {
                val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
                val updateAvailable by viewModel.updateAvailable.collectAsStateWithLifecycle()
                val updateResult by viewModel.updateResult.collectAsStateWithLifecycle()
                val isUpdateDialogDismissed by viewModel.isUpdateDialogDismissed.collectAsStateWithLifecycle()
                val downloadProgress by viewModel.updateDownloadProgress.collectAsStateWithLifecycle()
                val downloadState by viewModel.updateDownloadState.collectAsStateWithLifecycle()

                Box(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        if (currentScreen == Screen.Welcome) {
                            WelcomeScreen(viewModel = viewModel)
                        } else {
                            MainAppLayout(viewModel = viewModel, currentScreen = currentScreen)
                        }
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
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            BottomNavigationBar(
                currentScreen = currentScreen,
                onNavigate = { viewModel.navigateTo(it) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Screen router with smooth visual transitions
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "ScreenTransition"
            ) { targetScreen ->
                when (targetScreen) {
                    Screen.Dashboard -> DashboardScreen(viewModel = viewModel)
                    Screen.Timetable -> TimetableScreen(viewModel = viewModel)
                    Screen.Planner -> PlannerScreen(viewModel = viewModel)
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
    }
}

@Composable
fun BottomNavigationBar(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit
) {
    NavigationBar(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("bottom_nav_bar"),
        tonalElevation = 8.dp
    ) {
        // Tab 1: Home
        NavigationBarItem(
            selected = currentScreen == Screen.Dashboard,
            onClick = { onNavigate(Screen.Dashboard) },
            icon = {
                Icon(
                    imageVector = if (currentScreen == Screen.Dashboard) Icons.Default.Home else Icons.Outlined.Home,
                    contentDescription = "Dashboard"
                )
            },
            label = {
                Text(
                    text = "Home",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            },
            modifier = Modifier.testTag("nav_dashboard")
        )

        // Tab 2: Classes
        NavigationBarItem(
            selected = currentScreen == Screen.Timetable,
            onClick = { onNavigate(Screen.Timetable) },
            icon = {
                Icon(
                    imageVector = if (currentScreen == Screen.Timetable) Icons.Default.School else Icons.Outlined.School,
                    contentDescription = "Timetable"
                )
            },
            label = {
                Text(
                    text = "Classes",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            },
            modifier = Modifier.testTag("nav_timetable")
        )

        // Tab 3: Planner
        NavigationBarItem(
            selected = currentScreen == Screen.Planner,
            onClick = { onNavigate(Screen.Planner) },
            icon = {
                Icon(
                    imageVector = if (currentScreen == Screen.Planner) Icons.Default.Assignment else Icons.Outlined.Assignment,
                    contentDescription = "Curriculum Planner"
                )
            },
            label = {
                Text(
                    text = "Planner",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            },
            modifier = Modifier.testTag("nav_planner")
        )

        // Tab 4: Calendar
        NavigationBarItem(
            selected = currentScreen == Screen.Calendar,
            onClick = { onNavigate(Screen.Calendar) },
            icon = {
                Icon(
                    imageVector = if (currentScreen == Screen.Calendar) Icons.Default.CalendarToday else Icons.Outlined.CalendarToday,
                    contentDescription = "Calendar"
                )
            },
            label = {
                Text(
                    text = "Calendar",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            },
            modifier = Modifier.testTag("nav_calendar")
        )

        // Tab 5: AI Notice Parser
        NavigationBarItem(
            selected = currentScreen == Screen.AIChat,
            onClick = { onNavigate(Screen.AIChat) },
            icon = {
                Icon(
                    imageVector = if (currentScreen == Screen.AIChat) Icons.Default.AutoAwesome else Icons.Outlined.AutoAwesome,
                    contentDescription = "AI Assistant"
                )
            },
            label = {
                Text(
                    text = "AI",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            },
            modifier = Modifier.testTag("nav_ai")
        )

    }
}


