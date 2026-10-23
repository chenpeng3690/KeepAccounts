package com.local.notebook.data.backup

object CsvCodec {

    fun escape(value: String?): String {
        val text = value.orEmpty()
        return if (text.contains(',') || text.contains('"') || text.contains('\n') || text.contains('\r')) {
            "\"" + text.replace("\"", "\"\"") + "\""
        } else {
            text
        }
    }

    fun encode(header: List<String>, rows: List<List<String?>>): String = buildString {
        append(header.joinToString(",") { escape(it) })
        append("\r\n")
        rows.forEach { row ->
            append(row.joinToString(",") { escape(it) })
            append("\r\n")
        }
    }

    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0
        val content = text.removePrefix("\uFEFF")

        while (index < content.length) {
            val ch = content[index]
            when {
                inQuotes -> {
                    if (ch == '"') {
                        if (index + 1 < content.length && content[index + 1] == '"') {
                            field.append('"')
                            index++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        field.append(ch)
                    }
                }
                ch == '"' -> inQuotes = true
                ch == ',' -> {
                    row.add(field.toString())
                    field.clear()
                }
                ch == '\r' -> {
                    if (index + 1 < content.length && content[index + 1] == '\n') index++
                    row.add(field.toString())
                    field.clear()
                    if (row.size > 1 || row.first().isNotEmpty()) rows.add(row)
                    row = mutableListOf()
                }
                ch == '\n' -> {
                    row.add(field.toString())
                    field.clear()
                    if (row.size > 1 || row.first().isNotEmpty()) rows.add(row)
                    row = mutableListOf()
                }
                else -> field.append(ch)
            }
            index++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            if (row.size > 1 || row.first().isNotEmpty()) rows.add(row)
        }
        return rows
    }
}
