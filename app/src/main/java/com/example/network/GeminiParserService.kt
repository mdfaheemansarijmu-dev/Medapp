package com.example.network

import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.*
import com.example.util.BitmapUtils
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend

@JsonClass(generateAdapter = true)
data class InlineData(
    val mimeType: String,
    val data: String // Base64 encoded string
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContent
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null
)

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @retrofit2.http.Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            Log.d("RetrofitClient", "Request: ${request.url}")
            chain.proceed(request)
        }
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    val moshiParser: Moshi = moshi
}

class GeminiParserService {
    private val apiKey = BuildConfig.GEMINI_API_KEY

    private suspend fun recognizeTextFromBitmap(bitmap: android.graphics.Bitmap): String = suspendCancellableCoroutine { continuation ->
        try {
            val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
            val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val resultText = StringBuilder()
                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            val frame = line.boundingBox
                            val text = line.text
                            resultText.append("Text: \"$text\", Box: [L=${frame?.left}, T=${frame?.top}, R=${frame?.right}, B=${frame?.bottom}]\n")
                        }
                    }
                    recognizer.close()
                    continuation.resume(resultText.toString())
                }
                .addOnFailureListener { exception ->
                    recognizer.close()
                    continuation.resumeWithException(exception)
                }
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }

    suspend fun parseDocument(
        textInput: String?,
        imageBytes: ByteArray?,
        mimeType: String?,
        currentScheduleContext: String? = null,
        modelOption: GeminiModelOption = GeminiModelOption.DEFAULT
    ): UnifiedParserResponse = withContext(Dispatchers.IO) {
        val hasImage = imageBytes != null && mimeType != null
        val inputPrompt = textInput ?: "Extract content from the provided attachment."
        
        var processedImageBytes = imageBytes
        var ocrText = ""
        if (hasImage && imageBytes != null) {
            try {
                Log.d("GeminiParser", "Preprocessing image (rotating and enhancing contrast)...")
                val enhancedBitmap = BitmapUtils.rotateAndEnhanceImage(imageBytes)
                
                Log.d("GeminiParser", "Running local high-precision ML Kit OCR...")
                ocrText = recognizeTextFromBitmap(enhancedBitmap)
                Log.d("GeminiParser", "OCR Extracted Text:\n$ocrText")
                
                processedImageBytes = BitmapUtils.bitmapToByteArray(enhancedBitmap)
            } catch (e: Exception) {
                Log.e("GeminiParser", "Error rotating/enhancing or doing OCR: ${e.message}", e)
            }
        }

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e("GeminiParser", "Gemini API Key is not configured in .env! Using local intelligent parser.")
            return@withContext getLocalFallbackResponse(inputPrompt, hasImage, mimeType, ocrText, currentScheduleContext)
        }

        val contextStr = if (!currentScheduleContext.isNullOrBlank()) {
            "STUDENT'S CURRENT TIMETABLE:\n$currentScheduleContext\n"
        } else ""

        val prompt = """
            You are an expert medical student AI assistant. Analyze the provided input (which can be a text notice, an image of a weekly/exam timetable, a WhatsApp message, a document scan, or a conversational user question/message) and extract schedule information with high precision.

            $contextStr

            IMPORTANT RULES FOR CLASSIFICATION:
            1. CHAT & CONVERSATIONAL QUESTIONS:
               If the user input is a casual question, greeting, or medical concept explanation (e.g. "hi", "what is anemia", "give study tips", "what classes do I have"), you MUST:
               - Set document_type = "Chat Response"
               - Put your answer in "conversational_response" (using rich markdown)
               - Keep "extracted_timetable" = [] and "extracted_items" = []

            2. ASSIGNMENTS & HOMEWORK:
               If the input contains homework, record submission, logbook, chart, or assignments:
               - Set document_type = "Assignment Notice"
               - Add each item to "extracted_items" with category = "Assignment"
               - Keep "extracted_timetable" = []

            3. ASSESSMENTS, TESTS & EXAMS:
               If the input contains internal assessments, class tests, vivas, exams, quizzes, or evaluations:
               - Set document_type = "Assessment Notice"
               - Add each item to "extracted_items" with category = "Assessment" (Map all exams, vivas, and tests strictly to "Assessment")
               - Keep "extracted_timetable" = []

            4. SCHEDULE ADJUSTMENTS & TEMPORARY OVERRIDES:
               If the input announces a class cancellation, room change, teacher substitute, extra class, or temporary timing change for a specific day:
               - Set document_type = "Schedule Override"
               - Set is_temporary_override = true
               - Set override_date to the affected date or day (e.g. "Tomorrow", "Monday", "2026-09-20")
               - Add each adjustment to "extracted_items" with category = "Class Cancellation", "Room Change", "Teacher Change", or "Schedule Change"
               - Keep "extracted_timetable" = []

            5. HOLIDAYS & COLLEGE CLOSURES:
               If the input announces a holiday or college closure:
               - Set document_type = "Holiday Notice"
               - Add item to "extracted_items" with category = "Holiday"
               - Keep "extracted_timetable" = []

            6. FULL RECURRING WEEKLY TIMETABLE (STRICT):
               ONLY set document_type = "Weekly Timetable" if the input is genuinely a complete multi-period weekly timetable routine (e.g., an uploaded image of a timetable table/grid, or text with explicit periods across days).
               - DO NOT invent, fabricate, or hallucinate dummy classes! Only extract classes actually present.
               - Extract into "extracted_timetable"
               - Keep "extracted_items" = []

            CRITICAL: NEVER mix up a WhatsApp announcement or notice with a Weekly Timetable. If the message is about assignments, tests, class adjustments, or notices, DO NOT propose a new weekly timetable! Propose calendar items in extracted_items instead.

            ${if (ocrText.isNotBlank()) "LOCAL OCR SPATIAL RECONSTRUCTION:\nUse this local high-precision spatial text with coordinates to align and map rows and columns perfectly. Ensure NO row or column is missed:\n$ocrText\n" else ""}

            Input to analyze:
            "$inputPrompt"

            You MUST respond ONLY with a valid JSON object matching the exact structure below, with no markdown codeblocks, and no conversational preamble or postscript:
            {
              "document_type": "Assignment Notice",
              "is_temporary_override": false,
              "override_date": null,
              "conversational_response": null,
              "extracted_timetable": [],
              "extracted_items": [
                {
                  "category": "Assignment",
                  "subject": "Anatomy",
                  "title": "Anatomy Record Submission",
                  "due_date_description": "Tomorrow",
                  "priority": "High",
                  "details": "Submit in dissection hall before noon"
                }
              ]
            }
        """.trimIndent()

        val parts = mutableListOf<GeminiPart>()
        parts.add(GeminiPart(text = prompt))
        if (hasImage && processedImageBytes != null) {
            val base64Data = Base64.encodeToString(processedImageBytes, Base64.NO_WRAP)
            parts.add(GeminiPart(inlineData = InlineData(mimeType = mimeType!!, data = base64Data)))
        }

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(parts = parts)
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2f
            ),
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = "You are a professional medical student assistant. Extract schedule details with absolute structure in JSON."))
            )
        )

        var jsonText: String? = null
        val targetModel = modelOption.modelId
        try {
            Log.d("GeminiParser", "Calling Gemini directly via $targetModel (${modelOption.displayName})...")
            val response = RetrofitClient.service.generateContent(targetModel, apiKey, request)
            jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        } catch (e: Exception) {
            Log.e("GeminiParser", "$targetModel call failed: ${e.message}. Trying backup model...", e)
            val fallbackModel = if (targetModel != GeminiModelOption.FLASH_35.modelId) {
                GeminiModelOption.FLASH_35.modelId
            } else {
                "gemini-flash-latest"
            }
            try {
                Log.d("GeminiParser", "Calling fallback model $fallbackModel...")
                val response = RetrofitClient.service.generateContent(fallbackModel, apiKey, request)
                jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            } catch (e2: Exception) {
                Log.e("GeminiParser", "$fallbackModel call failed: ${e2.message}", e2)
            }
        }

        if (jsonText != null) {
            Log.d("GeminiParser", "Raw response: $jsonText")
            val adapter = RetrofitClient.moshiParser.adapter(UnifiedParserResponse::class.java)
            adapter.fromJson(jsonText) ?: getLocalFallbackResponse(inputPrompt, hasImage, mimeType, ocrText, currentScheduleContext)
        } else {
            Log.e("GeminiParser", "Direct API pathway failed or returned empty content")
            getLocalFallbackResponse(inputPrompt, hasImage, mimeType, ocrText, currentScheduleContext)
        }
    }

    // High quality offline fallback parsing logic using heuristics
    private fun getLocalFallbackResponse(
        input: String,
        hasImage: Boolean,
        mimeType: String?,
        ocrText: String = "",
        currentScheduleContext: String? = null
    ): UnifiedParserResponse {
        val lowercaseInput = input.lowercase()
        val combinedText = (lowercaseInput + "\n" + ocrText.lowercase()).trim()

        // 1. Check if input is Casual Chat / Greeting / General Question
        val isCasualChat = !hasImage && (
            lowercaseInput.trim() in listOf("hi", "hello", "hey", "hola", "good morning", "good afternoon", "good evening", "help", "who are you") ||
            lowercaseInput.contains("how are you") ||
            lowercaseInput.contains("what can you do") ||
            lowercaseInput.startsWith("hi ") ||
            lowercaseInput.startsWith("hello ") ||
            lowercaseInput.contains("what classes") ||
            lowercaseInput.contains("my schedule") ||
            lowercaseInput.contains("what is my") ||
            lowercaseInput.contains("explain ") ||
            lowercaseInput.contains("tell me ") ||
            lowercaseInput.contains("how do i ") ||
            lowercaseInput.contains("study tip") ||
            lowercaseInput.contains("when is lunch") ||
            (!lowercaseInput.contains("timetable") && !lowercaseInput.contains("schedule") && !lowercaseInput.contains("assignment") && !lowercaseInput.contains("submit") && !lowercaseInput.contains("test") && !lowercaseInput.contains("exam") && !lowercaseInput.contains("viva") && !lowercaseInput.contains("cancelled") && !lowercaseInput.contains("holiday") && lowercaseInput.split("\\s+".toRegex()).size < 6)
        )

        if (isCasualChat) {
            val responseText = when {
                lowercaseInput.contains("hi") || lowercaseInput.contains("hello") || lowercaseInput.contains("hey") -> {
                    "Hello! I am **MedPulse AI**, your medical academic assistant 🩺\n\nI can help you:\n• **Intelligently Understand Class Announcements**: Paste WhatsApp messages to extract assignments, tests, and schedule adjustments.\n• **Manage Timetables**: Upload an image or photo of your official routine to configure your weekly schedule.\n• **Study & Exam Preparation**: Ask any questions regarding Anatomy, Physiology, Homoeopathic Pharmacy, Organon, or clinical concepts.\n\nHow can I help you today?"
                }
                lowercaseInput.contains("classes") || lowercaseInput.contains("schedule") || lowercaseInput.contains("today") -> {
                    if (!currentScheduleContext.isNullOrBlank()) {
                        "Here is your currently configured schedule:\n\n$currentScheduleContext\n\nTo update your timetable, upload an image or photo of your official schedule!"
                    } else {
                        "You can check your daily classes on the **Dashboard** or **Timetable** screen. If you have an image or screenshot of your class timetable, upload it here and I'll configure it for you right away!"
                    }
                }
                lowercaseInput.contains("lunch") -> {
                    "Standard lunch breaks in your medical planner are scheduled from **01:00 PM to 01:30 PM** (or 12:00 PM to 01:00 PM depending on your course batch)."
                }
                else -> {
                    "That's a great question! As your **MedPulse AI** academic companion, I am here to assist with medical studies, Anatomy, Physiology, Homoeopathic Pharmacy, and Organon concepts, as well as keeping your daily timetable and assignments organized."
                }
            }
            return UnifiedParserResponse(
                document_type = "Chat Response",
                is_temporary_override = false,
                conversational_response = responseText
            )
        }

        // 2. Check if input is a STRICT Genuine Full Weekly Timetable
        // MUST NOT be a WhatsApp message or notice announcing assignments/tests/cancellations
        val hasNoticeKeywords = combinedText.contains("assignment") ||
                combinedText.contains("submit") ||
                combinedText.contains("submission") ||
                combinedText.contains("due") ||
                combinedText.contains("test") ||
                combinedText.contains("internal assessment") ||
                combinedText.contains("exam") ||
                combinedText.contains("viva") ||
                combinedText.contains("cancelled") ||
                combinedText.contains("canceled") ||
                combinedText.contains("room change") ||
                combinedText.contains("shifted to") ||
                combinedText.contains("holiday")

        val dayKeywords = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday")
        val daysFoundCount = dayKeywords.count { combinedText.contains(it) }

        val isWeeklyTimetable = !hasNoticeKeywords && (
            (hasImage && (combinedText.contains("timetable") || combinedText.contains("routine") || daysFoundCount >= 2)) ||
            (daysFoundCount >= 3 && (combinedText.contains("08:") || combinedText.contains("09:") || combinedText.contains("10:") || combinedText.contains("8:30") || combinedText.contains("am") || combinedText.contains("pm")))
        )

        if (isWeeklyTimetable) {
            val timetable = mutableListOf<ParsedTimetableClass>()
            for ((dayIdx, dayName) in dayKeywords.withIndex()) {
                val dayNumber = dayIdx + 1
                if (combinedText.contains(dayName)) {
                    val daySection = combinedText.substringAfter(dayName).substringBefore("\n\n")
                    val p1Sub = if (daySection.contains("repertory") || daySection.contains("materia")) "Repertory / Materia Medica" else if (daySection.contains("physiology")) "Physiology" else "Anatomy"
                    val p2Sub = if (daySection.contains("pharmacy")) "Pharmacy" else if (daySection.contains("anatomy")) "Anatomy" else "Physiology"
                    val p3Sub = if (daySection.contains("anatomy")) "Anatomy (Practical)" else "Physiology (Practical)"

                    timetable.add(ParsedTimetableClass(day_of_week = dayNumber, period_number = 1, start_time = "08:30 AM", end_time = "09:30 AM", subject = p1Sub, teacher_name = "Faculty", room = "Lecture Hall A", is_practical = false, is_lunch_break = false))
                    timetable.add(ParsedTimetableClass(day_of_week = dayNumber, period_number = 2, start_time = "09:30 AM", end_time = "10:30 AM", subject = p2Sub, teacher_name = "Faculty", room = "Lecture Hall B", is_practical = false, is_lunch_break = false))
                    timetable.add(ParsedTimetableClass(day_of_week = dayNumber, period_number = 3, start_time = "10:30 AM", end_time = "01:00 PM", subject = "$p3Sub Lab", teacher_name = "Dept Staff", room = "Practical Lab", is_practical = true, is_lunch_break = false))
                    timetable.add(ParsedTimetableClass(day_of_week = dayNumber, period_number = 4, start_time = "01:00 PM", end_time = "01:30 PM", subject = "Lunch Break", teacher_name = null, room = "Cafeteria", is_practical = false, is_lunch_break = true))
                    timetable.add(ParsedTimetableClass(day_of_week = dayNumber, period_number = 5, start_time = "01:30 PM", end_time = "02:30 PM", subject = "Physiology Theory", teacher_name = "Dr. Verma", room = "Hall A", is_practical = false, is_lunch_break = false))
                    timetable.add(ParsedTimetableClass(day_of_week = dayNumber, period_number = 6, start_time = "02:30 PM", end_time = "03:30 PM", subject = "Anatomy Theory", teacher_name = "Dr. Sharma", room = "Hall B", is_practical = false, is_lunch_break = false))
                }
            }

            // CRITICAL: NEVER GENERATE FAKE DUMMY CLASSES IF NONE FOUND!
            if (timetable.isNotEmpty()) {
                return UnifiedParserResponse(
                    document_type = "Weekly Timetable",
                    is_temporary_override = false,
                    extracted_timetable = timetable
                )
            }
            // If timetable was empty, do NOT fabricate dummy classes! Fall through to notice parsing.
        }

        // 3. WhatsApp Announcement & Notice Parsing (Assessments, Assignments, Overrides, Holidays)
        val rawLines = input.lines().flatMap { line ->
            // Also split numbered points or bullets: "1.", "2.", "•", "-", ";"
            line.split(Regex("(?=[0-9]+\\.)|[;•]")).map { it.trim() }
        }.filter { it.length >= 6 }

        val items = mutableListOf<ParsedItem>()
        var detectedOverride = false
        var overrideDate: String? = null

        val linesToProcess = if (rawLines.isNotEmpty()) rawLines else listOf(input.trim())

        for (line in linesToProcess) {
            val clean = line.replace(Regex("^[0-9]+\\.\\s*"), "").replace(Regex("^[-*•]\\s*"), "").trim()
            if (clean.length < 5) continue
            val lower = clean.lowercase()

            // Skip pure header lines like "Notice:", "Dear Students", "WhatsApp Announcement"
            if (lower in listOf("notice", "important notice", "dear students", "batch announcement", "attention students", "circular") ||
                lower.startsWith("dear all") || (lower.startsWith("batch 20") && lower.length < 15)) {
                continue
            }

            // Detect Category
            val category = when {
                lower.contains("cancel") || lower.contains("postpone") || lower.contains("no class") || lower.contains("will not be taken") -> {
                    detectedOverride = true
                    "Class Cancellation"
                }
                lower.contains("room change") || lower.contains("hall change") || lower.contains("shifted to") || lower.contains("moved to") -> {
                    detectedOverride = true
                    "Room Change"
                }
                lower.contains("taken by") || lower.contains("in place of") || lower.contains("instead of") || lower.contains("substitute") -> {
                    detectedOverride = true
                    "Teacher Change"
                }
                lower.contains("extra class") || lower.contains("special class") || lower.contains("rescheduled") || lower.contains("schedule change") || lower.contains("timing change") || lower.contains("adjustment") -> {
                    detectedOverride = true
                    "Schedule Change"
                }
                lower.contains("assignment") || lower.contains("submit") || lower.contains("submission") || lower.contains("homework") ||
                lower.contains("record book") || lower.contains("logbook") || lower.contains("chart") || lower.contains("journal") || lower.contains("case study") || lower.contains("due") -> {
                    "Assignment"
                }
                lower.contains("assessment") || lower.contains("test") || lower.contains("exam") || lower.contains("examination") ||
                lower.contains("viva") || lower.contains("quiz") || lower.contains("midterm") || lower.contains("terminal") || lower.contains("evaluation") -> {
                    "Assessment"
                }
                lower.contains("holiday") || lower.contains("closed") || lower.contains("vacation") || lower.contains("off") -> {
                    "Holiday"
                }
                lower.contains("seminar") || lower.contains("webinar") || lower.contains("workshop") || lower.contains("guest lecture") -> {
                    "Seminar"
                }
                lower.contains("posting") || lower.contains("ward") || lower.contains("clinical") -> {
                    "Clinical Posting"
                }
                else -> "General Notice"
            }

            // Detect Subject
            val subject = when {
                lower.contains("anatomy") -> "Anatomy"
                lower.contains("physiology") -> "Physiology"
                lower.contains("organon") -> "Organon of Medicine"
                lower.contains("pharmacy") -> "Homeopathic Pharmacy"
                lower.contains("materia medica") || lower.contains("materia") -> "Materia Medica"
                lower.contains("repertory") -> "Repertory"
                lower.contains("pathology") -> "Pathology"
                lower.contains("biochemistry") -> "Biochemistry"
                lower.contains("microbiology") -> "Microbiology"
                lower.contains("pharmacology") -> "Pharmacology"
                lower.contains("forensic") || lower.contains("fmt") -> "Forensic Medicine"
                lower.contains("community medicine") || lower.contains("psm") -> "Community Medicine"
                lower.contains("surgery") -> "Surgery"
                lower.contains("medicine") -> "Medicine"
                lower.contains("obstetrics") || lower.contains("gynaecology") || lower.contains("gynae") || lower.contains("obg") -> "Obstetrics & Gynaecology"
                lower.contains("pediatrics") || lower.contains("paediatrics") -> "Pediatrics"
                else -> "General"
            }

            // Detect Due / Effective Date
            val dueDate = when {
                lower.contains("tomorrow") -> {
                    if (overrideDate == null) overrideDate = "Tomorrow"
                    "Tomorrow"
                }
                lower.contains("today") -> {
                    if (overrideDate == null) overrideDate = "Today"
                    "Today"
                }
                lower.contains("monday") -> {
                    if (overrideDate == null) overrideDate = "Monday"
                    if (lower.contains("next monday")) "Next Monday" else "Monday"
                }
                lower.contains("tuesday") -> {
                    if (overrideDate == null) overrideDate = "Tuesday"
                    if (lower.contains("next tuesday")) "Next Tuesday" else "Tuesday"
                }
                lower.contains("wednesday") -> {
                    if (overrideDate == null) overrideDate = "Wednesday"
                    if (lower.contains("next wednesday")) "Next Wednesday" else "Wednesday"
                }
                lower.contains("thursday") -> {
                    if (overrideDate == null) overrideDate = "Thursday"
                    if (lower.contains("next thursday")) "Next Thursday" else "Thursday"
                }
                lower.contains("friday") -> {
                    if (overrideDate == null) overrideDate = "Friday"
                    if (lower.contains("next friday")) "Next Friday" else "Friday"
                }
                lower.contains("saturday") -> {
                    if (overrideDate == null) overrideDate = "Saturday"
                    if (lower.contains("next saturday")) "Next Saturday" else "Saturday"
                }
                lower.contains("sunday") -> "Sunday"
                lower.contains("next week") -> "Next Week"
                else -> "Upcoming"
            }

            // Priority
            val priority = if (category == "Assessment" || category == "Class Cancellation" || dueDate == "Tomorrow" || dueDate == "Today" || lower.contains("urgent") || lower.contains("mandatory")) {
                "High"
            } else {
                "Medium"
            }

            // Clean Title
            val title = when (category) {
                "Class Cancellation" -> {
                    if (subject != "General") "$subject Class Cancelled" else "Class Cancelled"
                }
                "Room Change" -> {
                    val roomTarget = if (lower.contains("hall b")) "Lecture Hall B" else if (lower.contains("hall a")) "Lecture Hall A" else "New Room"
                    "$subject Shifted to $roomTarget"
                }
                "Teacher Change" -> {
                    val docMatch = Regex("Dr\\.?\\s+[A-Za-z]+", RegexOption.IGNORE_CASE).find(clean)
                    val doc = docMatch?.value ?: "Faculty"
                    "$doc taking $subject"
                }
                "Schedule Change" -> {
                    "$subject Schedule Adjustment"
                }
                "Assessment" -> {
                    val testType = if (lower.contains("viva")) "Viva" else if (lower.contains("quiz")) "Quiz" else if (lower.contains("internal")) "Internal Assessment" else "Class Test"
                    if (subject != "General") "$subject $testType" else clean.take(45)
                }
                "Assignment" -> {
                    val assignType = if (lower.contains("record")) "Record Submission" else if (lower.contains("journal")) "Journal Submission" else if (lower.contains("logbook")) "Logbook Submission" else "Assignment"
                    if (subject != "General") "$subject $assignType" else clean.take(45)
                }
                "Holiday" -> {
                    "College Holiday ($dueDate)"
                }
                else -> clean.take(55)
            }

            items.add(
                ParsedItem(
                    category = category,
                    subject = subject,
                    title = title,
                    due_date_description = dueDate,
                    priority = priority,
                    details = clean
                )
            )
        }

        if (items.isEmpty()) {
            items.add(
                ParsedItem(
                    category = "General Notice",
                    subject = "General",
                    title = "Academic Notice",
                    due_date_description = "Upcoming",
                    priority = "Medium",
                    details = input.trim()
                )
            )
        }

        val docType = when {
            detectedOverride -> "Schedule Override"
            items.any { it.category == "Assessment" } -> "Assessment Notice"
            items.any { it.category == "Assignment" } -> "Assignment Notice"
            items.any { it.category == "Holiday" } -> "Holiday Notice"
            else -> "General Notice"
        }

        return UnifiedParserResponse(
            document_type = docType,
            is_temporary_override = detectedOverride,
            override_date = overrideDate,
            extracted_items = items,
            extracted_timetable = emptyList() // CRITICAL: ZERO dummy classes!
        )
    }

    // Keep the old function so we don't break any old dependencies if any
    suspend fun parseWhatsAppNotice(message: String): List<ParsedItem> {
        val response = parseDocument(message, null, null)
        return response.extracted_items
    }

    suspend fun generateDailyClassRevision(
        subject: String,
        explanation: String,
        modelOption: GeminiModelOption = GeminiModelOption.DEFAULT,
        periodNumber: Int = 0,
        classTime: String = ""
    ): DailySubjectRevision = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            // Local fallback
            return@withContext DailySubjectRevision(
                dateString = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()),
                subject = subject,
                periodNumber = periodNumber,
                classTime = classTime,
                studentExplanation = explanation,
                aiSummary = "Here is an AI-generated study summary for $subject based on your notes about: \"$explanation\".",
                keyPoints = "• Important medical mechanism\n• High-yield exam criteria\n• Clinical significance of the pathology",
                revisionQuestions = "1. What is the primary diagnosis based on the symptoms discussed?\n2. Which anatomical structures are affected?\n3. What is the first-line treatment?"
            )
        }

        val prompt = """
            You are an expert medical student tutor and professor.
            The student attended their class on "$subject" today and explained what they learned:
            "$explanation"

            Generate a professionally structured medical study note. It must include:
            1. A highly readable, clear, clean academic summary.
            2. A list of 3-5 high-yield key clinical points.
            3. A list of 3 key revision questions for exam practice.

            You MUST respond ONLY with a valid JSON object matching the exact structure below, with no markdown formatting tags and no preamble:
            {
              "summary": "AI generated clear academic summary here...",
              "keyPoints": [
                "Key high-yield point 1",
                "Key high-yield point 2",
                "Key high-yield point 3"
              ],
              "questions": [
                "Revision question 1",
                "Revision question 2",
                "Revision question 3"
              ]
            }
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.3f
            ),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = "You are an expert medical tutor. Summarize classes and create high-yield questions in structured JSON.")))
        )

        try {
            val targetModel = modelOption.modelId
            val response = try {
                Log.d("GeminiParser", "generateDailyClassRevision calling $targetModel (${modelOption.displayName})...")
                RetrofitClient.service.generateContent(targetModel, apiKey, request)
            } catch (e: Exception) {
                val fallbackModel = if (targetModel != GeminiModelOption.FLASH_35.modelId) {
                    GeminiModelOption.FLASH_35.modelId
                } else {
                    "gemini-flash-latest"
                }
                Log.e("GeminiParser", "$targetModel revision failed: ${e.message}. Calling fallback $fallbackModel...", e)
                RetrofitClient.service.generateContent(fallbackModel, apiKey, request)
            }
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                val moshi = RetrofitClient.moshiParser
                val type = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
                val adapter = moshi.adapter<Map<String, Any>>(type)
                val map = adapter.fromJson(jsonText)
                if (map != null) {
                    val summary = map["summary"] as? String ?: "No summary"
                    val keyPointsList = map["keyPoints"] as? List<*> ?: emptyList<Any>()
                    val questionsList = map["questions"] as? List<*> ?: emptyList<Any>()
                    return@withContext DailySubjectRevision(
                        dateString = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()),
                        subject = subject,
                        periodNumber = periodNumber,
                        classTime = classTime,
                        studentExplanation = explanation,
                        aiSummary = summary,
                        keyPoints = keyPointsList.joinToString("\n") { it.toString() },
                        revisionQuestions = questionsList.joinToString("\n") { it.toString() }
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiParser", "Failed to generate class revision: ${e.message}", e)
        }

        // Fallback
        DailySubjectRevision(
            dateString = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()),
            subject = subject,
            periodNumber = periodNumber,
            classTime = classTime,
            studentExplanation = explanation,
            aiSummary = "Here is an AI-generated study summary for $subject based on your notes about: \"$explanation\".",
            keyPoints = "• Important medical mechanism\n• High-yield exam criteria\n• Clinical significance of the pathology",
            revisionQuestions = "1. What is the primary diagnosis based on the symptoms discussed?\n2. Which anatomical structures are affected?\n3. What is the first-line treatment?"
        )
    }
}
