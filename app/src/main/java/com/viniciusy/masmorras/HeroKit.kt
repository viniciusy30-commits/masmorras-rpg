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
        Classe.NINJA -> Cranio(9.5f, 4.5f, 4.3f, 0xFF1C1626.toInt(), 0.8f)
    }

    /** Base de todo penteado: topo com brilho, testa, costeletas e nuca (em camadas, no estilo dos blocos). */
    private fun base(g: HeroGfx, k: Cranio, col: Int, colL: Int, colD: Int) {
        g.cube(0f, k.top + 0.45f, -0.1f, k.hw + 0.35f, 0.85f, k.hd + 0.35f, col)
        g.cube(0f, k.top + 1.35f, -0.2f, k.hw * 0.72f, 0.15f, k.hd * 0.78f, colL)
        g.cube(0f, k.top - 1.0f, k.hd + 0.2f, k.hw + 0.1f, 0.9f, 0.3f, col)
        g.cube(0f, k.top - 1.95f, k.hd + 0.25f, k.hw * 0.78f, 0.12f, 0.25f, colD)
        for (i in 0..1) {
            val sd = if (i == 0) 1f else -1f
            g.cube(sd * (k.hw + 0.25f), k.top - 2.3f, -0.5f, 0.4f, 2.3f, k.hd * 0.55f, colD)
            g.cube(sd * (k.hw + 0.55f), k.top - 1.4f, -0.5f, 0.12f, 1.2f, k.hd * 0.45f, col)
        }
        g.cube(0f, k.top - 2.9f, -k.hd - 0.25f, k.hw + 0.25f, 3.3f, 0.5f, colD)
        g.cube(0f, k.top - 1.4f, -k.hd - 0.55f, k.hw * 0.8f, 1.6f, 0.15f, col)
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
        if (st == 0) {
            // sem chapéu/elmo e sem cabelo próprio: coloca o penteado básico para a cabeça não ficar pelada
            if (!elmo && (c == Classe.GUERREIRO || c == Classe.BARBARO || c == Classe.MAGO)) base(g, k, col, colL, colD)
            return
        }
        if (st == 1) return
        when (st) {
            2 -> { // curto
                base(g, k, col, colL, colD)
                g.cube(-k.hw * 0.45f, k.top - 2.2f, k.hd + 0.3f, k.hw * 0.3f, 0.5f, 0.25f, col)
                g.cube(k.hw * 0.35f, k.top - 2.0f, k.hd + 0.3f, k.hw * 0.4f, 0.4f, 0.25f, colL)
            }
            3 -> { // longo, liso, caindo pelas costas e pelos lados
                base(g, k, col, colL, colD)
                g.push()
                g.translate(0f, k.top - 5.8f, -k.hd - 0.3f)
                g.rotX(0.06f + bal)
                g.cube(0f, -3.2f, 0f, k.hw + 0.1f, 3.4f, 0.55f, col)
                g.cube(0f, -3.2f, -0.5f, k.hw * 0.8f, 3.0f, 0.1f, colL)
                g.cube(0f, -6.6f, 0f, k.hw * 0.85f, 0.7f, 0.5f, colD)
                g.pop()
                for (i in 0..1) {
                    val sd = if (i == 0) 1f else -1f
                    g.cube(sd * (k.hw + 0.35f), k.top - 5.8f, -1.8f, 0.5f, 3.6f, 1.9f, colD)
                    g.cube(sd * (k.hw + 0.65f), k.top - 5.0f, -1.8f, 0.12f, 2.8f, 1.5f, col)
                }
            }
            4 -> { // moicano: laterais raspadas, crista no meio
                g.cube(0f, k.top + 0.4f, -0.2f, 0.85f, 0.7f, k.hd * 0.95f, col)
                for (j in 0..5) {
                    val h = (if (elmo) 0.3f else 2.4f) - j * 0.2f
                    if (h > 0.2f) g.cube(0f, k.top + 1.1f + h * 0.5f, 3.4f - j * 1.35f, 0.75f, h, 0.55f, if (j % 2 == 0) col else colL)
                }
                g.cube(0f, k.top - 1.0f, k.hd + 0.2f, 0.85f, 0.9f, 0.3f, col)
                g.cube(0f, k.top - 3.2f, -k.hd - 0.25f, 0.85f, 3.0f, 0.4f, colD)
            }
            5 -> { // rabo de cavalo alto
                base(g, k, col, colL, colD)
                g.cube(0f, k.top - 0.3f, -k.hd - 0.65f, 0.95f, 0.8f, 0.55f, colD)
                g.push()
                g.translate(0f, k.top - 0.3f, -k.hd - 1.0f)
                g.rotX(-1.0f + bal * 2f)
                g.cube(0f, 1.8f, 0f, 1.15f, 1.9f, 1.0f, col)
                g.cube(0f, 0.2f, 0f, 1.3f, 0.3f, 1.2f, colD)
                g.translate(0f, 3.6f, 0f)
                g.rotX(0.7f + bal * 3f)
                g.cube(0f, 1.8f, 0f, 0.95f, 2.0f, 0.85f, colL)
                g.translate(0f, 3.8f, 0f)
                g.rotX(0.8f + bal * 4f)
                g.cube(0f, 1.4f, 0f, 0.7f, 1.6f, 0.65f, col)
                g.pop()
            }
            6 -> { // coque
                base(g, k, col, colL, colD)
                g.cube(0f, k.top + 2.0f, -1.3f, 1.9f, 1.3f, 1.9f, col)
                g.cube(0f, k.top + 3.0f, -1.3f, 1.4f, 0.45f, 1.4f, colL)
                g.cube(0f, k.top + 1.45f, -1.3f, 2.1f, 0.18f, 2.1f, colD)
            }
            7 -> { // trança grossa
                base(g, k, col, colL, colD)
                g.cube(0f, k.top - 5.7f, -k.hd - 0.7f, 1.0f, 0.5f, 0.5f, colD)
                g.push()
                g.translate(0f, k.top - 6.0f, -k.hd - 1.0f)
                g.rotX(0.05f + bal * 2f)
                for (j in 0..5) {
                    val wv = if (j % 2 == 0) 0.35f else -0.35f
                    g.cube(wv, -0.9f - j * 1.55f, 0f, 0.95f, 0.8f, 0.8f, if (j % 2 == 0) col else colD)
                    g.cube(-wv, -1.7f - j * 1.55f, 0f, 0.85f, 0.8f, 0.75f, if (j % 2 == 0) colD else colL)
                }
                g.cube(0f, -10.6f, 0f, 0.7f, 0.5f, 0.7f, colD)
                g.pop()
            }
            8 -> { // espetado
                base(g, k, col, colL, colD)
                for (i in -2..2) for (r in 0..1) {
                    val h = (if (elmo) 0.4f else 1.5f + (2 - kotlin.math.abs(i)) * 0.7f)
                    g.cube(i * (k.hw * 0.4f), k.top + 1.4f + h * 0.5f, if (r == 0) 2.4f else -2.2f, 0.8f, h, 0.8f, if ((i + r) % 2 == 0) col else colL)
                }
            }
            9 -> { // franja longa, cobrindo a testa e uma lateral
                base(g, k, col, colL, colD)
                g.cube(0f, k.top - 2.3f, k.hd + 0.35f, k.hw * 0.98f, 1.3f, 0.3f, col)
                g.cube(-k.hw * 0.5f, k.top - 3.6f, k.hd + 0.4f, k.hw * 0.5f, 0.7f, 0.25f, colL)
                g.cube(k.hw * 0.55f, k.top - 4.0f, k.hd + 0.4f, k.hw * 0.4f, 1.0f, 0.25f, colD)
                for (i in 0..1) {
                    val sd = if (i == 0) 1f else -1f
                    g.cube(sd * (k.hw + 0.4f), k.top - 4.6f, 0.9f, 0.4f, 2.6f, 1.2f, col)
                }
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
