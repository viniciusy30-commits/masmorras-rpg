package com.viniciusy.masmorras

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Tela de talentos do Sylvarion, no estilo das árvores do Diablo:
 * 3 faixas (Dano, Agilidade, Controle) com nós ligados por linhas que brilham quando o caminho está aberto.
 * Ativas são círculos, passivas são losangos, ultimates têm anel dourado.
 * O painel mostra uma prévia animada da skill, os graus e os 5 slots para equipar.
 */
class SylvTreeView(
    ctx: Context,
    private val eng: SylvEngine,
    private val onClose: () -> Unit,
    private val onChange: () -> Unit
) : View(ctx) {

    private val d = ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rf = RectF()
    private val path = Path()
    private val t0 = System.nanoTime()

    private class Btn(val r: RectF, val acao: () -> Unit)
    private class NoHit(val x: Float, val y: Float, val r: Float, val t: Talento)

    private val btns = ArrayList<Btn>()
    private val nos = ArrayList<NoHit>()
    private var sel: Talento = SylvCatalog.todos[0]
    private var resetArm = 0L
    private var msg = ""
    private var msgAte = 0L
    private var downX = 0f
    private var downY = 0f

    private fun dp(v: Float): Float = v * d
    private fun a(cor: Int, f: Float): Int = WindArt.a(cor, f)
    private val st get() = eng.st

    private fun aviso(s: String) {
        msg = s
        msgAte = System.currentTimeMillis() + 2200L
    }

    private fun mudou() {
        eng.recalc()
        eng.salvar()
        onChange()
    }

    // ───────────── desenho ─────────────

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val t = (System.nanoTime() - t0) / 1e9f
        btns.clear()
        nos.clear()
        p.style = Paint.Style.FILL
        p.color = 0xFF070B0F.toInt()
        c.drawRect(0f, 0f, w, h, p)
        fundo(c, w, h, t)
        val topH = dp(52f)
        val land = w >= h
        val tree = RectF(0f, topH, if (land) w * 0.63f else w, if (land) h else topH + (h - topH) * 0.5f)
        val pan = if (land) RectF(w * 0.63f, topH, w, h) else RectF(0f, tree.bottom, w, h)
        topo(c, w, topH)
        arvore(c, tree, t)
        painel(c, pan, t)
        postInvalidateOnAnimation()
    }

    private fun fundo(c: Canvas, w: Float, h: Float, t: Float) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = dp(1.5f)
        p.strokeCap = Paint.Cap.ROUND
        for (i in 0..11) {
            val v = 30f + (i % 4) * 22f
            val x = ((t * v + i * 131f) % (w + 240f)) - 120f
            val y = (i * 97f) % h
            p.color = a(0xFF80DEEA.toInt(), 0.05f + (i % 3) * 0.02f)
            c.drawLine(x, y, x + dp(70f + (i % 3) * 30f), y, p)
        }
        p.strokeCap = Paint.Cap.BUTT
    }

    private fun txt(c: Canvas, s: String, x: Float, y: Float, sizeDp: Float, cor: Int, bold: Boolean = false, centro: Boolean = false, direita: Boolean = false) {
        p.style = Paint.Style.FILL
        p.textSize = dp(sizeDp)
        p.color = cor
        p.isFakeBoldText = bold
        p.textAlign = if (centro) Paint.Align.CENTER else if (direita) Paint.Align.RIGHT else Paint.Align.LEFT
        c.drawText(s, x, y, p)
        p.isFakeBoldText = false
        p.textAlign = Paint.Align.LEFT
    }

    private fun botao(c: Canvas, r: RectF, label: String, ativo: Boolean, cor: Int, acao: () -> Unit) {
        p.style = Paint.Style.FILL
        p.color = a(if (ativo) cor else 0xFF555555.toInt(), if (ativo) 0.28f else 0.18f)
        c.drawRoundRect(r, dp(6f), dp(6f), p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = dp(1.5f)
        p.color = if (ativo) cor else 0xFF555555.toInt()
        c.drawRoundRect(r, dp(6f), dp(6f), p)
        txt(c, label, r.centerX(), r.centerY() + dp(4f), 12f, if (ativo) 0xFFFFFFFF.toInt() else 0xFF888888.toInt(), true, true)
        btns.add(Btn(RectF(r), acao))
    }

    private fun topo(c: Canvas, w: Float, h: Float) {
        p.style = Paint.Style.FILL
        p.color = 0xFF0E161C.toInt()
        c.drawRect(0f, 0f, w, h, p)
        p.color = a(0xFF80DEEA.toInt(), 0.5f)
        c.drawRect(0f, h - dp(2f), w, h, p)
        txt(c, "SYLVARION", dp(12f), dp(22f), 17f, 0xFFB2EBF2.toInt(), true)
        val pts = st.pontos()
        txt(c, "Nível " + st.nivel + "  •  Pontos: " + pts, dp(12f), dp(41f), 12f, if (pts > 0) 0xFFB9F6CA.toInt() else 0xFFAAAAAA.toInt(), pts > 0)
        val bh = dp(34f)
        val by = (h - bh) / 2f
        val fechar = RectF(w - dp(52f), by, w - dp(8f), by + bh)
        botao(c, fechar, "X", true, 0xFFFF8A65.toInt()) { onClose() }
        val armado = System.currentTimeMillis() - resetArm < 3000L
        val reset = RectF(fechar.left - dp(104f), by, fechar.left - dp(8f), by + bh)
        botao(c, reset, if (armado) "Confirmar?" else "Resetar", true, if (armado) 0xFFFF5252.toInt() else 0xFF82A8FF.toInt()) {
            if (System.currentTimeMillis() - resetArm < 3000L) {
                st.resetar()
                resetArm = 0L
                mudou()
                aviso("Talentos resetados")
            } else {
                resetArm = System.currentTimeMillis()
            }
        }
    }

    // posição de cada nó na área da árvore
    private fun pos(t: Talento, area: RectF): FloatArray {
        val laneH = area.height() / 3f
        val colW = (area.width() - dp(16f)) / 5f
        val x = area.left + dp(8f) + colW * (t.tier + 0.5f)
        val y = area.top + laneH * (t.ramo.faixa + 0.5f) + t.lado * laneH * 0.22f
        return floatArrayOf(x, y)
    }

    private fun arvore(c: Canvas, area: RectF, t: Float) {
        val laneH = area.height() / 3f
        val colW = (area.width() - dp(16f)) / 5f
        val r = min(colW * 0.34f, laneH * 0.24f)
        // faixas
        for (ramo in Ramo.values()) {
            val top = area.top + laneH * ramo.faixa
            rf.set(area.left + dp(3f), top + dp(2f), area.right - dp(3f), top + laneH - dp(2f))
            p.style = Paint.Style.FILL
            p.color = a(ramo.cor, 0.06f)
            c.drawRoundRect(rf, dp(8f), dp(8f), p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = dp(1.2f)
            p.color = a(ramo.cor, 0.3f)
            c.drawRoundRect(rf, dp(8f), dp(8f), p)
            txt(c, ramo.nome + "  " + ramo.papel, area.left + dp(10f), top + dp(15f), 9.5f, a(ramo.cor, 0.95f), true)
        }
        // ligações
        for (n in SylvCatalog.todos) {
            val b = pos(n, area)
            for (paiId in n.pais) {
                val pai = SylvCatalog.porId[paiId] ?: continue
                val o = pos(pai, area)
                val aberto = st.rank(paiId) > 0
                val ambos = aberto && st.rank(n.id) > 0
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                if (ambos) {
                    p.strokeWidth = dp(5f)
                    p.color = a(n.ramo.cor, 0.25f)
                    c.drawLine(o[0], o[1], b[0], b[1], p)
                    p.strokeWidth = dp(2.2f)
                    p.color = a(n.ramo.cor, 1f)
                    c.drawLine(o[0], o[1], b[0], b[1], p)
                    val q = (t * 0.7f + n.tier * 0.31f) % 1f
                    p.style = Paint.Style.FILL
                    p.color = 0xFFFFFFFF.toInt()
                    c.drawCircle(o[0] + (b[0] - o[0]) * q, o[1] + (b[1] - o[1]) * q, dp(2.6f), p)
                } else if (aberto) {
                    p.strokeWidth = dp(2f)
                    p.color = a(n.ramo.cor, 0.5f)
                    c.drawLine(o[0], o[1], b[0], b[1], p)
                } else {
                    p.strokeWidth = dp(1.5f)
                    p.color = 0xFF2C363D.toInt()
                    c.drawLine(o[0], o[1], b[0], b[1], p)
                }
                p.strokeCap = Paint.Cap.BUTT
            }
        }
        // nós
        for (n in SylvCatalog.todos) {
            val ps = pos(n, area)
            no(c, n, ps[0], ps[1], if (n.ultimate) r * 1.18f else r, t)
            nos.add(NoHit(ps[0], ps[1], r * 1.25f, n))
        }
    }

    private fun no(c: Canvas, n: Talento, x: Float, y: Float, r: Float, t: Float) {
        val rk = st.rank(n.id)
        val travado = st.nivel < n.nivelMin
        val pode = st.podeAprender(n)
        val cor = n.ramo.cor
        if (rk > 0) {
            val pulso = 0.5f + 0.5f * sin(t * 3f + n.tier)
            for (i in 3 downTo 1) {
                p.style = Paint.Style.FILL
                p.color = a(cor, (0.05f + 0.04f * pulso) * (4 - i))
                c.drawCircle(x, y, r * (1f + i * 0.28f), p)
            }
        }
        forma(c, n, x, y, r)
        p.style = Paint.Style.FILL
        p.color = if (rk > 0) a(cor, 0.22f) else 0xFF111A20.toInt()
        c.drawPath(path, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = dp(if (rk > 0) 2.6f else 2f)
        p.color = when {
            rk > 0 -> cor
            pode -> a(cor, 0.5f + 0.5f * sin(t * 5f))
            else -> 0xFF37424A.toInt()
        }
        c.drawPath(path, p)
        if (n.ultimate) {
            p.strokeWidth = dp(1.6f)
            p.color = a(0xFFFFD54F.toInt(), if (rk > 0) 1f else 0.55f)
            c.drawCircle(x, y, r * 1.22f, p)
        }
        WindArt.icone(c, p, x, y, r * 0.58f, n.estilo, if (rk > 0) 0xFFFFFFFF.toInt() else cor, if (travado) 0.3f else if (rk > 0) 1f else 0.6f)
        // graus (3 pontinhos) ou nível necessário
        p.style = Paint.Style.FILL
        if (travado) {
            txt(c, "Nv" + n.nivelMin, x, y + r + dp(10f), 8.5f, 0xFFE57373.toInt(), true, true)
        } else {
            for (i in 0 until SylvCatalog.GRAU_MAX) {
                p.color = if (i < rk) cor else 0xFF3A454D.toInt()
                c.drawCircle(x + (i - 1) * dp(7f), y + r + dp(7f), dp(2.4f), p)
            }
        }
        if (n === sel) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = dp(1.8f)
            p.color = 0xFFFFFFFF.toInt()
            p.pathEffect = DashPathEffect(floatArrayOf(dp(5f), dp(4f)), t * dp(14f))
            c.drawCircle(x, y, r * 1.38f, p)
            p.pathEffect = null
        }
    }

    /** Monta em [path] o contorno do nó: círculo (ativa) ou losango (passiva). */
    private fun forma(@Suppress("UNUSED_PARAMETER") c: Canvas, n: Talento, x: Float, y: Float, r: Float) {
        path.reset()
        if (n.ativa) path.addCircle(x, y, r, Path.Direction.CW)
        else {
            val k = r * 1.12f
            path.moveTo(x, y - k)
            path.lineTo(x + k, y)
            path.lineTo(x, y + k)
            path.lineTo(x - k, y)
            path.close()
        }
    }

    private fun quebra(s: String, sizeDp: Float, maxW: Float): List<String> {
        p.textSize = dp(sizeDp)
        p.isFakeBoldText = false
        val out = ArrayList<String>()
        var linha = ""
        for (palavra in s.split(" ")) {
            val teste = if (linha.isEmpty()) palavra else "$linha $palavra"
            if (p.measureText(teste) <= maxW) linha = teste
            else {
                if (linha.isNotEmpty()) out.add(linha)
                linha = palavra
            }
        }
        if (linha.isNotEmpty()) out.add(linha)
        return out
    }

    private fun motivo(n: Talento): String {
        if (st.rank(n.id) >= SylvCatalog.GRAU_MAX) return "Grau máximo"
        if (st.pontos() <= 0) return "Sem pontos de talento"
        if (st.nivel < n.nivelMin) return "Requer nível " + n.nivelMin
        val pais = n.pais.mapNotNull { SylvCatalog.porId[it]?.nome }
        return "Aprenda antes: " + pais.joinToString(" ou ")
    }

    private fun painel(c: Canvas, area: RectF, t: Float) {
        val m = dp(8f)
        val box = RectF(area.left + m, area.top + m, area.right - m, area.bottom - m)
        p.style = Paint.Style.FILL
        p.color = 0xFF0E161C.toInt()
        c.drawRoundRect(box, dp(10f), dp(10f), p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = dp(1.5f)
        p.color = a(sel.ramo.cor, 0.6f)
        c.drawRoundRect(box, dp(10f), dp(10f), p)

        val x0 = box.left + dp(10f)
        val larg = box.width() - dp(20f)
        var y = box.top + dp(20f)
        txt(c, sel.nome, x0, y, 16f, sel.ramo.cor, true)
        y += dp(15f)
        var sub = sel.tipoTxt()
        if (sel.ativa) {
            sub += " • Recarga " + (sel.cdMs / 100L) / 10f + "s"
            if (sel.alcance > 0f) sub += " • Alcance " + sel.alcance.toInt()
        }
        txt(c, sub, x0, y, 10f, 0xFF9AA7AE.toInt())
        y += dp(8f)

        val prevH = (box.height() * 0.27f).coerceIn(dp(60f), dp(110f))
        rf.set(x0, y, x0 + larg, y + prevH)
        WindArt.preview(c, p, rf, sel.estilo, sel.ramo.cor, t)
        y += prevH + dp(14f)

        val rk = st.rank(sel.id)
        val okNivel = st.nivel >= sel.nivelMin
        txt(c, "Grau " + rk + "/" + SylvCatalog.GRAU_MAX, x0, y, 11.5f, if (rk > 0) sel.ramo.cor else 0xFFAAAAAA.toInt(), true)
        txt(c, "Requer Nv " + sel.nivelMin, x0 + larg, y, 10.5f, if (okNivel) 0xFF8FA0A8.toInt() else 0xFFE57373.toInt(), false, false, true)
        y += dp(6f)

        // base do painel (de baixo para cima): slots, botões
        val slotsH = dp(46f)
        val btnH = dp(32f)
        val slotsTop = box.bottom - dp(8f) - slotsH
        val btnTop = slotsTop - dp(6f) - btnH
        val descFim = btnTop - dp(16f)

        for (ln in quebra(sel.desc, 11f, larg)) {
            y += dp(13.5f)
            if (y > descFim) break
            txt(c, ln, x0, y, 11f, 0xFFDDE6EA.toInt())
        }
        y += dp(4f)
        for (ln in quebra(sel.porGrau, 10f, larg)) {
            y += dp(12.5f)
            if (y > descFim) break
            txt(c, ln, x0, y, 10f, a(sel.ramo.cor, 0.95f), true)
        }

        // mensagem de erro/aviso
        if (System.currentTimeMillis() < msgAte) txt(c, msg, x0, btnTop - dp(5f), 10.5f, 0xFFFFAB91.toInt(), true)

        // botões
        val bw = (larg - dp(8f)) / 2f
        val b1 = RectF(x0, btnTop, x0 + bw, btnTop + btnH)
        botao(c, b1, if (rk == 0) "Aprender" else if (rk < SylvCatalog.GRAU_MAX) "Evoluir  +" else "Grau máximo", st.podeAprender(sel), sel.ramo.cor) {
            if (st.aprender(sel)) {
                mudou()
                aviso(if (st.rank(sel.id) == 1) "Aprendeu " + sel.nome else sel.nome + " grau " + st.rank(sel.id))
            } else aviso(motivo(sel))
        }
        val b2 = RectF(x0 + bw + dp(8f), btnTop, x0 + larg, btnTop + btnH)
        botao(c, b2, "Remover  −", st.podeRemover(sel), 0xFFE57373.toInt()) {
            if (st.remover(sel)) {
                mudou()
                aviso("Ponto devolvido")
            } else aviso("Outro talento depende deste")
        }

        // slots equipados
        txt(c, if (sel.ativa) "Equipar no botão:" else "Skills equipadas (toque para ver):", x0, slotsTop + dp(9f), 9.5f, 0xFF9AA7AE.toInt())
        val sz = min(dp(30f), (larg - dp(4f) * 4f) / 5f)
        for (i in 0..4) {
            val sx = x0 + i * (sz + dp(4f))
            val sy = slotsTop + dp(14f)
            val id = st.slots[i]
            val tal = if (id.isEmpty()) null else SylvCatalog.porId[id]
            rf.set(sx, sy, sx + sz, sy + sz)
            p.style = Paint.Style.FILL
            p.color = 0xFF111A20.toInt()
            c.drawRoundRect(rf, dp(6f), dp(6f), p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = dp(if (tal != null && tal === sel) 2.5f else 1.5f)
            p.color = if (tal != null) (if (tal === sel) 0xFFFFFFFF.toInt() else tal.ramo.cor) else 0xFF3A454D.toInt()
            c.drawRoundRect(rf, dp(6f), dp(6f), p)
            if (tal != null) WindArt.icone(c, p, rf.centerX(), rf.centerY(), sz * 0.3f, tal.estilo, tal.ramo.cor, 1f)
            else txt(c, "" + (i + 1), rf.centerX(), rf.centerY() + dp(4f), 11f, 0xFF55606A.toInt(), true, true)
            val slot = i
            btns.add(Btn(RectF(rf)) {
                if (sel.ativa && st.rank(sel.id) > 0) {
                    st.equipar(sel.id, slot)
                    mudou()
                    aviso(sel.nome + " no botão " + (slot + 1))
                } else if (tal != null) {
                    sel = tal
                } else {
                    aviso("Selecione uma ativa aprendida para equipar")
                }
            })
        }
    }

    // ───────────── toque ─────────────

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
            }
            MotionEvent.ACTION_UP -> {
                if (abs(e.x - downX) < dp(18f) && abs(e.y - downY) < dp(18f)) {
                    for (i in btns.indices.reversed()) {
                        val b = btns[i]
                        if (b.r.contains(e.x, e.y)) {
                            b.acao()
                            invalidate()
                            return true
                        }
                    }
                    var best: NoHit? = null
                    var bd = 1e9f
                    for (n in nos) {
                        val dd = hypot(n.x - e.x, n.y - e.y)
                        if (dd < n.r && dd < bd) {
                            bd = dd
                            best = n
                        }
                    }
                    val b = best
                    if (b != null) {
                        sel = b.t
                        invalidate()
                    }
                }
            }
        }
        return true
    }
}
