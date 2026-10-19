package com.example

import com.example.network.GeminiParserService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WhatsAppParserTest {

    @Test
    fun testWhatsAppNoticeParsing() = runBlocking {
        val parser = GeminiParserService()
        val whatsappMessage = """
            [17/09, 3:54 pm] +91 80759 02502: Hypothalamic  hormones and post.pituitary assessment will be on Tuesday 2.30 to 3.30
            [17/09, 4:02 pm] afna zyda: Next Biochemistry Assignment : Tryptophan metabolism
        """.trimIndent()

        val response = parser.parseDocument(whatsappMessage, null, null)

        // Verify that exactly 2 items were extracted and NO fake items like "30 to"
        assertEquals(2, response.extracted_items.size)

        val item1 = response.extracted_items[0]
        assertEquals("Assessment", item1.category)
        assertEquals("Physiology", item1.subject)
        assertTrue("Title should mention Hypothalamic: ${item1.title}", item1.title.contains("Hypothalamic", ignoreCase = true))
        assertTrue("Title should not contain phone numbers: ${item1.title}", !item1.title.contains("+91"))
        assertTrue("Due date should capture Tuesday: ${item1.due_date_description}", item1.due_date_description.contains("Tuesday", ignoreCase = true))

        val item2 = response.extracted_items[1]
        assertEquals("Assignment", item2.category)
        assertEquals("Biochemistry", item2.subject)
        assertTrue("Title should capture Tryptophan metabolism: ${item2.title}", item2.title.contains("Tryptophan", ignoreCase = true))
        assertTrue("Title should not contain sender name: ${item2.title}", !item2.title.contains("afna zyda"))
    }

    @Test
    fun testThreeUpcomingAssessmentsParsing() = runBlocking {
        val parser = GeminiParserService()
        val whatsappNotice = """
            Upcoming Internal Assessments:
            1. Anatomy - 24/10/2026 at 10:00 AM (Upper Limb & General Anatomy)
            2. Physiology - 28/10/2026 at 11:30 AM (General Physiology & Nerve Muscle)
            3. Biochemistry - 02/11/2026 at 02:00 PM (Carbohydrate Metabolism)
        """.trimIndent()

        val response = parser.parseDocument(whatsappNotice, null, null)

        // Verify exactly 3 assessments are extracted
        assertEquals(3, response.extracted_items.size)

        val item1 = response.extracted_items[0]
        assertEquals("Assessment", item1.category)
        assertEquals("Anatomy", item1.subject)
        assertTrue("Due date should capture 24th Oct: ${item1.due_date_description}",
            item1.due_date_description.contains("2026-10-24") || item1.due_date_description.contains("24"))

        val item2 = response.extracted_items[1]
        assertEquals("Assessment", item2.category)
        assertEquals("Physiology", item2.subject)
        assertTrue("Due date should capture 28th Oct: ${item2.due_date_description}",
            item2.due_date_description.contains("2026-10-28") || item2.due_date_description.contains("28"))

        val item3 = response.extracted_items[2]
        assertEquals("Assessment", item3.category)
        assertEquals("Biochemistry", item3.subject)
        assertTrue("Due date should capture 2nd Nov: ${item3.due_date_description}",
            item3.due_date_description.contains("2026-11-02") || item3.due_date_description.contains("02"))
    }
}
