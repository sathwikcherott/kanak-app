package com.kanak.app.data.backup

import com.kanak.app.data.local.datastore.ThemePreference
import com.kanak.app.model.CategorizationState
import com.kanak.app.model.EntrySource
import com.kanak.app.model.ExpenseCategory

object KanakBackupSerializer {

    fun toJson(backup: KanakBackup): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"version\": ").append(backup.version).append(",\n")
        sb.append("  \"exportedAt\": ").append(backup.exportedAt).append(",\n")
        sb.append("  \"appVersion\": \"").append(escapeJson(backup.appVersion)).append("\",\n")
        sb.append("  \"preferences\": {\n")
        sb.append("    \"defaultCurrency\": \"").append(escapeJson(backup.preferences.defaultCurrency)).append("\",\n")
        sb.append("    \"themePreference\": \"").append(escapeJson(backup.preferences.themePreference.name)).append("\"\n")
        sb.append("  },\n")
        sb.append("  \"expenses\": [\n")
        backup.expenses.forEachIndexed { index, exp ->
            sb.append("    {\n")
            sb.append("      \"id\": ").append(exp.id).append(",\n")
            sb.append("      \"amountMinor\": ").append(exp.amountMinor).append(",\n")
            sb.append("      \"currency\": \"").append(escapeJson(exp.currency)).append("\",\n")
            sb.append("      \"occurredAt\": ").append(exp.occurredAt).append(",\n")
            sb.append("      \"note\": ").append(if (exp.note != null) "\"${escapeJson(exp.note)}\"" else "null").append(",\n")
            sb.append("      \"category\": ").append(if (exp.category != null) "\"${exp.category.name}\"" else "null").append(",\n")
            sb.append("      \"source\": \"").append(exp.source.name).append("\",\n")
            sb.append("      \"categorizationState\": \"").append(exp.categorizationState.name).append("\"\n")
            sb.append("    }").append(if (index < backup.expenses.size - 1) ",\n" else "\n")
        }
        sb.append("  ]\n")
        sb.append("}")
        return sb.toString()
    }

    fun fromJson(jsonString: String): KanakBackup {
        val rootMap = try {
            SimpleJsonParser(jsonString).parseRoot()
        } catch (e: Exception) {
            throw BackupException.CorruptedJson("Invalid JSON structure: ${e.message}")
        }

        val versionNum = (rootMap["version"] as? Number)?.toInt()
            ?: throw BackupException.CorruptedJson("Missing or invalid 'version' in backup")

        if (versionNum > KanakBackup.CURRENT_BACKUP_VERSION) {
            throw BackupException.UnsupportedSchemaVersion(versionNum)
        }
        if (versionNum <= 0) {
            throw BackupException.CorruptedJson("Invalid schema version: $versionNum")
        }

        val exportedAt = (rootMap["exportedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val appVersion = rootMap["appVersion"] as? String ?: "1.0"

        val prefMap = rootMap["preferences"] as? Map<*, *>
        val defaultCurrency = prefMap?.get("defaultCurrency") as? String ?: "INR"
        val themePrefStr = prefMap?.get("themePreference") as? String ?: "DARK"
        val themePreference = try {
            ThemePreference.valueOf(themePrefStr)
        } catch (_: Exception) {
            ThemePreference.DARK
        }

        val preferences = BackupPreferences(
            defaultCurrency = defaultCurrency,
            themePreference = themePreference
        )

        val expensesList = rootMap["expenses"] as? List<*>
            ?: throw BackupException.CorruptedJson("Missing or invalid 'expenses' list")

        val seenIds = HashSet<Long>()
        val backupExpenses = mutableListOf<BackupExpense>()

        expensesList.forEachIndexed { index, item ->
            val expMap = item as? Map<*, *>
                ?: throw BackupException.CorruptedJson("Expense at index $index is not a valid JSON object")

            val id = (expMap["id"] as? Number)?.toLong()
                ?: throw BackupException.MissingRequiredField("Missing required field 'id' at index $index")

            if (!seenIds.add(id)) {
                throw BackupException.DuplicateExpenseIds("Duplicate expense ID found: $id")
            }

            val amountMinor = (expMap["amountMinor"] as? Number)?.toLong()
                ?: throw BackupException.MissingRequiredField("Missing required field 'amountMinor' for expense ID $id")

            if (amountMinor < 0L) {
                throw BackupException.InvalidMonetaryValue("Negative amount ($amountMinor) for expense ID $id")
            }

            val occurredAt = (expMap["occurredAt"] as? Number)?.toLong()
                ?: throw BackupException.MissingRequiredField("Missing required field 'occurredAt' for expense ID $id")

            val currency = expMap["currency"] as? String ?: "INR"
            val note = expMap["note"] as? String

            val categoryStr = expMap["category"] as? String
            val category = if (categoryStr != null) {
                try {
                    ExpenseCategory.valueOf(categoryStr)
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }

            val sourceStr = expMap["source"] as? String ?: "TYPED"
            val source = try {
                EntrySource.valueOf(sourceStr)
            } catch (_: Exception) {
                EntrySource.TYPED
            }

            val catStateStr = expMap["categorizationState"] as? String ?: "CONFIRMED"
            val categorizationState = try {
                CategorizationState.valueOf(catStateStr)
            } catch (_: Exception) {
                CategorizationState.CONFIRMED
            }

            backupExpenses.add(
                BackupExpense(
                    id = id,
                    amountMinor = amountMinor,
                    currency = currency,
                    occurredAt = occurredAt,
                    note = note,
                    category = category,
                    source = source,
                    categorizationState = categorizationState
                )
            )
        }

        return KanakBackup(
            version = versionNum,
            exportedAt = exportedAt,
            appVersion = appVersion,
            preferences = preferences,
            expenses = backupExpenses
        )
    }

    fun summarize(backup: KanakBackup): BackupSummary {
        val uniqueCategories = backup.expenses.mapNotNull { it.category }.distinct().size
        return BackupSummary(
            version = backup.version,
            exportedAt = backup.exportedAt,
            totalExpenses = backup.expenses.size,
            totalCategoriesCount = uniqueCategories,
            defaultCurrency = backup.preferences.defaultCurrency,
            themePreference = backup.preferences.themePreference,
            backup = backup
        )
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\u000C", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}

private class SimpleJsonParser(json: String) {
    private var index = 0
    private val trimmed = json.trim()

    fun parseRoot(): Map<String, Any?> {
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw IllegalArgumentException("Root must be a JSON object")
        }
        return parseObject()
    }

    private fun skipWhitespace() {
        while (index < trimmed.length && trimmed[index].isWhitespace()) {
            index++
        }
    }

    private fun parseString(): String {
        if (index >= trimmed.length || trimmed[index] != '"') {
            throw IllegalArgumentException("Expected string at offset $index")
        }
        index++
        val sb = StringBuilder()
        while (index < trimmed.length) {
            val c = trimmed[index++]
            if (c == '"') return sb.toString()
            if (c == '\\' && index < trimmed.length) {
                when (val escaped = trimmed[index++]) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> {
                        if (index + 4 <= trimmed.length) {
                            val hex = trimmed.substring(index, index + 4)
                            index += 4
                            sb.append(hex.toInt(16).toChar())
                        }
                    }
                    else -> sb.append(escaped)
                }
            } else {
                sb.append(c)
            }
        }
        throw IllegalArgumentException("Unterminated string")
    }

    private fun parseObject(): Map<String, Any?> {
        if (index >= trimmed.length || trimmed[index] != '{') {
            throw IllegalArgumentException("Expected '{' at offset $index")
        }
        index++
        skipWhitespace()
        val map = LinkedHashMap<String, Any?>()
        if (index < trimmed.length && trimmed[index] == '}') {
            index++
            return map
        }
        while (index < trimmed.length) {
            skipWhitespace()
            val key = parseString()
            skipWhitespace()
            if (index >= trimmed.length || trimmed[index] != ':') {
                throw IllegalArgumentException("Expected ':' after key '$key'")
            }
            index++
            skipWhitespace()
            val value = parseValue()
            map[key] = value
            skipWhitespace()
            if (index < trimmed.length && trimmed[index] == ',') {
                index++
                skipWhitespace()
            } else if (index < trimmed.length && trimmed[index] == '}') {
                index++
                return map
            }
        }
        throw IllegalArgumentException("Unterminated object")
    }

    private fun parseArray(): List<Any?> {
        if (index >= trimmed.length || trimmed[index] != '[') {
            throw IllegalArgumentException("Expected '[' at offset $index")
        }
        index++
        skipWhitespace()
        val list = ArrayList<Any?>()
        if (index < trimmed.length && trimmed[index] == ']') {
            index++
            return list
        }
        while (index < trimmed.length) {
            skipWhitespace()
            val value = parseValue()
            list.add(value)
            skipWhitespace()
            if (index < trimmed.length && trimmed[index] == ',') {
                index++
                skipWhitespace()
            } else if (index < trimmed.length && trimmed[index] == ']') {
                index++
                return list
            }
        }
        throw IllegalArgumentException("Unterminated array")
    }

    private fun parseValue(): Any? {
        skipWhitespace()
        if (index >= trimmed.length) throw IllegalArgumentException("Unexpected end of input")
        return when (val c = trimmed[index]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't', 'f' -> {
                if (trimmed.startsWith("true", index)) {
                    index += 4
                    true
                } else if (trimmed.startsWith("false", index)) {
                    index += 5
                    false
                } else throw IllegalArgumentException("Invalid boolean at offset $index")
            }
            'n' -> {
                if (trimmed.startsWith("null", index)) {
                    index += 4
                    null
                } else throw IllegalArgumentException("Invalid null at offset $index")
            }
            '-', in '0'..'9' -> {
                val start = index
                if (c == '-') index++
                while (index < trimmed.length && (trimmed[index].isDigit() || trimmed[index] == '.' || trimmed[index] == 'e' || trimmed[index] == 'E' || trimmed[index] == '+' || trimmed[index] == '-')) {
                    index++
                }
                val numStr = trimmed.substring(start, index)
                numStr.toLongOrNull() ?: numStr.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number $numStr")
            }
            else -> throw IllegalArgumentException("Unexpected character '$c' at offset $index")
        }
    }
}
