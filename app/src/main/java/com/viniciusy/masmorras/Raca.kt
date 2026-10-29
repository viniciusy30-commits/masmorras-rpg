package com.viniciusy.masmorras

/** Os 6 atributos, no estilo de D&D. */
enum class Atrib(val sigla: String, val nome: String, val efeito: String) {
    FOR("FOR", "Força", "dano corpo a corpo"),
    DES("DES", "Destreza", "velocidade, dano do arqueiro e crítico"),
    CON("CON", "Constituição", "vida e defesa"),
    INT("INT", "Inteligência", "dano e mana do mago"),
    SAB("SAB", "Sabedoria", "regeneração de vida e mana"),
    CAR("CAR", "Carisma", "força do golpe crítico")
}

/**
 * As 9 raças. bonus = FOR, DES, CON, INT, SAB, CAR.
 * orelha: 0 nenhuma, 1 élfica longa, 2 felina, 3 grande de goblin, 4 pontuda curta.
 * chifre: 0 nenhum, 1 curvo (tiefling), 2 para trás (draconato).
 * focinho: 0 nenhum, 1 felino, 2 de dragão. cauda: 0 nenhuma, 1 de diabo, 2 de dragão, 3 de gato.
 * escala e o tamanho só visual (a colisão não muda).
 */
enum class Raca(
    val nome: String,
    val descricao: String,
    val bonus: IntArray,
    val orelha: Int,
    val chifre: Int,
    val presas: Boolean,
    val focinho: Int,
    val cauda: Int,
    val escala: Float,
    val pele: Int?,
    val velMul: Float,
    val hpMul: Float,
    val regen: Float,
    val crit: Float
) {
    HUMANO("Humano", "Versátil: +1 em todos os atributos.", intArrayOf(1, 1, 1, 1, 1, 1), 0, 0, false, 0, 0, 1f, null, 1f, 1f, 0f, 0f),
    ELFO("Elfo", "Ágil e perspicaz, com orelhas longas. +2 Destreza, +1 Inteligência.", intArrayOf(0, 2, 0, 1, 0, 0), 1, 0, false, 0, 0, 1f, null, 1f, 1f, 0f, 0f),
    ANAO("Anão", "Robusto e teimoso. +2 Constituição, +1 Força, anda um pouco mais devagar.", intArrayOf(1, 0, 2, 0, 0, 0), 0, 0, false, 0, 0, 1f, null, 0.96f, 1f, 0f, 0f),
    ORC("Orc", "Brutal, de pele verde e presas. +2 Força, +1 Constituição, -1 Inteligência.", intArrayOf(2, 0, 1, -1, 0, 0), 4, 0, true, 0, 0, 1f, 0xFF7CA855.toInt(), 1f, 1f, 0f, 0f),
    TIEFLING("Tiefling", "Sangue infernal: chifres e cauda. +2 Carisma, +1 Inteligência.", intArrayOf(0, 0, 0, 1, 0, 2), 4, 1, false, 0, 1, 1f, 0xFFB5463F.toInt(), 1f, 1f, 0f, 0f),
    DRACONATO("Draconato", "Herdeiro dos dragões: focinho, chifres e cauda. +2 Força, +1 Carisma.", intArrayOf(2, 0, 0, 0, 0, 1), 0, 2, false, 2, 2, 1f, 0xFF3F8F5B.toInt(), 1f, 1f, 0f, 0f),
    GOLIAS("Golias", "Gigante das montanhas, bem maior. +2 Força, +1 Constituição, um pouco mais lento.", intArrayOf(2, 0, 1, 0, 0, 0), 0, 0, false, 0, 0, 1.18f, 0xFF8E9FB4.toInt(), 0.98f, 1f, 0f, 0f),
    FELINO("Felino", "Caçador de orelhas, focinho e cauda de gato. +2 Destreza, +1 Carisma, rápido.", intArrayOf(0, 2, 0, 0, 0, 1), 2, 0, true, 1, 3, 1f, 0xFFD9A066.toInt(), 1f, 1f, 0f, 0.02f),
    GOBLIN("Goblin", "Pequeno e esperto, com orelhas grandes e presas. +2 Destreza, +1 Constituição.", intArrayOf(0, 2, 1, 0, 0, 0), 3, 0, true, 0, 0, 0.86f, 0xFF8DB04A.toInt(), 1.05f, 0.95f, 0f, 0f),
    MEIOELFO("Meio-elfo", "Entre dois mundos, de orelhas curtas e muito carisma. +2 Carisma, +1 Destreza, +1 Constituição.", intArrayOf(0, 1, 1, 0, 0, 2), 4, 0, false, 0, 0, 1f, null, 1f, 1f, 0f, 0f),
    GNOMO("Gnomo", "Pequeno, curioso e inventivo. +2 Inteligência, +1 Constituição.", intArrayOf(0, 0, 1, 2, 0, 0), 4, 0, false, 0, 0, 0.8f, null, 1f, 0.95f, 0f, 0f),
    HALFLING("Halfling", "Pequeno, sortudo e ligeiro. +2 Destreza, +1 Carisma, um pouco mais rápido.", intArrayOf(0, 2, 0, 0, 0, 1), 4, 0, false, 0, 0, 0.82f, null, 1.04f, 0.95f, 0f, 0.02f),
    AASIMAR("Aasimar", "Toque celestial: pele dourada e cura lenta. +2 Carisma, +1 Sabedoria.", intArrayOf(0, 0, 0, 0, 1, 2), 0, 0, false, 0, 0, 1f, 0xFFF2D58A.toInt(), 1f, 1f, 0.4f, 0f);

    fun bonusDe(a: Atrib): Int = bonus[a.ordinal]
}

/** Soma dos pontos gastos (compra de pontos: notas de 8 a 15, 27 pontos no total). */
object CompraPontos {
    const val TOTAL = 27
    const val MIN = 8
    const val MAX = 15

    fun custo(nota: Int): Int = when (nota.coerceIn(MIN, MAX)) {
        8 -> 0
        9 -> 1
        10 -> 2
        11 -> 3
        12 -> 4
        13 -> 5
        14 -> 7
        else -> 9
    }

    fun mod(nota: Int): Int = Math.floorDiv(nota - 10, 2)
}

/** O herói de uma classe: raça, atributos e aparência. Guardado por classe. */
class HeroiPerfil(val classe: Classe) {
    var raca: Raca = racaPadrao(classe)
    val notas: IntArray = notasPadrao(classe).clone()
    var skin: Int? = null
    var hair: Int? = null
    var eye: Int? = null
    var cicatriz: Int = 0
    var pintura: Int = 0
    var acess: Int = 0
    var altura: Int = 2
    var cabelo: Int = 0
    var barba: Int = 0
    var elmo: Int = 0      // 0 = usa o capacete/chapéu/máscara, 1 = sem
    var topo: Int = 0
    var ombro: Int = 0     // 0 = ombreira original, 1 = sem, 2..6 = estilos da classe
    var metal: Int? = null
    var tecido: Int? = null
    var sexo: Int = 0      // 0 = masculino, 1 = feminino
    var peito: Int = 0
    var pernas: Int = 0
    var capa: Int = 0
    var busto: Int = 1     // 0 pequeno, 1 médio, 2 grande (corpo feminino)

    fun nota(a: Atrib): Int = notas[a.ordinal]
    fun total(a: Atrib): Int = notas[a.ordinal] + raca.bonusDe(a)
    fun gastos(): Int = notas.sumOf { CompraPontos.custo(it) }
    fun livres(): Int = CompraPontos.TOTAL - gastos()

    fun pode(a: Atrib, delta: Int): Boolean {
        val n = notas[a.ordinal] + delta
        if (n < CompraPontos.MIN || n > CompraPontos.MAX) return false
        val novoGasto = gastos() - CompraPontos.custo(notas[a.ordinal]) + CompraPontos.custo(n)
        return novoGasto <= CompraPontos.TOTAL
    }

    fun mudar(a: Atrib, delta: Int): Boolean {
        if (!pode(a, delta)) return false
        notas[a.ordinal] += delta
        return true
    }

    fun copia(): HeroiPerfil {
        val p = HeroiPerfil(classe)
        p.raca = raca
        for (i in notas.indices) p.notas[i] = notas[i]
        p.skin = skin
        p.hair = hair
        p.eye = eye
        p.cicatriz = cicatriz
        p.pintura = pintura
        p.acess = acess
        p.altura = altura
        p.cabelo = cabelo
        p.barba = barba
        p.elmo = elmo
        p.topo = topo
        p.ombro = ombro
        p.metal = metal
        p.tecido = tecido
        p.sexo = sexo
        p.peito = peito
        p.pernas = pernas
        p.capa = capa
        p.busto = busto
        return p
    }

    fun copiarDe(o: HeroiPerfil) {
        raca = o.raca
        for (i in notas.indices) notas[i] = o.notas[i]
        skin = o.skin
        hair = o.hair
        eye = o.eye
        cicatriz = o.cicatriz
        pintura = o.pintura
        acess = o.acess
        altura = o.altura
        cabelo = o.cabelo
        barba = o.barba
        elmo = o.elmo
        topo = o.topo
        ombro = o.ombro
        metal = o.metal
        tecido = o.tecido
        sexo = o.sexo
        peito = o.peito
        pernas = o.pernas
        capa = o.capa
        busto = o.busto
    }

    // ── conta final: tudo e relativo ao herói padrão da classe, então o padrão dá os mesmos números de antes ──
    private fun d(a: Atrib, ref: HeroiPerfil): Int = CompraPontos.mod(total(a)) - CompraPontos.mod(ref.total(a))

    private val ref: HeroiPerfil get() = if (isPadrao) this else padrao(classe)
    private val isPadrao: Boolean get() = this === padraoCache[classe]

    private fun primario(): Atrib = when (classe) {
        Classe.MAGO -> Atrib.INT
        Classe.ARQUEIRO -> Atrib.DES
        Classe.NINJA, Classe.PIKACHU -> Atrib.DES
        else -> Atrib.FOR
    }

    val maxHp: Float
        get() {
            val r = ref
            val v = classe.maxHp * (1f + 0.07f * d(Atrib.CON, r)) * (raca.hpMul / r.raca.hpMul)
            return v.coerceAtLeast(classe.maxHp * 0.4f)
        }

    val maxMp: Float
        get() {
            if (classe.maxMp <= 0f) return 0f
            val v = classe.maxMp * (1f + 0.08f * d(Atrib.INT, ref))
            return v.coerceAtLeast(classe.maxMp * 0.4f)
        }

    val velocidade: Float
        get() {
            val r = ref
            val v = classe.velocidade * (1f + 0.03f * d(Atrib.DES, r)) * (raca.velMul / r.raca.velMul)
            return v.coerceIn(classe.velocidade * 0.6f, classe.velocidade * 1.5f)
        }

    val dano: Float
        get() {
            val v = classe.danoBase * (1f + 0.06f * d(primario(), ref))
            return v.coerceAtLeast(classe.danoBase * 0.4f)
        }

    val defesa: Float
        get() {
            val r = ref
            val v = 0.025f * d(Atrib.CON, r) + 0.01f * d(Atrib.FOR, r)
            return v.coerceIn(-0.15f, 0.45f)
        }

    val regenHp: Float
        get() {
            val r = ref
            return (0.35f * d(Atrib.SAB, r) + (raca.regen - r.raca.regen)).coerceAtLeast(0f)
        }

    val regenMp: Float
        get() = (8f * (1f + 0.12f * d(Atrib.SAB, ref))).coerceAtLeast(2f)

    val critChance: Float
        get() {
            val r = ref
            return (0.02f * d(Atrib.DES, r) + 0.01f * d(Atrib.CAR, r) + (raca.crit - r.raca.crit)).coerceIn(0f, 0.6f)
        }

    val critMul: Float
        get() = (1.6f + 0.05f * d(Atrib.CAR, ref)).coerceIn(1.3f, 2.4f)

    companion object {
        const val N_CABELO = 37
        const val N_BARBA = 5
        const val N_TOPO = 5
        private val padraoCache = HashMap<Classe, HeroiPerfil>()

        fun racaPadrao(c: Classe): Raca = if (c == Classe.ARQUEIRO) Raca.ELFO else Raca.HUMANO

        /** Notas padrão (todas dão exatamente 27 pontos). */
        fun notasPadrao(c: Classe): IntArray = when (c) {
            //                   FOR DES CON INT SAB CAR
            Classe.GUERREIRO -> intArrayOf(15, 12, 14, 8, 12, 11)
            Classe.MAGO -> intArrayOf(8, 13, 12, 15, 13, 12)
            Classe.ARQUEIRO -> intArrayOf(10, 15, 13, 10, 12, 13)
            Classe.BARBARO -> intArrayOf(15, 12, 15, 8, 10, 11)
            Classe.NINJA, Classe.PIKACHU -> intArrayOf(11, 15, 12, 10, 13, 12)
            Classe.ICHIGO -> intArrayOf(15, 13, 13, 9, 12, 11)
        }

        fun padrao(c: Classe): HeroiPerfil = padraoCache.getOrPut(c) { HeroiPerfil(c) }
    }
}
