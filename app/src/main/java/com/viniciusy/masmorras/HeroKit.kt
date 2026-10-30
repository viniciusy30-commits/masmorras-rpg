package com.viniciusy.masmorras

import kotlin.math.sin

/** Os 4 modelos 3D implementam isso para deixar o HeroKit desenhar as peças de raça no mesmo espaço deles. */
interface HeroGfx {
    fun cube(cx: Float, cy: Float, cz: Float, hx: Float, hy: Float, hz: Float, color: Int, emissive: Boolean = false)
    fun push()
    fun pop()
    fun translate(x: Float, y: Float, z: Float)
    fun rotX(a: Float)
    fun rotY(a: Float)
    fun rotZ(a: Float)
    fun scale(x: Float, y: Float, z: Float)
}

/** Orelhas, chifres, presas, focinho e cauda de cada raça, por cima dos 4 modelos. */
object HeroKit {
    /** Pontos de encaixe na cabeça e no corpo de cada classe (já com o elmo/chapéu em conta). */
    private class Spec(
        val skin: Int,
        val earX: Float, val earY: Float, val earZ: Float,
        val catX: Float, val catY: Float, val catZ: Float, val catTilt: Float,
        val hornX: Float, val hornY: Float, val hornZ: Float, val hornOut: Float,
        val noseY: Float, val noseZ: Float,
        val mouthY: Float, val mouthZ: Float,
        val tailY: Float, val tailZ: Float
    )

    private fun spec(c: Classe): Spec = when (c) {
        Classe.MAGO -> Spec(0xFFF1C27D.toInt(), 4.5f, 4.6f, -0.3f, 4.9f, 8.4f, 0f, 0.9f, 4.5f, 8.6f, 0.4f, 0.95f, 3.8f, 4.45f, 2.5f, 4.85f, -1.0f, -4.6f)
        Classe.GUERREIRO -> Spec(0xFFF1C27D.toInt(), 6.2f, 5.2f, 0.3f, 3.4f, 12.3f, 0f, 0.2f, 3.8f, 11.8f, 0.4f, 0.5f, 3.3f, 5.0f, 1.7f, 5.4f, -1.0f, -4.4f)
        Classe.BARBARO -> Spec(0xFFD99A5E.toInt(), 5.3f, 4.0f, 0.2f, 3.4f, 12.6f, -0.3f, 0.2f, 4.0f, 11.4f, -0.2f, 0.55f, 3.4f, 4.9f, 2.3f, 5.45f, -1.0f, -4.6f)
        Classe.ARQUEIRO -> Spec(0xFFF6DCC0.toInt(), 4.5f, 4.4f, -0.7f, 3.0f, 10.8f, -0.2f, 0.15f, 3.6f, 10.6f, -0.2f, 0.45f, 4.3f, 4.35f, 2.3f, 4.55f, -1.0f, -4.4f)
        Classe.NINJA, Classe.PIKACHU, Classe.ICHIGO, Classe.LUFFY, Classe.EXPLORADOR -> Spec(0xFFF1C27D.toInt(), 4.6f, 4.8f, -0.4f, 3.4f, 10.2f, -0.2f, 0.3f, 3.8f, 9.6f, -0.2f, 0.7f, 3.8f, 4.5f, 2.4f, 4.8f, -1.0f, -4.6f)
    }

    private const val PINK = 0xFFE8A0A8.toInt()
    private const val NOSE = 0xFFD97A8A.toInt()
    private const val IVORY = 0xFFF3EBD2.toInt()
    private const val BONE = 0xFFE8DCC0.toInt()
    private const val HORN_DARK = 0xFF2B1B1B.toInt()
    private const val DARK = 0xFF1B1B1B.toInt()
    private const val SPADE = 0xFF9E1B1B.toInt()

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    // ───────────── cabeça ─────────────

    /** Chamado dentro da cabeça de cada modelo (no mesmo espaço do rosto). */
    fun cabeca(g: HeroGfx, c: Classe) {
        val r = HeroLook.raca
        val sp = spec(c)
        val sk = HeroLook.skin(sp.skin)
        val skD = HeroLook.skinD(sp.skin)
        val skL = HeroLook.skinL(sp.skin)

        when (r.orelha) {
            1 -> if (c != Classe.ARQUEIRO) for (k in 0..1) elfa(g, if (k == 0) 1f else -1f, sp, sk, skD, 1f)
            2 -> for (k in 0..1) felina(g, if (k == 0) 1f else -1f, sp, sk)
            3 -> for (k in 0..1) goblin(g, if (k == 0) 1f else -1f, sp, sk, skD)
            4 -> for (k in 0..1) elfa(g, if (k == 0) 1f else -1f, sp, sk, skD, 0.5f)
        }
        when (r.chifre) {
            1 -> for (k in 0..1) chifreDiabo(g, if (k == 0) 1f else -1f, sp)
            2 -> for (k in 0..1) chifreDragao(g, if (k == 0) 1f else -1f, sp)
        }
        if (r.presas) presas(g, sp, r == Raca.ORC)
        when (r.focinho) {
            1 -> focinhoGato(g, sp, skL)
            2 -> focinhoDragao(g, sp, sk, skD)
        }
        rosto(g, c)
        cabelo(g, c)
        barba(g, c)
        topo(g, c)
    }

    // ───────────── cabelo, barba e acessórios de cabeça ─────────────

    /** Caixa da cabeça de cada classe: topo, meia largura, meia profundidade, cor padrão do cabelo e altura do queixo. */
    private class Cranio(val top: Float, val hw: Float, val hd: Float, val base: Int, val queixo: Float)

    private fun cranio(c: Classe): Cranio = when (c) {
        Classe.GUERREIRO -> Cranio(10.2f, 5.0f, 4.8f, 0xFF5A3A22.toInt(), 0.9f)
        Classe.BARBARO -> Cranio(9.7f, 4.8f, 4.6f, 0xFFB8421C.toInt(), 0.9f)
        Classe.MAGO -> Cranio(9.5f, 4.4f, 4.2f, 0xFFEDEFF3.toInt(), 0.8f)
        Classe.ARQUEIRO -> Cranio(9.5f, 4.3f, 4.1f, 0xFFEAD79B.toInt(), 0.8f)
        Classe.NINJA, Classe.PIKACHU, Classe.ICHIGO, Classe.LUFFY, Classe.EXPLORADOR -> Cranio(9.5f, 4.5f, 4.3f, 0xFF1C1626.toInt(), 0.8f)
    }

    private fun cor3(i: Int, a: Int, b: Int, c: Int): Int {
        val m = ((i % 3) + 3) % 3
        return if (m == 0) a else if (m == 1) b else c
    }

    /**
     * Base de todo penteado: calota em degraus com fios em relevo, testa irregular,
     * laterais escalonadas (sem frestas) e nuca em colunas de mechas.
     */
    private fun base(g: HeroGfx, k: Cranio, col: Int, colL: Int, colD: Int) {
        val t = k.top
        val w = k.hw
        val d = k.hd
        // sombra por baixo: as frestas entre as mechas nunca mostram pele
        g.cube(0f, t - 1.0f, -0.1f, w + 0.2f, 1.2f, d + 0.2f, colD)
        // domo em degraus
        g.cube(0f, t + 0.45f, -0.1f, w + 0.35f, 0.85f, d + 0.35f, col)
        g.cube(0f, t + 1.2f, -0.15f, (w + 0.1f) * 0.88f, 0.3f, (d + 0.1f) * 0.9f, col)
        if (!HeroLook.elmo) {
            g.cube(0f, t + 1.55f, -0.2f, w * 0.6f, 0.1f, d * 0.66f, colL)
            // fios que saem de um redemoinho atrás do topo e se abrem para a testa e as laterais
            for (r in 0..10) {
                val a = -1.75f + r * 0.35f
                val sa = kotlin.math.abs(sin(a))
                val ca = kotlin.math.cos(a)
                val dx = if (sa < 0.25f) 0.25f else sa
                val dz = if (ca > 0.25f) ca else 0.25f
                val len = minOf(w / dx, (d + 1.2f) / dz) * 0.62f
                g.push()
                g.translate(0f, t + 1.45f + (r % 3) * 0.05f, -1.3f)
                g.rotY(a)
                g.cube(0f, 0f, len * 0.5f + 0.4f, 0.3f + (r % 2) * 0.08f, 0.1f, len * 0.5f, if (r % 2 == 0) colL else col)
                g.pop()
            }
            g.cube(0f, t + 1.6f, -1.3f, 0.5f, 0.08f, 0.5f, colD)
        }
        // testa: linha do cabelo irregular
        val sp = w * 2f / 7f
        for (i in -3..3) {
            val ln = 1.7f + ((i * 7 + 21) % 3) * 0.3f
            g.cube(i * sp, t + 0.1f - ln * 0.5f, d + 0.2f + (kotlin.math.abs(i) % 2) * 0.08f, sp * 0.5f + 0.06f, ln * 0.5f, 0.3f, cor3(i + 3, col, colL, col))
        }
        g.cube(0f, t - 1.95f, d + 0.25f, w * 0.78f, 0.12f, 0.25f, colD)
        // laterais: 4 colunas da frente para trás, mais curtas na frente (costeleta) e mais longas atrás
        val lens = floatArrayOf(2.6f, 3.6f, 4.4f, 4.7f)
        for (si in 0..1) {
            val sd = if (si == 0) 1f else -1f
            for (j in 0..3) {
                val z = d * 0.55f - j * d * 0.45f
                val ln = lens[j]
                g.cube(sd * (w + 0.25f), t + 0.1f - ln * 0.5f, z, 0.4f + (j % 2) * 0.08f, ln * 0.5f, d * 0.24f + 0.06f, cor3(j + si, colD, col, colD))
                g.cube(sd * (w + 0.62f), t - ln * 0.35f, z + 0.1f, 0.13f, ln * 0.34f, d * 0.17f, if (j % 2 == 0) colL else col)
            }
        }
        // nuca: colunas de comprimentos diferentes
        for (i in -3..3) {
            val ln = 4.3f - kotlin.math.abs(i) * 0.25f + (((i + 3) * 5) % 3) * 0.2f
            g.cube(i * sp, t + 0.1f - ln * 0.5f, -d - 0.25f - (kotlin.math.abs(i) % 2) * 0.1f, sp * 0.5f + 0.1f, ln * 0.5f, 0.5f, cor3(i + 4, colD, col, colD))
        }
        g.cube(0f, t - 1.4f, -d - 0.6f, w * 0.8f, 1.6f, 0.15f, col)
    }

    /** Cortina de mechas que cai atrás da cabeça (cabelos longos). */
    private fun cortinaTras(g: HeroGfx, k: Cranio, col: Int, colL: Int, colD: Int, n: Int, onda: Float, bal: Float) {
        g.push(); g.translate(0f, k.top - 5.0f, -k.hd - 0.3f); g.rotX(0.05f + bal)
        for (i in -3..3) mecha(g, i * k.hw * 0.27f, -0.4f, -0.1f * kotlin.math.abs(i), n, k.hw * 0.15f, 0.55f, 1.5f, if (i % 2 == 0) col else colL, colL, colD, onda)
        g.pop()
    }

    /** Mechas dos dois lados do rosto, caindo sobre os ombros. */
    private fun mechasLaterais(g: HeroGfx, k: Cranio, col: Int, colL: Int, colD: Int, n: Int, onda: Float, bal: Float, z: Float) {
        for (sd in floatArrayOf(-1f, 1f)) {
            g.push(); g.translate(sd * (k.hw + 0.4f), k.top - 3.4f, z); g.rotX(-0.04f - bal)
            mecha(g, 0f, 0f, 0f, n, 0.55f, 1.0f, 1.45f, col, colL, colD, onda)
            mecha(g, sd * 0.4f, -0.2f, 1.1f, n - 1, 0.42f, 0.5f, 1.45f, colL, colL, colD, onda)
            g.pop()
        }
    }

    /** Mecha pendurada que afina para a ponta (n camadas), com ondulação opcional. */
    private fun mecha(g: HeroGfx, x: Float, y: Float, z: Float, n: Int, w: Float, dp: Float, step: Float, col: Int, colL: Int, colD: Int, onda: Float) {
        for (j in 0 until n) {
            val f = 1f - j.toFloat() / (n + 1)
            val xx = x + (if (j % 2 == 0) onda else -onda)
            g.cube(xx, y - j * step, z, w * (0.55f + 0.45f * f), step * 0.62f, dp * (0.6f + 0.4f * f), if (j % 3 == 1) colD else if (j % 3 == 2) colL else col)
            if (j % 2 == 0 && j > 0) g.cube(xx - w * 0.2f, y - j * step, z + dp * 0.85f, w * 0.18f, step * 0.5f, dp * 0.2f, colL)
        }
    }

    /** Franja repartida: tufos que caem sobre a testa. */
    private fun franja(g: HeroGfx, k: Cranio, col: Int, colL: Int, colD: Int, lado: Float, comp: Float) {
        for (i in -2..2) {
            val x = i * k.hw * 0.4f + lado * 0.5f
            val h = comp - kotlin.math.abs(i + lado * 0.5f) * 0.25f
            g.cube(x, k.top - 1.6f - h * 0.5f, k.hd + 0.4f - kotlin.math.abs(i) * 0.1f, k.hw * 0.21f, h * 0.5f, 0.28f, if (i % 2 == 0) col else colL)
            g.cube(x, k.top - 1.6f - h, k.hd + 0.35f, k.hw * 0.15f, 0.25f, 0.2f, colD)
        }
    }

    fun cabelo(g: HeroGfx, c: Classe) {
        val st = HeroLook.cabelo
        val k = cranio(c)
        val col = HeroLook.hair(k.base)
        val colL = HeroLook.hairL(k.base)
        val colD = HeroLook.hairD(k.base)
        val elmo = HeroLook.elmo
        val t = System.nanoTime() / 1_000_000_000f
        val bal = sin(t * 1.7f) * 0.05f
        val v = if (elmo) 0.25f else 1f
        if (st == 0) {
            if (!elmo && (c == Classe.GUERREIRO || c == Classe.BARBARO || c == Classe.MAGO)) base(g, k, col, colL, colD)
            return
        }
        if (st == 1) return
        val bz = -k.hd - 0.3f
        val sds = floatArrayOf(-1f, 1f)
        when (st) {
            2 -> { // curto, com textura e mechas na testa
                base(g, k, col, colL, colD)
                for (i in -3..3) g.cube(i * k.hw * 0.27f, k.top + 1.3f + ((i * 5) and 1) * 0.4f * v, -0.3f + (i and 1) * 0.6f, k.hw * 0.16f, 0.45f * v + 0.1f, 1.2f, if (i % 2 == 0) colL else col)
                franja(g, k, col, colL, colD, 0.8f, 1.8f)
            }
            3 -> { // longo liso com repartição e mechas laterais compridas
                base(g, k, col, colL, colD)
                g.cube(0f, k.top + 1.4f, -0.3f, 0.12f, 0.1f, k.hd * 0.7f, colD)
                g.push(); g.translate(0f, k.top - 5.0f, bz); g.rotX(0.05f + bal)
                for (i in -3..3) mecha(g, i * k.hw * 0.27f, -0.4f, -0.1f * kotlin.math.abs(i), 6, k.hw * 0.15f, 0.55f, 1.5f, if (i % 2 == 0) col else colL, colL, colD, 0f)
                g.pop()
                for (sd in sds) {
                    g.push(); g.translate(sd * (k.hw + 0.4f), k.top - 3.4f, 1.2f); g.rotX(-0.04f - bal)
                    mecha(g, 0f, 0f, 0f, 6, 0.55f, 1.0f, 1.45f, col, colL, colD, 0f)
                    mecha(g, sd * 0.4f, -0.2f, 1.1f, 5, 0.42f, 0.5f, 1.45f, colL, colL, colD, 0f)
                    g.pop()
                }
                franja(g, k, col, colL, colD, -0.8f, 2.6f)
            }
            4 -> { // moicano alto, com leque de espinhos e laterais raspadas
                g.cube(0f, k.top + 0.4f, -0.2f, 0.9f, 0.7f, k.hd * 0.95f, col)
                for (sd in sds) g.cube(sd * (k.hw + 0.1f), k.top - 1.2f, -0.4f, 0.12f, 1.7f, k.hd * 0.55f, colD)
                for (j in 0..6) {
                    val h = (if (elmo) 0.3f else 2.8f - kotlin.math.abs(j - 3) * 0.55f)
                    if (h > 0.2f) g.cube(0f, k.top + 1.1f + h * 0.5f, 3.6f - j * 1.15f, 0.8f, h, 0.5f, if (j % 2 == 0) col else colL)
                    if (h > 0.6f) g.cube(0f, k.top + 1.1f + h * 1.1f, 3.6f - j * 1.15f, 0.45f, h * 0.35f, 0.35f, colL)
                }
                g.cube(0f, k.top - 1.0f, k.hd + 0.2f, 0.85f, 0.9f, 0.3f, col)
                g.cube(0f, k.top - 3.2f, -k.hd - 0.25f, 0.85f, 3.0f, 0.4f, colD)
            }
            5 -> { // rabo de cavalo alto que chicoteia, preso por argola
                base(g, k, col, colL, colD)
                g.cube(0f, k.top - 0.2f, -k.hd - 0.55f, 1.2f, 0.9f, 0.7f, colD)
                g.cube(0f, k.top - 0.2f, -k.hd - 0.95f, 1.3f, 0.35f, 0.4f, 0xFFD9A93A.toInt())
                g.push(); g.translate(0f, k.top - 0.2f, -k.hd - 1.1f); g.rotX(-1.0f + bal * 2f)
                for (i in -1..1) {
                    g.push(); g.translate(i * 0.9f, 0f, 0f); g.rotX(i * 0.1f + bal)
                    mecha(g, 0f, 3.2f, 0f, 6, 0.55f, 0.6f, 1.25f, if (i == 0) col else colL, colL, colD, 0.12f)
                    g.pop()
                }
                g.pop()
                franja(g, k, col, colL, colD, 0.6f, 1.6f)
            }
            6 -> { // coque alto trançado com fita
                base(g, k, col, colL, colD)
                g.cube(0f, k.top + 2.0f * v, -1.0f, 2.1f, 1.4f * v + 0.2f, 2.1f, col)
                g.cube(0f, k.top + 3.0f * v + 0.2f, -1.0f, 1.6f, 0.55f, 1.6f, colL)
                for (i in 0..3) g.cube(kotlin.math.cos(i * 1.57f) * 2.0f, k.top + 2.0f * v, -1.0f + kotlin.math.sin(i * 1.57f) * 2.0f, 0.4f, 1.0f * v + 0.2f, 0.4f, colD)
                g.cube(0f, k.top + 1.45f, -1.0f, 2.3f, 0.2f, 2.3f, 0xFFB5242E.toInt())
                franja(g, k, col, colL, colD, -0.6f, 1.5f)
            }
            7 -> { // trança grossa e trançada de verdade
                base(g, k, col, colL, colD)
                g.cube(0f, k.top - 5.0f, -k.hd - 0.55f, 1.2f, 0.5f, 0.55f, colD)
                g.push(); g.translate(0f, k.top - 5.3f, -k.hd - 0.9f); g.rotX(0.05f + bal * 2f)
                for (j in 0..7) {
                    val wv = if (j % 2 == 0) 0.45f else -0.45f
                    g.cube(wv, -0.8f - j * 1.3f, 0f, 0.8f - j * 0.04f, 0.72f, 0.75f, if (j % 2 == 0) col else colD)
                    g.cube(-wv, -1.4f - j * 1.3f, 0.05f, 0.72f - j * 0.04f, 0.72f, 0.7f, if (j % 2 == 0) colD else colL)
                }
                g.cube(0f, -11.4f, 0f, 0.8f, 0.4f, 0.75f, 0xFFD9A93A.toInt())
                g.cube(0f, -12.4f, 0f, 0.5f, 0.7f, 0.45f, col)
                g.pop()
                franja(g, k, col, colL, colD, 0.7f, 2.0f)
            }
            8 -> { // espetado, com pontas desencontradas
                base(g, k, col, colL, colD)
                for (i in -3..3) for (r in -1..1) {
                    val h = if (elmo) 0.4f else 1.4f + (3 - kotlin.math.abs(i)) * 0.5f + (r + 1) * 0.2f
                    g.cube(i * (k.hw * 0.3f), k.top + 1.3f + h * 0.5f, r * 2.4f, 0.5f, h * 0.5f, 0.5f, if ((i + r) % 2 == 0) col else colL)
                    g.cube(i * (k.hw * 0.3f) + (i and 1) * 0.15f, k.top + 1.3f + h, r * 2.4f, 0.25f, 0.4f, 0.25f, colL)
                }
            }
            9 -> { // franja longa cobrindo um olho
                base(g, k, col, colL, colD)
                franja(g, k, col, colL, colD, 1.4f, 3.4f)
                g.cube(-k.hw * 0.55f, k.top - 4.6f, k.hd + 0.45f, k.hw * 0.4f, 1.1f, 0.25f, colL)
                for (sd in sds) g.cube(sd * (k.hw + 0.4f), k.top - 4.6f, 0.9f, 0.4f, 2.6f, 1.2f, col)
            }
            10 -> { // ondulado: ondas macias até o meio das costas
                base(g, k, col, colL, colD)
                g.push(); g.translate(0f, k.top - 5.0f, bz); g.rotX(0.05f + bal)
                for (i in -3..3) mecha(g, i * k.hw * 0.27f, -0.4f, -0.1f * kotlin.math.abs(i), 7, k.hw * 0.15f, 0.6f, 1.4f, if (i % 2 == 0) col else colL, colL, colD, 0.45f)
                g.pop()
                for (sd in sds) {
                    g.push(); g.translate(sd * (k.hw + 0.45f), k.top - 3.2f, 1.0f); g.rotX(-0.04f - bal)
                    mecha(g, 0f, 0f, 0f, 6, 0.6f, 1.1f, 1.4f, col, colL, colD, 0.4f)
                    mecha(g, sd * 0.5f, -0.3f, 1.2f, 5, 0.45f, 0.5f, 1.4f, colL, colL, colD, 0.35f)
                    g.pop()
                }
                franja(g, k, col, colL, colD, -0.6f, 2.2f)
            }
            11 -> { // cacheado: cachos em anéis ao redor da cabeça e nos ombros
                base(g, k, col, colL, colD)
                for (r in 0..3) for (a in 0..9) {
                    val ang = a * 0.628f + r * 0.3f
                    val rad = k.hw + 0.7f + r * 0.1f
                    val px = kotlin.math.cos(ang) * rad
                    val pz = kotlin.math.sin(ang) * (k.hd + 0.7f)
                    if (pz > k.hd * 0.4f && r < 2) continue
                    g.cube(px, k.top - 0.8f - r * 1.7f, pz - 0.2f, 0.85f, 0.85f, 0.85f, if ((a + r) % 2 == 0) col else colL)
                }
                for (i in -2..2) for (r in 0..1) g.cube(i * 1.8f, k.top - 6.0f - r * 1.5f, -k.hd - 0.8f, 0.85f, 0.8f, 0.8f, if ((i + r) % 2 == 0) colD else col)
                for (r in 0..8) g.cube(((r * 7) % 5 - 2) * 1.4f, k.top + 1.1f * v + 0.3f, ((r * 3) % 5 - 2) * 1.5f, 0.9f, 0.55f * v + 0.2f, 0.9f, colL)
            }
            12 -> { // chanel: corte reto na altura do queixo com franja reta
                base(g, k, col, colL, colD)
                g.cube(0f, k.top - 3.4f, -k.hd - 0.5f, k.hw + 0.6f, 3.3f, 0.6f, col)
                g.cube(0f, k.top - 3.4f, -k.hd - 1.0f, k.hw * 0.8f, 3.0f, 0.12f, colL)
                for (sd in sds) {
                    g.cube(sd * (k.hw + 0.6f), k.top - 4.0f, -0.3f, 0.65f, 3.2f, k.hd * 0.8f, col)
                    g.cube(sd * (k.hw + 0.95f), k.top - 3.6f, -0.3f, 0.15f, 2.6f, k.hd * 0.6f, colL)
                    g.cube(sd * (k.hw - 0.1f), k.top - 6.6f, 0.2f, 0.7f, 0.4f, k.hd * 0.7f, colD)
                }
                for (i in -3..3) g.cube(i * k.hw * 0.28f, k.top - 1.9f, k.hd + 0.4f, k.hw * 0.15f, 0.75f, 0.25f, if (i % 2 == 0) col else colL)
                g.cube(0f, k.top - 2.8f, k.hd + 0.35f, k.hw * 0.9f, 0.14f, 0.18f, colD)
            }
            13 -> { // duas marias-chiquinhas altas com laços
                base(g, k, col, colL, colD)
                for (sd in sds) {
                    g.cube(sd * (k.hw - 0.4f), k.top - 0.2f, -0.5f, 0.9f, 0.9f, 0.9f, colD)
                    g.cube(sd * (k.hw - 0.4f), k.top - 0.2f, -0.5f, 1.05f, 0.3f, 1.05f, 0xFFE04A7A.toInt())
                    g.push(); g.translate(sd * (k.hw + 0.6f), k.top - 0.8f, -0.5f); g.rotZ(sd * -0.25f); g.rotX(bal * 2f)
                    mecha(g, 0f, 0f, 0f, 7, 1.0f, 1.0f, 1.3f, col, colL, colD, 0.1f)
                    g.pop()
                }
                franja(g, k, col, colL, colD, 0f, 1.8f)
            }
            14 -> { // pompadour: topete alto penteado para trás
                base(g, k, col, colL, colD)
                for (j in 0..4) g.cube(0f, k.top + 1.2f + j * 0.45f * v + 0.2f, 3.2f - j * 1.3f, k.hw * 0.8f - j * 0.12f, 0.7f * v + 0.3f, 1.0f, if (j % 2 == 0) col else colL)
                g.cube(0f, k.top + 1.1f + 2.2f * v + 0.6f, 3.9f, k.hw * 0.7f, 0.6f * v + 0.2f, 0.8f, colL)
                for (sd in sds) g.cube(sd * (k.hw + 0.3f), k.top - 2.4f, -0.4f, 0.35f, 1.8f, k.hd * 0.55f, colD)
            }
            15 -> { // rabo de samurai: laterais lisas, nó no alto com fita
                base(g, k, col, colL, colD)
                g.cube(0f, k.top + 1.0f, -k.hd * 0.4f, 1.0f, 0.7f, 1.1f, colD)
                g.push(); g.translate(0f, k.top + 1.4f, -k.hd * 0.8f); g.rotX(-0.5f + bal * 2f)
                mecha(g, 0f, 0f, 0f, 3, 0.9f, 0.9f, 1.0f, col, colL, colD, 0f)
                g.pop()
                g.cube(0f, k.top + 1.25f, -k.hd * 0.55f, 1.1f, 0.25f, 1.2f, 0xFFB5242E.toInt())
                g.push(); g.translate(0f, k.top + 1.6f, -k.hd * 0.9f); g.rotX(-1.1f + bal * 3f)
                mecha(g, 0f, 2.4f, 0f, 5, 0.9f, 0.8f, 1.2f, col, colL, colD, 0.1f)
                g.pop()
            }
            16 -> { // raspado dos lados, topo longo penteado de lado
                for (sd in sds) g.cube(sd * (k.hw + 0.15f), k.top - 1.6f, -0.4f, 0.14f, 2.0f, k.hd * 0.6f, colD)
                g.cube(0f, k.top + 0.5f, -0.2f, k.hw * 0.75f, 0.8f, k.hd * 0.9f, col)
                for (j in 0..3) g.cube(-1.0f + j * 0.7f, k.top + 1.0f + j * 0.35f * v + 0.2f, 1.8f - j * 1.2f, k.hw * 0.62f, 0.55f * v + 0.2f, 1.2f, if (j % 2 == 0) colL else col)
                for (i in -2..2) g.cube(i * 1.3f + 0.8f, k.top - 1.8f, k.hd + 0.4f, 0.65f, 0.9f - kotlin.math.abs(i) * 0.1f, 0.26f, col)
                g.cube(0f, k.top - 2.9f, -k.hd - 0.25f, k.hw * 0.6f, 2.8f, 0.35f, colD)
            }
            17 -> { // black power: afro redondo e cheio
                base(g, k, col, colL, colD)
                val r = if (elmo) 0.4f else 1f
                g.cube(0f, k.top + 0.8f, -0.3f, (k.hw + 2.0f) * (0.6f + 0.4f * r), 1.2f + 1.0f * r, (k.hd + 2.0f) * (0.6f + 0.4f * r), col)
                g.cube(0f, k.top + 1.2f, -0.3f, (k.hw + 1.1f) * (0.6f + 0.4f * r), 2.0f + 1.0f * r, (k.hd + 1.1f) * (0.6f + 0.4f * r), colL)
                for (sd in sds) g.cube(sd * (k.hw + 1.6f * r), k.top - 1.4f, -0.4f, 0.7f * r + 0.2f, 2.2f, k.hd + 0.8f * r, colD)
                g.cube(0f, k.top - 1.0f, -k.hd - 1.2f * r, k.hw + 1.4f * r, 2.4f, 0.8f * r + 0.2f, colD)
                for (i in -2..2) g.cube(i * 1.7f, k.top + 3.0f * r + 0.6f, 0f, 0.55f, 0.5f, 0.55f, colL)
            }
            18 -> { // dreadlocks: mechas grossas com anéis
                base(g, k, col, colL, colD)
                for (i in 0..8) {
                    val ang = i * 0.7f + 0.2f
                    val px = kotlin.math.cos(ang) * (k.hw + 0.3f)
                    val pz = kotlin.math.sin(ang) * (k.hd + 0.3f)
                    if (pz > k.hd * 0.5f) continue
                    g.push(); g.translate(px, k.top - 1.4f, pz); g.rotX(bal * (1 + i % 3))
                    mecha(g, 0f, 0f, 0f, 5 + (i % 3) * 2, 0.55f, 0.55f, 1.25f, if (i % 2 == 0) col else colD, colL, colD, 0.05f)
                    g.cube(0f, -1.0f - (i % 3) * 0.6f, 0f, 0.65f, 0.15f, 0.65f, 0xFFD9A93A.toInt())
                    g.pop()
                }
                for (i in -3..3) g.cube(i * 1.3f, k.top + 1.1f * v + 0.3f, 0.2f, 0.65f, 0.5f * v + 0.2f, 1.6f, if (i % 2 == 0) colL else col)
            }
            19 -> { // trança lateral caindo sobre o ombro
                base(g, k, col, colL, colD)
                g.cube(0f, k.top - 4.0f, -k.hd - 0.5f, k.hw * 0.8f, 2.8f, 0.5f, col)
                g.cube(-k.hw - 0.45f, k.top - 3.6f, 0.4f, 0.6f, 2.0f, 1.6f, colD)
                g.push(); g.translate(k.hw + 0.4f, k.top - 2.8f, 1.6f); g.rotZ(-0.1f); g.rotX(bal * 2f)
                for (j in 0..8) {
                    val wv = if (j % 2 == 0) 0.4f else -0.4f
                    g.cube(wv, -0.7f - j * 1.25f, 0f, 0.75f - j * 0.03f, 0.7f, 0.7f, if (j % 2 == 0) col else colD)
                    g.cube(-wv, -1.3f - j * 1.25f, 0.05f, 0.7f - j * 0.03f, 0.7f, 0.65f, if (j % 2 == 0) colD else colL)
                }
                g.cube(0f, -11.6f, 0f, 0.8f, 0.4f, 0.75f, 0xFFD9A93A.toInt())
                g.pop()
                franja(g, k, col, colL, colD, -0.9f, 2.2f)
            }
            20 -> { // dois coques laterais no alto
                base(g, k, col, colL, colD)
                for (sd in sds) {
                    g.cube(sd * (k.hw * 0.62f), k.top + 1.9f * v + 0.2f, -0.6f, 1.5f, 1.2f * v + 0.3f, 1.5f, col)
                    g.cube(sd * (k.hw * 0.62f), k.top + 2.8f * v + 0.5f, -0.6f, 1.05f, 0.5f, 1.05f, colL)
                    g.cube(sd * (k.hw * 0.62f), k.top + 1.3f, -0.6f, 1.65f, 0.18f, 1.65f, 0xFFE04A7A.toInt())
                    g.push(); g.translate(sd * (k.hw * 0.62f + sd * 1.2f), k.top + 1.0f, -0.6f); g.rotZ(sd * -0.5f); g.rotX(bal)
                    mecha(g, 0f, 0f, 0f, 4, 0.4f, 0.4f, 1.1f, col, colL, colD, 0f)
                    g.pop()
                }
                franja(g, k, col, colL, colD, 0f, 1.9f)
            }
            21 -> { // hime: franja reta e mechas laterais compridas
                base(g, k, col, colL, colD)
                for (i in -3..3) g.cube(i * k.hw * 0.28f, k.top - 2.0f, k.hd + 0.4f, k.hw * 0.15f, 0.95f, 0.26f, if (i % 2 == 0) col else colL)
                g.push(); g.translate(0f, k.top - 4.0f, bz); g.rotX(0.05f + bal)
                for (i in -3..3) mecha(g, i * k.hw * 0.27f, -0.2f, 0f, 8, k.hw * 0.15f, 0.55f, 1.4f, col, colL, colD, 0f)
                g.pop()
                for (sd in sds) {
                    g.push(); g.translate(sd * (k.hw + 0.35f), k.top - 2.6f, k.hd * 0.55f); g.rotX(-0.03f - bal)
                    mecha(g, 0f, 0f, 0f, 8, 0.5f, 0.55f, 1.45f, col, colL, colD, 0f)
                    g.pop()
                }
            }
            22 -> { // meia-cauda: parte presa no alto, resto solto
                base(g, k, col, colL, colD)
                g.cube(0f, k.top + 0.5f, -k.hd * 0.9f, 1.3f, 1.0f, 0.9f, colD)
                g.cube(0f, k.top + 0.5f, -k.hd * 1.0f - 0.7f, 1.35f, 0.3f, 0.4f, 0xFFD9A93A.toInt())
                g.push(); g.translate(0f, k.top + 0.3f, -k.hd * 1.0f - 1.0f); g.rotX(-0.7f + bal * 2f)
                mecha(g, 0f, 1.5f, 0f, 6, 1.1f, 0.8f, 1.2f, col, colL, colD, 0.1f)
                g.pop()
                g.push(); g.translate(0f, k.top - 5.0f, bz); g.rotX(0.05f + bal)
                for (i in -2..2) mecha(g, i * k.hw * 0.32f, -0.4f, 0f, 6, k.hw * 0.17f, 0.55f, 1.45f, if (i % 2 == 0) col else colL, colL, colD, 0.15f)
                g.pop()
                for (sd in sds) {
                    g.push(); g.translate(sd * (k.hw + 0.4f), k.top - 3.3f, 1.0f); g.rotX(-0.04f - bal)
                    mecha(g, 0f, 0f, 0f, 5, 0.55f, 1.0f, 1.4f, col, colL, colD, 0f)
                    g.pop()
                }
                franja(g, k, col, colL, colD, 0.5f, 2.0f)
            }
            23 -> { // cabelo ao vento: mechas longas varridas para trás
                base(g, k, col, colL, colD)
                for (i in -3..3) {
                    g.push(); g.translate(i * k.hw * 0.27f, k.top - 3.2f, bz); g.rotX(-0.9f + bal * 3f + i * 0.04f); g.rotY(i * 0.12f)
                    mecha(g, 0f, 0f, 0f, 8, k.hw * 0.15f, 0.5f, 1.5f, if (i % 2 == 0) col else colL, colL, colD, 0.3f)
                    g.pop()
                }
                for (sd in sds) {
                    g.push(); g.translate(sd * (k.hw + 0.4f), k.top - 2.2f, 0.4f); g.rotX(-1.0f + bal * 3f); g.rotZ(sd * -0.15f)
                    mecha(g, 0f, 0f, 0f, 6, 0.5f, 0.55f, 1.4f, colL, colL, colD, 0.2f)
                    g.pop()
                }
                franja(g, k, col, colL, colD, 1.0f, 2.4f)
            }
            24 -> { // longo com franja cortina: abre ao meio e emoldura o rosto
                base(g, k, col, colL, colD)
                g.cube(0f, k.top + 1.65f, -0.3f, 0.1f, 0.08f, k.hd * 0.75f, colD)
                cortinaTras(g, k, col, colL, colD, 8, 0.12f, bal)
                mechasLaterais(g, k, col, colL, colD, 6, 0.1f, bal, 1.4f)
                for (sd in sds) for (i in 0..3) {
                    val h = 1.9f + i * 0.85f
                    g.cube(sd * (0.7f + i * k.hw * 0.24f), k.top - 1.5f - h * 0.5f, k.hd + 0.45f - i * 0.1f, k.hw * 0.13f, h * 0.5f, 0.27f, if (i % 2 == 0) col else colL)
                }
            }
            25 -> { // cachos longos: espirais cobrindo costas e ombros
                base(g, k, col, colL, colD)
                for (i in -3..3) for (j in 0..5) {
                    val xx = i * k.hw * 0.29f + (if (j % 2 == 0) 0.5f else -0.5f)
                    g.cube(xx, k.top - 4.6f - j * 1.25f, bz - (j % 2) * 0.25f, 0.95f, 0.7f, 0.85f, cor3(i + j, col, colL, colD))
                }
                for (sd in sds) for (j in 0..4) {
                    g.cube(sd * (k.hw + 0.9f + (j % 2) * 0.35f), k.top - 3.6f - j * 1.3f, 1.6f - (j % 2) * 0.5f, 0.85f, 0.65f, 0.8f, cor3(j, col, colL, colD))
                }
                for (r in 0..6) g.cube(((r * 7) % 5 - 2) * 1.4f, k.top + 1.5f * v + 0.2f, ((r * 3) % 5 - 2) * 1.5f, 0.9f, 0.5f * v + 0.2f, 0.9f, colL)
                franja(g, k, col, colL, colD, 0.5f, 2.0f)
            }
            26 -> { // chanel assimétrico: um lado longo até o queixo, o outro curto
                base(g, k, col, colL, colD)
                g.cube(0f, k.top - 3.2f, -k.hd - 0.5f, k.hw + 0.6f, 3.1f, 0.6f, col)
                g.cube(0f, k.top - 3.2f, -k.hd - 1.0f, k.hw * 0.8f, 2.7f, 0.12f, colL)
                g.cube(-(k.hw + 0.6f), k.top - 4.6f, 0f, 0.65f, 4.3f, k.hd * 0.85f, col)
                g.cube(-(k.hw + 0.95f), k.top - 4.4f, 0f, 0.15f, 3.4f, k.hd * 0.65f, colL)
                g.cube(-(k.hw - 0.1f), k.top - 9.2f, 0.4f, 0.7f, 0.4f, k.hd * 0.7f, colD)
                g.cube(k.hw + 0.5f, k.top - 3.0f, -0.3f, 0.55f, 2.5f, k.hd * 0.75f, col)
                g.cube(k.hw + 0.8f, k.top - 2.8f, -0.3f, 0.12f, 1.9f, k.hd * 0.55f, colL)
                franja(g, k, col, colL, colD, -1.2f, 2.7f)
            }
            27 -> { // pixie repicado com franja lateral
                base(g, k, col, colL, colD)
                for (i in -3..3) g.cube(i * k.hw * 0.27f, k.top + 1.6f + ((i * 5 + 15) % 3) * 0.3f * v, -0.3f + (i and 1) * 0.8f, k.hw * 0.15f, 0.4f * v + 0.1f, 1.3f, if (i % 2 == 0) colL else col)
                for (i in -2..2) g.cube(i * 1.4f + 1.2f, k.top - 1.7f, k.hd + 0.45f, 0.7f, 1.0f - kotlin.math.abs(i) * 0.1f, 0.27f, if (i % 2 == 0) col else colL)
                for (sd in sds) g.cube(sd * (k.hw + 0.5f), k.top - 4.9f, 1.8f, 0.35f, 0.5f, 0.3f, colD)
            }
            28 -> { // duas tranças longas sobre os ombros, com laços
                base(g, k, col, colL, colD)
                g.cube(0f, k.top + 1.65f, -0.3f, 0.1f, 0.08f, k.hd * 0.75f, colD)
                for (sd in sds) {
                    g.push(); g.translate(sd * (k.hw + 0.5f), k.top - 3.0f, 1.4f); g.rotZ(sd * -0.08f); g.rotX(bal * 2f)
                    for (j in 0..8) {
                        val wv = if (j % 2 == 0) 0.4f else -0.4f
                        g.cube(wv, -0.7f - j * 1.25f, 0f, 0.75f - j * 0.03f, 0.7f, 0.7f, if (j % 2 == 0) col else colD)
                        g.cube(-wv, -1.3f - j * 1.25f, 0.05f, 0.7f - j * 0.03f, 0.7f, 0.65f, if (j % 2 == 0) colD else colL)
                    }
                    g.cube(0f, -1.2f, 0f, 0.9f, 0.3f, 0.85f, 0xFFE04A7A.toInt())
                    g.cube(0f, -11.6f, 0f, 0.8f, 0.4f, 0.75f, 0xFFE04A7A.toInt())
                    g.cube(0f, -12.5f, 0f, 0.5f, 0.7f, 0.45f, col)
                    g.pop()
                }
                franja(g, k, col, colL, colD, 0f, 1.9f)
            }
            29 -> { // coque de bailarina: liso, no alto, com rede e fita
                base(g, k, col, colL, colD)
                g.cube(0f, k.top + 2.2f * v, -0.4f, 1.9f, 1.3f * v + 0.2f, 1.9f, col)
                g.cube(0f, k.top + 3.2f * v + 0.2f, -0.4f, 1.4f, 0.5f, 1.4f, colL)
                for (i in 0..5) g.cube(kotlin.math.cos(i * 1.047f) * 1.9f, k.top + 2.2f * v, -0.4f + kotlin.math.sin(i * 1.047f) * 1.9f, 0.3f, 1.0f * v + 0.2f, 0.3f, colD)
                g.cube(0f, k.top + 1.5f, -0.4f, 2.1f, 0.2f, 2.1f, 0xFFF4A7C0.toInt())
                for (sd in sds) g.cube(sd * (k.hw + 0.3f), k.top - 4.3f, 1.1f, 0.25f, 1.3f, 0.25f, colD)
            }
            30 -> { // princesa: meio presa com laço e cascata de ondas
                base(g, k, col, colL, colD)
                cortinaTras(g, k, col, colL, colD, 9, 0.3f, bal)
                mechasLaterais(g, k, col, colL, colD, 7, 0.25f, bal, 1.2f)
                g.cube(0f, k.top + 1.75f, -k.hd * 0.5f, 0.6f, 0.6f, 0.6f, 0xFFB8326A.toInt())
                for (sd in sds) {
                    g.push(); g.translate(sd * 1.8f, k.top + 1.8f, -k.hd * 0.5f); g.rotZ(sd * 0.35f)
                    g.cube(0f, 0f, 0f, 1.5f, 0.75f, 0.45f, 0xFFE04A7A.toInt())
                    g.pop()
                }
                franja(g, k, col, colL, colD, 0f, 1.7f)
            }
            31 -> { // longo em camadas, com volume nas pontas
                base(g, k, col, colL, colD)
                cortinaTras(g, k, col, colL, colD, 8, 0.2f, bal)
                g.push(); g.translate(0f, k.top - 5.0f, -k.hd - 0.3f); g.rotX(0.05f + bal)
                for (i in -3..3) g.cube(i * k.hw * 0.3f, -11.3f, -0.5f, k.hw * 0.16f, 0.9f, 0.7f, if (i % 2 == 0) colL else col)
                g.pop()
                mechasLaterais(g, k, col, colL, colD, 6, 0.2f, bal, 1.3f)
                franja(g, k, col, colL, colD, -0.8f, 2.3f)
            }
            32 -> { // rabo lateral caindo sobre o ombro
                base(g, k, col, colL, colD)
                g.cube(k.hw * 0.7f, k.top - 1.6f, -k.hd * 0.55f, 1.0f, 0.9f, 0.9f, colD)
                g.cube(k.hw * 0.7f, k.top - 1.6f, -k.hd * 0.55f, 1.2f, 0.3f, 1.1f, 0xFFE04A7A.toInt())
                g.push(); g.translate(k.hw * 0.8f + 0.6f, k.top - 2.2f, -k.hd * 0.3f); g.rotZ(-0.2f); g.rotX(bal * 2f)
                for (i in -1..1) {
                    g.push(); g.translate(i * 0.8f, 0f, 0f)
                    mecha(g, 0f, 0f, 0f, 8, 0.6f, 0.65f, 1.3f, if (i == 0) col else colL, colL, colD, 0.15f)
                    g.pop()
                }
                g.pop()
                franja(g, k, col, colL, colD, -0.9f, 2.2f)
            }
            33 -> { // social: risca marcada e topete penteado de lado
                base(g, k, col, colL, colD)
                g.cube(-1.4f, k.top + 1.65f, 0f, 0.1f, 0.08f, k.hd * 0.8f, colD)
                g.cube(1.8f, k.top + 1.5f + 0.3f * v, 0.6f, k.hw * 0.45f, 0.4f * v + 0.15f, k.hd * 0.85f, col)
                g.cube(2.3f, k.top + 1.85f + 0.3f * v, 0.6f, k.hw * 0.3f, 0.15f, k.hd * 0.7f, colL)
                for (i in 0..4) g.cube(-0.9f + i * 1.25f, k.top - 1.7f, k.hd + 0.45f, 0.65f, 0.9f - i * 0.05f, 0.27f, if (i % 2 == 0) col else colL)
            }
            34 -> { // médio bagunçado: ondas até a nuca, mechas desencontradas
                base(g, k, col, colL, colD)
                for (r in 0..8) g.cube(((r * 7) % 5 - 2) * 1.5f, k.top + 1.5f * v + 0.2f + (r % 3) * 0.2f, ((r * 3) % 5 - 2) * 1.6f, 0.95f, 0.5f * v + 0.2f, 1.0f, if (r % 2 == 0) colL else col)
                for (sd in sds) {
                    g.push(); g.translate(sd * (k.hw + 0.4f), k.top - 3.6f, 0.4f); g.rotX(-0.03f - bal)
                    mecha(g, 0f, 0f, 0f, 3, 0.6f, 1.2f, 1.2f, col, colL, colD, 0.3f)
                    g.pop()
                }
                g.push(); g.translate(0f, k.top - 5.0f, -k.hd - 0.3f); g.rotX(0.05f + bal)
                for (i in -3..3) mecha(g, i * k.hw * 0.27f, -0.4f, -0.1f * kotlin.math.abs(i), 2 + (i + 3) % 2, k.hw * 0.15f, 0.55f, 1.4f, if (i % 2 == 0) col else colL, colL, colD, 0.4f)
                g.pop()
                franja(g, k, col, colL, colD, 0.7f, 2.5f)
            }
            35 -> { // juba selvagem: mechas grossas até as costas
                base(g, k, col, colL, colD)
                cortinaTras(g, k, col, colL, colD, 9, 0.35f, bal)
                mechasLaterais(g, k, col, colL, colD, 7, 0.3f, bal, 1.0f)
                for (r in 0..8) g.cube(((r * 7) % 5 - 2) * 1.5f, k.top + 1.4f * v + 0.3f, ((r * 3) % 5 - 2) * 1.6f, 0.9f, 0.55f * v + 0.2f, 0.95f, if (r % 2 == 0) colL else col)
                franja(g, k, col, colL, colD, 0.4f, 2.6f)
            }
            36 -> { // tonsura: coroa de cabelo ao redor, topo à mostra
                for (sd in sds) g.cube(sd * (k.hw + 0.25f), k.top - 2.5f, -0.3f, 0.45f, 1.9f, k.hd * 0.9f, col)
                g.cube(0f, k.top - 2.6f, -k.hd - 0.25f, k.hw + 0.2f, 1.9f, 0.5f, colD)
                g.cube(0f, k.top - 1.9f, -k.hd - 0.5f, k.hw * 0.8f, 1.1f, 0.15f, col)
            }
        }
    }

    fun barba(g: HeroGfx, c: Classe) {
        val st = HeroLook.barba
        if (st < 2 || HeroLook.fem) return
        val k = cranio(c)
        val f = face(c)
        val sp = spec(c)
        val col = HeroLook.hair(k.base)
        val colD = HeroLook.hairD(k.base)
        val z = f.fz - 0.5f
        when (st) {
            2 -> { // barba curta
                g.cube(0f, k.queixo + 1.0f, z, k.hw * 0.78f, 1.0f, 0.5f, col)
                g.cube(0f, k.queixo + 2.2f, z, 1.7f, 0.45f, 0.5f, colD)
                for (i in 0..1) {
                    val sd = if (i == 0) 1f else -1f
                    g.cube(sd * (k.hw + 0.05f), k.queixo + 2.2f, k.hd * 0.4f, 0.25f, 1.8f, k.hd * 0.55f, col)
                }
            }
            3 -> { // cavanhaque
                g.cube(0f, k.queixo + 0.7f, z, 1.0f, 1.2f, 0.5f, col)
                g.cube(0f, k.queixo - 0.5f, z - 0.1f, 0.6f, 0.7f, 0.4f, colD)
                g.cube(0f, sp.mouthY + 0.9f, z, 1.2f, 0.3f, 0.4f, colD)
            }
            4 -> { // bigode
                for (i in 0..1) {
                    val sd = if (i == 0) 1f else -1f
                    g.cube(sd * 1.1f, sp.mouthY + 1.0f, z, 1.2f, 0.4f, 0.45f, col)
                    g.cube(sd * 2.5f, sp.mouthY + 0.5f, z - 0.1f, 0.35f, 0.9f, 0.35f, colD)
                }
            }
        }
    }

    /** Bandana, tiara, coroa e casquete: por cima de qualquer cabelo. */
    fun topo(g: HeroGfx, c: Classe) {
        val st = HeroLook.topo
        if (st == 0) return
        val k = cranio(c)
        when (st) {
            1 -> { // bandana
                val t = HeroLook.tecido(0xFFC62828.toInt())
                val td = HeroLook.tecido(0xFF8E1B1B.toInt())
                g.cube(0f, k.top - 1.9f, 0f, k.hw + 0.3f, 0.7f, k.hd + 0.3f, t)
                g.cube(0f, k.top - 1.9f, -k.hd - 0.5f, 0.8f, 0.8f, 0.5f, td)
                g.push()
                g.translate(0f, k.top - 1.9f, -k.hd - 0.9f)
                g.rotX(0.25f)
                g.cube(-0.8f, -1.6f, 0f, 0.55f, 1.7f, 0.18f, t)
                g.cube(0.8f, -2.1f, 0f, 0.55f, 2.2f, 0.18f, td)
                g.pop()
            }
            2 -> { // tiara
                val o = HeroLook.metal(0xFFE8B83A.toInt())
                val od = HeroLook.metal(0xFFB8861F.toInt())
                g.cube(0f, k.top - 1.5f, k.hd + 0.2f, k.hw * 0.9f, 0.3f, 0.2f, o)
                for (i in 0..1) {
                    val sd = if (i == 0) 1f else -1f
                    g.cube(sd * (k.hw + 0.25f), k.top - 1.5f, 0f, 0.2f, 0.3f, k.hd + 0.2f, o)
                }
                g.cube(0f, k.top - 1.5f, -k.hd - 0.25f, k.hw + 0.2f, 0.3f, 0.2f, od)
                g.cube(0f, k.top - 0.9f, k.hd + 0.25f, 0.45f, 0.8f, 0.18f, o)
                g.cube(0f, k.top - 1.2f, k.hd + 0.4f, 0.3f, 0.3f, 0.12f, 0xFF4FC3FF.toInt(), true)
            }
            3 -> { // coroa
                val o = HeroLook.metal(0xFFE8B83A.toInt())
                val od = HeroLook.metal(0xFFB8861F.toInt())
                g.cube(0f, k.top - 0.4f, 0f, k.hw + 0.3f, 0.5f, k.hd + 0.3f, o)
                g.cube(0f, k.top - 0.4f, 0f, k.hw + 0.35f, 0.15f, k.hd + 0.35f, od)
                for (i in -2..2) {
                    val h = if (i == 0) 1.9f else if (i % 2 == 0) 1.5f else 1.1f
                    g.cube(i * (k.hw * 0.42f), k.top + 0.1f + h, k.hd * 0.78f, 0.45f, h, 0.45f, o)
                    g.cube(i * (k.hw * 0.42f), k.top + 0.1f + h, -k.hd * 0.78f, 0.45f, h, 0.45f, o)
                }
                g.cube(0f, k.top + 0.9f, k.hd + 0.35f, 0.4f, 0.4f, 0.12f, 0xFFFF3A44.toInt(), true)
            }
            4 -> { // casquete de aço
                val a = HeroLook.metal(0xFFAEB9C2.toInt())
                val al = HeroLook.metal(0xFFD9E1E7.toInt())
                val ad = HeroLook.metal(0xFF6F7C86.toInt())
                g.cube(0f, k.top + 0.2f, -0.2f, k.hw + 0.4f, 1.0f, k.hd + 0.4f, a)
                g.cube(0f, k.top + 1.3f, -0.2f, k.hw * 0.7f, 0.4f, k.hd * 0.7f, al)
                g.cube(0f, k.top - 1.1f, 0f, k.hw + 0.5f, 0.35f, k.hd + 0.5f, ad)
                g.cube(0f, k.top - 3.0f, k.hd + 0.4f, 0.45f, 2.0f, 0.2f, a)
                g.cube(0f, k.top + 0.2f, 0f, 0.3f, 1.2f, k.hd + 0.5f, al)
            }
        }
    }

    // ───────────── rosto: cicatrizes, pinturas e acessórios ─────────────

    /** fz = plano à frente dos olhos; ex = meia distância dos olhos; ec = desvio do centro dos olhos; fw = meia largura do rosto. */
    private class Face(val fz: Float, val eyeY: Float, val ex: Float, val ec: Float, val fw: Float)

    private fun face(c: Classe): Face = when (c) {
        Classe.MAGO -> Face(4.7f, 5.2f, 2f, 0f, 4.4f)
        Classe.GUERREIRO -> Face(5.3f, 4.7f, 2f, 0f, 5f)
        Classe.BARBARO -> Face(5.15f, 4.8f, 2.2f, 0f, 4.8f)
        Classe.ARQUEIRO -> Face(4.7f, 5.6f, 2f, 0.15f, 4.7f)
        Classe.NINJA, Classe.PIKACHU, Classe.ICHIGO, Classe.LUFFY, Classe.EXPLORADOR -> Face(4.75f, 5.0f, 2f, 0f, 4.5f)
    }

    private const val CICATRIZ = 0xFFD98B80.toInt()
    private const val CICATRIZ_D = 0xFF8E3B3B.toInt()
    private const val TINTA_RED = 0xFFB0242A.toInt()
    private const val TINTA_PRETA = 0xFF1E1E22.toInt()
    private const val OURO = 0xFFE8B83A.toInt()
    private const val OURO_D = 0xFFB8861F.toInt()
    private const val COURO = 0xFF1A1512.toInt()

    private fun rosto(g: HeroGfx, c: Classe) {
        val f = face(c)
        val sp = spec(c)
        if (HeroLook.fem && !(c == Classe.NINJA && HeroLook.elmo)) feminino(g, f, sp)
        when (HeroLook.cicatriz) {
            1 -> cicatrizOlho(g, f)
            2 -> cicatrizBochecha(g, f, sp)
            3 -> cicatrizLabio(g, f, sp)
            4 -> cicatrizGarras(g, f)
        }
        when (HeroLook.pintura) {
            1 -> pinturaListras(g, f)
            2 -> pinturaMascara(g, f)
            3 -> pinturaLagrima(g, f)
            4 -> pinturaTigre(g, f)
        }
        when (HeroLook.acess) {
            1 -> tapaOlho(g, f)
            2 -> brincos(g, f, sp)
            3 -> aroSobrancelha(g, f)
            4 -> aroNariz(g, sp)
            5 -> oculos(g, f)
            6 -> monoculo(g, f)
        }
    }

    /** Rosto feminino: cílios, lábios e bochechas coradas. */
    private fun feminino(g: HeroGfx, f: Face, sp: Spec) {
        val z = f.fz + 0.06f
        for (i in 0..1) {
            val sd = if (i == 0) 1f else -1f
            val x = sd * f.ex + f.ec
            g.cube(x, f.eyeY + 0.95f, z, 0.95f, 0.14f, 0.06f, TINTA_PRETA)
            g.cube(x + sd * 0.85f, f.eyeY + 0.75f, z, 0.18f, 0.3f, 0.06f, TINTA_PRETA)
            g.cube(sd * f.fw * 0.62f, f.eyeY - 1.5f, z, 0.8f, 0.5f, 0.05f, 0xFFE89A9A.toInt())
        }
        g.cube(0f, sp.mouthY, z, 0.95f, 0.28f, 0.06f, 0xFFC2475A.toInt())
        g.cube(0f, sp.mouthY - 0.3f, z, 0.6f, 0.15f, 0.06f, 0xFFA83A4C.toInt())
    }

    private fun cicatrizOlho(g: HeroGfx, f: Face) {
        g.push()
        g.translate(-f.ex + f.ec, f.eyeY, f.fz)
        g.rotZ(0.45f)
        g.cube(0f, 0f, 0f, 0.2f, 3.2f, 0.08f, CICATRIZ)
        for (i in -2..2) g.cube(0f, i * 1.15f, 0.03f, 0.6f, 0.11f, 0.08f, CICATRIZ_D)
        g.pop()
    }

    private fun cicatrizBochecha(g: HeroGfx, f: Face, sp: Spec) {
        g.push()
        g.translate(f.fw * 0.6f, sp.noseY + 0.4f, f.fz)
        g.rotZ(-0.35f)
        g.cube(0f, 0f, 0f, 0.17f, 1.9f, 0.08f, CICATRIZ)
        for (i in -1..1) g.cube(0f, i * 1.1f, 0.03f, 0.5f, 0.1f, 0.08f, CICATRIZ_D)
        g.pop()
    }

    private fun cicatrizLabio(g: HeroGfx, f: Face, sp: Spec) {
        g.push()
        g.translate(1.3f, sp.mouthY + 0.2f, f.fz)
        g.cube(0f, 0f, 0f, 0.17f, 1.5f, 0.08f, CICATRIZ)
        for (i in -1..1) g.cube(0f, i * 0.9f, 0.03f, 0.45f, 0.1f, 0.08f, CICATRIZ_D)
        g.pop()
    }

    private fun cicatrizGarras(g: HeroGfx, f: Face) {
        for (i in -1..1) {
            g.push()
            g.translate(f.ex + f.ec + i * 0.95f, f.eyeY - 1.1f, f.fz)
            g.rotZ(-0.3f)
            g.cube(0f, -0.4f, 0f, 0.14f, 2.5f, 0.08f, CICATRIZ)
            g.cube(0f, -0.4f, 0.03f, 0.05f, 2.5f, 0.08f, CICATRIZ_D)
            g.pop()
        }
    }

    private fun pinturaListras(g: HeroGfx, f: Face) {
        for (k in 0..1) {
            val sd = if (k == 0) 1f else -1f
            val x = sd * f.ex + f.ec
            g.cube(x, f.eyeY - 2.0f, f.fz, 1.5f, 0.28f, 0.08f, TINTA_RED)
            g.cube(x, f.eyeY - 2.8f, f.fz, 1.2f, 0.28f, 0.08f, TINTA_RED)
        }
    }

    private fun pinturaMascara(g: HeroGfx, f: Face) {
        for (k in 0..1) {
            val sd = if (k == 0) 1f else -1f
            val x = sd * f.ex + f.ec
            g.cube(x, f.eyeY + 1.3f, f.fz, 1.8f, 0.3f, 0.08f, TINTA_PRETA)
            g.cube(x, f.eyeY - 1.3f, f.fz, 1.8f, 0.3f, 0.08f, TINTA_PRETA)
            g.cube(x + sd * 1.7f, f.eyeY, f.fz, 0.3f, 1.3f, 0.08f, TINTA_PRETA)
        }
        g.cube(f.ec, f.eyeY + 0.9f, f.fz, 0.5f, 0.3f, 0.08f, TINTA_PRETA)
    }

    private fun pinturaLagrima(g: HeroGfx, f: Face) {
        g.push()
        g.translate(f.ex + f.ec, f.eyeY - 2.1f, f.fz)
        g.rotZ(0.785f)
        g.cube(0f, 0f, 0f, 0.5f, 0.5f, 0.08f, OURO)
        g.cube(0f, 0f, 0.03f, 0.25f, 0.25f, 0.08f, OURO_D)
        g.pop()
    }

    private fun pinturaTigre(g: HeroGfx, f: Face) {
        for (k in 0..1) {
            val sd = if (k == 0) 1f else -1f
            for (i in 0..2) {
                val x = sd * (f.fw * 0.55f + i * 0.8f)
                g.cube(x, f.eyeY - 2.2f - i * 0.2f, f.fz, 0.13f, 1.1f - i * 0.15f, 0.08f, TINTA_PRETA)
            }
        }
    }

    private fun anelFrente(g: HeroGfx, x: Float, y: Float, z: Float, w: Float, h: Float, cor: Int) {
        val t = 0.1f
        g.cube(x, y + h, z, w, t, t, cor)
        g.cube(x, y - h, z, w, t, t, cor)
        g.cube(x - w, y, z, t, h, t, cor)
        g.cube(x + w, y, z, t, h, t, cor)
    }

    private fun tapaOlho(g: HeroGfx, f: Face) {
        val x = -f.ex + f.ec
        g.cube(x, f.eyeY, f.fz + 0.1f, 1.5f, 1.3f, 0.12f, COURO)
        g.push()
        g.translate(f.ec, f.eyeY + 1.6f, f.fz - 0.2f)
        g.rotZ(0.3f)
        g.cube(0f, 0f, 0f, f.fw * 0.85f, 0.17f, 0.1f, COURO)
        g.pop()
    }

    private fun brincos(g: HeroGfx, f: Face, sp: Spec) {
        for (k in 0..1) {
            val sd = if (k == 0) 1f else -1f
            val x = sd * (f.fw + 0.7f)
            val y = sp.earY - 0.8f
            val z = sp.earZ
            g.cube(x, y + 0.55f, z, 0.18f, 0.12f, 0.5f, OURO)
            g.cube(x, y - 0.55f, z, 0.18f, 0.12f, 0.5f, OURO)
            g.cube(x, y, z - 0.5f, 0.18f, 0.55f, 0.12f, OURO)
            g.cube(x, y, z + 0.5f, 0.18f, 0.55f, 0.12f, OURO)
            g.cube(x, y - 1.1f, z, 0.22f, 0.22f, 0.22f, TINTA_RED)
        }
    }

    private fun aroSobrancelha(g: HeroGfx, f: Face) {
        anelFrente(g, f.ex + f.ec + 0.4f, f.eyeY + 1.5f, f.fz, 0.35f, 0.35f, OURO)
    }

    private fun aroNariz(g: HeroGfx, sp: Spec) {
        anelFrente(g, 0f, sp.noseY - 1.0f, sp.noseZ + 0.45f, 0.4f, 0.4f, OURO)
    }

    private fun oculos(g: HeroGfx, f: Face) {
        for (k in 0..1) {
            val sd = if (k == 0) 1f else -1f
            anelFrente(g, sd * f.ex + f.ec, f.eyeY, f.fz, 1.5f, 1.3f, OURO_D)
        }
        g.cube(f.ec, f.eyeY + 0.5f, f.fz, (f.ex - 1.5f).coerceAtLeast(0.2f), 0.1f, 0.1f, OURO_D)
    }

    private fun monoculo(g: HeroGfx, f: Face) {
        val x = f.ex + f.ec
        anelFrente(g, x, f.eyeY, f.fz, 1.5f, 1.4f, OURO)
        g.cube(x + 1.6f, f.eyeY - 2.8f, f.fz, 0.07f, 1.5f, 0.08f, OURO)
    }

    private fun elfa(g: HeroGfx, sd: Float, sp: Spec, sk: Int, skD: Int, k: Float) {
        g.push()
        g.translate(sd * sp.earX, sp.earY, sp.earZ)
        g.rotZ(-sd * 0.42f)
        g.rotX(-0.5f)
        g.cube(sd * 0.45f, 1.3f * k, 0f, 0.3f, 1.7f * k, 0.95f, sk)
        g.cube(sd * 0.45f, 3.3f * k, 0f, 0.24f, 0.9f * k, 0.7f, sk)
        g.cube(sd * 0.45f, 4.6f * k, 0f, 0.17f, 0.5f * k, 0.42f, sk)
        g.cube(sd * 0.45f, 5.5f * k, 0f, 0.1f, 0.4f * k, 0.22f, sk)
        g.cube(sd * 0.78f, 1.4f * k, 0.05f, 0.07f, 1.2f * k, 0.5f, skD)
        g.pop()
    }

    private fun felina(g: HeroGfx, sd: Float, sp: Spec, sk: Int) {
        g.push()
        g.translate(sd * sp.catX, sp.catY, sp.catZ)
        g.rotZ(-sd * sp.catTilt)
        g.cube(0f, 0.9f, 0f, 1.45f, 0.9f, 0.6f, sk)
        g.cube(0f, 2.3f, 0f, 1.05f, 0.7f, 0.5f, sk)
        g.cube(0f, 3.3f, 0f, 0.6f, 0.45f, 0.4f, sk)
        g.cube(0f, 1.3f, 0.5f, 0.8f, 0.9f, 0.12f, PINK)
        g.cube(0f, 2.5f, 0.45f, 0.45f, 0.5f, 0.1f, PINK)
        g.pop()
    }

    private fun goblin(g: HeroGfx, sd: Float, sp: Spec, sk: Int, skD: Int) {
        g.push()
        g.translate(sd * sp.earX, sp.earY + 0.6f, sp.earZ + 0.2f)
        g.rotZ(-sd * 1.2f)
        g.cube(0f, 1.5f, 0f, 0.35f, 1.6f, 1.2f, sk)
        g.cube(0f, 3.6f, 0f, 0.3f, 0.9f, 0.95f, sk)
        g.cube(0f, 5.0f, 0f, 0.22f, 0.6f, 0.65f, sk)
        g.cube(0f, 6.0f, 0f, 0.15f, 0.4f, 0.35f, sk)
        g.cube(0f, 2.0f, 0.05f, 0.4f, 1.2f, 0.7f, skD)
        g.pop()
    }

    private fun chifreDiabo(g: HeroGfx, sd: Float, sp: Spec) {
        g.push()
        g.translate(sd * sp.hornX, sp.hornY, sp.hornZ)
        g.rotZ(-sd * (0.3f + sp.hornOut))
        g.rotX(-0.15f)
        for (i in 0..3) {
            val w = lerp(0.75f, 0.22f, i / 3f)
            g.cube(0f, 0.95f, 0f, w, 1f, w, if (i == 3) BONE else HORN_DARK)
            g.translate(0f, 1.9f, 0f)
            g.rotX(-0.22f)
            g.rotZ(-sd * 0.04f)
        }
        g.pop()
    }

    private fun chifreDragao(g: HeroGfx, sd: Float, sp: Spec) {
        g.push()
        g.translate(sd * sp.hornX, sp.hornY, sp.hornZ)
        g.rotZ(-sd * (0.22f + sp.hornOut))
        g.rotX(-0.85f)
        for (i in 0..2) {
            val w = lerp(0.8f, 0.25f, i / 2f)
            g.cube(0f, 1.2f, 0f, w, 1.25f, w, BONE)
            g.translate(0f, 2.4f, 0f)
            g.rotX(-0.1f)
        }
        g.pop()
    }

    private fun presas(g: HeroGfx, sp: Spec, grande: Boolean) {
        val x = if (grande) 1.9f else 1.1f
        val hy = if (grande) 1.0f else 0.55f
        val hw = if (grande) 0.3f else 0.2f
        for (k in 0..1) {
            val sd = if (k == 0) 1f else -1f
            g.cube(sd * x, sp.mouthY + hy * 0.8f, sp.mouthZ, hw, hy, hw * 0.95f, IVORY)
        }
    }

    private fun focinhoGato(g: HeroGfx, sp: Spec, skL: Int) {
        g.cube(0f, sp.noseY - 0.5f, sp.noseZ + 0.35f, 1.5f, 1.0f, 0.75f, skL)
        g.cube(0f, sp.noseY + 0.35f, sp.noseZ + 1.05f, 0.55f, 0.38f, 0.22f, NOSE)
    }

    private fun focinhoDragao(g: HeroGfx, sp: Spec, sk: Int, skD: Int) {
        g.cube(0f, sp.noseY - 0.6f, sp.noseZ + 1.5f, 1.9f, 1.4f, 1.7f, sk)
        g.cube(0f, sp.noseY - 1.9f, sp.noseZ + 1.2f, 1.6f, 0.5f, 1.4f, skD)
        g.cube(-0.8f, sp.noseY + 0.55f, sp.noseZ + 3.25f, 0.28f, 0.28f, 0.12f, DARK)
        g.cube(0.8f, sp.noseY + 0.55f, sp.noseZ + 3.25f, 0.28f, 0.28f, 0.12f, DARK)
    }

    // ───────────── cauda ─────────────

    private fun corrente(
        g: HeroGfx, n: Int, len: Float, w0: Float, w1: Float, h0: Float, h1: Float,
        pitch0: Float, dPitch: Float, balanco: Float, cor: Int, corD: Int
    ) {
        g.rotX(pitch0)
        for (i in 0 until n) {
            val f = if (n > 1) i / (n - 1f) else 0f
            g.cube(0f, 0f, -len, lerp(w0, w1, f), lerp(h0, h1, f), len, if (i % 2 == 0) cor else corD)
            g.translate(0f, 0f, -2f * len)
            g.rotX(dPitch)
            g.rotY(balanco)
        }
    }

    /** Chamado no começo do tronco de cada modelo (origem na cintura). */
    fun cauda(g: HeroGfx, c: Classe) {
        val r = HeroLook.raca
        if (r.cauda == 0) return
        val sp = spec(c)
        val sk = HeroLook.skin(sp.skin)
        val skD = HeroLook.skinD(sp.skin)
        val t = System.nanoTime() / 1_000_000_000f
        val balanco = sin(t * 2.1f) * 0.1f
        g.push()
        g.translate(0f, sp.tailY, sp.tailZ)
        when (r.cauda) {
            1 -> {
                corrente(g, 7, 1.2f, 0.45f, 0.3f, 0.45f, 0.3f, -0.7f, 0.2f, balanco, skD, sk)
                g.cube(0f, 0f, -1.3f, 1.0f, 0.25f, 1.1f, SPADE)
            }
            2 -> corrente(g, 6, 1.5f, 1.9f, 0.4f, 1.6f, 0.35f, -0.15f, 0.07f, balanco * 0.8f, sk, skD)
            3 -> corrente(g, 6, 1.4f, 0.8f, 0.65f, 0.8f, 0.65f, 0.9f, -0.12f, balanco * 1.4f, sk, skD)
        }
        g.pop()
    }
}
