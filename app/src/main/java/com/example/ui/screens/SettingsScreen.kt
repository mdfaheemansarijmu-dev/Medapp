package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.data.model.MedicalCourse
import com.example.data.university.CollegeCategory
import com.example.data.university.CollegeInfo
import com.example.data.university.UniversityDirectory
import com.example.ui.viewmodel.PlannerViewModel
import com.example.ui.viewmodel.LoginMode
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import android.app.Activity
import android.widget.Toast
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: PlannerViewModel,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }

    val selectedCourse by viewModel.selectedCourse.collectAsStateWithLifecycle()
    var showCourseSelector by remember { mutableStateOf(false) }

    val studentName by viewModel.studentName.collectAsStateWithLifecycle()
    val studentDpUrl by viewModel.studentDpUrl.collectAsStateWithLifecycle()
    val studentDpPreset by viewModel.studentDpPreset.collectAsStateWithLifecycle()
    val studentEmail by viewModel.studentEmail.collectAsStateWithLifecycle()
    val googleUserId by viewModel.googleUserId.collectAsStateWithLifecycle()
    val loginMode by viewModel.loginMode.collectAsStateWithLifecycle()
    val studentCollege by viewModel.studentCollege.collectAsStateWithLifecycle()
    val studentYear by viewModel.studentYear.collectAsStateWithLifecycle()
    val studentAdmissionYear by viewModel.studentAdmissionYear.collectAsStateWithLifecycle()
    val studentBatch by viewModel.studentBatch.collectAsStateWithLifecycle()
    val studentSemester by viewModel.studentSemester.collectAsStateWithLifecycle()

    var showDpDialog by remember { mutableStateOf(false) }
    var showPhotoSourceChooser by remember { mutableStateOf(false) }
    var showGoogleChooserInSettings by remember { mutableStateOf(false) }
    var tempName by remember(studentName) { mutableStateOf(studentName) }

    // Student Info Center Dialog States
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showCollegeSelectorDialog by remember { mutableStateOf(false) }
    var showYearBatchDialog by remember { mutableStateOf(false) }
    var showAdmissionYearDialog by remember { mutableStateOf(false) }
    var showPostBatchNoticeDialog by remember { mutableStateOf(false) }
    var newNoticeTitle by remember { mutableStateOf("") }
    var newNoticeMessage by remember { mutableStateOf("") }
    var newNoticeCategory by remember { mutableStateOf("batch_notice") }
    var newNoticeUrgent by remember { mutableStateOf(false) }

    var editNameInput by remember(studentName) { mutableStateOf(studentName) }
    var collegeSearchQuery by remember { mutableStateOf("") }
    var customCollegeInput by remember { mutableStateOf("") }
    var isEnteringCustomCollege by remember { mutableStateOf(false) }
    var editYearInput by remember(studentYear) { mutableStateOf(studentYear) }
    var editBatchInput by remember(studentBatch) { mutableStateOf(studentBatch) }
    var editSemesterInput by remember(studentSemester) { mutableStateOf(studentSemester) }
    var editAdmissionYearInput by remember(studentAdmissionYear) { mutableStateOf(studentAdmissionYear.toString()) }

    val context = LocalContext.current
    val tempUri = remember {
        val tempFile = java.io.File(context.cacheDir, "temp_profile_capture.jpg")
        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            tempFile
        )
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            viewModel.updateProfilePictureFromUri(tempUri)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.updateProfilePictureFromUri(uri)
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.updateProfilePictureFromUri(uri)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setNotificationsEnabled(true)
        } else {
            viewModel.setNotificationsEnabled(false)
        }
    }

    if (showPhotoSourceChooser) {
        ProfilePictureSourceDialog(
            onDismiss = { showPhotoSourceChooser = false },
            onCameraSelect = {
                showPhotoSourceChooser = false
                cameraLauncher.launch(tempUri)
            },
            onGallerySelect = {
                showPhotoSourceChooser = false
                galleryLauncher.launch("image/*")
            },
            onFilesSelect = {
                showPhotoSourceChooser = false
                fileLauncher.launch("image/*")
            }
        )
    }

    val isAuthenticating by viewModel.isAuthenticating.collectAsStateWithLifecycle()

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
                val name = account.displayName ?: "Google User"
                val email = account.email ?: ""
                val photoUrl = account.photoUrl?.toString() ?: ""
                val id = account.id ?: ""
                val idToken = account.idToken
                
                viewModel.signInWithGoogle(name, email, photoUrl, id, idToken)
                Toast.makeText(context, "Successfully signed in with Google: $email", Toast.LENGTH_SHORT).show()
            } catch (e: ApiException) {
                val errorMsg = "Google sign-in failed: ${e.message ?: "Code ${e.statusCode}"}"
                viewModel.setAuthError(errorMsg)
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            }
        } else if (result.resultCode == Activity.RESULT_CANCELED) {
            Toast.makeText(context, "Google sign-in cancelled", Toast.LENGTH_SHORT).show()
        } else {
            val errorMsg = "Google sign-in failed (code: ${result.resultCode})"
            viewModel.setAuthError(errorMsg)
            Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
        }
    }

    fun isPlayServicesAvailable(context: android.content.Context): Boolean {
        val googleApiAvailability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
        return resultCode == com.google.android.gms.common.ConnectionResult.SUCCESS
    }

    if (showDpDialog) {
        var inputDpUrl by remember { mutableStateOf(studentDpUrl) }
        var selectedPreset by remember { mutableStateOf(studentDpPreset) }

        AlertDialog(
            onDismissRequest = { showDpDialog = false },
            title = {
                Text(
                    text = "Customize Profile Picture",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Choose a preset medical avatar or paste a custom image URL below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Presets Grid
                    Text(
                        text = "Medical Avatars",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    val presets = listOf(
                        "doctor_male" to "Male Doctor",
                        "doctor_female" to "Female Doctor",
                        "nurse" to "Nurse",
                        "surgeon" to "Surgeon",
                        "scientist" to "Researcher",
                        "stethoscope" to "Stethoscope"
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        presets.chunked(3).forEach { rowPresets ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowPresets.forEach { (presetKey, presetLabel) ->
                                    val isSelected = selectedPreset == presetKey && inputDpUrl.isBlank()
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                selectedPreset = presetKey
                                                inputDpUrl = "" // clear custom URL when selecting preset
                                            }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                                        else MaterialTheme.colorScheme.surfaceVariant
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                val icon = when (presetKey) {
                                                    "doctor_male" -> Icons.Default.Face
                                                    "doctor_female" -> Icons.Default.Face
                                                    "nurse" -> Icons.Default.Healing
                                                    "surgeon" -> Icons.Default.MedicalServices
                                                    "scientist" -> Icons.Default.Science
                                                    "stethoscope" -> Icons.Default.MedicalServices
                                                    else -> Icons.Default.Person
                                                }
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = presetLabel,
                                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = presetLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Custom URL
                    Text(
                        text = "Custom Image URL",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = inputDpUrl,
                        onValueChange = { inputDpUrl = it },
                        placeholder = { Text("https://example.com/avatar.jpg") },
                        singleLine = true,
                        label = { Text("Profile Photo Link") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(Icons.Default.Link, contentDescription = null)
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateStudentProfile(studentName, inputDpUrl, selectedPreset)
                        showDpDialog = false
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = { viewModel.navigateTo(com.example.ui.viewmodel.Screen.Dashboard) },
                    modifier = Modifier.testTag("settings_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Home",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "System Settings",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Configure specialization profiles, sync services and alerts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Settings -> Account Group
            SettingsSectionHeader(title = "Account Settings")

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Clickable avatar to edit
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .clickable { showPhotoSourceChooser = true }
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            UserProfileAvatar(
                                studentDpUrl = studentDpUrl,
                                studentDpPreset = studentDpPreset,
                                studentName = studentName,
                                size = 72.dp
                            )
                            // A small edit camera overlay icon at the bottom right/center
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Profile Picture",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            // Name display/input
                            OutlinedTextField(
                                value = tempName,
                                onValueChange = {
                                    tempName = it
                                    viewModel.updateStudentProfile(it, studentDpUrl, studentDpPreset)
                                },
                                label = { Text("Name") },
                                placeholder = { Text("Med Student") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("student_name_input"),
                                leadingIcon = {
                                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                            )

                            if (loginMode == LoginMode.GOOGLE) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = studentEmail.ifBlank { "mdfaheemansarijmu@gmail.com" },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                                if (googleUserId.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "ID: $googleUserId",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Change Photo button
                        OutlinedButton(
                            onClick = { showPhotoSourceChooser = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).testTag("change_photo_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Change Photo", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                        }

                        // Auth Action button (Sign In vs Sign Out)
                        if (loginMode == LoginMode.GOOGLE) {
                            Button(
                                onClick = { viewModel.signOut() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).testTag("sign_out_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Logout,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sign Out", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (!isPlayServicesAvailable(context)) {
                                        Toast.makeText(context, "Google Play Services are unavailable on this device.", Toast.LENGTH_LONG).show()
                                    } else if (!isAuthenticating) {
                                        viewModel.setAuthenticating(true)
                                        googleSignInClient.signOut().addOnCompleteListener {
                                            googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                        }
                                    }
                                },
                                enabled = !isAuthenticating,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).testTag("sign_in_google_btn")
                            ) {
                                if (isAuthenticating) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Connecting...", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Login,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Sign In with Google", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Cloud Synchronization Group
            SettingsSectionHeader(title = "Cloud Synchronization")
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth().testTag("settings_cloud_sync_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val isCloudSyncActive = loginMode == LoginMode.GOOGLE || loginMode == LoginMode.FIREBASE
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isCloudSyncActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isCloudSyncActive) Icons.Default.Cloud else Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = if (isCloudSyncActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isCloudSyncActive) "Firebase Cloud Sync Active" else "Cloud Sync Pending",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isCloudSyncActive) "Your schedule, attendance, and notes are securely backed up to Firebase Firestore in real-time." else "Please sign in to MedPulse account to protect your data.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (loginMode == LoginMode.GOOGLE || loginMode == LoginMode.FIREBASE) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Divider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Back up button
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        isBackingUp = true
                                        try {
                                            viewModel.syncDataToFirebase()
                                            Toast.makeText(context, "Data backed up to Firebase successfully!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Backup failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isBackingUp = false
                                        }
                                    }
                                },
                                enabled = !isBackingUp && !isRestoring,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).testTag("settings_backup_now_btn")
                            ) {
                                if (isBackingUp) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Back Up", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                }
                            }

                            // Restore button
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        isRestoring = true
                                        viewModel.restoreDataFromFirebase { success ->
                                            isRestoring = false
                                            if (success) {
                                                Toast.makeText(context, "Data successfully restored from Firebase!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Failed to restore. No cloud backup found or network error.", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                },
                                enabled = !isBackingUp && !isRestoring,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).testTag("settings_restore_now_btn")
                            ) {
                                if (isRestoring) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Restore", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Student Info Center Group
            SettingsSectionHeader(title = "Student Info Center")

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    SettingsRow(
                        title = "Full Name",
                        subtitle = studentName.ifBlank { "Med Student" },
                        icon = Icons.Default.Person,
                        color = MaterialTheme.colorScheme.primary,
                        onClick = {
                            editNameInput = studentName
                            showEditNameDialog = true
                        },
                        tag = "settings_edit_name"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(
                        title = "College / University",
                        subtitle = studentCollege.ifBlank { "AIIMS New Delhi" },
                        icon = Icons.Default.School,
                        color = MaterialTheme.colorScheme.secondary,
                        onClick = {
                            collegeSearchQuery = ""
                            customCollegeInput = ""
                            isEnteringCustomCollege = false
                            showCollegeSelectorDialog = true
                        },
                        tag = "settings_college_info"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(
                        title = "Course & Specialization",
                        subtitle = selectedCourse?.displayName ?: "MBBS (Bachelor of Medicine, Bachelor of Surgery)",
                        icon = Icons.Default.MedicalServices,
                        color = MaterialTheme.colorScheme.primary,
                        onClick = { showCourseSelector = true },
                        tag = "settings_change_course"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(
                        title = "Academic Year, Semester & Batch",
                        subtitle = "${studentYear.ifBlank { "1st Year" }} • ${studentSemester.ifBlank { "Semester 1" }} • ${studentBatch.ifBlank { "Batch A" }}",
                        icon = Icons.Default.Groups,
                        color = MaterialTheme.colorScheme.tertiary,
                        onClick = {
                            editYearInput = studentYear.ifBlank { "1st Year" }
                            editSemesterInput = studentSemester.ifBlank { "Semester 1" }
                            editBatchInput = studentBatch.ifBlank { "Batch A" }
                            showYearBatchDialog = true
                        },
                        tag = "settings_year_batch_info"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(
                        title = "Year of Admission (Batch Sync)",
                        subtitle = "Admitted in $studentAdmissionYear • Batch assignments active",
                        icon = Icons.Default.Event,
                        color = MaterialTheme.colorScheme.primary,
                        onClick = {
                            editAdmissionYearInput = studentAdmissionYear.toString()
                            showAdmissionYearDialog = true
                        },
                        tag = "settings_admission_year_info"
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // About & Updates Group
            SettingsSectionHeader(title = "About & Updates")

            val updateCheckInProgress by viewModel.updateCheckInProgress.collectAsStateWithLifecycle()
            val lastCheckedTime by viewModel.lastCheckedTime.collectAsStateWithLifecycle()
            val cachedConfig by viewModel.cachedUpdateConfig.collectAsStateWithLifecycle()
            val updateResult by viewModel.updateResult.collectAsStateWithLifecycle()
            val downloadProgress by viewModel.updateDownloadProgress.collectAsStateWithLifecycle()
            val downloadState by viewModel.updateDownloadState.collectAsStateWithLifecycle()

            val lastCheckedStr = if (lastCheckedTime == 0L) {
                "Never"
            } else {
                val sdf = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
                sdf.format(Date(lastCheckedTime))
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // About Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                text = "MedPulse Student Planner",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Your comprehensive AI-assisted companion",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Version & Status Grid
                    val currentVersionStr = remember {
                        try {
                            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                            pInfo.versionName?.takeIf { it.isNotBlank() } ?: BuildConfig.VERSION_NAME
                        } catch (_: Exception) {
                            BuildConfig.VERSION_NAME
                        }
                    }
                    val cleanCurrentVersion = currentVersionStr.trim().removePrefix("v").removePrefix("V")
                    val cleanLatestVersion = cachedConfig?.latestVersion?.trim()?.removePrefix("v")?.removePrefix("V") ?: cleanCurrentVersion

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Current Version", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("v$cleanCurrentVersion", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Latest Version", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "v$cleanLatestVersion",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (cachedConfig != null && cleanLatestVersion != cleanCurrentVersion) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Last Checked", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(lastCheckedStr, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    // GitHub Release Repository Configuration
                    val gitHubOwner by viewModel.gitHubOwner.collectAsStateWithLifecycle()
                    val gitHubRepo by viewModel.gitHubRepo.collectAsStateWithLifecycle()
                    var isEditingGitHub by remember { mutableStateOf(false) }
                    var ownerInput by remember(gitHubOwner) { mutableStateOf(gitHubOwner) }
                    var repoInput by remember(gitHubRepo) { mutableStateOf(gitHubRepo) }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "GitHub Repository",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = {
                                    if (isEditingGitHub) {
                                        viewModel.updateGitHubRepoConfig(ownerInput, repoInput)
                                        isEditingGitHub = false
                                    } else {
                                        isEditingGitHub = true
                                    }
                                },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text(
                                    text = if (isEditingGitHub) "Save" else "Edit",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        if (isEditingGitHub) {
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = ownerInput,
                                onValueChange = { ownerInput = it },
                                label = { Text("GitHub Username / Org") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth().testTag("github_owner_input")
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = repoInput,
                                onValueChange = { repoInput = it },
                                label = { Text("Repository Name") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth().testTag("github_repo_input")
                            )
                        } else {
                            Text(
                                text = "$gitHubOwner/$gitHubRepo",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Check for Updates Button
                    Button(
                        onClick = { viewModel.checkForUpdates(silent = false) },
                        enabled = !updateCheckInProgress,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("settings_check_updates_btn")
                    ) {
                        if (updateCheckInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Checking...", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Check Now", style = MaterialTheme.typography.bodyMedium)
                        }
                    }



                    // Update result feedback message
                    updateResult?.let { result ->
                        Spacer(modifier = Modifier.height(14.dp))
                        when (result) {
                            is com.example.network.AppUpdateResult.UpToDate -> {
                                Text(
                                    text = "✅ Your application is fully up to date.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            is com.example.network.AppUpdateResult.UpdateAvailable -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "🎉 New version v${result.config.latestVersion} is available!",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    if (downloadState != null) {
                                        Text(
                                            text = downloadState ?: "Downloading...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        downloadProgress?.let { progress ->
                                            if (progress >= 0f) {
                                                LinearProgressIndicator(
                                                    progress = progress,
                                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    trackColor = MaterialTheme.colorScheme.primaryContainer
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "${(progress * 100).toInt()}%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else {
                                                LinearProgressIndicator(
                                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    trackColor = MaterialTheme.colorScheme.primaryContainer
                                                )
                                            }
                                        }

                                        val currentProgress = downloadProgress
                                        val isComplete = (currentProgress != null && currentProgress >= 1f) ||
                                                (downloadState?.contains("complete", ignoreCase = true) == true) ||
                                                (downloadState?.contains("ready", ignoreCase = true) == true)
                                        val isFailed = downloadState?.contains("failed", ignoreCase = true) == true ||
                                                downloadState?.contains("error", ignoreCase = true) == true

                                        if (isComplete) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Button(
                                                onClick = { viewModel.installDownloadedUpdate() },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("install_update_btn")
                                            ) {
                                                Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Install Update", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                            }
                                        } else if (isFailed) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Button(
                                                onClick = { viewModel.downloadAndInstallUpdate(result.config.apkUrl) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.error,
                                                    contentColor = MaterialTheme.colorScheme.onError
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("retry_update_btn")
                                            ) {
                                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Retry Download", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                            }
                                        }
                                    } else {
                                        Button(
                                            onClick = { viewModel.downloadAndInstallUpdate(result.config.apkUrl) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("download_update_btn")
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Download & Install Update", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                            is com.example.network.AppUpdateResult.Error -> {
                                Text(
                                    text = "⚠️ ${result.message}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                                )
                            }
                            else -> {}
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Update History / Release Notes
                    Text(
                        text = "Update History",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Dynamic release notes if available, otherwise fallback to static
                    val displayNotes = cachedConfig?.let {
                        listOf(
                            it.latestVersion to it.releaseNotes.split("\n")
                        )
                    } ?: listOf(
                        "1.0.1" to listOf(
                            "AI timetable import",
                            "Dashboard synchronization",
                            "Profile improvements",
                            "Faster performance",
                            "Bug fixes"
                        )
                    )

                    displayNotes.forEach { (version, notes) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "Version $version",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            notes.forEach { note ->
                                if (note.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 2.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text("•", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = note.trim().removePrefix("•").trim(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Attendance & Academics Group
            SettingsSectionHeader(title = "Attendance & Academics")

            val attendanceTarget by viewModel.attendanceTarget.collectAsStateWithLifecycle()
            var showTargetDialogInSettings by remember { mutableStateOf(false) }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTargetDialogInSettings = true }
                    .testTag("settings_attendance_target_card")
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
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Attendance Target",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Configured requirement: $attendanceTarget%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "$attendanceTarget%",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (showTargetDialogInSettings) {
                AttendanceTargetDialog(
                    currentTarget = attendanceTarget,
                    onDismiss = { showTargetDialogInSettings = false },
                    onSave = { newTarget ->
                        viewModel.setAttendanceTarget(newTarget)
                        showTargetDialogInSettings = false
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Display Appearance Group
            SettingsSectionHeader(title = "Display Appearance")

            val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
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
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dark Mode Theme",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isDarkTheme) "Currently using Dark theme" else "Currently using Light theme",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = { viewModel.setDarkTheme(it) },
                        modifier = Modifier.testTag("theme_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Notifications & Alerts Group
            SettingsSectionHeader(title = "Notifications & Alerts")

            val areNotificationsEnabled by viewModel.areNotificationsEnabled.collectAsStateWithLifecycle()
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
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
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (areNotificationsEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Push & Browser Notifications",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (areNotificationsEnabled) "Enabled for tasks & lectures due in < 24h" else "Notifications are muted",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = areNotificationsEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    val hasPermission = context.checkSelfPermission(
                                        android.Manifest.permission.POST_NOTIFICATIONS
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        viewModel.setNotificationsEnabled(true)
                                    } else {
                                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                } else {
                                    viewModel.setNotificationsEnabled(true)
                                }
                            } else {
                                viewModel.setNotificationsEnabled(false)
                            }
                        },
                        modifier = Modifier.testTag("notifications_toggle_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Shared Batch Notification System Card
            val isBatchListening by viewModel.isBatchSyncListening.collectAsStateWithLifecycle()
            val batchSyncStatus by viewModel.batchSyncStatus.collectAsStateWithLifecycle()
            val activeBatchKey by viewModel.activeBatchKey.collectAsStateWithLifecycle()
            val sharedNotices by viewModel.sharedBatchNotices.collectAsStateWithLifecycle()

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth().testTag("shared_batch_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Batch Peer Channel & Auto-Sync",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Live noticeboard for ${studentBatch.ifBlank { "Batch A" }} (Class of $studentAdmissionYear)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isBatchListening) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = if (isBatchListening) "● LIVE" else "○ IDLE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isBatchListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Status: $batchSyncStatus\nChannel: $activeBatchKey",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.startBatchNotificationSync() },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("reconnect_batch_sync_btn")
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Refresh Feed", style = MaterialTheme.typography.labelMedium)
                        }

                        Button(
                            onClick = {
                                newNoticeTitle = ""
                                newNoticeMessage = ""
                                newNoticeCategory = "batch_notice"
                                newNoticeUrgent = false
                                showPostBatchNoticeDialog = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("post_batch_notice_btn")
                        ) {
                            Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Broadcast", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sync Database Group
            SettingsSectionHeader(title = "Local Database Persistence")

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    SettingsRow(
                        title = "Reset Specialized Schedule",
                        subtitle = "Overwrites current timetable classes with course defaults",
                        icon = Icons.Default.RestartAlt,
                        color = MaterialTheme.colorScheme.tertiary,
                        onClick = {
                            selectedCourse?.let { viewModel.selectCourse(it) }
                        },
                        tag = "settings_reset_schedule"
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp))

                    SettingsRow(
                        title = "Erase App Database",
                        subtitle = "Clears all local assignments, assessments, study goals, and chat logs",
                        icon = Icons.Default.DeleteForever,
                        color = MaterialTheme.colorScheme.error,
                        onClick = { viewModel.resetWholeApp() },
                        tag = "settings_erase_database"
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Spacer(modifier = Modifier.height(80.dp)) // Padding for bottom navbar
        }

        // Drop-down selector for specialty course
        if (showCourseSelector) {
            AlertDialog(
                onDismissRequest = { showCourseSelector = false },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showCourseSelector = false }) { Text("Dismiss") }
                },
                title = { Text("Choose Specialization", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MedicalCourse.values().forEach { course ->
                            Surface(
                                color = if (selectedCourse == course) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectCourse(course)
                                        showCourseSelector = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.School,
                                        contentDescription = null,
                                        tint = if (selectedCourse == course) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = course.displayName,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (selectedCourse == course) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }

        // Student Info Center Dialogs
        // 1. Edit Name Dialog
        if (showEditNameDialog) {
            AlertDialog(
                onDismissRequest = { showEditNameDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        "Edit Student Name",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Enter your full name as you would like it to appear across your academic records and timetable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = editNameInput,
                            onValueChange = { editNameInput = it },
                            label = { Text("Student Name") },
                            placeholder = { Text("e.g. Dr. Alex Smith") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("edit_student_name_dialog_field")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val trimmed = editNameInput.trim()
                            if (trimmed.isNotBlank()) {
                                viewModel.saveUserProfile(
                                    name = trimmed,
                                    college = studentCollege.ifBlank { "AIIMS New Delhi" },
                                    course = selectedCourse?.displayName ?: "MBBS",
                                    year = studentYear.ifBlank { "1st Year" },
                                    semester = studentSemester.ifBlank { "Semester 1" },
                                    batch = studentBatch.ifBlank { "Batch A" },
                                    admissionYear = studentAdmissionYear,
                                    currentYear = studentYear.ifBlank { "1st Year" }
                                )
                                tempName = trimmed
                            }
                            showEditNameDialog = false
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("save_student_name_btn")
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showEditNameDialog = false },
                        modifier = Modifier.testTag("cancel_edit_student_name_btn")
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // 2. Edit College/University Dialog
        if (showCollegeSelectorDialog) {
            val allColleges = remember { UniversityDirectory.colleges }
            val filteredColleges = remember(collegeSearchQuery) {
                if (collegeSearchQuery.isBlank()) allColleges
                else allColleges.filter {
                    it.name.contains(collegeSearchQuery, ignoreCase = true) ||
                    it.shortName.contains(collegeSearchQuery, ignoreCase = true) ||
                    it.state.contains(collegeSearchQuery, ignoreCase = true)
                }
            }

            AlertDialog(
                onDismissRequest = { showCollegeSelectorDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        "Select College / University",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = collegeSearchQuery,
                            onValueChange = { collegeSearchQuery = it },
                            label = { Text("Search College") },
                            placeholder = { Text("Search by name, AIIMS, state...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("search_college_field")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (isEnteringCustomCollege) {
                            OutlinedTextField(
                                value = customCollegeInput,
                                onValueChange = { customCollegeInput = it },
                                label = { Text("Enter Custom Institution Name") },
                                placeholder = { Text("e.g. Government Medical College") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("custom_college_name_field")
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = { isEnteringCustomCollege = false },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Back to Directory List")
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { isEnteringCustomCollege = true },
                                    modifier = Modifier.testTag("other_college_btn")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("My College Not Listed")
                                }
                            }

                            Box(modifier = Modifier.weight(1f, fill = false).heightIn(max = 280.dp)) {
                                androidx.compose.foundation.lazy.LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(filteredColleges.size) { index ->
                                        val college = filteredColleges[index]
                                        val isSelected = studentCollege.equals(college.name, ignoreCase = true)
                                        Surface(
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.saveUserProfile(
                                                        name = studentName.ifBlank { "Med Student" },
                                                        college = college.name,
                                                        course = selectedCourse?.displayName ?: "MBBS",
                                                        year = studentYear.ifBlank { "1st Year" },
                                                        semester = studentSemester.ifBlank { "Semester 1" },
                                                        batch = studentBatch.ifBlank { "Batch A" },
                                                        admissionYear = studentAdmissionYear,
                                                        currentYear = studentYear.ifBlank { "1st Year" }
                                                    )
                                                    showCollegeSelectorDialog = false
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = college.name,
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "${college.state} • ${college.category.displayName}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (isSelected) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    if (isEnteringCustomCollege) {
                        Button(
                            onClick = {
                                val trimmed = customCollegeInput.trim()
                                if (trimmed.isNotBlank()) {
                                    viewModel.saveUserProfile(
                                        name = studentName.ifBlank { "Med Student" },
                                        college = trimmed,
                                        course = selectedCourse?.displayName ?: "MBBS",
                                        year = studentYear.ifBlank { "1st Year" },
                                        semester = studentSemester.ifBlank { "Semester 1" },
                                        batch = studentBatch.ifBlank { "Batch A" },
                                        admissionYear = studentAdmissionYear,
                                        currentYear = studentYear.ifBlank { "1st Year" }
                                    )
                                }
                                showCollegeSelectorDialog = false
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Save College")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCollegeSelectorDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // 3. Edit Academic Year, Semester & Batch Dialog
        if (showYearBatchDialog) {
            val academicYears = listOf("1st Year", "2nd Year", "3rd Year", "4th Year", "Final Year", "Intern")
            val semesters = listOf("Semester 1", "Semester 2", "Semester 3", "Semester 4", "Semester 5", "Semester 6", "Semester 7", "Semester 8", "Semester 9")
            val batches = listOf("Batch A", "Batch B", "Batch C", "Batch D", "Batch 1", "Batch 2")

            AlertDialog(
                onDismissRequest = { showYearBatchDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        "Academic Details",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Academic Year
                        Text(
                            "Academic Year",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            academicYears.take(3).forEach { yr ->
                                FilterChip(
                                    selected = editYearInput == yr,
                                    onClick = { editYearInput = yr },
                                    label = { Text(yr, style = MaterialTheme.typography.labelMedium) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            academicYears.drop(3).forEach { yr ->
                                FilterChip(
                                    selected = editYearInput == yr,
                                    onClick = { editYearInput = yr },
                                    label = { Text(yr, style = MaterialTheme.typography.labelMedium) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Semester
                        Text(
                            "Current Semester",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            semesters.take(3).forEach { sem ->
                                FilterChip(
                                    selected = editSemesterInput == sem,
                                    onClick = { editSemesterInput = sem },
                                    label = { Text(sem.replace("Semester ", "Sem "), style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            semesters.drop(3).take(3).forEach { sem ->
                                FilterChip(
                                    selected = editSemesterInput == sem,
                                    onClick = { editSemesterInput = sem },
                                    label = { Text(sem.replace("Semester ", "Sem "), style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Batch
                        Text(
                            "Practical / Clinical Batch",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            batches.take(3).forEach { b ->
                                FilterChip(
                                    selected = editBatchInput == b,
                                    onClick = { editBatchInput = b },
                                    label = { Text(b, style = MaterialTheme.typography.labelMedium) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            batches.drop(3).forEach { b ->
                                FilterChip(
                                    selected = editBatchInput == b,
                                    onClick = { editBatchInput = b },
                                    label = { Text(b, style = MaterialTheme.typography.labelMedium) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.saveUserProfile(
                                name = studentName.ifBlank { "Med Student" },
                                college = studentCollege.ifBlank { "AIIMS New Delhi" },
                                course = selectedCourse?.displayName ?: "MBBS",
                                year = editYearInput,
                                semester = editSemesterInput,
                                batch = editBatchInput,
                                admissionYear = studentAdmissionYear,
                                currentYear = editYearInput
                            )
                            showYearBatchDialog = false
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("save_year_batch_btn")
                    ) {
                        Text("Save Details")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showYearBatchDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // 4. Edit Admission Year Dialog (Batch Sync)
        if (showAdmissionYearDialog) {
            val admissionYears = (2018..2026).toList()

            AlertDialog(
                onDismissRequest = { showAdmissionYearDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        "Year of Admission",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Your admission year links you with peers in the same batch for auto-syncing shared assignments, tests, and department notices.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Box(modifier = Modifier.heightIn(max = 240.dp)) {
                            androidx.compose.foundation.lazy.LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(admissionYears.size) { idx ->
                                    val yr = admissionYears[idx]
                                    val isSelected = editAdmissionYearInput == yr.toString()
                                    Surface(
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { editAdmissionYearInput = yr.toString() }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Batch of $yr",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val parsedYear = editAdmissionYearInput.toIntOrNull() ?: studentAdmissionYear
                            viewModel.saveUserProfile(
                                name = studentName.ifBlank { "Med Student" },
                                college = studentCollege.ifBlank { "AIIMS New Delhi" },
                                course = selectedCourse?.displayName ?: "MBBS",
                                year = studentYear.ifBlank { "1st Year" },
                                semester = studentSemester.ifBlank { "Semester 1" },
                                batch = studentBatch.ifBlank { "Batch A" },
                                admissionYear = parsedYear,
                                currentYear = studentYear.ifBlank { "1st Year" }
                            )
                            showAdmissionYearDialog = false
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("save_admission_year_btn")
                    ) {
                        Text("Save Year")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAdmissionYearDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Broadcast Notice to Batch Dialog
        if (showPostBatchNoticeDialog) {
            val isPosting by viewModel.isPostingBatchNotice.collectAsStateWithLifecycle()
            val postError by viewModel.batchPostError.collectAsStateWithLifecycle()

            AlertDialog(
                onDismissRequest = { if (!isPosting) showPostBatchNoticeDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        val targetBatchName = studentBatch.ifBlank { "Batch A" }
                        Text(
                            text = "Broadcast to $targetBatchName",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "This notification will immediately synchronize with all students registered under your batch and admission year.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = newNoticeTitle,
                            onValueChange = { newNoticeTitle = it },
                            label = { Text("Title / Subject") },
                            placeholder = { Text("e.g. Pathology Test Room Changed") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("notice_title_input")
                        )

                        OutlinedTextField(
                            value = newNoticeMessage,
                            onValueChange = { newNoticeMessage = it },
                            label = { Text("Notice Details / Instructions") },
                            placeholder = { Text("e.g. Bring lab coat & logbook by 9:00 AM to LT-3") },
                            minLines = 3,
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth().testTag("notice_message_input")
                        )

                        // Category Selector
                        Text(
                            text = "Notice Category",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "batch_notice" to "General",
                                "shared_assignment" to "Assignment",
                                "exam_alert" to "Test/Exam"
                            ).forEach { (catKey, catLabel) ->
                                FilterChip(
                                    selected = newNoticeCategory == catKey,
                                    onClick = { newNoticeCategory = catKey },
                                    label = { Text(catLabel, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Urgent Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = newNoticeUrgent,
                                onCheckedChange = { newNoticeUrgent = it }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Mark as Urgent / Priority Alert",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (newNoticeUrgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (postError != null) {
                            Text(
                                text = "Error: $postError",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newNoticeTitle.isNotBlank() && newNoticeMessage.isNotBlank()) {
                                viewModel.postNoticeToBatch(
                                    title = newNoticeTitle,
                                    message = newNoticeMessage,
                                    category = newNoticeCategory,
                                    urgent = newNoticeUrgent
                                ) { success, err ->
                                    if (success) {
                                        Toast.makeText(context, "Notice broadcasted to ${studentBatch.ifBlank { "Batch A" }}!", Toast.LENGTH_SHORT).show()
                                        showPostBatchNoticeDialog = false
                                    } else {
                                        Toast.makeText(context, "Failed: ${err ?: "Network error"}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Please enter title and message", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isPosting && newNoticeTitle.isNotBlank() && newNoticeMessage.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("submit_batch_notice_btn")
                    ) {
                        if (isPosting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Broadcasting...")
                        } else {
                            Text("Post Notice")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showPostBatchNoticeDialog = false },
                        enabled = !isPosting
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
    )
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun ProfilePictureSourceDialog(
    onDismiss: () -> Unit,
    onCameraSelect: () -> Unit,
    onGallerySelect: () -> Unit,
    onFilesSelect: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "Change Profile Picture",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Select a source to upload your profile picture (JPG, PNG, JPEG, WEBP are supported).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Camera option
                Surface(
                    onClick = onCameraSelect,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().testTag("profile_src_camera")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("📷 Camera", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                    }
                }

                // Gallery option
                Surface(
                    onClick = onGallerySelect,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().testTag("profile_src_gallery")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("🖼️ Gallery", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                    }
                }

                // Files option
                Surface(
                    onClick = onFilesSelect,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().testTag("profile_src_files")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("📁 Files", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("profile_src_cancel")) {
                Text("❌ Cancel", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
    )
}


