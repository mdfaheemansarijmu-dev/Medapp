package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MedicalCourse
import com.example.ui.viewmodel.PlannerViewModel
import com.example.ui.viewmodel.LoginMode
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import android.app.Activity
import android.widget.Toast
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.PowerManager
import android.os.Build
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

@Composable
fun WelcomeScreen(
    viewModel: PlannerViewModel,
    modifier: Modifier = Modifier
) {
    val loginMode by viewModel.loginMode.collectAsStateWithLifecycle()
    var selectedItem by remember { mutableStateOf<MedicalCourse?>(null) }
    var showNotificationOnboarding by remember { mutableStateOf(false) }
    var showAccountChooserDialog by remember { mutableStateOf(false) }
    var loginSubState by remember { mutableStateOf(LoginSubState.CHOICE) }

    LaunchedEffect(loginMode) {
        if (loginMode != LoginMode.UNDECIDED) {
            loginSubState = LoginSubState.CHOICE
        }
    }
    
    val context = LocalContext.current
    val isAuthenticating by viewModel.isAuthenticating.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val phoneOtpSent by viewModel.phoneOtpSent.collectAsStateWithLifecycle()

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .build()
    }
    val googleSignInClient = remember(context, gso) {
        GoogleSignIn.getClient(context, gso)
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val name = account.displayName ?: "Google User"
                val email = account.email ?: ""
                val photoUrl = account.photoUrl?.toString() ?: ""
                val id = account.id ?: ""
                
                viewModel.signInWithGoogle(name, email, photoUrl, id)
            } catch (e: ApiException) {
                val errorMsg = when (e.statusCode) {
                    com.google.android.gms.common.api.CommonStatusCodes.NETWORK_ERROR -> "No internet connection. Please check your network and try again."
                    com.google.android.gms.common.api.CommonStatusCodes.SIGN_IN_REQUIRED -> "Google sign-in required."
                    else -> "Authentication failed: ${e.message ?: "Unknown error"}"
                }
                viewModel.setAuthError(errorMsg)
                Toast.makeText(context, "$errorMsg. Opening account chooser fallback.", Toast.LENGTH_LONG).show()
                showAccountChooserDialog = true
            }
        } else if (result.resultCode == Activity.RESULT_CANCELED) {
            viewModel.setAuthError("Sign-in cancelled")
            Toast.makeText(context, "Sign-in cancelled. Opening account chooser fallback.", Toast.LENGTH_SHORT).show()
            showAccountChooserDialog = true
        } else {
            viewModel.setAuthError("Sign-in failed (code: ${result.resultCode})")
            Toast.makeText(context, "Sign-in failed. Opening account chooser fallback.", Toast.LENGTH_SHORT).show()
            showAccountChooserDialog = true
        }
    }

    fun isPlayServicesAvailable(context: android.content.Context): Boolean {
        val googleApiAvailability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
        return resultCode == com.google.android.gms.common.ConnectionResult.SUCCESS
    }

    if (showAccountChooserDialog) {
        GoogleAccountChooserDialog(
            onAccountSelected = { name, email, dpUrl ->
                showAccountChooserDialog = false
                viewModel.signInWithGoogle(name, email, dpUrl, "simulated_google_id_12345")
            },
            onDismiss = {
                showAccountChooserDialog = false
                viewModel.setAuthenticating(false)
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f)
                    )
                )
            )
            .padding(24.dp)
    ) {
    val isProfileCompleted by viewModel.isProfileCompleted.collectAsStateWithLifecycle()
    val currentStep = when {
        showNotificationOnboarding -> WelcomeStep.NOTIFICATIONS_SETUP
        loginMode == LoginMode.UNDECIDED -> WelcomeStep.LOGIN
        loginMode == LoginMode.FIREBASE && !isProfileCompleted -> WelcomeStep.PROFILE_SETUP
        else -> WelcomeStep.COURSE_SELECTION
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f)
                    )
                )
            )
            .padding(24.dp)
    ) {
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                slideInHorizontally { width -> width } + fadeIn() togetherWith
                        slideOutHorizontally { width -> -width } + fadeOut()
            },
            label = "WelcomeTransition"
        ) { step ->
            when (step) {
                WelcomeStep.LOGIN -> {
                    when (loginSubState) {
                        LoginSubState.CHOICE -> {
                            OnboardingLoginChoice(
                                isAuthenticating = isAuthenticating,
                                authError = authError,
                                onGoogleClick = {
                                    if (!isPlayServicesAvailable(context)) {
                                        viewModel.setAuthError("Google Play Services are unavailable.")
                                        Toast.makeText(context, "Google Play Services are unavailable. Opening simulated account chooser.", Toast.LENGTH_LONG).show()
                                        showAccountChooserDialog = true
                                    } else if (!isAuthenticating) {
                                        viewModel.setAuthenticating(true)
                                        viewModel.setAuthError(null)
                                        googleSignInClient.signOut().addOnCompleteListener {
                                            googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                        }
                                    }
                                },
                                onGuestClick = {
                                    if (!isAuthenticating) {
                                        viewModel.setGuestMode()
                                    }
                                },
                                onEmailSignInClick = {
                                    viewModel.setAuthError(null)
                                    loginSubState = LoginSubState.SIGN_IN
                                },
                                onEmailSignUpClick = {
                                    viewModel.setAuthError(null)
                                    loginSubState = LoginSubState.SIGN_UP
                                },
                                onPhoneClick = {
                                    viewModel.setAuthError(null)
                                    loginSubState = LoginSubState.PHONE_SIGN_IN
                                }
                            )
                        }
                        LoginSubState.SIGN_IN -> {
                            EmailSignInForm(
                                isAuthenticating = isAuthenticating,
                                authError = authError,
                                onBack = { loginSubState = LoginSubState.CHOICE },
                                onSignIn = { email, password ->
                                    viewModel.signInWithEmailAndPassword(email, password)
                                }
                            )
                        }
                        LoginSubState.SIGN_UP -> {
                            EmailSignUpForm(
                                isAuthenticating = isAuthenticating,
                                authError = authError,
                                onBack = { loginSubState = LoginSubState.CHOICE },
                                onSignUp = { name, email, password, avatarPreset ->
                                    viewModel.signUpWithEmailAndPassword(name, email, password, avatarPreset)
                                }
                            )
                        }
                        LoginSubState.PHONE_SIGN_IN -> {
                            PhoneSignInForm(
                                isAuthenticating = isAuthenticating,
                                authError = authError,
                                otpSent = phoneOtpSent,
                                onBack = {
                                    viewModel.resetPhoneOtpState()
                                    loginSubState = LoginSubState.CHOICE
                                },
                                onSendOtp = { phone, name ->
                                    var currentContext = context
                                    var foundActivity: Activity? = null
                                    while (currentContext is android.content.ContextWrapper) {
                                        if (currentContext is Activity) {
                                            foundActivity = currentContext
                                            break
                                        }
                                        currentContext = currentContext.baseContext
                                    }
                                    if (foundActivity != null) {
                                        viewModel.sendPhoneOtp(foundActivity, phone, name)
                                    } else {
                                        viewModel.setAuthError("Unable to retrieve Android Activity for OTP verification")
                                    }
                                },
                                onVerifyOtp = { phone, otp, name, avatarPreset ->
                                    viewModel.verifyPhoneOtp(phone, otp, name, avatarPreset)
                                }
                            )
                        }
                    }
                }
                WelcomeStep.PROFILE_SETUP -> {
                    ProfileSetupScreen(
                        onSave = { name, college, course, year, semester, batch ->
                            viewModel.saveUserProfile(name, college, course, year, semester, batch)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                WelcomeStep.COURSE_SELECTION -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Header
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "MedPulse icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(64.dp)
                                    .padding(bottom = 12.dp)
                            )
                            Text(
                                text = "MedPulse",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-1).sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Smart Medical Student Companion",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Text(
                                text = "Replace scattered WhatsApp announcements and messy notes with an intelligent dashboard.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 12.dp, start = 12.dp, end = 12.dp)
                            )
                        }

                        // Selection section
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 24.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Select Your Specialization",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(bottom = 16.dp, start = 4.dp)
                            )

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(MedicalCourse.values()) { course ->
                                    val isSelected = selectedItem == course
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surface
                                            }
                                        ),
                                        border = BorderStroke(
                                            width = 1.5.dp,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                            }
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                            .clickable { selectedItem = course }
                                            .testTag("course_card_${course.code}")
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = course.code,
                                                    style = MaterialTheme.typography.headlineSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        letterSpacing = 0.sp
                                                    ),
                                                    color = if (isSelected) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    }
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = course.displayName.split(" ").first(),
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.Medium
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom CTA
                        Button(
                            onClick = {
                                if (selectedItem != null) {
                                    showNotificationOnboarding = true
                                }
                            },
                            enabled = selectedItem != null,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .navigationBarsPadding()
                                .testTag("get_started_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Get Started",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                )
                            }
                        }
                    }
                }
                WelcomeStep.NOTIFICATIONS_SETUP -> {
                    OnboardingNotificationsSetup(
                        onFinish = {
                            selectedItem?.let { viewModel.selectCourse(it) }
                        },
                        onBack = {
                            showNotificationOnboarding = false
                        }
                    )
                }
            }
        }
    }
    }
}

@Composable
fun OnboardingLoginChoice(
    isAuthenticating: Boolean,
    authError: String?,
    onGoogleClick: () -> Unit,
    onGuestClick: () -> Unit,
    onEmailSignInClick: () -> Unit,
    onEmailSignUpClick: () -> Unit,
    onPhoneClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Logo & Brand Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "MedPulse icon",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(80.dp)
                    .padding(bottom = 16.dp)
            )
            Text(
                text = "MedPulse",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1).sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Smart Medical Student Companion",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = "Replace scattered WhatsApp announcements and messy notes with an intelligent dashboard.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp)
            )
        }

        // Beautiful Card illustrating cloud readiness & academic sync
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CloudQueue,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Future-Ready Account System",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Sign in to easily link your academic profile. Guest mode works completely offline and stores data securely on your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        // CTAs and Errors
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (authError != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = authError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Real Email Credentials Pathway
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onEmailSignInClick,
                    enabled = !isAuthenticating,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("email_sign_in_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Email Login", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }
                }

                OutlinedButton(
                    onClick = onEmailSignUpClick,
                    enabled = !isAuthenticating,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("email_sign_up_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Up", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }

            // Mobile OTP Pathway
            Button(
                onClick = onPhoneClick,
                enabled = !isAuthenticating,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                    disabledContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
                    disabledContentColor = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("mobile_otp_login_btn")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Login with Mobile OTP",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            // Google Log-In
            Button(
                onClick = onGoogleClick,
                enabled = !isAuthenticating,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("continue_with_google_btn")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isAuthenticating) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Connecting...",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Continue with Google",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            // Guest Mode
            OutlinedButton(
                onClick = onGuestClick,
                enabled = !isAuthenticating,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, if (isAuthenticating) MaterialTheme.colorScheme.outline.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("continue_without_login_btn")
            ) {
                Text(
                    text = "Continue without Login (Guest Mode)",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isAuthenticating) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        }
    }
}

@Composable
fun GoogleAccountChooserDialog(
    onAccountSelected: (name: String, email: String, dpUrl: String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 6.dp,
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "G",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                Text(
                    text = "Choose an account",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "to continue to MedPulse",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )

                Surface(
                    onClick = {
                        onAccountSelected(
                            "Faheem Ansari",
                            "mdfaheemansarijmu@gmail.com",
                            ""
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth().testTag("google_account_faheem")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "F",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Faheem Ansari",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "mdfaheemansarijmu@gmail.com",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    onClick = { /* Simulated */ },
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add account",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Add another account",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "To continue, Google will share your name, email address, and profile picture with MedPulse. See our Privacy Policy and Terms of Service.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

enum class WelcomeStep {
    LOGIN,
    PROFILE_SETUP,
    COURSE_SELECTION,
    NOTIFICATIONS_SETUP
}

@Composable
fun OnboardingNotificationsSetup(
    onFinish: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val powerManager = remember(context) { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    val alarmManager = remember(context) { context.getSystemService(Context.ALARM_SERVICE) as AlarmManager }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else {
                true
            }
        )
    }

    var canScheduleExact by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }
        )
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            Toast.makeText(context, "Notifications permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Notifications are disabled. Reminders won't show in your status bar.", Toast.LENGTH_LONG).show()
        }
    }

    val batteryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }
    }

    val exactAlarmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            canScheduleExact = alarmManager.canScheduleExactAlarms()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.Start)
                    .testTag("notification_onboarding_back_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Never Miss a Class or Exam",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "To deliver class reminders and assignment deadlines precisely on time (exactly like Google Calendar), please configure the background permissions below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        // Checklist of setup tasks
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Task 1: Notifications
            PermissionSetupRow(
                title = "System Notifications",
                description = "Enables alerts on your lock screen and heads-up banner notifications.",
                isConfigured = hasNotificationPermission,
                buttonText = "Grant Permission",
                onConfigure = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                testTag = "onboarding_grant_notifs_button"
            )

            // Task 2: Battery Optimizations
            PermissionSetupRow(
                title = "Battery Saver Whitelist",
                description = "Bypasses system sleep restrictions to ensure alerts arrive even when you haven't opened the app for several days.",
                isConfigured = isIgnoringBatteryOptimizations,
                buttonText = "Configure",
                onConfigure = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        batteryLauncher.launch(intent)
                        Toast.makeText(context, "Scroll to MedPulse and set to 'Unrestricted' or 'Don't Optimize'.", Toast.LENGTH_LONG).show()
                    }
                },
                testTag = "onboarding_grant_battery_button"
            )

            // Task 3: Exact Alarm Permission (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PermissionSetupRow(
                    title = "Precise Alarms",
                    description = "Enables reminders to wake up the phone at the exact minute of a scheduled lecture or submission.",
                    isConfigured = canScheduleExact,
                    buttonText = "Enable",
                    onConfigure = {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        exactAlarmLauncher.launch(intent)
                    },
                    testTag = "onboarding_grant_exact_alarms_button"
                )
            }
        }

        // Bottom CTAs
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onFinish,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("onboarding_finish_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "Finish Setup & Launch",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            TextButton(
                onClick = onFinish,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .testTag("onboarding_skip_button")
            ) {
                Text(
                    text = "Skip for Now",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun PermissionSetupRow(
    title: String,
    description: String,
    isConfigured: Boolean,
    buttonText: String,
    onConfigure: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConfigured) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isConfigured) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (isConfigured) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (!isConfigured) {
                Button(
                    onClick = onConfigure,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .testTag(testTag)
                        .height(36.dp)
                ) {
                    Text(
                        text = buttonText,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.height(36.dp),
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Active",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

enum class LoginSubState {
    CHOICE,
    SIGN_IN,
    SIGN_UP,
    PHONE_SIGN_IN
}

@Composable
fun EmailSignInForm(
    isAuthenticating: Boolean,
    authError: String?,
    onBack: () -> Unit,
    onSignIn: (String, String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, enabled = !isAuthenticating) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.weight(1f))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Welcome Back",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Text(
                text = "Sign in to access your synchronized medical studies",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp)
            )
        }

        // Form Fields
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address") },
                placeholder = { Text("student@university.edu") },
                singleLine = true,
                enabled = !isAuthenticating,
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("signin_email_input")
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                enabled = !isAuthenticating,
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle password visibility"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("signin_password_input")
            )

            if (authError != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = authError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Action Buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onSignIn(email.trim(), password) },
                enabled = !isAuthenticating && email.isNotBlank() && password.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("signin_submit_btn")
            ) {
                if (isAuthenticating) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                } else {
                    Text("Sign In", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            }

            TextButton(
                onClick = onBack,
                enabled = !isAuthenticating,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Cancel", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

@Composable
fun EmailSignUpForm(
    isAuthenticating: Boolean,
    authError: String?,
    onBack: () -> Unit,
    onSignUp: (String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedPreset by remember { mutableStateOf("doctor_male") }
    var passwordVisible by remember { mutableStateOf(false) }

    val presets = listOf(
        "doctor_male" to "Male Doctor",
        "doctor_female" to "Female Doctor",
        "nurse" to "Nurse",
        "surgeon" to "Surgeon",
        "scientist" to "Researcher",
        "stethoscope" to "Stethoscope"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, enabled = !isAuthenticating) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.weight(1f))
            }
            
            Text(
                text = "Create Account",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Text(
                text = "Join MedPulse to sync and secure your academic schedule",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp)
            )
        }

        // Form Fields
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name") },
                placeholder = { Text("Dr. Faheem Ansari") },
                singleLine = true,
                enabled = !isAuthenticating,
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("signup_name_input")
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address") },
                placeholder = { Text("student@university.edu") },
                singleLine = true,
                enabled = !isAuthenticating,
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("signup_email_input")
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password (6+ chars)") },
                singleLine = true,
                enabled = !isAuthenticating,
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle password visibility"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("signup_password_input")
            )

            // Avatar Preset Selection header
            Text(
                text = "Choose Medical Avatar",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )

            // Horizontal Grid of Medical Avatars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.take(3).forEach { (presetKey, presetLabel) ->
                    val isSelected = selectedPreset == presetKey
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedPreset = presetKey }
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                val icon = when (presetKey) {
                                    "doctor_male" -> Icons.Default.Face
                                    "doctor_female" -> Icons.Default.Face
                                    "nurse" -> Icons.Default.Healing
                                    else -> Icons.Default.Person
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = presetLabel,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(presetLabel, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            if (authError != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = authError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Action Buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onSignUp(name.trim(), email.trim(), password, selectedPreset) },
                enabled = !isAuthenticating && name.isNotBlank() && email.isNotBlank() && password.length >= 6,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("signup_submit_btn")
            ) {
                if (isAuthenticating) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                } else {
                    Text("Register & Connect", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            }

            TextButton(
                onClick = onBack,
                enabled = !isAuthenticating,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Cancel", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

@Composable
fun PhoneSignInForm(
    isAuthenticating: Boolean,
    authError: String?,
    otpSent: Boolean,
    onBack: () -> Unit,
    onSendOtp: (String, String) -> Unit,
    onVerifyOtp: (String, String, String, String) -> Unit
) {
    var phoneNumber by remember { mutableStateOf("") }
    var studentName by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var selectedPreset by remember { mutableStateOf("doctor_male") }

    val presets = listOf(
        "doctor_male" to "Male Doctor",
        "doctor_female" to "Female Doctor",
        "nurse" to "Nurse",
        "surgeon" to "Surgeon",
        "scientist" to "Researcher",
        "stethoscope" to "Stethoscope"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, enabled = !isAuthenticating) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.weight(1f))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Icon(
                imageVector = Icons.Default.PhoneAndroid,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(56.dp)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = if (!otpSent) "Mobile OTP Login" else "Verify Mobile Code",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = if (!otpSent) 
                    "Enter your mobile details to receive a 1-click verification code" 
                    else "Enter the security code to authorize your device profile",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        // Form Fields Container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 24.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!otpSent) {
                // Phone & Name Input Form
                OutlinedTextField(
                    value = studentName,
                    onValueChange = { studentName = it },
                    label = { Text("Display Name") },
                    placeholder = { Text("e.g. Dr. Alex Mercer") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.tertiary,
                        focusedLabelColor = MaterialTheme.colorScheme.tertiary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("phone_login_name_input")
                )

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone Number") },
                    placeholder = { Text("e.g. +1 555-0199") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.tertiary,
                        focusedLabelColor = MaterialTheme.colorScheme.tertiary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("phone_login_phone_input")
                )

                // Avatar Presets Selector
                Text(
                    text = "Choose Academic Avatar:",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { (presetKey, presetLabel) ->
                        val isSelected = selectedPreset == presetKey
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                                    else Color.Transparent
                                )
                                .border(
                                    1.5.dp,
                                    if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedPreset = presetKey }
                                .padding(vertical = 10.dp, horizontal = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                val icon = when (presetKey) {
                                    "doctor_male" -> Icons.Default.Person
                                    "doctor_female" -> Icons.Default.Face
                                    "nurse" -> Icons.Default.MedicalServices
                                    "surgeon" -> Icons.Default.HealthAndSafety
                                    "scientist" -> Icons.Default.Science
                                    else -> Icons.Default.Person
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = presetLabel,
                                    tint = if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = presetLabel.substringBefore(" "),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            } else {
                // OTP Field View
                OutlinedTextField(
                    value = otpCode,
                    onValueChange = { if (it.length <= 6) otpCode = it },
                    label = { Text("6-Digit OTP Code") },
                    placeholder = { Text("------") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.tertiary,
                        focusedLabelColor = MaterialTheme.colorScheme.tertiary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("phone_login_otp_input")
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "📲 Real SMS OTP Triggered",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "A live authentication request has been sent to your mobile carrier. Please check your SMS inbox for the security verification code. (If using Firebase test credentials, enter your configured test OTP.)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            if (authError != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = authError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Action Buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { 
                    if (!otpSent) {
                        onSendOtp(phoneNumber.trim(), studentName.trim())
                    } else {
                        onVerifyOtp(phoneNumber.trim(), otpCode.trim(), studentName.trim(), selectedPreset)
                    }
                },
                enabled = !isAuthenticating && (
                    if (!otpSent) phoneNumber.isNotBlank() && studentName.isNotBlank() 
                    else otpCode.length >= 6
                ),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("phone_login_submit_btn")
            ) {
                if (isAuthenticating) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.size(20.dp))
                } else {
                    Text(
                        text = if (!otpSent) "Send Verification Code" else "Verify & Sign In",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            TextButton(
                onClick = onBack,
                enabled = !isAuthenticating,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    onSave: (name: String, college: String, course: String, year: String, semester: String, batch: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var fullName by remember { mutableStateOf("") }
    var college by remember { mutableStateOf("") }
    var selectedCourse by remember { mutableStateOf("MBBS") }
    var selectedYear by remember { mutableStateOf("1st Year") }
    var semester by remember { mutableStateOf("") }
    var batch by remember { mutableStateOf("") }

    var courseExpanded by remember { mutableStateOf(false) }
    var yearExpanded by remember { mutableStateOf(false) }

    val courses = listOf("MBBS", "BHMS", "BDS", "BAMS", "BUMS", "BSMS")
    val years = listOf("1st Year", "2nd Year", "3rd Year", "4th Year", "Internship")

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Hero Header
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = "Profile Setup",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Complete Your Profile",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Please provide your academic details to personalize your MedPulse companion.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Full Name
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Full Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().testTag("profile_name_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors()
                )

                // College
                OutlinedTextField(
                    value = college,
                    onValueChange = { college = it },
                    label = { Text("Medical College / University") },
                    leadingIcon = { Icon(Icons.Default.Place, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().testTag("profile_college_input"),
                    singleLine = true
                )

                // Course Dropdown
                ExposedDropdownMenuBox(
                    expanded = courseExpanded,
                    onExpandedChange = { courseExpanded = !courseExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedCourse,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Medical Course") },
                        leadingIcon = { Icon(Icons.Default.Menu, contentDescription = null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = courseExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth().testTag("profile_course_dropdown"),
                        colors = OutlinedTextFieldDefaults.colors()
                    )
                    ExposedDropdownMenu(
                        expanded = courseExpanded,
                        onDismissRequest = { courseExpanded = false }
                    ) {
                        courses.forEach { courseItem ->
                            DropdownMenuItem(
                                text = { Text(courseItem) },
                                onClick = {
                                    selectedCourse = courseItem
                                    courseExpanded = false
                                }
                            )
                        }
                    }
                }

                // Year Dropdown
                ExposedDropdownMenuBox(
                    expanded = yearExpanded,
                    onExpandedChange = { yearExpanded = !yearExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedYear,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Academic Year") },
                        leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = yearExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth().testTag("profile_year_dropdown"),
                        colors = OutlinedTextFieldDefaults.colors()
                    )
                    ExposedDropdownMenu(
                        expanded = yearExpanded,
                        onDismissRequest = { yearExpanded = false }
                    ) {
                        years.forEach { yearItem ->
                            DropdownMenuItem(
                                text = { Text(yearItem) },
                                onClick = {
                                    selectedYear = yearItem
                                    yearExpanded = false
                                }
                            )
                        }
                    }
                }

                // Semester (Optional)
                OutlinedTextField(
                    value = semester,
                    onValueChange = { semester = it },
                    label = { Text("Semester (Optional)") },
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                    placeholder = { Text("e.g. 1st Semester") },
                    modifier = Modifier.fillMaxWidth().testTag("profile_semester_input"),
                    singleLine = true
                )

                // Batch/Section (Optional)
                OutlinedTextField(
                    value = batch,
                    onValueChange = { batch = it },
                    label = { Text("Batch / Section (Optional)") },
                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                    placeholder = { Text("e.g. Batch A") },
                    modifier = Modifier.fillMaxWidth().testTag("profile_batch_input"),
                    singleLine = true
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (fullName.isNotBlank() && college.isNotBlank()) {
                    onSave(fullName, college, selectedCourse, selectedYear, semester, batch)
                }
            },
            enabled = fullName.isNotBlank() && college.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("save_profile_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                "Save Profile & Continue",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}


