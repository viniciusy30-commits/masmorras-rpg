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

    fun aplicar(p: HeroiPerfil) {
        raca = p.raca
        skinC = p.skin
        hairC = p.hair
        eyeC = p.eye
        escala = p.raca.escala
    }

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
        e.apply()
    }
}
