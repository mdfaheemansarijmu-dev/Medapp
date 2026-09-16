package com.example.util

import com.example.data.model.AttendanceRecord
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * Attendance intelligence and mathematical calculation engine for MedPulse.
 * Implements exact integer arithmetic to avoid floating-point rounding errors.
 */
enum class AttendanceStatus(
    val label: String,
    val description: String
) {
    ON_TRACK("On Track", "Healthy attendance with a safety buffer."),
    NEAR_TARGET("Near Target", "Attendance is at target. Any absence will drop you below."),
    BELOW_TARGET("Below Target", "Currently below target. Recovery attendance required."),
    CRITICAL("Critical", "Significantly below target. Immediate attention needed."),
    NO_DATA("No Data", "No attendance records yet.")
}

enum class AttendanceTrend {
    IMPROVING,
    STABLE,
    DECLINING,
    INSUFFICIENT_DATA
}

data class SubjectAttendanceSummary(
    val subject: String,
    val attendedClasses: Int,
    val totalClasses: Int,
    val percentage: Float,
    val target: Int,
    val status: AttendanceStatus,
    val statusMessage: String,
    val actionableInsight: String,
    val classesCanMiss: Int,
    val classesToRecover: Int,
    val records: List<AttendanceRecord> = emptyList()
)

data class OverallAttendanceSummary(
    val totalAttended: Int,
    val totalClasses: Int,
    val percentage: Float,
    val target: Int,
    val status: AttendanceStatus,
    val statusLabel: String,
    val hasData: Boolean,
    val classesCanMiss: Int,
    val classesToRecover: Int,
    val actionableInsight: String,
    val currentStreak: Int,
    val trend: AttendanceTrend,
    val trendDifference: Float,
    val trendDescription: String
)

object AttendanceCalculator {

    /**
     * Finds the smallest non-negative integer n such that:
     * (attended + n) / (total + n) >= target / 100
     *
     * Exact integer arithmetic:
     * n * (100 - target) >= target * total - 100 * attended
     */
    fun calculateClassesToAttend(attended: Int, total: Int, targetPercentage: Int): Int {
        if (total <= 0 || targetPercentage <= 0) return 0
        if (targetPercentage >= 100) {
            return if (attended >= total) 0 else 999 // Effectively impossible if already missed one
        }
        val diff = targetPercentage * total - 100 * attended
        if (diff <= 0) return 0
        val denominator = 100 - targetPercentage
        return (diff + denominator - 1) / denominator
    }

    /**
     * Finds the largest non-negative integer m such that:
     * attended / (total + m) >= target / 100
     *
     * Exact integer arithmetic:
     * m * target <= 100 * attended - target * total
     */
    fun calculateClassesCanMiss(attended: Int, total: Int, targetPercentage: Int): Int {
        if (total <= 0) return 0
        if (targetPercentage <= 0) return 999
        val diff = 100 * attended - targetPercentage * total
        if (diff < 0) return 0
        return diff / targetPercentage
    }

    /**
     * Determines whether an attendance record counts as applicable (not cancelled / no class).
     */
    fun isApplicableRecord(record: AttendanceRecord): Boolean {
        return record.status != "NO_CLASS" && record.status != "CANCELLED"
    }

    /**
     * Determines whether a record counts as attended (Present or Excused).
     */
    fun isAttendedRecord(record: AttendanceRecord): Boolean {
        if (!isApplicableRecord(record)) return false
        return record.status == "PRESENT" || record.status == "EXCUSED" || record.isPresent
    }

    /**
     * Evaluates attendance status based on percentage, target, and miss buffer.
     */
    fun evaluateStatus(attended: Int, total: Int, target: Int): AttendanceStatus {
        if (total == 0) return AttendanceStatus.NO_DATA
        val pct = (attended.toFloat() / total.toFloat()) * 100f
        return when {
            pct < (target - 10f) -> AttendanceStatus.CRITICAL
            pct < target.toFloat() -> AttendanceStatus.BELOW_TARGET
            calculateClassesCanMiss(attended, total, target) == 0 -> AttendanceStatus.NEAR_TARGET
            else -> AttendanceStatus.ON_TRACK
        }
    }

    /**
     * Generates a clear, actionable human insight for a subject or overall summary.
     */
    fun generateInsight(
        attended: Int,
        total: Int,
        target: Int
    ): String {
        if (total == 0) {
            return "No attendance recorded yet. Marks will appear after your first lecture."
        }
        val pct = (attended.toFloat() / total.toFloat()) * 100f
        return if (pct < target.toFloat()) {
            val toAttend = calculateClassesToAttend(attended, total, target)
            if (target >= 100) {
                "Attend all future classes to maximize your record."
            } else {
                "Attend the next $toAttend ${if (toAttend == 1) "class" else "classes"} to reach $target%."
            }
        } else {
            val canMiss = calculateClassesCanMiss(attended, total, target)
            if (canMiss == 0) {
                "You cannot safely miss another class."
            } else {
                "You can miss $canMiss more ${if (canMiss == 1) "class" else "classes"} and remain at or above $target%."
            }
        }
    }

    /**
     * Calculates subject-wise attendance summary for a given list of records.
     */
    fun calculateSubjectSummary(
        subject: String,
        records: List<AttendanceRecord>,
        target: Int
    ): SubjectAttendanceSummary {
        val subjectRecords = records.filter { it.subject.equals(subject, ignoreCase = true) }
            .sortedWith(compareByDescending<AttendanceRecord> { it.dateString }.thenByDescending { it.recordedTimestamp })
        
        val applicable = subjectRecords.filter { isApplicableRecord(it) }
        val total = applicable.size
        val attended = applicable.count { isAttendedRecord(it) }
        val pct = if (total == 0) 0f else (attended.toFloat() / total.toFloat()) * 100f
        val status = evaluateStatus(attended, total, target)
        val canMiss = calculateClassesCanMiss(attended, total, target)
        val toAttend = calculateClassesToAttend(attended, total, target)
        val insight = generateInsight(attended, total, target)

        val statusMsg = when (status) {
            AttendanceStatus.NO_DATA -> "No records"
            AttendanceStatus.CRITICAL -> "Critical (Below $target%)"
            AttendanceStatus.BELOW_TARGET -> "Below $target% target"
            AttendanceStatus.NEAR_TARGET -> "At target ($target%)"
            AttendanceStatus.ON_TRACK -> "On track"
        }

        return SubjectAttendanceSummary(
            subject = subject,
            attendedClasses = attended,
            totalClasses = total,
            percentage = pct,
            target = target,
            status = status,
            statusMessage = statusMsg,
            actionableInsight = insight,
            classesCanMiss = canMiss,
            classesToRecover = toAttend,
            records = subjectRecords
        )
    }

    /**
     * Calculates the overall attendance summary across all subjects.
     */
    fun calculateOverallSummary(
        allRecords: List<AttendanceRecord>,
        target: Int
    ): OverallAttendanceSummary {
        val applicable = allRecords.filter { isApplicableRecord(it) }
            .sortedWith(compareByDescending<AttendanceRecord> { it.dateString }.thenByDescending { it.recordedTimestamp })

        val total = applicable.size
        val attended = applicable.count { isAttendedRecord(it) }
        val pct = if (total == 0) 0f else (attended.toFloat() / total.toFloat()) * 100f
        val hasData = total > 0
        val status = evaluateStatus(attended, total, target)
        val canMiss = calculateClassesCanMiss(attended, total, target)
        val toAttend = calculateClassesToAttend(attended, total, target)
        val insight = generateInsight(attended, total, target)

        // Calculate consecutive attendance streak
        var streak = 0
        for (rec in applicable) {
            if (isAttendedRecord(rec)) {
                streak++
            } else {
                break
            }
        }

        // Calculate monthly trend
        val sdfMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val cal = Calendar.getInstance()
        val currentMonthStr = sdfMonth.format(cal.time)
        cal.add(Calendar.MONTH, -1)
        val lastMonthStr = sdfMonth.format(cal.time)

        val currentMonthRecords = applicable.filter { it.dateString.startsWith(currentMonthStr) }
        val lastMonthRecords = applicable.filter { it.dateString.startsWith(lastMonthStr) }

        val (trend, trendDiff, trendDesc) = if (currentMonthRecords.isNotEmpty() && lastMonthRecords.isNotEmpty()) {
            val curAttended = currentMonthRecords.count { isAttendedRecord(it) }
            val curPct = (curAttended.toFloat() / currentMonthRecords.size.toFloat()) * 100f

            val lastAttended = lastMonthRecords.count { isAttendedRecord(it) }
            val lastPct = (lastAttended.toFloat() / lastMonthRecords.size.toFloat()) * 100f

            val diff = curPct - lastPct
            val roundedDiff = (diff * 10).roundToInt() / 10f
            when {
                diff >= 1.0f -> Triple(
                    AttendanceTrend.IMPROVING,
                    roundedDiff,
                    "Your attendance has improved by ${roundedDiff.toInt()}% this month."
                )
                diff <= -1.0f -> Triple(
                    AttendanceTrend.DECLINING,
                    roundedDiff,
                    "Your attendance is down by ${Math.abs(roundedDiff).toInt()}% this month."
                )
                else -> Triple(
                    AttendanceTrend.STABLE,
                    0f,
                    "Your attendance is stable this month."
                )
            }
        } else if (currentMonthRecords.isNotEmpty()) {
            val curAttended = currentMonthRecords.count { isAttendedRecord(it) }
            val curPct = (curAttended.toFloat() / currentMonthRecords.size.toFloat()) * 100f
            Triple(
                AttendanceTrend.STABLE,
                0f,
                "Attendance this month: ${curPct.roundToInt()}%"
            )
        } else {
            Triple(
                AttendanceTrend.INSUFFICIENT_DATA,
                0f,
                "Not enough monthly data for trend comparison."
            )
        }

        return OverallAttendanceSummary(
            totalAttended = attended,
            totalClasses = total,
            percentage = pct,
            target = target,
            status = status,
            statusLabel = when (status) {
                AttendanceStatus.NO_DATA -> "No Data"
                AttendanceStatus.ON_TRACK -> "On Track"
                AttendanceStatus.NEAR_TARGET -> "Near Target"
                AttendanceStatus.BELOW_TARGET -> "Below Target"
                AttendanceStatus.CRITICAL -> "Critical"
            },
            hasData = hasData,
            classesCanMiss = canMiss,
            classesToRecover = toAttend,
            actionableInsight = insight,
            currentStreak = streak,
            trend = trend,
            trendDifference = trendDiff,
            trendDescription = trendDesc
        )
    }
}
