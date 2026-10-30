package com.viniciusy.masmorras

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** As 5 Escolas Elementais do Arqueiro Elfo. Cada uma tem uma mecânica própria de Sintonia. */
enum class Escola(val nome: String, val papel: String, val cor: Int, val recurso: String, val mecanica: String) {
    BRASA(
        "Brasa Viva", "Dano em área sustentado (DoT)", 0xFFFF6A2B.toInt(), "Calor",
        "Calor alto deixa a queimadura mais forte. No máximo você fica Superaquecido: +40% de dano, mas não pode saltar."
    ),
    GEADA(
        "Geada Eterna", "Controle e kiting", 0xFF4FC3F7.toInt(), "Gelo",
        "Inimigos lentos acumulam Frio. Com 5 acúmulos congelam e levam dano crítico. Gelo alto aumenta o dano em quem está lento."
    ),
    TEMPESTADE(
        "Tempestade Viva", "Burst em alvo único", 0xFFFFE04A.toInt(), "Carga",
        "Atirar acumula Carga. As skills de descarga gastam tudo e escalam o dano. Planeje o momento."
    ),
    RAIZ(
        "Raiz Profunda", "Sustentação e proteção", 0xFF66BB6A.toInt(), "Vitalidade",
        "Acertar gera Vitalidade. Ao encher, a floresta floresce e cura você."
    ),
    VENDAVAL(
        "Vendaval", "Mobilidade e dano em movimento", 0xFF80DEEA.toInt(), "Ímpeto",
        "O Ímpeto sobe só enquanto você se move e dá dano e velocidade. Parado, cai rápido."
    )
}

/** Como a skill escolhe onde acerta. */
enum class Alvo { INIMIGO, AREA, DIRECAO, SELF }

class SkillDef(
    val id: String,
    val nome: String,
    val desc: String,
    val glifo: String,
    val alvo: Alvo,
    val cdMs: Long,
    val alcance: Float,
    val raio: Float,
    val nivelMin: Int,
    val ultimate: Boolean = false
) {
    fun tipoTxt(): String = when (alvo) {
        Alvo.INIMIGO -> "Alvo"
        Alvo.AREA -> "Área mirada"
        Alvo.DIRECAO -> "Direção"
        Alvo.SELF -> "Self"
    }
}

object ElfoCatalog {
    const val NIVEL_MAX = 20
    const val RANK_MAX = 3

    private fun d(
        id: String, nome: String, glifo: String, alvo: Alvo, cd: Long, alc: Float, raio: Float, nv: Int,
        desc: String, ult: Boolean = false
    ) = SkillDef(id, nome, desc, glifo, alvo, cd, alc, raio, nv, ult)

    private val brasa = listOf(
        d("cinza", "Flecha de Cinza", "FC", Alvo.INIMIGO, 1500L, 230f, 0f, 1,
            "Dano + queimadura que empilha até 5x."),
        d("semente", "Semente Flamejante", "SF", Alvo.AREA, 6000L, 260f, 70f, 3,
            "Planta uma semente que brota em 2s e vira um arbusto em chamas por 8s. Máx. 3."),
        d("incendio", "Incêndio Florestal", "IF", Alvo.AREA, 12000L, 260f, 110f, 6,
            "Chama que se espalha entre inimigos queimados vizinhos por 3s."),
        d("brasas", "Chuva de Brasas", "CB", Alvo.AREA, 9000L, 260f, 90f, 9,
            "Brasas caem por 4s, dando dano e queimadura."),
        d("fenix", "Renascer da Fênix", "FX", Alvo.AREA, 40000L, 280f, 130f, 14,
            "Fênix cai na área: dano massivo e chão em chamas. Por 8s, se você morrer, revive com 30% de vida.", true)
    )

    private val geada = listOf(
        d("gelida", "Flecha Gélida", "FG", Alvo.INIMIGO, 1500L, 230f, 0f, 1,
            "Dano + lentidão + 1 acúmulo de Frio."),
        d("armadilha", "Armadilha de Geada", "AG", Alvo.AREA, 8000L, 260f, 40f, 3,
            "Armadilha no chão (máx. 3). Enraíza ao pisar e deixa gelo que desacelera."),
        d("cristais", "Chuva de Cristais", "CC", Alvo.AREA, 10000L, 260f, 100f, 6,
            "Estilhaços caem por 4s, cada tique aplica Frio e lentidão."),
        d("passogelido", "Passo Gélido", "PG", Alvo.DIRECAO, 7000L, 0f, 0f, 9,
            "Salto curto que deixa uma trilha de gelo que desacelera quem atravessa."),
        d("zero", "Zero Absoluto", "ZA", Alvo.SELF, 45000L, 0f, 170f, 14,
            "Onda de gelo que congela tudo ao redor por 2s. Congelados ficam vulneráveis.", true)
    )

    private val tempestade = listOf(
        d("relampago", "Flecha Relâmpago", "FR", Alvo.INIMIGO, 1400L, 240f, 0f, 1,
            "Salta para até 2 inimigos próximos. Gera Carga."),
        d("perfurante", "Tiro Perfurante: Trovão", "TP", Alvo.DIRECAO, 6000L, 480f, 0f, 3,
            "Atravessa todos em linha. Gasta toda a Carga: dano escala com ela."),
        d("concentrada", "Tempestade Concentrada", "TC", Alvo.INIMIGO, 12000L, 240f, 0f, 6,
            "Canaliza 2s no alvo, dano crescente. Se você andar, a canalização quebra."),
        d("marca", "Marca do Caçador", "MC", Alvo.INIMIGO, 14000L, 300f, 0f, 9,
            "O alvo marcado recebe +25% de dano seu por 8s."),
        d("olho", "Olho da Tempestade", "OT", Alvo.INIMIGO, 45000L, 300f, 70f, 14,
            "Raio massivo do céu no alvo. Dano dobrado se ele estiver marcado.", true)
    )

    private val raiz = listOf(
        d("espinhosa", "Flecha Espinhosa", "FE", Alvo.INIMIGO, 1500L, 230f, 0f, 1,
            "Dano + veneno. Cura você com parte do dano."),
        d("totem", "Totem de Seiva", "TS", Alvo.AREA, 14000L, 260f, 110f, 3,
            "Totem que cura quem estiver na área por 8s."),
        d("raizes", "Raízes Aprisionadoras", "RA", Alvo.AREA, 10000L, 260f, 90f, 6,
            "Enraíza inimigos por 2,5s e dá escudo a você se estiver na área."),
        d("bencao", "Bênção da Floresta", "BF", Alvo.SELF, 20000L, 0f, 0f, 9,
            "Regeneração e +15% de velocidade por 10s."),
        d("arvore", "Árvore-Mãe", "AM", Alvo.AREA, 50000L, 280f, 150f, 14,
            "Árvore gigante por 12s: cura, protege, fere e puxa os inimigos para ela.", true)
    )

    private val vendaval = listOf(
        d("cortante", "Flecha Cortante", "FO", Alvo.INIMIGO, 700L, 240f, 0f, 1,
            "Rápida, dano baixo, gera muito Ímpeto."),
        d("ciclone", "Salto Ciclone", "SC", Alvo.DIRECAO, 6000L, 0f, 80f, 3,
            "Salto com dano em área na aterrissagem. Reseta a recarga se matar."),
        d("rajada", "Rajada Dupla", "RD", Alvo.INIMIGO, 4000L, 240f, 0f, 6,
            "Dois tiros rápidos. O segundo é bem mais forte se você estiver com Ímpeto."),
        d("redemoinho", "Redemoinho", "RE", Alvo.AREA, 12000L, 260f, 90f, 9,
            "Vento que empurra os inimigos para fora por 5s."),
        d("furacao", "Furacão", "FU", Alvo.SELF, 50000L, 0f, 100f, 14,
            "5s de velocidade máxima e golpes de vento em volta enquanto você corre.", true)
    )

    fun skills(e: Escola): List<SkillDef> = when (e) {
        Escola.BRASA -> brasa
        Escola.GEADA -> geada
        Escola.TEMPESTADE -> tempestade
        Escola.RAIZ -> raiz
        Escola.VENDAVAL -> vendaval
    }

    /** Passivas da escola (aplicadas automaticamente). */
    fun passivas(e: Escola): List<Pair<String, String>> = when (e) {
        Escola.BRASA -> listOf("Cinzas Fertilizam" to "Inimigo que morre queimando devolve Calor.")
        Escola.GEADA -> listOf("Pele de Geada" to "Inimigos colados em você ganham Frio aos poucos.")
        Escola.TEMPESTADE -> listOf("Condutor" to "Acertos críticos cortam 1s da recarga de uma skill.")
        Escola.RAIZ -> listOf("Seiva Vital" to "Flechas curam um pouco; ao encher a Vitalidade você floresce e cura.")
        Escola.VENDAVAL -> listOf("Pés de Vento" to "Ímpeto dá velocidade de movimento extra.")
    }

    /** Passivas de todo Elfo, independentes da escola. */
    val comuns: List<Pair<String, String>> = listOf(
        "Flecha Viva" to "Todo 3º tiro no mesmo alvo causa +30% de dano e mais Sintonia.",
        "Olho de Águia" to "+15% de alcance em tudo.",
        "Raízes Antigas" to "Parado por 2s, você ganha um escudo leve. Andar remove."
    )
}

/** Progressão do Arqueiro Elfo: nível, XP, pontos e graus (1 a 3) das skills. Guardado no aparelho. */
class ElfoState {
    var escola: Escola? = null
    var nivel = 1
    var xp = 0
    var pontos = 0
    private val ranks = HashMap<String, Int>()

    fun xpPara(n: Int): Int = 40 + 25 * n

    /** Soma XP; devolve true se subiu de nível. */
    fun addXp(v: Int): Boolean {
        if (nivel >= ElfoCatalog.NIVEL_MAX) return false
        xp += v
        var subiu = false
        while (nivel < ElfoCatalog.NIVEL_MAX && xp >= xpPara(nivel)) {
            xp -= xpPara(nivel)
            nivel++
            if (nivel % 2 == 0) pontos++
            subiu = true
        }
        if (nivel >= ElfoCatalog.NIVEL_MAX) xp = 0
        return subiu
    }

    /** 0 = ainda bloqueada; 1..3 = grau da skill. */
    fun rank(s: SkillDef): Int = if (nivel < s.nivelMin) 0 else (ranks[s.id] ?: 1).coerceIn(1, ElfoCatalog.RANK_MAX)

    fun podeEvoluir(s: SkillDef): Boolean = pontos > 0 && rank(s) in 1 until ElfoCatalog.RANK_MAX

    fun evoluir(s: SkillDef): Boolean {
        if (!podeEvoluir(s)) return false
        ranks[s.id] = rank(s) + 1
        pontos--
        return true
    }

    /** Troca de escola: devolve os pontos gastos. */
    fun trocarEscola(e: Escola) {
        if (escola == e) return
        var gasto = 0
        for (v in ranks.values) gasto += (v - 1).coerceAtLeast(0)
        pontos += gasto
        ranks.clear()
        escola = e
    }

    fun load(c: Context) {
        val sp = c.getSharedPreferences("elfo", Context.MODE_PRIVATE)
        escola = Escola.values().firstOrNull { it.name == sp.getString("escola", "") }
        nivel = sp.getInt("nivel", 1).coerceIn(1, ElfoCatalog.NIVEL_MAX)
        xp = sp.getInt("xp", 0).coerceAtLeast(0)
        pontos = sp.getInt("pontos", 0).coerceAtLeast(0)
        ranks.clear()
        val raw = sp.getString("ranks", "") ?: ""
        for (part in raw.split(",")) {
            val kv = part.split(":")
            if (kv.size == 2) ranks[kv[0]] = kv[1].toIntOrNull() ?: 1
        }
    }

    fun save(c: Context) {
        val sb = StringBuilder()
        for ((k, v) in ranks) {
            if (sb.isNotEmpty()) sb.append(',')
            sb.append(k).append(':').append(v)
        }
        c.getSharedPreferences("elfo", Context.MODE_PRIVATE).edit()
            .putString("escola", escola?.name ?: "")
            .putInt("nivel", nivel).putInt("xp", xp).putInt("pontos", pontos)
            .putString("ranks", sb.toString()).apply()
    }
}

/** Ícone provisório das skills (círculo da cor da escola com as iniciais). Dá para trocar por arte depois. */
class SkillIcon(private val cor: Int, private val glifo: String, private val bloqueada: Boolean = false, private val escola: Int = -1) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas) {
        val b = bounds
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val r = minOf(b.width(), b.height()) / 2f
        p.style = Paint.Style.FILL
        p.color = 0xFF16201B.toInt()
        canvas.drawCircle(cx, cy, r * 0.92f, p)
        p.color = (cor and 0x00FFFFFF) or 0x55000000
        canvas.drawCircle(cx, cy, r * 0.78f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.13f
        p.color = if (bloqueada) 0xFF555555.toInt() else cor
        canvas.drawCircle(cx, cy, r * 0.86f, p)
        if (!bloqueada) simbolo(canvas, cx, cy, r)
        p.style = Paint.Style.FILL
        p.color = if (bloqueada) 0xFF888888.toInt() else 0xFFFFFFFF.toInt()
        p.textAlign = Paint.Align.CENTER
        p.isFakeBoldText = true
        p.textSize = r * 0.8f
        canvas.drawText(glifo, cx, cy + r * 0.28f, p)
    }

    /** Símbolo do elemento ao fundo: chama, floco, raio, folha ou vento. */
    private fun simbolo(c: Canvas, cx: Float, cy: Float, r: Float) {
        p.color = (cor and 0x00FFFFFF) or 0x66000000
        p.style = Paint.Style.FILL
        val path = Path()
        when (escola) {
            0 -> { // chama
                path.moveTo(cx, cy - r * 0.62f)
                path.cubicTo(cx + r * 0.7f, cy - r * 0.1f, cx + r * 0.5f, cy + r * 0.6f, cx, cy + r * 0.6f)
                path.cubicTo(cx - r * 0.5f, cy + r * 0.6f, cx - r * 0.7f, cy - r * 0.1f, cx, cy - r * 0.62f)
                c.drawPath(path, p)
            }
            1 -> { // floco de gelo
                p.style = Paint.Style.STROKE
                p.strokeWidth = r * 0.1f
                for (i in 0 until 3) {
                    val a = i * 1.0472f
                    val dx = kotlin.math.cos(a) * r * 0.62f
                    val dy = kotlin.math.sin(a) * r * 0.62f
                    c.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, p)
                }
            }
            2 -> { // raio
                path.moveTo(cx + r * 0.15f, cy - r * 0.7f)
                path.lineTo(cx - r * 0.4f, cy + r * 0.08f)
                path.lineTo(cx - r * 0.02f, cy + r * 0.08f)
                path.lineTo(cx - r * 0.2f, cy + r * 0.7f)
                path.lineTo(cx + r * 0.42f, cy - r * 0.12f)
                path.lineTo(cx + r * 0.04f, cy - r * 0.12f)
                path.close()
                c.drawPath(path, p)
            }
            3 -> { // folha
                path.moveTo(cx - r * 0.55f, cy + r * 0.55f)
                path.cubicTo(cx - r * 0.6f, cy - r * 0.4f, cx + r * 0.1f, cy - r * 0.7f, cx + r * 0.6f, cy - r * 0.6f)
                path.cubicTo(cx + r * 0.7f, cy, cx + r * 0.1f, cy + r * 0.6f, cx - r * 0.55f, cy + r * 0.55f)
                c.drawPath(path, p)
            }
            4 -> { // vento
                p.style = Paint.Style.STROKE
                p.strokeWidth = r * 0.12f
                c.drawArc(cx - r * 0.6f, cy - r * 0.5f, cx + r * 0.3f, cy + r * 0.1f, 200f, 220f, false, p)
                c.drawArc(cx - r * 0.3f, cy - r * 0.1f, cx + r * 0.6f, cy + r * 0.5f, 20f, 220f, false, p)
            }
        }
        p.style = Paint.Style.FILL
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
