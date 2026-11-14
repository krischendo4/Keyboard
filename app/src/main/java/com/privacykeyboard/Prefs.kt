package com.privacykeyboard

import android.content.Context

/** Settings Engine: только настройки внешнего вида и список языков. Текст ввода сюда не попадает. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE)
    fun int(k: String, d: Int) = sp.getInt(k, d)
    fun set(k: String, v: Int) = sp.edit().putInt(k, v).apply()
    fun bool(k: String, d: Boolean) = sp.getBoolean(k, d)
    fun set(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()
    fun str(k: String, d: String) = sp.getString(k, d) ?: d
    fun set(k: String, v: String) = sp.edit().putString(k, v).apply()
    fun langs(): List<String> = str("langs", "en,ru,ko,uz").split(",").filter { it in Layouts.ids }.ifEmpty { listOf("en") }
}
