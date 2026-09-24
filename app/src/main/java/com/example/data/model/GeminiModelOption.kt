package com.example.data.model

enum class GeminiModelOption(
    val modelId: String,
    val displayName: String,
    val shortBadge: String,
    val latencyLabel: String,
    val description: String,
    val isFastest: Boolean = false,
    val isLatest: Boolean = false
) {
    FLASH_LITE(
        modelId = "gemini-3.1-flash-lite-preview",
        displayName = "Gemini 3.1 Flash-Lite",
        shortBadge = "3.1 Lite (Fastest)",
        latencyLabel = "⚡ Fastest (<1s)",
        description = "Google's fastest preview model. Ultra-low latency, blazing fast responses for instant answers and schedule parsing.",
        isFastest = true,
        isLatest = true
    ),
    FLASH_35(
        modelId = "gemini-3.5-flash",
        displayName = "Gemini 3.5 Flash",
        shortBadge = "3.5 Flash (Latest)",
        latencyLabel = "🚀 Fast (~1.5s)",
        description = "Latest flagship multimodal model. High intelligence, advanced medical comprehension, and rapid throughput.",
        isFastest = false,
        isLatest = true
    ),
    PRO_31(
        modelId = "gemini-3.1-pro-preview",
        displayName = "Gemini 3.1 Pro",
        shortBadge = "3.1 Pro (Deep)",
        latencyLabel = "🧠 Deep Reasoning (~2-3s)",
        description = "Latest pro reasoning model. Complex clinical case reasoning, diagnostic dilemmas, and advanced analysis.",
        isFastest = false,
        isLatest = true
    );

    companion object {
        val DEFAULT = FLASH_35

        fun fromModelId(id: String): GeminiModelOption {
            return entries.find { it.modelId == id } ?: DEFAULT
        }
    }
}
