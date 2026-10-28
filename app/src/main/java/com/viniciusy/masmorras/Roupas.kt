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

    /** Capa: [forma] 1 longa, 2 curta (mantelete), 3 com capuz, 4 rasgada, 5 meia capa (um ombro), 6 de peles. */
    class Capa(val nome: String, val forma: Int, val cor: Int, val corD: Int, val pele: Boolean)

    private fun t(n: String, e: Int, b: Long, l: Long, d: Long, o: Long, m: Boolean) =
        Traje(n, e, b.toInt(), l.toInt(), d.toInt(), o.toInt(), m)

    private fun cp(n: String, f: Int, c: Long, d: Long, p: Boolean = false) = Capa(n, f, c.toInt(), d.toInt(), p)

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
        cp("Capa real azul (longa)", 1, 0xFF24459E, 0xFF182D68),
        cp("Mantelete vermelho (curto)", 2, 0xFFA3202E, 0xFF6E1520),
        cp("Capuz preto (com capuz)", 3, 0xFF22222A, 0xFF111116),
        cp("Pele de lobo (peles)", 6, 0xFF6B6B70, 0xFF3E3E42, true),
        cp("Meia capa branca (um ombro)", 5, 0xFFE6E6EC, 0xFFB0B0BC)
    )
    private val CAPAS_B = listOf(
        cp("Pele de urso (peles)", 6, 0xFF4A3A2A, 0xFF2E2217, true),
        cp("Manto rasgado (rasgada)", 4, 0xFF5A4630, 0xFF3A2C1C),
        cp("Meia pele de lobo (um ombro)", 5, 0xFF6B6B70, 0xFF3E3E42, true),
        cp("Mantelete de couro (curto)", 2, 0xFF6B4423, 0xFF3B2412),
        cp("Manto vermelho (longa)", 1, 0xFF8A1F1F, 0xFF541010)
    )
    private val CAPAS_M = listOf(
        cp("Manto astral (longa)", 1, 0xFF2A2F7A, 0xFF181C50),
        cp("Capa roxa (com capuz)", 3, 0xFF5A2A9E, 0xFF3C1B6E),
        cp("Meia capa carmesim (um ombro)", 5, 0xFF9A2A1B, 0xFF661B10),
        cp("Mantelete da floresta (curto)", 2, 0xFF2E6B34, 0xFF1D4522),
        cp("Manto branco rasgado (rasgada)", 4, 0xFFEDEAE0, 0xFFBDB8A8)
    )
    private val CAPAS_A = listOf(
        cp("Capa élfica verde (com capuz)", 3, 0xFF2E6B34, 0xFF1D4522),
        cp("Mantelete cinza (curto)", 2, 0xFF6B7078, 0xFF444850),
        cp("Manto de camuflagem (rasgada)", 4, 0xFF4F5A32, 0xFF343C20),
        cp("Meia capa marrom (um ombro)", 5, 0xFF6B4423, 0xFF3B2412),
        cp("Pele de raposa (peles)", 6, 0xFFB8652A, 0xFF7E4219, true)
    )
    private val CAPAS_N = listOf(
        cp("Capa da noite (rasgada)", 4, 0xFF15151C, 0xFF0A0A0F),
        cp("Mantelete carmesim (curto)", 2, 0xFF8E1A22, 0xFF5E0F15),
        cp("Capuz da neve (com capuz)", 3, 0xFFE6ECF0, 0xFFA8B2BC),
        cp("Meia capa roxa (um ombro)", 5, 0xFF4A2A7A, 0xFF2E1A50),
        cp("Capa dourada (longa)", 1, 0xFFB8902A, 0xFF7E6018)
    )

    // ───────────── ombreiras ─────────────
    // Formas: 1 placas, 2 espinhos, 3 peles, 4 couro, 5 gala (tecido e franjas), 6 ossos/chifres, 7 cristal, 8 folhas.
    private val OMB_G = listOf(
        t("Placas reais", 1, 0xFFB8C2CC, 0xFFE2E8EE, 0xFF7D8791, 0xFFD9A93A, true),
        t("Espinhos de guerra", 2, 0xFF5F6872, 0xFFB4BEC8, 0xFF3C434B, 0xFFD9A93A, true),
        t("Pele de lobo", 3, 0xFF6B6B70, 0xFFE9E0CC, 0xFF3E3E42, 0xFF9A7420, false),
        t("Couro de gladiador", 4, 0xFF7A4A28, 0xFF9A6236, 0xFF4A2C14, 0xFFC9A04A, false),
        t("Gala do capitão", 5, 0xFF1E3F8F, 0xFF3A5FBF, 0xFF132A63, 0xFFE0B040, false)
    )
    private val OMB_B = listOf(
        t("Pele de urso", 3, 0xFF4A3A2A, 0xFFC9B79A, 0xFF2E2217, 0xFF8A6A30, false),
        t("Chifres de touro", 6, 0xFF6B4423, 0xFFE8DCC0, 0xFF3B2412, 0xFF9A7420, false),
        t("Espinhos de ferro", 2, 0xFF5F6872, 0xFF8892A0, 0xFF3C434B, 0xFF9A7420, true),
        t("Couro tribal", 4, 0xFF8A5B33, 0xFFA97545, 0xFF55341A, 0xFFE8DCC0, false),
        t("Placas de ossos", 1, 0xFFE8DCC0, 0xFFF3EBD2, 0xFFB8A98A, 0xFF6B4423, false)
    )
    private val OMB_M = listOf(
        t("Cristais arcanos", 7, 0xFF3C1B6E, 0xFF9A7BFF, 0xFF2A1450, 0xFFE0B040, false),
        t("Gala do arquimago", 5, 0xFF5A2A9E, 0xFF7A4CC4, 0xFF3C1B6E, 0xFFE0B040, false),
        t("Placas rúnicas", 1, 0xFFC8B070, 0xFFEBDDA0, 0xFF8A7A40, 0xFFE0B040, true),
        t("Pele de druida", 3, 0xFF3E6B3A, 0xFFD8E2C0, 0xFF284A25, 0xFFB89A50, false),
        t("Couro de feiticeiro", 4, 0xFF2A2A3A, 0xFF3E3E55, 0xFF16161F, 0xFF8A5BFF, false)
    )
    private val OMB_A = listOf(
        t("Couro de caçador", 4, 0xFF4F6B3A, 0xFF6C8A52, 0xFF33472A, 0xFFB8963A, false),
        t("Folhas élficas", 8, 0xFF3E7A3A, 0xFF7CB85A, 0xFF2A5428, 0xFFD8C060, false),
        t("Placas élficas", 1, 0xFFA9B8A0, 0xFFD2DEC8, 0xFF76866E, 0xFFC8D6E0, true),
        t("Pele de raposa", 3, 0xFFB8652A, 0xFFF0E4D0, 0xFF7E4219, 0xFF8A6A30, false),
        t("Gala de seda", 5, 0xFFD7E6C8, 0xFFEEF6E4, 0xFF8FA886, 0xFFC8D6E0, false)
    )
    private val OMB_N = listOf(
        t("Placas shinobi", 1, 0xFF3A3F4A, 0xFF5A6070, 0xFF22252D, 0xFFC9302C, true),
        t("Espinhos de aço", 2, 0xFF3A3F4A, 0xFF9AA4B0, 0xFF22252D, 0xFFC9302C, true),
        t("Couro de assassino", 4, 0xFF1B1A24, 0xFF2C2A3A, 0xFF0F0E15, 0xFFB5242E, false),
        t("Seda carmesim", 5, 0xFF8E1A22, 0xFFB52C34, 0xFF5E0F15, 0xFFE0B040, false),
        t("Chifres de oni", 6, 0xFF2B2B33, 0xFFE8DCC0, 0xFF16161C, 0xFFC9302C, false)
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
    fun ombros(c: Classe): List<Traje> = when (c) {
        Classe.GUERREIRO -> OMB_G
        Classe.BARBARO -> OMB_B
        Classe.MAGO -> OMB_M
        Classe.ARQUEIRO -> OMB_A
        Classe.NINJA -> OMB_N
    }

    /** 0 = original da classe, 1 = sem ombreiras, 2..6 = os 5 estilos da classe. */
    fun nomesOmbro(c: Classe): List<String> = listOf("Original", "Sem ombreiras") + ombros(c).map { it.nome }
    const val NO = 7

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

    /** Capa nas costas (também no espaço do peito), com 6 formas diferentes. Parada, só acompanha o giro do corpo. */
    fun capa(g: HeroGfx, c: Classe) {
        val k = HeroLook.capa
        if (k <= 0) return
        val cp = capas(c)[(k - 1).coerceIn(0, 4)]
        val d = dim(c)
        val cor = HeroLook.tecido(cp.cor)
        val corD = HeroLook.tecido(cp.corD)
        val trim = if (cp.pele) HeroLook.tecido(0xFFE9E0CC.toInt()) else HeroLook.metal(0xFF9A7420.toInt())
        val ouroC = HeroLook.metal(0xFFD9A93A.toInt())
        val zb = -(d.hz + 1.5f)
        val top = d.cy + d.hy - 0.6f
        val w = d.hx * 0.92f
        when (cp.forma) {
            2 -> capaCurta(g, d, w, top, zb, cor, corD, trim)
            3 -> capaCapuz(g, d, w, top, zb, cor, corD, trim)
            4 -> capaRasgada(g, d, w, top, zb, cor, corD, trim)
            5 -> capaMeia(g, d, w, top, zb, cor, corD, trim, ouroC)
            6 -> capaPeles(g, d, w, top, zb, cor, corD, trim)
            else -> capaLonga(g, d, w, top, zb, cor, corD, trim, cp.pele)
        }
        if (cp.forma != 5 && cp.forma != 6) {
            g.cube(0f, top + 0.1f, d.zF + 0.1f, 0.9f, 0.4f, 0.12f, ouroC)
        }
    }

    private fun capaLonga(g: HeroGfx, d: D, w: Float, top: Float, zb: Float, cor: Int, corD: Int, trim: Int, pele: Boolean) {
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
        g.cube(0f, top + 0.4f, zb + 1.2f, w, 0.45f, 1.5f, if (pele) trim else corD)
        g.cube(-w + 0.3f, top + 0.1f, 0f, 0.4f, 0.4f, d.hz + 0.2f, if (pele) trim else corD)
        g.cube(w - 0.3f, top + 0.1f, 0f, 0.4f, 0.4f, d.hz + 0.2f, if (pele) trim else corD)
    }

    /** Mantelete: cobre os ombros e cai só até a cintura. */
    private fun capaCurta(g: HeroGfx, d: D, w: Float, top: Float, zb: Float, cor: Int, corD: Int, trim: Int) {
        g.push()
        g.translate(0f, top, zb + 0.2f)
        g.rotX(0.12f)
        g.cube(0f, -2.6f, 0f, w + 0.2f, 2.7f, 0.3f, cor)
        g.cube(0f, -2.6f, -0.3f, w - 0.2f, 2.5f, 0.12f, corD)
        g.cube(0f, -5.4f, -0.05f, w + 0.2f, 0.25f, 0.4f, trim)
        g.pop()
        g.cube(0f, d.cy + d.hy + 0.1f, 0f, d.hx + 0.5f, 0.45f, d.hz + 0.5f, cor)
        g.cube(0f, d.cy + d.hy - 0.4f, 0f, d.hx + 0.65f, 0.3f, d.hz + 0.65f, trim)
        g.cube(0f, top + 0.4f, d.zF + 0.15f, d.hx * 0.7f, 0.6f, 0.15f, cor)
    }

    /** Capa longa e estreita com capuz caído atrás da cabeça. */
    private fun capaCapuz(g: HeroGfx, d: D, w: Float, top: Float, zb: Float, cor: Int, corD: Int, trim: Int) {
        g.push()
        g.translate(0f, top, zb)
        g.rotX(0.1f)
        g.cube(0f, -3.4f, 0f, w - 0.4f, 3.5f, 0.3f, cor)
        g.cube(0f, -3.4f, -0.3f, w - 0.8f, 3.3f, 0.12f, corD)
        g.translate(0f, -6.8f, 0f)
        g.rotX(0.08f)
        g.cube(0f, -2.6f, 0f, w - 0.2f, 2.7f, 0.3f, cor)
        g.cube(0f, -2.6f, -0.3f, w - 0.6f, 2.5f, 0.12f, corD)
        g.cube(0f, -5.2f, -0.05f, w - 0.2f, 0.25f, 0.4f, trim)
        g.pop()
        g.cube(0f, top + 1.0f, zb + 0.5f, 2.8f, 1.7f, 1.2f, cor)
        g.cube(0f, top + 0.2f, zb + 1.3f, 3.4f, 0.6f, 1.4f, corD)
        g.cube(0f, top + 2.7f, zb + 0.3f, 2.2f, 0.9f, 0.9f, cor)
        g.cube(0f, top + 0.4f, zb + 1.2f, w - 0.6f, 0.45f, 1.5f, corD)
    }

    /** Manto esfarrapado: barra em tiras de comprimentos diferentes. */
    private fun capaRasgada(g: HeroGfx, d: D, w: Float, top: Float, zb: Float, cor: Int, corD: Int, trim: Int) {
        g.push()
        g.translate(0f, top, zb)
        g.rotX(0.1f)
        g.cube(0f, -2.8f, 0f, w, 3.0f, 0.3f, cor)
        g.cube(0f, -2.8f, -0.3f, w - 0.4f, 2.8f, 0.12f, corD)
        g.translate(0f, -5.8f, 0f)
        g.rotX(0.08f)
        val meio = floatArrayOf(1.6f, 3.2f, 2.2f, 3.8f, 1.9f)
        for (i in 0..4) {
            val x = (i - 2) * (w * 0.4f)
            val l = meio[i]
            g.cube(x, -l, 0f, w * 0.2f, l, 0.28f, if (i % 2 == 0) cor else corD)
            g.cube(x, -l * 2f + 0.2f, -0.05f, w * 0.2f, 0.2f, 0.34f, trim)
        }
        g.pop()
        g.cube(0f, top + 0.4f, zb + 1.2f, w, 0.45f, 1.5f, corD)
        g.cube(-w + 0.3f, top + 0.1f, 0f, 0.4f, 0.4f, d.hz + 0.2f, corD)
        g.cube(w - 0.3f, top + 0.1f, 0f, 0.4f, 0.4f, d.hz + 0.2f, corD)
    }

    /** Meia capa: cobre só o ombro esquerdo e cai pelas costas, com tira em diagonal no peito. */
    private fun capaMeia(g: HeroGfx, d: D, w: Float, top: Float, zb: Float, cor: Int, corD: Int, trim: Int, ouroC: Int) {
        val sx = w * 0.55f
        g.cube(sx * 1.05f, d.cy + d.hy + 0.1f, 0f, w * 0.52f, 0.45f, d.hz + 0.45f, cor)
        g.cube(sx * 1.05f, d.cy + d.hy - 0.4f, 0f, w * 0.52f, 0.3f, d.hz + 0.6f, trim)
        g.push()
        g.translate(sx, top, zb + 0.3f)
        g.rotX(0.08f)
        g.cube(0f, -4.2f, 0f, w * 0.5f, 4.3f, 0.3f, cor)
        g.cube(0f, -4.2f, -0.3f, w * 0.5f - 0.3f, 4.1f, 0.12f, corD)
        g.translate(0f, -8.4f, 0f)
        g.rotX(0.06f)
        g.cube(0f, -1.6f, 0f, w * 0.5f, 1.7f, 0.3f, cor)
        g.cube(0f, -3.4f, -0.05f, w * 0.5f, 0.2f, 0.36f, trim)
        g.pop()
        g.push()
        g.translate(0f, d.cy + 0.3f, d.zF + 0.2f)
        g.rotZ(-0.55f)
        g.cube(0f, 0f, 0f, 0.4f, d.hy + 1.2f, 0.07f, corD)
        g.pop()
        g.cube(sx * 1.0f, d.cy + d.hy - 0.7f, d.zF + 0.2f, 0.55f, 0.55f, 0.1f, ouroC)
    }

    /** Gola grossa e saia curta de pelo, com tufos na barra. */
    private fun capaPeles(g: HeroGfx, d: D, w: Float, top: Float, zb: Float, cor: Int, corD: Int, trim: Int) {
        g.cube(0f, top + 0.6f, 0f, d.hx + 0.9f, 0.9f, d.hz + 0.9f, trim)
        for (i in -3..3) g.cube(i * (w / 3.4f), top + 0.2f, d.hz + 0.9f, 0.7f, 0.8f, 0.35f, if (i % 2 == 0) trim else cor)
        g.push()
        g.translate(0f, top, zb + 0.2f)
        g.rotX(0.1f)
        g.cube(0f, -3.4f, 0f, w + 0.3f, 3.5f, 0.4f, cor)
        g.cube(0f, -3.4f, -0.35f, w, 3.3f, 0.15f, corD)
        for (i in -3..3) g.cube(i * (w / 3.2f), -7.2f, 0f, 0.8f, 0.7f, 0.35f, if (i % 2 == 0) trim else cor)
        g.pop()
    }

    // ───────────── ombreiras ─────────────

    private fun tamOmbro(c: Classe): Float = when (c) {
        Classe.GUERREIRO -> 1.0f
        Classe.BARBARO -> 1.12f
        Classe.MAGO -> 0.88f
        Classe.ARQUEIRO -> 0.82f
        Classe.NINJA -> 0.88f
    }

    /**
     * Ombreira escolhida na criação. Chamado no espaço do ombro (antes de girar o braço), side = -1 direita, +1 esquerda.
     * Devolve true quando desenhou uma ombreira nova; false = o modelo desenha a original (ou nada, se tirou).
     */
    fun ombreira(g: HeroGfx, c: Classe, side: Float): Boolean {
        val k = HeroLook.ombroK
        if (k < 2) return false
        val o = ombros(c)[(k - 2).coerceIn(0, 4)]
        val base = cor(o, o.base)
        val luz = cor(o, o.luz)
        val esc = cor(o, o.esc)
        val ouroC = ouro(o)
        val ouroE = ouroD(o)
        val s = side
        val z = tamOmbro(c)
        g.push()
        g.scale(z, z, z)
        when (o.estilo) {
            1 -> {
                g.cube(s * 0.8f, 1.5f, 0f, 3.1f, 1.0f, 3.2f, luz)
                g.cube(s * 1.5f, 0.1f, 0f, 2.8f, 0.9f, 3.0f, base)
                g.cube(s * 2.0f, -1.0f, 0f, 2.4f, 0.8f, 2.7f, esc)
                g.cube(s * 3.3f, -1.8f, 0f, 2.0f, 0.6f, 2.5f, esc)
                g.cube(s * 0.8f, 2.6f, 0f, 3.15f, 0.22f, 3.25f, ouroC)
                g.cube(s * 3.4f, 0.1f, 0f, 0.28f, 2.0f, 3.1f, ouroE)
            }
            2 -> {
                g.cube(s * 1.2f, 1.1f, 0f, 3.0f, 1.0f, 3.0f, base)
                g.cube(s * 2.6f, -0.2f, 0f, 2.4f, 0.9f, 2.8f, esc)
                g.cube(s * 1.2f, 2.2f, 0f, 3.05f, 0.22f, 3.05f, ouroC)
                g.cube(s * 1.4f, 3.4f, 0f, 0.55f, 1.0f, 0.55f, luz)
                g.cube(s * 1.4f, 4.6f, 0f, 0.3f, 0.6f, 0.3f, luz)
                g.cube(s * 3.4f, 1.2f, 2.6f, 0.4f, 0.4f, 0.9f, luz)
                g.cube(s * 3.4f, 1.2f, -2.6f, 0.4f, 0.4f, 0.9f, luz)
                g.cube(s * 4.2f, 0.2f, 0f, 0.5f, 0.5f, 0.5f, luz)
            }
            3 -> {
                g.cube(s * 0.9f, 1.9f, 0f, 3.0f, 1.0f, 3.3f, luz)
                g.cube(s * 2.4f, 0.4f, 0f, 2.9f, 0.9f, 3.4f, base)
                g.cube(s * 3.5f, -0.7f, 0f, 2.2f, 0.7f, 3.2f, luz)
                g.cube(s * 3.6f, -1.6f, 2.5f, 0.9f, 0.5f, 0.9f, esc)
                g.cube(s * 3.6f, -1.6f, -2.5f, 0.9f, 0.5f, 0.9f, esc)
                g.cube(s * 0.4f, 3.0f, 0.8f, 1.2f, 0.5f, 1.3f, luz)
                g.cube(s * 2.2f, 2.8f, -1.0f, 1.1f, 0.45f, 1.1f, esc)
            }
            4 -> {
                g.cube(s * 1.0f, 1.2f, 0f, 3.0f, 0.8f, 3.0f, base)
                g.cube(s * 2.7f, 0.1f, 0f, 2.3f, 0.8f, 2.8f, luz)
                g.cube(s * 1.0f, 2.1f, 0f, 3.05f, 0.2f, 3.05f, esc)
                g.cube(s * 2.7f, -0.9f, 0f, 2.35f, 0.2f, 2.85f, esc)
                g.cube(s * 1.0f, 2.4f, 1.8f, 0.28f, 0.28f, 0.2f, ouroC)
                g.cube(s * 1.0f, 2.4f, -1.8f, 0.28f, 0.28f, 0.2f, ouroC)
                g.cube(s * 3.0f, 0.9f, 2.9f, 0.28f, 0.28f, 0.2f, ouroC)
                g.cube(s * 3.0f, 0.9f, -2.9f, 0.28f, 0.28f, 0.2f, ouroC)
            }
            5 -> {
                g.cube(s * 1.0f, 1.3f, 0f, 3.0f, 0.8f, 3.0f, base)
                g.cube(s * 2.6f, 0.2f, 0f, 2.5f, 0.9f, 3.0f, luz)
                g.cube(s * 2.9f, -0.9f, 0f, 2.7f, 0.25f, 3.1f, ouroC)
                g.cube(s * 1.0f, 2.2f, 0f, 3.05f, 0.2f, 3.05f, ouroC)
                for (i in -2..2) g.cube(s * 3.0f, -1.7f, i * 1.2f, 0.3f, 0.6f, 0.3f, ouroC)
            }
            6 -> {
                g.cube(s * 1.0f, 1.2f, 0f, 3.0f, 0.9f, 3.0f, base)
                g.cube(s * 2.6f, 0.1f, 0f, 2.4f, 0.8f, 2.8f, esc)
                g.push()
                g.translate(s * 1.8f, 1.9f, 0f)
                g.rotZ(-s * 0.45f)
                g.cube(0f, 1.6f, 0f, 0.7f, 1.7f, 0.7f, luz)
                g.cube(0f, 3.5f, 0f, 0.4f, 0.8f, 0.4f, luz)
                g.pop()
                g.cube(s * 3.6f, 1.0f, 2.0f, 0.4f, 0.4f, 0.9f, luz)
                g.cube(s * 3.6f, 1.0f, -2.0f, 0.4f, 0.4f, 0.9f, luz)
                g.cube(s * 0.9f, 2.1f, 0f, 3.05f, 0.2f, 3.05f, ouroC)
            }
            7 -> {
                g.cube(s * 1.0f, 1.2f, 0f, 2.8f, 0.9f, 2.9f, base)
                g.cube(s * 2.6f, 0.1f, 0f, 2.2f, 0.8f, 2.6f, esc)
                g.cube(s * 1.0f, 2.1f, 0f, 2.85f, 0.2f, 2.95f, ouroC)
                g.cube(s * 2.0f, 3.6f, 0f, 0.7f, 1.3f, 0.7f, luz, true)
                g.cube(s * 2.0f, 3.6f, 0f, 0.35f, 1.6f, 0.35f, 0xFFFFFFFF.toInt(), true)
            }
            else -> {
                for (i in 0..2) {
                    g.push()
                    g.translate(s * (0.8f + i * 1.2f), 1.6f - i * 0.9f, 0f)
                    g.rotZ(-s * (0.15f + i * 0.3f))
                    g.cube(0f, 0f, 0f, 1.6f, 0.45f, 2.8f - i * 0.2f, if (i % 2 == 0) base else luz)
                    g.pop()
                }
                g.cube(s * 0.8f, 2.4f, 0f, 0.7f, 0.3f, 1.0f, ouroC)
            }
        }
        g.pop()
        return true
    }

    // ───────────── corpo (tipo de cada classe, masculino e feminino) ─────────────

    /**
     * Tipo de corpo: escalas aplicadas por região. ombros = largura do peito e distância dos ombros, peitoZ = profundidade do peito,
     * cintura/cinturaZ = parte de baixo do tronco, quadril = afastamento das pernas, coxa = grossura das pernas,
     * braco = grossura dos braços, cabeca = tamanho da cabeça.
     */
    private class Corpo(
        val nome: String, val ombros: Float, val peitoZ: Float, val cintura: Float, val cinturaZ: Float,
        val quadril: Float, val coxa: Float, val braco: Float, val cabeca: Float
    )

    private fun corpo(c: Classe): Corpo = if (HeroLook.fem) {
        when (c) {
            Classe.GUERREIRO -> Corpo("Guerreira atlética (ombros firmes, cintura marcada)", 0.94f, 1.0f, 0.86f, 0.92f, 1.07f, 1.05f, 0.95f, 0.96f)
            Classe.BARBARO -> Corpo("Amazona robusta (braços fortes, quadril largo)", 1.06f, 1.06f, 0.92f, 0.98f, 1.12f, 1.1f, 1.12f, 0.95f)
            Classe.MAGO -> Corpo("Feiticeira graciosa (esguia e delicada)", 0.82f, 0.88f, 0.78f, 0.84f, 1.02f, 0.9f, 0.76f, 0.98f)
            Classe.ARQUEIRO -> Corpo("Caçadora esguia (ágil, cintura fina)", 0.86f, 0.88f, 0.78f, 0.84f, 1.04f, 0.92f, 0.8f, 0.97f)
            Classe.NINJA -> Corpo("Assassina ágil (magra e flexível)", 0.84f, 0.86f, 0.76f, 0.82f, 1.02f, 0.9f, 0.78f, 0.97f)
        }
    } else {
        when (c) {
            Classe.GUERREIRO -> Corpo("Atlético imponente (peito largo, tronco em V)", 1.08f, 1.04f, 0.95f, 1.0f, 1.04f, 1.06f, 1.1f, 1.0f)
            Classe.BARBARO -> Corpo("Titã musculoso (tronco de barril, braços enormes)", 1.2f, 1.12f, 1.1f, 1.08f, 1.08f, 1.12f, 1.28f, 0.96f)
            Classe.MAGO -> Corpo("Esguio erudito (ombros estreitos, braços finos)", 0.9f, 0.9f, 0.9f, 0.9f, 0.94f, 0.88f, 0.82f, 1.04f)
            Classe.ARQUEIRO -> Corpo("Esbelto ágil (tronco longo e enxuto)", 0.95f, 0.92f, 0.88f, 0.9f, 0.96f, 0.92f, 0.88f, 1.0f)
            Classe.NINJA -> Corpo("Magro e flexível (tipo corredor)", 0.92f, 0.88f, 0.82f, 0.84f, 0.94f, 0.9f, 0.85f, 1.0f)
        }
    }

    /** Nome do tipo de corpo (mostrado na criação). */
    fun tipoCorpo(c: Classe, fem: Boolean): String {
        val antes = HeroLook.fem
        HeroLook.fem = fem
        val n = corpo(c).nome
        HeroLook.fem = antes
        return n
    }

    /** Fator da distância dos ombros (e do peito) no eixo X. */
    fun ombrosK(c: Classe): Float = corpo(c).ombros

    /** Fator do afastamento das pernas. */
    fun quad(c: Classe): Float = corpo(c).quadril

    /** Aplica no espaço do quadril, antes do tronco de baixo (cinto, barriga, saiote). Fechar com pop() antes do peito. */
    fun cintura(g: HeroGfx, c: Classe) {
        val b = corpo(c)
        g.scale(b.cintura, 1f, b.cinturaZ)
    }

    /** Aplica no espaço do peito, depois do giro e da respiração. */
    fun peitoEsc(g: HeroGfx, c: Classe) {
        val b = corpo(c)
        g.scale(b.ombros, 1f, b.peitoZ)
    }

    /** Aplica no espaço da coxa, depois de girar o quadril: engrossa a perna inteira. */
    fun perna(g: HeroGfx, c: Classe) {
        val b = corpo(c)
        g.scale(b.coxa, 1f, b.coxa)
    }

    /** Aplica antes de desenhar o braço de cima ou o antebraço (dentro de push/pop). */
    fun braco(g: HeroGfx, c: Classe) {
        val b = corpo(c)
        g.scale(b.braco, 1f, b.braco)
    }

    /** Aplica no espaço da cabeça, depois de girar. */
    fun cabecaEsc(g: HeroGfx, c: Classe) {
        val k = corpo(c).cabeca
        g.scale(k, k, k)
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
