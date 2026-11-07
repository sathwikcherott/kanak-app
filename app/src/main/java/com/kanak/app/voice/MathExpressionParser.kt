package com.kanak.app.voice

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Deterministic mathematical expression parser supporting addition, subtraction,
 * multiplication, division, parentheses, decimals, and precedence rules.
 */
object MathExpressionParser {

    private const val MAX_LENGTH = 256

    fun hasOperator(input: String): Boolean = input.any { it in "+-−*xX×/÷()" }

    /**
     * Evaluates a mathematical expression string and returns the result as a positive BigDecimal,
     * or null if the expression is invalid, contains division by zero, or results in zero/negative values.
     */
    fun evaluateToBigDecimal(expression: String): BigDecimal? {
        val trimmed = expression.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH) return null

        val normalized = trimmed
            .replace('x', '*')
            .replace('X', '*')
            .replace('×', '*')
            .replace('÷', '/')
            .replace('−', '-')

        val tokens = tokenize(normalized) ?: return null
        if (tokens.isEmpty()) return null

        return try {
            val parser = Parser(tokens)
            val result = parser.parse() ?: return null
            if (result <= BigDecimal.ZERO) return null
            result
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Evaluates expression and returns formatted numeric string (e.g., "75", "150.5", "75.75"),
     * or null if evaluation fails or is non-positive.
     */
    fun evaluate(expression: String): String? {
        val bg = evaluateToBigDecimal(expression) ?: return null
        return formatBigDecimal(bg)
    }

    fun formatBigDecimal(value: BigDecimal): String {
        val stripped = value.stripTrailingZeros()
        return if (stripped.scale() <= 0) {
            stripped.toBigInteger().toString()
        } else {
            stripped.toPlainString()
        }
    }

    sealed class Token {
        data class Number(val value: BigDecimal) : Token()
        data class Operator(val op: Char) : Token()
        object LPAREN : Token()
        object RPAREN : Token()
    }

    private val groupedNumber = Regex("""(?:\d{1,3}(?:,\d{3})+|\d{1,2}(?:,\d{2})+,\d{3})(?:\.\d+)?""")

    private fun tokenize(input: String): List<Token>? {
        val tokens = mutableListOf<Token>()
        var i = 0
        val len = input.length

        while (i < len) {
            val ch = input[i]
            when {
                ch.isWhitespace() -> i++
                ch == '(' -> {
                    tokens.add(Token.LPAREN)
                    i++
                }
                ch == ')' -> {
                    tokens.add(Token.RPAREN)
                    i++
                }
                ch in "+-*/" -> {
                    tokens.add(Token.Operator(ch))
                    i++
                }
                ch.isDigit() || (ch == '.') || (ch == ',') -> {
                    val start = i
                    while (i < len && (input[i].isDigit() || input[i] in ".,")) i++
                    val number = input.substring(start, i)
                    val normalized = when {
                        groupedNumber.matches(number) -> number.replace(",", "")
                        else -> number.replace(',', '.')
                    }
                    val bd = normalized.toBigDecimalOrNull() ?: return null
                    tokens.add(Token.Number(bd))
                }
                else -> return null
            }
        }
        return tokens
    }

    private class Parser(private val tokens: List<Token>) {
        private var pos = 0

        fun parse(): BigDecimal? {
            for (k in 0 until tokens.size - 1) {
                if (tokens[k] is Token.Operator && tokens[k + 1] is Token.Operator) {
                    return null
                }
            }
            val result = parseExpression() ?: return null
            if (pos != tokens.size) return null
            return result
        }

        private fun parseExpression(): BigDecimal? {
            var left = parseTerm() ?: return null
            while (pos < tokens.size) {
                val tok = tokens[pos]
                if (tok is Token.Operator && (tok.op == '+' || tok.op == '-')) {
                    pos++
                    val right = parseTerm() ?: return null
                    left = if (tok.op == '+') left.add(right) else left.subtract(right)
                } else {
                    break
                }
            }
            return left
        }

        private fun parseTerm(): BigDecimal? {
            var left = parseFactor() ?: return null
            while (pos < tokens.size) {
                val tok = tokens[pos]
                if (tok is Token.Operator && (tok.op == '*' || tok.op == '/')) {
                    pos++
                    val right = parseFactor() ?: return null
                    if (tok.op == '*') {
                        left = left.multiply(right)
                    } else {
                        if (right.compareTo(BigDecimal.ZERO) == 0) return null
                        left = left.divide(right, 8, RoundingMode.HALF_UP)
                    }
                } else {
                    break
                }
            }
            return left
        }

        private fun parseFactor(): BigDecimal? {
            if (pos >= tokens.size) return null
            val tok = tokens[pos]

            if (tok is Token.Operator && (tok.op == '+' || tok.op == '-')) {
                pos++
                val factor = parseFactor() ?: return null
                return if (tok.op == '-') factor.negate() else factor
            }

            if (tok is Token.LPAREN) {
                pos++
                val expr = parseExpression() ?: return null
                if (pos >= tokens.size || tokens[pos] !is Token.RPAREN) return null
                pos++
                return expr
            }

            if (tok is Token.Number) {
                pos++
                return tok.value
            }

            return null
        }
    }
}
