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
        Classe.NINJA -> Spec(0xFFF1C27D.toInt(), 4.6f, 4.8f, -0.4f, 3.4f, 10.2f, -0.2f, 0.3f, 3.8f, 9.6f, -0.2f, 0.7f, 3.8f, 4.5f, 2.4f, 4.8f, -1.0f, -4.6f)
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
    }

    // ───────────── rosto: cicatrizes, pinturas e acessórios ─────────────

    /** fz = plano à frente dos olhos; ex = meia distância dos olhos; ec = desvio do centro dos olhos; fw = meia largura do rosto. */
    private class Face(val fz: Float, val eyeY: Float, val ex: Float, val ec: Float, val fw: Float)

    private fun face(c: Classe): Face = when (c) {
        Classe.MAGO -> Face(4.7f, 5.2f, 2f, 0f, 4.4f)
        Classe.GUERREIRO -> Face(5.3f, 4.7f, 2f, 0f, 5f)
        Classe.BARBARO -> Face(5.15f, 4.8f, 2.2f, 0f, 4.8f)
        Classe.ARQUEIRO -> Face(4.7f, 5.6f, 2f, 0.15f, 4.7f)
        Classe.NINJA -> Face(4.75f, 5.0f, 2f, 0f, 4.5f)
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
