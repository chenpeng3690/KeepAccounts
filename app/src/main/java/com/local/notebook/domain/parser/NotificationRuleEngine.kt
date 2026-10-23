package com.local.notebook.domain.parser

/**
 * 解析流水线：L0 通知类型 → L1 噪音词 → 规则匹配 → L2 规则排除 → L3 金额校验 → L4 方向判定。
 * 通用兜底规则命中的一律进入待确认。
 */
class NotificationRuleEngine {

    fun parse(
        payload: NotificationPayload,
        rules: List<RuleDefinition>,
        fallbackEnabled: Boolean
    ): ParseOutcome {
        val text = payload.fullText.trim()

        if (NoiseFilter.l0Blocked(payload.category)) {
            return ParseOutcome.Rejected("L0", "推广/社交类通知，已拦截")
        }
        if (text.length < 4) {
            return ParseOutcome.Rejected("L3", "文本过短，未找到金额")
        }
        val noiseWord = NoiseFilter.l1NoiseWord(text)
        if (noiseWord != null) {
            return ParseOutcome.Rejected("L1", "命中噪音词「$noiseWord」")
        }
        val amount = AmountParser.bestAmount(text)

        for (rule in rules.sortedByDescending { it.priority }) {
            if (!TemplateMatcher.matches(text, rule)) continue
            val exclude = rule.excludeKeywords.firstOrNull { text.contains(it) }
            if (exclude != null) {
                return ParseOutcome.Rejected("L2", "命中规则排除词「$exclude」")
            }
            if (amount == null) {
                return ParseOutcome.Rejected("L3", "未找到金额")
            }
            val direction = rule.explicitDirection ?: DirectionParser.detect(text)
            val merchant = MerchantExtractor.extract(text)
            val category = CategoryGuesser.guess(text, direction)

            return if (rule.active && direction != Direction.UNKNOWN) {
                ParseOutcome.Matched(
                    ruleId = rule.id,
                    ruleName = rule.name,
                    amount = amount.cents,
                    direction = direction,
                    merchant = merchant,
                    categoryName = category,
                    defaultAccountId = rule.defaultAccountId,
                    defaultCategoryId = rule.defaultCategoryId
                )
            } else {
                ParseOutcome.Pending(
                    ruleId = rule.id,
                    ruleName = rule.name,
                    amount = amount.cents,
                    direction = direction.takeIf { it != Direction.UNKNOWN },
                    merchant = merchant,
                    categoryName = category,
                    defaultAccountId = rule.defaultAccountId,
                    defaultCategoryId = rule.defaultCategoryId,
                    note = if (!rule.active) "规则观察中" else "方向无法判定"
                )
            }
        }

        // 通用兜底只服务于「没有专属规则」的应用，避免微信聊天等通知全部涌入待确认
        if (rules.isNotEmpty()) return ParseOutcome.Ignored
        if (!fallbackEnabled) return ParseOutcome.Ignored

        if (amount == null) {
            val hasDigits = text.any { it.isDigit() }
            return ParseOutcome.Rejected("L3", if (hasDigits) "未找到有效金额" else "未找到金额")
        }
        val direction = DirectionParser.detect(text)
        return ParseOutcome.Pending(
            ruleId = null,
            ruleName = null,
            amount = amount.cents,
            direction = direction.takeIf { it != Direction.UNKNOWN },
            merchant = MerchantExtractor.extract(text),
            categoryName = CategoryGuesser.guess(text, direction),
            defaultAccountId = null,
            defaultCategoryId = null,
            note = "通用规则"
        )
    }
}
