package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MedicalCourse
import com.example.data.university.CollegeCategory
import com.example.data.university.CollegeInfo
import com.example.data.university.UniversityCalendarService
import com.example.data.university.UniversityDirectory

@Composable
fun OnboardingUniversityScreen(
    selectedCollege: String,
    selectedCourse: MedicalCourse?,
    onSelectCollege: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterCategory by remember {
        // Auto-select AYUSH filter if course is BAMS or BHMS
        mutableStateOf(
            if (selectedCourse == MedicalCourse.BAMS || selectedCourse == MedicalCourse.BHMS) {
                CollegeCategory.AYUSH
            } else {
                null
            }
        )
    }
    var showCustomInput by remember { mutableStateOf(false) }
    var customCollegeName by remember { mutableStateOf("") }

    val filteredColleges = remember(searchQuery, selectedFilterCategory) {
        UniversityDirectory.filterColleges(
            query = searchQuery,
            category = selectedFilterCategory
        )
    }

    // Tomorrow holiday preview for currently selected institution
    val tomorrowHolidayInfo = remember(selectedCollege) {
        UniversityCalendarService.isTomorrowHoliday(selectedCollege)
    }
    val nextUpcomingHoliday = remember(selectedCollege) {
        UniversityCalendarService.getUpcomingHolidays(selectedCollege, limit = 1).firstOrNull()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Header Bar with Step Indicator "2 of 5"
            OnboardingStepHeader(
                currentStepNumber = 2,
                totalSteps = 5,
                onBack = onBack
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Headline
            Text(
                text = "Select your Medical College",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "MedPulse links directly with your university academic calendar to track upcoming holidays, clinical rotations, and exam leaves.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("college_search_input"),
                placeholder = {
                    Text(
                        text = "Search 40+ medical & AYUSH colleges, AIIMS...",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips (All, AYUSH, Allopathic, AIIMS/INI)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedFilterCategory == null,
                        onClick = { selectedFilterCategory = null },
                        label = { Text("All Institutes") },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilterCategory == CollegeCategory.AYUSH,
                        onClick = {
                            selectedFilterCategory = if (selectedFilterCategory == CollegeCategory.AYUSH) null else CollegeCategory.AYUSH
                        },
                        label = { Text("🌿 AYUSH Colleges") },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFE8F5E9),
                            selectedLabelColor = Color(0xFF1B5E20)
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilterCategory == CollegeCategory.ALLOPATHIC,
                        onClick = {
                            selectedFilterCategory = if (selectedFilterCategory == CollegeCategory.ALLOPATHIC) null else CollegeCategory.ALLOPATHIC
                        },
                        label = { Text("🩺 Allopathic (MBBS)") },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilterCategory == CollegeCategory.CENTRAL_INI,
                        onClick = {
                            selectedFilterCategory = if (selectedFilterCategory == CollegeCategory.CENTRAL_INI) null else CollegeCategory.CENTRAL_INI
                        },
                        label = { Text("🏛️ AIIMS / INIs") },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tomorrow Holiday Alert Banner (if applicable for currently selected college)
            if (tomorrowHolidayInfo.first && tomorrowHolidayInfo.second != null) {
                val hol = tomorrowHolidayInfo.second!!
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFEF3C7),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFF59E0B),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Celebration,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🌴 Tomorrow is a Holiday: ${hol.name}!",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF92400E)
                                )
                            )
                            Text(
                                text = "Official university schedule: Lectures & clinical postings suspended.",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF78350F)),
                                maxLines = 2
                            )
                        }
                    }
                }
            } else if (selectedCollege.isNotBlank() && nextUpcomingHoliday != null) {
                // Verified Official Calendar Sync confirmation
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Next Gazetted Holiday: ${nextUpcomingHoliday.holiday.name} (${nextUpcomingHoliday.relativeLabel})",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Colleges List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredColleges, key = { it.id }) { college ->
                    val isSelected = selectedCollege.equals(college.name, ignoreCase = true) ||
                            selectedCollege.equals(college.shortName, ignoreCase = true)

                    CollegeSelectionCard(
                        college = college,
                        isSelected = isSelected,
                        onClick = {
                            showCustomInput = false
                            onSelectCollege(college.name)
                        }
                    )
                }

                // Custom College Input Option
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedCard(
                        onClick = {
                            showCustomInput = true
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            width = if (showCustomInput) 2.dp else 1.dp,
                            color = if (showCustomInput) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (showCustomInput) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_college_card")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AddBusiness,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Can't find your college? Type custom name",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (showCustomInput) {
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = customCollegeName,
                                    onValueChange = {
                                        customCollegeName = it
                                        if (it.isNotBlank()) {
                                            onSelectCollege(it)
                                        }
                                    },
                                    label = { Text("Enter your College & University Name") },
                                    placeholder = { Text("e.g. Government Medical College, Surat") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("custom_college_text_input")
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Selected Status Note
        if (selectedCollege.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Calendar syncing with ${selectedCollege.take(35)}${if (selectedCollege.length > 35) "..." else ""}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Continue Button
        Button(
            onClick = onContinue,
            enabled = selectedCollege.isNotBlank(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("college_continue_button")
        ) {
            Text(
                text = "Continue to Academic Details",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun CollegeSelectionCard(
    college: CollegeInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("college_item_${college.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Badge Icon
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = when {
                    college.isAyush -> Color(0xFFE8F5E9)
                    college.category == CollegeCategory.CENTRAL_INI -> Color(0xFFEDE7F6)
                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                },
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when {
                            college.isAyush -> Icons.Default.Spa
                            college.category == CollegeCategory.CENTRAL_INI -> Icons.Default.AccountBalance
                            else -> Icons.Default.LocalHospital
                        },
                        contentDescription = null,
                        tint = when {
                            college.isAyush -> Color(0xFF2E7D32)
                            college.category == CollegeCategory.CENTRAL_INI -> Color(0xFF512DA8)
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = college.shortName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (college.isAyush) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFC8E6C9),
                            modifier = Modifier.padding(start = 2.dp)
                        ) {
                            Text(
                                text = "AYUSH",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = Color(0xFF1B5E20)
                                ),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = college.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Affiliated: ${college.universityAffiliation} • ${college.state}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            AnimatedVisibility(
                visible = isSelected,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
