package com.example

import com.example.data.model.AttendanceRecord
import com.example.util.AttendanceCalculator
import com.example.util.AttendanceStatus
import org.junit.Assert.*
import org.junit.Test

class AttendanceCalculatorTest {

    @Test
    fun testClassesCanMiss_normalBuffer() {
        // 10 out of 10 attended, target 75%
        // If 3 missed: 10/13 = 76.9% (>= 75%)
        // If 4 missed: 10/14 = 71.4% (< 75%)
        val canMiss = AttendanceCalculator.calculateClassesCanMiss(10, 10, 75)
        assertEquals(3, canMiss)
    }

    @Test
    fun testClassesCanMiss_exactlyAtTarget() {
        // 15 out of 20 attended = 75.0%
        val canMiss = AttendanceCalculator.calculateClassesCanMiss(15, 20, 75)
        assertEquals(0, canMiss)
    }

    @Test
    fun testClassesCanMiss_belowTarget() {
        // 14 out of 20 attended = 70.0%
        val canMiss = AttendanceCalculator.calculateClassesCanMiss(14, 20, 75)
        assertEquals(0, canMiss)
    }

    @Test
    fun testClassesToAttend_zeroWhenAtOrAboveTarget() {
        val toAttend1 = AttendanceCalculator.calculateClassesToAttend(15, 20, 75)
        assertEquals(0, toAttend1)

        val toAttend2 = AttendanceCalculator.calculateClassesToAttend(18, 20, 75)
        assertEquals(0, toAttend2)
    }

    @Test
    fun testClassesToAttend_whenBelowTarget() {
        // 14 out of 20 = 70.0%. Target = 75%
        // Diff = 75*20 - 100*14 = 1500 - 1400 = 100
        // Denominator = 25
        // n = 100 / 25 = 4
        // Check: (14 + 4) / (20 + 4) = 18 / 24 = 75.0%
        val toAttend = AttendanceCalculator.calculateClassesToAttend(14, 20, 75)
        assertEquals(4, toAttend)
    }

    @Test
    fun testClassesToAttend_zeroAttended() {
        // 0 out of 5 attended. Target = 75%
        // Diff = 75*5 - 0 = 375
        // Denom = 25
        // n = 375 / 25 = 15
        // Check: 15 / 20 = 75.0%
        val toAttend = AttendanceCalculator.calculateClassesToAttend(0, 5, 75)
        assertEquals(15, toAttend)
    }

    @Test
    fun testStatusEvaluation() {
        assertEquals(AttendanceStatus.NO_DATA, AttendanceCalculator.evaluateStatus(0, 0, 75))
        assertEquals(AttendanceStatus.CRITICAL, AttendanceCalculator.evaluateStatus(10, 20, 75)) // 50% < 65%
        assertEquals(AttendanceStatus.BELOW_TARGET, AttendanceCalculator.evaluateStatus(14, 20, 75)) // 70% < 75%
        assertEquals(AttendanceStatus.NEAR_TARGET, AttendanceCalculator.evaluateStatus(15, 20, 75)) // 75% and 0 miss buffer
        assertEquals(AttendanceStatus.ON_TRACK, AttendanceCalculator.evaluateStatus(16, 20, 75)) // 80% with >= 1 miss buffer
    }

    @Test
    fun testActionableInsightMessages() {
        // No data
        val noData = AttendanceCalculator.generateInsight(0, 0, 75)
        assertTrue(noData.contains("No attendance recorded yet"))

        // Below target
        val belowTarget = AttendanceCalculator.generateInsight(14, 20, 75)
        assertEquals("Attend the next 4 classes to reach 75%.", belowTarget)

        // At target
        val atTarget = AttendanceCalculator.generateInsight(15, 20, 75)
        assertEquals("You cannot safely miss another class.", atTarget)

        // Safe with buffer
        val safe = AttendanceCalculator.generateInsight(10, 10, 75)
        assertEquals("You can miss 3 more classes and remain at or above 75%.", safe)
    }

    @Test
    fun testSubjectSummary_excludesCancelledClasses() {
        val records = listOf(
            AttendanceRecord(dateString = "2026-09-01", subject = "Anatomy", isPresent = true, status = "PRESENT"),
            AttendanceRecord(dateString = "2026-09-02", subject = "Anatomy", isPresent = true, status = "PRESENT"),
            AttendanceRecord(dateString = "2026-09-03", subject = "Anatomy", isPresent = false, status = "NO_CLASS"),
            AttendanceRecord(dateString = "2026-09-04", subject = "Anatomy", isPresent = false, status = "ABSENT")
        )

        val summary = AttendanceCalculator.calculateSubjectSummary("Anatomy", records, 75)
        assertEquals(3, summary.totalClasses)
        assertEquals(2, summary.attendedClasses)
        assertEquals(66.66f, summary.percentage, 0.1f)
        assertEquals(AttendanceStatus.BELOW_TARGET, summary.status)
    }

    @Test
    fun testOverallSummary_streakCalculation() {
        val records = listOf(
            AttendanceRecord(dateString = "2026-09-05", subject = "Physiology", isPresent = true, status = "PRESENT"),
            AttendanceRecord(dateString = "2026-09-04", subject = "Biochemistry", isPresent = true, status = "PRESENT"),
            AttendanceRecord(dateString = "2026-09-03", subject = "Anatomy", isPresent = true, status = "EXCUSED"),
            AttendanceRecord(dateString = "2026-09-02", subject = "Pathology", isPresent = false, status = "ABSENT")
        )

        val overall = AttendanceCalculator.calculateOverallSummary(records, 75)
        assertEquals(3, overall.currentStreak) // 3 in a row before the absent class
        assertEquals(4, overall.totalClasses)
        assertEquals(3, overall.totalAttended)
    }
}
