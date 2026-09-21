package com.example.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MedicalCourse
import com.example.ui.viewmodel.LoginMode
import com.example.ui.viewmodel.PlannerViewModel
import com.example.ui.viewmodel.Screen
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.delay

enum class AuthMode {
    SPLASH,
    LOGIN,
    SIGN_UP,
    PHONE_OTP
}

/**
 * Mandatory Authentication Screen matching the MedPulse visual specification.
 * Eliminates guest mode bypass to guarantee student data is securely backed up in Firebase.
 */
@Composable
fun AuthScreen(
    viewModel: PlannerViewModel,
    onAuthSuccess: (isNewUser: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var currentAuthMode by remember { mutableStateOf(AuthMode.SPLASH) }
    val isAuthenticating by viewModel.isAuthenticating.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val phoneOtpSent by viewModel.phoneOtpSent.collectAsStateWithLifecycle()
    val loginMode by viewModel.loginMode.collectAsStateWithLifecycle()

    // Form inputs
    var loginEmail by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginPasswordVisible by remember { mutableStateOf(false) }

    var signUpName by remember { mutableStateOf("") }
    var signUpEmail by remember { mutableStateOf("") }
    var signUpPassword by remember { mutableStateOf("") }
    var signUpConfirmPassword by remember { mutableStateOf("") }
    var signUpPasswordVisible by remember { mutableStateOf(false) }
    var signUpConfirmPasswordVisible by remember { mutableStateOf(false) }

    var phoneInput by remember { mutableStateOf("") }
    var otpInput by remember { mutableStateOf("") }
    var localValidationError by remember { mutableStateOf<String?>(null) }

    // Google Sign-In setup
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
        viewModel.setAuthenticating(false)
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val name = account.displayName ?: "Medical Student"
                val email = account.email ?: ""
                val photoUrl = account.photoUrl?.toString() ?: ""
                val id = account.id ?: ""
                val idToken = account.idToken

                viewModel.signInWithGoogle(name, email, photoUrl, id, idToken)
                Toast.makeText(context, "Signed in as $name", Toast.LENGTH_SHORT).show()

                // Restore cloud data
                viewModel.restoreDataFromFirebase { success ->
                    if (success && viewModel.isOnboardingCompleted()) {
                        viewModel.navigateTo(Screen.Dashboard)
                    } else {
                        onAuthSuccess(false)
                    }
                }
            } catch (e: ApiException) {
                val errorMsg = when (e.statusCode) {
                    com.google.android.gms.common.api.CommonStatusCodes.NETWORK_ERROR -> "Network error connecting to Google. Please check internet connection."
                    com.google.android.gms.common.api.CommonStatusCodes.CANCELED -> "Google sign-in cancelled."
                    else -> "Sign-in error (${e.statusCode}). Please try again."
                }
                viewModel.setAuthError(errorMsg)
            }
        }
    }

    // Auto-advance splash after brief delay
    LaunchedEffect(currentAuthMode) {
        if (currentAuthMode == AuthMode.SPLASH) {
            delay(1200)
            currentAuthMode = AuthMode.LOGIN
        }
    }

    // React to successful Firebase login
    LaunchedEffect(loginMode) {
        if (loginMode == LoginMode.FIREBASE || loginMode == LoginMode.GOOGLE) {
            if (viewModel.isOnboardingCompleted()) {
                viewModel.navigateTo(Screen.Dashboard)
            } else {
                onAuthSuccess(currentAuthMode == AuthMode.SIGN_UP)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                if (currentAuthMode == AuthMode.SPLASH) Color(0xFF1A3EB1)
                else Color(0xFFF8FAFC)
            )
    ) {
        AnimatedContent(
            targetState = currentAuthMode,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            },
            label = "AuthModeTransition"
        ) { mode ->
            when (mode) {
                AuthMode.SPLASH -> {
                    AuthSplashScreen(
                        onSplashComplete = { currentAuthMode = AuthMode.LOGIN }
                    )
                }

                AuthMode.LOGIN -> {
                    AuthLoginScreen(
                        email = loginEmail,
                        onEmailChange = {
                            loginEmail = it
                            localValidationError = null
                        },
                        password = loginPassword,
                        onPasswordChange = {
                            loginPassword = it
                            localValidationError = null
                        },
                        passwordVisible = loginPasswordVisible,
                        onTogglePasswordVisibility = { loginPasswordVisible = !loginPasswordVisible },
                        isAuthenticating = isAuthenticating,
                        errorMessage = localValidationError ?: authError,
                        onSignIn = {
                            focusManager.clearFocus()
                            if (loginEmail.isBlank() || !loginEmail.contains("@")) {
                                localValidationError = "Please enter a valid email address."
                                return@AuthLoginScreen
                            }
                            if (loginPassword.length < 6) {
                                localValidationError = "Password must be at least 6 characters."
                                return@AuthLoginScreen
                            }
                            localValidationError = null
                            viewModel.signInWithEmailAndPassword(loginEmail.trim(), loginPassword)
                        },
                        onGoogleSignIn = {
                            viewModel.setAuthenticating(true)
                            viewModel.setAuthError(null)
                            localValidationError = null
                            googleSignInLauncher.launch(googleSignInClient.signInIntent)
                        },
                        onPhoneSignIn = {
                            viewModel.setAuthError(null)
                            localValidationError = null
                            currentAuthMode = AuthMode.PHONE_OTP
                        },
                        onNavigateToSignUp = {
                            viewModel.setAuthError(null)
                            localValidationError = null
                            currentAuthMode = AuthMode.SIGN_UP
                        }
                    )
                }

                AuthMode.SIGN_UP -> {
                    AuthSignUpScreen(
                        name = signUpName,
                        onNameChange = {
                            signUpName = it
                            localValidationError = null
                        },
                        email = signUpEmail,
                        onEmailChange = {
                            signUpEmail = it
                            localValidationError = null
                        },
                        password = signUpPassword,
                        onPasswordChange = {
                            signUpPassword = it
                            localValidationError = null
                        },
                        confirmPassword = signUpConfirmPassword,
                        onConfirmPasswordChange = {
                            signUpConfirmPassword = it
                            localValidationError = null
                        },
                        passwordVisible = signUpPasswordVisible,
                        onTogglePasswordVisibility = { signUpPasswordVisible = !signUpPasswordVisible },
                        confirmPasswordVisible = signUpConfirmPasswordVisible,
                        onToggleConfirmPasswordVisibility = { signUpConfirmPasswordVisible = !signUpConfirmPasswordVisible },
                        isAuthenticating = isAuthenticating,
                        errorMessage = localValidationError ?: authError,
                        onBackToLogin = {
                            viewModel.setAuthError(null)
                            localValidationError = null
                            currentAuthMode = AuthMode.LOGIN
                        },
                        onSignUp = {
                            focusManager.clearFocus()
                            if (signUpEmail.isBlank() || !signUpEmail.contains("@")) {
                                localValidationError = "Please enter a valid email address."
                                return@AuthSignUpScreen
                            }
                            if (signUpPassword.length < 6) {
                                localValidationError = "Password must be at least 6 characters."
                                return@AuthSignUpScreen
                            }
                            if (signUpPassword != signUpConfirmPassword) {
                                localValidationError = "Passwords do not match."
                                return@AuthSignUpScreen
                            }
                            localValidationError = null
                            val studentName = if (signUpName.isNotBlank()) signUpName.trim() else signUpEmail.substringBefore("@")
                            viewModel.signUpWithEmailAndPassword(
                                name = studentName,
                                email = signUpEmail.trim(),
                                password = signUpPassword,
                                dpPreset = "doctor_male"
                            )
                        },
                        onGoogleSignIn = {
                            viewModel.setAuthenticating(true)
                            viewModel.setAuthError(null)
                            localValidationError = null
                            googleSignInLauncher.launch(googleSignInClient.signInIntent)
                        },
                        onPhoneSignIn = {
                            viewModel.setAuthError(null)
                            localValidationError = null
                            currentAuthMode = AuthMode.PHONE_OTP
                        }
                    )
                }

                AuthMode.PHONE_OTP -> {
                    AuthPhoneScreen(
                        phone = phoneInput,
                        onPhoneChange = { phoneInput = it },
                        otp = otpInput,
                        onOtpChange = { otpInput = it },
                        phoneOtpSent = phoneOtpSent,
                        isAuthenticating = isAuthenticating,
                        errorMessage = authError,
                        onBack = { currentAuthMode = AuthMode.LOGIN },
                        onSendCode = {
                            (context as? Activity)?.let { act ->
                                viewModel.sendPhoneOtp(act, phoneInput.trim(), "Medical Student")
                            }
                        },
                        onVerifyOtp = {
                            viewModel.verifyPhoneOtp(
                                phoneNumber = phoneInput.trim(),
                                otp = otpInput.trim(),
                                name = "Medical Student"
                            )
                        }
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 1. SPLASH SCREEN (Left screen from design)
// -------------------------------------------------------------------------------------------------
@Composable
private fun AuthSplashScreen(
    onSplashComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A3EB1))
            .clickable { onSplashComplete() }
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Brand Accent Dots
            MedPulseBrandDots(dotSize = 14.dp, spacing = 8.dp)

            Spacer(modifier = Modifier.height(16.dp))

            // App Name
            Text(
                text = "MedPulse",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Your College, Organized.",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = Color.White.copy(alpha = 0.85f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Cloud Data Protection Badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Cloud-Protected Academic Records",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 2. LOGIN TO YOUR ACCOUNT SCREEN (Middle screen from design)
// -------------------------------------------------------------------------------------------------
@Composable
private fun AuthLoginScreen(
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePasswordVisibility: () -> Unit,
    isAuthenticating: Boolean,
    errorMessage: String?,
    onSignIn: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onPhoneSignIn: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Centered MedPulse Logo with colored dots
            MedPulseBrandHeader()

            Spacer(modifier = Modifier.height(28.dp))

            // Screen Heading
            Text(
                text = "Login to your Account",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = Color(0xFF1E293B),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Email Input Field
            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                placeholder = { Text("Email", color = Color(0xFF94A3B8)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "Email",
                        tint = Color(0xFF64748B)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color(0xFF1A3EB1),
                    unfocusedBorderColor = Color(0xFFE2E8F0)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_email_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password Input Field
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                placeholder = { Text("Password", color = Color(0xFF94A3B8)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Password",
                        tint = Color(0xFF64748B)
                    )
                },
                trailingIcon = {
                    IconButton(onClick = onTogglePasswordVisibility) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle password visibility",
                            tint = Color(0xFF64748B)
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onSignIn() }
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color(0xFF1A3EB1),
                    unfocusedBorderColor = Color(0xFFE2E8F0)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_password_input")
            )

            // Error display
            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Sign In Button
            Button(
                onClick = onSignIn,
                enabled = !isAuthenticating,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1A3EB1),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .shadow(elevation = 3.dp, shape = RoundedCornerShape(12.dp))
                    .testTag("auth_sign_in_button")
            ) {
                if (isAuthenticating) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(
                        text = "Sign In",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Or sign in with divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = Color(0xFFE2E8F0)
                )
                Text(
                    text = " - Or sign in with - ",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = Color(0xFFE2E8F0)
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Social Login Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Google
                SocialLoginCard(
                    testTag = "auth_google_button",
                    onClick = onGoogleSignIn
                ) {
                    GoogleIcon(modifier = Modifier.size(24.dp))
                }

                Spacer(modifier = Modifier.width(20.dp))

                // Mobile OTP
                SocialLoginCard(
                    testTag = "auth_phone_button",
                    onClick = onPhoneSignIn
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "Sign in with phone",
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                // Medical Cloud ID
                SocialLoginCard(
                    testTag = "auth_quick_cloud_button",
                    onClick = onGoogleSignIn
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = "Google Cloud Backup",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Cloud Data Protection Guarantee
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFEFF6FF),
                border = BorderStroke(1.dp, Color(0xFFBFDBFE).copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = Color(0xFF1A3EB1),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Your timetable, attendance & notes auto-sync to your account so you never lose data.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF1E3A8A)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Footer: Don't have an account? Sign up
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Don't have an account? ",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF64748B)
            )
            Text(
                text = "Sign up",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A3EB1)
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onNavigateToSignUp() }
                    .padding(4.dp)
                    .testTag("auth_switch_to_signup")
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 3. CREATE YOUR ACCOUNT SCREEN (Right screen from design)
// -------------------------------------------------------------------------------------------------
@Composable
private fun AuthSignUpScreen(
    name: String,
    onNameChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePasswordVisibility: () -> Unit,
    confirmPasswordVisible: Boolean,
    onToggleConfirmPasswordVisibility: () -> Unit,
    isAuthenticating: Boolean,
    errorMessage: String?,
    onBackToLogin: () -> Unit,
    onSignUp: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onPhoneSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Top App Bar with back button and centered logo
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onBackToLogin,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .testTag("auth_signup_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Login",
                        tint = Color(0xFF1E293B)
                    )
                }

                MedPulseBrandHeader()
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Screen Heading
            Text(
                text = "Create your Account",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = Color(0xFF1E293B),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Full Name Input
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                placeholder = { Text("Full Name", color = Color(0xFF94A3B8)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Full Name",
                        tint = Color(0xFF64748B)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color(0xFF1A3EB1),
                    unfocusedBorderColor = Color(0xFFE2E8F0)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_name_input")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Email Input
            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                placeholder = { Text("Email", color = Color(0xFF94A3B8)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "Email",
                        tint = Color(0xFF64748B)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color(0xFF1A3EB1),
                    unfocusedBorderColor = Color(0xFFE2E8F0)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_signup_email_input")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Password Input
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                placeholder = { Text("Password", color = Color(0xFF94A3B8)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Password",
                        tint = Color(0xFF64748B)
                    )
                },
                trailingIcon = {
                    IconButton(onClick = onTogglePasswordVisibility) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle password visibility",
                            tint = Color(0xFF64748B)
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color(0xFF1A3EB1),
                    unfocusedBorderColor = Color(0xFFE2E8F0)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_signup_password_input")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Confirm Password Input
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = onConfirmPasswordChange,
                placeholder = { Text("Confirm Password", color = Color(0xFF94A3B8)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Confirm Password",
                        tint = Color(0xFF64748B)
                    )
                },
                trailingIcon = {
                    IconButton(onClick = onToggleConfirmPasswordVisibility) {
                        Icon(
                            imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle confirm password visibility",
                            tint = Color(0xFF64748B)
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onSignUp() }
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color(0xFF1A3EB1),
                    unfocusedBorderColor = Color(0xFFE2E8F0)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_signup_confirm_password_input")
            )

            // Error display
            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Primary Sign Up Button
            Button(
                onClick = onSignUp,
                enabled = !isAuthenticating,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1A3EB1),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .shadow(elevation = 3.dp, shape = RoundedCornerShape(12.dp))
                    .testTag("auth_sign_up_button")
            ) {
                if (isAuthenticating) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(
                        text = "Sign up",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Or sign up with divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = Color(0xFFE2E8F0)
                )
                Text(
                    text = " - Or sign up with - ",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = Color(0xFFE2E8F0)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Social Sign up row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SocialLoginCard(
                    testTag = "auth_signup_google_button",
                    onClick = onGoogleSignIn
                ) {
                    GoogleIcon(modifier = Modifier.size(24.dp))
                }

                Spacer(modifier = Modifier.width(20.dp))

                SocialLoginCard(
                    testTag = "auth_signup_phone_button",
                    onClick = onPhoneSignIn
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "Sign up with phone",
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                SocialLoginCard(
                    testTag = "auth_signup_cloud_button",
                    onClick = onGoogleSignIn
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = "Cloud backup account",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Footer: Already have an account? Sign In
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Already have an account? ",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF64748B)
            )
            Text(
                text = "Sign In",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A3EB1)
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onBackToLogin() }
                    .padding(4.dp)
                    .testTag("auth_switch_to_signin")
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 4. PHONE OTP AUTH SCREEN
// -------------------------------------------------------------------------------------------------
@Composable
private fun AuthPhoneScreen(
    phone: String,
    onPhoneChange: (String) -> Unit,
    otp: String,
    onOtpChange: (String) -> Unit,
    phoneOtpSent: Boolean,
    isAuthenticating: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onSendCode: () -> Unit,
    onVerifyOtp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF1E293B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            MedPulseBrandHeader()

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (!phoneOtpSent) "Mobile Verification" else "Enter OTP Code",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF1E293B)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (!phoneOtpSent)
                    "Enter your phone number with country code (e.g. +91) to sign in securely."
                else
                    "Enter the 6-digit verification code sent via SMS to $phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (!phoneOtpSent) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = onPhoneChange,
                    placeholder = { Text("+91 98765 43210") },
                    leadingIcon = {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF64748B))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color(0xFF1A3EB1),
                        unfocusedBorderColor = Color(0xFFE2E8F0)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onSendCode,
                    enabled = phone.isNotBlank() && !isAuthenticating,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A3EB1)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (isAuthenticating) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Text("Send Verification Code", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                OutlinedTextField(
                    value = otp,
                    onValueChange = onOtpChange,
                    placeholder = { Text("6-digit Code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color(0xFF1A3EB1),
                        unfocusedBorderColor = Color(0xFFE2E8F0)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onVerifyOtp,
                    enabled = otp.length >= 6 && !isAuthenticating,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A3EB1)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (isAuthenticating) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Text("Verify & Sign In", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

// -------------------------------------------------------------------------------------------------
// BRAND COMPONENTS
// -------------------------------------------------------------------------------------------------

@Composable
fun MedPulseBrandHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MedPulseBrandDots(dotSize = 10.dp, spacing = 6.dp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "MedPulse",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            ),
            color = Color(0xFF1E293B)
        )
    }
}

@Composable
fun MedPulseBrandDots(
    dotSize: androidx.compose.ui.unit.Dp = 10.dp,
    spacing: androidx.compose.ui.unit.Dp = 6.dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Dot 1: Cyan
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(Color(0xFF38BDF8))
        )
        // Dot 2: Royal Blue
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(Color(0xFF2563EB))
        )
        // Dot 3: Lime Green
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(Color(0xFF10B981))
        )
        // Dot 4: Warm Gold
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(Color(0xFFF59E0B))
        )
    }
}

@Composable
private fun SocialLoginCard(
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 1.dp,
        modifier = modifier
            .size(56.dp)
            .testTag(testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            content()
        }
    }
}

@Composable
private fun GoogleIcon(modifier: Modifier = Modifier) {
    // Branded multi-colored Google 'G' icon representation
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "G",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Black,
                fontSize = 20.sp
            ),
            color = Color(0xFF4285F4)
        )
    }
}
