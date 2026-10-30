package com.viniciusy.masmorras

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** O que o motor do Sylvarion precisa do jogo (implementado pelo GameView). */
interface SylvHost {
    val jogador: Player?
    val inimigos: List<Enemy>
    fun dano(e: Enemy, d: Float, fx: Float, fy: Float, kb: Float, silencioso: Boolean)
    fun particulas(x: Float, y: Float, n: Int, cor: Int)
    fun som(nome: String, vol: Float)
    fun texto(x: Float, y: Float, t: String, cor: Int)
    fun livre(x: Float, y: Float): Boolean
    fun tremor(v: Float)
}

/**
 * Sylvarion, o Arqueiro do Vendaval.
 * - Toque num inimigo para marcar o alvo: ataque básico e skills de alvo vão direto nele.
 * - Skills de área: aperte o botão, toque no chão para mirar o círculo e aperte de novo para soltar.
 * - Botões 1 a 5 = as skills equipadas na árvore de talentos.
 * - Recurso: Ímpeto (sobe correndo, cai parado) = dano e velocidade extras.
 */
class SylvEngine(private val host: SylvHost) {

    val st = SylvState()
    private var ctx: Context? = null

    var alvo: Enemy? = null
    var mira = 0
        private set
    private var mx = 0f
    private var my = 0f
    private var miraT = 0f

    private val cdId = HashMap<String, Long>()
    var impeto = 0f
        private set
    private var parado = 0f
    private var ventoT = 0f
    private var mantoT = 0f
    private var mantoVal = 0f
    private var furacaoT = 0f
    private var furacaoTick = 0f
    private var dancaT = 0f
    private var ultimoAlvo: Enemy? = null
    private var contTiro = 0
    private var relogio = 0f
    private var naZona = false
    private var emitT = 0f

    // efeitos das passivas (recalculados em recalc())
    private var critCh = 0f
    private var critMul = 1.6f
    private var distB = 0f
    private var sopro = 0f
    private var spdP = 0f
    private var dancaB = 0f
    private var fugaS = 0f
    private var arRar = 0f
    private var corrArea = 0f
    private var corrDur = 0f
    private var calmoB = 0f

    // ───────────── estruturas ─────────────

    private class Est {
        var deseqT = 0f
    }

    private class Flecha(
        var x: Float, var y: Float, val alvo: Enemy, val cor: Int, val vel: Float, val espiral: Boolean,
        val onHit: (Enemy) -> Unit
    ) {
        var vivo = true
        var t = 0f
        var ang = 0f
    }

    private class Lamina(var x: Float, var y: Float, val dx: Float, val dy: Float, val alcance: Float, val cor: Int, val rank: Float) {
        var perc = 0f
        var vivo = true
        val acertou = HashSet<Enemy>()
    }

    private class Zona(var tipo: Int, var x: Float, var y: Float, val raio: Float, var vida: Float, val cor: Int, val rank: Float) {
        var idade = 0f
        var tick = 0f
        var armada = false
        var alvoE: Enemy? = null
        val vida0 = vida
    }

    private class Feixe(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val cor: Int, var vida: Float, val larg: Float) {
        val vida0 = vida
    }

    private class Queda(val x: Float, val y: Float, val cor: Int) {
        var t = 0f
    }

    private class Fantasma(val x: Float, val y: Float, val cor: Int, var vida: Float)

    private class Part(
        var x: Float, var y: Float, var vx: Float, var vy: Float, var vida: Float, val vida0: Float,
        val tam: Float, val cor: Int, val linha: Boolean
    )

    private class Agenda(var t: Float, val acao: () -> Unit)

    private val est = HashMap<Enemy, Est>()
    private val flechas = ArrayList<Flecha>()
    private val laminas = ArrayList<Lamina>()
    private val zonas = ArrayList<Zona>()
    private val feixes = ArrayList<Feixe>()
    private val quedas = ArrayList<Queda>()
    private val fantasmas = ArrayList<Fantasma>()
    private val parts = ArrayList<Part>()
    private val agenda = ArrayList<Agenda>()

    private fun est(e: Enemy): Est = est.getOrPut(e) { Est() }

    private companion object {
        const val Z_CHUVA = 1
        const val Z_REDEMOINHO = 2
        const val Z_TORNADO = 3
        const val Z_GRANDE = 4
        const val Z_IMPLOSAO = 5
        const val Z_RASTRO = 6
        const val Z_ONDA = 7
        const val Z_EXEC = 8
        const val ALC = 1f
        const val COR_VENTO = 0xFFB2EBF2.toInt()
        const val BRANCO = -1
        const val DEG = 57.29578f
    }

    private fun rnd(): Float = Math.random().toFloat()

    // ───────────── ciclo de vida ─────────────

    fun novaPartida(c: Context) {
        ctx = c
        st.load(c)
        resetRuntime()
        recalc()
    }

    private fun resetRuntime() {
        alvo = null
        mira = 0
        impeto = 0f
        parado = 0f
        ventoT = 0f
        mantoT = 0f
        mantoVal = 0f
        furacaoT = 0f
        dancaT = 0f
        ultimoAlvo = null
        contTiro = 0
        cdId.clear()
        est.clear()
        flechas.clear()
        laminas.clear()
        zonas.clear()
        feixes.clear()
        quedas.clear()
        fantasmas.clear()
        parts.clear()
        agenda.clear()
    }

    fun salvar() {
        ctx?.let { st.save(it) }
    }

    /** Recalcula os bônus das passivas (chamar depois de mexer na árvore). */
    fun recalc() {
        val fio = st.rank("fio")
        critCh = 0.06f * fio
        critMul = 1.6f + 0.10f * fio
        distB = 0.08f * st.rank("predador")
        sopro = 0.4f * st.rank("sopro")
        spdP = 0.05f * st.rank("penas")
        dancaB = 0.15f * st.rank("danca")
        fugaS = 0.08f * st.rank("fuga")
        arRar = 0.06f * st.rank("rarefeito")
        val cor = st.rank("corrente")
        corrArea = 0.08f * cor
        corrDur = 0.10f * cor
        calmoB = 0.06f * st.rank("calmo")
        mira = 0
    }

    private fun talentoSlot(slot: Int): Talento? {
        val id = st.slots.getOrNull(slot - 1) ?: return null
        return if (id.isEmpty()) null else SylvCatalog.porId[id]
    }

    /** Talento equipado no slot (1 a 5) ou null. */
    fun talento(slot: Int): Talento? = talentoSlot(slot)

    /** Segundos que faltam de recarga da skill do slot. */
    fun cd(slot: Int): Float {
        val t = talentoSlot(slot) ?: return 0f
        return (((cdId[t.id] ?: 0L) - System.currentTimeMillis()) / 1000f).coerceAtLeast(0f)
    }

    private fun vivos(): List<Enemy> = host.inimigos.filter { it.alive }

    private fun curar(v: Float) {
        val p = host.jogador ?: return
        if (!p.alive || v <= 0f) return
        p.hp = min(p.maxHp, p.hp + v)
    }

    fun onKill(e: Enemy) {
        est.remove(e)
        if (e === alvo) alvo = null
        impeto = min(100f, impeto + 8f)
        val subiu = st.addXp(12 + 10 * e.tipo)
        val p = host.jogador
        if (subiu && p != null) {
            host.texto(p.x, p.y - 90f - p.alt, "NÍVEL " + st.nivel + "!", 0xFFFFD54F.toInt())
            host.som("ui_ping", 0.8f)
            puff(p.x, p.y - 30f - p.alt, 18, 0xFFFFD54F.toInt(), 160f)
            if (st.pontos() > 0) host.texto(p.x, p.y - 118f - p.alt, "+1 ponto de talento", 0xFFB9F6CA.toInt())
        }
        salvar()
    }

    // ───────────── partículas ─────────────

    private fun puff(x: Float, y: Float, n: Int, cor: Int, vel: Float) {
        if (parts.size > 380) return
        for (i in 0 until n) {
            val an = rnd() * 6.2831855f
            val v = vel * (0.4f + rnd() * 0.6f)
            parts.add(Part(x, y, cos(an) * v, sin(an) * v * 0.7f, 0.35f + rnd() * 0.3f, 0.65f, 2f + rnd() * 2.5f, cor, rnd() < 0.5f))
        }
    }

    private fun faixa(x: Float, y: Float, dx: Float, dy: Float, cor: Int) {
        if (parts.size > 380) return
        parts.add(Part(x, y, dx * (60f + rnd() * 60f), dy * (60f + rnd() * 60f), 0.3f, 0.3f, 2f, cor, true))
    }

    // ───────────── Ímpeto ─────────────

    private fun addImpeto(v: Float) {
        impeto = min(100f, impeto + v)
    }

    // ───────────── dano ─────────────

    private fun hurt(e: Enemy, m: Float, kb: Float = 0.5f, sil: Boolean = false): Float {
        val p = host.jogador ?: return 0f
        var d = p.danoH * m
        d *= 1f + 0.25f * impeto / 100f
        if (distB > 0f && hypot(e.x - p.x, e.y - p.y) > 160f) d *= 1f + distB
        val s = est[e]
        if (arRar > 0f && (e.slowT > 0f || (s != null && s.deseqT > 0f))) d *= 1f + arRar
        if (calmoB > 0f && naZona) d *= 1f + calmoB
        if (critCh > 0f && Math.random() < critCh.toDouble()) {
            d *= critMul
            soproLetal()
        }
        host.dano(e, d, p.x, p.y, kb, sil)
        return d
    }

    /** Sopro Letal: crítico corta a recarga de uma skill. Chamado também pelos críticos do jogo base. */
    fun aoCritar() {
        soproLetal()
    }

    private fun soproLetal() {
        if (sopro <= 0f) return
        val now = System.currentTimeMillis()
        val cand = st.slots.filter { it.isNotEmpty() && (cdId[it] ?: 0L) > now }
        if (cand.isEmpty()) return
        val id = cand[(rnd() * cand.size).toInt().coerceAtMost(cand.size - 1)]
        cdId[id] = max(now, (cdId[id] ?: 0L) - (sopro * 1000f).toLong())
    }

    // ───────────── alvo ─────────────

    private fun dist(a: Enemy, p: Player): Float = hypot(a.x - p.x, a.y - p.y)

    private fun acharAlvo(alc: Float): Enemy? {
        val p = host.jogador ?: return null
        val a = alvo
        if (a != null && a.alive && dist(a, p) <= alc) return a
        var best: Enemy? = null
        var bd = alc
        for (e in vivos()) {
            val d = dist(e, p)
            if (d <= bd) {
                bd = d
                best = e
            }
        }
        if (best != null) alvo = best
        return best
    }

    private fun face(p: Player, tx: Float, ty: Float) {
        val dx = tx - p.x
        val dy = ty - p.y
        val d = hypot(dx, dy)
        if (d > 1f) {
            p.facingX = dx / d
            p.facingY = dy / d
        }
    }

    /** Toque no mundo: marca o alvo ou, no modo de mira, move o círculo. */
    fun tocar(wx: Float, wy: Float): Boolean {
        if (host.jogador == null) return false
        var best: Enemy? = null
        var bd = 1e9f
        for (e in vivos()) {
            val d = hypot(e.x - wx, e.y - 22f - e.alt - wy)
            if (d < e.radius + 30f && d < bd) {
                bd = d
                best = e
            }
        }
        if (mira != 0) {
            setMira(best?.x ?: wx, best?.y ?: wy)
            if (best != null) alvo = best
            return true
        }
        if (best != null) {
            alvo = best
            host.som("ui_ping", 0.4f)
            return true
        }
        alvo = null
        return false
    }

    private fun raioDe(t: Talento): Float = t.raio * (1f + corrArea)

    private fun setMira(x: Float, y: Float) {
        val p = host.jogador ?: return
        val t = talentoSlot(mira) ?: return
        val alc = t.alcance * ALC
        val dx = x - p.x
        val dy = y - p.y
        val d = hypot(dx, dy)
        if (d > alc && d > 0.001f) {
            mx = p.x + dx / d * alc
            my = p.y + dy / d * alc
        } else {
            mx = x
            my = y
        }
    }

    // ───────────── ataque básico ─────────────

    /** Flecha Viva: dispara uma flecha guiada em espiral no alvo. false se não há inimigo ao alcance. */
    fun tiroBasico(pw: Float): Boolean {
        val t = acharAlvo(200f * ALC) ?: return false
        val p = host.jogador ?: return false
        face(p, t.x, t.y)
        if (t === ultimoAlvo) contTiro++ else {
            ultimoAlvo = t
            contTiro = 1
        }
        val terceiro = contTiro >= 3
        if (terceiro) contTiro = 0
        val danca = dancaT > 0f
        dancaT = 0f
        flecha(t, COR_VENTO, 540f, true) { e ->
            var m = 1f + 0.5f * pw
            if (terceiro) m *= 1.3f
            if (danca) m *= 1f + dancaB
            hurt(e, m, 0.6f)
            addImpeto(if (terceiro) 8f else 4f)
            if (danca) addImpeto(10f * st.rank("danca"))
            puff(e.x, e.y - 18f - e.alt, 5, COR_VENTO, 90f)
        }
        return true
    }

    private fun flecha(t: Enemy, cor: Int, vel: Float, espiral: Boolean, lateral: Float = 0f, onHit: (Enemy) -> Unit) {
        val p = host.jogador ?: return
        val nx = -p.facingY
        val ny = p.facingX
        flechas.add(Flecha(p.x + p.facingX * 20f + nx * lateral, p.y + p.facingY * 20f + ny * lateral, t, cor, vel, espiral, onHit))
    }

    // ───────────── usar skills ─────────────

    /** Devolve null se deu certo, ou o motivo da falha. */
    fun usar(slot: Int): String? {
        val p = host.jogador ?: return ""
        if (!p.alive) return ""
        val t = talentoSlot(slot) ?: return "Slot vazio: abra os talentos"
        val rk = st.rank(t.id)
        if (rk == 0) return "Não aprendida"
        if (System.currentTimeMillis() < (cdId[t.id] ?: 0L)) return "Recarregando"
        if (t.alvo == Alvo.AREA) {
            if (mira == slot) {
                val x = mx
                val y = my
                mira = 0
                executar(t, rk, null, x, y)
                return null
            }
            mira = slot
            miraT = 0f
            val a = alvo
            if (a != null && a.alive) setMira(a.x, a.y) else setMira(p.x + p.facingX * 120f, p.y + p.facingY * 120f)
            return null
        }
        mira = 0
        var alvoE: Enemy? = null
        if (t.alvo == Alvo.INIMIGO) {
            alvoE = acharAlvo(t.alcance * ALC) ?: return "Sem alvo ao alcance"
        }
        executar(t, rk, alvoE, 0f, 0f)
        return null
    }

    private fun executar(t: Talento, rk: Int, alvoE: Enemy?, ax: Float, ay: Float) {
        val p = host.jogador ?: return
        val now = System.currentTimeMillis()
        val rm = 1f + 0.15f * (rk - 1)
        cdId[t.id] = now + (t.cdMs * (1f - 0.07f * (rk - 1))).toLong()
        var atraso = 0f
        if (t.alvo == Alvo.INIMIGO && alvoE != null) face(p, alvoE.x, alvoE.y)
        else if (t.alvo == Alvo.AREA) face(p, ax, ay)
        val atira = t.alvo == Alvo.INIMIGO || t.alvo == Alvo.AREA || t.id == "lamina"
        if (atira && p.canAttack(now)) {
            p.doAttack(now)
            atraso = p.shotDelay()
        }
        if (dancaB > 0f) dancaT = 4f
        if (atraso > 0f) agenda.add(Agenda(atraso) { efeito(t, rk, rm, alvoE, ax, ay) })
        else efeito(t, rk, rm, alvoE, ax, ay)
    }

    private fun zona(tipo: Int, x: Float, y: Float, raio: Float, vida: Float, cor: Int, rank: Float): Zona {
        val z = Zona(tipo, x, y, raio, vida, cor, rank)
        zonas.add(z)
        return z
    }

    private fun limitar(max: Int, vararg tipos: Int) {
        while (zonas.count { it.tipo in tipos } >= max) {
            val velha = zonas.firstOrNull { it.tipo in tipos } ?: break
            zonas.remove(velha)
        }
    }

    private fun empurrar(e: Enemy, dx: Float, dy: Float) {
        val nx = e.x + dx
        val ny = e.y + dy
        if (host.livre(nx, ny)) {
            e.x = nx
            e.y = ny
        }
    }

    /** Joga o inimigo para longe de (ox, oy) em passos curtos, parando na parede. */
    private fun arremessar(e: Enemy, ox: Float, oy: Float, dist: Float) {
        val dx = e.x - ox
        val dy = e.y - oy
        val d = hypot(dx, dy).coerceAtLeast(1f)
        var passos = (dist / 8f).toInt()
        while (passos > 0) {
            val nx = e.x + dx / d * 8f
            val ny = e.y + dy / d * 8f
            if (!host.livre(nx, ny)) break
            e.x = nx
            e.y = ny
            passos--
        }
    }

    /** Direção do dash: analógico se estiver mexendo; senão, para longe do alvo; senão, para onde olha. */
    private fun dirDash(p: Player): FloatArray {
        val ix = p.inX
        val iy = p.inY
        val m = hypot(ix, iy)
        if (m > 0.3f) return floatArrayOf(ix / m, iy / m)
        val a = alvo
        if (a != null && a.alive) {
            val dx = p.x - a.x
            val dy = p.y - a.y
            val d = hypot(dx, dy)
            if (d > 1f) return floatArrayOf(dx / d, dy / d)
        }
        return floatArrayOf(p.facingX, p.facingY)
    }

    private fun dash(p: Player, dx: Float, dy: Float, dist: Float, cor: Int, rastroRank: Float) {
        p.facingX = dx
        p.facingY = dy
        puff(p.x, p.y - 8f - p.alt, 10, cor, 130f)
        var passos = 0
        val total = (dist / 10f).toInt()
        while (passos < total) {
            val nx = p.x + dx * 10f
            val ny = p.y + dy * 10f
            if (!host.livre(nx, ny)) break
            p.x = nx
            p.y = ny
            passos++
            if (passos % 3 == 0) fantasmas.add(Fantasma(p.x, p.y - p.alt, cor, 0.4f))
            if (rastroRank > 0f && passos % 4 == 0) zona(Z_RASTRO, p.x, p.y, 42f, 4f * (1f + corrDur), cor, rastroRank)
            if (passos % 2 == 0) faixa(p.x, p.y - 20f - p.alt, -dx, -dy, cor)
        }
        puff(p.x, p.y - 8f - p.alt, 10, cor, 130f)
        host.som("hop", 0.8f)
    }

    private fun efeito(t: Talento, rk: Int, rm: Float, alvoE: Enemy?, ax: Float, ay: Float) {
        val p = host.jogador ?: return
        if (!p.alive) return
        val cor = t.ramo.cor
        val raio = raioDe(t)
        val dur = 1f + corrDur
        when (t.id) {
            // ── Lâmina do Vendaval ──
            "ciclonica" -> if (alvoE != null) {
                flecha(alvoE, cor, 580f, true) { e ->
                    hurt(e, 1.3f * rm)
                    addImpeto(6f)
                    puff(e.x, e.y - 18f - e.alt, 8, cor, 120f)
                }
                host.som("bow_shot", 0.9f)
            }
            "triplo" -> if (alvoE != null) {
                for (i in 0..2) {
                    agenda.add(Agenda(0.08f * i + 0.01f) {
                        if (alvoE.alive) flecha(alvoE, cor, 620f, true, (i - 1) * 16f) { e ->
                            hurt(e, 0.75f * rm, 0.3f)
                            addImpeto(3f)
                            puff(e.x, e.y - 18f - e.alt, 5, cor, 90f)
                        }
                    })
                }
                host.som("bow_shot", 0.9f)
            }
            "lamina" -> {
                var dx = p.facingX
                var dy = p.facingY
                val a = alvo
                if (a != null && a.alive) {
                    val d = hypot(a.x - p.x, a.y - p.y)
                    if (d > 1f) {
                        dx = (a.x - p.x) / d
                        dy = (a.y - p.y) / d
                    }
                }
                laminas.add(Lamina(p.x + dx * 26f, p.y + dy * 26f, dx, dy, t.alcance, cor, rm))
                addImpeto(8f)
                host.som("thunder_fire", 0.7f)
            }
            "chuvalam" -> {
                zona(Z_CHUVA, ax, ay, raio, 3f * dur, cor, rm)
                addImpeto(10f)
                host.som("rain_cast", 0.8f)
            }
            "execucao" -> if (alvoE != null) {
                val z = zona(Z_EXEC, p.x, p.y, 40f, 1.3f, cor, rm)
                z.alvoE = alvoE
                host.som("thunder_cast", 1f)
            }
            // ── Dançarino das Brisas ──
            "passo" -> {
                ventoT = 4f + 0.5f * (rk - 1)
                addImpeto(30f)
                host.texto(p.x, p.y - 70f - p.alt, "PASSO DO VENTO", cor)
                puff(p.x, p.y - 15f - p.alt, 14, cor, 150f)
                host.som("hop", 0.9f)
            }
            "salto" -> {
                val d = dirDash(p)
                dash(p, d[0], d[1], 170f, cor, 0f)
                var matou = false
                for (e in vivos()) {
                    if (hypot(e.x - p.x, e.y - p.y) < raio + e.radius * 0.5f) {
                        hurt(e, 1.4f * rm, 1.2f)
                        arremessar(e, p.x, p.y, 40f)
                        if (!e.alive) matou = true
                    }
                }
                zona(Z_ONDA, p.x, p.y, raio, 0.5f, cor, 1f)
                host.tremor(0.15f)
                addImpeto(15f)
                if (matou) {
                    cdId[t.id] = 0L
                    host.texto(p.x, p.y - 70f - p.alt, "RESETOU", cor)
                }
            }
            "manto" -> {
                mantoVal = p.maxHp * 0.25f * rm
                p.shield += mantoVal
                mantoT = 4f
                host.texto(p.x, p.y - 70f - p.alt, "MANTO DE BRISA", cor)
                puff(p.x, p.y - 20f - p.alt, 16, cor, 140f)
                host.som("ui_ping", 0.8f)
            }
            "rastro" -> {
                val d = dirDash(p)
                dash(p, d[0], d[1], 150f, cor, rm)
                addImpeto(12f)
            }
            "furacao" -> {
                furacaoT = 6f
                furacaoTick = 0f
                impeto = 100f
                host.texto(p.x, p.y - 80f - p.alt, "FURACÃO!", cor)
                host.tremor(0.3f)
                host.som("thunder_cast", 0.9f)
            }
            // ── Olho do Furacão ──
            "repulsora" -> if (alvoE != null) {
                flecha(alvoE, cor, 580f, true) { e ->
                    hurt(e, 0.9f * rm, 0f)
                    arremessar(e, p.x, p.y, 90f)
                    e.slowT = max(e.slowT, 2.5f)
                    est(e).deseqT = 4f
                    puff(e.x, e.y - 18f - e.alt, 10, cor, 150f)
                }
                host.som("bow_shot", 0.9f)
            }
            "redemoinho" -> {
                zona(Z_REDEMOINHO, ax, ay, raio, 5f * dur, cor, rm)
                addImpeto(10f)
                host.som("rain_cast", 0.8f)
            }
            "tornado" -> {
                limitar(2, Z_TORNADO)
                zona(Z_TORNADO, ax, ay, raio, 6f * dur, cor, rm)
                addImpeto(12f)
                host.som("thunder_cast", 0.8f)
            }
            "implosao" -> {
                zona(Z_IMPLOSAO, ax, ay, raio, 1.3f, cor, rm)
                addImpeto(12f)
                host.som("rain_cast", 0.9f)
            }
            "grande" -> {
                zona(Z_GRANDE, ax, ay, raio, 8f * dur, cor, rm)
                addImpeto(25f)
                host.tremor(0.3f)
                host.som("thunder_cast", 1f)
            }
        }
    }

    // ───────────── atualização ─────────────

    fun update(dt: Float, p: Player) {
        relogio += dt
        for (a in agenda.toList()) {
            a.t -= dt
            if (a.t <= 0f) {
                agenda.remove(a)
                a.acao()
            }
        }
        val a0 = alvo
        if (a0 != null && !a0.alive) alvo = null
        if (mira != 0) {
            miraT += dt
            if (miraT > 8f) mira = 0 else setMira(mx, my)
        }

        val movendo = hypot(p.inX, p.inY) > 0.2f
        if (movendo) parado = 0f else parado += dt
        val ferido = p.hp < p.maxHp * 0.45f

        // Ímpeto: sobe correndo, cai parado
        impeto = when {
            furacaoT > 0f -> 100f
            movendo -> min(100f, impeto + 16f * dt)
            ferido && fugaS > 0f -> impeto
            else -> max(0f, impeto - 30f * dt)
        }

        // buffs de velocidade
        var sp = 1f + spdP
        sp *= 1f + 0.2f * impeto / 100f
        if (ventoT > 0f) {
            ventoT -= dt
            sp *= 1.45f
        }
        if (mantoT > 0f) {
            mantoT -= dt
            sp *= 1.2f
            if (mantoT <= 0f) {
                p.shield = max(0f, p.shield - mantoVal)
                mantoVal = 0f
            }
        }
        if (ferido && fugaS > 0f) sp *= 1f + fugaS
        if (furacaoT > 0f) {
            furacaoT -= dt
            sp *= 1.5f
            furacaoTick -= dt
            if (furacaoTick <= 0f) {
                furacaoTick += 0.4f
                val rm = 1f + 0.15f * (st.rank("furacao") - 1).coerceAtLeast(0)
                for (e in vivos()) {
                    if (hypot(e.x - p.x, e.y - p.y) < 110f + e.radius * 0.5f) {
                        hurt(e, 0.5f * rm, 0.9f, true)
                        arremessar(e, p.x, p.y, 14f)
                    }
                }
                puff(p.x, p.y - 12f - p.alt, 6, COR_VENTO, 200f)
            }
        }
        p.spdMul = sp
        if (dancaT > 0f) dancaT -= dt

        // rastro de vento enquanto corre rápido
        emitT -= dt
        if (emitT <= 0f && movendo && (impeto > 55f || ventoT > 0f || furacaoT > 0f)) {
            emitT = 0.05f
            faixa(p.x - p.inX * 10f, p.y - 22f - p.alt + (rnd() - 0.5f) * 26f, -p.inX, -p.inY, COR_VENTO)
        }

        naZona = false
        for (z in zonas) {
            if (z.tipo == Z_ONDA || z.tipo == Z_EXEC) continue
            if (hypot(p.x - z.x, p.y - z.y) < z.raio) naZona = true
        }

        updFlechas(dt)
        updLaminas(dt)
        for (z in zonas.toList()) updZona(z, dt, p)
        zonas.removeAll { it.vida <= 0f }
        for (f in feixes) f.vida -= dt
        feixes.removeAll { it.vida <= 0f }
        for (q in quedas) q.t += dt
        quedas.removeAll { it.t > 0.35f }
        for (f in fantasmas) f.vida -= dt
        fantasmas.removeAll { it.vida <= 0f }
        for (pt in parts) {
            pt.vida -= dt
            pt.x += pt.vx * dt
            pt.y += pt.vy * dt
            pt.vx *= 0.96f
            pt.vy *= 0.96f
        }
        parts.removeAll { it.vida <= 0f }
        for (e in est.keys.toList()) {
            val s = est[e] ?: continue
            if (!e.alive) {
                est.remove(e)
                continue
            }
            if (s.deseqT > 0f) s.deseqT -= dt
        }
    }

    private fun updFlechas(dt: Float) {
        for (f in flechas.toList()) {
            if (!f.vivo) continue
            f.t += dt
            if (!f.alvo.alive || f.t > 2.5f) {
                f.vivo = false
                continue
            }
            val dx = f.alvo.x - f.x
            val dy = f.alvo.y - f.y
            val d = hypot(dx, dy)
            f.ang = atan2(dy, dx)
            val passo = f.vel * dt
            if (f.espiral) faixa(f.x, f.y - 22f - f.alvo.alt, -cos(f.ang), -sin(f.ang), f.cor)
            if (d <= passo + f.alvo.radius * 0.6f) {
                f.vivo = false
                f.onHit(f.alvo)
                host.som("hit_arrow", 0.6f)
            } else {
                f.x += dx / d * passo
                f.y += dy / d * passo
            }
        }
        flechas.removeAll { !it.vivo }
    }

    private fun updLaminas(dt: Float) {
        for (l in laminas.toList()) {
            val passo = 480f * dt
            val nx = l.x + l.dx * passo
            val ny = l.y + l.dy * passo
            if (!host.livre(nx, ny)) {
                l.vivo = false
                puff(l.x, l.y - 22f, 10, l.cor, 160f)
                continue
            }
            l.x = nx
            l.y = ny
            l.perc += passo
            faixa(l.x, l.y - 22f, -l.dx, -l.dy, l.cor)
            for (e in vivos()) {
                if (e in l.acertou) continue
                if (hypot(e.x - l.x, e.y - l.y) < e.radius + 22f) {
                    l.acertou.add(e)
                    hurt(e, 1.8f * l.rank, 0.8f)
                    puff(e.x, e.y - 18f - e.alt, 8, l.cor, 150f)
                }
            }
            if (l.perc >= l.alcance) l.vivo = false
        }
        laminas.removeAll { !it.vivo }
    }

    private fun dentro(z: Zona, e: Enemy): Boolean = hypot(e.x - z.x, e.y - z.y) < z.raio + e.radius * 0.5f

    private fun updZona(z: Zona, dt: Float, p: Player) {
        z.idade += dt
        z.vida -= dt
        z.tick -= dt
        val per = if (z.tipo == Z_CHUVA) 0.25f else 0.5f
        val tk = z.tick <= 0f
        if (tk) z.tick += per
        val ini = vivos()
        when (z.tipo) {
            Z_CHUVA -> if (tk && z.vida > 0f) {
                val an = rnd() * 6.2831855f
                val rr = z.raio * kotlin.math.sqrt(rnd())
                val px = z.x + cos(an) * rr
                val py = z.y + sin(an) * rr * 0.8f
                quedas.add(Queda(px, py, z.cor))
                agenda.add(Agenda(0.2f) {
                    for (e in vivos()) if (hypot(e.x - px, e.y - py) < 48f + e.radius * 0.5f) hurt(e, 0.55f * z.rank, 0.3f, true)
                    puff(px, py - 6f, 4, z.cor, 120f)
                })
            }
            Z_REDEMOINHO -> {
                for (e in ini) {
                    if (!dentro(z, e)) continue
                    val dx = e.x - z.x
                    val dy = e.y - z.y
                    val d = hypot(dx, dy).coerceAtLeast(1f)
                    empurrar(e, dx / d * 110f * dt, dy / d * 110f * dt)
                    est(e).deseqT = 1f
                    if (tk) hurt(e, 0.2f * z.rank, 0f, true)
                }
                orbitar(z, 0.6f)
            }
            Z_TORNADO, Z_GRANDE -> if (!z.armada) {
                if (z.vida > 0f) {
                    val grande = z.tipo == Z_GRANDE
                    if (!grande) {
                        var alvoT: Enemy? = null
                        var bd = 380f
                        for (e in ini) {
                            val d = hypot(e.x - z.x, e.y - z.y)
                            if (d < bd) {
                                bd = d
                                alvoT = e
                            }
                        }
                        if (alvoT != null && bd > 20f) {
                            val nx = z.x + (alvoT.x - z.x) / bd * 55f * dt
                            val ny = z.y + (alvoT.y - z.y) / bd * 55f * dt
                            if (host.livre(nx, ny)) {
                                z.x = nx
                                z.y = ny
                            }
                        }
                    }
                    for (e in ini) {
                        if (!dentro(z, e)) continue
                        val dx = z.x - e.x
                        val dy = z.y - e.y
                        val d = hypot(dx, dy)
                        if (d > 24f) empurrar(e, dx / d * (if (grande) 75f else 42f) * dt, dy / d * (if (grande) 75f else 42f) * dt)
                        est(e).deseqT = 1f
                        if (tk) hurt(e, (if (grande) 0.4f else 0.3f) * z.rank, 0f, true)
                    }
                    orbitar(z, if (grande) 1.6f else 1f)
                } else {
                    z.armada = true
                    val grande = z.tipo == Z_GRANDE
                    for (e in ini) {
                        if (hypot(e.x - z.x, e.y - z.y) < z.raio * 1.2f + e.radius * 0.5f) {
                            hurt(e, (if (grande) 2.5f else 1.5f) * z.rank, 0.5f)
                            arremessar(e, z.x, z.y, if (grande) 130f else 90f)
                        }
                    }
                    zona(Z_ONDA, z.x, z.y, z.raio * 1.2f, 0.5f, z.cor, 1f)
                    puff(z.x, z.y - 20f, 22, z.cor, 260f)
                    host.tremor(if (grande) 0.5f else 0.3f)
                    host.som("thunder_fire", 0.9f)
                    z.vida = 0.4f
                }
            }
            Z_IMPLOSAO -> if (!z.armada) {
                if (z.idade < 0.9f) {
                    for (e in ini) {
                        val dx = z.x - e.x
                        val dy = z.y - e.y
                        val d = hypot(dx, dy)
                        if (d < z.raio * 1.1f + e.radius && d > 8f) empurrar(e, dx / d * 150f * dt, dy / d * 150f * dt)
                    }
                    if (rnd() < 0.5f) {
                        val an = rnd() * 6.2831855f
                        val rr = z.raio
                        faixa(z.x + cos(an) * rr, z.y + sin(an) * rr * 0.7f - 10f, -cos(an), -sin(an) * 0.7f, z.cor)
                    }
                } else {
                    z.armada = true
                    for (e in ini) {
                        if (hypot(e.x - z.x, e.y - z.y) < z.raio * 0.8f + e.radius * 0.5f) {
                            hurt(e, 3.0f * z.rank, 0.4f)
                            e.stunT = max(e.stunT, 1.2f)
                        }
                    }
                    zona(Z_ONDA, z.x, z.y, z.raio, 0.5f, z.cor, 1f)
                    puff(z.x, z.y - 14f, 24, z.cor, 280f)
                    host.tremor(0.4f)
                    host.som("thunder_fire", 1f)
                    z.vida = 0.4f
                }
            }
            Z_RASTRO -> if (tk) {
                for (e in ini) if (dentro(z, e)) {
                    e.slowT = max(e.slowT, 0.8f)
                    est(e).deseqT = 1f
                    hurt(e, 0.22f * z.rank, 0f, true)
                }
            }
            Z_EXEC -> {
                val a = z.alvoE
                if (a != null && a.alive) {
                    z.x = p.x
                    z.y = p.y
                    face(p, a.x, a.y)
                }
                if (!z.armada && z.idade >= 0.9f) {
                    z.armada = true
                    if (a != null && a.alive) {
                        val fraco = a.hp < a.maxHp * 0.35f
                        feixes.add(Feixe(p.x + p.facingX * 20f, p.y - 24f - p.alt, a.x, a.y - 20f - a.alt, z.cor, 0.5f, 16f))
                        hurt(a, 6.5f * z.rank * (if (fraco) 1.6f else 1f), 2f)
                        puff(a.x, a.y - 20f - a.alt, 26, z.cor, 300f)
                        host.tremor(0.5f)
                        host.som("thunder_fire", 1f)
                        if (fraco) host.texto(a.x, a.y - a.radius - 50f - a.alt, "EXECUTADO", z.cor)
                    }
                    z.vida = 0.4f
                }
            }
            Z_ONDA -> {}
        }
    }

    /** Detritos girando em volta de um tornado ou redemoinho. */
    private fun orbitar(z: Zona, f: Float) {
        if (rnd() < 0.35f * f) {
            val an = rnd() * 6.2831855f
            val rr = z.raio * (0.3f + rnd() * 0.7f)
            val vx = -sin(an) * 140f
            val vy = cos(an) * 100f
            if (parts.size < 380) parts.add(Part(z.x + cos(an) * rr, z.y + sin(an) * rr * 0.7f - rnd() * 60f, vx, vy, 0.5f, 0.5f, 2f + rnd() * 2f, WindArt.a(BRANCO, 0.8f), rnd() < 0.5f))
        }
    }

    // ───────────── desenho ─────────────

    private fun oval(c: Canvas, p: Paint, sx: Float, sy: Float, r: Float, fill: Int, stroke: Int, w: Float) {
        p.style = Paint.Style.FILL
        p.color = fill
        c.drawOval(sx - r, sy - r * 0.72f, sx + r, sy + r * 0.72f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = w
        p.color = stroke
        c.drawOval(sx - r, sy - r * 0.72f, sx + r, sy + r * 0.72f, p)
        p.style = Paint.Style.FILL
    }

    /** Chão (antes dos personagens): anel do alvo, zonas e círculo de mira. */
    fun drawGround(c: Canvas, p: Paint, camX: Float, camY: Float, time: Float) {
        val pl = host.jogador
        val t = alvo
        if (t != null && t.alive) {
            val sx = t.x - camX
            val feet = t.y - camY + t.radius * 0.55f - t.alt
            val pulso = 1f + 0.06f * sin(time * 6f)
            oval(c, p, sx, feet, t.radius * 1.35f * pulso, 0x22FF5252, 0xFFFF5252.toInt(), 3f)
        }
        for (f in fantasmas) {
            val k = (f.vida / 0.4f).coerceIn(0f, 1f)
            val sx = f.x - camX
            val sy = f.y - camY
            p.style = Paint.Style.FILL
            p.color = WindArt.a(f.cor, 0.4f * k)
            c.drawCircle(sx, sy - 38f, 9f, p)
            c.drawRect(sx - 8f, sy - 30f, sx + 8f, sy - 4f, p)
        }
        for (z in zonas) drawZona(c, p, z, camX, camY, time)
        val def = talentoSlot(mira)
        if (pl != null && def != null && mira != 0) {
            val sx = mx - camX
            val sy = my - camY
            val cor = def.ramo.cor
            val r = raioDe(def)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2f
            p.color = WindArt.a(cor, 0.5f)
            c.drawLine(pl.x - camX, pl.y - camY, sx, sy, p)
            oval(c, p, sx, sy, r, WindArt.a(cor, 0.16f), WindArt.a(cor, 0.9f), 4f)
            WindArt.anel(c, p, sx, sy, r * 0.8f, time, cor, 0.9f, 3f)
            oval(c, p, sx, sy, r * (0.4f + 0.08f * sin(time * 8f)), 0x00000000, WindArt.a(cor, 0.6f), 2f)
        }
    }

    private fun drawZona(c: Canvas, p: Paint, z: Zona, camX: Float, camY: Float, time: Float) {
        val sx = z.x - camX
        val sy = z.y - camY
        val fade = min(1f, z.vida / 0.6f)
        when (z.tipo) {
            Z_CHUVA -> {
                oval(c, p, sx, sy, z.raio, WindArt.a(z.cor, 0.12f * fade), WindArt.a(z.cor, 0.6f * fade), 3f)
                WindArt.anel(c, p, sx, sy, z.raio * 0.9f, time, z.cor, 0.8f * fade, 3f)
            }
            Z_REDEMOINHO -> {
                oval(c, p, sx, sy, z.raio, WindArt.a(z.cor, 0.12f * fade), WindArt.a(z.cor, 0.6f * fade), 3f)
                WindArt.funil(c, p, sx, sy, z.raio * 0.7f, 70f, time, z.cor, fade)
            }
            Z_TORNADO -> {
                oval(c, p, sx, sy, z.raio * 0.9f, WindArt.a(z.cor, 0.14f * fade), WindArt.a(z.cor, 0.5f * fade), 3f)
                WindArt.funil(c, p, sx, sy, z.raio * 0.8f, 120f, time, z.cor, fade)
            }
            Z_GRANDE -> {
                oval(c, p, sx, sy, z.raio, WindArt.a(z.cor, 0.12f * fade), WindArt.a(z.cor, 0.5f * fade), 4f)
                WindArt.anel(c, p, sx, sy, z.raio, time, z.cor, 0.8f * fade, 4f)
                WindArt.funil(c, p, sx, sy, z.raio * 0.75f, 200f, time, z.cor, fade)
                WindArt.funil(c, p, sx, sy, z.raio * 0.5f, 170f, time * 1.3f + 1f, WindArt.BRANCO, fade * 0.6f)
            }
            Z_IMPLOSAO -> {
                val k = (z.idade / 0.9f).coerceIn(0f, 1f)
                oval(c, p, sx, sy, z.raio, WindArt.a(z.cor, 0.1f + 0.2f * k), WindArt.a(z.cor, 0.9f), 4f)
                oval(c, p, sx, sy, z.raio * (1f - k), 0x00000000, WindArt.a(WindArt.BRANCO, 0.9f), 3f)
                WindArt.anel(c, p, sx, sy, z.raio * (1f - k * 0.6f), time * 2f, z.cor, 0.9f, 3f)
            }
            Z_RASTRO -> {
                oval(c, p, sx, sy, z.raio, WindArt.a(z.cor, 0.14f * fade), WindArt.a(z.cor, 0.5f * fade), 2f)
                WindArt.anel(c, p, sx, sy, z.raio * 0.7f, time * 1.5f, z.cor, 0.7f * fade, 2f)
            }
            Z_EXEC -> {
                val a = z.alvoE
                val k = (z.idade / 0.9f).coerceIn(0f, 1f)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 3f
                p.color = WindArt.a(z.cor, 0.4f + 0.5f * k)
                c.drawCircle(sx, sy - 24f, 50f * (1.2f - k), p)
                WindArt.anel(c, p, sx, sy - 24f, 40f * (1.1f - k * 0.5f), time * 2f, WindArt.BRANCO, 0.8f, 3f)
                if (a != null && a.alive) {
                    oval(c, p, a.x - camX, a.y - camY + a.radius * 0.55f - a.alt, a.radius * (2.2f - k), 0x00000000, WindArt.a(z.cor, 0.9f), 3f)
                }
            }
            Z_ONDA -> {
                val k = 1f - (z.vida / z.vida0).coerceIn(0f, 1f)
                oval(c, p, sx, sy, z.raio * k, WindArt.a(z.cor, 0.18f * (1f - k)), WindArt.a(z.cor, 0.9f * (1f - k)), 5f)
                WindArt.anel(c, p, sx, sy, z.raio * k, time * 2f, WindArt.BRANCO, 0.8f * (1f - k), 3f)
            }
        }
    }

    /** Acima do chão: flechas, lâminas, raios, quedas, partículas, aura do Sylvarion e tornados. */
    fun drawAir(c: Canvas, p: Paint, camX: Float, camY: Float, time: Float) {
        val pl = host.jogador
        for (q in quedas) {
            val k = (q.t / 0.2f).coerceIn(0f, 1f)
            val sx = q.x - camX
            val sy = q.y - camY
            if (q.t < 0.2f) {
                WindArt.crescente(c, p, sx, sy - 140f * (1f - k) - 10f, 24f, 90f, q.cor, 0.9f, 5f)
            } else {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 3f
                p.color = WindArt.a(q.cor, 1f - (q.t - 0.2f) / 0.15f)
                c.drawCircle(sx, sy, 10f + (q.t - 0.2f) * 160f, p)
            }
        }
        for (f in feixes) {
            val k = (f.vida / f.vida0).coerceIn(0f, 1f)
            val x1 = f.x1 - camX
            val y1 = f.y1 - camY
            val x2 = f.x2 - camX
            val y2 = f.y2 - camY
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = f.larg * (0.4f + k)
            p.color = WindArt.a(f.cor, 0.3f * k)
            c.drawLine(x1, y1, x2, y2, p)
            p.strokeCap = Paint.Cap.BUTT
            WindArt.espiral(c, p, x1, y1, x2, y2, time, f.cor, k, 16f * k + 4f, 6f)
            p.style = Paint.Style.FILL
            p.color = WindArt.a(WindArt.BRANCO, 0.6f * k)
            c.drawCircle(x2, y2, 22f * (1.4f - k), p)
        }
        for (l in laminas) {
            val ang = atan2(l.dy, l.dx) * DEG
            val sx = l.x - camX
            val sy = l.y - camY - 22f
            WindArt.crescente(c, p, sx, sy, 26f, ang, l.cor, 1f, 6f)
            WindArt.crescente(c, p, sx - l.dx * 22f, sy - l.dy * 22f, 22f, ang, l.cor, 0.45f, 4f)
        }
        for (f in flechas) {
            val sx = f.x - camX
            val sy = f.y - camY - 22f - f.alvo.alt
            if (f.espiral) {
                val bx = sx - cos(f.ang) * 56f
                val by = sy - sin(f.ang) * 56f
                WindArt.espiral(c, p, bx, by, sx, sy, time, f.cor, 0.9f, 5f, 3f)
            }
            c.save()
            c.translate(sx, sy)
            c.rotate(f.ang * DEG)
            p.style = Paint.Style.FILL
            p.color = 0xFF8D6E63.toInt()
            c.drawRect(-14f, -1.5f, 8f, 1.5f, p)
            p.color = WindArt.a(f.cor, 0.95f)
            c.drawRect(-14f, -3.5f, -9f, 3.5f, p)
            c.drawRect(8f, -3f, 15f, 3f, p)
            p.color = 0xFFFFFFFF.toInt()
            c.drawRect(11f, -1.5f, 17f, 1.5f, p)
            c.restore()
        }
        // aura do Sylvarion
        if (pl != null && pl.alive) {
            val px = pl.x - camX
            val py = pl.y - camY - 4f - pl.alt
            if (ventoT > 0f) WindArt.anel(c, p, px, py, 34f, time, ramoCor(Ramo.BRISA), 0.9f, 3f)
            if (mantoT > 0f) {
                WindArt.anel(c, p, px, py - 14f, 40f, time * 1.3f, WindArt.BRANCO, 0.8f, 4f)
                WindArt.anel(c, p, px, py - 30f, 34f, -time * 1.6f, ramoCor(Ramo.BRISA), 0.8f, 3f)
            }
            if (furacaoT > 0f) {
                WindArt.funil(c, p, px, py + 6f, 62f, 110f, time * 1.5f, ramoCor(Ramo.BRISA), 0.9f)
                WindArt.anel(c, p, px, py, 110f, time * 2f, COR_VENTO, 0.6f, 3f)
            }
            if (pl.shield > 0.5f && mantoT <= 0f) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 3f
                p.color = WindArt.a(COR_VENTO, 0.5f)
                c.drawCircle(px, py - 14f, 30f + 1.5f * sin(time * 5f), p)
            }
        }
        // marcas de desequilíbrio nos inimigos
        for ((e, s) in est) {
            if (!e.alive || s.deseqT <= 0f) continue
            val sx = e.x - camX
            val topo = e.y - camY - e.radius * 2.7f - e.alt
            WindArt.anel(c, p, sx, topo, 12f, time * 2f, ramoCor(Ramo.OLHO), 0.9f, 2.5f)
        }
        for (pt in parts) {
            val k = (pt.vida / pt.vida0).coerceIn(0f, 1f)
            val sx = pt.x - camX
            val sy = pt.y - camY
            if (pt.linha) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = pt.tam * 0.8f
                p.color = WindArt.a(pt.cor, k)
                c.drawLine(sx, sy, sx - pt.vx * 0.05f, sy - pt.vy * 0.05f, p)
            } else {
                p.style = Paint.Style.FILL
                p.color = WindArt.a(pt.cor, k)
                c.drawCircle(sx, sy, pt.tam * k, p)
            }
        }
        p.style = Paint.Style.FILL
    }

    private fun ramoCor(r: Ramo): Int = r.cor

    /** Barra de Ímpeto e XP no topo da tela (fora do zoom). */
    fun drawHud(c: Canvas, p: Paint, w: Float, @Suppress("UNUSED_PARAMETER") h: Float) {
        val bw = min(260f, w * 0.34f)
        val x0 = w * 0.5f - bw / 2f
        val y0 = 70f
        val k = (impeto / 100f).coerceIn(0f, 1f)
        p.style = Paint.Style.FILL
        p.color = 0xFF111111.toInt()
        c.drawRoundRect(x0 - 2f, y0 - 2f, x0 + bw + 2f, y0 + 14f, 5f, 5f, p)
        p.color = 0xFF2A2A2A.toInt()
        c.drawRect(x0, y0, x0 + bw, y0 + 12f, p)
        p.color = if (furacaoT > 0f) 0xFFFFFFFF.toInt() else COR_VENTO
        c.drawRect(x0, y0, x0 + bw * k, y0 + 12f, p)
        p.color = 0x55FFFFFF
        c.drawRect(x0, y0, x0 + bw * k, y0 + 4f, p)
        val xpN = if (st.nivel >= SylvCatalog.NIVEL_MAX) 1f else st.xp / st.xpPara(st.nivel).toFloat()
        p.color = 0xFF111111.toInt()
        c.drawRect(x0, y0 + 16f, x0 + bw, y0 + 21f, p)
        p.color = 0xFFFFD54F.toInt()
        c.drawRect(x0, y0 + 16f, x0 + bw * xpN.coerceIn(0f, 1f), y0 + 21f, p)
        p.textAlign = Paint.Align.CENTER
        p.textSize = 22f
        val pts = st.pontos()
        val txt = "Ímpeto " + impeto.toInt() + "   •   Nv " + st.nivel + (if (pts > 0) "  (+" + pts + " pts: toque no emblema)" else "")
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f
        p.color = 0xFF000000.toInt()
        c.drawText(txt, w * 0.5f, y0 - 8f, p)
        p.style = Paint.Style.FILL
        p.color = if (pts > 0) 0xFFB9F6CA.toInt() else 0xFFFFFFFF.toInt()
        c.drawText(txt, w * 0.5f, y0 - 8f, p)
        if (mira != 0) {
            val d = talentoSlot(mira)
            val msg = "Toque no chão para mirar • aperte " + (d?.nome ?: "a skill") + " de novo para soltar"
            p.style = Paint.Style.STROKE
            p.color = 0xFF000000.toInt()
            p.textSize = 20f
            c.drawText(msg, w * 0.5f, y0 + 48f, p)
            p.style = Paint.Style.FILL
            p.color = 0xFFFFFFFF.toInt()
            c.drawText(msg, w * 0.5f, y0 + 48f, p)
        }
    }
}
