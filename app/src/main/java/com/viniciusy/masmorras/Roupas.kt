package com.viniciusy.masmorras

/**
 * Roupas dos 5 heróis. Cada classe tem 5 trajes próprios (nome, cores e estilo), e o peito, as pernas
 * e a capa se escolhem separadamente. A opção 0 é o visual original da classe.
 * Também desenha o corpo feminino (busto) por cima de qualquer roupa.
 * Os 5 modelos 3D chamam isto no mesmo esquema do HeroKit.
 */
object Roupas {
    const val N = 6 // 0 = original + 5 trajes

    /** Um traje: estilo 1 linho, 2 couro, 3 malha, 4 placas, 5 gala, 6 peles. */
    class Traje(val nome: String, val estilo: Int, val base: Int, val luz: Int, val esc: Int, val ouro: Int, val metal: Boolean)

    class Capa(val nome: String, val cor: Int, val corD: Int, val pele: Boolean)

    private fun t(n: String, e: Int, b: Long, l: Long, d: Long, o: Long, m: Boolean) =
        Traje(n, e, b.toInt(), l.toInt(), d.toInt(), o.toInt(), m)

    private fun cp(n: String, c: Long, d: Long, p: Boolean = false) = Capa(n, c.toInt(), d.toInt(), p)

    private val GUERREIRO = listOf(
        t("Cavaleiro de aço", 4, 0xFFB8C2CC, 0xFFE2E8EE, 0xFF7D8791, 0xFFD9A93A, true),
        t("Gladiador de couro", 2, 0xFF7A4A28, 0xFF9A6236, 0xFF4A2C14, 0xFFC9A04A, false),
        t("Guarda de malha", 3, 0xFF8E98A3, 0xFFB4BEC8, 0xFF5F6872, 0xFFD9A93A, true),
        t("Capitão real", 5, 0xFF1E3F8F, 0xFF3A5FBF, 0xFF132A63, 0xFFE0B040, false),
        t("Mercenário de peles", 6, 0xFF5A4630, 0xFFE9E0CC, 0xFF3E2F20, 0xFF9A7420, false)
    )
    private val BARBARO = listOf(
        t("Peles de urso", 6, 0xFF4A3A2A, 0xFFC9B79A, 0xFF2E2217, 0xFF8A6A30, false),
        t("Couro tribal", 2, 0xFF8A5B33, 0xFFA97545, 0xFF55341A, 0xFFE8DCC0, false),
        t("Saqueador de ferro", 3, 0xFF5F6872, 0xFF8892A0, 0xFF3C434B, 0xFF9A7420, true),
        t("Armadura de ossos", 4, 0xFFE8DCC0, 0xFFF3EBD2, 0xFFB8A98A, 0xFF6B4423, false),
        t("Guerreiro do clã", 5, 0xFF7A1F1F, 0xFFA33030, 0xFF4E1010, 0xFFE8DCC0, false)
    )
    private val MAGO = listOf(
        t("Arquimago", 5, 0xFF5A2A9E, 0xFF7A4CC4, 0xFF3C1B6E, 0xFFE0B040, false),
        t("Vestes do sábio", 1, 0xFFE8E2D0, 0xFFF5F0E2, 0xFF3A5FA8, 0xFF3A5FA8, false),
        t("Feiticeiro das sombras", 2, 0xFF2A2A3A, 0xFF3E3E55, 0xFF16161F, 0xFF8A5BFF, false),
        t("Mago de fogo", 5, 0xFFB5341B, 0xFFD9552F, 0xFF7E200F, 0xFFFFB347, false),
        t("Druida do bosque", 6, 0xFF3E6B3A, 0xFFD8E2C0, 0xFF284A25, 0xFFB89A50, false)
    )
    private val ARQUEIRO = listOf(
        t("Caçador da floresta", 2, 0xFF4F6B3A, 0xFF6C8A52, 0xFF33472A, 0xFFB8963A, false),
        t("Elfo de seda", 1, 0xFFD7E6C8, 0xFFEEF6E4, 0xFF8FA886, 0xFFC8D6E0, false),
        t("Ladrão encapuzado", 2, 0xFF2E2A33, 0xFF47404F, 0xFF1A171E, 0xFFA8A8B8, false),
        t("Guarda élfico", 4, 0xFFA9B8A0, 0xFFD2DEC8, 0xFF76866E, 0xFFC8D6E0, true),
        t("Patrulheiro do inverno", 6, 0xFF6B7A8F, 0xFFF0F4F8, 0xFF48566A, 0xFFB8C6D6, false)
    )
    private val NINJA = listOf(
        t("Shinobi noturno", 1, 0xFF1B1A24, 0xFF2C2A3A, 0xFF0F0E15, 0xFFB5242E, false),
        t("Assassino carmesim", 5, 0xFF8E1A22, 0xFFB52C34, 0xFF5E0F15, 0xFF2B2B2B, false),
        t("Ninja da neve", 2, 0xFFD5DCE2, 0xFFEEF2F5, 0xFF9AA4AE, 0xFF4A5560, false),
        t("Ninja blindado", 4, 0xFF3A3F4A, 0xFF5A6070, 0xFF22252D, 0xFFC9302C, true),
        t("Mestre dourado", 5, 0xFF2B2438, 0xFF41375A, 0xFF181321, 0xFFE0B040, false)
    )

    private val CAPAS_G = listOf(
        cp("Capa real azul", 0xFF24459E, 0xFF182D68), cp("Capa vermelha", 0xFFA3202E, 0xFF6E1520),
        cp("Pele de lobo", 0xFF6B6B70, 0xFF3E3E42, true), cp("Capa preta", 0xFF22222A, 0xFF111116),
        cp("Capa branca de cavaleiro", 0xFFE6E6EC, 0xFFB0B0BC)
    )
    private val CAPAS_B = listOf(
        cp("Pele de urso", 0xFF4A3A2A, 0xFF2E2217, true), cp("Pele de lobo", 0xFF6B6B70, 0xFF3E3E42, true),
        cp("Manto vermelho", 0xFF8A1F1F, 0xFF541010), cp("Manto de couro", 0xFF6B4423, 0xFF3B2412),
        cp("Pele de mamute", 0xFF8A6A48, 0xFF5A4630, true)
    )
    private val CAPAS_M = listOf(
        cp("Manto astral", 0xFF2A2F7A, 0xFF181C50), cp("Manto roxo", 0xFF5A2A9E, 0xFF3C1B6E),
        cp("Manto carmesim", 0xFF9A2A1B, 0xFF661B10), cp("Manto da floresta", 0xFF2E6B34, 0xFF1D4522),
        cp("Manto branco", 0xFFEDEAE0, 0xFFBDB8A8)
    )
    private val CAPAS_A = listOf(
        cp("Capa élfica verde", 0xFF2E6B34, 0xFF1D4522), cp("Capa cinza", 0xFF6B7078, 0xFF444850),
        cp("Capa de camuflagem", 0xFF4F5A32, 0xFF343C20), cp("Capa marrom", 0xFF6B4423, 0xFF3B2412),
        cp("Pele de raposa", 0xFFB8652A, 0xFF7E4219, true)
    )
    private val CAPAS_N = listOf(
        cp("Capa da noite", 0xFF15151C, 0xFF0A0A0F), cp("Capa carmesim", 0xFF8E1A22, 0xFF5E0F15),
        cp("Capa da neve", 0xFFE6ECF0, 0xFFA8B2BC), cp("Capa roxa", 0xFF4A2A7A, 0xFF2E1A50),
        cp("Capa dourada", 0xFFB8902A, 0xFF7E6018)
    )

    fun trajes(c: Classe): List<Traje> = when (c) {
        Classe.GUERREIRO -> GUERREIRO
        Classe.BARBARO -> BARBARO
        Classe.MAGO -> MAGO
        Classe.ARQUEIRO -> ARQUEIRO
        Classe.NINJA -> NINJA
    }

    fun capas(c: Classe): List<Capa> = when (c) {
        Classe.GUERREIRO -> CAPAS_G
        Classe.BARBARO -> CAPAS_B
        Classe.MAGO -> CAPAS_M
        Classe.ARQUEIRO -> CAPAS_A
        Classe.NINJA -> CAPAS_N
    }

    fun nomesPeito(c: Classe): List<String> = listOf("Original") + trajes(c).map { it.nome }
    fun nomesPernas(c: Classe): List<String> = listOf("Original") + trajes(c).map { it.nome }
    fun nomesCapa(c: Classe): List<String> = listOf("Sem capa") + capas(c).map { it.nome }

    private fun traje(c: Classe, k: Int): Traje = trajes(c)[(k - 1).coerceIn(0, 4)]

    private const val PELE_PADRAO = 0xFFF1C27D.toInt()

    private fun mul(c: Int, k: Float): Int {
        val r = (((c shr 16) and 0xFF) * k).toInt().coerceIn(0, 255)
        val g = (((c shr 8) and 0xFF) * k).toInt().coerceIn(0, 255)
        val b = ((c and 0xFF) * k).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun cor(t: Traje, x: Int): Int = if (t.metal) HeroLook.metal(x) else HeroLook.tecido(x)
    private fun ouro(t: Traje): Int = HeroLook.metal(t.ouro)
    private fun ouroD(t: Traje): Int = HeroLook.metal(mul(t.ouro, 0.72f))
    private fun pele(): Int = HeroLook.skin(PELE_PADRAO)

    /** Medidas do corpo de cada classe (espaço do peito e da barriga). */
    private class D(
        val cy: Float, val hx: Float, val hy: Float, val hz: Float, val zF: Float,
        val ay: Float, val ahx: Float, val ahy: Float, val ahz: Float, val azF: Float
    )

    private fun dim(c: Classe): D = when (c) {
        Classe.GUERREIRO -> D(9.8f, 6.9f, 5.0f, 3.9f, 5.95f, 3.8f, 5.2f, 2.2f, 3.5f, 3.8f)
        Classe.BARBARO -> D(9.8f, 7.4f, 5.0f, 4.2f, 5.1f, 3.8f, 5.9f, 2.2f, 3.7f, 3.9f)
        Classe.MAGO -> D(9.8f, 5.7f, 4.8f, 3.5f, 4.1f, 3.8f, 5.0f, 2.2f, 3.3f, 3.6f)
        Classe.ARQUEIRO -> D(9.8f, 5.5f, 4.8f, 3.4f, 4.05f, 3.8f, 0f, 0f, 0f, 0f)
        Classe.NINJA -> D(9.6f, 5.4f, 4.8f, 3.4f, 4.05f, 3.8f, 4.8f, 2.2f, 3.2f, 3.45f)
    }

    /** Medidas das pernas: coxa e canela (hx, hz, z da frente, centro y, meia altura). */
    private class L(
        val th: Float, val tz: Float, val tzF: Float,
        val sh: Float, val sz: Float, val szF: Float, val sy: Float, val shy: Float
    )

    private fun lim(c: Classe): L = when (c) {
        Classe.GUERREIRO -> L(2.7f, 2.75f, 3.2f, 2.5f, 2.55f, 2.9f, -2.7f, 2.8f)
        Classe.BARBARO -> L(2.95f, 2.95f, 3.1f, 2.7f, 2.75f, 2.95f, -2.7f, 2.8f)
        Classe.MAGO -> L(2.2f, 2.3f, 2.4f, 2.05f, 2.1f, 2.5f, -2.7f, 2.8f)
        Classe.ARQUEIRO -> L(2.25f, 2.3f, 2.5f, 2.45f, 2.15f, 2.3f, -3.1f, 2.7f)
        Classe.NINJA -> L(2.35f, 2.4f, 2.5f, 2.15f, 2.15f, 2.3f, -2.7f, 2.8f)
    }

    // ───────────── barriga / cintura ─────────────

    /** Chamado no espaço do corpo (quadril), logo no começo do torso. */
    fun abdomen(g: HeroGfx, c: Classe) {
        val k = HeroLook.peito
        if (k <= 0) return
        val d = dim(c)
        if (d.ahx <= 0f) return
        val t = traje(c, k)
        val base = cor(t, t.base)
        g.cube(0f, d.ay, 0f, d.ahx + 0.12f, d.ahy + 0.05f, d.ahz + 0.12f, base)
        val dz = (d.azF - d.ahz) / 2f
        if (dz > 0.05f) g.cube(0f, d.ay, d.ahz + dz, d.ahx - 0.2f, d.ahy - 0.2f, dz, base)
        val z = d.azF + 0.05f
        when (t.estilo) {
            1 -> g.cube(0f, d.ay, z, 0.35f, d.ahy - 0.4f, 0.06f, cor(t, t.esc))
            2 -> {
                g.cube(0f, d.ay - 1.0f, z, d.ahx - 0.3f, 0.35f, 0.07f, cor(t, t.esc))
                g.cube(0f, d.ay - 1.0f, z + 0.06f, 0.6f, 0.5f, 0.06f, ouro(t))
            }
            3 -> for (i in 0..2) g.cube(0f, d.ay - 1.4f + i * 1.4f, z, d.ahx - 0.3f, 0.12f, 0.06f, cor(t, if (i % 2 == 0) t.esc else t.luz))
            4 -> {
                for (i in 0..2) g.cube(0f, d.ay - 1.4f + i * 1.4f, z, d.ahx - 0.3f, 0.5f, 0.07f, cor(t, if (i == 1) t.luz else t.base))
                g.cube(0f, d.ay - 0.2f, z + 0.07f, d.ahx - 0.3f, 0.1f, 0.05f, ouro(t))
            }
            5 -> {
                g.cube(0f, d.ay - 1.2f, z, d.ahx - 0.2f, 0.4f, 0.07f, ouro(t))
                g.cube(0f, d.ay, z, 0.4f, d.ahy - 0.4f, 0.06f, ouroD(t))
            }
            6 -> {
                g.cube(0f, d.ay - 1.8f, z, d.ahx - 0.1f, 0.5f, 0.08f, cor(t, t.luz))
                g.cube(0f, d.ay - 0.6f, z, d.ahx - 0.4f, 0.3f, 0.06f, cor(t, t.esc))
            }
        }
    }

    // ───────────── peito ─────────────

    /** Cor do peito original de cada classe (para o busto combinar quando não há traje). */
    private fun peitoOriginal(c: Classe): Int = when (c) {
        Classe.GUERREIRO -> HeroLook.metal(0xFFAEB9C2.toInt())
        Classe.BARBARO -> HeroLook.skin(0xFFD99A5E.toInt())
        Classe.MAGO -> HeroLook.tecido(0xFF2E5CB8.toInt())
        Classe.ARQUEIRO -> HeroLook.tecido(0xFF66795A.toInt())
        Classe.NINJA -> HeroLook.tecido(0xFF1B1A24.toInt())
    }

    /** Chamado dentro do peito (já com giro e respiração), no começo da parte de cima do torso. */
    fun peito(g: HeroGfx, c: Classe) {
        val k = HeroLook.peito
        val d = dim(c)
        if (k > 0) vestirPeito(g, d, traje(c, k))
        if (HeroLook.fem) busto(g, c, d, k)
    }

    private fun busto(g: HeroGfx, c: Classe, d: D, k: Int) {
        val col: Int
        val colL: Int
        if (k > 0) {
            val t = traje(c, k)
            col = cor(t, t.base)
            colL = cor(t, t.luz)
        } else {
            col = peitoOriginal(c)
            colL = mul(col, 1.12f)
        }
        val zBase = if (k > 0) d.zF else d.hz
        val bx = d.hx * 0.36f
        val bw = d.hx * 0.27f
        for (i in 0..1) {
            val sd = if (i == 0) 1f else -1f
            g.cube(sd * bx, d.cy + 0.9f, zBase + 0.15f, bw, 1.35f, 0.85f, col)
            g.cube(sd * bx, d.cy + 1.0f, zBase + 0.95f, bw * 0.65f, 0.95f, 0.2f, colL)
        }
        g.cube(0f, d.cy + 1.0f, zBase + 0.4f, 0.18f, 1.1f, 0.5f, mul(col, 0.7f))
    }

    private fun vestirPeito(g: HeroGfx, d: D, t: Traje) {
        val base = cor(t, t.base)
        g.cube(0f, d.cy, 0f, d.hx + 0.12f, d.hy + 0.05f, d.hz + 0.12f, base)
        val dz = (d.zF - d.hz) / 2f
        if (dz > 0.05f) g.cube(0f, d.cy, d.hz + dz, d.hx - 0.2f, d.hy - 0.2f, dz, base)
        val z = d.zF + 0.05f
        val top = d.cy + d.hy
        val w = d.hx - 0.3f
        val esc = cor(t, t.esc)
        val luz = cor(t, t.luz)
        when (t.estilo) {
            1 -> { // linho: gola em V, cordões e barra
                g.cube(0f, top - 1.2f, z, 1.2f, 1.2f, 0.07f, pele())
                g.cube(0f, top - 1.3f, z + 0.05f, 0.3f, 2.6f, 0.06f, esc)
                for (i in 0..2) g.cube(0f, top - 1.0f - i * 0.9f, z + 0.1f, 0.9f, 0.1f, 0.05f, ouro(t))
                g.cube(0f, d.cy - d.hy + 1.0f, z, w, 0.35f, 0.07f, esc)
                g.cube(0f, top - 0.2f, 0f, d.hx - 0.4f, 0.3f, d.hz, esc)
            }
            2 -> { // couro: costuras, tiras e rebites
                g.cube(0f, d.cy, z, 0.25f, d.hy - 0.5f, 0.06f, esc)
                g.cube(-w * 0.55f, d.cy + 0.5f, z, 0.12f, d.hy - 1.0f, 0.05f, luz)
                g.cube(w * 0.55f, d.cy + 0.5f, z, 0.12f, d.hy - 1.0f, 0.05f, luz)
                for (i in 0..2) {
                    g.cube(0f, d.cy - 2.5f + i * 2.2f, z + 0.05f, w * 0.8f, 0.3f, 0.06f, esc)
                    g.cube(w * 0.8f, d.cy - 2.5f + i * 2.2f, z + 0.1f, 0.22f, 0.22f, 0.05f, ouro(t))
                    g.cube(-w * 0.8f, d.cy - 2.5f + i * 2.2f, z + 0.1f, 0.22f, 0.22f, 0.05f, ouro(t))
                }
                g.cube(0f, top - 0.3f, 0f, d.hx - 0.3f, 0.35f, d.hz + 0.15f, esc)
            }
            3 -> { // malha: fileiras de anéis e gola
                var y = d.cy - d.hy + 0.8f
                var i = 0
                while (y < top - 0.4f) {
                    g.cube(0f, y, z, w, 0.14f, 0.06f, if (i % 2 == 0) esc else luz)
                    y += 0.9f
                    i++
                }
                g.cube(0f, top - 0.3f, 0f, d.hx - 0.3f, 0.4f, d.hz + 0.15f, esc)
                g.cube(0f, d.cy - d.hy + 0.4f, z, w + 0.1f, 0.4f, 0.08f, ouroD(t))
            }
            4 -> { // placas: peitoral polido com friso
                g.cube(0f, d.cy + 0.5f, z, w, d.hy - 1.0f, 0.08f, luz)
                g.cube(0f, d.cy + 0.5f, z + 0.08f, 0.25f, d.hy - 1.0f, 0.06f, esc)
                g.cube(0f, d.cy - d.hy + 1.0f, z + 0.05f, w, 0.3f, 0.08f, ouro(t))
                g.cube(0f, top - 0.9f, z + 0.05f, w, 0.25f, 0.08f, ouro(t))
                g.cube(-w + 0.2f, d.cy + 0.5f, z + 0.05f, 0.2f, d.hy - 1.0f, 0.08f, ouroD(t))
                g.cube(w - 0.2f, d.cy + 0.5f, z + 0.05f, 0.2f, d.hy - 1.0f, 0.08f, ouroD(t))
                g.cube(0f, d.cy + 1.2f, z + 0.14f, 0.7f, 0.7f, 0.06f, ouro(t), true)
                g.cube(0f, top - 0.2f, 0f, d.hx - 0.3f, 0.35f, d.hz + 0.15f, esc)
            }
            5 -> { // gala: bordas e colar, emblema
                g.cube(-w + 0.3f, d.cy, z, 0.5f, d.hy - 0.3f, 0.06f, ouro(t))
                g.cube(w - 0.3f, d.cy, z, 0.5f, d.hy - 0.3f, 0.06f, ouro(t))
                g.cube(0f, top - 0.6f, z, w, 0.6f, 0.08f, ouro(t))
                g.cube(0f, top - 2.0f, z + 0.05f, 1.3f, 0.3f, 0.07f, ouroD(t))
                g.cube(0f, d.cy + 0.2f, z + 0.05f, 1.2f, 1.2f, 0.07f, ouro(t))
                g.cube(0f, d.cy + 0.2f, z + 0.12f, 0.6f, 0.6f, 0.06f, esc)
                g.cube(0f, d.cy - d.hy + 0.8f, z, w, 0.4f, 0.07f, ouro(t))
            }
            6 -> { // peles: gola felpuda e barra clara
                g.cube(0f, top - 0.2f, 0f, d.hx + 0.3f, 0.7f, d.hz + 0.35f, luz)
                for (i in -2..2) g.cube(i * (w / 2.6f), top - 0.6f, d.hz + 0.35f, 0.7f, 0.9f, 0.3f, if (i % 2 == 0) luz else esc)
                g.cube(0f, d.cy - d.hy + 0.6f, z, w + 0.2f, 0.6f, 0.12f, luz)
                g.cube(0f, d.cy, z, 0.18f, d.hy - 1.2f, 0.06f, esc)
                for (i in 0..2) g.cube(0f, d.cy - 1.5f + i * 1.8f, z + 0.05f, 0.4f, 0.2f, 0.06f, ouroD(t))
            }
        }
    }

    // ───────────── capa ─────────────

    /** Capa longa nas costas (também no espaço do peito). Parada, só acompanha o giro do corpo. */
    fun capa(g: HeroGfx, c: Classe) {
        val k = HeroLook.capa
        if (k <= 0) return
        val cp = capas(c)[(k - 1).coerceIn(0, 4)]
        val d = dim(c)
        val cor = HeroLook.tecido(cp.cor)
        val corD = HeroLook.tecido(cp.corD)
        val trim = if (cp.pele) HeroLook.tecido(0xFFE9E0CC.toInt()) else HeroLook.metal(0xFF9A7420.toInt())
        val zb = -(d.hz + 1.5f)
        val top = d.cy + d.hy - 0.6f
        val w = d.hx * 0.92f
        g.push()
        g.translate(0f, top, zb)
        g.rotX(0.1f)
        g.cube(0f, -3.4f, 0f, w, 3.5f, 0.3f, cor)
        g.cube(0f, -3.4f, -0.3f, w - 0.4f, 3.3f, 0.12f, corD)
        g.translate(0f, -6.8f, 0f)
        g.rotX(0.08f)
        g.cube(0f, -3.0f, 0f, w + 0.2f, 3.1f, 0.3f, cor)
        g.cube(0f, -3.0f, -0.3f, w - 0.2f, 2.9f, 0.12f, corD)
        g.cube(0f, -6.0f, -0.05f, w + 0.2f, 0.3f, 0.4f, trim)
        g.pop()
        g.cube(0f, top + 0.4f, zb + 1.2f, w, 0.45f, 1.5f, if (cp.pele) trim else corD)
        g.cube(-w + 0.3f, top + 0.1f, 0f, 0.4f, 0.4f, d.hz + 0.2f, if (cp.pele) trim else corD)
        g.cube(w - 0.3f, top + 0.1f, 0f, 0.4f, 0.4f, d.hz + 0.2f, if (cp.pele) trim else corD)
        g.cube(0f, top + 0.1f, d.zF + 0.1f, 0.9f, 0.4f, 0.12f, HeroLook.metal(0xFFD9A93A.toInt()))
    }

    // ───────────── pernas ─────────────

    /** Chamado no espaço da coxa (depois de girar o quadril). */
    fun coxa(g: HeroGfx, c: Classe, side: Float) {
        val k = HeroLook.pernas
        if (k <= 0) return
        val l = lim(c)
        val t = traje(c, k)
        val base = cor(t, t.base)
        val esc = cor(t, t.esc)
        val luz = cor(t, t.luz)
        g.cube(0f, -2.7f, 0f, l.th + 0.12f, 2.95f, l.tz + 0.12f, base)
        val dz = (l.tzF - l.tz) / 2f
        if (dz > 0.05f) g.cube(0f, -2.7f, l.tz + dz, l.th - 0.2f, 2.7f, dz, base)
        val z = l.tzF + 0.05f
        when (t.estilo) {
            1 -> {
                g.cube(0f, -0.5f, z, l.th, 0.3f, 0.06f, esc)
                g.cube(0f, -3.5f, z, l.th - 0.4f, 0.1f, 0.05f, ouro(t))
            }
            2 -> {
                g.cube(side * (l.th - 0.5f), -2.7f, z, 0.1f, 2.6f, 0.05f, esc)
                g.cube(0f, -1.4f, z, l.th, 0.28f, 0.06f, esc)
                g.cube(0f, -4.0f, z, l.th, 0.28f, 0.06f, esc)
                g.cube(0f, -1.4f, z + 0.06f, 0.3f, 0.3f, 0.05f, ouro(t))
            }
            3 -> for (i in 0..4) g.cube(0f, -0.8f - i * 1.0f, z, l.th - 0.2f, 0.12f, 0.06f, if (i % 2 == 0) esc else luz)
            4 -> {
                g.cube(0f, -2.0f, z, l.th - 0.3f, 1.6f, 0.08f, luz)
                g.cube(0f, -4.3f, z, l.th - 0.3f, 0.6f, 0.08f, base)
                g.cube(0f, -0.4f, z, l.th, 0.3f, 0.09f, ouro(t))
                g.cube(0f, -3.6f, z + 0.05f, l.th, 0.2f, 0.08f, ouro(t))
            }
            5 -> {
                g.cube(0f, -0.5f, z, l.th, 0.45f, 0.07f, ouro(t))
                g.cube(0f, -2.7f, z, 0.2f, 2.2f, 0.05f, ouroD(t))
                g.cube(0f, -4.9f, z, l.th, 0.3f, 0.07f, ouro(t))
            }
            6 -> {
                g.cube(0f, -0.4f, 0f, l.th + 0.3f, 0.6f, l.tz + 0.3f, luz)
                g.cube(0f, -5.2f, 0f, l.th + 0.3f, 0.4f, l.tz + 0.3f, luz)
                g.cube(0f, -2.7f, z, 0.15f, 2.2f, 0.05f, esc)
            }
        }
    }

    /** Chamado no espaço da canela (depois de dobrar o joelho). */
    fun canela(g: HeroGfx, c: Classe, side: Float) {
        val k = HeroLook.pernas
        if (k <= 0) return
        val l = lim(c)
        val t = traje(c, k)
        val base = cor(t, t.base)
        val esc = cor(t, t.esc)
        val luz = cor(t, t.luz)
        g.cube(0f, l.sy, 0f, l.sh + 0.12f, l.shy + 0.03f, l.sz + 0.12f, base)
        val dz = (l.szF - l.sz) / 2f
        if (dz > 0.05f) g.cube(0f, l.sy, l.sz + dz, l.sh - 0.2f, l.shy - 0.2f, dz, base)
        val z = l.szF + 0.05f
        val top = l.sy + l.shy
        when (t.estilo) {
            1 -> {
                g.cube(0f, top - 4.2f, z, l.sh, 0.3f, 0.06f, esc)
                g.cube(0f, top - 5.3f, z, l.sh, 0.3f, 0.06f, esc)
            }
            2 -> {
                g.cube(0f, top - 1.4f, z, l.sh, 0.28f, 0.06f, esc)
                g.cube(0f, top - 3.6f, z, l.sh, 0.28f, 0.06f, esc)
                g.cube(0f, top - 2.5f, z, 0.1f, 1.0f, 0.05f, luz)
            }
            3 -> for (i in 0..4) g.cube(0f, top - 0.8f - i * 1.0f, z, l.sh - 0.2f, 0.12f, 0.06f, if (i % 2 == 0) esc else luz)
            4 -> {
                g.cube(0f, top - 2.6f, z, l.sh - 0.25f, 2.3f, 0.08f, luz)
                g.cube(0f, top - 2.6f, z + 0.08f, 0.2f, 2.3f, 0.06f, esc)
                g.cube(0f, top - 0.4f, z, l.sh, 0.3f, 0.09f, ouro(t))
                g.cube(0f, top - 4.9f, z, l.sh, 0.3f, 0.09f, ouro(t))
            }
            5 -> {
                g.cube(0f, top - 0.5f, z, l.sh, 0.4f, 0.07f, ouro(t))
                g.cube(0f, top - 2.8f, z, 0.2f, 1.8f, 0.05f, ouroD(t))
                g.cube(0f, top - 5.0f, z, l.sh, 0.3f, 0.07f, ouro(t))
            }
            6 -> {
                g.cube(0f, top - 0.6f, 0f, l.sh + 0.3f, 0.8f, l.sz + 0.3f, luz)
                g.cube(0f, top - 5.0f, 0f, l.sh + 0.3f, 0.5f, l.sz + 0.3f, luz)
                g.cube(0f, top - 2.8f, z, 0.15f, 1.6f, 0.05f, esc)
            }
        }
    }
}
