package com.kanak.app.data.backup

import com.kanak.app.data.local.datastore.ThemePreference
import com.kanak.app.model.CategorizationState
import com.kanak.app.model.EntrySource
import com.kanak.app.model.ExpenseCategory

object KanakBackupSerializer {

    fun toJson(backup: KanakBackup): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"format\": \"").append(escapeJson(backup.format)).append("\",\n")
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
        val rootMap = SimpleJsonParser(jsonString).parseRoot()

        // Format validation
        val format = rootMap["format"] as? String
            ?: throw BackupException.InvalidFormat("Missing or invalid 'format' identifier in backup")
        if (format != KanakBackup.FORMAT_IDENTIFIER) {
            throw BackupException.InvalidFormat("Unsupported backup format: '$format'")
        }

        // Schema version validation
        val versionRaw = rootMap["version"]
        if (versionRaw !is Long) {
            throw BackupException.CorruptedJson("Missing or invalid integer 'version' in backup")
        }
        val version = versionRaw.toInt()
        if (version != KanakBackup.CURRENT_BACKUP_VERSION) {
            throw BackupException.UnsupportedSchemaVersion(version)
        }

        val exportedAtRaw = rootMap["exportedAt"]
        if (exportedAtRaw !is Long) {
            throw BackupException.CorruptedJson("Missing or invalid integer 'exportedAt' timestamp in backup")
        }

        val appVersion = rootMap["appVersion"] as? String ?: "1.0"

        // Preferences validation
        val prefMap = rootMap["preferences"] as? Map<*, *>
            ?: throw BackupException.MissingRequiredField("Missing 'preferences' object in backup")

        val defaultCurrency = prefMap["defaultCurrency"] as? String
            ?: throw BackupException.MissingRequiredField("Missing 'defaultCurrency' in preferences")
        if (defaultCurrency.isBlank()) {
            throw BackupException.MissingRequiredField("Blank 'defaultCurrency' in preferences")
        }

        val themePrefStr = prefMap["themePreference"] as? String
            ?: throw BackupException.MissingRequiredField("Missing 'themePreference' in preferences")
        val themePreference = try {
            ThemePreference.valueOf(themePrefStr)
        } catch (_: Exception) {
            throw BackupException.UnknownEnum("Unknown theme preference: '$themePrefStr'")
        }

        val preferences = BackupPreferences(
            defaultCurrency = defaultCurrency,
            themePreference = themePreference,
        )

        // Expenses array validation
        val expensesList = rootMap["expenses"] as? List<*>
            ?: throw BackupException.CorruptedJson("Missing or invalid 'expenses' list in backup")

        if (expensesList.size > KanakBackup.MAX_EXPENSE_RECORDS) {
            throw BackupException.LimitExceeded("Backup exceeds maximum record count (${KanakBackup.MAX_EXPENSE_RECORDS})")
        }

        val seenIds = HashSet<Long>()
        val backupExpenses = ArrayList<BackupExpense>(expensesList.size)

        expensesList.forEachIndexed { index, item ->
            val expMap = item as? Map<*, *>
                ?: throw BackupException.CorruptedJson("Expense item at index $index is not a valid JSON object")

            // ID validation
            val idRaw = expMap["id"]
            if (idRaw !is Long) {
                throw BackupException.MissingRequiredField("Missing or invalid integer 'id' for expense record at index $index")
            }
            if (idRaw <= 0L) {
                throw BackupException.MissingRequiredField("Invalid non-positive ID ($idRaw) for expense record at index $index")
            }
            if (!seenIds.add(idRaw)) {
                throw BackupException.DuplicateExpenseIds("Duplicate expense ID found: $idRaw")
            }

            // Amount validation (strictly positive: amount > 0L)
            val amountRaw = expMap["amountMinor"]
            if (amountRaw !is Long) {
                throw BackupException.InvalidMonetaryValue("Missing or non-integer 'amountMinor' for expense ID $idRaw")
            }
            if (amountRaw <= 0L) {
                throw BackupException.InvalidMonetaryValue("Invalid zero or negative monetary amount ($amountRaw) for expense ID $idRaw")
            }

            // Currency validation
            val currency = expMap["currency"] as? String
                ?: throw BackupException.MissingRequiredField("Missing 'currency' for expense ID $idRaw")
            if (currency.isBlank()) {
                throw BackupException.MissingRequiredField("Blank currency for expense ID $idRaw")
            }

            // Timestamp validation
            val occurredAtRaw = expMap["occurredAt"]
            if (occurredAtRaw !is Long || occurredAtRaw <= 0L) {
                throw BackupException.MissingRequiredField("Missing or invalid 'occurredAt' timestamp for expense ID $idRaw")
            }

            // Note (optional)
            val note = expMap["note"] as? String

            // Category validation (nullable, but if non-null must match enum strictly)
            val categoryStr = expMap["category"] as? String
            val category: ExpenseCategory? = if (categoryStr != null) {
                try {
                    ExpenseCategory.valueOf(categoryStr)
                } catch (_: Exception) {
                    throw BackupException.UnknownEnum("Unknown expense category: '$categoryStr' for expense ID $idRaw")
                }
            } else {
                null
            }

            // Source validation
            val sourceStr = expMap["source"] as? String
                ?: throw BackupException.MissingRequiredField("Missing 'source' for expense ID $idRaw")
            val source = try {
                EntrySource.valueOf(sourceStr)
            } catch (_: Exception) {
                throw BackupException.UnknownEnum("Unknown entry source: '$sourceStr' for expense ID $idRaw")
            }

            // CategorizationState validation
            val stateStr = expMap["categorizationState"] as? String
                ?: throw BackupException.MissingRequiredField("Missing 'categorizationState' for expense ID $idRaw")
            val categorizationState = try {
                CategorizationState.valueOf(stateStr)
            } catch (_: Exception) {
                throw BackupException.UnknownEnum("Unknown categorization state: '$stateStr' for expense ID $idRaw")
            }

            backupExpenses.add(
                BackupExpense(
                    id = idRaw,
                    amountMinor = amountRaw,
                    currency = currency,
                    occurredAt = occurredAtRaw,
                    note = note,
                    category = category,
                    source = source,
                    categorizationState = categorizationState
                )
            )
        }

        return KanakBackup(
            format = format,
            version = version,
            exportedAt = exportedAtRaw,
            appVersion = appVersion,
            preferences = preferences,
            expenses = backupExpenses.toList()
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

private class SimpleJsonParser(private val json: String) {
    private var index = 0
    private val len = json.length

    init {
        if (json.toByteArray(Charsets.UTF_8).size > KanakBackup.MAX_BACKUP_BYTES) {
            throw BackupException.LimitExceeded("Backup payload exceeds maximum byte size of 50MB")
        }
    }

    fun parseRoot(): Map<String, Any?> {
        skipWhitespace()
        if (index >= len || json[index] != '{') {
            throw BackupException.CorruptedJson("Root must be a JSON object")
        }
        val root = parseObject()
        skipWhitespace()
        if (index < len) {
            throw BackupException.CorruptedJson("Extra characters after JSON root object")
        }
        return root
    }

    private fun skipWhitespace() {
        while (index < len && json[index].isWhitespace()) {
            index++
        }
    }

    private fun parseString(): String {
        if (index >= len || json[index] != '"') {
            throw BackupException.CorruptedJson("Expected double-quoted string at position $index")
        }
        index++
        val sb = StringBuilder()
        while (index < len) {
            val c = json[index++]
            if (c == '"') return sb.toString()
            if (c == '\\') {
                if (index >= len) throw BackupException.CorruptedJson("Unterminated escape sequence in string")
                when (val escaped = json[index++]) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> {
                        if (index + 4 <= len) {
                            val hex = json.substring(index, index + 4)
                            index += 4
                            try {
                                sb.append(hex.toInt(16).toChar())
                            } catch (_: Exception) {
                                throw BackupException.CorruptedJson("Invalid unicode escape \\u$hex")
                            }
                        } else {
                            throw BackupException.CorruptedJson("Truncated unicode escape sequence")
                        }
                    }
                    else -> throw BackupException.CorruptedJson("Invalid escape character '\\$escaped'")
                }
            } else {
                sb.append(c)
            }
        }
        throw BackupException.CorruptedJson("Unterminated string literal")
    }

    private fun parseObject(): Map<String, Any?> {
        if (index >= len || json[index] != '{') {
            throw BackupException.CorruptedJson("Expected '{' at position $index")
        }
        index++
        skipWhitespace()
        val map = LinkedHashMap<String, Any?>()
        if (index < len && json[index] == '}') {
            index++
            return map
        }

        while (index < len) {
            skipWhitespace()
            if (index >= len || json[index] != '"') {
                throw BackupException.CorruptedJson("Expected string key in JSON object at position $index")
            }
            val key = parseString()
            if (map.containsKey(key)) {
                throw BackupException.CorruptedJson("Duplicate JSON key detected: '$key'")
            }
            skipWhitespace()
            if (index >= len || json[index] != ':') {
                throw BackupException.CorruptedJson("Expected ':' after key '$key'")
            }
            index++
            skipWhitespace()
            val value = parseValue()
            map[key] = value
            skipWhitespace()
            if (index < len && json[index] == ',') {
                index++
                skipWhitespace()
                if (index < len && json[index] == '}') {
                    throw BackupException.CorruptedJson("Trailing comma in object at position $index")
                }
            } else if (index < len && json[index] == '}') {
                index++
                return map
            } else {
                throw BackupException.CorruptedJson("Expected ',' or '}' in object at position $index")
            }
        }
        throw BackupException.CorruptedJson("Unterminated object literal")
    }

    private fun parseArray(): List<Any?> {
        if (index >= len || json[index] != '[') {
            throw BackupException.CorruptedJson("Expected '[' at position $index")
        }
        index++
        skipWhitespace()
        val list = ArrayList<Any?>()
        if (index < len && json[index] == ']') {
            index++
            return list
        }

        while (index < len) {
            skipWhitespace()
            val value = parseValue()
            list.add(value)
            skipWhitespace()
            if (index < len && json[index] == ',') {
                index++
                skipWhitespace()
                if (index < len && json[index] == ']') {
                    throw BackupException.CorruptedJson("Trailing comma in array at position $index")
                }
            } else if (index < len && json[index] == ']') {
                index++
                return list
            } else {
                throw BackupException.CorruptedJson("Expected ',' or ']' in array at position $index")
            }
        }
        throw BackupException.CorruptedJson("Unterminated array literal")
    }

    private fun parseValue(): Any? {
        skipWhitespace()
        if (index >= len) throw BackupException.CorruptedJson("Unexpected end of JSON input")
        return when (val c = json[index]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't', 'f' -> {
                if (json.startsWith("true", index)) {
                    index += 4
                    true
                } else if (json.startsWith("false", index)) {
                    index += 5
                    false
                } else throw BackupException.CorruptedJson("Invalid boolean literal at position $index")
            }
            'n' -> {
                if (json.startsWith("null", index)) {
                    index += 4
                    null
                } else throw BackupException.CorruptedJson("Invalid null literal at position $index")
            }
            '-', in '0'..'9' -> parseNumber()
            else -> throw BackupException.CorruptedJson("Unexpected character '$c' at position $index")
        }
    }

    private fun parseNumber(): Number {
        val start = index
        if (json[index] == '-') index++
        var hasDecimal = false
        var hasExponent = false

        while (index < len) {
            val c = json[index]
            if (c.isDigit()) {
                index++
            } else if (c == '.') {
                if (hasDecimal || hasExponent) throw BackupException.CorruptedJson("Malformed number at position $index")
                hasDecimal = true
                index++
            } else if (c == 'e' || c == 'E') {
                if (hasExponent) throw BackupException.CorruptedJson("Malformed number at position $index")
                hasExponent = true
                index++
                if (index < len && (json[index] == '+' || json[index] == '-')) {
                    index++
                }
            } else {
                break
            }
        }

        val numStr = json.substring(start, index)
        if (hasDecimal || hasExponent) {
            return numStr.toDoubleOrNull() ?: throw BackupException.CorruptedJson("Invalid double value: $numStr")
        }

        return try {
            numStr.toLong()
        } catch (_: NumberFormatException) {
            throw BackupException.CorruptedJson("Integer overflow in number: $numStr")
        }
    }
}
