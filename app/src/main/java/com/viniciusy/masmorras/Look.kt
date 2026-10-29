package com.viniciusy.masmorras

import android.content.Context

/**
 * Aparência do herói que está em cena. Os 4 modelos 3D leem daqui a cor da pele, do cabelo e dos olhos.
 * Quando a cor não foi escolhida (null), o modelo usa a cor original dele.
 */
object HeroLook {
    var raca: Raca = Raca.HUMANO
    var skinC: Int? = null
    var hairC: Int? = null
    var eyeC: Int? = null
    var escala: Float = 1f
    var cicatriz: Int = 0
    var pintura: Int = 0
    var acess: Int = 0
    var cabelo: Int = 0       // 0 = padrão da classe, 1 = careca, 2.. = estilos novos
    var barba: Int = 0        // 0 = padrão da classe, 1 = sem barba, 2.. = estilos novos
    var elmo: Boolean = true  // capacete / chapéu / máscara da classe
    var topo: Int = 0         // acessório de cabeça: bandana, tiara, coroa, casquete
    var ombro: Boolean = true // ombreiras originais da classe (false = tirou ou usa estilo novo)
    var ombroK: Int = 0       // 0 = original, 1 = sem ombreiras, 2..6 = estilos novos da classe
    var metalC: Int? = null   // cor da armadura (metal)
    var tecidoC: Int? = null  // cor da roupa (tecido)
    var peito: Int = 0        // roupa do peito (0 = padrão da classe)
    var pernas: Int = 0       // roupa das pernas (0 = padrão da classe)
    var capa: Int = 0         // capa nas costas (0 = nenhuma)
    var busto: Int = 1        // 0 pequeno, 1 médio, 2 grande
    var fem: Boolean = false  // corpo e rosto femininos

    /** Fatores de altura do herói (Muito baixo ... Muito alto). */
    val ALTURAS = floatArrayOf(0.85f, 0.93f, 1f, 1.07f, 1.15f)

    fun aplicar(p: HeroiPerfil) {
        raca = p.raca
        skinC = p.skin
        hairC = p.hair
        eyeC = p.eye
        escala = p.raca.escala * ALTURAS[p.altura.coerceIn(0, ALTURAS.size - 1)]
        cicatriz = p.cicatriz
        pintura = p.pintura
        acess = p.acess
        cabelo = p.cabelo
        fem = p.sexo == 1
        barba = if (p.sexo == 1) 1 else p.barba
        elmo = p.elmo == 0
        topo = p.topo
        ombro = p.ombro == 0
        ombroK = p.ombro
        metalC = p.metal
        tecidoC = p.tecido
        peito = p.peito
        pernas = p.pernas
        capa = p.capa
        busto = p.busto
    }

    private fun lum(c: Int): Float =
        ((c shr 16) and 0xFF) * 0.30f + ((c shr 8) and 0xFF) * 0.59f + (c and 0xFF) * 0.11f

    private fun tingir(def: Int, cor: Int, ref: Float): Int {
        val k = (lum(def) / ref).coerceIn(0.35f, 1.5f)
        return mul(cor, k)
    }

    /** Peças de metal (armadura): mantém o brilho relativo de cada peça, só troca a cor. */
    fun metal(def: Int): Int = metalC?.let { tingir(def, it, 165f) } ?: def

    /** Peças de tecido (roupa, capa, túnica). */
    fun tecido(def: Int): Int = tecidoC?.let { tingir(def, it, 95f) } ?: def

    private fun peleBase(): Int? = skinC ?: raca.pele

    private fun mul(c: Int, k: Float): Int {
        val r = (((c shr 16) and 0xFF) * k).toInt().coerceIn(0, 255)
        val g = (((c shr 8) and 0xFF) * k).toInt().coerceIn(0, 255)
        val b = ((c and 0xFF) * k).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun skin(def: Int): Int = peleBase() ?: def
    fun skinL(def: Int): Int = peleBase()?.let { mul(it, 1.12f) } ?: def
    fun skinD(def: Int): Int = peleBase()?.let { mul(it, 0.81f) } ?: def
    fun skinDD(def: Int): Int = peleBase()?.let { mul(it, 0.62f) } ?: def

    fun hair(def: Int): Int = hairC ?: def
    fun hairL(def: Int): Int = hairC?.let { mul(it, 1.25f) } ?: def
    fun hairD(def: Int): Int = hairC?.let { mul(it, 0.72f) } ?: def

    fun eye(def: Int): Int = eyeC ?: def

    /** O arqueiro já tem orelhas élficas no modelo: só as mostra quando a raça é élfica. */
    fun orelhaElfica(): Boolean = raca.orelha == 1
}

/** Guarda e entrega o herói de cada classe (salvo no aparelho). */
object Herois {
    private var ctx: Context? = null
    private val perfis = HashMap<Classe, HeroiPerfil>()

    fun init(c: Context) {
        ctx = c.applicationContext
        perfis.clear()
    }

    fun perfil(classe: Classe): HeroiPerfil = perfis.getOrPut(classe) { carregar(classe) }

    fun ativar(p: HeroiPerfil) = HeroLook.aplicar(p)

    private fun prefs(classe: Classe) =
        ctx?.getSharedPreferences("heroi_" + classe.name, Context.MODE_PRIVATE)

    private fun carregar(classe: Classe): HeroiPerfil {
        // Sem customização: todo herói usa o visual e os atributos padrão da classe (ignora o que foi salvo antes).
        if (true) return HeroiPerfil(classe)
        val p = HeroiPerfil(classe)
        val sp = prefs(classe) ?: return p
        try {
            val r = sp.getString("raca", null)
            if (r != null) p.raca = Raca.values().firstOrNull { it.name == r } ?: p.raca
            val notas = sp.getString("notas", null)?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
            if (notas != null && notas.size == p.notas.size) {
                val ok = notas.all { it in CompraPontos.MIN..CompraPontos.MAX }
                val gasto = notas.sumOf { CompraPontos.custo(it) }
                if (ok && gasto <= CompraPontos.TOTAL) {
                    for (i in notas.indices) p.notas[i] = notas[i]
                }
            }
            p.skin = if (sp.contains("skin")) sp.getInt("skin", 0) else null
            p.hair = if (sp.contains("hair")) sp.getInt("hair", 0) else null
            p.eye = if (sp.contains("eye")) sp.getInt("eye", 0) else null
            p.cicatriz = sp.getInt("cicatriz", 0).coerceIn(0, 4)
            p.pintura = sp.getInt("pintura", 0).coerceIn(0, 4)
            p.acess = sp.getInt("acess", 0).coerceIn(0, 6)
            p.altura = sp.getInt("altura", 2).coerceIn(0, 4)
            p.cabelo = sp.getInt("cabelo", 0).coerceIn(0, HeroiPerfil.N_CABELO - 1)
            p.barba = sp.getInt("barba", 0).coerceIn(0, HeroiPerfil.N_BARBA - 1)
            p.elmo = sp.getInt("elmo", 0).coerceIn(0, 1)
            p.topo = sp.getInt("topo", 0).coerceIn(0, HeroiPerfil.N_TOPO - 1)
            p.ombro = sp.getInt("ombro", 0).coerceIn(0, Roupas.NO - 1)
            p.metal = if (sp.contains("metal")) sp.getInt("metal", 0) else null
            p.tecido = if (sp.contains("tecido")) sp.getInt("tecido", 0) else null
            p.sexo = sp.getInt("sexo", 0).coerceIn(0, 1)
            p.peito = sp.getInt("peito", 0).coerceIn(0, Roupas.N - 1)
            p.pernas = sp.getInt("pernas", 0).coerceIn(0, Roupas.N - 1)
            p.capa = sp.getInt("capa", 0).coerceIn(0, Roupas.N - 1)
            p.busto = sp.getInt("busto", 1).coerceIn(0, 2)
        } catch (e: Exception) {
            // dados antigos ou corrompidos: volta ao herói padrão
            return HeroiPerfil(classe)
        }
        return p
    }

    fun salvar(p: HeroiPerfil) {
        perfis[p.classe] = p
        val sp = prefs(p.classe) ?: return
        val e = sp.edit()
        e.putString("raca", p.raca.name)
        e.putString("notas", p.notas.joinToString(","))
        val s = p.skin
        if (s != null) e.putInt("skin", s) else e.remove("skin")
        val h = p.hair
        if (h != null) e.putInt("hair", h) else e.remove("hair")
        val y = p.eye
        if (y != null) e.putInt("eye", y) else e.remove("eye")
        e.putInt("cicatriz", p.cicatriz)
        e.putInt("pintura", p.pintura)
        e.putInt("acess", p.acess)
        e.putInt("altura", p.altura)
        e.putInt("cabelo", p.cabelo)
        e.putInt("barba", p.barba)
        e.putInt("elmo", p.elmo)
        e.putInt("topo", p.topo)
        e.putInt("ombro", p.ombro)
        val m = p.metal
        if (m != null) e.putInt("metal", m) else e.remove("metal")
        val t = p.tecido
        if (t != null) e.putInt("tecido", t) else e.remove("tecido")
        e.putInt("sexo", p.sexo)
        e.putInt("peito", p.peito)
        e.putInt("pernas", p.pernas)
        e.putInt("capa", p.capa)
        e.putInt("busto", p.busto)
        e.apply()
    }
}
