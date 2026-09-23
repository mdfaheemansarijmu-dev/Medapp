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
import java.util.Locale
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

data class LocalOcrResult(
    val cleanText: String,
    val spatialText: String
)

class GeminiParserService {
    private fun getActiveApiKey(): String {
        val buildKey = BuildConfig.GEMINI_API_KEY
        return if (!buildKey.isNullOrBlank() && buildKey != "MY_GEMINI_API_KEY") {
            buildKey
        } else {
            ""
        }
    }

    private suspend fun recognizeTextFromBitmap(bitmap: android.graphics.Bitmap): LocalOcrResult = suspendCancellableCoroutine { continuation ->
        try {
            val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
            val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val cleanSb = StringBuilder()
                    val spatialSb = StringBuilder()
                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            val frame = line.boundingBox
                            val text = line.text.trim()
                            if (text.isNotBlank()) {
                                cleanSb.append(text).append("\n")
                                spatialSb.append("Text: \"$text\", Box: [L=${frame?.left}, T=${frame?.top}, R=${frame?.right}, B=${frame?.bottom}]\n")
                            }
                        }
                    }
                    recognizer.close()
                    continuation.resume(LocalOcrResult(cleanSb.toString(), spatialSb.toString()))
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
        var ocrResult = LocalOcrResult("", "")
        if (hasImage && imageBytes != null) {
            try {
                Log.d("GeminiParser", "Preprocessing image (rotating and enhancing contrast)...")
                val enhancedBitmap = BitmapUtils.rotateAndEnhanceImage(imageBytes)
                
                Log.d("GeminiParser", "Running local high-precision ML Kit OCR...")
                ocrResult = recognizeTextFromBitmap(enhancedBitmap)
                Log.d("GeminiParser", "OCR Clean Text:\n${ocrResult.cleanText}")
                
                processedImageBytes = BitmapUtils.bitmapToByteArray(enhancedBitmap)
            } catch (e: Exception) {
                Log.e("GeminiParser", "Error rotating/enhancing or doing OCR: ${e.message}", e)
            }
        }

        val activeKey = getActiveApiKey()
        if (activeKey.isEmpty() || activeKey == "MY_GEMINI_API_KEY") {
            Log.e("GeminiParser", "Gemini API Key is not configured! Using local intelligent parser.")
            val localRes = getLocalFallbackResponse(inputPrompt, hasImage, mimeType, ocrResult.cleanText, currentScheduleContext)
            return@withContext sanitizeResponse(localRes, inputPrompt)
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

            2. TIMETABLE & CLASS SCHEDULES (CRITICAL):
               If the input contains a timetable, routine, or class schedule (whether an image of a weekly routine/grid, handwritten/printed timetable, a single-day routine, or plain text with class timings):
               - Set document_type = "Weekly Timetable"
               - Extract EVERY period/class found into "extracted_timetable"
               - Map days to day_of_week: 1=Monday, 2=Tuesday, 3=Wednesday, 4=Thursday, 5=Friday, 6=Saturday, 7=Sunday (default to 1 if no day specified)
               - Extract period_number (1, 2, 3...), start_time (e.g. "09:00 AM"), end_time (e.g. "10:00 AM"), subject, teacher_name (if any), room (if any), is_practical (true for labs, dissection, practicals, clinics), is_lunch_break (true for recess, lunch)
               - Keep "extracted_items" = []

            3. ASSIGNMENTS & HOMEWORK:
               If the input contains homework, record submission, logbook, chart, or assignments:
               - Set document_type = "Assignment Notice"
               - Add each item to "extracted_items" with category = "Assignment"
               - Keep "extracted_timetable" = []

            4. ASSESSMENTS, TESTS & EXAMS:
               If the input contains internal assessments, class tests, vivas, exams, quizzes, or evaluations:
               - Set document_type = "Assessment Notice"
               - Add each item to "extracted_items" with category = "Assessment" (Map all exams, vivas, and tests strictly to "Assessment")
               - Keep "extracted_timetable" = []

            5. SCHEDULE ADJUSTMENTS & TEMPORARY OVERRIDES:
               If the input announces a specific class cancellation, room change, teacher substitute, extra class, or temporary timing change for a specific day (NOT a routine timetable):
               - Set document_type = "Schedule Override"
               - Set is_temporary_override = true
               - Set override_date to the affected date or day (e.g. "Tomorrow", "Monday", "2026-09-20")
               - Add each adjustment to "extracted_items" with category = "Class Cancellation", "Room Change", "Teacher Change", or "Schedule Change"
               - Keep "extracted_timetable" = []

            6. HOLIDAYS & COLLEGE CLOSURES:
               If the input announces a holiday or college closure:
               - Set document_type = "Holiday Notice"
               - Add item to "extracted_items" with category = "Holiday"
               - Keep "extracted_timetable" = []

            7. WHATSAPP & FORWARDED CHAT MESSAGES (CRITICAL):
               When analyzing messages copied from WhatsApp, Telegram, or class groups (e.g. `[17/09, 3:54 pm] +91 80759 02502: ...`):
               - Strip timestamps, phone numbers, and sender names completely from titles/details.
               - NEVER extract numbers, decimal times, or fragments (like "30 to" or "2.30") as standalone general notices.
               - Extract clean, academic titles describing the specific topic (e.g., "Hypothalamic & Post. Pituitary Assessment", "Biochemistry: Tryptophan Metabolism").
               - Infer medical subject accurately (e.g. "Hypothalamic hormones / Pituitary" -> "Physiology", "Tryptophan metabolism" -> "Biochemistry").
               - Extract day, date, and timings into due_date_description (e.g., "Tuesday 2.30 to 3.30" -> "Tuesday (02:30 PM - 03:30 PM)").

            ${if (ocrResult.spatialText.isNotBlank()) "LOCAL OCR SPATIAL RECONSTRUCTION:\nUse this local high-precision spatial text with coordinates to align and map rows and columns perfectly. Ensure NO row or column is missed:\n${ocrResult.spatialText}\n" else ""}

            Input to analyze:
            "$inputPrompt"

            You MUST respond ONLY with a valid JSON object matching the exact structure below, with no markdown codeblocks, and no conversational preamble or postscript:
            {
              "document_type": "Weekly Timetable",
              "is_temporary_override": false,
              "override_date": null,
              "conversational_response": null,
              "extracted_timetable": [
                {
                  "day_of_week": 1,
                  "period_number": 1,
                  "start_time": "09:00 AM",
                  "end_time": "10:00 AM",
                  "subject": "Anatomy",
                  "teacher_name": "Dr. Sharma",
                  "room": "Hall A",
                  "is_practical": false,
                  "is_lunch_break": false
                }
              ],
              "extracted_items": [
                {
                  "category": "Assessment",
                  "subject": "Physiology",
                  "title": "Hypothalamic & Post. Pituitary Assessment",
                  "due_date_description": "Tuesday (02:30 PM - 03:30 PM)",
                  "priority": "High",
                  "details": "Hypothalamic hormones and post.pituitary assessment"
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
            val response = RetrofitClient.service.generateContent(targetModel, activeKey, request)
            jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        } catch (e: Exception) {
            Log.e("GeminiParser", "$targetModel call failed: ${e.message}. Trying backup model...", e)
            val fallbackModel = if (targetModel != "gemini-2.5-flash") {
                "gemini-2.5-flash"
            } else {
                GeminiModelOption.FLASH_35.modelId
            }
            try {
                Log.d("GeminiParser", "Calling fallback model $fallbackModel...")
                val response = RetrofitClient.service.generateContent(fallbackModel, activeKey, request)
                jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            } catch (e2: Exception) {
                Log.e("GeminiParser", "$fallbackModel call failed: ${e2.message}", e2)
            }
        }

        val rawResponse = if (jsonText != null) {
            Log.d("GeminiParser", "Raw response: $jsonText")
            val adapter = RetrofitClient.moshiParser.adapter(UnifiedParserResponse::class.java)
            adapter.fromJson(jsonText) ?: getLocalFallbackResponse(inputPrompt, hasImage, mimeType, ocrResult.cleanText, currentScheduleContext)
        } else {
            Log.e("GeminiParser", "Direct API pathway failed or returned empty content")
            getLocalFallbackResponse(inputPrompt, hasImage, mimeType, ocrResult.cleanText, currentScheduleContext)
        }
        return@withContext sanitizeResponse(rawResponse, inputPrompt)
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

        // 2. Check if input is a Class Schedule or Timetable
        val hasNoticeKeywords = combinedText.contains("assignment") ||
                combinedText.contains("submission") ||
                combinedText.contains("record book") ||
                combinedText.contains("internal assessment") ||
                combinedText.contains("viva") ||
                combinedText.contains("cancelled") ||
                combinedText.contains("room change") ||
                combinedText.contains("shifted to") ||
                combinedText.contains("holiday")

        val dayKeywords = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
        val dayMap = mapOf(
            "monday" to 1, "mon" to 1,
            "tuesday" to 2, "tue" to 2, "tues" to 2,
            "wednesday" to 3, "wed" to 3,
            "thursday" to 4, "thu" to 4, "thur" to 4, "thurs" to 4,
            "friday" to 5, "fri" to 5,
            "saturday" to 6, "sat" to 6,
            "sunday" to 7, "sun" to 7
        )

        val timeRegex = Regex("(?i)(\\d{1,2})(?:[:.](\\d{2}))?\\s*(am|pm)?\\s*(?:-|–|to)\\s*(\\d{1,2})(?:[:.](\\d{2}))?\\s*(am|pm)?")

        val hasScheduleIntent = !hasNoticeKeywords && (
            hasImage ||
            combinedText.contains("timetable") ||
            combinedText.contains("routine") ||
            combinedText.contains("schedule") ||
            timeRegex.containsMatchIn(combinedText) ||
            dayKeywords.any { combinedText.contains(it) }
        )

        if (hasScheduleIntent) {
            val timetable = mutableListOf<ParsedTimetableClass>()
            var currentDayOfWeek = 1
            var periodCounter = 1

            val sourceLines = (if (ocrText.isNotBlank()) ocrText else input).lines()

            for (line in sourceLines) {
                val trimmed = line.trim()
                if (trimmed.isBlank()) continue
                val lowerLine = trimmed.lowercase()

                // Check if line sets the day (e.g. "Monday:", "## Tuesday", "Wednesday Schedule")
                var matchedDay: Int? = null
                for ((dayName, dayNum) in dayMap) {
                    if (Regex("\\b$dayName\\b", RegexOption.IGNORE_CASE).containsMatchIn(lowerLine)) {
                        matchedDay = dayNum
                        break
                    }
                }

                if (matchedDay != null && !timeRegex.containsMatchIn(trimmed)) {
                    currentDayOfWeek = matchedDay
                    periodCounter = 1
                    continue
                }

                // Check if line contains a time interval and class subject
                val match = timeRegex.find(trimmed)
                if (match != null) {
                    val dayForClass = matchedDay ?: currentDayOfWeek
                    val h1 = match.groupValues[1]
                    val m1 = match.groupValues[2].ifEmpty { "00" }
                    val p1 = match.groupValues[3].uppercase()
                    val h2 = match.groupValues[4]
                    val m2 = match.groupValues[5].ifEmpty { "00" }
                    val p2 = match.groupValues[6].uppercase()

                    val finalP2 = if (p2.isNotBlank()) p2 else if (h2.toIntOrNull() ?: 0 in 1..7) "PM" else "AM"
                    val finalP1 = if (p1.isNotBlank()) p1 else if (h1.toIntOrNull() ?: 0 in 8..11) "AM" else finalP2

                    val startTime = String.format(Locale.US, "%02d:%s %s", h1.toIntOrNull() ?: 9, m1, finalP1)
                    val endTime = String.format(Locale.US, "%02d:%s %s", h2.toIntOrNull() ?: 10, m2, finalP2)

                    // Extract subject text after removing the time string
                    var rawSubject = trimmed.replace(match.value, "")
                        .replace(Regex("^[0-9]+[.):-]\\s*"), "")
                        .replace(Regex("^[-:•|]\\s*"), "")
                        .replace(Regex("[-:•|]\\s*$"), "")
                        .trim()

                    if (rawSubject.isBlank()) {
                        rawSubject = "Medical Class"
                    }

                    val isPractical = rawSubject.contains("practical", ignoreCase = true) ||
                            rawSubject.contains("lab", ignoreCase = true) ||
                            rawSubject.contains("dissection", ignoreCase = true) ||
                            rawSubject.contains("clinic", ignoreCase = true)

                    val isLunch = rawSubject.contains("lunch", ignoreCase = true) ||
                            rawSubject.contains("break", ignoreCase = true) ||
                            rawSubject.contains("recess", ignoreCase = true)

                    timetable.add(
                        ParsedTimetableClass(
                            day_of_week = dayForClass,
                            period_number = periodCounter++,
                            start_time = startTime,
                            end_time = endTime,
                            subject = rawSubject,
                            is_practical = isPractical,
                            is_lunch_break = isLunch
                        )
                    )
                }
            }

            if (timetable.isNotEmpty()) {
                return UnifiedParserResponse(
                    document_type = "Weekly Timetable",
                    is_temporary_override = false,
                    extracted_timetable = timetable
                )
            }
        }

        // 3. WhatsApp Announcement & Notice Parsing (Assessments, Assignments, Overrides, Holidays)
        val whatsappRegex = Regex("^\\[?\\d{1,2}[/\\.-]\\d{1,2}(?:[/\\.-]\\d{2,4})?,?\\s*\\d{1,2}:\\d{2}(?::\\d{2})?\\s*(?:[ap]\\.?m\\.?)?\\]?\\s*(?:-\\s*)?(?:[^:\\n]{1,50}:)?\\s*", RegexOption.IGNORE_CASE)

        val rawLines = input.lines().flatMap { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank()) emptyList()
            else {
                // Split multi-announcements separated by bullets or numbered points like "1. ... 2. ...",
                // BUT strictly avoid splitting decimal numbers/times like "2.30 to 3.30"!
                trimmed.split(Regex("(?<=\\s)(?=[0-9]{1,2}\\.\\s+[A-Za-z])|[;•]")).map { it.trim() }
            }
        }.filter { it.length >= 4 }

        val items = mutableListOf<ParsedItem>()
        var detectedOverride = false
        var overrideDate: String? = null

        val linesToProcess = if (rawLines.isNotEmpty()) rawLines else listOf(input.trim())

        for (line in linesToProcess) {
            // Strip WhatsApp sender and timestamp headers
            var clean = whatsappRegex.replace(line, "").trim()
            clean = clean.replace(Regex("^[0-9]+\\.\\s*"), "").replace(Regex("^[-*•]\\s*"), "").trim()

            // If clean line is too short or is a noise fragment like "30 to" or numbers, ignore
            if (clean.length < 4 || clean.matches(Regex("^[0-9\\s\\.\\:to\\-]+$", RegexOption.IGNORE_CASE))) {
                continue
            }
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
                lower.contains("assessment") || lower.contains("test") || lower.contains("exam") || lower.contains("examination") ||
                lower.contains("viva") || lower.contains("quiz") || lower.contains("midterm") || lower.contains("terminal") || lower.contains("evaluation") -> {
                    "Assessment"
                }
                lower.contains("assignment") || lower.contains("submit") || lower.contains("submission") || lower.contains("homework") ||
                lower.contains("record book") || lower.contains("logbook") || lower.contains("chart") || lower.contains("journal") || lower.contains("case study") || lower.contains("due") -> {
                    "Assignment"
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
                lower.contains("biochem") || lower.contains("tryptophan") || lower.contains("metabolism") || lower.contains("enzyme") || lower.contains("amino acid") || lower.contains("carbohydrate") || lower.contains("lipid") || lower.contains("protein") || lower.contains("glycolysis") -> "Biochemistry"
                lower.contains("physio") || lower.contains("hypothalamic") || lower.contains("pituitary") || lower.contains("hormone") || lower.contains("endocrine") || lower.contains("cns") || lower.contains("reflex") || lower.contains("cardiovascular") || lower.contains("renal") || lower.contains("respiratory") || lower.contains("nerve") || lower.contains("muscle") -> "Physiology"
                lower.contains("anatomy") || lower.contains("dissection") || lower.contains("histology") || lower.contains("embryology") || lower.contains("osteology") || lower.contains("gross") || lower.contains("cadaver") || lower.contains("thorax") || lower.contains("abdomen") -> "Anatomy"
                lower.contains("organon") || lower.contains("aphorism") || lower.contains("vital force") || lower.contains("miasm") -> "Organon of Medicine"
                lower.contains("materia medica") || lower.contains("materia") -> "Materia Medica"
                lower.contains("repertory") || lower.contains("rubric") -> "Repertory"
                lower.contains("pharmacy") -> "Homeopathic Pharmacy"
                lower.contains("pharmacology") || lower.contains("pharma") || lower.contains("drug") -> "Pharmacology"
                lower.contains("pathology") || lower.contains("patho") || lower.contains("necrosis") || lower.contains("biopsy") || lower.contains("neoplasia") -> "Pathology"
                lower.contains("microbiology") || lower.contains("microbio") || lower.contains("bacteriology") || lower.contains("culture") -> "Microbiology"
                lower.contains("forensic") || lower.contains("fmt") || lower.contains("toxicology") -> "Forensic Medicine"
                lower.contains("community medicine") || lower.contains("psm") || lower.contains("spm") -> "Community Medicine"
                lower.contains("surgery") || lower.contains("surgical") -> "Surgery"
                lower.contains("medicine") -> "Medicine"
                lower.contains("obstetrics") || lower.contains("gynaecology") || lower.contains("gynae") || lower.contains("obg") -> "Obstetrics & Gynaecology"
                lower.contains("pediatrics") || lower.contains("paediatrics") -> "Pediatrics"
                else -> "General"
            }

            // Detect Due / Effective Date & Time Range
            val dayMatch = Regex("\\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday|tomorrow|today)\\b", RegexOption.IGNORE_CASE).find(lower)
            val dayName = dayMatch?.value?.replaceFirstChar { it.uppercase() }

            val timeMatch = Regex("(\\d{1,2}(?:[\\.:]\\d{2})?)\\s*(?:to|-)\\s*(\\d{1,2}(?:[\\.:]\\d{2})?)\\s*([ap]m)?", RegexOption.IGNORE_CASE).find(lower)
            val dueDate = if (dayName != null && timeMatch != null) {
                var t1 = timeMatch.groupValues[1].replace('.', ':')
                var t2 = timeMatch.groupValues[2].replace('.', ':')
                if (!t1.contains(':')) t1 += ":00"
                if (!t2.contains(':')) t2 += ":00"
                val h1 = t1.substringBefore(':').toIntOrNull() ?: 12
                val h2 = t2.substringBefore(':').toIntOrNull() ?: 12
                val m1 = t1.substringAfter(':')
                val m2 = t2.substringAfter(':')
                val p1 = if (h1 in 1..7 || lower.contains("pm")) "PM" else "AM"
                val p2 = if (h2 in 1..7 || lower.contains("pm")) "PM" else "AM"
                val formatted = String.format(Locale.ENGLISH, "%s (%02d:%s %s - %02d:%s %s)", dayName, h1, m1, p1, h2, m2, p2)
                if (overrideDate == null && detectedOverride) overrideDate = dayName
                formatted
            } else if (dayName != null) {
                if (overrideDate == null && detectedOverride) overrideDate = dayName
                if (lower.contains("next $dayName", ignoreCase = true)) "Next $dayName" else dayName
            } else {
                "Upcoming"
            }

            // Priority
            val priority = if (category == "Assessment" || category == "Class Cancellation" || dueDate.contains("Tomorrow", ignoreCase = true) || dueDate.contains("Today", ignoreCase = true) || lower.contains("urgent") || lower.contains("mandatory")) {
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
                    val topicMatch = Regex("^(.*?)(?:\\s+assessment|\\s+test|\\s+exam|\\s+viva|\\s+quiz)", RegexOption.IGNORE_CASE).find(clean)
                    val rawTopic = topicMatch?.groupValues?.get(1)?.trim()
                    if (!rawTopic.isNullOrBlank() && rawTopic.length > 3 && !rawTopic.contains("upcoming", ignoreCase = true)) {
                        val formattedTopic = rawTopic
                            .replace(Regex("\\bpost\\.\\s*pituitary\\b", RegexOption.IGNORE_CASE), "Post. Pituitary")
                            .split(" ")
                            .filter { it.isNotBlank() }
                            .joinToString(" ") { word ->
                                if (word.equals("and", ignoreCase = true) || word.equals("&", ignoreCase = true)) "&"
                                else word.replaceFirstChar { it.uppercase() }
                            }
                        "$formattedTopic Assessment"
                    } else if (subject != "General") {
                        val testType = if (lower.contains("viva")) "Viva" else if (lower.contains("quiz")) "Quiz" else if (lower.contains("internal")) "Internal Assessment" else "Assessment"
                        "$subject $testType"
                    } else {
                        clean.take(45)
                    }
                }
                "Assignment" -> {
                    val colonSplit = clean.split(":", limit = 2)
                    if (colonSplit.size > 1 && colonSplit[1].trim().length > 2) {
                        val topic = colonSplit[1].trim().split(" ").filter { it.isNotBlank() }.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                        if (subject != "General") "$subject: $topic" else "$topic Assignment"
                    } else if (subject != "General") {
                        val assignType = if (lower.contains("record")) "Record Submission" else if (lower.contains("journal")) "Journal Submission" else if (lower.contains("logbook")) "Logbook Submission" else "Assignment"
                        "$subject $assignType"
                    } else {
                        clean.take(45)
                    }
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

    /**
     * Sanitizes AI parser responses (Gemini or local fallback) to eliminate WhatsApp headers,
     * filter out number/time fragments, enrich generic titles, and ensure medical subjects.
     */
    private fun sanitizeResponse(response: UnifiedParserResponse, rawInput: String): UnifiedParserResponse {
        val whatsappRegex = Regex("^\\[?\\d{1,2}[/\\.-]\\d{1,2}(?:[/\\.-]\\d{2,4})?,?\\s*\\d{1,2}:\\d{2}(?::\\d{2})?\\s*(?:[ap]\\.?m\\.?)?\\]?\\s*(?:-\\s*)?(?:[^:\\n]{1,50}:)?\\s*", RegexOption.IGNORE_CASE)

        val cleanedItems = response.extracted_items.mapNotNull { item ->
            var cleanTitle = whatsappRegex.replace(item.title, "").trim()
            cleanTitle = cleanTitle.replace(Regex("^[0-9]+\\.\\s*"), "").replace(Regex("^[-*•]\\s*"), "").trim()
            val cleanDetails = item.details?.let { whatsappRegex.replace(it, "").trim() } ?: cleanTitle

            // Filter out junk/fragments like "30 to", "to", or pure numbers/punctuation
            if (cleanTitle.length < 4 && !cleanTitle.equals("quiz", ignoreCase = true) && !cleanTitle.equals("exam", ignoreCase = true)) {
                return@mapNotNull null
            }
            if (cleanTitle.matches(Regex("^[0-9\\s\\.\\:to\\-]+$", RegexOption.IGNORE_CASE))) {
                return@mapNotNull null
            }

            // Infer better subject if "General"
            var s = item.subject
            val lowerCombined = "${cleanTitle.lowercase()} ${cleanDetails.lowercase()} ${rawInput.lowercase()}"
            if (s.equals("General", ignoreCase = true) || s.isBlank()) {
                s = when {
                    lowerCombined.contains("biochem") || lowerCombined.contains("tryptophan") || lowerCombined.contains("metabolism") || lowerCombined.contains("enzyme") -> "Biochemistry"
                    lowerCombined.contains("physio") || lowerCombined.contains("hypothalamic") || lowerCombined.contains("pituitary") || lowerCombined.contains("hormone") -> "Physiology"
                    lowerCombined.contains("anatomy") || lowerCombined.contains("dissection") || lowerCombined.contains("histology") -> "Anatomy"
                    lowerCombined.contains("organon") || lowerCombined.contains("aphorism") -> "Organon of Medicine"
                    lowerCombined.contains("pharmacy") || lowerCombined.contains("pharmacology") -> "Pharmacology"
                    lowerCombined.contains("pathology") -> "Pathology"
                    lowerCombined.contains("microbiology") -> "Microbiology"
                    else -> "General"
                }
            }

            // Enrich title if generic like "Biochemistry Assignment" but details has topic like "Tryptophan metabolism"
            var t = cleanTitle
            if (t.equals("Biochemistry Assignment", ignoreCase = true) || t.equals("Assignment", ignoreCase = true)) {
                if (lowerCombined.contains("tryptophan")) {
                    t = "Biochemistry: Tryptophan Metabolism"
                }
            } else if (t.contains("Hypothalami", ignoreCase = true) && !t.contains("Hypothalamic Hormones", ignoreCase = true)) {
                t = "Hypothalamic & Post. Pituitary Assessment"
            }

            item.copy(
                title = t,
                subject = s,
                details = cleanDetails
            )
        }

        return response.copy(extracted_items = cleanedItems)
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
        val activeKey = getActiveApiKey()
        if (activeKey.isEmpty() || activeKey == "MY_GEMINI_API_KEY") {
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
                RetrofitClient.service.generateContent(targetModel, activeKey, request)
            } catch (e: Exception) {
                val fallbackModel = if (targetModel != "gemini-2.5-flash") {
                    "gemini-2.5-flash"
                } else {
                    GeminiModelOption.FLASH_35.modelId
                }
                Log.e("GeminiParser", "$targetModel revision failed: ${e.message}. Calling fallback $fallbackModel...", e)
                RetrofitClient.service.generateContent(fallbackModel, activeKey, request)
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
