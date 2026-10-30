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
        Classe.NINJA, Classe.PIKACHU, Classe.ICHIGO, Classe.LUFFY -> NINJA
    }

    fun capas(c: Classe): List<Capa> = when (c) {
        Classe.GUERREIRO -> CAPAS_G
        Classe.BARBARO -> CAPAS_B
        Classe.MAGO -> CAPAS_M
        Classe.ARQUEIRO -> CAPAS_A
        Classe.NINJA, Classe.PIKACHU, Classe.ICHIGO, Classe.LUFFY -> CAPAS_N
    }

    fun nomesPeito(c: Classe): List<String> = listOf("Original") + trajes(c).map { it.nome }
    fun nomesPernas(c: Classe): List<String> = listOf("Original") + trajes(c).map { it.nome }
    fun ombros(c: Classe): List<Traje> = when (c) {
        Classe.GUERREIRO -> OMB_G
        Classe.BARBARO -> OMB_B
        Classe.MAGO -> OMB_M
        Classe.ARQUEIRO -> OMB_A
        Classe.NINJA, Classe.PIKACHU, Classe.ICHIGO, Classe.LUFFY -> OMB_N
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
        Classe.NINJA, Classe.PIKACHU, Classe.ICHIGO, Classe.LUFFY -> D(9.6f, 5.4f, 4.8f, 3.4f, 4.05f, 3.8f, 4.8f, 2.2f, 3.2f, 3.45f)
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
        Classe.NINJA, Classe.PIKACHU, Classe.ICHIGO, Classe.LUFFY -> L(2.35f, 2.4f, 2.5f, 2.15f, 2.15f, 2.3f, -2.7f, 2.8f)
    }

    // ───────────── barriga / cintura ─────────────

    /** Chamado no espaço do corpo (quadril), logo no começo do torso. */
    fun abdomen(g: HeroGfx, c: Classe) {
        val d = dim(c)
        if (HeroLook.fem) quadris(g, c, d)
        val k = HeroLook.peito
        if (k <= 0) return
        if (d.ahx <= 0f) return
        val t = traje(c, k)
        val base = cor(t, t.base)
        g.cube(0f, d.ay, 0f, d.ahx + 0.12f, d.ahy + 0.05f, d.ahz + 0.12f, base)
        val dz = (d.azF - d.ahz) / 2f
        if (dz > 0.05f) g.cube(0f, d.ay, d.ahz + dz, d.ahx - 0.2f, d.ahy - 0.2f, dz, base)
        abdomenUnico(g, c, k, d, t)
    }

    // ───────────── peito ─────────────

    /** Cor do peito original de cada classe (para o busto combinar quando não há traje). */
    private fun peitoOriginal(c: Classe): Int = when (c) {
        Classe.GUERREIRO -> HeroLook.metal(0xFFAEB9C2.toInt())
        Classe.BARBARO -> HeroLook.skin(0xFFD99A5E.toInt())
        Classe.MAGO -> HeroLook.tecido(0xFF2E5CB8.toInt())
        Classe.ARQUEIRO -> HeroLook.tecido(0xFF66795A.toInt())
        Classe.NINJA, Classe.PIKACHU, Classe.ICHIGO, Classe.LUFFY -> HeroLook.tecido(0xFF1B1A24.toInt())
    }

    /** Chamado dentro do peito (já com giro e respiração), no começo da parte de cima do torso. */
    fun peito(g: HeroGfx, c: Classe) {
        val k = HeroLook.peito
        val d = dim(c)
        if (k > 0) vestirPeito(g, c, k, d, traje(c, k))
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
        val colD = mul(col, 0.74f)
        // 0 = pequeno, 1 = médio, 2 = grande: cada seio é uma cúpula de camadas que afinam para a frente
        val s = when (HeroLook.busto) { 0 -> 0.82f; 2 -> 1.32f; else -> 1.06f }
        val zb = if (k > 0) d.zF + 0.05f else d.zF - 0.3f
        val bx = d.hx * 0.34f
        val r = 1.75f * s
        val h = 1.6f * s
        val by = d.cy + 0.55f - (s - 1f) * 0.5f
        for (i in 0..1) {
            val sd = if (i == 0) 1f else -1f
            val x = sd * bx
            g.cube(x, by, zb + 0.55f * s, r, h, 0.6f * s, col)
            g.cube(x, by + 0.05f, zb + 1.05f * s, r * 0.86f, h * 0.88f, 0.4f * s, col)
            g.cube(x - sd * 0.1f * s, by + 0.1f, zb + 1.45f * s, r * 0.66f, h * 0.7f, 0.35f * s, colL)
            g.cube(x - sd * 0.15f * s, by + 0.12f, zb + 1.8f * s, r * 0.4f, h * 0.45f, 0.25f * s, colL)
            g.cube(x - sd * 0.2f * s, by + 0.05f, zb + 2.0f * s, r * 0.15f, h * 0.17f, 0.15f * s, mul(col, 0.62f))
            g.cube(x, by - h - 0.1f, zb + 0.9f * s, r * 0.9f, 0.16f, 0.5f * s, colD)
            g.cube(x + sd * (r + 0.1f), by, zb + 0.4f * s, 0.12f, h * 0.8f, 0.4f * s, colD)
        }
        g.cube(0f, by - 0.1f, zb + 0.5f * s, 0.2f * s, h * 0.9f, 0.45f * s, colD)
        // faixa sob o busto, para parecer um corpo e não duas peças coladas
        g.cube(0f, d.cy - 0.9f, d.zF - 0.1f, d.hx * 0.62f, 0.5f, 0.15f, mul(col, 0.9f))
    }

    private fun calcaOriginal(c: Classe): Int = when (c) {
        Classe.GUERREIRO -> HeroLook.tecido(0xFF4A3F36.toInt())
        Classe.BARBARO -> HeroLook.tecido(0xFF5A4630.toInt())
        Classe.MAGO -> HeroLook.tecido(0xFF2E3F7A.toInt())
        Classe.ARQUEIRO -> HeroLook.tecido(0xFF4A5A3A.toInt())
        Classe.NINJA, Classe.PIKACHU, Classe.ICHIGO, Classe.LUFFY -> HeroLook.tecido(0xFF1B1A24.toInt())
    }

    /** Quadril feminino: curva de cubos que alarga logo abaixo da cintura (espaço do quadril, depois do cintura()). */
    private fun quadris(g: HeroGfx, c: Classe, d: D) {
        val kp = HeroLook.pernas
        val col = if (kp > 0) cor(traje(c, kp), traje(c, kp).base) else calcaOriginal(c)
        val colD = mul(col, 0.82f)
        val ax = if (d.ahx > 0f) d.ahx else d.hx * 0.9f
        for (i in 0..1) {
            val sd = if (i == 0) 1f else -1f
            g.cube(sd * (ax * 0.55f), -0.5f, 0f, ax * 0.5f, 2.4f, d.ahz + 0.5f, col)
            g.cube(sd * (ax * 0.85f), -1.2f, 0f, ax * 0.22f, 1.8f, d.ahz + 0.7f, colD)
            g.cube(sd * (ax * 0.55f), -0.7f, d.ahz + 0.5f, ax * 0.46f, 1.9f, 0.4f, col)
            g.cube(sd * (ax * 0.55f), -0.7f, -(d.ahz + 0.5f), ax * 0.46f, 1.9f, 0.4f, colD)
        }
        g.cube(0f, 1.4f, 0f, ax * 0.55f, 0.8f, d.ahz + 0.15f, mul(col, 0.92f))
    }

    private fun vestirPeito(g: HeroGfx, c: Classe, k: Int, d: D, t: Traje) {
        val B = cor(t, t.base)
        val L = cor(t, t.luz)
        val E = cor(t, t.esc)
        val O = ouro(t)
        val OD = ouroD(t)
        val cy = d.cy
        val top = d.cy + d.hy
        val bot = d.cy - d.hy
        val w = d.hx - 0.3f
        val z = d.zF + 0.05f
        // tronco base
        g.cube(0f, cy, 0f, d.hx + 0.12f, d.hy + 0.05f, d.hz + 0.12f, B)
        val dz = (d.zF - d.hz) / 2f
        if (dz > 0.05f) g.cube(0f, cy, d.hz + dz, d.hx - 0.2f, d.hy - 0.2f, dz, B)
        // costas: um detalhe discreto para a roupa não ser lisa atrás
        g.cube(0f, cy + 0.4f, -(d.hz + 0.2f), d.hx - 0.6f, d.hy - 1.0f, 0.1f, mul(B, 0.86f))
        g.cube(0f, top - 0.3f, 0f, d.hx - 0.2f, 0.35f, d.hz + 0.15f, E)
        fun q(x: Float, y: Float, zz: Float, hx: Float, hy: Float, hz: Float, col: Int, em: Boolean = false) = g.cube(x, y, zz, hx, hy, hz, col, em)
        val sds = floatArrayOf(-1f, 1f)
        when (c) {
            Classe.GUERREIRO -> when (k) {
                1 -> { // Cavaleiro de aço: couraça de placas com quilha, faldas e gorjal
                    q(0f, cy + 0.4f, z + 0.05f, w * 0.64f, d.hy - 0.9f, 0.2f, L)
                    q(0f, cy + 0.4f, z + 0.25f, 0.28f, d.hy - 1.0f, 0.1f, E)
                    for (s in sds) {
                        q(s * w * 0.5f, cy + 1.5f, z + 0.1f, w * 0.3f, 1.5f, 0.16f, L)
                        q(s * w * 0.5f, cy + 1.5f, z + 0.28f, w * 0.26f, 0.12f, 0.05f, OD)
                        q(s * (d.hx + 0.04f), cy, 0f, 0.18f, d.hy - 0.5f, d.hz - 0.4f, E)
                        q(s * (w - 0.35f), cy + 2.7f, z + 0.14f, 0.2f, 0.2f, 0.06f, OD)
                        q(s * (w - 0.35f), cy - 1.2f, z + 0.14f, 0.2f, 0.2f, 0.06f, OD)
                    }
                    for (i in 0..2) q(0f, bot + 0.9f + i * 0.95f, z + 0.04f, w * (0.97f - i * 0.07f), 0.4f, 0.13f, if (i % 2 == 0) B else L)
                    q(0f, top - 0.6f, z + 0.1f, w * 0.9f, 0.42f, 0.14f, O)
                    q(0f, cy + 1.2f, z + 0.34f, 0.2f, 1.1f, 0.06f, O)
                    q(0f, cy + 1.7f, z + 0.34f, 0.8f, 0.2f, 0.06f, O)
                }
                2 -> { // Gladiador: couraça muscular de couro, arreio cruzado e leão dourado
                    for (s in sds) {
                        q(s * w * 0.42f, cy + 1.5f, z + 0.15f, w * 0.38f, 1.7f, 0.3f, L)
                        q(s * w * 0.42f, cy + 1.0f, z + 0.4f, w * 0.22f, 0.9f, 0.1f, mul(L, 1.12f))
                        for (r in 0..2) q(s * w * 0.3f, cy - 0.4f - r * 1.15f, z + 0.1f, w * 0.26f, 0.45f, 0.2f, if (r % 2 == 0) L else B)
                    }
                    q(0f, cy - 1.2f, z + 0.06f, 0.14f, 2.8f, 0.08f, E)
                    for (s in sds) {
                        push(g, 0f, cy + 0.6f, z + 0.5f, s * 0.78f) {
                            q(0f, 0f, 0f, 0.5f, d.hy + 0.8f, 0.07f, E)
                            for (r in -2..2) q(0f, r * 1.7f, 0.07f, 0.2f, 0.2f, 0.06f, O)
                        }
                    }
                    q(0f, cy + 0.6f, z + 0.65f, 1.15f, 1.15f, 0.1f, O)
                    q(0f, cy + 0.6f, z + 0.78f, 0.7f, 0.7f, 0.08f, OD)
                    q(0f, cy + 0.5f, z + 0.86f, 0.28f, 0.28f, 0.06f, O, true)
                    q(0f, top - 0.9f, z + 0.1f, w * 0.85f, 0.35f, 0.1f, E)
                }
                3 -> { // Guarda de malha: sobreveste azul com cruz branca e malha nas laterais
                    var y = bot + 0.8f
                    var i = 0
                    while (y < top - 0.4f) {
                        for (s in sds) q(s * (w * 0.8f), y, z, w * 0.2f, 0.14f, 0.06f, if (i % 2 == 0) E else L)
                        y += 0.8f; i++
                    }
                    q(0f, cy - 0.3f, z + 0.12f, w * 0.52f, d.hy - 0.9f, 0.1f, 0xFF2F4F9F.toInt())
                    q(0f, cy - 0.3f, z + 0.22f, 0.4f, d.hy - 1.5f, 0.06f, 0xFFF2F2F2.toInt())
                    q(0f, cy + 0.7f, z + 0.22f, 1.7f, 0.4f, 0.06f, 0xFFF2F2F2.toInt())
                    q(0f, bot + 0.9f, z + 0.2f, w * 0.55f, 0.3f, 0.08f, O)
                    q(0f, top - 0.9f, z + 0.1f, w * 0.7f, 0.55f, 0.12f, E)
                    for (s in sds) q(s * (w - 0.4f), top - 0.9f, z + 0.14f, 0.28f, 0.28f, 0.08f, O)
                }
                4 -> { // Capitão real: casaco com botões duplos, faixa e alamares dourados
                    q(0f, cy, z + 0.02f, w, d.hy - 0.4f, 0.08f, B)
                    for (s in sds) {
                        q(s * w * 0.78f, cy, z + 0.06f, w * 0.18f, d.hy - 0.5f, 0.07f, O)
                        for (r in 0..3) {
                            q(s * 0.9f, bot + 1.3f + r * 1.9f, z + 0.12f, 0.28f, 0.28f, 0.08f, O)
                            q(s * 0.9f, bot + 1.3f + r * 1.9f, z + 0.2f, 0.12f, 0.12f, 0.05f, OD)
                        }
                        for (r in 0..2) q(s * (w - 0.3f), top - 1.2f - r * 0.65f, z + 0.1f, 0.38f, 0.1f, 0.06f, O)
                        q(s * (w - 0.1f), top - 2.6f, z + 0.1f, 0.12f, 1.5f, 0.05f, O)
                    }
                    push(g, 0f, cy + 0.3f, z + 0.14f, 0.62f) {
                        q(0f, 0f, 0f, 0.55f, d.hy + 1.0f, 0.06f, E)
                        q(0f, 0f, 0.05f, 0.14f, d.hy + 1.0f, 0.05f, O)
                    }
                    q(2.3f, cy + 2.2f, z + 0.14f, 0.55f, 0.55f, 0.07f, O)
                    q(2.3f, cy + 2.2f, z + 0.2f, 0.3f, 0.3f, 0.06f, 0xFFB5242E.toInt(), true)
                    q(0f, top - 0.5f, z + 0.12f, w * 0.7f, 0.5f, 0.14f, O)
                }
                else -> { // Mercenário de peles: colete de pele, cintos cruzados e dentes
                    q(0f, top - 0.3f, 0f, d.hx + 0.3f, 0.75f, d.hz + 0.35f, L)
                    for (i in -3..3) q(i * (w / 3.2f), top - 0.7f, d.hz + 0.38f, 0.62f, 0.95f, 0.3f, if (i % 2 == 0) L else E)
                    for (s in sds) {
                        q(s * w * 0.6f, cy + 1.0f, z + 0.1f, w * 0.34f, d.hy - 1.6f, 0.14f, mul(L, 0.9f))
                        for (r in 0..3) q(s * w * 0.6f, cy + 2.8f - r * 1.7f, z + 0.28f, w * 0.3f, 0.3f, 0.1f, if (r % 2 == 0) L else B)
                    }
                    q(0f, cy - 0.5f, z + 0.1f, 0.2f, d.hy - 1.0f, 0.06f, E)
                    for (r in 0..2) q(0f, cy + 1.6f - r * 1.7f, z + 0.15f, 0.55f, 0.18f, 0.06f, OD)
                    push(g, 0f, cy + 0.4f, z + 0.3f, 0.7f) { q(0f, 0f, 0f, 0.6f, d.hy + 0.7f, 0.08f, E) }
                    for (i in -2..2) q(i * 0.7f, top - 2.0f - kotlin.math.abs(i) * 0.25f, z + 0.35f, 0.2f, 0.55f, 0.1f, 0xFFF1EAD6.toInt())
                    q(0f, bot + 0.9f, z + 0.14f, w + 0.1f, 0.5f, 0.14f, L)
                }
            }
            Classe.BARBARO -> when (k) {
                1 -> { // Peles de urso: manto felpudo, tufos e colar de garras
                    for (i in -4..4) {
                        q(i * (w / 4.2f), top - 0.6f, d.hz + 0.4f, 0.8f, 1.1f, 0.38f, if (i % 2 == 0) L else B)
                        q(i * (w / 4.2f) + 0.4f, cy + 1.2f - kotlin.math.abs(i) * 0.3f, z + 0.12f, 0.7f, 1.5f, 0.2f, if (i % 2 == 0) B else E)
                        q(i * (w / 4.2f), bot + 1.0f, z + 0.15f, 0.8f, 0.8f, 0.22f, if (i % 2 == 0) E else B)
                    }
                    q(0f, top - 0.2f, 0f, d.hx + 0.6f, 0.9f, d.hz + 0.55f, L)
                    for (i in -2..2) q(i * 0.85f, cy + 3.3f - kotlin.math.abs(i) * 0.5f, z + 0.5f, 0.22f, 0.8f, 0.15f, 0xFFF3EBD2.toInt())
                    q(0f, cy + 4.1f, z + 0.4f, 1.9f, 0.14f, 0.1f, OD)
                    q(0f, cy - 0.4f, z + 0.12f, 0.35f, d.hy - 1.4f, 0.1f, E)
                }
                2 -> { // Couro tribal: tiras com contas de osso e pintura de guerra
                    for (s in sds) {
                        push(g, 0f, cy + 0.4f, z + 0.1f, s * 0.7f) {
                            q(0f, 0f, 0f, 0.85f, d.hy + 0.9f, 0.1f, B)
                            q(0f, 0f, 0.08f, 0.3f, d.hy + 0.9f, 0.05f, L)
                            for (r in -2..2) q(0f, r * 1.8f, 0.14f, 0.38f, 0.38f, 0.08f, 0xFFE8DCC0.toInt())
                        }
                    }
                    for (r in 0..2) q(0f, cy + 3.0f - r * 3.0f, z + 0.18f, 0.7f, 0.7f, 0.1f, if (r == 1) O else OD)
                    q(0f, cy + 0.2f, z + 0.28f, 1.1f, 1.1f, 0.1f, E)
                    q(0f, cy + 0.2f, z + 0.38f, 0.5f, 0.5f, 0.08f, 0xFFE8DCC0.toInt())
                    for (s in sds) for (r in 0..2) q(s * (w - 0.5f - r * 0.3f), cy - 0.5f + r * 1.3f, z + 0.06f, 0.14f, 0.5f, 0.05f, 0xFFC9302C.toInt())
                    q(0f, top - 0.9f, z + 0.1f, w * 0.75f, 0.3f, 0.1f, E)
                    for (i in -3..3) q(i * 0.8f, top - 1.5f - kotlin.math.abs(i) * 0.15f, z + 0.18f, 0.2f, 0.5f, 0.08f, 0xFFE8DCC0.toInt())
                }
                3 -> { // Saqueador de ferro: placas soldadas, correntes e caveira de fivela
                    q(0f, cy + 1.4f, z + 0.08f, w * 0.7f, 1.8f, 0.2f, L)
                    q(-w * 0.35f, cy - 1.4f, z + 0.1f, w * 0.34f, 1.6f, 0.18f, B)
                    q(w * 0.38f, cy - 1.8f, z + 0.12f, w * 0.3f, 1.3f, 0.2f, L)
                    q(0f, cy - 3.6f, z + 0.12f, w * 0.8f, 0.6f, 0.16f, E)
                    for (s in sds) {
                        for (r in 0..2) q(s * (w - 0.4f), cy + 3.0f - r * 2.3f, z + 0.22f, 0.26f, 0.26f, 0.1f, OD)
                        for (r in 0..4) q(s * (0.9f + r * 0.8f), cy + 3.6f - r * 0.5f, z + 0.3f, 0.3f, 0.2f, 0.06f, E)
                        q(s * (w * 0.5f), top - 0.3f, z + 0.2f, 0.6f, 0.8f, 0.4f, L)
                        q(s * (w * 0.5f), top + 0.3f, z + 0.25f, 0.18f, 0.7f, 0.18f, L)
                    }
                    q(0f, cy + 0.2f, z + 0.34f, 1.1f, 1.0f, 0.1f, 0xFFE8DCC0.toInt())
                    q(-0.4f, cy + 0.3f, z + 0.44f, 0.25f, 0.25f, 0.05f, E)
                    q(0.4f, cy + 0.3f, z + 0.44f, 0.25f, 0.25f, 0.05f, E)
                    q(0f, top - 0.5f, z + 0.1f, w * 0.85f, 0.45f, 0.14f, E)
                }
                4 -> { // Armadura de ossos: caixa torácica, coluna e caveira
                    q(0f, cy, z + 0.04f, 0.38f, d.hy - 0.3f, 0.12f, L)
                    for (r in 0..4) {
                        val yy = top - 1.5f - r * 1.7f
                        val len = w * (0.92f - r * 0.07f)
                        for (s in sds) {
                            q(s * len * 0.5f, yy, z + 0.12f, len * 0.5f, 0.3f, 0.14f, if (r % 2 == 0) L else B)
                            q(s * (len - 0.3f), yy - 0.5f, z + 0.1f, 0.3f, 0.5f, 0.12f, B)
                        }
                    }
                    q(0f, top - 0.6f, z + 0.18f, 1.5f, 1.0f, 0.22f, L)
                    q(-0.55f, top - 0.5f, z + 0.42f, 0.3f, 0.3f, 0.06f, 0xFF2B1B10.toInt())
                    q(0.55f, top - 0.5f, z + 0.42f, 0.3f, 0.3f, 0.06f, 0xFF2B1B10.toInt())
                    q(0f, top - 1.35f, z + 0.3f, 0.9f, 0.3f, 0.12f, B)
                    for (i in -2..2) q(i * 0.45f, top - 1.65f, z + 0.3f, 0.14f, 0.3f, 0.1f, L)
                    q(0f, bot + 0.6f, z + 0.1f, w, 0.5f, 0.14f, E)
                    for (s in sds) q(s * (w + 0.2f), top - 0.4f, 0f, 0.4f, 0.9f, d.hz + 0.2f, L)
                }
                else -> { // Guerreiro do clã: tartã xadrez, broche do clã e tiras de couro
                    val rd = 0xFFB52B2B.toInt()
                    val vd = 0xFF1F5A2E.toInt()
                    q(0f, cy, z + 0.03f, w, d.hy - 0.4f, 0.07f, B)
                    for (i in -3..3) q(i * (w / 3.4f), cy, z + 0.1f, 0.3f, d.hy - 0.4f, 0.05f, if (i % 2 == 0) vd else rd)
                    for (r in 0..4) q(0f, bot + 0.9f + r * 1.8f, z + 0.15f, w, 0.25f, 0.05f, if (r % 2 == 0) E else vd)
                    for (s in sds) {
                        push(g, s * 0.6f, top - 0.5f, z + 0.18f, s * 0.9f) {
                            q(0f, -3.4f, 0f, 1.0f, 4.6f, 0.07f, rd)
                            for (r in 0..2) q(0f, -1.0f - r * 2.0f, 0.06f, 1.0f, 0.15f, 0.05f, vd)
                        }
                    }
                    q(-w * 0.62f, top - 1.1f, z + 0.3f, 0.95f, 0.95f, 0.1f, O)
                    q(-w * 0.62f, top - 1.1f, z + 0.4f, 0.55f, 0.55f, 0.08f, 0xFF5A1B1B.toInt())
                    q(0f, bot + 0.8f, z + 0.2f, w, 0.45f, 0.12f, mul(B, 0.7f))
                    q(0f, bot + 0.8f, z + 0.32f, 0.8f, 0.38f, 0.08f, O)
                }
            }
            Classe.MAGO -> when (k) {
                1 -> { // Arquimago: bordado de constelações, colar largo dourado e gema
                    q(0f, top - 0.8f, z, w, 0.9f, 0.1f, O)
                    for (s in sds) q(s * (w - 0.35f), cy - 0.6f, z, 0.45f, d.hy - 1.3f, 0.07f, O)
                    for (i in 0..8) {
                        val px = (((i * 37) % 11) - 5) * 0.8f
                        val py = bot + 1.0f + ((i * 53) % 7) * 0.95f
                        q(px, py, z + 0.1f, 0.16f, 0.16f, 0.06f, 0xFFFFF2B0.toInt(), true)
                    }
                    q(-1.6f, cy + 0.6f, z + 0.1f, 0.12f, 1.0f, 0.04f, OD)
                    q(1.2f, cy - 0.5f, z + 0.1f, 0.12f, 1.2f, 0.04f, OD)
                    q(0f, top - 1.6f, z + 0.14f, 0.9f, 0.9f, 0.1f, O)
                    q(0f, top - 1.6f, z + 0.24f, 0.5f, 0.5f, 0.08f, 0xFF8A5BFF.toInt(), true)
                    q(0f, cy - 1.0f, z + 0.12f, 0.12f, 3.4f, 0.05f, O)
                    q(0f, cy - 4.2f, z + 0.14f, 0.35f, 0.35f, 0.07f, O)
                }
                2 -> { // Vestes do sábio: gola cruzada, faixa azul com nó e runas
                    for (s in sds) {
                        push(g, s * 0.8f, top - 0.5f, z + 0.08f, s * 0.55f) {
                            q(0f, -3.6f, 0f, 1.0f, 4.8f, 0.08f, L)
                            q(s * 0.9f, -3.6f, 0.05f, 0.14f, 4.8f, 0.05f, E)
                        }
                    }
                    q(0f, top - 1.4f, z + 0.1f, 1.0f, 1.3f, 0.07f, pele())
                    q(0f, bot + 1.7f, z + 0.14f, w + 0.15f, 1.0f, 0.12f, E)
                    q(w * 0.35f, bot + 1.7f, z + 0.28f, 0.7f, 0.7f, 0.1f, E)
                    q(w * 0.35f + 0.3f, bot + 0.3f, z + 0.24f, 0.28f, 1.0f, 0.07f, E)
                    for (r in 0..3) q(-w * 0.55f, cy - 1.0f + r * 1.3f, z + 0.1f, 0.22f, 0.22f, 0.05f, E, true)
                    q(0f, top - 0.4f, z + 0.1f, w * 0.7f, 0.3f, 0.1f, E)
                }
                3 -> { // Feiticeiro das sombras: casaco com selo arcano brilhante e gola de espinhos
                    val gl = 0xFF9A5BFF.toInt()
                    for (a in 0..11) {
                        val ang = a * 0.5236f
                        q(kotlin.math.cos(ang) * 2.0f, cy + 0.4f + kotlin.math.sin(ang) * 2.0f, z + 0.1f, 0.2f, 0.2f, 0.05f, gl, true)
                    }
                    q(0f, cy + 0.4f, z + 0.12f, 0.9f, 0.18f, 0.05f, gl, true)
                    q(0f, cy + 0.4f, z + 0.12f, 0.18f, 0.9f, 0.05f, gl, true)
                    for (s in sds) {
                        q(s * w * 0.78f, cy, z + 0.06f, 0.25f, d.hy - 0.6f, 0.07f, L)
                        for (r in 0..3) q(s * w * 0.78f, bot + 1.0f + r * 2.0f, z + 0.12f, 0.35f, 0.18f, 0.06f, OD)
                        for (r in 0..2) q(s * (w * 0.5f + r * 0.9f), top + 0.2f + r * 0.25f, z - 0.4f, 0.3f, 0.7f, 0.2f, E)
                    }
                    q(0f, top - 0.8f, z + 0.1f, w * 0.8f, 0.35f, 0.1f, E)
                    q(0f, bot + 0.8f, z + 0.1f, w, 0.35f, 0.1f, E)
                }
                4 -> { // Mago de fogo: línguas de chamas na barra, brasa no peito
                    for (i in -4..4) {
                        val h = 1.4f + ((i * 7) and 3) * 0.6f
                        q(i * (w / 4.3f), bot + h * 0.5f + 0.4f, z + 0.1f, 0.6f, h * 0.5f, 0.08f, if (i % 2 == 0) 0xFFFF9A2A.toInt() else 0xFFFFD25A.toInt(), true)
                        q(i * (w / 4.3f), bot + h + 0.6f, z + 0.12f, 0.3f, 0.5f, 0.06f, 0xFFFF6A1A.toInt(), true)
                    }
                    q(0f, cy + 1.2f, z + 0.12f, 1.2f, 1.2f, 0.1f, O)
                    q(0f, cy + 1.2f, z + 0.22f, 0.7f, 0.7f, 0.08f, 0xFFFF5A1A.toInt(), true)
                    q(0f, cy + 1.2f, z + 0.3f, 0.3f, 0.3f, 0.06f, 0xFFFFE08A.toInt(), true)
                    for (s in sds) {
                        q(s * (w - 0.4f), cy, z + 0.06f, 0.4f, d.hy - 1.0f, 0.07f, E)
                        q(s * 2.2f, top - 0.9f, z + 0.1f, 0.14f, 0.7f, 0.06f, O)
                    }
                    q(0f, top - 0.6f, z + 0.1f, w * 0.7f, 0.4f, 0.12f, O)
                }
                else -> { // Druida do bosque: escamas de folhas, colar de casca e flor
                    for (r in 0..4) for (i in -3..3) {
                        val ox = if (r % 2 == 0) 0f else 0.5f
                        q(i * (w / 3.2f) + ox, bot + 0.9f + r * 1.9f, z + 0.06f + r * 0.02f, 0.62f, 0.55f, 0.07f, if ((i + r) % 2 == 0) B else L)
                    }
                    q(0f, top - 0.6f, 0f, d.hx + 0.2f, 0.7f, d.hz + 0.3f, E)
                    for (i in -3..3) q(i * 1.7f, top - 1.0f, d.hz + 0.35f, 0.5f, 0.8f, 0.14f, if (i % 2 == 0) B else L)
                    for (s in sds) q(s * (w - 0.1f), cy, z + 0.04f, 0.14f, d.hy - 0.5f, 0.06f, 0xFF6B4A2A.toInt())
                    q(0f, cy - 0.2f, z + 0.14f, 0.14f, d.hy - 1.0f, 0.05f, 0xFF6B4A2A.toInt())
                    for (a in 0..4) q(kotlin.math.cos(a * 1.2566f) * 0.7f, cy + 1.6f + kotlin.math.sin(a * 1.2566f) * 0.7f, z + 0.22f, 0.4f, 0.4f, 0.06f, 0xFFF4B6D2.toInt())
                    q(0f, cy + 1.6f, z + 0.28f, 0.3f, 0.3f, 0.06f, O)
                }
            }
            Classe.ARQUEIRO -> when (k) {
                1 -> { // Caçador da floresta: gibão com cadarço, tira da aljava e pena
                    q(0f, cy, z + 0.03f, w * 0.9f, d.hy - 0.5f, 0.08f, L)
                    for (r in 0..4) {
                        q(0f, bot + 1.0f + r * 1.7f, z + 0.12f, 0.9f, 0.1f, 0.05f, E)
                        q(-0.55f, bot + 1.0f + r * 1.7f, z + 0.14f, 0.12f, 0.2f, 0.05f, O)
                        q(0.55f, bot + 1.0f + r * 1.7f, z + 0.14f, 0.12f, 0.2f, 0.05f, O)
                    }
                    for (s in sds) q(s * 0.3f, cy, z + 0.1f, 0.08f, d.hy - 0.6f, 0.05f, E)
                    push(g, 0f, cy + 0.2f, z + 0.2f, -0.62f) { q(0f, 0f, 0f, 0.65f, d.hy + 1.0f, 0.07f, E) }
                    q(w * 0.6f, cy - 1.8f, z + 0.2f, 0.9f, 0.9f, 0.2f, B)
                    q(w * 0.6f, cy - 0.9f, z + 0.3f, 0.9f, 0.3f, 0.12f, E)
                    q(-w * 0.55f, top - 1.0f, z + 0.2f, 0.18f, 1.2f, 0.06f, 0xFFD94B3A.toInt())
                    q(-w * 0.55f, top - 0.2f, z + 0.2f, 0.4f, 0.5f, 0.05f, 0xFFF0F0F0.toInt())
                }
                2 -> { // Elfo de seda: folhas bordadas, gola assimétrica e pingente de lua
                    for (s in sds) {
                        push(g, s * 0.5f, top - 0.4f, z + 0.06f, s * 0.35f) { q(0f, -3.8f, 0f, 1.1f, 5.0f, 0.08f, L) }
                        for (r in 0..3) {
                            q(s * (w * 0.55f), bot + 1.4f + r * 2.0f, z + 0.1f, 0.5f, 0.25f, 0.05f, E)
                            q(s * (w * 0.55f) + s * 0.4f, bot + 1.7f + r * 2.0f, z + 0.1f, 0.22f, 0.4f, 0.05f, E)
                        }
                    }
                    q(0f, bot + 1.0f, z + 0.1f, w, 0.35f, 0.07f, O)
                    q(0f, top - 1.9f, z + 0.14f, 0.12f, 0.9f, 0.05f, O)
                    q(0f, top - 3.0f, z + 0.16f, 0.5f, 0.5f, 0.06f, 0xFFEAF2FF.toInt(), true)
                    q(0.25f, top - 3.0f, z + 0.2f, 0.4f, 0.4f, 0.05f, B)
                    q(0f, top - 0.4f, z + 0.1f, w * 0.7f, 0.3f, 0.1f, O)
                }
                3 -> { // Ladrão encapuzado: cintos cruzados, fileira de facas e gola alta
                    for (s in sds) {
                        push(g, 0f, cy + 0.3f, z + 0.1f, s * 0.6f) {
                            q(0f, 0f, 0f, 0.5f, d.hy + 1.0f, 0.08f, L)
                            for (r in -2..2) q(0f, r * 1.7f, 0.08f, 0.22f, 0.3f, 0.06f, O)
                        }
                    }
                    for (r in 0..3) {
                        q(-w * 0.55f + r * 0.5f, cy - 2.0f, z + 0.16f, 0.15f, 0.9f, 0.05f, 0xFFB8B8C8.toInt())
                        q(-w * 0.55f + r * 0.5f, cy - 2.9f, z + 0.16f, 0.2f, 0.3f, 0.06f, E)
                    }
                    q(0f, bot + 0.7f, z + 0.1f, w, 0.4f, 0.1f, L)
                    q(0f, bot + 0.7f, z + 0.18f, 0.4f, 0.3f, 0.06f, O)
                    q(0f, top - 0.2f, 0f, d.hx + 0.1f, 0.9f, d.hz + 0.35f, E)
                    q(0f, top + 0.4f, z - 0.4f, 3.0f, 0.5f, 0.15f, E)
                }
                4 -> { // Guarda élfico: placas em forma de folha com gema e filigrana
                    for (r in 0..3) {
                        val wv = w * (0.8f - r * 0.1f)
                        q(0f, top - 1.7f - r * 1.9f, z + 0.1f + r * 0.02f, wv, 0.85f, 0.12f, if (r % 2 == 0) L else B)
                        q(0f, top - 1.7f - r * 1.9f - 0.8f, z + 0.14f, wv * 0.8f, 0.1f, 0.06f, O)
                    }
                    for (s in sds) {
                        q(s * (w - 0.3f), cy + 1.0f, z + 0.1f, 0.4f, 2.6f, 0.1f, E)
                        q(s * (w - 0.3f), cy - 2.2f, z + 0.1f, 0.4f, 1.5f, 0.1f, E)
                    }
                    q(0f, top - 0.9f, z + 0.14f, 1.0f, 0.5f, 0.1f, O)
                    q(0f, cy + 1.0f, z + 0.3f, 0.5f, 0.5f, 0.08f, 0xFF59E0A0.toInt(), true)
                    q(0f, cy + 1.0f, z + 0.26f, 0.9f, 0.14f, 0.05f, O)
                    q(0f, cy + 1.0f, z + 0.26f, 0.14f, 0.9f, 0.05f, O)
                }
                else -> { // Patrulheiro do inverno: casaco com gola de pele, botões de toggle e cristal de gelo
                    q(0f, top - 0.3f, 0f, d.hx + 0.4f, 0.9f, d.hz + 0.45f, L)
                    for (i in -3..3) q(i * (w / 3.2f), top - 0.8f, d.hz + 0.5f, 0.6f, 0.9f, 0.26f, if (i % 2 == 0) L else mul(L, 0.88f))
                    q(0f, bot + 0.8f, z + 0.1f, w + 0.15f, 0.7f, 0.14f, L)
                    for (s in sds) {
                        for (r in 0..3) {
                            q(s * 1.0f, bot + 2.0f + r * 1.7f, z + 0.1f, 0.5f, 0.2f, 0.06f, E)
                            q(s * 0.5f, bot + 2.0f + r * 1.7f, z + 0.14f, 0.18f, 0.18f, 0.06f, 0xFFE8DCC0.toInt())
                        }
                        q(s * (w * 0.7f), cy + 0.2f, z + 0.06f, 0.14f, d.hy - 1.2f, 0.05f, E)
                    }
                    q(0f, cy + 0.2f, z + 0.1f, 0.12f, d.hy - 1.4f, 0.06f, OD)
                    q(-w * 0.5f, top - 2.0f, z + 0.3f, 0.5f, 0.7f, 0.08f, 0xFFBFEFFF.toInt(), true)
                    q(-w * 0.5f, top - 2.0f, z + 0.36f, 0.2f, 0.4f, 0.06f, 0xFFFFFFFF.toInt(), true)
                }
            }
            Classe.NINJA, Classe.PIKACHU, Classe.ICHIGO, Classe.LUFFY -> when (k) {
                1 -> { // Shinobi noturno: faixas cruzadas, bandoleira de kunai e emblema tomoe
                    for (s in sds) {
                        push(g, 0f, cy + 0.2f, z + 0.06f, s * 0.7f) {
                            q(0f, 0f, 0f, 0.75f, d.hy + 0.9f, 0.07f, L)
                            for (r in -2..2) q(0f, r * 1.7f, 0.06f, 0.78f, 0.1f, 0.05f, E)
                        }
                    }
                    for (r in 0..3) {
                        q(-w * 0.62f + r * 0.55f, cy - 2.2f, z + 0.14f, 0.12f, 0.95f, 0.05f, 0xFFB8B8C8.toInt())
                        q(-w * 0.62f + r * 0.55f, cy - 3.1f, z + 0.14f, 0.18f, 0.3f, 0.06f, O)
                    }
                    q(0f, bot + 0.8f, z + 0.1f, w, 0.4f, 0.1f, O)
                    q(w * 0.55f, cy + 1.2f, z + 0.2f, 0.7f, 0.7f, 0.06f, E)
                    q(w * 0.55f, cy + 1.2f, z + 0.26f, 0.35f, 0.35f, 0.05f, O, true)
                }
                2 -> { // Assassino carmesim: quimono cruzado, obi preto e dragão bordado
                    for (s in sds) {
                        push(g, s * 0.7f, top - 0.4f, z + 0.08f, s * 0.5f) {
                            q(0f, -3.7f, 0f, 1.0f, 4.9f, 0.08f, L)
                            q(s * 0.95f, -3.7f, 0.05f, 0.14f, 4.9f, 0.05f, O)
                        }
                    }
                    q(0f, top - 1.3f, z + 0.1f, 0.9f, 1.2f, 0.06f, 0xFF15151C.toInt())
                    q(0f, bot + 1.5f, z + 0.14f, w + 0.15f, 1.1f, 0.12f, 0xFF15151C.toInt())
                    q(0f, bot + 1.5f, z + 0.26f, 0.9f, 0.9f, 0.08f, O)
                    for (r in 0..4) q(w * 0.5f + (r % 2) * 0.5f, cy - 0.8f + r * 1.0f, z + 0.1f, 0.4f, 0.2f, 0.05f, O)
                    for (s in sds) q(s * (w - 0.2f), cy + 0.5f, z + 0.1f, 0.12f, 2.0f, 0.05f, O)
                }
                3 -> { // Ninja da neve: jaqueta acolchoada em grade, gola de pelo e bolsa de shuriken
                    for (r in 0..4) for (i in -3..3) q(i * (w / 3.4f), bot + 1.0f + r * 1.8f, z + 0.06f, w / 3.4f - 0.14f, 0.82f, 0.08f, if ((r + i) % 2 == 0) B else L)
                    for (r in 0..5) q(0f, bot + 0.2f + r * 1.8f, z + 0.16f, w, 0.07f, 0.04f, E)
                    for (i in -3..3) q(i * (w / 3.4f), cy, z + 0.16f, 0.06f, d.hy - 0.3f, 0.04f, E)
                    q(0f, top - 0.4f, 0f, d.hx + 0.2f, 0.7f, d.hz + 0.3f, 0xFFFFFFFF.toInt())
                    q(0f, bot + 0.8f, z + 0.12f, w, 0.35f, 0.1f, O)
                    q(w * 0.55f, cy - 1.8f, z + 0.2f, 0.9f, 0.9f, 0.2f, E)
                    q(w * 0.55f, cy - 1.8f, z + 0.34f, 0.5f, 0.5f, 0.05f, 0xFFBFEFFF.toInt(), true)
                    for (s in sds) q(s * 0.5f, cy, z + 0.2f, 0.12f, d.hy - 0.6f, 0.05f, O)
                }
                4 -> { // Ninja blindado: armadura lamelar com cordões e placa de máscara oni
                    for (r in 0..5) for (i in -4..4) q(i * (w / 4.3f), bot + 0.8f + r * 1.5f, z + 0.05f + (r % 2) * 0.03f, w / 4.3f - 0.1f, 0.66f, 0.07f, if ((i + r) % 2 == 0) L else B)
                    for (i in -2..2) q(i * (w / 2.2f), cy, z + 0.14f, 0.07f, d.hy - 0.4f, 0.04f, E)
                    q(0f, cy + 0.8f, z + 0.2f, w * 0.5f, 1.9f, 0.1f, E)
                    q(0f, cy + 0.8f, z + 0.3f, w * 0.4f, 1.6f, 0.06f, L)
                    q(-0.9f, cy + 1.3f, z + 0.36f, 0.45f, 0.18f, 0.05f, O, true)
                    q(0.9f, cy + 1.3f, z + 0.36f, 0.45f, 0.18f, 0.05f, O, true)
                    q(0f, cy + 0.2f, z + 0.36f, 0.9f, 0.14f, 0.05f, O)
                    for (s in sds) q(s * 0.5f, cy + 2.0f, z + 0.4f, 0.14f, 0.5f, 0.05f, O)
                    q(0f, top - 0.8f, z + 0.1f, w * 0.85f, 0.4f, 0.12f, E)
                    q(0f, bot + 0.6f, z + 0.1f, w, 0.35f, 0.1f, O)
                }
                else -> { // Mestre dourado: seda negra com dragão dourado serpenteando e obi largo
                    for (r in 0..5) {
                        val xx = kotlin.math.sin(r * 1.1f) * w * 0.55f
                        q(xx, top - 1.2f - r * 1.35f, z + 0.1f, 0.9f, 0.45f, 0.06f, O)
                        q(xx + (if (r % 2 == 0) 0.8f else -0.8f), top - 1.5f - r * 1.35f, z + 0.12f, 0.3f, 0.3f, 0.05f, OD)
                    }
                    q(w * 0.4f, top - 0.9f, z + 0.16f, 0.55f, 0.55f, 0.07f, O)
                    q(w * 0.4f + 0.12f, top - 0.8f, z + 0.22f, 0.18f, 0.18f, 0.05f, 0xFFFF3030.toInt(), true)
                    q(0f, bot + 1.4f, z + 0.12f, w + 0.15f, 1.0f, 0.12f, O)
                    q(0f, bot + 1.4f, z + 0.24f, 1.2f, 0.8f, 0.08f, OD)
                    q(0f, bot + 1.4f, z + 0.32f, 0.5f, 0.4f, 0.06f, 0xFFB5242E.toInt(), true)
                    for (s in sds) {
                        q(s * (w - 0.2f), cy + 0.6f, z + 0.06f, 0.14f, d.hy - 1.2f, 0.05f, O)
                        push(g, s * 0.7f, top - 0.4f, z + 0.1f, s * 0.4f) { q(0f, -2.5f, 0f, 0.14f, 3.4f, 0.05f, O) }
                    }
                    q(0f, top - 0.3f, z + 0.1f, w * 0.7f, 0.3f, 0.1f, O)
                }
            }
        }
    }

    /** Roda o bloco dentro de um push/pop com rotZ (para tiras e faixas diagonais). */
    private inline fun push(g: HeroGfx, x: Float, y: Float, z: Float, rot: Float, bloco: () -> Unit) {
        g.push()
        g.translate(x, y, z)
        g.rotZ(rot)
        bloco()
        g.pop()
    }

    private fun abdomenUnico(g: HeroGfx, c: Classe, k: Int, d: D, t: Traje) {
        val B = cor(t, t.base)
        val L = cor(t, t.luz)
        val E = cor(t, t.esc)
        val O = ouro(t)
        val OD = ouroD(t)
        val y = d.ay
        val z = d.azF + 0.05f
        val w = d.ahx - 0.3f
        fun q(x: Float, yy: Float, zz: Float, hx: Float, hy: Float, hz: Float, col: Int, em: Boolean = false) = g.cube(x, yy, zz, hx, hy, hz, col, em)
        val sds = floatArrayOf(-1f, 1f)
        when (c) {
            Classe.GUERREIRO -> when (k) {
                1 -> { for (i in 0..2) q(0f, y + 1.3f - i * 1.15f, z + 0.02f * i, w * (1f - i * 0.05f), 0.5f, 0.1f, if (i % 2 == 0) L else B); q(0f, y + 0.2f, z + 0.14f, 0.9f, 0.7f, 0.08f, O) }
                2 -> { for (i in -3..3) q(i * (w / 3.4f), y - 0.3f, z + 0.08f, 0.62f, 1.7f, 0.06f, if (i % 2 == 0) L else B); q(0f, y + 1.5f, z + 0.12f, w, 0.32f, 0.1f, E); q(0f, y + 1.5f, z + 0.22f, 0.5f, 0.45f, 0.06f, O) }
                3 -> { q(0f, y - 0.2f, z + 0.1f, w * 0.5f, 2.0f, 0.08f, 0xFF2F4F9F.toInt()); q(0f, y + 1.5f, z + 0.12f, w, 0.3f, 0.1f, E); q(0f, y + 1.5f, z + 0.2f, 0.5f, 0.4f, 0.06f, O) }
                4 -> { q(0f, y + 1.1f, z + 0.06f, w, 0.55f, 0.1f, O); q(0f, y + 1.1f, z + 0.16f, 0.6f, 0.45f, 0.06f, 0xFFB5242E.toInt(), true); for (s in sds) q(s * 1.6f, y - 0.9f, z + 0.08f, 0.2f, 1.3f, 0.06f, O) }
                else -> { q(0f, y + 1.2f, z + 0.08f, w + 0.1f, 0.6f, 0.14f, L); for (i in -2..2) q(i * 0.9f, y - 0.1f, z + 0.12f, 0.4f, 0.8f, 0.1f, if (i % 2 == 0) E else L); q(0f, y + 1.2f, z + 0.24f, 0.4f, 0.4f, 0.06f, OD) }
            }
            Classe.BARBARO -> when (k) {
                1 -> { for (i in -3..3) q(i * (w / 3.2f), y - 0.1f, z + 0.1f, 0.7f, 1.9f - kotlin.math.abs(i) * 0.15f, 0.14f, if (i % 2 == 0) L else B) }
                2 -> { q(0f, y + 1.4f, z + 0.08f, w, 0.3f, 0.1f, E); for (i in -3..3) q(i * 1.3f, y + 0.2f - kotlin.math.abs(i) * 0.1f, z + 0.12f, 0.2f, 0.2f, 0.07f, 0xFFE8DCC0.toInt()); q(0f, y - 0.8f, z + 0.1f, 0.14f, 0.9f, 0.06f, E) }
                3 -> { q(0f, y + 0.6f, z + 0.08f, w, 1.0f, 0.12f, L); q(-w * 0.5f, y - 0.8f, z + 0.1f, w * 0.4f, 0.9f, 0.1f, B); for (i in 0..3) q(-w + i * 1.2f, y - 1.7f, z + 0.14f, 0.28f, 0.28f, 0.06f, E) }
                4 -> { for (s in sds) { q(s * w * 0.7f, y + 0.4f, z + 0.06f, 0.35f, 1.5f, 0.1f, L); q(s * w * 0.35f, y - 0.2f, z + 0.06f, 0.3f, 1.2f, 0.1f, B) }; q(0f, y + 1.5f, z + 0.1f, w, 0.35f, 0.1f, E) }
                else -> { val rd = 0xFFB52B2B.toInt(); for (i in -3..3) q(i * (w / 3.4f), y - 0.2f, z + 0.1f, w / 3.4f - 0.08f, 1.8f, 0.08f, if (i % 2 == 0) rd else 0xFF1F5A2E.toInt()); q(0f, y + 1.5f, z + 0.14f, w, 0.3f, 0.1f, E) }
            }
            Classe.MAGO -> when (k) {
                1 -> { q(0f, y + 1.1f, z + 0.06f, w, 0.6f, 0.1f, O); for (i in 0..2) q(-1.4f + i * 1.4f, y - 0.4f, z + 0.1f, 0.18f, 0.18f, 0.06f, 0xFFFFF2B0.toInt(), true); q(0f, y - 1.3f, z + 0.06f, 0.1f, 0.9f, 0.05f, O) }
                2 -> { q(0f, y + 0.4f, z + 0.1f, w + 0.1f, 1.2f, 0.12f, E); q(w * 0.45f, y + 0.3f, z + 0.24f, 0.7f, 0.7f, 0.1f, E); q(w * 0.5f, y - 1.2f, z + 0.2f, 0.28f, 1.3f, 0.07f, E) }
                3 -> { for (s in sds) { push(g, 0f, y, z + 0.1f, s * 0.7f) { q(0f, 0f, 0f, 0.3f, 2.4f, 0.07f, L) } }; q(0f, y, z + 0.2f, 0.5f, 0.5f, 0.08f, O); q(0f, y, z + 0.28f, 0.22f, 0.22f, 0.06f, 0xFF9A5BFF.toInt(), true) }
                4 -> { q(0f, y + 1.1f, z + 0.06f, w, 0.6f, 0.1f, 0xFF7E200F.toInt()); for (i in -2..2) q(i * 1.4f, y - 0.2f, z + 0.1f, 0.4f, 0.9f + (2 - kotlin.math.abs(i)) * 0.3f, 0.07f, 0xFFFF9A2A.toInt(), true) }
                else -> { q(0f, y + 1.2f, z + 0.08f, w, 0.28f, 0.1f, 0xFF6B4A2A.toInt()); for (i in -3..3) q(i * 1.4f, y + 0.2f - kotlin.math.abs(i) * 0.12f, z + 0.12f, 0.45f, 0.7f, 0.07f, if (i % 2 == 0) B else L) }
            }
            Classe.ARQUEIRO -> {}
            Classe.NINJA, Classe.PIKACHU, Classe.ICHIGO, Classe.LUFFY -> when (k) {
                1 -> { q(0f, y + 1.2f, z + 0.08f, w, 0.55f, 0.1f, O); q(w * 0.5f, y + 1.2f, z + 0.2f, 0.6f, 0.6f, 0.1f, O); q(w * 0.55f, y - 0.2f, z + 0.16f, 0.25f, 1.4f, 0.06f, O) }
                2 -> { q(0f, y + 0.9f, z + 0.08f, w, 0.9f, 0.12f, 0xFF15151C.toInt()); q(0f, y + 0.9f, z + 0.2f, 0.8f, 0.7f, 0.08f, O) }
                3 -> { q(0f, y + 0.9f, z + 0.08f, w, 0.8f, 0.12f, E); q(-w * 0.4f, y + 0.9f, z + 0.2f, 0.6f, 0.6f, 0.08f, 0xFFBFEFFF.toInt(), true); q(-w * 0.4f, y - 0.5f, z + 0.16f, 0.2f, 1.0f, 0.06f, E) }
                4 -> { for (i in -3..3) q(i * (w / 3.4f), y - 0.1f, z + 0.08f, w / 3.4f - 0.1f, 1.8f, 0.08f, if (i % 2 == 0) L else B); q(0f, y + 1.5f, z + 0.12f, w, 0.3f, 0.1f, O) }
                else -> { q(0f, y + 0.9f, z + 0.08f, w, 0.9f, 0.12f, O); q(0f, y + 0.9f, z + 0.2f, 1.1f, 0.7f, 0.08f, OD); q(0f, y + 0.9f, z + 0.28f, 0.5f, 0.35f, 0.06f, 0xFFB5242E.toInt(), true) }
            }
        }
    }

    private fun pernaUnica(g: HeroGfx, c: Classe, k: Int, th: Float, zf: Float, canela: Boolean, ty: Float, t: Traje) {
        val B = cor(t, t.base)
        val L = cor(t, t.luz)
        val E = cor(t, t.esc)
        val O = ouro(t)
        val OD = ouroD(t)
        val z = zf + 0.05f
        val y0 = ty
        fun q(x: Float, yy: Float, zz: Float, hx: Float, hy: Float, hz: Float, col: Int, em: Boolean = false) = g.cube(x, yy, zz, hx, hy, hz, col, em)
        val a = th - 0.1f
        // y0 = centro da perna; as peças são relativas a ele
        if (!canela) {
            when (c) {
                Classe.GUERREIRO -> when (k) {
                    1 -> { q(0f, y0 + 1.2f, z + 0.05f, a, 1.3f, 0.12f, L); q(0f, y0 + 1.2f, z + 0.15f, 0.2f, 1.2f, 0.06f, E); q(0f, y0 - 2.6f, z + 0.05f, a * 0.8f, 0.3f, 0.1f, O) }
                    2 -> { for (i in -2..2) q(i * (a / 2.4f), y0 + 1.6f - kotlin.math.abs(i) * 0.15f, z + 0.08f, a / 2.4f - 0.05f, 1.6f, 0.08f, if (i % 2 == 0) L else B); q(0f, y0 - 1.0f, z + 0.1f, a, 0.22f, 0.08f, E) }
                    3 -> { for (i in 0..5) q(0f, y0 + 2.2f - i * 0.8f, z + 0.05f, a - 0.1f, 0.12f, 0.06f, if (i % 2 == 0) E else L); q(0f, y0 - 2.6f, z + 0.1f, a, 0.25f, 0.08f, O) }
                    4 -> { q(0f, y0, z + 0.04f, 0.28f, 2.6f, 0.06f, O); q(0f, y0 + 2.2f, z + 0.1f, a, 0.3f, 0.08f, O); q(0f, y0 - 2.5f, z + 0.1f, a, 0.5f, 0.1f, 0xFFB5242E.toInt()) }
                    else -> { for (i in 0..3) q(0f, y0 + 2.2f - i * 1.4f, z + 0.1f, a, 0.28f, 0.12f, E); q(0f, y0 - 2.4f, z + 0.12f, a + 0.1f, 0.7f, 0.2f, L) }
                }
                Classe.BARBARO -> when (k) {
                    1 -> { for (i in -2..2) q(i * (a / 2.4f), y0 - 2.4f, z + 0.12f, 0.6f, 0.9f - kotlin.math.abs(i) * 0.1f, 0.2f, if (i % 2 == 0) L else B); q(0f, y0 + 2.4f, z + 0.08f, a + 0.1f, 0.5f, 0.14f, L) }
                    2 -> { q(0f, y0 + 1.0f, z + 0.06f, a, 0.22f, 0.08f, E); q(0f, y0 - 0.8f, z + 0.06f, a, 0.22f, 0.08f, E); for (i in 0..2) q(-a * 0.6f + i * a * 0.6f, y0 - 1.9f, z + 0.1f, 0.18f, 0.18f, 0.06f, 0xFFE8DCC0.toInt()) }
                    3 -> { q(0f, y0 + 1.0f, z + 0.06f, a * 0.8f, 1.4f, 0.12f, L); for (i in 0..2) q(-a * 0.6f + i * a * 0.6f, y0 + 1.0f, z + 0.2f, 0.2f, 0.2f, 0.06f, OD); q(0f, y0 - 1.6f, z + 0.06f, a * 0.5f, 0.3f, 0.1f, E) }
                    4 -> { for (i in 0..2) q(0f, y0 + 1.8f - i * 1.6f, z + 0.08f, a * 0.7f, 0.2f, 0.12f, L); q(0f, y0 + 0.2f, z + 0.06f, 0.18f, 2.4f, 0.1f, B) }
                    else -> { val rd = 0xFFB52B2B.toInt(); for (i in -2..2) q(i * (a / 2.4f), y0, z + 0.06f, a / 2.4f - 0.05f, 2.2f, 0.06f, if (i % 2 == 0) rd else 0xFF1F5A2E.toInt()); q(0f, y0 + 2.3f, z + 0.1f, a, 0.28f, 0.1f, E) }
                }
                Classe.MAGO -> when (k) {
                    1 -> { q(0f, y0 - 2.4f, z + 0.06f, a, 0.4f, 0.08f, O); q(0f, y0 + 0.4f, z + 0.04f, 0.1f, 2.0f, 0.05f, O) }
                    2 -> { q(0f, y0 - 2.2f, z + 0.06f, a, 0.35f, 0.08f, E); for (i in 0..2) q(0f, y0 + 1.2f - i * 1.1f, z + 0.06f, 0.18f, 0.18f, 0.05f, E, true) }
                    3 -> { q(0f, y0 + 1.6f, z + 0.06f, a, 0.28f, 0.08f, OD); q(0f, y0 - 1.8f, z + 0.06f, a, 0.28f, 0.08f, OD); q(0f, y0, z + 0.1f, 0.5f, 0.5f, 0.06f, 0xFF9A5BFF.toInt(), true) }
                    4 -> { for (i in 0..2) q((i - 1) * a * 0.6f, y0 - 2.0f + i % 2 * 0.4f, z + 0.06f, 0.4f, 0.8f, 0.06f, 0xFFFF9A2A.toInt(), true); q(0f, y0 + 2.2f, z + 0.06f, a, 0.3f, 0.08f, 0xFF7E200F.toInt()) }
                    else -> { for (i in 0..3) q(if (i % 2 == 0) -a * 0.45f else a * 0.45f, y0 + 1.8f - i * 1.2f, z + 0.06f, 0.5f, 0.4f, 0.06f, if (i % 2 == 0) B else L); q(0f, y0 + 0.2f, z + 0.04f, 0.08f, 2.6f, 0.04f, 0xFF6B4A2A.toInt()) }
                }
                Classe.ARQUEIRO -> when (k) {
                    1 -> { q(0f, y0 + 1.6f, z + 0.06f, a, 0.28f, 0.08f, E); q(0f, y0 - 1.4f, z + 0.06f, a, 0.28f, 0.08f, E); q(a * 0.5f, y0, z + 0.14f, 0.7f, 1.1f, 0.14f, B) }
                    2 -> { q(0f, y0, z + 0.04f, 0.16f, 2.6f, 0.05f, E); for (i in 0..2) q(0.5f, y0 + 1.6f - i * 1.3f, z + 0.06f, 0.35f, 0.2f, 0.05f, E) }
                    3 -> { q(0f, y0 + 1.8f, z + 0.06f, a, 0.22f, 0.08f, L); q(0f, y0 - 0.8f, z + 0.06f, a, 0.22f, 0.08f, L); q(-a * 0.5f, y0 + 0.5f, z + 0.14f, 0.2f, 0.7f, 0.06f, 0xFFB8B8C8.toInt()) }
                    4 -> { for (i in 0..2) q(0f, y0 + 1.8f - i * 1.5f, z + 0.08f, a * (0.9f - i * 0.1f), 0.6f, 0.1f, if (i % 2 == 0) L else B); q(0f, y0 - 2.4f, z + 0.12f, a, 0.22f, 0.08f, O) }
                    else -> { q(0f, y0 - 2.4f, z + 0.1f, a + 0.1f, 0.5f, 0.2f, L); q(0f, y0 + 2.2f, z + 0.06f, a, 0.25f, 0.1f, E); for (i in 0..1) q(0f, y0 + 0.6f - i * 1.3f, z + 0.06f, 0.3f, 0.18f, 0.06f, 0xFFE8DCC0.toInt()) }
                }
                Classe.NINJA, Classe.PIKACHU, Classe.ICHIGO, Classe.LUFFY -> when (k) {
                    1 -> { for (i in 0..3) q(0f, y0 + 2.2f - i * 1.1f, z + 0.05f, a, 0.1f, 0.05f, L); q(a * 0.6f, y0 + 1.0f, z + 0.14f, 0.5f, 0.9f, 0.14f, E) }
                    2 -> { q(0f, y0 - 2.4f, z + 0.06f, a, 0.4f, 0.08f, O); q(0f, y0 + 0.4f, z + 0.04f, 0.1f, 2.0f, 0.05f, O) }
                    3 -> { for (i in 0..3) q(0f, y0 + 2.0f - i * 1.3f, z + 0.06f, a, 0.5f, 0.08f, if (i % 2 == 0) B else L); q(0f, y0 - 2.4f, z + 0.1f, a + 0.05f, 0.3f, 0.1f, 0xFFBFEFFF.toInt()) }
                    4 -> { for (i in 0..3) q(0f, y0 + 2.0f - i * 1.3f, z + 0.06f, a, 0.55f, 0.1f, if (i % 2 == 0) L else B); q(0f, y0 + 0.2f, z + 0.16f, 0.1f, 2.6f, 0.05f, O) }
                    else -> { q(0f, y0 + 0.2f, z + 0.04f, 0.14f, 2.6f, 0.05f, O); q(0f, y0 - 2.5f, z + 0.1f, a, 0.4f, 0.08f, O); q(a * 0.55f, y0 + 1.6f, z + 0.1f, 0.3f, 0.3f, 0.06f, 0xFFB5242E.toInt(), true) }
                }
            }
        } else {
            when (c) {
                Classe.GUERREIRO -> when (k) {
                    1 -> { q(0f, y0 + 2.2f, z + 0.2f, a, 0.9f, 0.3f, L); q(0f, y0 + 2.2f, z + 0.5f, 0.3f, 0.3f, 0.1f, O); q(0f, y0 - 0.3f, z + 0.06f, 0.2f, 2.2f, 0.06f, E); q(0f, y0 - 2.6f, z + 0.1f, a, 0.3f, 0.1f, O) }
                    2 -> { for (i in 0..3) q(0f, y0 + 2.0f - i * 1.3f, z + 0.1f, a, 0.22f, 0.1f, E); q(0f, y0 - 2.6f, z + 0.1f, a + 0.1f, 0.4f, 0.12f, L) }
                    3 -> { for (i in 0..5) q(0f, y0 + 2.4f - i * 0.85f, z + 0.05f, a - 0.1f, 0.12f, 0.06f, if (i % 2 == 0) E else L); q(0f, y0 + 2.6f, z + 0.12f, a, 0.4f, 0.1f, O) }
                    4 -> { q(0f, y0 - 2.4f, z + 0.12f, a + 0.1f, 0.7f, 0.2f, 0xFF3A2A1A.toInt()); q(0f, y0 + 2.4f, z + 0.08f, a, 0.3f, 0.1f, O); q(0f, y0 - 0.2f, z + 0.04f, 0.18f, 2.0f, 0.05f, O) }
                    else -> { q(0f, y0 + 1.4f, z + 0.1f, a, 0.28f, 0.12f, E); q(0f, y0 - 0.2f, z + 0.1f, a, 0.28f, 0.12f, E); q(0f, y0 - 2.4f, z + 0.12f, a + 0.15f, 0.7f, 0.2f, L) }
                }
                Classe.BARBARO -> when (k) {
                    1 -> { q(0f, y0 + 2.4f, z + 0.1f, a + 0.2f, 0.6f, 0.2f, L); for (i in -1..1) q(i * a * 0.6f, y0 - 2.5f, z + 0.12f, 0.5f, 0.7f, 0.2f, if (i % 2 == 0) B else L) }
                    2 -> { for (i in 0..2) q(0f, y0 + 1.8f - i * 1.6f, z + 0.08f, a, 0.2f, 0.1f, E); q(0f, y0 - 2.6f, z + 0.08f, a, 0.3f, 0.1f, 0xFFE8DCC0.toInt()) }
                    3 -> { q(0f, y0 + 0.8f, z + 0.1f, a - 0.1f, 1.8f, 0.14f, L); q(0f, y0 + 2.0f, z + 0.26f, 0.8f, 0.7f, 0.1f, B); q(0f, y0 - 2.4f, z + 0.1f, a, 0.4f, 0.12f, E) }
                    4 -> { q(0f, y0 + 2.0f, z + 0.14f, 0.9f, 0.9f, 0.2f, L); q(0f, y0 - 0.4f, z + 0.06f, 0.18f, 2.0f, 0.08f, L); for (i in 0..2) q(0f, y0 - 0.4f - i * 0.9f + 0.9f, z + 0.14f, 0.7f, 0.14f, 0.08f, B) }
                    else -> { val rd = 0xFFB52B2B.toInt(); for (i in 0..3) q(0f, y0 + 2.2f - i * 1.4f, z + 0.06f, a, 0.14f, 0.08f, if (i % 2 == 0) rd else 0xFF1F5A2E.toInt()); q(0f, y0 - 2.5f, z + 0.1f, a + 0.1f, 0.4f, 0.12f, E) }
                }
                Classe.MAGO -> when (k) {
                    1 -> { q(0f, y0 - 2.4f, z + 0.06f, a, 0.3f, 0.08f, O); q(0f, y0 + 0.4f, z + 0.04f, 0.1f, 1.8f, 0.05f, O) }
                    2 -> { q(0f, y0 - 2.4f, z + 0.06f, a, 0.3f, 0.08f, E); q(0f, y0 + 1.4f, z + 0.06f, a, 0.2f, 0.08f, E) }
                    3 -> { q(0f, y0 + 1.4f, z + 0.08f, a, 0.28f, 0.1f, OD); q(0f, y0 - 1.6f, z + 0.08f, a, 0.28f, 0.1f, OD); q(0f, y0 - 0.1f, z + 0.1f, 0.4f, 0.4f, 0.05f, 0xFF9A5BFF.toInt(), true) }
                    4 -> { q(0f, y0 - 2.5f, z + 0.1f, a + 0.1f, 0.5f, 0.2f, 0xFF7E200F.toInt()); q(0f, y0 - 2.4f, z + 0.3f, 0.3f, 0.3f, 0.06f, 0xFFFF9A2A.toInt(), true) }
                    else -> { for (i in 0..3) q(if (i % 2 == 0) -a * 0.4f else a * 0.4f, y0 + 1.8f - i * 1.2f, z + 0.06f, 0.5f, 0.4f, 0.06f, if (i % 2 == 0) B else L); q(0f, y0 - 2.6f, z + 0.1f, a, 0.3f, 0.1f, 0xFF6B4A2A.toInt()) }
                }
                Classe.ARQUEIRO -> when (k) {
                    1 -> { q(0f, y0 + 2.4f, z + 0.1f, a + 0.15f, 0.7f, 0.2f, L); q(0f, y0 - 0.2f, z + 0.06f, 0.14f, 1.8f, 0.05f, E) }
                    2 -> { q(0f, y0 - 2.4f, z + 0.08f, a, 0.4f, 0.1f, E); q(0f, y0 + 0.4f, z + 0.04f, 0.12f, 2.0f, 0.05f, E) }
                    3 -> { q(0f, y0 + 1.4f, z + 0.08f, a, 0.28f, 0.1f, L); q(0f, y0 - 0.4f, z + 0.08f, a, 0.28f, 0.1f, L); q(0f, y0 - 2.2f, z + 0.1f, a + 0.05f, 0.6f, 0.12f, E) }
                    4 -> { q(0f, y0 + 1.6f, z + 0.12f, a, 1.0f, 0.14f, L); q(0f, y0 + 1.6f, z + 0.24f, 0.4f, 0.4f, 0.06f, 0xFF59E0A0.toInt(), true); q(0f, y0 - 1.4f, z + 0.1f, a, 0.9f, 0.12f, B) }
                    else -> { q(0f, y0 + 2.4f, z + 0.1f, a + 0.2f, 0.8f, 0.22f, L); q(0f, y0 - 2.4f, z + 0.1f, a + 0.15f, 0.6f, 0.2f, L) }
                }
                Classe.NINJA, Classe.PIKACHU, Classe.ICHIGO, Classe.LUFFY -> when (k) {
                    1 -> { for (i in 0..4) q(0f, y0 + 2.2f - i * 1.0f, z + 0.05f, a, 0.1f, 0.05f, L); q(0f, y0 - 0.4f, z + 0.1f, 0.6f, 1.2f, 0.08f, E) }
                    2 -> { q(0f, y0 - 2.5f, z + 0.08f, a, 0.4f, 0.1f, O); q(0f, y0 + 0.4f, z + 0.04f, 0.1f, 2.0f, 0.05f, O) }
                    3 -> { for (i in 0..3) q(0f, y0 + 2.0f - i * 1.3f, z + 0.06f, a, 0.5f, 0.08f, if (i % 2 == 0) B else L); q(0f, y0 - 2.5f, z + 0.1f, a + 0.05f, 0.3f, 0.1f, 0xFFBFEFFF.toInt()) }
                    4 -> { q(0f, y0 + 1.2f, z + 0.14f, a, 1.4f, 0.12f, L); q(0f, y0 + 1.2f, z + 0.26f, 0.5f, 0.5f, 0.06f, O, true); q(0f, y0 - 1.6f, z + 0.1f, a, 0.9f, 0.1f, B) }
                    else -> { q(0f, y0 - 2.5f, z + 0.1f, a, 0.4f, 0.1f, O); q(0f, y0 + 1.6f, z + 0.1f, a, 0.28f, 0.1f, O); q(0f, y0 - 0.2f, z + 0.04f, 0.12f, 2.2f, 0.05f, OD) }
                }
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
        Classe.NINJA, Classe.PIKACHU, Classe.ICHIGO, Classe.LUFFY -> 0.88f
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

    // ───────────── corpo (tipo de cada raça, masculino e feminino) ─────────────

    /** Quando true, o modelo não desenha as peças originais (a roupa escolhida ocupa o lugar delas). */
    @JvmField var ocultar: Boolean = false

    /**
     * Tipo de corpo: escalas aplicadas por região. ombros = largura do peito e distância dos ombros, peitoZ = profundidade do peito,
     * cintura/cinturaZ = parte de baixo do tronco, quadril = afastamento das pernas, coxa = grossura das pernas,
     * braco = grossura dos braços, cabeca = tamanho da cabeça.
     */
    private class Corpo(
        val nome: String, val ombros: Float, val peitoZ: Float, val cintura: Float, val cinturaZ: Float,
        val quadril: Float, val coxa: Float, val braco: Float, val cabeca: Float
    )

    private fun base(r: Raca): Corpo = when (r) {
        Raca.HUMANO -> Corpo("Humano (proporções médias)", 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f)
        Raca.ELFO -> Corpo("Elfo esguio (alto e leve)", 0.92f, 0.92f, 0.88f, 0.9f, 0.97f, 0.92f, 0.86f, 1f)
        Raca.ANAO -> Corpo("Anão atarracado (largo e firme)", 1.1f, 1.1f, 1.12f, 1.1f, 1f, 1.18f, 1.2f, 1.05f)
        Raca.ORC -> Corpo("Orc brutamontes (ombros enormes)", 1.18f, 1.1f, 1.1f, 1.08f, 1.05f, 1.12f, 1.3f, 0.98f)
        Raca.TIEFLING -> Corpo("Tiefling esbelto (tronco em V)", 0.96f, 0.95f, 0.92f, 0.94f, 0.98f, 0.96f, 0.92f, 1f)
        Raca.DRACONATO -> Corpo("Draconato forte (peito de escamas)", 1.12f, 1.08f, 1.05f, 1.05f, 1.06f, 1.1f, 1.18f, 1f)
        Raca.GOLIAS -> Corpo("Golias colossal (músculos de pedra)", 1.16f, 1.12f, 1.1f, 1.08f, 1.08f, 1.12f, 1.25f, 0.97f)
        Raca.FELINO -> Corpo("Felino ágil (magro e flexível)", 0.9f, 0.88f, 0.82f, 0.86f, 0.95f, 0.9f, 0.85f, 1f)
        Raca.GOBLIN -> Corpo("Goblin magricela (cabeça grande)", 0.88f, 0.86f, 0.84f, 0.86f, 0.92f, 0.82f, 0.8f, 1.12f)
        Raca.MEIOELFO -> Corpo("Meio-elfo (esbelto e atlético)", 0.96f, 0.96f, 0.94f, 0.95f, 1f, 0.96f, 0.93f, 1f)
        Raca.GNOMO -> Corpo("Gnomo miúdo (cabeça grande)", 0.9f, 0.95f, 0.95f, 0.95f, 0.94f, 0.9f, 0.85f, 1.12f)
        Raca.HALFLING -> Corpo("Halfling roliço (pequeno e ligeiro)", 0.94f, 1f, 1.02f, 1f, 0.96f, 1f, 0.92f, 1.08f)
        Raca.AASIMAR -> Corpo("Aasimar nobre (porte elegante)", 1f, 0.98f, 0.94f, 0.96f, 1f, 0.98f, 0.96f, 1f)
    }

    private fun corpoDe(r: Raca, fem: Boolean): Corpo {
        val b = base(r)
        if (!fem) return b
        return Corpo(
            b.nome.replace("Elfo", "Elfa").replace("Anão", "Anã").replace("Orc ", "Orc ").replace("Tiefling", "Tiefling")
                .replace("Draconato", "Draconata").replace("Golias", "Golias").replace("Felino", "Felina")
                .replace("Goblin", "Goblin").replace("Meio-elfo", "Meio-elfa").replace("Gnomo", "Gnoma")
                .replace("Aasimar", "Aasimar").replace("Humano", "Humana").replace("atarracado", "atarracada")
                .replace("esguio", "esguia").replace("brutamontes", "robusta").replace("esbelto", "esbelta")
                .replace("forte", "forte").replace("colossal", "colossal").replace("ágil", "ágil")
                .replace("magricela", "magricela").replace("miúdo", "miúda").replace("roliço", "roliça")
                .replace("nobre", "nobre").replace("atlético", "atlética"),
            b.ombros * 0.93f, b.peitoZ * 0.96f, b.cintura * 0.88f, b.cinturaZ * 0.92f,
            b.quadril * 1.06f, b.coxa * 1.05f, b.braco * 0.88f, b.cabeca * 0.98f
        )
    }

    /** O corpo agora depende da raça escolhida (a classe só decide as roupas e o modelo). */
    private fun corpo(@Suppress("UNUSED_PARAMETER") c: Classe): Corpo = corpoDe(HeroLook.raca, HeroLook.fem)

    /** Nome do tipo de corpo (mostrado na criação). */
    fun tipoCorpo(r: Raca, fem: Boolean): String = corpoDe(r, fem).nome

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
        pernaUnica(g, c, k, l.th, l.tzF, false, -2.7f, t)
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
        pernaUnica(g, c, k, l.sh, l.szF, true, l.sy, t)
    }
}
