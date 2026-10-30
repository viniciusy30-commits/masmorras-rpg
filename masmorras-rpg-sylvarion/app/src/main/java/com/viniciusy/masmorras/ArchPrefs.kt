package com.viniciusy.masmorras

import android.content.Context

/** Ajustes da visão arquitetônica. Só visual; guardados no aparelho, não no mapa. */
object ArchPrefs {
    var mode = 0
    var minWall = 0.30f
    var speed = 5f
    var reach = 6

    val modeNames = arrayOf("Automático", "Normal (tudo opaco)", "Interior (sem telhado)", "Corte (some o lado sul)", "Isolamento (só esta construção)")

    fun load(c: Context) {
        val p = c.getSharedPreferences("arch", Context.MODE_PRIVATE)
        mode = p.getInt("mode", 0).coerceIn(0, 4)
        minWall = p.getFloat("minWall", 0.30f).coerceIn(0.05f, 0.9f)
        speed = p.getFloat("speed", 5f).coerceIn(1f, 12f)
        reach = p.getInt("reach", 6).coerceIn(2, 12)
    }

    fun save(c: Context) {
        c.getSharedPreferences("arch", Context.MODE_PRIVATE).edit()
            .putInt("mode", mode).putFloat("minWall", minWall)
            .putFloat("speed", speed).putInt("reach", reach).apply()
    }
}
