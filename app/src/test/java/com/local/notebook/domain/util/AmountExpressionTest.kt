package com.local.notebook.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class AmountExpressionTest {

    @Test
    fun `加法与小数`() {
        assertEquals(2_050L, AmountExpression.toCents(AmountExpression.evaluate("12.5+8")!!))
    }

    @Test
    fun `乘除优先级`() {
        assertEquals(0, AmountExpression.evaluate("1+2×3")!!.compareTo(BigDecimal("7")))
        assertEquals(250L, AmountExpression.toCents(AmountExpression.evaluate("10÷4")!!))
    }

    @Test
    fun `非法算式返回空`() {
        assertNull(AmountExpression.evaluate("1+"))
        assertNull(AmountExpression.evaluate("abc"))
        assertNull(AmountExpression.evaluate("1/0"))
        assertNull(AmountExpression.evaluate(""))
    }
}
