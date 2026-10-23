package com.local.notebook.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRuleEngineTest {

    private val engine = NotificationRuleEngine()

    private val wechatRule = RuleDefinition(
        id = 1L,
        name = "微信支付",
        packageName = "com.tencent.mm",
        priority = 20,
        state = "ACTIVE",
        literalGroups = listOf(
            listOf("微信支付", "微信支付凭证", "微信收款", "收款到账", "红包到账", "退款到账", "转账", "已支付", "支付成功", "付款成功")
        ),
        fuzzy = true,
        explicitDirection = null,
        excludeKeywords = emptyList(),
        defaultAccountId = 1L,
        defaultCategoryId = null
    )

    private fun payload(
        text: String,
        title: String = "",
        category: String? = null,
        packageName: String = "com.tencent.mm"
    ) = NotificationPayload(
        packageName = packageName,
        appLabel = "微信",
        title = title,
        text = text,
        notificationKey = "test-key",
        groupKey = null,
        category = category,
        postedAt = 1_700_000_000_000L,
        receivedAt = 1_700_000_000_000L
    )

    @Test
    fun `微信支付成功生成支出并抽取商户分类`() {
        val outcome = engine.parse(
            payload("支付成功\n¥25.00\n瑞幸咖啡(国贸店)", title = "微信支付"),
            listOf(wechatRule),
            fallbackEnabled = true
        )
        assertTrue(outcome is ParseOutcome.Matched)
        val matched = outcome as ParseOutcome.Matched
        assertEquals(2_500L, matched.amount)
        assertEquals(Direction.EXPENSE, matched.direction)
        assertEquals("瑞幸咖啡(国贸店)", matched.merchant)
        assertEquals("餐饮", matched.categoryName)
    }

    @Test
    fun `支付成功后的优惠券文案不被噪音误杀`() {
        val outcome = engine.parse(
            payload("支付成功¥25.00已为你节省5元优惠券", title = "微信支付"),
            listOf(wechatRule),
            fallbackEnabled = true
        )
        assertTrue(outcome is ParseOutcome.Matched)
        assertEquals(2_500L, (outcome as ParseOutcome.Matched).amount)
    }

    @Test
    fun `活动满减文案被L1拦截`() {
        val outcome = engine.parse(
            payload("限时活动！满100减20，速来抢购", title = "微信支付"),
            listOf(wechatRule),
            fallbackEnabled = true
        )
        assertTrue(outcome is ParseOutcome.Rejected)
        assertEquals("L1", (outcome as ParseOutcome.Rejected).layer)
    }

    @Test
    fun `推广类型通知被L0拦截`() {
        val outcome = engine.parse(
            payload("支付成功¥10.00", title = "微信支付", category = "promo"),
            listOf(wechatRule),
            fallbackEnabled = true
        )
        assertTrue(outcome is ParseOutcome.Rejected)
        assertEquals("L0", (outcome as ParseOutcome.Rejected).layer)
    }

    @Test
    fun `匹配规则但无金额被L3拦截`() {
        val outcome = engine.parse(
            payload("微信支付\n支付成功", title = "微信支付"),
            listOf(wechatRule),
            fallbackEnabled = true
        )
        assertTrue(outcome is ParseOutcome.Rejected)
        assertEquals("L3", (outcome as ParseOutcome.Rejected).layer)
    }

    @Test
    fun `退款优先识别为收入`() {
        val outcome = engine.parse(
            payload("微信退款到账¥18.60", title = "微信支付"),
            listOf(wechatRule),
            fallbackEnabled = true
        )
        assertTrue(outcome is ParseOutcome.Matched)
        val matched = outcome as ParseOutcome.Matched
        assertEquals(Direction.INCOME, matched.direction)
        assertEquals(1_860L, matched.amount)
        assertEquals("退款", matched.categoryName)
    }

    @Test
    fun `红包到账识别为收入且不被红包噪音拦截`() {
        val outcome = engine.parse(
            payload("红包到账¥66.00", title = "微信支付"),
            listOf(wechatRule),
            fallbackEnabled = true
        )
        assertTrue(outcome is ParseOutcome.Matched)
        val matched = outcome as ParseOutcome.Matched
        assertEquals(Direction.INCOME, matched.direction)
        assertEquals("红包", matched.categoryName)
    }

    @Test
    fun `营销红包金额不会被当成交易金额`() {
        val outcome = engine.parse(
            payload("送你一张8元优惠券", title = "微信支付"),
            listOf(wechatRule),
            fallbackEnabled = true
        )
        assertTrue(outcome !is ParseOutcome.Matched)
    }

    @Test
    fun `无专属规则的应用走通用规则并进入待确认`() {
        val outcome = engine.parse(
            payload("支付成功 ￥30.00", packageName = "com.example.shop"),
            emptyList(),
            fallbackEnabled = true
        )
        assertTrue(outcome is ParseOutcome.Pending)
        assertEquals(3_000L, (outcome as ParseOutcome.Pending).amount)
    }

    @Test
    fun `有专属规则但未匹配时不再走通用兜底`() {
        val outcome = engine.parse(
            payload("你的快递已发出", packageName = "com.tencent.mm"),
            listOf(wechatRule),
            fallbackEnabled = true
        )
        assertEquals(ParseOutcome.Ignored, outcome)
    }

    @Test
    fun `通用规则关闭时忽略未匹配通知`() {
        val outcome = engine.parse(
            payload("支付成功 ￥30.00", packageName = "com.example.shop"),
            emptyList(),
            fallbackEnabled = false
        )
        assertEquals(ParseOutcome.Ignored, outcome)
    }

    @Test
    fun `规则排除词命中L2`() {
        val rule = wechatRule.copy(excludeKeywords = listOf("会员"))
        val outcome = engine.parse(
            payload("微信支付 支付成功 ¥10.00 会员", title = "微信支付"),
            listOf(rule),
            fallbackEnabled = true
        )
        assertTrue(outcome is ParseOutcome.Rejected)
        assertEquals("L2", (outcome as ParseOutcome.Rejected).layer)
    }

    @Test
    fun `方向无法判定进入待确认`() {
        val outcome = engine.parse(
            payload("微信转账 25.00元"),
            listOf(wechatRule),
            fallbackEnabled = true
        )
        assertTrue(outcome is ParseOutcome.Pending)
        assertNull((outcome as ParseOutcome.Pending).direction)
    }

    @Test
    fun `金额解析支持元字粘连与千分位`() {
        assertEquals(1_250L, AmountParser.bestAmount("支付成功 12.50元")?.cents)
        assertEquals(128_640L, AmountParser.bestAmount("支付成功 ¥1,286.40")?.cents)
        assertNull(AmountParser.bestAmount("满100减20"))
    }

    @Test
    fun `方向词收入优先`() {
        assertEquals(Direction.INCOME, DirectionParser.detect("支付成功，退款到账"))
        assertEquals(Direction.EXPENSE, DirectionParser.detect("微信支付成功"))
        assertEquals(Direction.UNKNOWN, DirectionParser.detect("你的快递已发出"))
    }

    @Test
    fun `模糊匹配忽略空格与标点`() {
        val fuzzyRule = wechatRule.copy(
            literalGroups = listOf(listOf("微信支付", "支付成功")),
            fuzzy = true
        )
        assertTrue(
            TemplateMatcher.matches("微 信 支 付\n支 付 成 功", fuzzyRule)
        )
    }

    @Test
    fun `Canonical_merchant_null_when_only_order_text`() {
        assertNull(MerchantExtractor.extract("支付成功 ￥30.00，订单已提交"))
        assertNotNull(MerchantExtractor.extract("支付成功 ¥25.00 瑞幸咖啡(国贸店)"))
    }
}
