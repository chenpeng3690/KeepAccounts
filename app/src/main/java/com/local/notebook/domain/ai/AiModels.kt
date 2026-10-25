package com.local.notebook.domain.ai

data class AiConfig(
    val baseUrl: String,
    val model: String,
    val apiKey: String,
    val confidencePercent: Int
) {
    val confidenceThreshold: Float
        get() = (confidencePercent.coerceIn(0, 100)) / 100f
}

data class AiParseResult(
    val isTransaction: Boolean,
    val type: String?,
    val amountCents: Long?,
    val merchant: String?,
    val category: String?,
    val account: String?,
    val toAccount: String?,
    val confidence: Float,
    val reason: String?
)

sealed interface AiCallResult {
    data class Success(
        val result: AiParseResult,
        val rawJson: String
    ) : AiCallResult

    data class Failure(
        val message: String,
        val retryable: Boolean
    ) : AiCallResult
}
