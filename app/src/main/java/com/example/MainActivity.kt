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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import com.example.data.local.PlannerDatabase
import com.example.data.repository.PlannerRepository
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.PlannerViewModel
import com.example.ui.viewmodel.PlannerViewModelFactory
import com.example.ui.viewmodel.Screen
import com.example.ui.viewmodel.BrowserPushNotification

class MainActivity : ComponentActivity() {
    private lateinit var database: PlannerDatabase
    private lateinit var repository: PlannerRepository
    private lateinit var viewModel: PlannerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                val activeBrowserPush by viewModel.activeBrowserPush.collectAsStateWithLifecycle()

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

                    // Simulated Browser Push Notification Overlay
                    AnimatedVisibility(
                        visible = activeBrowserPush != null,
                        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(16.dp)
                            .zIndex(99f)
                    ) {
                        activeBrowserPush?.let { push ->
                            BrowserPushNotificationToast(
                                push = push,
                                onDismiss = { viewModel.dismissBrowserPush() }
                            )
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

        // Tab 6: Settings
        NavigationBarItem(
            selected = currentScreen == Screen.Settings,
            onClick = { onNavigate(Screen.Settings) },
            icon = {
                Icon(
                    imageVector = if (currentScreen == Screen.Settings) Icons.Default.Settings else Icons.Outlined.Settings,
                    contentDescription = "Settings"
                )
            },
            label = {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            },
            modifier = Modifier.testTag("nav_settings")
        )
    }
}

@Composable
fun BrowserPushNotificationToast(
    push: BrowserPushNotification,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("browser_push_toast"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Chrome push badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "MedPulse Web Portal (Browser Push)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp).testTag("dismiss_push_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss Notification",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Body content
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (push.iconType == "assignment") 
                                MaterialTheme.colorScheme.errorContainer 
                            else 
                                MaterialTheme.colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (push.iconType == "assignment") 
                            Icons.Default.AssignmentLate 
                        else 
                            Icons.Default.School,
                        contentDescription = null,
                        tint = if (push.iconType == "assignment") 
                            MaterialTheme.colorScheme.onErrorContainer 
                        else 
                            MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = push.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = push.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
        }
    }
}
