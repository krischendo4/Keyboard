package com.privacykeyboard

data class Key(val label: String, val a: String, val w: Float = 1f, val alt: String? = null, val fn: Boolean = false)

/** Language Engine: раскладки en / ru / ko / uz (латиница) / uzc (кириллица). */
object Layouts {
    val ids = listOf("en", "ru", "ko", "uz", "uzc")
    val names = mapOf("en" to "English", "ru" to "Русский", "ko" to "한국어", "uz" to "Oʻzbekcha", "uzc" to "Ўзбекча")
    private val KO_SHIFT = mapOf("ㅂ" to "ㅃ", "ㅈ" to "ㅉ", "ㄷ" to "ㄸ", "ㄱ" to "ㄲ", "ㅅ" to "ㅆ", "ㅐ" to "ㅒ", "ㅔ" to "ㅖ")

    fun shifted(lang: String, s: String) = if (lang == "ko") (KO_SHIFT[s] ?: s) else s.uppercase()

    private fun chars(s: String, alt: Map<Char, String> = emptyMap()) = s.map { Key(it.toString(), it.toString(), 1f, alt[it]) }
    private fun spacer(w: Float) = Key("", "", w)
    private val NUM = chars("1234567890")
    private fun del() = Key("⌫", "DEL", 1.4f, fn = true)

    private fun bottom(first: Key, lang: String) = listOf(
        first, Key("🌐", "LANG", 1.1f, fn = true), Key(",", ",", 1f, "!"),
        Key(names[lang]!!, "SPACE", 4f, fn = true), Key(".", ".", 1f, "?"), Key("⏎", "ENTER", 1.4f, fn = true))

    private fun letters(lang: String, r1: String, r2: String, r3: String, alt: Map<Char, String> = emptyMap()): List<List<Key>> {
        val pad = (r1.length - r2.length) / 2f
        val row2 = if (pad > 0f) listOf(spacer(pad)) + chars(r2, alt) + spacer(pad) else chars(r2, alt)
        return listOf(NUM, chars(r1, alt), row2,
            listOf(Key("⇧", "SHIFT", 1.4f, fn = true)) + chars(r3, alt) + del(),
            bottom(Key("123", "SYM", 1.3f, fn = true), lang))
    }

    fun get(lang: String, sym: Boolean): List<List<Key>> {
        if (sym) return listOf(NUM, chars("@#$%&-+()/"),
            listOf(spacer(0.5f)) + chars("*\"':;!?_~") + del(),
            bottom(Key("ABC", "ABC", 1.3f, fn = true), lang))
        return when (lang) {
            "ru" -> letters(lang, "йцукенгшщзхъ", "фывапролджэ", "ячсмитьбю", mapOf('е' to "ё"))
            "ko" -> letters(lang, "ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔ", "ㅁㄴㅇㄹㅎㅗㅓㅏㅣ", "ㅋㅌㅊㅍㅠㅜㅡ")
            "uz" -> letters(lang, "qwertyuiop", "asdfghjkl", "zxcvbnmʻ", mapOf('o' to "oʻ", 'g' to "gʻ"))
            "uzc" -> letters(lang, "йцукенгшўзҳ", "фқвапролджэ", "ячсмитбюғь")
            else -> letters("en", "qwertyuiop", "asdfghjkl", "zxcvbnm")
        }
    }
}
