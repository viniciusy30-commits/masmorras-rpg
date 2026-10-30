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

/** O que o motor do Elfo precisa do jogo (implementado pelo GameView). */
interface ElfoHost {
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
 * Combate por alvo do Arqueiro Elfo.
 * - Toque num inimigo para marcar o alvo. O ataque básico e as skills de alvo vão direto nele.
 * - Skills de área mostram um círculo no chão: toque no chão (ou num inimigo) para mirar e aperte o botão de novo para soltar.
 * - 5 escolas, cada uma com 5 skills (slots 1 a 5 = botões de skill).
 */
class ElfoEngine(private val host: ElfoHost) {

    val st = ElfoState()
    private var ctx: Context? = null

    var alvo: Enemy? = null
    var mira = 0
        private set
    private var mx = 0f
    private var my = 0f
    private var miraT = 0f

    private val cdAt = LongArray(7)
    private var ventoT = 0f
    var sint = 0f
        private set
    private var superT = 0f
    private var semHit = 0f
    private var parado = 0f
    private var escP = 0f
    private var buffVelT = 0f
    private var regenT = 0f
    private var furacaoT = 0f
    private var furacaoTick = 0f
    private var fenixT = 0f
    private var reviveUsado = false
    private var auraT = 0f
    private var ultimoAlvo: Enemy? = null
    private var contTiro = 0
    private var relogio = 0f

    val superaquecido: Boolean get() = superT > 0f

    // ───────────── estruturas ─────────────

    private class Est {
        var burn = 0
        var burnT = 0f
        var burnAcc = 0f
        var frio = 0
        var frioT = 0f
        var congT = 0f
        var imuneT = 0f
        var venenoT = 0f
        var venenoAcc = 0f
        var marcaT = 0f
    }

    private class Flecha(
        var x: Float, var y: Float, val alvo: Enemy, val cor: Int, val vel: Float, val onHit: (Enemy) -> Unit
    ) {
        var vivo = true
        var t = 0f
        var ang = 0f
    }

    private class Zona(var tipo: Int, var x: Float, var y: Float, val raio: Float, var vida: Float, val cor: Int, val rank: Float) {
        var idade = 0f
        var tick = 0f
        var armada = false
        var alvoE: Enemy? = null
        val vida0 = vida
    }

    private class Raio(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val cor: Int, var vida: Float, val larg: Float) {
        val vida0 = vida
    }

    private class Canal(val alvo: Enemy, val gasto: Float, val rank: Float) {
        var t = 0f
        var tick = 0.2f
        var passo = 0
    }

    private class Agenda(var t: Float, val acao: () -> Unit)

    private val est = HashMap<Enemy, Est>()
    private val flechas = ArrayList<Flecha>()
    private val zonas = ArrayList<Zona>()
    private val raios = ArrayList<Raio>()
    private val agenda = ArrayList<Agenda>()
    private var canal: Canal? = null

    private fun st(e: Enemy): Est = est.getOrPut(e) { Est() }

    private companion object {
        const val Z_SEMENTE = 1
        const val Z_ARBUSTO = 2
        const val Z_INCENDIO = 3
        const val Z_BRASAS = 4
        const val Z_FENIX = 5
        const val Z_FOGO = 6
        const val Z_ARMADILHA = 7
        const val Z_GELO = 8
        const val Z_CRISTAIS = 9
        const val Z_TOTEM = 10
        const val Z_RAIZES = 11
        const val Z_ARVORE = 12
        const val Z_REDEMOINHO = 13
        const val Z_OLHO = 14
        const val Z_ONDA = 15
        const val ALC = 1.15f // Olho de Águia
        const val COR_BRASA = 0xFFFF6A2B.toInt()
        const val COR_GELO = 0xFF4FC3F7.toInt()
        const val COR_RAIO = 0xFFFFE04A.toInt()
        const val COR_VERDE = 0xFF66BB6A.toInt()
        const val COR_VENTO = 0xFF80DEEA.toInt()
    }

    // ───────────── ciclo de vida ─────────────

    fun novaPartida(c: Context, p: Player) {
        ctx = c
        st.load(c)
        resetRuntime()
        p.reviveHook = { reviver(p) }
    }

    private fun resetRuntime() {
        alvo = null
        mira = 0
        sint = 0f
        superT = 0f
        semHit = 0f
        parado = 0f
        escP = 0f
        buffVelT = 0f
        ventoT = 0f
        regenT = 0f
        furacaoT = 0f
        fenixT = 0f
        reviveUsado = false
        ultimoAlvo = null
        contTiro = 0
        canal = null
        for (i in cdAt.indices) cdAt[i] = 0L
        est.clear()
        flechas.clear()
        zonas.clear()
        raios.clear()
        agenda.clear()
    }

    fun escolher(e: Escola) {
        st.trocarEscola(e)
        ctx?.let { st.save(it) }
        resetRuntime()
    }

    fun salvar() {
        ctx?.let { st.save(it) }
    }

    fun defs(): List<SkillDef>? = st.escola?.let { ElfoCatalog.skills(it) }

    private fun defSlot(slot: Int): SkillDef? {
        val l = defs() ?: return null
        return if (slot in 1..l.size) l[slot - 1] else null
    }

    /** Segundos que faltam de recarga da skill do slot. */
    fun cd(slot: Int): Float = if (slot in 1..6) ((cdAt[slot] - System.currentTimeMillis()) / 1000f).coerceAtLeast(0f) else 0f

    private fun vivos(): List<Enemy> = host.inimigos.filter { it.alive }

    private fun curar(v: Float) {
        val p = host.jogador ?: return
        if (!p.alive || v <= 0f) return
        val antes = p.hp
        p.hp = min(p.maxHp, p.hp + v)
        if (p.hp - antes >= 3f) host.texto(p.x, p.y - 70f - p.alt, "+" + (p.hp - antes).toInt(), 0xFF7CFF8A.toInt())
    }

    private fun reviver(p: Player): Boolean {
        if (fenixT > 0f && !reviveUsado) {
            reviveUsado = true
            fenixT = 0f
            p.hp = p.maxHp * 0.3f
            host.texto(p.x, p.y - 80f - p.alt, "RENASCEU!", COR_BRASA)
            host.particulas(p.x, p.y - 20f - p.alt, 24, COR_BRASA)
            host.tremor(0.4f)
            return true
        }
        return false
    }

    fun onKill(e: Enemy) {
        val s = est.remove(e)
        if (s != null && s.burn > 0 && st.escola == Escola.BRASA) addSint(15f)
        if (e === alvo) alvo = null
        val subiu = st.addXp(12 + 10 * e.tipo)
        val p = host.jogador
        if (subiu && p != null) {
            host.texto(p.x, p.y - 90f - p.alt, "NÍVEL " + st.nivel + "!", 0xFFFFD54F.toInt())
            host.som("ui_ping", 0.8f)
            host.particulas(p.x, p.y - 30f - p.alt, 18, 0xFFFFD54F.toInt())
        }
        salvar()
    }

    // ───────────── Sintonia ─────────────

    private fun addSint(v: Float) {
        semHit = 0f
        val esc = st.escola ?: return
        if (esc == Escola.BRASA && superT > 0f) return
        sint = min(100f, sint + v)
        if (sint >= 100f) {
            val p = host.jogador ?: return
            when (esc) {
                Escola.BRASA -> {
                    superT = 6f
                    host.texto(p.x, p.y - 80f - p.alt, "SUPERAQUECIDO!", COR_BRASA)
                    host.som("rain_cast", 0.7f)
                }
                Escola.RAIZ -> {
                    curar(p.maxHp * 0.2f)
                    sint = 0f
                    host.texto(p.x, p.y - 80f - p.alt, "FLORESCER", COR_VERDE)
                    host.particulas(p.x, p.y - 20f - p.alt, 14, COR_VERDE)
                }
                else -> {}
            }
        }
    }

    // ───────────── dano e status ─────────────

    private fun hurt(e: Enemy, m: Float, kb: Float = 0.5f, sil: Boolean = false): Float {
        val p = host.jogador ?: return 0f
        val s = est[e]
        var d = p.danoH * m
        if (superT > 0f) d *= 1.4f
        if (st.escola == Escola.VENDAVAL) d *= 1f + 0.25f * sint / 100f
        if (s != null) {
            if (s.marcaT > 0f) d *= 1.25f
            if (s.congT > 0f) d *= 1.5f
            if (st.escola == Escola.GEADA && (e.slowT > 0f || s.frio > 0)) d *= 1f + 0.2f * sint / 100f
        }
        host.dano(e, d, p.x, p.y, kb, sil)
        return d
    }

    private fun queima(e: Enemy, n: Int) {
        val s = st(e)
        s.burn = min(5, s.burn + n)
        s.burnT = 4f
    }

    private fun gelar(e: Enemy, n: Int) {
        val s = st(e)
        e.slowT = max(e.slowT, 2f)
        if (s.congT > 0f || s.imuneT > 0f) return
        s.frio += n
        s.frioT = 6f
        if (s.frio >= 5) {
            s.frio = 0
            s.congT = 1.5f
            s.imuneT = 8f
            e.stunT = max(e.stunT, 1.5f)
            host.texto(e.x, e.y - e.radius - 40f - e.alt, "CONGELADO", COR_GELO)
            host.particulas(e.x, e.y - 14f - e.alt, 10, COR_GELO)
        }
    }

    private fun conduz() {}

    /** Pele de Geada: quem bate em você ganha Frio. */
    fun aoLevarDano(e: Enemy) {
        if (st.escola != Escola.GEADA || !e.alive) return
        gelar(e, 2)
        host.particulas(e.x, e.y - 14f - e.alt, 5, COR_GELO)
    }

    /** Condutor: um crítico de verdade corta 1s da recarga de uma skill. */
    fun aoCritar() {
        val p = host.jogador ?: return
        if (st.escola != Escola.TEMPESTADE) return
        run {
            val now = System.currentTimeMillis()
            val cand = (1..5).filter { cdAt[it] > now }
            if (cand.isNotEmpty()) {
                val i = cand[(Math.random() * cand.size).toInt().coerceAtMost(cand.size - 1)]
                cdAt[i] = max(now, cdAt[i] - 1000L)
                host.texto(p.x, p.y - 60f - p.alt, "CONDUTOR", COR_RAIO)
            }
        }
    }

    private fun sabor(e: Enemy, esc: Escola, d: Float) {
        when (esc) {
            Escola.BRASA -> if (Math.random() < 0.25) queima(e, 1)
            Escola.GEADA -> if (Math.random() < 0.25) gelar(e, 1)
            Escola.RAIZ -> curar(d * 0.08f)
            else -> {}
        }
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
        host.jogador ?: return false
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

    private fun setMira(x: Float, y: Float) {
        val p = host.jogador ?: return
        val def = defSlot(mira) ?: return
        val alc = def.alcance * ALC
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

    /** Flecha Viva: dispara uma flecha guiada no alvo. Devolve false se não há inimigo ao alcance. */
    fun tiroBasico(pw: Float): Boolean {
        val esc = st.escola ?: return false
        val t = acharAlvo(200f * ALC) ?: return false
        val p = host.jogador ?: return false
        face(p, t.x, t.y)
        if (t === ultimoAlvo) contTiro++ else {
            ultimoAlvo = t
            contTiro = 1
        }
        val terceiro = contTiro >= 3
        if (terceiro) contTiro = 0
        flecha(t, esc.cor, 520f) { e ->
            var m = 1f + 0.5f * pw
            if (terceiro) m *= 1.3f
            val d = hurt(e, m, 0.6f)
            addSint(if (terceiro) 10f else 5f)
            sabor(e, esc, d)
            conduz()
        }
        return true
    }

    private fun flecha(t: Enemy, cor: Int, vel: Float, onHit: (Enemy) -> Unit) {
        val p = host.jogador ?: return
        flechas.add(Flecha(p.x + p.facingX * 20f, p.y + p.facingY * 20f, t, cor, vel, onHit))
    }

    // ───────────── usar skills ─────────────

    /** Devolve null se deu certo, ou o motivo da falha. */
    fun usar(slot: Int): String? {
        val p = host.jogador ?: return ""
        if (!p.alive) return ""
        st.escola ?: return "Escolha a escola"
        if (slot == 6) {
            if (System.currentTimeMillis() < cdAt[6]) return "Recarregando"
            cdAt[6] = System.currentTimeMillis() + 12000L
            ventoT = 5f
            host.texto(p.x, p.y - 70f - p.alt, "PASSO DO VENTO", COR_VENTO)
            host.particulas(p.x, p.y - 15f - p.alt, 12, COR_VENTO)
            host.som("hop", 0.9f)
            return null
        }
        val def = defSlot(slot) ?: return ""
        val rk = st.rank(def)
        if (rk == 0) return "Nível " + def.nivelMin
        if (System.currentTimeMillis() < cdAt[slot]) return "Recarregando"
        if (def.alvo == Alvo.AREA) {
            if (mira == slot) {
                val x = mx
                val y = my
                mira = 0
                executar(def, slot, rk, null, x, y)
                return null
            }
            mira = slot
            miraT = 0f
            val t = alvo
            if (t != null && t.alive) setMira(t.x, t.y) else setMira(p.x + p.facingX * 120f, p.y + p.facingY * 120f)
            return null
        }
        mira = 0
        var t: Enemy? = null
        if (def.alvo == Alvo.INIMIGO) {
            t = acharAlvo(def.alcance * ALC) ?: return "Sem alvo ao alcance"
        }
        executar(def, slot, rk, t, 0f, 0f)
        return null
    }

    fun cancelarMira() {
        mira = 0
    }

    private fun executar(def: SkillDef, slot: Int, rk: Int, t: Enemy?, ax: Float, ay: Float) {
        val p = host.jogador ?: return
        val now = System.currentTimeMillis()
        val rm = 1f + 0.15f * (rk - 1)
        cdAt[slot] = now + (def.cdMs * (1f - 0.07f * (rk - 1))).toLong()
        var atraso = 0f
        if (def.alvo == Alvo.INIMIGO && t != null) face(p, t.x, t.y)
        else if (def.alvo == Alvo.AREA) face(p, ax, ay)
        else if (def.alvo == Alvo.DIRECAO) {
            val a = alvo
            if (a != null && a.alive) face(p, a.x, a.y)
        }
        val anima = def.alvo == Alvo.INIMIGO || def.alvo == Alvo.AREA || def.id == "perfurante"
        if (anima && p.canAttack(now)) {
            p.doAttack(now)
            atraso = p.shotDelay()
        }
        if (atraso > 0f) agenda.add(Agenda(atraso) { efeito(def, rk, rm, t, ax, ay) })
        else efeito(def, rk, rm, t, ax, ay)
    }

    private fun zona(tipo: Int, x: Float, y: Float, raio: Float, vida: Float, cor: Int, rank: Float): Zona {
        val z = Zona(tipo, x, y, raio, vida, cor, rank)
        zonas.add(z)
        return z
    }

    /** Remove as zonas mais antigas dos tipos dados até sobrar espaço para uma nova. */
    private fun limitar(max: Int, vararg tipos: Int) {
        while (zonas.count { it.tipo in tipos } >= max) {
            val velha = zonas.firstOrNull { it.tipo in tipos } ?: break
            zonas.remove(velha)
        }
    }

    private fun dash(p: Player, dx0: Float, dy0: Float, dist: Float, rastro: Boolean) {
        val l = hypot(dx0, dy0)
        if (l < 0.001f) return
        val dx = dx0 / l
        val dy = dy0 / l
        p.facingX = dx
        p.facingY = dy
        host.particulas(p.x, p.y - 10f - p.alt, 8, if (rastro) COR_GELO else COR_VENTO)
        var passos = 0
        val total = (dist / 10f).toInt()
        while (passos < total) {
            val nx = p.x + dx * 10f
            val ny = p.y + dy * 10f
            if (!host.livre(nx, ny)) break
            p.x = nx
            p.y = ny
            passos++
            if (rastro && passos % 4 == 0) zona(Z_GELO, p.x, p.y, 50f, 3.5f, COR_GELO, 1f)
        }
        host.particulas(p.x, p.y - 10f - p.alt, 8, if (rastro) COR_GELO else COR_VENTO)
        host.som("hop", 0.8f)
    }

    private fun efeito(def: SkillDef, rk: Int, rm: Float, t: Enemy?, ax: Float, ay: Float) {
        val p = host.jogador ?: return
        if (!p.alive) return
        when (def.id) {
            // ── Brasa Viva ──
            "cinza" -> if (t != null) flecha(t, COR_BRASA, 520f) { e ->
                hurt(e, 1.0f * rm)
                queima(e, 1)
                addSint(8f)
            }
            "semente" -> {
                limitar(3, Z_SEMENTE, Z_ARBUSTO)
                zona(Z_SEMENTE, ax, ay, def.raio, 2f, COR_BRASA, rm)
                addSint(6f)
                host.som("trap_land", 0.8f)
            }
            "incendio" -> {
                zona(Z_INCENDIO, ax, ay, def.raio, 3f, COR_BRASA, rm)
                addSint(10f)
                host.som("rain_cast", 0.8f)
            }
            "brasas" -> {
                zona(Z_BRASAS, ax, ay, def.raio, 4f, COR_BRASA, rm)
                addSint(8f)
                host.som("rain_release", 0.8f)
            }
            "fenix" -> {
                zona(Z_FENIX, ax, ay, def.raio, 0.8f, COR_BRASA, rm)
                fenixT = 8f
                reviveUsado = false
                addSint(25f)
                host.som("thunder_cast", 0.9f)
            }
            // ── Geada Eterna ──
            "gelida" -> if (t != null) flecha(t, COR_GELO, 520f) { e ->
                hurt(e, 0.9f * rm)
                e.slowT = max(e.slowT, 3f)
                gelar(e, 1)
                addSint(8f)
            }
            "armadilha" -> {
                limitar(3, Z_ARMADILHA)
                zona(Z_ARMADILHA, ax, ay, def.raio, 25f, COR_GELO, rm)
                addSint(6f)
                host.som("trap_land", 0.8f)
            }
            "cristais" -> {
                zona(Z_CRISTAIS, ax, ay, def.raio, 4f, COR_GELO, rm)
                addSint(8f)
                host.som("rain_release", 0.8f)
            }
            "passogelido" -> {
                dash(p, p.facingX, p.facingY, 150f, true)
                addSint(8f)
            }
            "zero" -> {
                zona(Z_ONDA, p.x, p.y, def.raio, 0.7f, COR_GELO, rm)
                for (e in vivos()) {
                    if (hypot(e.x - p.x, e.y - p.y) < def.raio + e.radius * 0.5f) {
                        val s = st(e)
                        s.congT = 2f
                        s.imuneT = max(s.imuneT, 8f)
                        e.stunT = max(e.stunT, 2f)
                        hurt(e, 0.8f * rm, 0.3f)
                    }
                }
                host.tremor(0.3f)
                host.som("thunder_cast", 0.9f)
                addSint(20f)
            }
            // ── Tempestade Viva ──
            "relampago" -> if (t != null) flecha(t, COR_RAIO, 620f) { e ->
                hurt(e, 1.0f * rm)
                host.som("thunder_hit", 0.8f)
                var prev = e
                val usados = HashSet<Enemy>()
                usados.add(e)
                for (i in 0 until 2) {
                    var prox: Enemy? = null
                    var nd = 150f
                    for (o in vivos()) {
                        if (o in usados) continue
                        val d = hypot(o.x - prev.x, o.y - prev.y)
                        if (d < nd) {
                            nd = d
                            prox = o
                        }
                    }
                    val n = prox ?: break
                    usados.add(n)
                    raios.add(Raio(prev.x, prev.y - 18f - prev.alt, n.x, n.y - 18f - n.alt, COR_RAIO, 0.25f, 3f))
                    hurt(n, 0.6f * rm, 0.3f)
                    prev = n
                }
                addSint(12f)
                conduz()
            }
            "perfurante" -> {
                var dx = p.facingX
                var dy = p.facingY
                val a = alvo
                if (a != null && a.alive) {
                    val d = hypot(a.x - p.x, a.y - p.y)
                    if (d > 1f && d < 560f) {
                        dx = (a.x - p.x) / d
                        dy = (a.y - p.y) / d
                    }
                }
                val len = def.alcance * ALC
                val carga = if (st.escola == Escola.TEMPESTADE) sint else 0f
                sint = 0f
                raios.add(Raio(p.x + dx * 20f, p.y + dy * 20f - 22f - p.alt, p.x + dx * len, p.y + dy * len - 22f - p.alt, COR_RAIO, 0.3f, 7f))
                for (e in vivos()) {
                    val ex = e.x - p.x
                    val ey = e.y - p.y
                    val along = ex * dx + ey * dy
                    val perp = kotlin.math.abs(ex * dy - ey * dx)
                    if (along in 0f..len && perp < e.radius + 14f) {
                        hurt(e, (1.2f + 2.4f * carga / 100f) * rm, 1.0f)
                        host.particulas(e.x, e.y - 14f - e.alt, 6, COR_RAIO)
                    }
                }
                host.tremor(0.2f)
                host.som("thunder_fire", 1f)
            }
            "concentrada" -> if (t != null) {
                val gasto = if (st.escola == Escola.TEMPESTADE) sint else 0f
                sint = 0f
                canal = Canal(t, gasto, rm)
                host.som("thunder_cast", 0.8f)
            }
            "marca" -> if (t != null) {
                st(t).marcaT = 8f
                host.texto(t.x, t.y - t.radius - 44f - t.alt, "MARCADO", 0xFFFF5252.toInt())
                addSint(10f)
                host.som("ui_ping", 0.7f)
            }
            "olho" -> if (t != null) {
                val z = zona(Z_OLHO, t.x, t.y, def.raio, 1.4f, COR_RAIO, rm)
                z.alvoE = t
                sint = 0f
                host.som("thunder_cast", 1f)
            }
            // ── Raiz Profunda ──
            "espinhosa" -> if (t != null) flecha(t, COR_VERDE, 520f) { e ->
                val d = hurt(e, 0.8f * rm)
                st(e).venenoT = 4f
                curar(d * 0.2f)
                addSint(10f)
            }
            "totem" -> {
                zona(Z_TOTEM, ax, ay, def.raio, 8f, COR_VERDE, rm)
                addSint(8f)
                host.som("trap_land", 0.8f)
            }
            "raizes" -> {
                zona(Z_RAIZES, ax, ay, def.raio, 2.6f, COR_VERDE, rm)
                addSint(10f)
                host.som("trap_snap", 0.8f)
            }
            "bencao" -> {
                regenT = 10f
                buffVelT = 10f
                host.texto(p.x, p.y - 70f - p.alt, "BÊNÇÃO", COR_VERDE)
                host.particulas(p.x, p.y - 20f - p.alt, 14, COR_VERDE)
                addSint(15f)
                host.som("ui_ping", 0.8f)
            }
            "arvore" -> {
                zona(Z_ARVORE, ax, ay, def.raio, 12f, COR_VERDE, rm)
                addSint(25f)
                host.tremor(0.25f)
                host.som("thunder_cast", 0.8f)
            }
            // ── Vendaval ──
            "cortante" -> if (t != null) flecha(t, COR_VENTO, 720f) { e ->
                hurt(e, 0.55f * rm, 0.3f)
                addSint(10f)
            }
            "ciclone" -> {
                dash(p, p.facingX, p.facingY, 170f, false)
                var matou = false
                for (e in vivos()) {
                    if (hypot(e.x - p.x, e.y - p.y) < def.raio + e.radius * 0.5f) {
                        hurt(e, 1.4f * rm, 1.2f)
                        if (!e.alive) matou = true
                    }
                }
                zona(Z_ONDA, p.x, p.y, def.raio, 0.5f, COR_VENTO, 1f)
                host.tremor(0.15f)
                addSint(15f)
                if (matou) {
                    for (i in cdAt.indices) if (def === defSlot(i)) cdAt[i] = 0L
                    host.texto(p.x, p.y - 70f - p.alt, "RESETOU", COR_VENTO)
                }
            }
            "rajada" -> if (t != null) {
                flecha(t, COR_VENTO, 700f) { e ->
                    hurt(e, 0.7f * rm, 0.3f)
                    addSint(8f)
                }
                val forte = sint > 30f
                agenda.add(Agenda(0.15f) {
                    if (t.alive) flecha(t, COR_VENTO, 700f) { e ->
                        hurt(e, (if (forte) 1.4f else 0.7f) * rm, 0.5f)
                        addSint(8f)
                    }
                })
                host.som("bow_shot", 0.9f)
            }
            "redemoinho" -> {
                zona(Z_REDEMOINHO, ax, ay, def.raio, 5f, COR_VENTO, rm)
                addSint(10f)
                host.som("rain_cast", 0.8f)
            }
            "furacao" -> {
                furacaoT = 5f
                furacaoTick = 0f
                sint = max(sint, 60f)
                host.texto(p.x, p.y - 80f - p.alt, "FURACÃO!", COR_VENTO)
                host.tremor(0.3f)
                host.som("thunder_cast", 0.9f)
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

        // Raízes Antigas (passiva comum): parado por 2s dá escudo leve; andar tira
        val maxS = 8f + p.maxHp * 0.05f
        if (parado >= 2f) {
            if (escP < maxS) {
                val add = min(maxS - escP, dt * maxS * 0.6f)
                escP += add
                p.shield += add
            }
        } else if (movendo && escP > 0f) {
            p.shield = max(0f, p.shield - escP)
            escP = 0f
        }
        escP = min(escP, p.shield)

        // Sintonia
        val esc = st.escola
        if (esc == Escola.VENDAVAL) {
            sint = if (movendo) min(100f, sint + 14f * dt) else max(0f, sint - 30f * dt)
        } else if (esc != null) {
            semHit += dt
            if (superT > 0f) {
                superT -= dt
                sint = 100f * (superT / 6f).coerceIn(0f, 1f)
                if (superT <= 0f) sint = 30f
            } else if (semHit > 3f) {
                sint = max(0f, sint - (if (esc == Escola.TEMPESTADE) 2f else 3f) * dt)
            }
        }

        // buffs
        var sp = 1f
        if (buffVelT > 0f) {
            buffVelT -= dt
            sp *= 1.15f
        }
        if (ventoT > 0f) {
            ventoT -= dt
            sp *= 1.4f
        }
        if (furacaoT > 0f) {
            furacaoT -= dt
            sp *= 1.6f
            furacaoTick -= dt
            if (furacaoTick <= 0f) {
                furacaoTick += 0.4f
                for (e in vivos()) {
                    if (hypot(e.x - p.x, e.y - p.y) < 100f + e.radius * 0.5f) hurt(e, 0.5f, 0.8f, true)
                }
                host.particulas(p.x + (Math.random().toFloat() - 0.5f) * 80f, p.y - 10f - p.alt, 4, COR_VENTO)
            }
        }
        if (esc == Escola.VENDAVAL) sp *= 1f + 0.2f * sint / 100f
        p.spdMul = sp
        if (regenT > 0f) {
            regenT -= dt
            p.hp = min(p.maxHp, p.hp + p.maxHp * 0.02f * dt)
        }
        if (fenixT > 0f) fenixT -= dt

        updCanal(dt, p, movendo)
        updFlechas(dt)
        for (z in zonas.toList()) updZona(z, dt, p)
        zonas.removeAll { it.vida <= 0f }
        for (r in raios) r.vida -= dt
        raios.removeAll { it.vida <= 0f }
        updStatus(dt)
    }

    private fun updCanal(dt: Float, p: Player, movendo: Boolean) {
        val c = canal ?: return
        if (!c.alvo.alive) {
            canal = null
            return
        }
        if (movendo || hypot(c.alvo.x - p.x, c.alvo.y - p.y) > 340f) {
            canal = null
            host.texto(p.x, p.y - 70f - p.alt, "INTERROMPIDO", 0xFFFF8A65.toInt())
            return
        }
        c.t += dt
        c.tick -= dt
        face(p, c.alvo.x, c.alvo.y)
        if (c.tick <= 0f) {
            c.tick += 0.4f
            c.passo++
            hurt(c.alvo, (0.45f + 0.25f * c.passo) * c.rank * (1f + c.gasto / 100f), 0.1f, true)
            raios.add(Raio(p.x + p.facingX * 18f, p.y - 22f - p.alt, c.alvo.x, c.alvo.y - 22f - c.alvo.alt, COR_RAIO, 0.2f, 3f))
            host.som("thunder_hit", 0.5f)
        }
        if (c.t >= 2f) canal = null
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

    private fun dentro(z: Zona, e: Enemy): Boolean = hypot(e.x - z.x, e.y - z.y) < z.raio + e.radius * 0.5f

    private fun empurrar(e: Enemy, dx: Float, dy: Float) {
        val nx = e.x + dx
        val ny = e.y + dy
        if (host.livre(nx, ny)) {
            e.x = nx
            e.y = ny
        }
    }

    private fun updZona(z: Zona, dt: Float, p: Player) {
        z.idade += dt
        z.vida -= dt
        z.tick -= dt
        val tk = z.tick <= 0f
        if (tk) z.tick += 0.5f
        val ini = vivos()
        when (z.tipo) {
            Z_SEMENTE -> if (z.idade >= 2f) {
                z.tipo = Z_ARBUSTO
                z.vida = 8f
                z.idade = 0f
                host.particulas(z.x, z.y - 10f, 10, COR_BRASA)
                host.som("rain_land", 0.8f)
            }
            Z_ARBUSTO, Z_FOGO, Z_BRASAS -> if (tk) {
                for (e in ini) if (dentro(z, e)) {
                    hurt(e, (if (z.tipo == Z_BRASAS) 0.35f else 0.25f) * z.rank, 0f, true)
                    queima(e, 1)
                }
            }
            Z_INCENDIO -> if (tk) {
                for (e in ini) if (dentro(z, e)) {
                    hurt(e, 0.2f * z.rank, 0f, true)
                    queima(e, 1)
                    // a chama pula para os vizinhos
                    for (o in ini) {
                        if (o !== e && hypot(o.x - e.x, o.y - e.y) < 110f && (est[o]?.burn ?: 0) == 0) queima(o, 1)
                    }
                }
            }
            Z_FENIX -> if (!z.armada && z.idade >= 0.8f) {
                z.armada = true
                for (e in ini) if (dentro(z, e)) {
                    hurt(e, 4.0f * z.rank, 1.5f)
                    queima(e, 3)
                }
                host.tremor(0.4f)
                host.particulas(z.x, z.y - 20f, 26, COR_BRASA)
                host.som("thunder_fire", 1f)
                z.tipo = Z_FOGO
                z.vida = 5f
                z.idade = 0f
            }
            Z_ARMADILHA -> if (z.idade >= 0.6f) {
                var pisou = false
                for (e in ini) if (hypot(e.x - z.x, e.y - z.y) < z.raio + e.radius * 0.5f) pisou = true
                if (pisou) {
                    for (e in ini) if (hypot(e.x - z.x, e.y - z.y) < 70f + e.radius * 0.5f) {
                        e.stunT = max(e.stunT, 1.5f)
                        gelar(e, 2)
                        hurt(e, 0.5f * z.rank, 0.2f)
                    }
                    zona(Z_GELO, z.x, z.y, 90f, 6f, COR_GELO, 1f)
                    host.particulas(z.x, z.y - 10f, 12, COR_GELO)
                    host.som("trap_snap", 1f)
                    z.vida = 0f
                }
            }
            Z_GELO -> for (e in ini) if (dentro(z, e)) e.slowT = max(e.slowT, 0.5f)
            Z_CRISTAIS -> if (tk) {
                for (e in ini) if (dentro(z, e)) {
                    hurt(e, 0.3f * z.rank, 0f, true)
                    gelar(e, 1)
                }
                host.particulas(z.x + (Math.random().toFloat() - 0.5f) * z.raio, z.y + (Math.random().toFloat() - 0.5f) * z.raio * 0.7f - 20f, 3, COR_GELO)
            }
            Z_TOTEM -> if (tk && hypot(p.x - z.x, p.y - z.y) < z.raio) curar(p.maxHp * 0.025f * z.rank)
            Z_RAIZES -> if (!z.armada && z.idade >= 0.3f) {
                z.armada = true
                for (e in ini) if (dentro(z, e)) e.stunT = max(e.stunT, 2.5f)
                if (hypot(p.x - z.x, p.y - z.y) < z.raio) p.shield = max(p.shield, p.maxHp * 0.2f * z.rank)
                host.particulas(z.x, z.y - 10f, 14, COR_VERDE)
            }
            Z_ARVORE -> {
                if (tk) {
                    if (hypot(p.x - z.x, p.y - z.y) < z.raio) {
                        curar(p.maxHp * 0.03f * z.rank)
                        p.shield = max(p.shield, p.maxHp * 0.1f)
                    }
                    for (e in ini) if (dentro(z, e)) hurt(e, 0.2f * z.rank, 0f, true)
                }
                for (e in ini) {
                    if (!dentro(z, e)) continue
                    val dx = z.x - e.x
                    val dy = z.y - e.y
                    val d = hypot(dx, dy)
                    if (d > 30f) empurrar(e, dx / d * 40f * dt, dy / d * 40f * dt)
                }
            }
            Z_REDEMOINHO -> {
                for (e in ini) {
                    if (!dentro(z, e)) continue
                    val dx = e.x - z.x
                    val dy = e.y - z.y
                    val d = hypot(dx, dy).coerceAtLeast(1f)
                    empurrar(e, dx / d * 110f * dt, dy / d * 110f * dt)
                    if (tk) hurt(e, 0.2f * z.rank, 0f, true)
                }
            }
            Z_OLHO -> {
                val a = z.alvoE
                if (a != null && a.alive) {
                    z.x = a.x
                    z.y = a.y
                }
                if (!z.armada && z.idade >= 1f) {
                    z.armada = true
                    if (a != null && a.alive) {
                        val marcado = (est[a]?.marcaT ?: 0f) > 0f
                        raios.add(Raio(a.x, a.y - 300f, a.x, a.y - a.alt, COR_RAIO, 0.35f, 10f))
                        for (e in ini) if (e !== a && hypot(e.x - a.x, e.y - a.y) < z.raio) hurt(e, 3f * z.rank, 1f)
                        hurt(a, 6f * z.rank * (if (marcado) 2f else 1f), 2f)
                        host.tremor(0.5f)
                        host.som("thunder_fire", 1f)
                    }
                    z.vida = 0.35f
                }
            }
            Z_ONDA -> {}
        }
    }

    private fun updStatus(dt: Float) {
        for (e in est.keys.toList()) {
            if (!e.alive) {
                est.remove(e)
                continue
            }
            val s = est[e] ?: continue
            if (s.burnT > 0f) {
                s.burnT -= dt
                s.burnAcc += dt
                if (s.burnAcc >= 0.5f) {
                    s.burnAcc -= 0.5f
                    val calor = if (st.escola == Escola.BRASA) 1f + 0.5f * sint / 100f else 1f
                    hurt(e, 0.10f * s.burn * calor, 0f, true)
                }
                if (s.burnT <= 0f) s.burn = 0
            }
            if (!e.alive) continue
            if (s.venenoT > 0f) {
                s.venenoT -= dt
                s.venenoAcc += dt
                if (s.venenoAcc >= 0.5f) {
                    s.venenoAcc -= 0.5f
                    hurt(e, 0.12f, 0f, true)
                }
            }
            if (s.frioT > 0f) {
                s.frioT -= dt
                if (s.frioT <= 0f) s.frio = 0
            }
            if (s.congT > 0f) s.congT -= dt
            if (s.imuneT > 0f) s.imuneT -= dt
            if (s.marcaT > 0f) s.marcaT -= dt
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

    private fun a(cor: Int, f: Float): Int = (((cor ushr 24) * f.coerceIn(0f, 1f)).toInt() shl 24) or (cor and 0x00FFFFFF)

    /** Desenha no chão (antes dos personagens): anel do alvo, zonas e círculo de mira. */
    fun drawGround(c: Canvas, p: Paint, camX: Float, camY: Float, time: Float) {
        val pl = host.jogador
        val t = alvo
        if (t != null && t.alive) {
            val sx = t.x - camX
            val feet = t.y - camY + t.radius * 0.55f - t.alt
            val pulso = 1f + 0.06f * sin(time * 6f)
            oval(c, p, sx, feet, t.radius * 1.35f * pulso, 0x22FF5252, 0xFFFF5252.toInt(), 3f)
        }
        for (z in zonas) drawZona(c, p, z, camX, camY, time)
        val def = defSlot(mira)
        if (pl != null && def != null && mira != 0) {
            val sx = mx - camX
            val sy = my - camY
            val cor = st.escola?.cor ?: 0xFFFFFFFF.toInt()
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2f
            p.color = a(cor, 0.5f)
            c.drawLine(pl.x - camX, pl.y - camY, sx, sy, p)
            oval(c, p, sx, sy, def.raio, a(cor, 0.18f), a(cor, 0.9f), 4f)
            oval(c, p, sx, sy, def.raio * (0.5f + 0.1f * sin(time * 8f)), 0x00000000, a(cor, 0.6f), 2f)
        }
    }

    private fun drawZona(c: Canvas, p: Paint, z: Zona, camX: Float, camY: Float, time: Float) {
        val sx = z.x - camX
        val sy = z.y - camY
        val fade = min(1f, z.vida / 0.6f)
        when (z.tipo) {
            Z_SEMENTE -> {
                oval(c, p, sx, sy, 14f, a(COR_BRASA, 0.25f * fade), a(COR_BRASA, 0.9f * fade), 2f)
                p.color = a(0xFF8D6E63.toInt(), fade)
                c.drawCircle(sx, sy - 4f, 5f + 3f * (z.idade / 2f), p)
            }
            Z_ARBUSTO, Z_FOGO, Z_BRASAS, Z_INCENDIO -> {
                oval(c, p, sx, sy, z.raio, a(COR_BRASA, 0.16f * fade), a(COR_BRASA, 0.55f * fade), 3f)
                val n = (z.raio / 12f).toInt().coerceIn(4, 10)
                for (i in 0 until n) {
                    val ang = i * 2.399f + z.x
                    val rr = z.raio * (0.25f + 0.65f * ((i * 37) % 10) / 10f)
                    val fx = sx + cos(ang) * rr
                    val fy = sy + sin(ang) * rr * 0.72f
                    val h = 10f + 8f * sin(time * 9f + i * 1.7f)
                    p.color = a(0xFFFF8A30.toInt(), 0.85f * fade)
                    c.drawCircle(fx, fy - h * 0.5f, 5f, p)
                    p.color = a(0xFFFFE08A.toInt(), 0.8f * fade)
                    c.drawCircle(fx, fy - h * 0.7f, 2.5f, p)
                }
            }
            Z_FENIX -> {
                val k = (z.idade / 0.8f).coerceIn(0f, 1f)
                oval(c, p, sx, sy, z.raio, a(COR_BRASA, 0.12f + 0.2f * k), a(COR_BRASA, 0.9f), 4f)
                oval(c, p, sx, sy, z.raio * (1f - k), 0x00000000, a(0xFFFFE08A.toInt(), 0.9f), 3f)
            }
            Z_ARMADILHA -> {
                oval(c, p, sx, sy, z.raio, a(COR_GELO, 0.2f), a(COR_GELO, 0.9f), 2f)
                p.color = a(0xFFE1F5FE.toInt(), 0.9f)
                for (i in 0 until 6) {
                    val ang = i * 1.0472f
                    c.drawCircle(sx + cos(ang) * z.raio * 0.6f, sy + sin(ang) * z.raio * 0.43f - 3f, 3f, p)
                }
            }
            Z_GELO -> oval(c, p, sx, sy, z.raio, a(COR_GELO, 0.22f * fade), a(0xFFE1F5FE.toInt(), 0.6f * fade), 2f)
            Z_CRISTAIS -> {
                oval(c, p, sx, sy, z.raio, a(COR_GELO, 0.14f * fade), a(COR_GELO, 0.6f * fade), 3f)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 3f
                p.color = a(0xFFE1F5FE.toInt(), 0.9f * fade)
                for (i in 0 until 7) {
                    val ang = i * 2.399f + time * 0.3f
                    val rr = z.raio * (((i * 53 + (time * 4f).toInt() * 17) % 10) / 10f)
                    val fx = sx + cos(ang) * rr
                    val fy = sy + sin(ang) * rr * 0.72f
                    val q = ((time * 3f + i * 0.37f) % 1f)
                    c.drawLine(fx, fy - 40f * (1f - q), fx, fy - 40f * (1f - q) + 10f, p)
                }
                p.style = Paint.Style.FILL
            }
            Z_TOTEM -> {
                oval(c, p, sx, sy, z.raio, a(COR_VERDE, 0.14f * fade), a(COR_VERDE, 0.55f * fade), 3f)
                p.color = a(0xFF6D4C41.toInt(), fade)
                c.drawRect(sx - 6f, sy - 34f, sx + 6f, sy, p)
                p.color = a(COR_VERDE, 0.9f * fade)
                c.drawCircle(sx, sy - 38f + 2f * sin(time * 4f), 9f, p)
            }
            Z_RAIZES -> {
                oval(c, p, sx, sy, z.raio, a(COR_VERDE, 0.16f * fade), a(0xFF2E7D32.toInt(), 0.85f * fade), 3f)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 4f
                p.color = a(0xFF4E342E.toInt(), 0.9f * fade)
                for (i in 0 until 8) {
                    val ang = i * 0.785f
                    c.drawLine(sx + cos(ang) * z.raio * 0.2f, sy + sin(ang) * z.raio * 0.14f, sx + cos(ang) * z.raio * 0.85f, sy + sin(ang) * z.raio * 0.6f - 8f, p)
                }
                p.style = Paint.Style.FILL
            }
            Z_ARVORE -> {
                oval(c, p, sx, sy, z.raio, a(COR_VERDE, 0.12f * fade), a(COR_VERDE, 0.5f * fade), 3f)
                p.color = a(0xFF5D4037.toInt(), fade)
                c.drawRect(sx - 12f, sy - 70f, sx + 12f, sy, p)
                p.color = a(0xFF2E7D32.toInt(), 0.95f * fade)
                c.drawCircle(sx, sy - 90f, 44f, p)
                c.drawCircle(sx - 28f, sy - 74f, 28f, p)
                c.drawCircle(sx + 28f, sy - 74f, 28f, p)
                p.color = a(COR_VERDE, 0.9f * fade)
                c.drawCircle(sx, sy - 96f + 3f * sin(time * 3f), 22f, p)
            }
            Z_REDEMOINHO -> {
                oval(c, p, sx, sy, z.raio, a(COR_VENTO, 0.12f * fade), a(COR_VENTO, 0.6f * fade), 3f)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 3f
                p.color = a(0xFFFFFFFF.toInt(), 0.7f * fade)
                for (i in 0 until 3) {
                    val rr = z.raio * (0.35f + 0.25f * i)
                    val st0 = time * 220f + i * 120f
                    c.drawArc(sx - rr, sy - rr * 0.72f - 12f * i, sx + rr, sy + rr * 0.72f - 12f * i, st0, 110f, false, p)
                }
                p.style = Paint.Style.FILL
            }
            Z_OLHO -> {
                val k = (z.idade / 1f).coerceIn(0f, 1f)
                oval(c, p, sx, sy, z.raio, a(COR_RAIO, 0.1f + 0.2f * k), a(COR_RAIO, 0.9f), 3f)
                oval(c, p, sx, sy, z.raio * (1f - k), 0x00000000, a(0xFFFFFFFF.toInt(), 0.9f), 2f)
            }
            Z_ONDA -> {
                val k = 1f - (z.vida / z.vida0).coerceIn(0f, 1f)
                oval(c, p, sx, sy, z.raio * k, a(z.cor, 0.18f * (1f - k)), a(z.cor, 0.9f * (1f - k)), 5f)
            }
        }
    }

    /** Desenha acima do chão: flechas, raios, status nos inimigos e escudo do jogador. */
    fun drawAir(c: Canvas, p: Paint, camX: Float, camY: Float, time: Float) {
        for (r in raios) {
            val k = (r.vida / r.vida0).coerceIn(0f, 1f)
            p.style = Paint.Style.STROKE
            p.strokeWidth = r.larg * (0.5f + k)
            p.color = a(r.cor, 0.9f * k)
            // linha em zigue-zague
            var px = r.x1 - camX
            var py = r.y1 - camY
            val n = 6
            for (i in 1..n) {
                val q = i / n.toFloat()
                var qx = r.x1 + (r.x2 - r.x1) * q - camX
                var qy = r.y1 + (r.y2 - r.y1) * q - camY
                if (i < n) {
                    qx += (((i * 7919 + (time * 30f).toInt() * 31) % 13) - 6f)
                    qy += (((i * 104729 + (time * 30f).toInt() * 17) % 13) - 6f)
                }
                c.drawLine(px, py, qx, qy, p)
                px = qx
                py = qy
            }
            p.style = Paint.Style.FILL
        }
        for (f in flechas) {
            val sx = f.x - camX
            val sy = f.y - camY - 22f - f.alvo.alt
            p.style = Paint.Style.FILL
            for (i in 1..4) {
                p.color = a(f.cor, 0.35f - i * 0.07f)
                c.drawCircle(sx - cos(f.ang) * i * 8f, sy - sin(f.ang) * i * 8f, 6f - i, p)
            }
            c.save()
            c.translate(sx, sy)
            c.rotate(f.ang * 57.29578f)
            p.color = 0xFF8D6E63.toInt()
            c.drawRect(-14f, -1.5f, 8f, 1.5f, p)
            p.color = a(f.cor, 0.95f)
            c.drawRect(-14f, -3.5f, -9f, 3.5f, p)
            c.drawRect(8f, -3f, 15f, 3f, p)
            p.color = 0xFFFFFFFF.toInt()
            c.drawRect(11f, -1.5f, 17f, 1.5f, p)
            c.restore()
        }
        // status nos inimigos
        for ((e, s) in est) {
            if (!e.alive) continue
            val sx = e.x - camX
            val topo = e.y - camY - e.radius * 2.7f - e.alt
            var off = -((s.burn + s.frio) * 4f)
            p.style = Paint.Style.FILL
            for (i in 0 until s.burn) {
                p.color = COR_BRASA
                c.drawCircle(sx + off, topo, 3.5f, p)
                off += 8f
            }
            for (i in 0 until s.frio) {
                p.color = COR_GELO
                c.drawCircle(sx + off, topo, 3.5f, p)
                off += 8f
            }
            if (s.venenoT > 0f) {
                p.color = COR_VERDE
                c.drawCircle(sx + off, topo, 3.5f, p)
            }
            if (s.marcaT > 0f) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 3f
                p.color = a(0xFFFF5252.toInt(), 0.9f)
                c.drawCircle(sx, topo - 14f - 2f * sin(time * 7f), 9f, p)
                c.drawLine(sx - 13f, topo - 14f, sx + 13f, topo - 14f, p)
                c.drawLine(sx, topo - 27f, sx, topo - 1f, p)
                p.style = Paint.Style.FILL
            }
            if (s.congT > 0f) {
                p.color = a(0xFFE1F5FE.toInt(), 0.45f)
                c.drawCircle(sx, e.y - camY - e.radius * 0.5f - e.alt, e.radius * 1.4f, p)
            }
        }
        val pl = host.jogador
        if (pl != null && pl.alive && pl.shield > 0.5f) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 3f
            p.color = a(0xFF9CFFB0.toInt(), 0.55f)
            c.drawCircle(pl.x - camX, pl.y - camY - 18f - pl.alt, 30f + 1.5f * sin(time * 5f), p)
            p.style = Paint.Style.FILL
        }
    }

    /** Barra de Sintonia + nível, no topo da tela (fora do zoom). */
    fun drawHud(c: Canvas, p: Paint, w: Float, @Suppress("UNUSED_PARAMETER") h: Float) {
        val esc = st.escola ?: return
        val bw = min(260f, w * 0.34f)
        val x0 = w * 0.5f - bw / 2f
        val y0 = 70f
        p.style = Paint.Style.FILL
        p.color = 0xFF111111.toInt()
        c.drawRoundRect(x0 - 2f, y0 - 2f, x0 + bw + 2f, y0 + 14f, 5f, 5f, p)
        p.color = 0xFF2A2A2A.toInt()
        c.drawRect(x0, y0, x0 + bw, y0 + 12f, p)
        p.color = if (superT > 0f) 0xFFFFD54F.toInt() else esc.cor
        c.drawRect(x0, y0, x0 + bw * (sint / 100f).coerceIn(0f, 1f), y0 + 12f, p)
        p.color = 0x44FFFFFF
        c.drawRect(x0, y0, x0 + bw * (sint / 100f).coerceIn(0f, 1f), y0 + 4f, p)
        // XP
        val xpN = if (st.nivel >= ElfoCatalog.NIVEL_MAX) 1f else st.xp / st.xpPara(st.nivel).toFloat()
        p.color = 0xFF111111.toInt()
        c.drawRect(x0, y0 + 16f, x0 + bw, y0 + 21f, p)
        p.color = 0xFFFFD54F.toInt()
        c.drawRect(x0, y0 + 16f, x0 + bw * xpN.coerceIn(0f, 1f), y0 + 21f, p)
        p.textAlign = Paint.Align.CENTER
        p.textSize = 22f
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f
        p.color = 0xFF000000.toInt()
        val txt = esc.recurso + " " + sint.toInt() + "   •   Nv " + st.nivel + (if (st.pontos > 0) "  (+" + st.pontos + " pts)" else "")
        c.drawText(txt, w * 0.5f, y0 - 8f, p)
        p.style = Paint.Style.FILL
        p.color = 0xFFFFFFFF.toInt()
        c.drawText(txt, w * 0.5f, y0 - 8f, p)
        if (mira != 0) {
            val d = defSlot(mira)
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

    /** Marca do Caçador: seta na borda da tela apontando para o inimigo marcado que está fora de vista. */
    fun drawMarcas(c: Canvas, p: Paint, camX: Float, camY: Float, w: Float, h: Float, zoom: Float) {
        for ((e, s) in est) {
            if (!e.alive || s.marcaT <= 0f) continue
            val sx = (e.x - camX - w * 0.5f) * zoom + w * 0.5f
            val sy = (e.y - camY - h * 0.5f) * zoom + h * 0.5f
            val m = 40f
            if (sx in m..(w - m) && sy in m..(h - m)) continue
            val ax = sx.coerceIn(m, w - m)
            val ay = sy.coerceIn(m, h - m)
            val ang = atan2(sy - ay, sx - ax)
            c.save()
            c.translate(ax, ay)
            c.rotate(ang * 57.29578f)
            p.style = Paint.Style.FILL
            p.color = 0xFFFF5252.toInt()
            val path = android.graphics.Path()
            path.moveTo(16f, 0f)
            path.lineTo(-10f, -11f)
            path.lineTo(-10f, 11f)
            path.close()
            c.drawPath(path, p)
            c.restore()
        }
    }
}
