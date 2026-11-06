package com.kanak.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MathExpressionParserTest {

    @Test
    fun evaluate_addition() {
        assertEquals("75", MathExpressionParser.evaluate("25 + 50"))
        assertEquals("170", MathExpressionParser.evaluate("100 + 50 + 20"))
    }

    @Test
    fun evaluate_subtraction() {
        assertEquals("150", MathExpressionParser.evaluate("200 - 50"))
        assertEquals("70", MathExpressionParser.evaluate("100 - 20 - 10"))
    }

    @Test
    fun evaluate_multiplication() {
        assertEquals("100", MathExpressionParser.evaluate("25 * 4"))
        assertEquals("100", MathExpressionParser.evaluate("25 x 4"))
        assertEquals("100", MathExpressionParser.evaluate("25 × 4"))
    }

    @Test
    fun evaluate_division() {
        assertEquals("250", MathExpressionParser.evaluate("500 / 2"))
        assertEquals("250", MathExpressionParser.evaluate("500 ÷ 2"))
    }

    @Test
    fun evaluate_operatorPrecedence() {
        // Multiplication & Division before Addition & Subtraction
        assertEquals("70", MathExpressionParser.evaluate("10 + 20 * 3"))
        assertEquals("110", MathExpressionParser.evaluate("100 + 20 / 2"))
    }

    @Test
    fun evaluate_parentheses() {
        assertEquals("90", MathExpressionParser.evaluate("(10 + 20) * 3"))
        assertEquals("60", MathExpressionParser.evaluate("100 / (2 + 3) * 3"))
    }

    @Test
    fun evaluate_decimalAmounts() {
        assertEquals("75.75", MathExpressionParser.evaluate("25.50 + 50.25"))
        assertEquals("12.5", MathExpressionParser.evaluate("25 / 2"))
    }

    @Test
    fun evaluate_divisionByZero_returnsNull() {
        assertNull(MathExpressionParser.evaluate("500 / 0"))
    }

    @Test
    fun evaluate_invalidSyntax_returnsNull() {
        assertNull(MathExpressionParser.evaluate("25 +"))
        assertNull(MathExpressionParser.evaluate("25 + + 50"))
        assertNull(MathExpressionParser.evaluate("abc"))
    }

    @Test
    fun evaluate_negativeOrZeroResult_returnsNull() {
        assertNull(MathExpressionParser.evaluate("20 - 50"))
        assertNull(MathExpressionParser.evaluate("50 - 50"))
    }
}
