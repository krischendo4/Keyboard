package com.privacykeyboard

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*

/** Settings Engine UI + страница «Проверка безопасности». */
class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val p = Prefs(this)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad * 2, pad, pad) }
        setContentView(ScrollView(this).apply { addView(root) })
        fun head(t: String) = root.addView(TextView(this).apply { text = t; textSize = 18f; setPadding(0, pad, 0, pad / 2) })
        fun btn(t: String, f: () -> Unit) = root.addView(Button(this).apply { text = t; setOnClickListener { f() } })
        fun seek(t: String, key: String, def: Int, max: Int) {
            val l = TextView(this); l.text = t; root.addView(l)
            root.addView(SeekBar(this).apply {
                this.max = max; progress = p.int(key, def)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, v: Int, u: Boolean) { p.set(key, v) }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            })
        }

        head("Privacy Keyboard")
        btn("1. Включить клавиатуру") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        btn("2. Выбрать клавиатуру") { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }
        root.addView(EditText(this).apply { hint = "Поле для проверки ввода" })

        head("Языки (порядок фиксированный)")
        val boxes = Layouts.ids.map { id ->
            CheckBox(this).apply { text = Layouts.names[id]; isChecked = id in p.langs() }.also { root.addView(it) }
        }
        boxes.forEach { cb -> cb.setOnCheckedChangeListener { _, _ ->
            val sel = Layouts.ids.filterIndexed { i, _ -> boxes[i].isChecked }
            if (sel.isEmpty()) { cb.isChecked = true } else p.set("langs", sel.joinToString(","))
        } }

        head("Тема")
        val names = listOf("Liquid Glass Dark", "Liquid Glass Light", "Crystal", "Midnight Glass", "Custom Glass")
        root.addView(Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, names)
            setSelection(p.int("theme", 0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) { p.set("theme", pos) }
                override fun onNothingSelected(a: AdapterView<*>?) {}
            }
        })
        seek("Оттенок (Custom Glass)", "hue", 210, 360)
        seek("Прозрачность клавиш", "opacity", 60, 100)
        seek("Матовость панели (имитация blur)", "blur", 60, 100)
        seek("Яркость отражений", "reflect", 60, 100)
        seek("Радиус закругления (dp)", "radius", 12, 24)
        seek("Расстояние между клавишами (dp)", "gap", 5, 12)
        seek("Высота клавиатуры (%)", "height", 100, 130)
        seek("Интенсивность анимации", "anim", 70, 100)
        root.addView(Switch(this).apply {
            text = "Вибрация"; isChecked = p.bool("haptic", true)
            setOnCheckedChangeListener { _, v -> p.set("haptic", v) }
        })
        root.addView(Switch(this).apply {
            text = "Двойной пробел → точка"; isChecked = p.bool("dotspace", true)
            setOnCheckedChangeListener { _, v -> p.set("dotspace", v) }
        })

        head("Проверка безопасности")
        val perms = try {
            packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS).requestedPermissions?.toList() ?: emptyList()
        } catch (e: Exception) { emptyList() }
        val info = """
            Запрошенные разрешения: ${if (perms.isEmpty()) "нет" else perms.joinToString()}
            Сеть (INTERNET): ${if (perms.contains("android.permission.INTERNET")) "ЗАПРОШЕНО" else "не запрошено"}
            Хранится локально: только оформление и список языков (SharedPreferences). Набранный текст не сохраняется.
            Память: только текущий корейский слог, пока он набирается; сбрасывается при смене поля и языка.
            Privacy Mode: в этой версии нет предикции, персональных словарей, истории и буфера обмена, поэтому отключать нечего.
            Ограничение: ОС, другие приложения с особыми правами и физический доступ к устройству остаются вне контроля клавиатуры.
            Независимый аудит не проводился.
        """.trimIndent()
        root.addView(TextView(this).apply { text = info })
    }
}
