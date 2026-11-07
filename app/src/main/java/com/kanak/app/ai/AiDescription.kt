package com.kanak.app.ai

object AiDescription {
    private val numbersAndCurrency = Regex("""\p{N}+(?:[.,:/-]\p{N}+)*|\p{Sc}""")
    private val spaces = Regex("""[\s\p{Z}\p{Cc}]+""")

    fun forSuggestion(note: String): String = note
        .replace(numbersAndCurrency, " ")
        .replace(spaces, " ")
        .trim()
        .take(500)
        .trimEnd()
}
