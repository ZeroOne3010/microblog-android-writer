package com.example.microblogwriter.ai

class AiReviewClient {
    suspend fun review(apiKey: String, model: String, prompt: String): Result<String> {
        if (apiKey.isBlank()) return Result.failure(IllegalStateException("Missing API key"))
        val preview = prompt.take(600)
        return Result.success("AI review ($model):\n\n$preview\n\n- Suggestion: tighten long sentences.\n- Suggestion: verify transitions between sections.")
    }
}
