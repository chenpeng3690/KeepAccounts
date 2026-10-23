package com.local.notebook.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvCodecTest {

    @Test
    fun `解析包含逗号和引号的字段`() {
        val rows = CsvCodec.parse("a,b\n\"含,逗号\",\"他说\"\"你好\"\"\"")
        assertEquals(2, rows.size)
        assertEquals(listOf("含,逗号", "他说\"你好\""), rows[1])
    }

    @Test
    fun `解析引号内换行`() {
        val rows = CsvCodec.parse("name,note\n商品,\"第一行\n第二行\"")
        assertEquals(2, rows.size)
        assertEquals("第一行\n第二行", rows[1][1])
    }

    @Test
    fun `编码后再解析可还原`() {
        val header = listOf("id", "merchant", "note")
        val rows = listOf(
            listOf("1", "瑞幸,咖啡", "备注\"引号\""),
            listOf("2", "美团", "换行\n内容")
        )
        val encoded = CsvCodec.encode(header, rows)
        val parsed = CsvCodec.parse(encoded)
        assertEquals(3, parsed.size)
        assertEquals("瑞幸,咖啡", parsed[1][1])
        assertEquals("备注\"引号\"", parsed[1][2])
        assertEquals("换行\n内容", parsed[2][2])
    }

    @Test
    fun `空文本得到空列表`() {
        assertTrue(CsvCodec.parse("").isEmpty())
    }
}
