package com.example

import com.example.data.sync.BatchNotificationSyncManager
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testBatchKeyComputation_normal() {
    val key1 = BatchNotificationSyncManager.computeBatchKey("AIIMS Delhi", "MBBS", 2024, "Batch A")
    val key2 = BatchNotificationSyncManager.computeBatchKey("aiims  delhi", "mbbs", 2024, "batch a")
    assertEquals(key1, key2)
    assertEquals("aiims_delhi_mbbs_2024_batch_a", key1)
  }

  @Test
  fun testBatchKeyComputation_fallbacks() {
    val fallbackKey = BatchNotificationSyncManager.computeBatchKey("", "", 0, "")
    assertEquals("medical_college_mbbs_2024_batch_a", fallbackKey)
  }

  @Test
  fun testBatchKeyComputation_specialCharacters() {
    val key = BatchNotificationSyncManager.computeBatchKey("St. John's Medical College!", "BDS", 2023, "Batch-B")
    assertEquals("st_john_s_medical_college_bds_2023_batch_b", key)
  }

  @Test
  fun testBatchKeyComputation_customBatchCode() {
    val key = BatchNotificationSyncManager.computeBatchKey("AIIMS Delhi", "MBBS", 2024, "Batch A", "gmc_2024_a")
    assertEquals("gmc_2024_a", key)
  }

  @Test
  fun testBatchKeyComputation_canonicalNormalization() {
    val keyFullName = BatchNotificationSyncManager.computeBatchKey(
        "All India Institute of Medical Sciences (AIIMS), New Delhi",
        "MBBS",
        2024,
        "Batch A"
    )
    val keyShortName = BatchNotificationSyncManager.computeBatchKey(
        "AIIMS New Delhi",
        "MBBS",
        2024,
        "Batch A"
    )
    assertEquals(keyFullName, keyShortName)
    assertEquals("aiims_delhi_mbbs_2024_batch_a", keyFullName)
  }

  @Test
  fun testSyllabusDirectory_bhmsSubjectsAndTopics() {
    val bhmsCurriculum = com.example.data.syllabus.CourseSyllabusDirectory.getCurriculum("BHMS")
    assertNotNull(bhmsCurriculum)
    val firstYearSubjects = com.example.data.syllabus.CourseSyllabusDirectory.getSubjectsForYear("BHMS", "1st Year")
    assertTrue(firstYearSubjects.isNotEmpty())

    val pharmacySubject = firstYearSubjects.firstOrNull { it.name.contains("Homeopathic Pharmacy", ignoreCase = true) }
    assertNotNull("BHMS 1st Year should have Homeopathic Pharmacy", pharmacySubject)
    assertTrue(
        "Pharmacy should contain Doctrine of Signatures",
        pharmacySubject!!.suggestedTopics.any { it.contains("Doctrine of Signature", ignoreCase = true) }
    )

    val organonSubject = firstYearSubjects.firstOrNull { it.name.contains("Organon", ignoreCase = true) }
    assertNotNull("BHMS 1st Year should have Organon of Medicine", organonSubject)
  }

  @Test
  fun testSyllabusDirectory_allCoursesSupported() {
    val courses = listOf("BHMS", "MBBS", "BDS", "BAMS", "NURSING", "PHARMACY")
    for (course in courses) {
      val curriculum = com.example.data.syllabus.CourseSyllabusDirectory.getCurriculum(course)
      assertNotNull("Curriculum for $course should exist", curriculum)
      val years = com.example.data.syllabus.CourseSyllabusDirectory.getAvailableYears(course)
      assertTrue("Course $course should have multiple academic years", years.size >= 4)
      for (year in years) {
        val subjects = com.example.data.syllabus.CourseSyllabusDirectory.getSubjectsForYear(course, year)
        assertTrue("Course $course year $year should have subjects", subjects.isNotEmpty())
      }
    }
  }

  @Test
  fun testSyllabusDirectory_yearNormalization() {
    assertEquals("1st Year", com.example.data.syllabus.CourseSyllabusDirectory.normalizeYear("1"))
    assertEquals("1st Year", com.example.data.syllabus.CourseSyllabusDirectory.normalizeYear("1st Year"))
    assertEquals("2nd Year", com.example.data.syllabus.CourseSyllabusDirectory.normalizeYear("2nd Year"))
    assertEquals("3rd Year", com.example.data.syllabus.CourseSyllabusDirectory.normalizeYear("Third"))
    assertEquals("4th Year / Final Year", com.example.data.syllabus.CourseSyllabusDirectory.normalizeYear("Final Year"))
  }
}
