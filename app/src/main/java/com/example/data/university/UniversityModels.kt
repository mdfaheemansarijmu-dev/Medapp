package com.example.data.university

enum class CollegeCategory(val displayName: String) {
    ALLOPATHIC("Allopathic / Modern Medicine (MBBS)"),
    AYUSH("AYUSH (Ayurveda, Homeopathy, Unani, Siddha)"),
    DENTAL("Dental (BDS)"),
    CENTRAL_INI("Institutes of National Importance (AIIMS/INI)"),
    ALLIED_HEALTH("Nursing & Allied Sciences")
}

data class CollegeInfo(
    val id: String,
    val name: String,
    val shortName: String,
    val category: CollegeCategory,
    val stream: String, // e.g. "MBBS", "BAMS (Ayurveda)", "BHMS (Homoeopathy)", "BUMS (Unani)", "BDS"
    val state: String,
    val universityAffiliation: String,
    val campusLocation: String,
    val isAyush: Boolean = false,
    val websiteUrl: String? = null,
    val calendarNoticeUrl: String? = null
)

enum class HolidayType(val displayName: String, val badgeColorHex: String) {
    NATIONAL("National / Gazetted", "#D32F2F"),
    UNIVERSITY_RECESS("Academic & Semester Break", "#1976D2"),
    FESTIVAL("Cultural & Festival Holiday", "#E65100"),
    SPECIAL_OBSERVANCE("Medical & AYUSH Observance", "#388E3C")
}

data class UniversityHoliday(
    val id: String,
    val name: String,
    val date: String, // "yyyy-MM-dd"
    val type: HolidayType,
    val description: String,
    val isClassSuspended: Boolean = true,
    val applicableUniversities: List<String> = listOf("ALL"),
    val streamFilter: String? = null // null means all streams, or "AYUSH", "ALLOPATHIC"
)

data class HolidayCountdown(
    val holiday: UniversityHoliday,
    val daysRemaining: Int, // 0 = Today, 1 = Tomorrow, 2 = Day after tomorrow, etc.
    val relativeLabel: String // "Today", "Tomorrow", "In 2 days", "Next week"
)

data class GoogleCalendarSyncResult(
    val isSuccess: Boolean,
    val message: String,
    val syncedCount: Int = 0,
    val accountName: String? = null
)
