package com.local.notebook.data.ai

import com.local.notebook.domain.ai.AiCallResult
import com.local.notebook.domain.ai.AiConfig
import com.local.notebook.domain.ai.AiParseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenAI 兼容协议的极简客户端，默认指向 DeepSeek。
 * 只依赖 HttpURLConnection，不额外引入网络库。
 */
@Singleton
class AiClient @Inject constructor() {

    suspend fun parse(
        config: AiConfig,
        appLabel: String,
        packageName: String,
        title: String,
        text: String,
        categories: List<Pair<String, String>>,
        accounts: List<String>,
        occurredAt: Long
    ): AiCallResult = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) {
            return@withContext AiCallResult.Failure("未配置 API Key", retryable = false)
        }
        val categoryText = categories
            .groupBy { it.second }
            .entries
            .joinToString("；") { (type, items) ->
                val label = if (type == "INCOME") "收入" else "支出"
                "$label: ${items.joinToString("、") { it.first }}"
            }
            .ifBlank { "无" }
        val accountText = accounts.joinToString("、").ifBlank { "无账户" }
        val timeText = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
            .format(Date(occurredAt))

        val system = """
            你是记账应用的通知解析器。根据通知原文判断它是否是一笔真实的资金交易。
            只输出一个 JSON 对象，不要输出 markdown、解释或多余文本。
            JSON 字段：
            {
              "is_transaction": true 或 false,
              "type": "EXPENSE" 或 "INCOME" 或 "TRANSFER" 或 null,
              "amount": "正数金额，单位元，最多两位小数",
              "merchant": "商户或对方名称，无法判断时为 null",
              "category": "只能是给定分类中的一个名称",
              "account": "只能是给定账户中的一个名称",
              "to_account": "仅 TRANSFER 时填写转入账户，否则为 null",
              "confidence": 0 到 1 的小数,
              "reason": "一句话说明判断依据"
            }
            判断规则：
            1. 营销、优惠券、红包、积分、账单提醒、余额变动提醒、验证码、聊天消息不是交易。
            2. 退款算 INCOME；支出算 EXPENSE；收入算 INCOME。
            3. 转账必须能同时识别转出账户和转入账户，否则 is_transaction 仍为 true，但 type 为 TRANSFER。
            4. 金额必须来自通知正文，不要猜测。
            5. 分类和账户必须从给定列表中选择，无法匹配时填 null。
        """.trimIndent()

        val user = """
            应用：$appLabel（$packageName）
            标题：$title
            正文：$text
            通知时间：$timeText
            可选分类：$categoryText
            可选账户：$accountText
        """.trimIndent()

        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", user))

        var body = buildBody(config.model, messages, jsonMode = true)
        var (code, response) = post(config, body)
        if (code == 400 && response.contains("response_format")) {
            body = buildBody(config.model, messages, jsonMode = false)
            val retry = post(config, body)
            code = retry.first
            response = retry.second
        }

        if (code !in 200..299) {
            return@withContext AiCallResult.Failure(
                message = httpMessage(code, response),
                retryable = code == 408 || code == 429 || code >= 500 || code == 0
            )
        }

        val content = runCatching {
            JSONObject(response)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }.getOrElse {
            return@withContext AiCallResult.Failure(
                "AI 返回格式异常：${response.take(160)}",
                retryable = false
            )
        }

        val jsonText = content.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val obj = runCatching { JSONObject(jsonText) }.getOrElse {
            return@withContext AiCallResult.Failure(
                "AI 未返回有效 JSON：${content.take(160)}",
                retryable = false
            )
        }

        AiCallResult.Success(
            result = parseResult(obj),
            rawJson = jsonText
        )
    }

    suspend fun test(config: AiConfig): AiCallResult = parse(
        config = config,
        appLabel = "微信支付",
        packageName = "com.tencent.mm",
        title = "微信支付",
        text = "支付成功 ¥25.00 瑞幸咖啡",
        categories = listOf(
            "餐饮" to "EXPENSE",
            "交通" to "EXPENSE",
            "其他" to "EXPENSE",
            "工资" to "INCOME",
            "其他收入" to "INCOME"
        ),
        accounts = listOf("微信零钱"),
        occurredAt = System.currentTimeMillis()
    )

    private fun buildBody(model: String, messages: JSONArray, jsonMode: Boolean): String =
        JSONObject().apply {
            put("model", model.ifBlank { "deepseek-chat" })
            put("temperature", 0)
            put("messages", messages)
            if (jsonMode) {
                put("response_format", JSONObject().put("type", "json_object"))
            }
        }.toString()

    private fun post(config: AiConfig, body: String): Pair<Int, String> {
        val url = chatUrl(config.baseUrl)
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        }
        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            code to text
        } catch (error: IOException) {
            0 to (error.message ?: "网络连接失败")
        } finally {
            connection.disconnect()
        }
    }

    private fun chatUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) return "https://api.deepseek.com/chat/completions"
        return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
    }

    private fun parseResult(obj: JSONObject): AiParseResult {
        val rawAmount = obj.opt("amount")
        val amountText = when (rawAmount) {
            is Number -> rawAmount.toString()
            is String -> rawAmount
            else -> ""
        }
            .replace(",", "")
            .replace("¥", "")
            .replace("￥", "")
            .replace("元", "")
            .trim()
        val amountCents = amountText.toBigDecimalOrNull()
            ?.takeIf { it > BigDecimal.ZERO }
            ?.setScale(2, RoundingMode.HALF_UP)
            ?.movePointRight(2)
            ?.toLong()
            ?.takeIf { it in 1..MAX_AMOUNT_CENTS }

        var confidence = obj.optDouble("confidence", 0.0)
        if (confidence > 1.0) confidence /= 100.0
        val isTransaction = when (val raw = obj.opt("is_transaction")) {
            is Boolean -> raw
            is String -> raw.equals("true", ignoreCase = true)
            else -> false
        }
        return AiParseResult(
            isTransaction = isTransaction,
            type = normalizeType(obj.optString("type")),
            amountCents = amountCents,
            merchant = obj.optString("merchant").trim().takeIf { it.isNotEmpty() && it != "null" },
            category = obj.optString("category").trim().takeIf { it.isNotEmpty() && it != "null" },
            account = obj.optString("account").trim().takeIf { it.isNotEmpty() && it != "null" },
            toAccount = obj.optString("to_account").trim().takeIf { it.isNotEmpty() && it != "null" },
            confidence = confidence.coerceIn(0.0, 1.0).toFloat(),
            reason = obj.optString("reason").trim().takeIf { it.isNotEmpty() && it != "null" }
        )
    }

    private fun normalizeType(raw: String): String? = when (raw.trim().uppercase(Locale.US)) {
        "EXPENSE", "支出" -> "EXPENSE"
        "INCOME", "收入", "退款" -> "INCOME"
        "TRANSFER", "转账" -> "TRANSFER"
        else -> null
    }

    private fun httpMessage(code: Int, body: String): String = when (code) {
        0 -> body
        401, 403 -> "API Key 无效或没有权限"
        402 -> "AI 账户余额不足"
        404 -> "接口地址不存在，请检查 Base URL"
        429 -> "AI 请求过于频繁，稍后重试"
        else -> "AI 请求失败（HTTP $code）：${body.take(160)}"
    }

    private companion object {
        const val MAX_AMOUNT_CENTS = 10_000_000L
    }
}
