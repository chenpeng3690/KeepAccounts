package com.keepaccounts.data.parse

import com.keepaccounts.data.local.db.AppDatabase
import com.keepaccounts.data.local.db.RuleEntity
import com.keepaccounts.data.local.db.RuleState

/**
 * 内置规则。开箱即用，`builtin=true`（可停用、可编辑，不可删除）。
 *
 * 覆盖微信 / 支付宝最常见的支付、收款、退款、收益形态，用片段模板的「有序包含」
 * 匹配，不写死全等。金额、方向、商户、分类由引擎的抽取器统一负责，这里只做两件事：
 * 1. 用一句固定文案确认「这确实是一条支付类通知」；
 * 2. 给某些形态指定默认分类（如余额宝收益 → 投资收益）。
 *
 * 注意：这些字面量是根据通知的通用规律写的，微信/支付宝改版后可能失配，
 * 需要拿真机样本回来微调 —— 这也是第 5 步先「只采集不解析」的原因。
 */
object BuiltinRules {

    private data class Spec(
        val name: String,
        val packageName: String,
        val priority: Int,
        val literal: String,
        val defaultCategoryName: String? = null
    )

    private val WECHAT = "com.tencent.mm"
    private val ALIPAY = "com.eg.android.AlipayGphone"

    private val specs = listOf(
        Spec("微信·退款到账", WECHAT, 10, "退款", "退款"),
        Spec("微信·收款到账", WECHAT, 20, "收款"),
        Spec("微信·支付成功", WECHAT, 30, "支付成功"),
        Spec("支付宝·余额宝收益", ALIPAY, 10, "收益", "投资收益"),
        Spec("支付宝·退款", ALIPAY, 20, "退款", "退款"),
        Spec("支付宝·收款到账", ALIPAY, 30, "收款"),
        Spec("支付宝·付款成功", ALIPAY, 40, "付款成功"),
        Spec("支付宝·支付成功", ALIPAY, 50, "支付成功")
    )

    suspend fun ensure(db: AppDatabase) {
        val ruleDao = db.ruleDao()
        if (ruleDao.countBuiltin() > 0) return

        val categories = db.categoryDao().listAll().associateBy { it.name }
        val now = System.currentTimeMillis()

        specs.forEach { s ->
            val catId = s.defaultCategoryName?.let { categories[it]?.id }
            ruleDao.insert(
                RuleEntity(
                    name = s.name,
                    packageName = s.packageName,
                    priority = s.priority,
                    builtin = true,
                    state = RuleState.ACTIVE,
                    templateJson = """[{"t":"literal","v":${json(s.literal)}}]""",
                    defaultCategoryId = catId,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

        // 通用兜底规则：无 packageName，模板为空，命中一律进待确认箱
        ruleDao.insert(
            RuleEntity(
                name = "通用兜底",
                packageName = null,
                priority = 100,
                builtin = true,
                state = RuleState.ACTIVE,
                templateJson = "[]",
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private fun json(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
