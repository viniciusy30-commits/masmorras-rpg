package com.viniciusy.masmorras

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** Os 3 ramos da árvore de Sylvarion. Cada um é um estilo de jogo; dá para misturar. */
enum class Ramo(val nome: String, val papel: String, val cor: Int, val faixa: Int) {
    LAMINA("Lâmina do Vendaval", "DANO • burst e críticos", 0xFFFF8A65.toInt(), 0),
    BRISA("Dançarino das Brisas", "AGILIDADE • manter distância", 0xFF69F0AE.toInt(), 1),
    OLHO("Olho do Furacão", "CONTROLE • tornados e zonas", 0xFF82A8FF.toInt(), 2)
}

/** Como a skill escolhe onde acerta. */
enum class Alvo { INIMIGO, AREA, DIRECAO, SELF, PASSIVA }

/**
 * Um nó da árvore. [tier] é a coluna (0 a 4) e [lado] a posição na faixa: -1 em cima (passivas),
 * 0 no centro (raiz e ultimate) e 1 embaixo (ativas).
 */
class Talento(
    val id: String,
    val nome: String,
    val ramo: Ramo,
    val tier: Int,
    val lado: Int,
    val ativa: Boolean,
    val alvo: Alvo,
    val nivelMin: Int,
    val pais: List<String>,
    val cdMs: Long,
    val alcance: Float,
    val raio: Float,
    val desc: String,
    val porGrau: String,
    val estilo: Int,
    val ultimate: Boolean = false
) {
    fun tipoTxt(): String = when (alvo) {
        Alvo.INIMIGO -> "Ativa • Alvo"
        Alvo.AREA -> "Ativa • Área mirada"
        Alvo.DIRECAO -> "Ativa • Direção"
        Alvo.SELF -> "Ativa • Self"
        Alvo.PASSIVA -> "Passiva"
    }
}

object SylvCatalog {
    const val NIVEL_MAX = 30
    const val GRAU_MAX = 3

    private fun t(
        id: String, nome: String, ramo: Ramo, tier: Int, lado: Int, alvo: Alvo, nv: Int, pais: List<String>,
        cd: Long, alc: Float, raio: Float, estilo: Int, desc: String, porGrau: String, ult: Boolean = false
    ) = Talento(id, nome, ramo, tier, lado, alvo != Alvo.PASSIVA, alvo, nv, pais, cd, alc, raio, desc, porGrau, estilo, ult)

    val todos: List<Talento> = listOf(
        // ───────── LÂMINA DO VENDAVAL (dano) ─────────
        t("ciclonica", "Flecha Ciclônica", Ramo.LAMINA, 0, 0, Alvo.INIMIGO, 1, listOf(), 1200L, 240f, 0f, 0,
            "Flecha em espiral que vai direto no alvo. Dano alto e gera Ímpeto.", "+15% de dano por grau"),
        t("fio", "Fio Afiado", Ramo.LAMINA, 1, -1, Alvo.PASSIVA, 3, listOf("ciclonica"), 0L, 0f, 0f, 7,
            "Seus golpes ganham chance de acerto crítico.", "+6% de chance crítica e +10% de dano crítico por grau"),
        t("triplo", "Tiro Triplo", Ramo.LAMINA, 1, 1, Alvo.INIMIGO, 3, listOf("ciclonica"), 4500L, 240f, 0f, 0,
            "Três flechas em leque que convergem no alvo.", "+15% de dano por grau"),
        t("predador", "Predador do Céu", Ramo.LAMINA, 2, -1, Alvo.PASSIVA, 8, listOf("fio"), 0L, 0f, 0f, 7,
            "Quem mantém distância machuca mais: dano extra em alvos longe de você.", "+8% de dano em alvos a mais de 160 de distância, por grau"),
        t("lamina", "Lâmina de Vento", Ramo.LAMINA, 2, 1, Alvo.DIRECAO, 8, listOf("triplo"), 5000L, 420f, 0f, 1,
            "Lâmina crescente que atravessa todos os inimigos em linha.", "+15% de dano por grau"),
        t("sopro", "Sopro Letal", Ramo.LAMINA, 3, -1, Alvo.PASSIVA, 14, listOf("predador"), 0L, 0f, 0f, 7,
            "Críticos aceleram suas habilidades.", "-0,4s de recarga em uma skill a cada crítico, por grau"),
        t("chuvalam", "Chuva de Lâminas", Ramo.LAMINA, 3, 1, Alvo.AREA, 14, listOf("lamina"), 11000L, 270f, 100f, 5,
            "Lâminas de vento caem do céu sobre a área por 3s.", "+15% de dano por grau"),
        t("execucao", "Execução do Zéfiro", Ramo.LAMINA, 4, 0, Alvo.INIMIGO, 22, listOf("sopro", "chuvalam"), 45000L, 320f, 0f, 8,
            "Concentra o vento numa flecha gigante. Dano enorme, ainda maior em alvos abaixo de 35% de vida.", "+15% de dano por grau", true),

        // ───────── DANÇARINO DAS BRISAS (agilidade) ─────────
        t("passo", "Passo do Vento", Ramo.BRISA, 0, 0, Alvo.SELF, 1, listOf(), 9000L, 0f, 0f, 3,
            "Corre com o vento: +45% de velocidade e muito Ímpeto.", "+0,5s de duração por grau"),
        t("penas", "Pés de Pluma", Ramo.BRISA, 1, -1, Alvo.PASSIVA, 3, listOf("passo"), 0L, 0f, 0f, 7,
            "Você fica mais leve e veloz o tempo todo.", "+5% de velocidade de movimento por grau"),
        t("salto", "Salto Ciclone", Ramo.BRISA, 1, 1, Alvo.DIRECAO, 3, listOf("passo"), 6000L, 0f, 80f, 3,
            "Salto veloz (na direção do analógico ou para longe do alvo) que explode ao pousar. Matar reseta a recarga.", "+15% de dano por grau"),
        t("danca", "Dança do Vento", Ramo.BRISA, 2, -1, Alvo.PASSIVA, 8, listOf("penas"), 0L, 0f, 0f, 7,
            "Depois de usar uma skill, seu próximo tiro básico é reforçado.", "+15% de dano e +10 de Ímpeto por grau"),
        t("manto", "Manto de Brisa", Ramo.BRISA, 2, 1, Alvo.SELF, 8, listOf("salto"), 18000L, 0f, 0f, 4,
            "Ventos em volta de você: escudo e +20% de velocidade por 4s.", "+15% de escudo por grau"),
        t("fuga", "Instinto de Fuga", Ramo.BRISA, 3, -1, Alvo.PASSIVA, 14, listOf("danca"), 0L, 0f, 0f, 7,
            "Ferido, você vira vento: abaixo de 45% de vida ganha velocidade e o Ímpeto não cai.", "+8% de velocidade por grau"),
        t("rastro", "Rastro de Penas", Ramo.BRISA, 3, 1, Alvo.DIRECAO, 14, listOf("manto"), 10000L, 0f, 0f, 3,
            "Corrida que deixa um rastro de vento cortante que desacelera e fere.", "+15% de dano por grau"),
        t("furacao", "Furacão Pessoal", Ramo.BRISA, 4, 0, Alvo.SELF, 22, listOf("fuga", "rastro"), 50000L, 0f, 110f, 4,
            "Por 6s você vira um furacão: velocidade máxima e golpes de vento em volta.", "+15% de dano por grau", true),

        // ───────── OLHO DO FURACÃO (controle) ─────────
        t("repulsora", "Rajada Repulsora", Ramo.OLHO, 0, 0, Alvo.INIMIGO, 1, listOf(), 6000L, 240f, 0f, 0,
            "Flecha que empurra o alvo para longe e o desacelera: espaço para respirar.", "+15% de dano por grau"),
        t("rarefeito", "Ar Rarefeito", Ramo.OLHO, 1, -1, Alvo.PASSIVA, 3, listOf("repulsora"), 0L, 0f, 0f, 7,
            "Inimigos lentos ou desequilibrados sofrem mais dano.", "+6% de dano por grau"),
        t("redemoinho", "Redemoinho", Ramo.OLHO, 1, 1, Alvo.AREA, 3, listOf("repulsora"), 12000L, 260f, 90f, 2,
            "Vento giratório que empurra todos para fora por 5s e causa dano.", "+15% de dano por grau"),
        t("corrente", "Corrente de Ar", Ramo.OLHO, 2, -1, Alvo.PASSIVA, 8, listOf("rarefeito"), 0L, 0f, 0f, 7,
            "Suas zonas de vento ficam maiores e duram mais.", "+8% de área e +10% de duração por grau"),
        t("tornado", "Tornado Caçador", Ramo.OLHO, 2, 1, Alvo.AREA, 8, listOf("redemoinho"), 14000L, 270f, 70f, 2,
            "Invoca um tornado que persegue os inimigos, puxando e ferindo, e os arremessa ao acabar.", "+15% de dano por grau"),
        t("calmo", "Olho Calmo", Ramo.OLHO, 3, -1, Alvo.PASSIVA, 14, listOf("corrente"), 0L, 0f, 0f, 7,
            "Dentro das suas zonas de vento você luta melhor.", "+6% de dano por grau enquanto estiver numa zona"),
        t("implosao", "Implosão de Ar", Ramo.OLHO, 3, 1, Alvo.AREA, 14, listOf("tornado"), 16000L, 270f, 130f, 6,
            "Suga todos para o centro e explode, atordoando.", "+15% de dano por grau"),
        t("grande", "Grande Tornado", Ramo.OLHO, 4, 0, Alvo.AREA, 22, listOf("calmo", "implosao"), 50000L, 280f, 170f, 2,
            "Um tornado colossal por 8s: puxa, fere e arremessa tudo.", "+15% de dano por grau", true)
    )

    val porId: Map<String, Talento> = todos.associateBy { it.id }

    fun doRamo(r: Ramo): List<Talento> = todos.filter { it.ramo == r }
}

/** Progressão de Sylvarion: nível, XP, pontos gastos por talento e as 5 skills equipadas. Salvo no aparelho. */
class SylvState {
    var nivel = 1
    var xp = 0
    private val ranks = HashMap<String, Int>()
    val slots = Array(5) { "" }

    fun xpPara(n: Int): Int = 30 + 18 * n

    /** Soma XP; devolve true se subiu de nível. */
    fun addXp(v: Int): Boolean {
        if (nivel >= SylvCatalog.NIVEL_MAX) return false
        xp += v
        var subiu = false
        while (nivel < SylvCatalog.NIVEL_MAX && xp >= xpPara(nivel)) {
            xp -= xpPara(nivel)
            nivel++
            subiu = true
        }
        if (nivel >= SylvCatalog.NIVEL_MAX) xp = 0
        return subiu
    }

    fun rank(id: String): Int = ranks[id] ?: 0
    fun gasto(): Int = ranks.values.sum()
    fun pontos(): Int = nivel - gasto()

    /** Requisitos: nível e pelo menos um dos talentos "pai" aprendido. */
    fun disponivel(t: Talento): Boolean = nivel >= t.nivelMin && (t.pais.isEmpty() || t.pais.any { rank(it) > 0 })

    fun podeAprender(t: Talento): Boolean = rank(t.id) < SylvCatalog.GRAU_MAX && pontos() > 0 && disponivel(t)

    fun aprender(t: Talento): Boolean {
        if (!podeAprender(t)) return false
        ranks[t.id] = rank(t.id) + 1
        if (t.ativa && rank(t.id) == 1 && !slots.contains(t.id)) {
            val i = slots.indexOf("")
            if (i >= 0) slots[i] = t.id
        }
        return true
    }

    /** Só dá para tirar um ponto se nenhum talento filho ficar sem caminho. */
    fun podeRemover(t: Talento): Boolean {
        val r = rank(t.id)
        if (r <= 0) return false
        if (r > 1) return true
        for (f in SylvCatalog.todos) {
            if (rank(f.id) > 0 && f.pais.contains(t.id) && f.pais.none { it != t.id && rank(it) > 0 }) return false
        }
        return true
    }

    fun remover(t: Talento): Boolean {
        if (!podeRemover(t)) return false
        val r = rank(t.id) - 1
        if (r <= 0) {
            ranks.remove(t.id)
            for (i in slots.indices) if (slots[i] == t.id) slots[i] = ""
        } else ranks[t.id] = r
        return true
    }

    fun equipar(id: String, slot: Int) {
        if (slot !in 0..4 || rank(id) <= 0 || SylvCatalog.porId[id]?.ativa != true) return
        for (i in slots.indices) if (slots[i] == id) slots[i] = ""
        slots[slot] = id
    }

    fun desequipar(slot: Int) {
        if (slot in 0..4) slots[slot] = ""
    }

    fun resetar() {
        ranks.clear()
        for (i in slots.indices) slots[i] = ""
    }

    fun load(c: Context) {
        val sp = c.getSharedPreferences("sylvarion", Context.MODE_PRIVATE)
        nivel = sp.getInt("nivel", 1).coerceIn(1, SylvCatalog.NIVEL_MAX)
        xp = sp.getInt("xp", 0).coerceAtLeast(0)
        ranks.clear()
        for (part in (sp.getString("ranks", "") ?: "").split(",")) {
            val kv = part.split(":")
            val id = kv.getOrNull(0) ?: continue
            val v = kv.getOrNull(1)?.toIntOrNull() ?: continue
            if (SylvCatalog.porId.containsKey(id)) ranks[id] = v.coerceIn(1, SylvCatalog.GRAU_MAX)
        }
        val sl = (sp.getString("slots", "") ?: "").split(",")
        for (i in slots.indices) {
            val id = sl.getOrNull(i) ?: ""
            slots[i] = if (id.isNotEmpty() && rank(id) > 0) id else ""
        }
        // não deixa gastar mais pontos do que o nível permite
        if (gasto() > nivel) resetar()
    }

    fun save(c: Context) {
        val sb = StringBuilder()
        for ((k, v) in ranks) {
            if (sb.isNotEmpty()) sb.append(',')
            sb.append(k).append(':').append(v)
        }
        c.getSharedPreferences("sylvarion", Context.MODE_PRIVATE).edit()
            .putInt("nivel", nivel).putInt("xp", xp)
            .putString("ranks", sb.toString())
            .putString("slots", slots.joinToString(","))
            .apply()
    }
}

/** Ícone das skills nos botões do jogo (desenhado, sem arquivo de imagem). */
class SkillIcon(private val cor: Int, private val estilo: Int, private val vazio: Boolean = false) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas) {
        val b = bounds
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val r = minOf(b.width(), b.height()) / 2f
        p.style = Paint.Style.FILL
        p.color = 0xFF0E161B.toInt()
        canvas.drawCircle(cx, cy, r * 0.92f, p)
        p.color = WindArt.a(if (vazio) 0xFF666666.toInt() else cor, 0.28f)
        canvas.drawCircle(cx, cy, r * 0.78f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.12f
        p.color = if (vazio) 0xFF555555.toInt() else cor
        canvas.drawCircle(cx, cy, r * 0.86f, p)
        if (vazio) {
            p.strokeWidth = r * 0.14f
            p.color = 0xFF888888.toInt()
            canvas.drawLine(cx - r * 0.3f, cy, cx + r * 0.3f, cy, p)
            canvas.drawLine(cx, cy - r * 0.3f, cx, cy + r * 0.3f, p)
        } else {
            WindArt.icone(canvas, p, cx, cy, r * 0.62f, estilo, 0xFFFFFFFF.toInt(), 1f)
        }
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
