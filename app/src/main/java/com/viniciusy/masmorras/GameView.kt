package com.viniciusy.masmorras

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pixelFace: Typeface by lazy {
        androidx.core.content.res.ResourcesCompat.getFont(context, R.font.pixel) ?: Typeface.DEFAULT_BOLD
    }
    private var map: GameMap = TileMap()
    private var hadEnemies = true
    private var player: Player? = null
    private val enemies = mutableListOf<Enemy>()
    private val projectiles = mutableListOf<Projectile>()
    private val particles = mutableListOf<Particle>()
    private var chargeFxT = 0f
    private val texts = mutableListOf<DmgText>()
    private val drawList = ArrayList<Entity>()
    private var vignette: Shader? = null

    private var camX = 0f
    private var camY = 0f
    private var camVX = 0f
    private var camVY = 0f
    private var lookAheadX = 0f
    private var lookAheadY = 0f
    private var camZoom = 1f
    private var targetZoom = 1f

    // Zoom do jogador (pinça): 1.0 = o mais longe possível (visão atual); acima disso aproxima
    private val MIN_ZOOM = 1.0f
    private val MAX_ZOOM = 3.6f
    private var userZoom = 1f
    private var attackHeld = false
    // arqueiro: botão de ataque segurado (mira) e força do arco
    private var bowHeld = false
    private var bowT = 0f
    private var pendingShotPow = 0f
    // lança: golpe pendente (dano sai no instante da estocada), arremesso pendente e a lança no chão
    private var thrown: ThrownSpear? = null
    private var pendingHitT = -1f
    private var pendingHitSwing = 0
    private var pendingCastT = -1f
    private var pendingBarbT = -1f
    private var pendingBarbSwing = 0
    // arqueiro: flecha pendente, skills, armadilhas e efeitos
    private var pendingShotT = -1f
    private var pendingSkillT = -1f
    private var pendingSkillK = 0
    private var skillDX = 1f
    private var skillDY = 0f
    private val traps = ArrayList<Trap>()
    private val rains = ArrayList<RainZone>()
    private val bolts = ArrayList<ThunderBolt>()
    private val zaps = ArrayList<ZapFx>()
    // ninja: sombra, shurikens, cortes, golpe pendente e Marca da Morte
    private var ninjaShadow: NinjaShadow? = null
    private val swapFxs = ArrayList<SwapFx>()
    private var darkFlash = 0f
    private val shurikens = ArrayList<Shuriken>()
    private val slashFx = ArrayList<SlashFx>()
    private var pendingNinjaT = -1f
    private var pendingNinjaSwing = 0
    // pikachu: raio pendente, ataque segurado, quem já levou Investida / Ataque Rápido
    private var pendingPikaT = -1f
    private var pikaHeld = false
    // ichigo: golpe pendente, ondas de Getsuga, efeitos curtos e quem já levou o Shunpo / Sonído
    private var pendingIchiT = -1f
    private var pendingIchiSwing = 0
    private var pendingLuffyT = -1f
    private var pendingLuffySwing = 0
    private var pendingExplT = -1f
    private var pendingExplSwing = 0
    private val getsugas = ArrayList<GetsugaWave>()
    private val crossWaves = ArrayList<CrossWave>()
    private var ichiSkyWas = false
    private val ichiFxs = ArrayList<IchiFx>()
    private val ichiHit = HashSet<Enemy>()
    private var ichiDashWas = false
    private var ichiHolPrev = false
    private var voltWasOn = false
    private val voltHit = HashSet<Enemy>()
    private val quickHit = HashSet<Enemy>()
    private var markTarget: Enemy? = null
    private var markT = 0f
    private var markDmg = 0f
    private val markTotal = 3.0f
    private var pendingThrow = false
    private var throwDX = 1f
    private var throwDY = 0f
    private var throwRange = 0f
    private var throwPower = 0f
    private var aimTarget: Enemy? = null
    // mira feita arrastando o dedo no botão de ataque (lança)
    private var aimOn = false
    private var aimX = 0f
    private var aimY = 1f
    private var hintCd = 0f
    private var heldT = 0f
    private var queuedAt = 0L

    // ── Luffy: ataque por alvo. Tocar num inimigo marca o alvo; o botão de ataque (segurar = ataque automático)
    //    solta o soco direto nele, sem errar, a cada 1 segundo. Sem alvo marcado, pega o inimigo mais perto. ──
    private var luffyTarget: Enemy? = null
    private var luffyHitTarget: Enemy? = null
    private var luffyAimT: Enemy? = null   // alvo que o braço do Luffy segue durante TODO o soco (inclusive depois do impacto)
    private var luffyAutoAtk = false
    private var luffyMsgAt = 0L

    // ── Sylvarion (Arqueiro do Vendaval): combate por alvo, árvore de talentos e skills de vento ──
    private val sylv = SylvEngine(object : SylvHost {
        override val jogador: Player? get() = player
        override val inimigos: List<Enemy> get() = enemies
        override fun dano(e: Enemy, d: Float, fx: Float, fy: Float, kb: Float, silencioso: Boolean) {
            damageEnemy(e, d, fx, fy, kb, if (silencioso) "" else null)
        }
        override fun particulas(x: Float, y: Float, n: Int, cor: Int) {
            burst(x, y, n, cor)
        }
        override fun som(nome: String, vol: Float) {
            snd(nome, vol)
        }
        override fun texto(x: Float, y: Float, t: String, cor: Int) {
            texts.add(DmgText(x, y, t, 0.9f, cor))
        }
        override fun livre(x: Float, y: Float): Boolean = !map.isWall(x, y)
        override fun tremor(v: Float) {
            shake = max(shake, v)
        }
    })

    fun sylvEngine(): SylvEngine = sylv

    /** Pausa o jogo (menus de talentos abertos). */
    var paused = false

    private var tapX = 0f
    private var tapY = 0f
    private var tapT = 0L
    private var tapOk = false
    private var pinching = false
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(d: ScaleGestureDetector): Boolean {
            pinching = true
            return true
        }

        override fun onScale(d: ScaleGestureDetector): Boolean {
            userZoom = (userZoom * d.scaleFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
            return true
        }

        override fun onScaleEnd(d: ScaleGestureDetector) {
            pinching = false
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tapX = event.x
                tapY = event.y
                tapT = System.currentTimeMillis()
                tapOk = true
            }
            MotionEvent.ACTION_POINTER_DOWN -> tapOk = false
            MotionEvent.ACTION_MOVE -> if (tapOk && hypot(event.x - tapX, event.y - tapY) > 30f) tapOk = false
            MotionEvent.ACTION_UP -> {
                if (tapOk && !pinching && System.currentTimeMillis() - tapT < 400L) tocarMundo(event.x, event.y)
                tapOk = false
            }
            MotionEvent.ACTION_CANCEL -> tapOk = false
        }
        return true
    }

    /** Toque na tela do jogo: o Sylvarion marca o alvo (ou mira a área da skill). */
    private fun tocarMundo(sx: Float, sy: Float) {
        val p = player ?: return
        if ((p.classe != Classe.ARQUEIRO && p.classe != Classe.LUFFY) || !p.alive || gameOver) return
        val w = width.toFloat()
        val h = height.toFloat()
        val z = if (camZoom == 0f) 1f else camZoom
        val wx = (sx - w * 0.5f) / z + w * 0.5f + camX
        val wy = (sy - h * 0.5f) / z + h * 0.5f + camY
        if (p.classe == Classe.LUFFY) luffyTocar(wx, wy) else sylv.tocar(wx, wy)
    }

    /** Luffy: toque no mundo marca o inimigo mais perto do dedo como alvo; toque no vazio tira o alvo. */
    private fun luffyTocar(wx: Float, wy: Float) {
        var best: Enemy? = null
        var bd = 1e9f
        for (e in enemies) {
            if (!e.alive) continue
            val d = hypot(e.x - wx, e.y - 22f - e.alt - wy)
            if (d < e.radius + 34f && d < bd) {
                bd = d
                best = e
            }
        }
        luffyTarget = best
        if (best != null) snd("ui_ping", 0.4f)
    }

    /** Escolhe o alvo do soco: o marcado (se vivo) ou, sem marca, o inimigo vivo mais perto dentro do alcance. */
    private fun luffyPickTarget(p: Player): Enemy? {
        val cur = luffyTarget
        if (cur != null && cur.alive) return cur
        luffyTarget = null
        var best: Enemy? = null
        var bd = 1e9f
        for (e in enemies) {
            if (!e.alive) continue
            val d = hypot(e.x - p.x, e.y - p.y) - e.radius
            if (d <= Player.LUFFY_RANGE && d < bd) {
                bd = d
                best = e
            }
        }
        luffyTarget = best
        return best
    }

    private fun luffyMsg(p: Player, txt: String) {
        val now = System.currentTimeMillis()
        if (now - luffyMsgAt < 900L) return
        luffyMsgAt = now
        texts.add(DmgText(p.x, p.y - p.radius - 52f - p.alt, txt, 0.7f, 0xFFB0BEC5.toInt()))
        snd("ui_error", 0.4f)
    }

    /** Soco direto no alvo: vira o corpo para ele, o braço estica até a distância exata e o golpe não erra. */
    private fun luffyAttack(p: Player, now: Long) {
        val tgt = luffyPickTarget(p)
        if (tgt == null) {
            luffyMsg(p, "Sem alvo")
            return
        }
        val dx = tgt.x - p.x
        val dy = tgt.y - p.y
        val d = hypot(dx, dy).coerceAtLeast(1f)
        if (d - tgt.radius > Player.LUFFY_RANGE) {
            luffyMsg(p, "Alvo longe")
            return
        }
        p.facingX = dx / d
        p.facingY = dy / d
        p.luffyPunchLen = (d - tgt.radius * 0.3f).coerceIn(46f, Player.LUFFY_RANGE + tgt.radius)
        p.doAttack(now)
        swingSound(p)
        luffyHitTarget = tgt
        luffyAimT = tgt
        pendingLuffyT = p.luffyDelay()
        pendingLuffySwing = p.swing
    }

    /** Anel vermelho no chão em volta do alvo do Luffy. */
    private fun drawLuffyTarget(canvas: Canvas, p: Player) {
        val t = luffyTarget ?: return
        if (!t.alive) return
        val sx = t.x - camX
        val feet = t.y - camY + t.radius * 0.55f - t.alt
        val pulso = 1f + 0.06f * sin(time * 6f)
        val r = t.radius * 1.35f * pulso
        paint.style = Paint.Style.FILL
        paint.color = 0x22FF5252
        canvas.drawOval(sx - r, feet - r * 0.72f, sx + r, feet + r * 0.72f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = 0xFFFF5252.toInt()
        canvas.drawOval(sx - r, feet - r * 0.72f, sx + r, feet + r * 0.72f, paint)
        paint.style = Paint.Style.FILL
    }

    /** Bonecos de treino ao redor do herói: perto, no alcance e um além do alcance do soco. */
    private fun spawnDummies(sx: Float, sy: Float) {
        val dists = floatArrayOf(120f, 215f, 330f)
        val angs = floatArrayOf(0f, 0.55f, -0.55f, 1.1f, -1.1f, 1.7f, -1.7f, 2.4f, -2.4f, 3.1416f)
        for ((i, d) in dists.withIndex()) {
            for (a0 in angs) {
                val a = a0 + (i - 1) * 0.5f
                val x = sx + cos(a) * d
                val y = sy + sin(a) * d
                if (blockedAt(x, y, 14f) || blockedAt(x + 16f, y, 6f) || blockedAt(x - 16f, y, 6f)) continue
                if (enemies.any { it.dummy && hypot(it.x - x, it.y - y) < 60f }) continue
                val en = Enemy(x, y, 0)
                en.dummy = true
                en.maxHp = 1000f
                en.hp = 1000f
                en.ground = map.heightAt(x, y)
                en.alt = en.ground
                enemies.add(en)
                break
            }
        }
    }
    private var time = 0f
    private var shake = 0f
    private var lastTime = System.nanoTime()
    private var running = false
    private var gameOver = false
    private var victory = false

    // Framing estilo Minecraft Dungeons: herói um pouco abaixo do centro
    private val FRAME_X = 0.50f
    private val FRAME_Y = 0.50f
    private val CAM_SMOOTH = 8.5f
    private val LOOK_AHEAD = 0f

    var onGameOver: ((Boolean) -> Unit)? = null // true = vitória
    var onCharge: ((Float) -> Unit)? = null // 0 = sem carga, 0..1 enquanto segura o ataque
    private var lastChargeSent = -1f

    // ── áudio ──
    private var stepAcc = 0f
    private var stepIdx = 0
    private var hbT = 0f
    private var flyWas = false
    private var noLandSnd = false

    private fun snd(name: String, vol: Float = 1f, rate: Float = 1f) {
        Sfx.play(name, vol, rate)
    }

    private fun sndAt(name: String, x: Float, y: Float, vol: Float = 1f, rate: Float = 1f) {
        val p = player ?: return
        Sfx.playAt(name, x - p.x, y - p.y, vol, rate)
    }

    private fun vary(): Float = 0.94f + Random.nextFloat() * 0.12f

    private fun swingSound(p: Player) {
        when (p.classe) {
            Classe.BARBARO -> snd("axe1", 1f, if (p.swing == 2) 0.78f else if (p.swing == 1) 0.92f else 1.02f)
            Classe.ICHIGO -> if (p.hollow) snd("heavy_swing", 0.8f, (if (p.swing == 2) 0.85f else 1.05f) * vary()) else snd(if (p.swing == 2) "sword3" else if (p.swing == 1) "sword2" else "sword1", 0.9f, 0.95f * vary())
            Classe.LUFFY -> snd("heavy_swing", 0.6f, (if (p.swing == 2) 1.0f else 1.3f) * vary())
            Classe.EXPLORADOR -> snd("spear_sweep", 0.7f, (if (p.swing == 2) 1.1f else 1.5f) * vary())
            Classe.NINJA -> snd(if (p.swing == 2) "sword3" else if (p.swing == 1) "sword2" else "sword1", 0.8f, 1.22f * vary())
            Classe.GUERREIRO -> if (p.weapon == 1) {
                when (p.swing) {
                    0 -> snd("spear_thrust", 0.9f, vary())
                    1 -> snd("spear_sweep", 0.9f, vary())
                    else -> snd("spear_thrust", 1f, 0.82f)
                }
            } else {
                when (p.swing) {
                    0 -> snd("sword1", 0.9f, vary())
                    1 -> snd("sword2", 0.9f, vary())
                    else -> snd("sword3", 1f, vary())
                }
            }
            else -> {}
        }
    }

    private val loop = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.nanoTime()
            val dt = ((now - lastTime) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            lastTime = now
            update(dt)
            invalidate()
            postOnAnimation(this)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val r = max(w, h) * 0.95f
        vignette = RadialGradient(
            w / 2f, h / 2f, r,
            intArrayOf(0x00101010, 0x101A1B, 0x2A1B1A, 0xFF0A0D10.toInt()),
            floatArrayOf(0f, 0.36f, 0.74f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    /** Centraliza a câmera quando o mapa é menor que a tela; senão limita às bordas. */
    private fun camClamp(v: Float, world: Float, view: Float): Float =
        if (world <= view) (world - view) / 2f else v.coerceIn(0f, world - view)

    fun startGame(classe: Classe, custom: MapData? = null) {
        map = if (custom != null) CustomMap(custom) else TileMap()
        val (sx, sy) = map.getPlayerSpawn()
        map.prewarm(sx, sy)
        player = Player(sx, sy, classe)
        player?.let { it.ground = map.heightAt(sx, sy); it.alt = it.ground }
        if (classe == Classe.ARQUEIRO) sylv.novaPartida(context)
        enemies.clear()
        projectiles.clear()
        thrown = null
        pendingHitT = -1f
        pendingCastT = -1f
        pendingBarbT = -1f
        pendingShotT = -1f
        pendingSkillT = -1f
        traps.clear()
        rains.clear()
        bolts.clear()
        zaps.clear()
        ninjaShadow = null
        shurikens.clear()
        slashFx.clear()
        swapFxs.clear()
        darkFlash = 0f
        pendingNinjaT = -1f
        pendingPikaT = -1f
        pikaHeld = false
        pendingIchiT = -1f
        pendingLuffyT = -1f
        pendingExplT = -1f
        getsugas.clear()
        crossWaves.clear()
        ichiSkyWas = false
        ichiFxs.clear()
        ichiHit.clear()
        ichiDashWas = false
        ichiHolPrev = false
        voltWasOn = false
        voltHit.clear()
        quickHit.clear()
        markTarget = null
        markT = 0f
        markDmg = 0f
        pendingThrow = false
        aimTarget = null
        aimOn = false
        particles.clear()
        texts.clear()
        enemies.addAll(map.createEnemies())
        luffyTarget = null
        luffyHitTarget = null
        luffyAutoAtk = false
        gatIdx = 0
        gatAcc.clear()
        if (classe == Classe.LUFFY) spawnDummies(sx, sy)
        hadEnemies = enemies.any { !it.dummy }
        gameOver = false
        victory = false
        running = true
        shake = 0f
        attackHeld = false
        bowHeld = false
        bowT = 0f
        queuedAt = 0L
        Sfx.duck(false)
        Sfx.stopLoops()
        stepAcc = 0f
        hbT = 0f
        flyWas = false
        noLandSnd = false
        camVX = 0f
        camVY = 0f
        lookAheadX = 0f
        lookAheadY = 0f
        camZoom = userZoom
        targetZoom = userZoom
        val desiredX = camClamp(sx - width * FRAME_X, map.worldWidth(), width.toFloat())
        val desiredY = camClamp(sy - height * FRAME_Y, map.worldHeight(), height.toFloat())
        camX = desiredX
        camY = desiredY
        lastTime = System.nanoTime()
        removeCallbacks(loop)
        postOnAnimation(loop)
    }

    fun stopGame() {
        Sfx.stopLoops()
        running = false
        removeCallbacks(loop)
    }

    fun setMove(dx: Float, dy: Float) {
        val p = player ?: return
        if (!p.alive) return
        val len = hypot(dx, dy)
        if (len > 0.1f) {
            val nx = dx / len
            val ny = dy / len
            p.inX = nx
            p.inY = ny
            if (!p.rolling && !p.skilling && !pendingThrow && !(aimOn && (attackHeld || bowHeld)) && !(p.classe == Classe.LUFFY && p.attackAnim > 0.05f)) {
                p.facingX = nx
                p.facingY = ny
            }
        } else {
            p.inX = 0f
            p.inY = 0f
        }
    }

    /** Mira da lança: direção do arrasto do dedo no botão de ataque (já normalizada na tela). */
    fun setAim(dx: Float, dy: Float) {
        val p = player ?: return
        if (!(p.classe == Classe.GUERREIRO && p.weapon == 1) && p.classe != Classe.ARQUEIRO) return
        val l = hypot(dx, dy)
        if (l < 0.001f) return
        aimX = dx / l
        aimY = dy / l
        aimOn = true
    }

    fun clearAim() {
        aimOn = false
    }

    var onWeapon: ((Int) -> Unit)? = null

    /** Troca entre espada+escudo e lança (só Guerreiro, parado no chão e sem golpe em andamento). */
    fun switchWeapon() {
        val p = player ?: return
        if (!p.alive || gameOver || p.classe != Classe.GUERREIRO) return
        if (p.rolling || attackHeld || p.charging) return
        val now = System.currentTimeMillis()
        if (now < p.swapUntil) return
        p.weapon = 1 - p.weapon
        p.swapUntil = now + 450L
        p.comboStep = -1
        burst(p.x, p.y + p.radius * 0.55f - p.ground, 7, 0xFFFFE08A.toInt())
        onWeapon?.invoke(p.weapon)
        snd("weapon_swap", 0.9f)
    }

    fun jump() {
        val p = player ?: return
        if (gameOver) return
        val wasAir = !p.grounded
        if (p.jump()) {
            if (wasAir && p.classe == Classe.NINJA) {
                // pulo-dash do ninja: some o golpe que estava saindo e deixa um rastro de fumaça sombria
                pendingNinjaT = -1f
                burst(p.x, p.y + p.radius * 0.55f - p.alt, 8, 0xFF6A4A9A.toInt())
                burst(p.x, p.y + p.radius * 0.55f - p.alt, 4, 0xFFB3202D.toInt())
                snd("dash", 0.9f, 1.25f * vary())
                shake = max(shake, 0.04f)
            } else if (wasAir) {
                // pulo duplo: cancela o tiro que estava saindo e solta um anel de poeira no ar
                pendingShotT = -1f
                burst(p.x, p.y + p.radius * 0.55f - p.alt, 7, 0xFFD6FFD9.toInt())
                snd("jump_air", 0.85f, vary())
            } else {
                burst(p.x, p.y + p.radius * 0.55f - p.ground, 5, 0xFFB8B0A8.toInt())
                snd("jump", 0.75f, vary())
            }
        }
    }

    private var jumpHeld = false
    private var jumpHoldT = 0f

    /** Botão de pular apertado: o mago, se continuar segurando no ar, passa a voar. */
    fun jumpDown() {
        val p = player ?: return
        if (gameOver) return
        jumpHeld = true
        jumpHoldT = 0f
        if (p.classe == Classe.BARBARO) {
            if (p.startLeapCharge()) snd("leap_charge", 0.85f)
            return
        }
        if (p.classe == Classe.LUFFY) {
            // segurar: agacha e torce as pernas (Gomu Gomu no Helicóptero); soltar rápido vira pulo normal
            if (p.startHeliCharge()) snd("leap_charge", 0.55f, 1.35f)
            return
        }
        if (p.classe == Classe.MAGO && p.flying) return
        if (p.classe == Classe.ICHIGO && p.sky) return
        if (p.classe == Classe.EXPLORADOR) {
            // no ar, apertar e segurar lança o chicote para o céu e começa o balanço
            if (!p.grounded) explorerSwingStart(p)
            else jump()
            return
        }
        jump()
    }

    /** Explorador: chicote preso no céu. Segurar o botão mantém o balanço; soltar larga a corda. */
    private fun explorerSwingStart(p: Player) {
        if (!p.startSwing()) return
        pendingExplT = -1f
        val ay = p.swAY + p.radius * 0.55f - p.swAAlt
        burst(p.swAX, ay, 7, 0xFFFFE08A.toInt())
        burst(p.x, p.y + p.radius * 0.55f - p.alt, 4, 0xFFE9D9B0.toInt())
        snd("dash", 0.8f, 1.5f * vary())
        shake = max(shake, 0.03f)
    }

    private fun explorerSwingEnd(p: Player) {
        if (!p.swinging) return
        p.endSwing()
        burst(p.x, p.y + p.radius * 0.55f - p.alt, 5, 0xFFE9D9B0.toInt())
        snd("jump_air", 0.7f, 0.9f * vary())
    }

    fun jumpUp() {
        jumpHeld = false
        jumpHoldT = 0f
        val p = player ?: return
        if (p.swinging) {
            explorerSwingEnd(p)
            return
        }
        if (p.leapCharging) {
            if (p.releaseLeap()) {
                snd("leap_launch", 0.95f)
                groundSmash(p, 62f, 0.6f, 1.2f, 6)
            }
            return
        }
        if (p.heliCharging) {
            if (p.releaseHeli()) {
                if (p.heli) {
                    burst(p.x, p.y + p.radius * 0.55f - p.ground, 8, 0xFFB8B0A8.toInt())
                    snd("jump_air", 0.9f, 0.85f * vary())
                } else {
                    burst(p.x, p.y + p.radius * 0.55f - p.ground, 5, 0xFFB8B0A8.toInt())
                    snd("jump", 0.75f, vary())
                }
            }
            return
        }
        if (p.sky) {
            p.sky = false
            p.glide = true
        }
        if (p.flying) {
            p.flying = false
            p.glide = true
        }
    }

    /** Teleporte do mago: salta instantaneamente para a frente (até onde houver chão livre). */
    private fun teleport(p: Player) {
        val now = System.currentTimeMillis()
        if (!p.canTeleport(now) || p.rolling) return
        var dx = p.inX
        var dy = p.inY
        if (hypot(dx, dy) < 0.1f) {
            dx = p.facingX
            dy = p.facingY
        }
        val l = hypot(dx, dy).coerceAtLeast(0.001f)
        dx /= l
        dy /= l
        var d = Player.TP_DIST
        var nx = p.x
        var ny = p.y
        var ok = false
        while (d >= 40f) {
            val tx = (p.x + dx * d).coerceIn(p.radius, map.worldWidth() - p.radius)
            val ty = (p.y + dy * d).coerceIn(p.radius, map.worldHeight() - p.radius)
            if (!playerBlocked(p, tx, ty)) {
                nx = tx
                ny = ty
                ok = true
                break
            }
            d -= 15f
        }
        if (!ok) return
        p.mp -= Player.TP_COST
        p.lastTp = now
        p.tpFromX = p.x
        p.tpFromY = p.y
        p.tpT = Player.TP_TIME
        p.facingX = dx
        p.facingY = dy
        p.x = nx
        p.y = ny
        p.attackAnim = 0f
        p.charging = false
        p.chargeT = 0f
        burst(p.tpFromX, p.tpFromY + p.radius * 0.55f - p.alt, 10, 0xFF5FD4FF.toInt())
        burst(nx, ny + p.radius * 0.55f - p.alt, 10, 0xFFC4F4FF.toInt())
        shake = max(shake, 0.08f)
        snd("teleport_out", 0.85f)
        snd("teleport_in", 0.85f)
    }

    /** Soltou o botão de rolar: o dash do Luffy deixa de carregar e é lançado. */
    fun rollRelease() {
        player?.luffyHeld = false
    }

    fun roll() {
        val p = player ?: return
        if (gameOver) return
        if (p.classe == Classe.LUFFY && p.g2T > 0f) {
            luffySoru(p)
            return
        }
        if (p.swinging) {
            // rolar enquanto balança = soltar a corda
            explorerSwingEnd(p)
            return
        }
        if (p.classe == Classe.MAGO) {
            teleport(p)
            return
        }
        if (p.classe == Classe.NINJA) {
            // no lugar do rolamento: Sombra Viva (1º toque cria a sombra, 2º toque troca de lugar com ela)
            ninjaSkill(p, 2)
            return
        }
        if (p.classe == Classe.ARQUEIRO) {
            val ox = p.x
            val oy = p.y
            val gz = p.ground
            if (p.startHop(System.currentTimeMillis())) {
                snd("hop", 0.9f, vary())
                pendingShotT = -1f
                burst(ox, oy + p.radius * 0.55f - gz, 7, 0xFFB8B0A8.toInt())
            }
            return
        }
        if (p.classe == Classe.ICHIGO) {
            // Shunpo (Bankai) / Sonído (Vasto Lorde): avanço veloz e invulnerável no lugar do rolamento
            val now = System.currentTimeMillis()
            if (p.ichiDashReady(now)) {
                var dx = p.inX
                var dy = p.inY
                if (hypot(dx, dy) < 0.1f) {
                    dx = if (aimOn) aimX else p.facingX
                    dy = if (aimOn) aimY else p.facingY
                }
                pendingShotT = -1f
                pendingIchiT = -1f
                queuedAt = 0L
                p.lastIchiDash = now
                ichiDashStart(p, dx, dy, p.hollow, Player.ICHI_DASH_DIST, Player.ICHI_DASH_T, 1)
            }
            return
        }
        if (p.classe == Classe.PIKACHU) {
            // Ataque Rápido: arrancada veloz e invulnerável, que também acerta quem estiver no caminho
            if (p.startRoll(System.currentTimeMillis())) {
                quickHit.clear()
                pendingPikaT = -1f
                snd("dash", 0.85f, 1.5f * vary())
                burst(p.x, p.y + p.radius * 0.55f - p.ground, 5, 0xFFFFE23A.toInt())
            }
            return
        }
        if (p.classe == Classe.BARBARO) {
            val ax = if (aimOn) aimX else p.facingX
            val ay = if (aimOn) aimY else p.facingY
            if (p.startDash(System.currentTimeMillis(), ax, ay)) {
                snd("dash", 0.95f, vary())
                groundSmash(p, 58f, 0.5f, 1f, 6)
            }
            return
        }
        if (p.startRoll(System.currentTimeMillis())) {
            pendingLuffyT = -1f
            pendingExplT = -1f
            snd("roll", 0.9f, vary())
            burst(p.x, p.y + p.radius * 0.55f - p.ground, 6, 0xFFB8B0A8.toInt())
        }
    }

    private fun cellBlocked(x: Float, y: Float, lim: Float): Boolean =
        map.isWall(x, y) || map.heightAt(x, y) > lim

    private fun playerBlocked(p: Player, x: Float, y: Float): Boolean {
        val r = p.radius * 0.6f
        val lim = p.alt + TileMap.LEVEL_PX * 0.55f
        return cellBlocked(x, y, lim) || cellBlocked(x + r, y, lim) || cellBlocked(x - r, y, lim) ||
            cellBlocked(x, y + r, lim) || cellBlocked(x, y - r, lim)
    }

    /** Skill do arqueiro: 1 = Chuva de Flechas, 2 = Flecha Trovão. */
    fun skill(k: Int) {
        val p = player ?: return
        if (gameOver) return
        if (p.classe == Classe.NINJA) {
            ninjaSkill(p, k)
            return
        }
        if (p.classe == Classe.PIKACHU) {
            pikaSkill(p, k)
            return
        }
        if (p.classe == Classe.ICHIGO) {
            ichiSkill(p, k)
            return
        }
        if (p.classe == Classe.LUFFY) {
            luffySkill(p, k)
            return
        }
        if (p.classe != Classe.ARQUEIRO) return
        // Sylvarion: as 5 skills equipadas na árvore de talentos (alvo, área mirada, direção ou self)
        val erro = sylv.usar(k)
        if (erro != null) {
            snd("ui_error", 0.5f)
            if (erro.isNotEmpty()) texts.add(DmgText(p.x, p.y - 70f - p.alt, erro, 0.8f, 0xFFFFAB91.toInt()))
        }
        if (true) return
        if (!p.startSkill(k, System.currentTimeMillis())) {
            snd("ui_error", 0.5f)
            return
        }
        pendingShotT = -1f
        queuedAt = 0L
        skillDX = p.facingX
        skillDY = p.facingY
        pendingSkillK = k
        pendingSkillT = p.skillReleaseAt(k)
        snd(if (k == 1) "rain_cast" else "thunder_cast", 0.9f)
    }

    /** Instante em que a flecha sai do arco na skill. */
    private fun releaseSkill(p: Player, k: Int) {
        if (p.classe == Classe.NINJA) {
            ninjaRelease(p, k)
            return
        }
        if (p.classe == Classe.PIKACHU) {
            pikaRelease(p, k)
            return
        }
        if (p.classe == Classe.ICHIGO) {
            ichiRelease(p, k)
            return
        }
        var dx = skillDX
        var dy = skillDY
        val l0 = hypot(dx, dy).coerceAtLeast(0.001f)
        dx /= l0
        dy /= l0
        if (k == 1) {
            // a chuva cai sobre o inimigo mais próximo à frente; sem inimigo, sobre o chão 210px adiante
            var tx: Float
            var ty: Float
            var best = 340f
            var tgt: Enemy? = null
            for (e in enemies) {
                if (!e.alive) continue
                val ex = e.x - p.x
                val ey = e.y - p.y
                val d = hypot(ex, ey)
                if (d in 20f..best && (ex * dx + ey * dy) / d > 0.35f) {
                    best = d
                    tgt = e
                }
            }
            if (tgt != null) {
                tx = tgt.x
                ty = tgt.y
            } else {
                val r = rayLen(p.x, p.y, dx, dy, 210f)
                tx = p.x + dx * r
                ty = p.y + dy * r
            }
            tx = tx.coerceIn(40f, map.worldWidth() - 40f)
            ty = ty.coerceIn(40f, map.worldHeight() - 40f)
            rains.add(RainZone(tx, ty, map.heightAt(tx, ty), 100f))
            shake = max(shake, 0.1f)
            snd("rain_release", 0.95f)
        } else {
            // leve ímã: se tem inimigo bem na linha, a flecha trovão corrige a mira
            var bestDot = 0.94f
            for (e in enemies) {
                if (!e.alive) continue
                val ex = e.x - p.x
                val ey = e.y - p.y
                val d = hypot(ex, ey)
                if (d < 40f || d > 560f) continue
                val dot = (ex * dx + ey * dy) / d
                if (dot > bestDot) {
                    bestDot = dot
                    dx = ex / d
                    dy = ey / d
                }
            }
            bolts.add(ThunderBolt(p.x + dx * 24f, p.y + dy * 24f, dx, dy, p.danoH * 2.3f))
            shake = max(shake, 0.2f)
            snd("thunder_fire", 1f)
        }
    }

    private fun thunderHit(b: ThunderBolt, e: Enemy) {
        damageEnemy(e, b.damage, b.x - b.dx * 20f, b.y - b.dy * 20f, 1.2f, "")
        sndAt("thunder_hit", e.x, e.y, 0.95f, vary())
        e.stunT = max(e.stunT, 0.5f)
        burst(e.x, e.y - 14f - e.alt, 8, 0xFFFFF27A.toInt())
        shake = max(shake, 0.12f)
        // o raio salta para até 2 inimigos próximos
        var prev = e
        for (i in 0 until 2) {
            var next: Enemy? = null
            var nd = 150f
            for (o in enemies) {
                if (!o.alive || o in b.hit) continue
                val d = hypot(o.x - prev.x, o.y - prev.y)
                if (d < nd) {
                    nd = d
                    next = o
                }
            }
            val n = next ?: break
            b.hit.add(n)
            zaps.add(ZapFx(prev.x, prev.y - 18f - prev.alt, n.x, n.y - 18f - n.alt))
            damageEnemy(n, b.damage * 0.5f, prev.x, prev.y, 0.6f, "")
            sndAt("thunder_hit", n.x, n.y, 0.7f, vary() + 0.1f)
            n.stunT = max(n.stunT, 0.4f)
            burst(n.x, n.y - 14f - n.alt, 5, 0xFFFFF27A.toInt())
            prev = n
        }
    }

    private fun updateArcherFx(dt: Float, p: Player) {
        // armadilha de urso arremessada no meio do salto: cai atrás, onde ele saltou
        if (p.trapThrowReq) {
            p.trapThrowReq = false
            val dl = hypot(p.rollDX, p.rollDY).coerceAtLeast(0.001f)
            traps.add(Trap(p.hopX0 + p.rollDX / dl * 6f, p.hopY0 + p.rollDY / dl * 6f, p.ground, p.trapRelX, p.trapRelY, p.trapRelUp + 22f))
            if (traps.size > 4) traps.removeAt(0)
            p.trapFx = true
            snd("trap_throw", 0.9f)
        }
        // armadilhas: quem pisar leva stun de 1 segundo
        for (t in traps) {
            t.update(dt)
            if (t.landedFx) {
                t.landedFx = false
                sndAt("trap_land", t.x, t.y, 0.9f)
                burst(t.x, t.y + 13.2f - t.gz, 8, 0xFFB8B0A8.toInt())
                shake = max(shake, 0.05f)
            }
            if (!t.armed) continue
            var stepped = false
            for (e in enemies) {
                if (e.alive && abs(e.alt - t.gz) < 16f && hypot(e.x - t.x, e.y - t.y) < e.radius * 0.5f + 16f) {
                    stepped = true
                    break
                }
            }
            if (stepped) {
                t.trigger()
                sndAt("trap_snap", t.x, t.y, 1f)
                for (e in enemies) {
                    if (e.alive && hypot(e.x - t.x, e.y - t.y) < e.radius + 40f) {
                        e.stunT = max(e.stunT, 1f)
                        texts.add(DmgText(e.x, e.y - e.radius - 30f - e.alt, "STUN", 0.8f, 0xFFFFD54F.toInt()))
                    }
                }
                burst(t.x, t.y + 13.2f - t.gz, 10, 0xFF7CFF8A.toInt())
                burst(t.x, t.y + 13.2f - t.gz, 6, 0xFFFFE08A.toInt())
                shake = max(shake, 0.06f)
            }
        }
        traps.removeAll { it.done }

        // chuva de flechas
        for (z in rains) {
            z.update(dt)
            if (z.age >= RainZone.DELAY && z.spawned < RainZone.COUNT) {
                z.spawnT -= dt
                while (z.spawnT <= 0f && z.spawned < RainZone.COUNT) {
                    z.spawnT += RainZone.EVERY
                    z.spawned++
                    var tx: Float
                    var ty: Float
                    val near = enemies.filter { it.alive && hypot(it.x - z.x, it.y - z.y) < z.radius * 1.05f }
                    if (near.isNotEmpty() && Random.nextFloat() < 0.55f) {
                        val e = near[Random.nextInt(near.size)]
                        tx = e.x + (Random.nextFloat() - 0.5f) * 20f
                        ty = e.y + (Random.nextFloat() - 0.5f) * 14f
                    } else {
                        val a = Random.nextFloat() * 6.2832f
                        val r = z.radius * kotlin.math.sqrt(Random.nextFloat())
                        tx = z.x + cos(a) * r
                        ty = z.y + sin(a) * r * 0.8f
                    }
                    z.arrows.add(RainArrow(tx, ty, z.gz))
                }
            }
            for (a in z.arrows) {
                if (a.landed || a.t < RainZone.FALL) continue
                a.landed = true
                sndAt("rain_land", a.tx, a.ty, 0.55f, vary() + Random.nextFloat() * 0.2f)
                burst(a.tx, a.ty + 13.2f - a.gz, 2, 0xFFB8B0A8.toInt())
                for (e in enemies) {
                    if (e.alive && hypot(e.x - a.tx, e.y - a.ty) < 34f + e.radius * 0.5f) {
                        damageEnemy(e, p.danoH * 0.5f, z.x, z.y, 0.2f)
                    }
                }
            }
        }
        rains.removeAll { it.done }

        // flechas trovão: atravessam tudo, eletrocutam e saltam entre inimigos
        for (b in bolts) {
            if (!b.alive) continue
            b.update(dt)
            if (map.isWall(b.x, b.y)) {
                b.alive = false
                burst(b.x, b.y - 14f, 8, 0xFFFFF27A.toInt())
                continue
            }
            for (e in enemies) {
                if (e.alive && e !in b.hit && hypot(e.x - b.x, e.y - b.y) < e.radius + 12f) {
                    b.hit.add(e)
                    thunderHit(b, e)
                }
            }
            if (!b.alive) burst(b.x, b.y - 14f, 6, 0xFFFFF27A.toInt())
        }
        bolts.removeAll { !it.alive }
        zaps.forEach { it.update(dt) }
        zaps.removeAll { it.life <= 0f }
    }

    fun tryAttack() {
        val p = player ?: return
        if (!p.alive || gameOver) return
        if (p.swinging) return
        val now = System.currentTimeMillis()
        if (p.classe == Classe.GUERREIRO && p.weapon == 1 && p.spearOut) {
            spearHint(p)
            return
        }
        if (!p.canAttack(now)) {
            if ((p.classe == Classe.GUERREIRO || p.classe == Classe.BARBARO || p.classe == Classe.NINJA || p.classe == Classe.ICHIGO || p.classe == Classe.LUFFY || p.classe == Classe.EXPLORADOR) && !p.rolling) queuedAt = now
            return
        }

        if (p.classe == Classe.LUFFY) {
            luffyAttack(p, now)
            return
        }

        if (p.classe.isRanged) {
            if (p.classe == Classe.MAGO && p.mp < 12f) {
                snd("ui_error", 0.6f)
                return
            }
            if (p.classe == Classe.MAGO) p.mp -= 12f
            p.doAttack(now)
            if (p.classe == Classe.MAGO) {
                // o orbe só sai do cajado no instante do gesto (sincronizado com a animação)
                pendingCastT = p.castDelay()
                snd("spell_charge", 0.8f)
                return
            }
            if (p.classe == Classe.ARQUEIRO) {
                // a flecha só sai do arco no instante do gesto (sincronizado com Archer3D.T_SHOT)
                pendingShotT = p.shotDelay()
                pendingShotPow = 0f
                snd("bow_draw", 0.55f, 1.6f)
                return
            }
            if (p.classe == Classe.PIKACHU) {
                // o Choque do Trovão só sai das mãos no instante do gesto (sincronizado com Pikachu3D.T_SHOT)
                pendingPikaT = p.pikaDelay()
                snd("spell_charge", 0.6f, 1.7f)
                return
            }
            val speed = 280f
            val vx = p.facingX * speed
            val vy = p.facingY * speed
            val isMage = p.classe == Classe.MAGO
            val color = if (isMage) 0xFF42A5F5.toInt() else 0xFF8D6E63.toInt()
            projectiles.add(
                Projectile(
                    p.x + p.facingX * 18f, p.y + p.facingY * 18f, vx, vy,
                    p.danoH, color, true, if (isMage) 0 else 1
                )
            )
        } else {
            p.doAttack(now)
            swingSound(p)
            if (p.classe == Classe.EXPLORADOR) {
                // a corda só estala no alcance máximo (sincronizado com Explorer3D.T_HIT)
                pendingExplT = p.explorerDelay()
                pendingExplSwing = p.swing
                return
            }
            if (p.classe == Classe.ICHIGO) {
                // a lâmina (ou a garra) só acerta no instante do impacto (sincronizado com Ichigo3D.impactAt)
                pendingIchiT = p.ichiDelay()
                pendingIchiSwing = p.swing
                return
            }
            if (p.classe == Classe.NINJA) {
                // a lâmina só acerta no instante do impacto (sincronizado com Ninja3D.impactAt)
                pendingNinjaT = p.ninjaDelay()
                pendingNinjaSwing = p.swing
                return
            }
            if (p.classe == Classe.BARBARO) {
                // o machado só acerta no instante do impacto (sincronizado com a animação)
                pendingBarbT = p.barbDelay()
                pendingBarbSwing = p.swing
                return
            }
            if (p.classe == Classe.GUERREIRO && p.weapon == 1) {
                pendingHitT = p.strikeDelay()
                pendingHitSwing = p.swing
                return
            }
            // estocada (3º golpe do combo): alcança mais longe, cone estreito, dano maior
            val spear = p.classe == Classe.GUERREIRO && p.weapon == 1
            val thrust = p.classe == Classe.GUERREIRO && (if (spear) p.swing != 1 else p.swing == 2)
            val reach = p.classe.alcance * (if (spear) (if (p.swing == 0) 1.55f else if (p.swing == 1) 1.2f else 1.95f) else if (thrust) 1.3f else 1f)
            val dmg = p.danoH * (if (spear) (if (p.swing == 0) 0.95f else if (p.swing == 1) 0.85f else 1.45f) else if (thrust) 1.3f else 1f) * (if (p.classe == Classe.LUFFY && p.swing == 2) 1.5f else 1f)
            enemies.forEach { e ->
                if (!e.alive) return@forEach
                val dist = hypot(e.x - p.x, e.y - p.y)
                if (dist < reach + e.radius) {
                    val dx = e.x - p.x
                    val dy = e.y - p.y
                    val dot = dx * p.facingX + dy * p.facingY
                    val inArc = if (thrust) dot > (if (spear) 0.6f else 0.5f) * dist else (dot > 0 || dist < 40f)
                    if (inArc) damageEnemy(e, dmg, p.x, p.y)
                }
            }
            if (thrust) shake = max(shake, if (spear && p.swing == 2) 0.16f else 0.1f)
        }
    }

    /** Impacto do bárbaro: só levanta poeira (nada de rachaduras/pedras) e causa dano em área com empurrão. */
    private fun groundSmash(p: Player, radius: Float, dmgMul: Float, kb: Float, dust: Int) {
        val gy = p.y + p.radius * 0.55f - p.ground
        burst(p.x, gy, dust, 0xFFB8B0A8.toInt())
        val dmg = p.danoH * dmgMul
        enemies.forEach { e ->
            if (!e.alive) return@forEach
            if (e.alt - p.alt > 24f) return@forEach
            val dist = hypot(e.x - p.x, e.y - p.y)
            if (dist < radius + e.radius) damageEnemy(e, dmg, p.x, p.y, kb)
        }
    }

    /** Pouso do salto: levanta poeira e causa dano em área. */
    private fun leapLand(p: Player) {
        Barbarian3D.slamReq = true
        noLandSnd = true
        snd("leap_land", 1f)
        groundSmash(p, 110f, 1.8f, 2.2f, 10)
    }

    /** Movimento especial do bárbaro: carga do salto e detecção do pouso. */
    private fun barbMotion(p: Player, dt: Float) {
        if (p.leapCharging) {
            if (!p.alive) {
                p.leapCharging = false
            } else {
                chargeFxT -= dt
                if (chargeFxT <= 0f) {
                    chargeFxT = 0.09f
                    burst(p.x + (Random.nextFloat() - 0.5f) * 36f, p.y + p.radius * 0.55f - p.ground, 2, 0xFF8A7B6A.toInt())
                }
                shake = max(shake, 0.02f + 0.06f * p.leapChargeT)
            }
        }
        if (p.leaping) {
            if (!p.leapAir && p.alt > p.ground + 2f) {
                p.leapAir = true
            } else if (p.leapAir && p.grounded) {
                p.leaping = false
                p.leapAir = false
                leapLand(p)
            }
        }
    }

    /** Golpe dos machados do bárbaro; chamado no instante do impacto. O 3º golpe (batida dupla) é mais forte e mais largo. */
    private fun barbStrike(sw: Int) {
        val p = player ?: return
        val slam = sw == 2
        val reach = p.classe.alcance * (if (slam) 1.25f else 1f)
        val dmg = p.danoH * (if (slam) 1.5f else 1f)
        enemies.forEach { e ->
            if (!e.alive) return@forEach
            val dist = hypot(e.x - p.x, e.y - p.y)
            if (dist < reach + e.radius) {
                val dx = e.x - p.x
                val dy = e.y - p.y
                val dot = dx * p.facingX + dy * p.facingY
                val inArc = if (slam) (dot > -0.25f * dist || dist < 50f) else (dot > 0 || dist < 40f)
                if (inArc) damageEnemy(e, dmg, p.x, p.y, if (slam) 1.5f else 1f)
            }
        }
        shake = max(shake, if (slam) 0.16f else 0.07f)
        if (slam) snd("axe_slam", 1f, vary())
        if (slam) burst(p.x + p.facingX * 26f, p.y + p.facingY * 26f + p.radius * 0.55f - p.ground, 7, 0xFFB8B0A8.toInt())
    }

    // ───────────────────────── PIKACHU ─────────────────────────

    /** Choque do Trovão: bola elétrica que sai das mãos (leve ímã para quem está bem à frente). */
    private fun pikaShock(p: Player) {
        var dx = p.facingX
        var dy = p.facingY
        val l0 = hypot(dx, dy).coerceAtLeast(0.001f)
        dx /= l0
        dy /= l0
        var bestDot = 0.93f
        for (e in enemies) {
            if (!e.alive) continue
            val ex = e.x - p.x
            val ey = e.y - p.y
            val d = hypot(ex, ey)
            if (d < 30f || d > 420f) continue
            val dot = (ex * dx + ey * dy) / d
            if (dot > bestDot) {
                bestDot = dot
                dx = ex / d
                dy = ey / d
            }
        }
        snd("thunder_fire", 0.55f, 1.5f * vary())
        projectiles.add(
            Projectile(
                p.x + dx * 20f, p.y + dy * 20f, dx * 400f, dy * 400f,
                p.danoH, 0xFFFFE23A.toInt(), true, 0
            )
        )
        burst(p.x + dx * 20f, p.y + dy * 20f - 14f - p.alt, 4, 0xFFFFF27A.toInt())
    }

    /** Começa uma skill do Pikachu: 1 Trovoada, 2 Cauda de Ferro, 3 Investida Trovão, 4 Trovão. */
    private fun pikaSkill(p: Player, k: Int) {
        if (!p.startSkill(k, System.currentTimeMillis())) {
            snd("ui_error", 0.5f)
            return
        }
        pendingShotT = -1f
        pendingPikaT = -1f
        queuedAt = 0L
        skillDX = p.facingX
        skillDY = p.facingY
        pendingSkillK = k
        pendingSkillT = p.skillReleaseAt(k)
        snd(if (k == 4) "thunder_cast" else if (k == 2) "spell_charge" else if (k == 3) "leap_charge" else "thunder_cast", 0.9f, if (k == 3) 1.4f else 1f)
    }

    /** Instante do golpe da skill do Pikachu. */
    private fun pikaRelease(p: Player, k: Int) {
        var dx = skillDX
        var dy = skillDY
        val l0 = hypot(dx, dy).coerceAtLeast(0.001f)
        dx /= l0
        dy /= l0
        val gy = p.y + p.radius * 0.55f - p.ground
        when (k) {
            1 -> {
                // Trovoada: descarga em linha que atravessa todo mundo à frente
                var bestDot = 0.92f
                for (e in enemies) {
                    if (!e.alive) continue
                    val ex = e.x - p.x
                    val ey = e.y - p.y
                    val d = hypot(ex, ey)
                    if (d < 30f || d > 420f) continue
                    val dot = (ex * dx + ey * dy) / d
                    if (dot > bestDot) {
                        bestDot = dot
                        dx = ex / d
                        dy = ey / d
                    }
                }
                val len = rayLen(p.x, p.y, dx, dy, 340f)
                val sx = p.x + dx * 18f
                val sy = p.y + dy * 18f
                val ex0 = p.x + dx * len
                val ey0 = p.y + dy * len
                val nx = -dy
                val ny = dx
                for (i in -1..1) {
                    zaps.add(ZapFx(sx + nx * i * 9f, sy + ny * i * 9f - 18f - p.alt, ex0 + nx * i * 9f, ey0 + ny * i * 9f - 18f - p.alt))
                }
                var hits = 0
                for (e in enemies) {
                    if (!e.alive) continue
                    val rx = e.x - p.x
                    val ry = e.y - p.y
                    val along = rx * dx + ry * dy
                    val side = abs(rx * nx + ry * ny)
                    if (along > -10f && along < len + e.radius && side < 30f + e.radius * 0.6f) {
                        damageEnemy(e, p.danoH * 2.2f, p.x, p.y, 1.3f, "")
                        sndAt("thunder_hit", e.x, e.y, 0.95f, vary())
                        e.stunT = max(e.stunT, 0.6f)
                        burst(e.x, e.y - 14f - e.alt, 8, 0xFFFFF27A.toInt())
                        hits++
                    }
                }
                burst(sx, sy - 14f - p.alt, 8, 0xFFFFF27A.toInt())
                shake = max(shake, 0.2f + 0.04f * (if (hits > 3) 3 else hits))
                snd("thunder_fire", 1f)
            }
            2 -> {
                // Cauda de Ferro: martelada em arco com a cauda de aço
                val reach = 100f
                var hits = 0
                for (e in enemies) {
                    if (!e.alive) continue
                    if (e.alt - p.alt > 24f) continue
                    val ex = e.x - p.x
                    val ey = e.y - p.y
                    val dist = hypot(ex, ey)
                    if (dist < reach + e.radius) {
                        val dot = ex * dx + ey * dy
                        if (dot > -0.25f * dist || dist < 45f) {
                            damageEnemy(e, p.danoH * 2.6f, p.x, p.y, 2.2f, "hit_heavy")
                            e.stunT = max(e.stunT, 0.5f)
                            hits++
                        }
                    }
                }
                burst(p.x + dx * 40f, p.y + dy * 40f + p.radius * 0.55f - p.ground, 8, 0xFFB8C4CE.toInt())
                burst(p.x + dx * 40f, p.y + dy * 40f + p.radius * 0.55f - p.ground, 5, 0xFFB8B0A8.toInt())
                shake = max(shake, 0.22f + 0.03f * (if (hits > 3) 3 else hits))
                snd("axe_slam", 1f, 1.25f)
            }
            3 -> {
                // Investida Trovão: avança coberto de eletricidade (o dano é aplicado a cada quadro em updatePikaFx)
                p.voltDX = dx
                p.voltDY = dy
                p.facingX = dx
                p.facingY = dy
                p.voltT = Player.PIKA_VOLT_T
                voltHit.clear()
                voltWasOn = true
                burst(p.x, gy, 8, 0xFFFFE23A.toInt())
                shake = max(shake, 0.12f)
                snd("dash", 1f, 0.8f)
                snd("thunder_fire", 0.8f, 0.9f)
            }
            else -> {
                // Trovão: raios caem do céu sobre os inimigos mais próximos
                val cand = ArrayList<Enemy>()
                for (e in enemies) {
                    if (e.alive && hypot(e.x - p.x, e.y - p.y) < 380f) cand.add(e)
                }
                cand.sortBy { hypot(it.x - p.x, it.y - p.y) }
                var n = 0
                for (e in cand) {
                    if (n >= 5) break
                    n++
                    zaps.add(ZapFx(e.x - 6f, e.y - 300f, e.x, e.y - 12f - e.alt))
                    zaps.add(ZapFx(e.x + 6f, e.y - 300f, e.x, e.y - 12f - e.alt))
                    damageEnemy(e, p.danoH * 2.0f, p.x, p.y, 0.8f, "")
                    sndAt("thunder_hit", e.x, e.y, 1f, vary())
                    e.stunT = max(e.stunT, 0.8f)
                    burst(e.x, e.y - 14f - e.alt, 10, 0xFFFFF27A.toInt())
                }
                if (n == 0) {
                    // ninguém por perto: o raio cai à frente mesmo assim
                    val tx = p.x + dx * 110f
                    val ty = p.y + dy * 110f
                    zaps.add(ZapFx(tx, ty - 300f, tx, ty - 6f))
                    burst(tx, ty - 6f, 8, 0xFFFFF27A.toInt())
                }
                shake = max(shake, 0.3f)
                snd("thunder_fire", 1f, 0.85f)
            }
        }
    }

    /** Efeitos contínuos do Pikachu: Investida Trovão (acerta tudo no caminho) e Ataque Rápido. */
    private fun updatePikaFx(dt: Float, p: Player) {
        if (p.classe != Classe.PIKACHU) return
        if (p.voltT > 0f) {
            voltWasOn = true
            if (Random.nextInt(2) == 0) burst(p.x, p.y - 12f - p.alt, 2, 0xFFFFE23A.toInt())
            for (e in enemies) {
                if (!e.alive || e in voltHit) continue
                if (abs(e.alt - p.alt) > 28f) continue
                if (hypot(e.x - p.x, e.y - p.y) < e.radius + p.radius + 12f) {
                    voltHit.add(e)
                    damageEnemy(e, p.danoH * 2.4f, p.x - p.voltDX * 20f, p.y - p.voltDY * 20f, 1.6f, "hit_heavy")
                    sndAt("thunder_hit", e.x, e.y, 0.95f, vary())
                    e.stunT = max(e.stunT, 0.4f)
                    burst(e.x, e.y - 14f - e.alt, 8, 0xFFFFF27A.toInt())
                    // o recuo da investida machuca um pouco (nunca mata)
                    p.hp = max(1f, p.hp - 3f)
                    shake = max(shake, 0.18f)
                }
            }
        } else if (voltWasOn) {
            voltWasOn = false
            voltHit.clear()
        }
        if (p.rolling) {
            for (e in enemies) {
                if (!e.alive || e in quickHit) continue
                if (abs(e.alt - p.alt) > 28f) continue
                if (hypot(e.x - p.x, e.y - p.y) < e.radius + p.radius + 8f) {
                    quickHit.add(e)
                    damageEnemy(e, p.danoH * 0.8f, p.x, p.y, 1f, "hit_magic")
                    e.stunT = max(e.stunT, 0.2f)
                    burst(e.x, e.y - 14f - e.alt, 4, 0xFFFFF27A.toInt())
                }
            }
        } else if (quickHit.isNotEmpty()) {
            quickHit.clear()
        }
    }

    // ───────────────────────── ICHIGO ─────────────────────────

    private val ICHI_RED = 0xFFC8102E.toInt()
    private val ICHI_BONE = 0xFFEDE6D6.toInt()

    /** Golpe do combo; chamado no instante do impacto. O 3º golpe (cruzado) é mais largo, mais forte e solta um Getsuga pequeno. */
    // ───────────── Gomu Gomu no Gatling ─────────────
    private var gatIdx = 0
    private val gatAcc = HashMap<Enemy, Float>()
    private val gatPath = Path()

    /** Começa o Gatling: vira para o alvo (marcado ou mais perto), cancela o soco em andamento e fica plantado no chão. */
    private fun luffySkill(p: Player, k: Int) {
        if (k == 2) {
            // Gear Second: não mira em ninguém, só se prepara e bombeia o sangue
            if (!p.startSkill(2, System.currentTimeMillis())) {
                snd("ui_error", 0.5f)
                return
            }
            pendingLuffyT = -1f
            luffyHitTarget = null
            luffyAimT = null
            p.luffyTgtOn = false
            queuedAt = 0L
            g2PumpIdx = 0
            snd("spell_charge", 0.8f, 0.7f)
            return
        }
        if (k == 3) {
            // Bazooka: vira para o alvo marcado (ou o mais perto) e arma os dois braços
            val tg3 = luffyPickTarget(p)
            if (!p.startSkill(3, System.currentTimeMillis())) {
                snd("ui_error", 0.5f)
                return
            }
            if (tg3 != null) {
                val dx3 = tg3.x - p.x
                val dy3 = tg3.y - p.y
                val d3 = hypot(dx3, dy3).coerceAtLeast(1f)
                p.facingX = dx3 / d3
                p.facingY = dy3 / d3
            }
            pendingLuffyT = -1f
            luffyHitTarget = null
            luffyAimT = null
            p.luffyTgtOn = false
            queuedAt = 0L
            bazFired = false
            snd("spell_charge", 0.9f, 0.6f)
            snd("leap_charge", 0.7f, 0.8f)
            return
        }
        if (k != 1) return
        val tgt = luffyPickTarget(p)
        if (!p.startSkill(k, System.currentTimeMillis())) {
            snd("ui_error", 0.5f)
            return
        }
        if (tgt != null) {
            val dx = tgt.x - p.x
            val dy = tgt.y - p.y
            val d = hypot(dx, dy).coerceAtLeast(1f)
            p.facingX = dx / d
            p.facingY = dy / d
        }
        pendingLuffyT = -1f
        luffyHitTarget = null
        luffyAimT = null
        p.luffyTgtOn = false
        queuedAt = 0L
        gatIdx = 0
        gatAcc.clear()
        snd("leap_charge", 0.75f, 1.25f)
    }

    /** Todo quadro: marca quem está no retângulo (os punhos miram neles) e aplica os socos que já chegaram na hora de acertar. */
    private var g2PumpIdx = 0

    // Soru: rastro (x0, y0, x1, y1, idade) e impactos instantâneos do soco do Gear Second (x, y, idade, rotação)
    private val soruTr = ArrayList<FloatArray>()
    private val g2Hits = ArrayList<FloatArray>()
    private val fxPath = Path()
    private val punchTr = ArrayList<FloatArray>()   // rastro de velocidade do soco do Gear Second (x0, y0, x1, y1, idade)
    private var soruWas = false
    private var soruLX = 0f
    private var soruLY = 0f

    private fun soruPuff(x: Float, y: Float) {
        burst(x, y, 9, 0xFFFFFFFF.toInt())
        burst(x, y, 5, 0xFFD6ECFF.toInt())
        shake = max(shake, 0.06f)
    }

    /** Soru: some de tão rápido por 1 s e corre controlado pelo joystick. */
    private fun luffySoru(p: Player) {
        val now = System.currentTimeMillis()
        if (!p.soruReady(now)) {
            snd("ui_error", 0.4f)
            return
        }
        var dx = p.inX
        var dy = p.inY
        if (hypot(dx, dy) < 0.15f) {
            dx = p.facingX
            dy = p.facingY
        }
        val l = hypot(dx, dy).coerceAtLeast(0.001f)
        p.startSoru(now, dx / l, dy / l)
        pendingLuffyT = -1f
        luffyHitTarget = null
        luffyAimT = null
        p.luffyTgtOn = false
        queuedAt = 0L
        soruTr.clear()
        soruLX = p.x
        soruLY = p.y - p.alt - 14f
        soruPuff(p.x, p.y - p.alt - 14f)
        snd("dash", 0.9f, 1.9f)
        snd("teleport_in", 0.55f, 1.7f)
    }

    /** Todo quadro: rastro do Soru (fitas que somem), fim do Soru e envelhecimento dos impactos. */
    private fun luffySoruUpdate(p: Player, dt: Float) {
        val on = p.soruT > 0f && p.alive
        val cy = p.y - p.alt - 14f
        if (on) {
            if (hypot(p.x - soruLX, cy - soruLY) > 0.5f) soruTr.add(floatArrayOf(soruLX, soruLY, p.x, cy, 0f))
            if (Random.nextFloat() < dt * 30f) burst(p.x, cy, 1, 0xFFFFFFFF.toInt())
            shake = max(shake, 0.02f)
        }
        if (soruWas && !on) {
            soruPuff(p.x, cy)
            snd("teleport_in", 0.7f, 1.3f)
        }
        soruWas = on
        soruLX = p.x
        soruLY = cy
        val it1 = soruTr.iterator()
        while (it1.hasNext()) {
            val sg = it1.next()
            sg[4] += dt
            if (sg[4] > 0.45f) it1.remove()
        }
        val it3 = punchTr.iterator()
        while (it3.hasNext()) {
            val sg = it3.next()
            sg[4] += dt
            if (sg[4] > 0.32f) it3.remove()
        }
        val it2 = g2Hits.iterator()
        while (it2.hasNext()) {
            val h = it2.next()
            h[2] += dt
            if (h[2] > 0.22f) it2.remove()
        }
    }

    // Bazooka: origem, direção e idade do golpe desde o estalo
    private var bazFired = false
    private var bazAge = -1f
    private var bazOX = 0f
    private var bazOY = 0f
    private var bazDX = 1f
    private var bazDY = 0f
    private var bazBig = false
    private var bazWindQ = -1f

    /** Todo quadro: faíscas da preparação, o golpe no instante do estalo (área à frente) e envelhecimento do efeito. */
    private fun luffyBazUpdate(p: Player, dt: Float) {
        if (bazAge >= 0f) {
            bazAge += dt
            if (bazAge > 0.6f) bazAge = -1f
        }
        if (p.skillK != 3 || !p.alive) {
            bazFired = false
            bazWindQ = -1f
            return
        }
        val el = p.skillDur - p.skillT
        val cy = p.y - p.alt - 20f
        if (el < Luffy3D.BAZ_HIT) {
            val q = (el / Luffy3D.BAZ_HIT).coerceIn(0f, 1f)
            bazWindQ = q
            if (Random.nextFloat() < dt * (16f + 50f * q)) {
                val ang = Random.nextFloat() * 6.2832f
                burst(p.x - p.facingX * 22f + cos(ang) * 20f, cy - p.facingY * 12f + sin(ang) * 14f, 1, if (Random.nextBoolean()) 0xFFFFFFFF.toInt() else 0xFFFFC23A.toInt())
            }
            shake = max(shake, 0.01f + 0.03f * q)
        } else if (!bazFired) {
            bazFired = true
            bazWindQ = -1f
            val big = p.g2T > 0f
            bazBig = big
            bazDX = p.facingX
            bazDY = p.facingY
            bazOX = p.x + p.facingX * 20f
            bazOY = cy + p.facingY * 10f
            bazAge = 0f
            val mul = if (big) 1.6f else 1f
            for (e in enemies) {
                if (!e.alive) continue
                if (e.alt - p.alt > 30f) continue
                val ex = e.x - p.x
                val ey = e.y - p.y
                val along = ex * p.facingX + ey * p.facingY
                val side = ex * p.facingY - ey * p.facingX
                if (along > -10f && along < Player.LUFFY_BAZ_LEN + e.radius && abs(side) < Player.LUFFY_BAZ_HALFW + e.radius * 0.6f) {
                    damageEnemy(e, p.danoH * Player.LUFFY_BAZ_DMG * mul, p.x, p.y, 3.4f)
                    e.stunT = max(e.stunT, 1.0f)
                    val hy = e.y + e.radius * 0.55f - e.alt - 26f
                    burst(e.x, hy, 10, 0xFFFFFFFF.toInt())
                    burst(e.x, hy, 8, 0xFFFFC23A.toInt())
                    g2Hits.add(floatArrayOf(e.x, hy, 0f, Random.nextFloat() * 1.0472f))
                }
            }
            burst(bazOX, bazOY, 18, 0xFFFFFFFF.toInt())
            burst(bazOX, bazOY, 14, 0xFFFFC23A.toInt())
            // poeira e faíscas estourando ao longo de toda a área
            for (i in 0..9) {
                val dd = 30f + i * 22f
                burst(bazOX + bazDX * dd, bazOY + bazDY * dd + 14f, 2, 0xFFDDD0B0.toInt())
                if (i % 2 == 0) burst(bazOX + bazDX * dd, bazOY + bazDY * dd, 2, 0xFFFFC23A.toInt())
            }
            g2Hits.add(floatArrayOf(bazOX, bazOY, 0f, 0f))
            shake = max(shake, 0.5f)
            snd("hit_heavy", 1f, 0.75f)
            snd("thunder_cast", 0.8f, 1.35f)
            texts.add(DmgText(p.x, p.y - 92f - p.alt, if (big) "JET BAZOOKA!" else "BAZOOKA!", 1.3f, 0xFFFFC23A.toInt()))
        }
    }

    /** Efeito do Bazooka: cone de choque, riscos de velocidade e anéis que correm à frente. */
    private fun drawBazFx(canvas: Canvas) {
        val pl = player
        if (bazWindQ >= 0f && pl != null) {
            val w = bazWindQ
            val fxv = pl.facingX
            val fyv = pl.facingY
            val nxv = -fyv
            val nyv = fxv
            val L = Player.LUFFY_BAZ_LEN
            val H = Player.LUFFY_BAZ_HALFW
            val bx = pl.x - camX
            val by = pl.y - camY + pl.radius * 0.55f - pl.alt
            // zona do golpe marcada no chão, enchendo de dourado conforme ele arma
            fxPath.reset()
            fxPath.moveTo(bx + nxv * 14f, by + nyv * 14f)
            fxPath.lineTo(bx + fxv * L + nxv * H, by + fyv * L + nyv * H)
            fxPath.lineTo(bx + fxv * L - nxv * H, by + fyv * L - nyv * H)
            fxPath.lineTo(bx - nxv * 14f, by - nyv * 14f)
            fxPath.close()
            paint.style = Paint.Style.FILL
            paint.color = (((0.05f + 0.16f * w) * 255f).toInt() shl 24) or 0xFFC23A
            canvas.drawPath(fxPath, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f
            paint.color = (((0.25f + 0.55f * w) * 255f).toInt() shl 24) or 0xFFE08A
            canvas.drawPath(fxPath, paint)
            // anéis que se fecham em volta dele e aura que cresce
            for (i in 0..1) {
                val fr = (time * 2.4f + i * 0.5f) % 1f
                val rr = 16f + 54f * (1f - fr)
                paint.strokeWidth = 2.5f
                paint.color = (((0.6f * w * fr * 255f).toInt()) shl 24) or 0xFFC23A
                canvas.drawOval(bx - rr, by - rr * 0.62f, bx + rr, by + rr * 0.62f, paint)
            }
            paint.style = Paint.Style.FILL
            paint.color = (((0.14f * w * 255f).toInt()) shl 24) or 0xFFC23A
            canvas.drawCircle(bx, by - 22f, 22f + 18f * w, paint)
        }
        if (bazAge < 0f) return
        val q = (bazAge / 0.6f).coerceIn(0f, 1f)
        val a = 1f - q
        val dx = bazDX
        val dy = bazDY
        val nx = -dy
        val ny = dx
        val ox = bazOX - camX
        val oy = bazOY - camY
        val len = Player.LUFFY_BAZ_LEN * (if (bazBig) 1.1f else 1f)
        val reach = len * (0.3f + 0.7f * kotlin.math.min(1f, q * 3.5f))
        paint.style = Paint.Style.FILL
        paint.color = (((0.55f * a * 255f).toInt()) shl 24) or 0xFFC23A
        canvas.drawCircle(ox, oy, 14f + 34f * q, paint)
        paint.color = (((0.8f * a * 255f).toInt()) shl 24) or 0xFFFFFF
        canvas.drawCircle(ox, oy, 8f + 16f * q, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        // cone: raios saindo das palmas
        for (i in -3..3) {
            val ang = i * 0.1f
            val ca = cos(ang)
            val sa = sin(ang)
            val rx = dx * ca - dy * sa
            val ry = dx * sa + dy * ca
            paint.strokeWidth = 3f + 9f * a * (1f - kotlin.math.abs(i) * 0.2f)
            paint.color = (((0.5f * a * 255f).toInt()) shl 24) or 0xFFFFFF
            canvas.drawLine(ox + rx * 16f, oy + ry * 16f, ox + rx * reach, oy + ry * reach, paint)
            paint.strokeWidth = 1.5f + 2f * a
            paint.color = (((0.9f * a * 255f).toInt()) shl 24) or 0xFFC23A
            canvas.drawLine(ox + rx * 24f, oy + ry * 24f, ox + rx * reach * 0.85f, oy + ry * reach * 0.85f, paint)
        }
        // riscos de velocidade paralelos
        for (k in 0..7) {
            val off = (k - 3.5f) * 9f
            val s0 = reach * 0.08f + (k % 3) * 12f
            val s1 = reach * (0.65f + 0.35f * ((k * 37) % 10) / 10f)
            paint.strokeWidth = 1.6f + 1.4f * a
            paint.color = (((0.85f * a * 255f).toInt()) shl 24) or 0xFFFFFF
            canvas.drawLine(ox + dx * s0 + nx * off, oy + dy * s0 + ny * off, ox + dx * s1 + nx * off, oy + dy * s1 + ny * off, paint)
        }
        // anéis de choque correndo para a frente
        for (r in 0..1) {
            val d0 = 30f + len * 0.75f * q + r * 22f
            val rad = 16f + 32f * q + r * 9f
            val cx2 = ox + dx * d0
            val cy2 = oy + dy * d0
            paint.strokeWidth = 1.5f + (6f - 3f * r) * a
            paint.color = (((0.9f * a * 255f).toInt()) shl 24) or (if (r == 0) 0xFFFFFF else 0xFFC23A)
            canvas.drawOval(cx2 - rad * 0.75f, cy2 - rad, cx2 + rad * 0.75f, cy2 + rad, paint)
        }
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
    }

    private fun drawSoruFx(canvas: Canvas, p: Player) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        // Soru: só linhas finas de velocidade, brancas, paralelas e que somem rápido (nada de fita grossa colorida)
        val offs = floatArrayOf(-13f, -7f, -2f, 4f, 9f, 14f)
        val lens = floatArrayOf(1f, 0.7f, 1f, 0.6f, 0.9f, 0.75f)
        for (sg in soruTr) {
            val a = (1f - sg[4] / 0.45f).coerceIn(0f, 1f)
            val ddx = sg[2] - sg[0]
            val ddy = sg[3] - sg[1]
            val dl = hypot(ddx, ddy)
            if (dl < 0.01f) continue
            val nx = -ddy / dl
            val ny = ddx / dl
            for (k in offs.indices) {
                paint.strokeWidth = 1f + 1.6f * a * (if (k % 2 == 0) 1f else 0.6f)
                paint.color = (((0.75f * a * lens[k] * 255f).toInt()) shl 24) or 0xFFFFFF
                canvas.drawLine(sg[0] + nx * offs[k] - camX, sg[1] + ny * offs[k] - camY, sg[2] + nx * offs[k] - camX, sg[3] + ny * offs[k] - camY, paint)
            }
        }
        // soco do Gear Second: riscos que DISPARAM do punho até o alvo (cabeça rápida + cauda afilada), punhos-fantasma
        // ao longo do caminho e, na chegada, anéis e espículas de impacto que se abrem
        paint.style = Paint.Style.FILL
        for (sg in punchTr) {
            val age = sg[4]
            val ddx = sg[2] - sg[0]
            val ddy = sg[3] - sg[1]
            val dl = hypot(ddx, ddy)
            if (dl < 4f) continue
            val ux = ddx / dl
            val uy = ddy / dl
            val nx = -uy
            val ny = ux
            val fade = (1f - age / 0.3f).coerceIn(0f, 1f)
            // 1) riscos: cada um com atraso e velocidade próprios; convergem para o ponto de impacto
            for (k in 0..6) {
                val o0 = (k - 3) * 6.5f
                val dly = 0.012f * ((k * 5) % 7)
                val tt = ((age - dly) / (0.075f + 0.012f * (k % 3))).coerceIn(0f, 1f)
                if (tt <= 0f) continue
                val head = 1f - (1f - tt) * (1f - tt) * (1f - tt)
                val tail = ((age - dly - 0.035f) / 0.2f).coerceIn(0f, 1f)
                val tl = tail * tail * head
                val hx0 = sg[0] + ux * dl * head
                val hy0 = sg[1] + uy * dl * head
                val tx0 = sg[0] + ux * dl * tl
                val ty0 = sg[1] + uy * dl * tl
                val ho = o0 * (1f - 0.75f * head)
                val to = o0 * (1f - 0.75f * tl)
                val w = (if (k == 3) 4.2f else 2.4f - 0.2f * kotlin.math.abs(k - 3)) * (0.35f + 0.65f * fade)
                fxPath.reset()
                fxPath.moveTo(tx0 + nx * to - camX, ty0 + ny * to - camY)
                fxPath.lineTo(hx0 + nx * (ho + w) - camX, hy0 + ny * (ho + w) - camY)
                fxPath.lineTo(hx0 + ux * 5f + nx * ho - camX, hy0 + uy * 5f + ny * ho - camY)
                fxPath.lineTo(hx0 + nx * (ho - w) - camX, hy0 + ny * (ho - w) - camY)
                fxPath.close()
                paint.color = (((0.28f * (0.4f + 0.6f * fade) * 255f).toInt()) shl 24) or 0x9AD8FF
                canvas.drawPath(fxPath, paint)
                // miolo branco mais fino, só no trecho final da cauda
                val mx = tx0 + (hx0 - tx0) * 0.45f
                val my = ty0 + (hy0 - ty0) * 0.45f
                val mo = to + (ho - to) * 0.45f
                fxPath.reset()
                fxPath.moveTo(mx + nx * mo - camX, my + ny * mo - camY)
                fxPath.lineTo(hx0 + nx * (ho + w * 0.45f) - camX, hy0 + ny * (ho + w * 0.45f) - camY)
                fxPath.lineTo(hx0 + ux * 5f + nx * ho - camX, hy0 + uy * 5f + ny * ho - camY)
                fxPath.lineTo(hx0 + nx * (ho - w * 0.45f) - camX, hy0 + ny * (ho - w * 0.45f) - camY)
                fxPath.close()
                paint.color = (((0.95f * fade * 255f).toInt()) shl 24) or 0xFFFFFF
                canvas.drawPath(fxPath, paint)
            }
            // 2) punhos-fantasma espalhados pelo caminho (efeito de vários socos de uma vez)
            for (g in 0..3) {
                val fr = 0.22f + 0.24f * g
                val appear = 0.05f * g
                if (age < appear) continue
                val ga = (1f - (age - appear) / 0.17f).coerceIn(0f, 1f)
                if (ga <= 0f) continue
                val gx = sg[0] + ux * dl * fr + nx * (g - 1.5f) * 4f - camX
                val gy = sg[1] + uy * dl * fr + ny * (g - 1.5f) * 4f - camY
                paint.color = (((0.38f * ga * 255f).toInt()) shl 24) or 0xFFFFFF
                canvas.drawCircle(gx, gy, 5f + 2.5f * g, paint)
                paint.color = (((0.5f * ga * 255f).toInt()) shl 24) or 0xFFD36A
                canvas.drawCircle(gx - ux * 5f, gy - uy * 5f, 3f + 1.5f * g, paint)
            }
            // 3) chegada: anéis que se abrem + espículas que voam para fora + riscos que seguem na direção do soco
            if (age > 0.06f) {
                val q = ((age - 0.06f) / 0.22f).coerceIn(0f, 1f)
                val ia = 1f - q
                val cx = sg[2] - camX
                val cy = sg[3] - camY
                paint.style = Paint.Style.FILL
                paint.color = (((0.5f * ia * ia * 255f).toInt()) shl 24) or 0xFFFFFF
                canvas.drawCircle(cx, cy, 6f + 16f * q, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                for (r in 0..1) {
                    val rad = 6f + (30f + 14f * r) * q
                    paint.strokeWidth = (1f + (4.5f - 2f * r) * ia)
                    paint.color = (((0.95f * ia * 255f).toInt()) shl 24) or (if (r == 0) 0xFFFFFF else 0xFFC23A)
                    canvas.drawOval(cx - rad, cy - rad * 0.7f, cx + rad, cy + rad * 0.7f, paint)
                }
                for (k in 0..9) {
                    val ang = k * 0.6283f + sg[5] * 6.28f
                    val ca = cos(ang)
                    val sa = sin(ang)
                    val r0 = 8f + 30f * q
                    val r1 = r0 + 6f + 18f * ia
                    paint.strokeWidth = 1f + 2.6f * ia
                    paint.color = (((0.95f * ia * 255f).toInt()) shl 24) or (if (k % 2 == 0) 0xFFFFFF else 0xFFD36A)
                    canvas.drawLine(cx + ca * r0, cy + sa * r0 * 0.8f, cx + ca * r1, cy + sa * r1 * 0.8f, paint)
                }
                for (k in -1..1) {
                    val st = 10f + 60f * q
                    paint.strokeWidth = 1.5f + 1.5f * ia
                    paint.color = (((0.8f * ia * 255f).toInt()) shl 24) or 0xFFFFFF
                    canvas.drawLine(cx + ux * st + nx * k * 7f, cy + uy * st + ny * k * 7f, cx + ux * (st + 22f * ia) + nx * k * 7f, cy + uy * (st + 22f * ia) + ny * k * 7f, paint)
                }
                paint.style = Paint.Style.FILL
            }
        }
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeCap = Paint.Cap.BUTT
        // impactos instantâneos do soco do Gear Second: clarão + estrela no inimigo
        for (h in g2Hits) {
            val q = (h[2] / 0.22f).coerceIn(0f, 1f)
            val cx = h[0] - camX
            val cyy = h[1] - camY
            paint.style = Paint.Style.FILL
            paint.color = (((0.8f * (1f - q) * 255f).toInt()) shl 24) or 0xFFFFFF
            canvas.drawCircle(cx, cyy, 5f + 13f * q, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = 2.5f
            paint.color = (((0.95f * (1f - q) * 255f).toInt()) shl 24) or 0xFFE08A
            for (k in 0..5) {
                val ang = k * 1.0472f + h[3]
                canvas.drawLine(cx + cos(ang) * (8f + 8f * q), cyy + sin(ang) * (8f + 8f * q), cx + cos(ang) * (16f + 16f * q), cyy + sin(ang) * (16f + 16f * q), paint)
            }
            paint.strokeCap = Paint.Cap.BUTT
        }
        // enquanto invisível, um anel rosa no chão mostra onde ele está
        if (p.soruT > 0f && p.alive) {
            val sx = p.x - camX
            val feet = p.y - camY + p.radius * 0.55f - p.alt
            paint.strokeWidth = 2.5f
            paint.color = 0x66FFFFFF
            canvas.drawOval(sx - 17f, feet - 11f, sx + 17f, feet + 11f, paint)
        }
        paint.style = Paint.Style.FILL
    }
    private var g2Was = false

    private fun luffyG2Update(p: Player) {
        // cada bombeada da perna faz um "puf"
        if (p.skillK == 2 && p.alive) {
            val tg = (1f - p.skillT / p.skillDur) * Luffy3D.G2_DUR
            val idx = Luffy3D.g2Cyc(kotlin.math.min(tg, Luffy3D.G2_PUMP)).toInt()
            if (idx > g2PumpIdx) {
                g2PumpIdx = idx
                snd("dash", 0.4f, 0.7f + 0.06f * idx)
                shake = max(shake, 0.015f + 0.004f * idx)
            }
        }
        val on = p.g2T > 0f
        if (on && !g2Was) {
            snd("thunder_cast", 0.9f, 0.75f)
            snd("hit_heavy", 0.6f, 0.6f)
            shake = max(shake, 0.12f)
            texts.add(DmgText(p.x, p.y - 84f - p.alt, "GEAR SECOND!", 1.3f, 0xFFFF7C86.toInt()))
        }
        g2Was = on
    }

    private fun luffyGatUpdate(p: Player) {
        luffyG2Update(p)
        if (p.skillK != 1 || !p.alive) {
            p.gatPtN = 0
            if (p.skillK == 0) gatIdx = 0
            return
        }
        val fx = p.facingX
        val fy = p.facingY
        var n = 0
        for (e in enemies) {
            if (!e.alive || n >= 6) continue
            if (e.alt - p.alt > 30f) continue
            val ex = e.x - p.x
            val ey = e.y - p.y
            val along = ex * fx + ey * fy
            val side = ex * fy - ey * fx      // + = esquerda do Luffy
            if (along > -10f && along < Player.LUFFY_GAT_LEN + e.radius && abs(side) < Player.LUFFY_GAT_HALFW + e.radius * 0.6f) {
                p.gatPtF[n] = (along - e.radius * 0.5f).coerceIn(30f, Player.LUFFY_GAT_LEN)
                p.gatPtU[n] = side.coerceIn(-Player.LUFFY_GAT_HALFW * 0.92f, Player.LUFFY_GAT_HALFW * 0.92f)
                n++
            }
        }
        p.gatPtN = n
        val el = p.skillDur - p.skillT
        while (gatIdx < Luffy3D.GAT_N && el >= Luffy3D.gatHit(gatIdx)) {
            gatTick(p, gatIdx)
            gatIdx++
        }
    }

    /** Um soco do Gatling: acerta todos que estão dentro do retângulo à frente. O último empurra para longe. */
    private fun gatTick(p: Player, idx: Int) {
        val last = idx == Luffy3D.GAT_N - 1
        val fx = p.facingX
        val fy = p.facingY
        for (e in enemies) {
            if (!e.alive) continue
            if (e.alt - p.alt > 30f) continue
            val ex = e.x - p.x
            val ey = e.y - p.y
            val along = ex * fx + ey * fy
            val side = abs(ex * fy - ey * fx)
            if (along > -10f && along < Player.LUFFY_GAT_LEN + e.radius && side < Player.LUFFY_GAT_HALFW + e.radius * 0.6f) {
                val hp0 = e.hp
                damageEnemy(e, p.danoH * Player.LUFFY_GAT_DMG, p.x, p.y, if (last) 2.2f else 0.04f, "", true)
                gatAcc[e] = (gatAcc[e] ?: 0f) + (hp0 - e.hp)
                e.stunT = max(e.stunT, 0.35f)
                if (idx % 3 == 0) sndAt("hit_heavy", e.x, e.y, 0.5f, vary() * 1.1f)
            }
        }
        if (idx % 2 == 0 || last) snd("heavy_swing", 0.3f, (1.5f + 0.4f * ((idx / 2) % 2)) * vary())
        shake = max(shake, if (last) 0.14f else 0.02f)
        if (idx % 10 == 9 || last) {
            for ((e, v) in gatAcc) {
                if (v > 0.5f) texts.add(DmgText(e.x, e.y - e.radius - 30f - e.alt, v.toInt().toString(), 0.9f, 0xFFFFD54F.toInt()))
            }
            gatAcc.clear()
        }
    }

    private fun sm01(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    /** O retângulo do Gatling no chão: aparece na preparação, pulsa na barragem e some junto com a volta dos braços. */
    private fun drawGatArea(canvas: Canvas, p: Player) {
        if (!p.alive || p.skillK != 1) return
        val el = p.skillDur - p.skillT
        val tb = Luffy3D.gatHit(Luffy3D.GAT_N - 1)
        val a = (sm01(el / 0.3f) * (1f - sm01((el - tb) / (Luffy3D.GAT_DUR - tb)))).coerceIn(0f, 1f)
        if (a <= 0.01f) return
        val barrage = el >= Luffy3D.GAT_T0 - 0.1f && el <= tb + 0.1f
        val pulse = if (barrage) 0.8f + 0.2f * sin(time * 20f) else 0.6f
        val fx = p.facingX
        val fy = p.facingY
        val lx = fy
        val ly = -fx
        val cx = p.x - camX
        val cy = p.y - camY + p.radius * 0.55f - p.ground
        val len = Player.LUFFY_GAT_LEN
        val w = Player.LUFFY_GAT_HALFW
        val b = -12f
        gatPath.reset()
        gatPath.moveTo(cx + fx * b - lx * w, cy + fy * b - ly * w)
        gatPath.lineTo(cx + fx * len - lx * w, cy + fy * len - ly * w)
        gatPath.lineTo(cx + fx * len + lx * w, cy + fy * len + ly * w)
        gatPath.lineTo(cx + fx * b + lx * w, cy + fy * b + ly * w)
        gatPath.close()
        paint.style = Paint.Style.FILL
        paint.color = ((0x34 * a * pulse).toInt() shl 24) or 0xFFC23A
        canvas.drawPath(gatPath, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = ((0xE0 * a).toInt() shl 24) or 0xFF5252
        canvas.drawPath(gatPath, paint)
        // faixas que correm para a frente (sensação de velocidade)
        paint.strokeWidth = 2f
        for (i in 0..7) {
            val f = ((time * (if (barrage) 720f else 120f) + i * len / 8f) % len)
            val fade = kotlin.math.sin(3.14159f * f / len)
            paint.color = (((0x66 * a * fade).toInt()) shl 24) or 0xFFFFFF
            canvas.drawLine(cx + fx * f - lx * w * 0.9f, cy + fy * f - ly * w * 0.9f, cx + fx * f + lx * w * 0.9f, cy + fy * f + ly * w * 0.9f, paint)
        }
        paint.style = Paint.Style.FILL
    }

    /** Gomu Gomu no Pistol: o punho esticado chega ao alvo marcado e sempre acerta (o alvo pode ter se mexido). */
    private fun luffyStrike(@Suppress("UNUSED_PARAMETER") sw: Int) {
        val p = player ?: return
        val e = luffyHitTarget ?: return
        luffyHitTarget = null
        if (!e.alive) return
        damageEnemy(e, p.danoH, p.x, p.y, 1.2f)
        val hy = e.y + e.radius * 0.55f - e.alt - 26f
        burst(e.x, hy, 6, 0xFFFFE08A.toInt())
        burst(e.x, hy, 3, 0xFFFFFFFF.toInt())
        shake = max(shake, 0.07f)
        if (p.g2T > 0f) {
            // rastro de velocidade do punho até o inimigo
            punchTr.add(floatArrayOf(p.x + p.facingX * 14f, p.y - p.alt - 20f + p.facingY * 6f, e.x, hy, 0f, Random.nextFloat()))
            // Gear Second: o braço mal se mexe, mas o impacto estoura direto no inimigo (vários clarões espalhados)
            for (i in 0..2) {
                val ox = (Random.nextFloat() - 0.5f) * e.radius * 1.1f
                val oy = (Random.nextFloat() - 0.5f) * e.radius * 0.8f
                g2Hits.add(floatArrayOf(e.x + ox, hy + oy, -0.04f * i, Random.nextFloat() * 1.0472f))
                burst(e.x + ox, hy + oy, 3, if (i % 2 == 0) 0xFFFFFFFF.toInt() else 0xFFFFC23A.toInt())
            }
        }
    }

    /** Chicote: a corda estala numa faixa reta à frente; a chicotada por cima (3º golpe) vai mais longe, abre mais e bate mais forte. */
    private fun explorerStrike(sw: Int) {
        val p = player ?: return
        val big = sw == 2
        val reach = if (big) Player.EXPL_REACH_3 else Player.EXPL_REACH
        val half = if (big) 40f else 22f
        val dmg = p.danoH * (if (big) 1.45f else 1f)
        var hits = 0
        enemies.forEach { e ->
            if (!e.alive) return@forEach
            if (e.alt - p.alt > 30f) return@forEach
            val dx = e.x - p.x
            val dy = e.y - p.y
            val along = dx * p.facingX + dy * p.facingY
            val side = abs(dx * p.facingY - dy * p.facingX)
            if (along > -10f && along < reach + e.radius && side < half + e.radius) {
                damageEnemy(e, dmg, p.x, p.y, if (big) 1.6f else 1.0f)
                burst(e.x, e.y + e.radius * 0.55f - e.alt - 14f, 4, 0xFFFFE08A.toInt())
                hits++
            }
        }
        snd("spear_thrust", 0.8f, 1.9f * vary())
        if (big) burst(p.x + p.facingX * reach * 0.9f, p.y + p.facingY * reach * 0.9f + p.radius * 0.55f - p.ground, 4, 0xFFE9D9B0.toInt())
        shake = max(shake, if (big) 0.1f else if (hits > 0) 0.04f else 0.015f)
    }

    private fun ichiStrike(sw: Int) {
        val p = player ?: return
        val cross = sw == 2
        val hol = p.hollow
        val reach = p.classe.alcance * (if (cross) 1.35f else 1f) * (if (hol) 1.15f else 1f)
        val dmg = p.danoH * (if (cross) 1.4f else 0.85f)
        var hits = 0
        enemies.forEach { e ->
            if (!e.alive) return@forEach
            if (e.alt - p.alt > 30f) return@forEach
            val dist = hypot(e.x - p.x, e.y - p.y)
            if (dist < reach + e.radius) {
                val dx = e.x - p.x
                val dy = e.y - p.y
                val dot = dx * p.facingX + dy * p.facingY
                val inArc = if (cross) (dot > -0.2f * dist || dist < 50f) else (dot > 0 || dist < 40f)
                if (inArc) {
                    damageEnemy(e, dmg, p.x, p.y, if (cross) 1.5f else 0.9f, if (hol) "hit_heavy" else null)
                    hits++
                }
            }
        }
        val col = if (hol) ICHI_RED else 0xFFFF3B52.toInt()
        val fy0 = p.y + p.facingY * 30f + p.radius * 0.55f - p.alt - 14f
        ichiFxs.add(IchiFx(4, p.x + p.facingX * 30f, fy0, p.facingX, p.facingY, 0.22f, if (cross) 58f else 42f, col))
        if (cross && !hol) {
            getsugas.add(GetsugaWave(p.x + p.facingX * 26f, p.y + p.facingY * 26f, p.facingX, p.facingY, p.danoH * 1.0f, 18f + p.alt))
        }
        shake = max(shake, if (cross) 0.12f else 0.04f)
        burst(p.x + p.facingX * 30f, fy0, if (hits > 0) 5 else 2, if (hits > 0) col else 0xFFE0E6EC.toInt())
    }

    /** Começa uma skill. Bankai: 1 Getsuga Tenshou, 2 Getsuga Jujisho, 3 Redemoinho, 4 Golpe Lunar, 5 Vasto Lorde.
     *  Vasto Lorde: 1 Cero, 2 Garras Rasgantes, 3 Grito Hollow, 4 Cero Oscuras, 5 Mugetsu. */
    private fun ichiSkill(p: Player, k: Int) {
        if (!p.startSkill(k, System.currentTimeMillis())) {
            snd("ui_error", 0.5f)
            return
        }
        pendingShotT = -1f
        pendingIchiT = -1f
        pendingLuffyT = -1f
        pendingExplT = -1f
        queuedAt = 0L
        skillDX = p.facingX
        skillDY = p.facingY
        pendingSkillK = k
        pendingSkillT = p.skillReleaseAt(k)
        if (p.ichiSkH) {
            when (k) {
                1 -> snd("spell_charge", 0.9f, 0.55f)
                2 -> snd("dash", 0.8f, 0.7f)
                3 -> snd("leap_charge", 0.9f, 0.6f)
                4 -> snd("spell_charge", 1f, 0.4f)
                else -> snd("thunder_cast", 1f, 0.5f)
            }
        } else {
            when (k) {
                1 -> snd("spell_charge", 0.8f, 0.8f)
                2 -> snd("spell_charge", 0.9f, 0.65f)
                3 -> snd("leap_charge", 0.9f, 1.1f)
                4 -> snd("leap_charge", 1f, 0.8f)
                else -> snd("thunder_cast", 1f, 0.6f)
            }
        }
    }

    /** Corte em linha com o centro em (cx, cy), inclinado [ang] graus, de comprimento [len]. */
    private fun ichiLine(cx: Float, cy: Float, ang: Float, len: Float, w: Float, col: Int, delay: Float) {
        val a = ang * 0.0174533f
        val hx = cos(a) * len * 0.5f
        val hy = sin(a) * len * 0.5f * 0.8f
        ichiFxs.add(IchiFx(6, cx - hx, cy - hy, cx + hx, cy + hy, 0.38f, w, col, delay))
    }

    /** Faísca de impacto sobre um inimigo atingido. */
    private fun ichiSpark(e: Enemy, col: Int, size: Float) {
        ichiFxs.add(IchiFx(7, e.x, e.y - 14f - e.alt, 0f, 0f, 0.26f, size, col))
    }

    /** Cero (big = false) e Cero Oscuras (big = true): feixe que atravessa tudo à frente, mirando de leve no inimigo mais alinhado. */
    private fun ichiBeam(p: Player, dx0: Float, dy0: Float, big: Boolean) {
        var dx = dx0
        var dy = dy0
        val chest = p.y + p.radius * 0.55f - p.alt - 20f
        var bestDot = if (big) 0.9f else 0.93f
        val maxD = if (big) 500f else 420f
        for (e in enemies) {
            if (!e.alive) continue
            val ex = e.x - p.x
            val ey = e.y - p.y
            val d = hypot(ex, ey)
            if (d < 30f || d > maxD) continue
            val dot = (ex * dx + ey * dy) / d
            if (dot > bestDot) {
                bestDot = dot
                dx = ex / d
                dy = ey / d
            }
        }
        val range = if (big) 520f else 400f
        val len = rayLen(p.x, p.y, dx, dy, range)
        val nx = -dy
        val ny = dx
        val hy = chest - 4f
        val half = if (big) 54f else 34f
        ichiFxs.add(IchiFx(if (big) 8 else 0, p.x + dx * 22f, hy + dy * 22f, p.x + dx * len, hy + dy * len, if (big) 0.8f else 0.55f, if (big) 40f else 26f, ICHI_RED))
        var hits = 0
        for (e in enemies) {
            if (!e.alive) continue
            val rx = e.x - p.x
            val ry = e.y - p.y
            val along = rx * dx + ry * dy
            val side = abs(rx * nx + ry * ny)
            if (along > -10f && along < len + e.radius && side < half + e.radius * 0.6f) {
                damageEnemy(e, p.danoH * (if (big) 5.2f else 3.2f), p.x, p.y, if (big) 2.0f else 1.6f, "hit_heavy")
                e.stunT = max(e.stunT, if (big) 0.9f else 0.5f)
                ichiSpark(e, ICHI_RED, if (big) 46f else 34f)
                burst(e.x, e.y - 14f - e.alt, 8, ICHI_RED)
                hits++
            }
        }
        p.facingX = dx
        p.facingY = dy
        burst(p.x + dx * 24f, hy + dy * 24f, 10, ICHI_RED)
        if (big) {
            val gy = p.y + p.radius * 0.55f - p.ground
            ichiFxs.add(IchiFx(1, p.x, gy, p.x, gy, 0.6f, 120f, ICHI_RED))
            burst(p.x + dx * 30f, hy + dy * 30f, 12, 0xFF1A0610.toInt())
            darkFlash = max(darkFlash, 0.5f)
        }
        shake = max(shake, (if (big) 0.45f else 0.3f) + 0.03f * (if (hits > 3) 3 else hits))
        snd("thunder_fire", 1f, if (big) 0.45f else 0.6f)
        snd("heavy_swing", 0.8f, if (big) 0.5f else 0.6f)
    }

    /** Instante do golpe da skill do Ichigo. */
    private fun ichiRelease(p: Player, k: Int) {
        var dx = skillDX
        var dy = skillDY
        val l0 = hypot(dx, dy).coerceAtLeast(0.001f)
        dx /= l0
        dy /= l0
        val gy = p.y + p.radius * 0.55f - p.ground
        val chest = p.y + p.radius * 0.55f - p.alt - 20f
        if (!p.ichiSkH) {
            when (k) {
                1 -> {
                    // Getsuga Tenshou: meia-lua negra de contorno rubro que atravessa todo mundo
                    getsugas.add(GetsugaWave(p.x + dx * 26f, p.y + dy * 26f, dx, dy, p.danoH * 2.6f, 18f + p.alt))
                    ichiFxs.add(IchiFx(4, p.x + dx * 30f, chest, dx, dy, 0.3f, 70f, ICHI_RED))
                    burst(p.x + dx * 28f, chest, 8, ICHI_RED)
                    shake = max(shake, 0.16f)
                    snd("heavy_swing", 1f, 0.8f)
                    snd("thunder_fire", 0.7f, 0.7f)
                }
                2 -> {
                    // Getsuga Jujisho: cruz de reiatsu que avança girando e atravessa todos à frente
                    crossWaves.add(CrossWave(p.x + dx * 28f, p.y + dy * 28f, dx, dy, p.danoH * 3.4f, 18f + p.alt))
                    ichiFxs.add(IchiFx(4, p.x + dx * 30f, chest, dx, dy, 0.3f, 84f, ICHI_RED))
                    ichiFxs.add(IchiFx(4, p.x + dx * 30f, chest, -dy, dx, 0.28f, 70f, ICHI_RED, 0.05f))
                    burst(p.x + dx * 28f, chest, 10, ICHI_RED)
                    burst(p.x + dx * 28f, chest, 5, 0xFFFFFFFF.toInt())
                    shake = max(shake, 0.22f)
                    snd("heavy_swing", 1f, 0.65f)
                    snd("thunder_fire", 0.8f, 0.55f)
                }
                3 -> {
                    // Redemoinho de Getsuga: giro com a Tensa Zangetsu, onda em volta e quatro luas se afastando
                    ichiFxs.add(IchiFx(1, p.x, gy, p.x, gy, 0.5f, 130f, ICHI_RED))
                    ichiFxs.add(IchiFx(4, p.x, chest, 1f, 0f, 0.32f, 80f, ICHI_RED))
                    ichiFxs.add(IchiFx(4, p.x, chest, -1f, 0f, 0.32f, 80f, ICHI_RED))
                    var hits = 0
                    for (e in enemies) {
                        if (!e.alive || e.alt - p.alt > 30f) continue
                        if (hypot(e.x - p.x, e.y - p.y) < 125f + e.radius) {
                            damageEnemy(e, p.danoH * 2.0f, p.x, p.y, 1.8f, "hit_heavy")
                            ichiSpark(e, ICHI_RED, 36f)
                            hits++
                        }
                    }
                    for (i in 0 until 4) {
                        val a = (i * 90 + 45) * 0.0174533f
                        getsugas.add(GetsugaWave(p.x + cos(a) * 24f, p.y + sin(a) * 24f, cos(a), sin(a), p.danoH * 0.9f, 18f + p.alt))
                    }
                    burst(p.x, gy, 10, ICHI_RED)
                    shake = max(shake, 0.22f + 0.03f * (if (hits > 3) 3 else hits))
                    snd("heavy_swing", 1f, 0.7f)
                    snd("axe_slam", 0.8f, 1.1f)
                }
                4 -> {
                    // Golpe Lunar: salto e golpe de cima para baixo; uma lua enorme racha o chão à frente
                    val gyF = p.y + dy * 60f + p.radius * 0.55f - p.ground
                    ichiFxs.add(IchiFx(5, p.x + dx * 60f, gyF, dx, dy, 0.55f, 135f, ICHI_RED))
                    ichiFxs.add(IchiFx(4, p.x + dx * 40f, chest + 8f, dx, dy, 0.34f, 110f, ICHI_RED))
                    ichiFxs.add(IchiFx(1, p.x + dx * 40f, gyF, p.x + dx * 40f, gyF, 0.5f, 100f, ICHI_RED))
                    var hits = 0
                    for (e in enemies) {
                        if (!e.alive || e.alt - p.alt > 30f) continue
                        val rx = e.x - p.x
                        val ry = e.y - p.y
                        val d = hypot(rx, ry)
                        if (d < 140f + e.radius && (rx * dx + ry * dy > -0.1f * d || d < 60f)) {
                            damageEnemy(e, p.danoH * 2.6f, p.x, p.y, 1.8f, "hit_heavy")
                            e.stunT = max(e.stunT, 0.5f)
                            ichiSpark(e, ICHI_RED, 40f)
                            hits++
                        }
                    }
                    getsugas.add(GetsugaWave(p.x + dx * 30f, p.y + dy * 30f, dx, dy, p.danoH * 1.1f, 8f + p.alt))
                    burst(p.x + dx * 50f, gyF, 12, ICHI_RED)
                    burst(p.x + dx * 50f, gyF, 6, 0xFF2A2A34.toInt())
                    shake = max(shake, 0.3f + 0.03f * (if (hits > 3) 3 else hits))
                    snd("axe_slam", 1f, 0.9f)
                    snd("heavy_swing", 1f, 0.75f)
                }
                else -> {
                    // transformação em Vasto Lorde: onda escura que empurra e atordoa quem está perto
                    p.hollow = true
                    p.hollowT = Player.ICHI_HOLLOW_T
                    p.hp = kotlin.math.min(p.maxHp, p.hp + p.maxHp * 0.2f)
                    ichiFxs.add(IchiFx(3, p.x, gy, p.x, gy, 0.9f, 240f, ICHI_RED))
                    ichiFxs.add(IchiFx(1, p.x, gy, p.x, gy, 0.7f, 170f, ICHI_BONE))
                    for (e in enemies) {
                        if (!e.alive || e.alt - p.alt > 30f) continue
                        if (hypot(e.x - p.x, e.y - p.y) < 170f + e.radius) {
                            damageEnemy(e, p.danoH * 1.2f, p.x, p.y, 2.2f, "hit_heavy")
                            e.stunT = max(e.stunT, 1.0f)
                        }
                    }
                    burst(p.x, gy - 18f, 16, ICHI_RED)
                    burst(p.x, gy - 18f, 10, ICHI_BONE)
                    darkFlash = 1f
                    shake = max(shake, 0.4f)
                    snd("thunder_cast", 1f, 0.5f)
                    snd("axe_slam", 1f, 0.7f)
                }
            }
        } else {
            when (k) {
                1 -> ichiBeam(p, dx, dy, false)
                2 -> {
                    // Garras Rasgantes: arrancada curta e uma chuva de cortes das garras
                    val dist = 125f
                    ichiDashStart(p, dx, dy, true, dist, 0.18f, 2)
                    val cx = p.x + p.facingX * dist * 0.8f
                    val cy = chest + p.facingY * dist * 0.8f
                    for (i in -1..1) {
                        ichiLine(cx + i * 13f, cy, -58f, 96f, 6f, ICHI_BONE, 0f)
                        ichiLine(cx + i * 13f, cy, 58f, 96f, 6f, ICHI_RED, 0.12f)
                    }
                    var hits = 0
                    for (e in enemies) {
                        if (!e.alive || abs(e.alt - p.alt) > 30f) continue
                        val rx = e.x - p.x
                        val ry = e.y - p.y
                        val d = hypot(rx, ry)
                        if (d < 150f + e.radius && (rx * p.facingX + ry * p.facingY > -0.1f * d || d < 50f)) {
                            damageEnemy(e, p.danoH * 2.4f, p.x, p.y, 1.4f, "hit_heavy")
                            e.stunT = max(e.stunT, 0.5f)
                            ichiSpark(e, ICHI_BONE, 38f)
                            burst(e.x, e.y - 14f - e.alt, 6, ICHI_RED)
                            ichiHit.add(e)
                            hits++
                        }
                    }
                    shake = max(shake, 0.12f + 0.02f * (if (hits > 3) 3 else hits))
                    snd("heavy_swing", 1f, 1.1f)
                }
                3 -> {
                    // Grito Hollow: berro que atordoa e empurra em volta
                    ichiFxs.add(IchiFx(1, p.x, gy, p.x, gy, 0.6f, 175f, ICHI_BONE))
                    ichiFxs.add(IchiFx(1, p.x, gy, p.x, gy, 0.8f, 120f, ICHI_RED))
                    for (e in enemies) {
                        if (!e.alive || e.alt - p.alt > 30f) continue
                        if (hypot(e.x - p.x, e.y - p.y) < 160f + e.radius) {
                            damageEnemy(e, p.danoH * 1.8f, p.x, p.y, 2.0f, "hit_heavy")
                            e.stunT = max(e.stunT, 1.5f)
                        }
                    }
                    burst(p.x, gy - 18f, 12, ICHI_BONE)
                    shake = max(shake, 0.3f)
                    snd("leap_land", 1f, 0.6f)
                    snd("thunder_cast", 0.8f, 0.7f)
                }
                4 -> ichiBeam(p, dx, dy, true)
                else -> {
                    // Mugetsu: explosão negra enorme; depois dela a forma se desfaz
                    ichiFxs.add(IchiFx(3, p.x, gy, p.x, gy, 1.1f, 330f, ICHI_RED))
                    ichiFxs.add(IchiFx(1, p.x, gy, p.x, gy, 0.9f, 260f, ICHI_BONE))
                    ichiFxs.add(IchiFx(4, p.x, chest, 1f, 0f, 0.4f, 110f, ICHI_RED))
                    ichiFxs.add(IchiFx(4, p.x, chest, -1f, 0f, 0.4f, 110f, ICHI_RED))
                    for (e in enemies) {
                        if (!e.alive || e.alt - p.alt > 40f) continue
                        if (hypot(e.x - p.x, e.y - p.y) < 270f + e.radius) {
                            damageEnemy(e, p.danoH * 6.0f, p.x, p.y, 2.6f, "hit_heavy")
                            e.stunT = max(e.stunT, 1.0f)
                            burst(e.x, e.y - 14f - e.alt, 10, ICHI_RED)
                        }
                    }
                    p.endHollow = true
                    burst(p.x, gy - 18f, 20, ICHI_RED)
                    burst(p.x, gy - 18f, 10, 0xFF1A0610.toInt())
                    darkFlash = 1f
                    shake = max(shake, 0.55f)
                    snd("thunder_fire", 1f, 0.45f)
                    snd("axe_slam", 1f, 0.55f)
                }
            }
        }
    }

    /** Shunpo (Bankai) e Sonído (Vasto Lorde): avanço veloz e invulnerável que corta quem estiver no caminho.
     *  [kind] 1 = botão de rolar, 2 = avanço de uma skill. O avanço para antes das paredes. */
    private fun ichiDashStart(p: Player, dx0: Float, dy0: Float, hol: Boolean, dist0: Float, dur: Float, kind: Int) {
        val l = hypot(dx0, dy0).coerceAtLeast(0.001f)
        val dx = dx0 / l
        val dy = dy0 / l
        var dist = dist0
        while (dist > 24f && playerBlocked(p, p.x + dx * dist, p.y + dy * dist)) dist -= 14f
        if (dist < 24f) dist = 24f
        val chest = p.y + p.radius * 0.55f - p.alt - 14f
        p.beginIchiDash(dx, dy, dist, dur, kind)
        ichiHit.clear()
        ichiDashWas = true
        val col = if (hol) ICHI_BONE else ICHI_RED
        ichiFxs.add(IchiFx(2, p.x, chest, p.x + dx * dist, chest + dy * dist, 0.4f, 22f, col))
        burst(p.x, p.y + p.radius * 0.55f - p.alt, 6, if (hol) ICHI_BONE else 0xFF2A2A34.toInt())
        shake = max(shake, 0.06f)
        snd("dash", 1f, if (hol) 0.75f else 1.45f)
    }

    /** Efeitos contínuos do Ichigo: Getsugas no ar, efeitos curtos, corte do Shunpo / Sonído e o fim da forma Vasto Lorde. */
    private fun updateIchiFx(dt: Float, p: Player) {
        if (p.classe != Classe.ICHIGO) return
        for (w in getsugas) {
            if (!w.alive) continue
            w.update(dt)
            if (map.isWall(w.x, w.y)) {
                w.alive = false
                burst(w.x, w.y - 14f, 5, ICHI_RED)
                sndAt("spell_wall", w.x, w.y, 0.8f, 0.7f)
                continue
            }
            for (e in enemies) {
                if (e.alive && e !in w.hit && hypot(e.x - w.x, e.y - w.y) < e.radius + 30f) {
                    w.hit.add(e)
                    damageEnemy(e, w.dmg, w.x - w.dx * 20f, w.y - w.dy * 20f, 1.4f, "hit_heavy")
                    burst(e.x, e.y - 14f - e.alt, 5, ICHI_RED)
                }
            }
        }
        getsugas.removeAll { !it.alive }
        for (w in crossWaves) {
            if (!w.alive) continue
            w.update(dt)
            if (map.isWall(w.x, w.y)) {
                w.alive = false
                burst(w.x, w.y - 14f, 8, ICHI_RED)
                ichiFxs.add(IchiFx(7, w.x, w.y - 14f - w.lift, 0f, 0f, 0.3f, 44f, ICHI_RED))
                sndAt("spell_wall", w.x, w.y, 0.9f, 0.6f)
                continue
            }
            for (e in enemies) {
                if (e.alive && e !in w.hit && hypot(e.x - w.x, e.y - w.y) < e.radius + 48f) {
                    w.hit.add(e)
                    damageEnemy(e, w.dmg, w.x - w.dx * 20f, w.y - w.dy * 20f, 1.6f, "hit_heavy")
                    e.stunT = max(e.stunT, 0.3f)
                    ichiSpark(e, ICHI_RED, 38f)
                    burst(e.x, e.y - 14f - e.alt, 6, ICHI_RED)
                }
            }
        }
        crossWaves.removeAll { !it.alive }
        ichiFxs.forEach { it.update(dt) }
        ichiFxs.removeAll { it.done }

        if (p.ichiT > 0f) {
            for (e in enemies) {
                if (!e.alive || e in ichiHit) continue
                if (abs(e.alt - p.alt) > 28f) continue
                if (hypot(e.x - p.x, e.y - p.y) < e.radius + p.radius + 16f) {
                    ichiHit.add(e)
                    val hol = p.hollow
                    damageEnemy(e, p.danoH * (if (hol) 1.6f else 1.1f), p.x - p.ichiDX * 20f, p.y - p.ichiDY * 20f, 1.2f, if (hol) "hit_heavy" else null)
                    if (hol) e.stunT = max(e.stunT, 0.4f)
                    ichiFxs.add(IchiFx(4, e.x, e.y - 14f - e.alt, p.ichiDX, p.ichiDY, 0.22f, 40f, if (hol) ICHI_BONE else ICHI_RED))
                    ichiSpark(e, if (hol) ICHI_BONE else ICHI_RED, 30f)
                    burst(e.x, e.y - 14f - e.alt, 5, if (hol) ICHI_BONE else ICHI_RED)
                    shake = max(shake, 0.1f)
                }
            }
        } else if (ichiDashWas) {
            ichiDashWas = false
            ichiHit.clear()
        }

        if (p.sky && Random.nextInt(3) == 0) {
            burst(p.x + (Random.nextFloat() - 0.5f) * 36f, p.y + p.radius * 0.55f - p.alt + 2f, 1, if (Random.nextBoolean()) ICHI_RED else 0xFF1A0610.toInt())
        }
        if (p.sky && kotlin.math.abs(p.vx) + kotlin.math.abs(p.vy) > 1f && Random.nextInt(3) == 0) {
            // rastro de reiatsu atrás de quem corre no céu
            burst(p.x - p.facingX * 12f + (Random.nextFloat() - 0.5f) * 8f, p.y + p.radius * 0.55f - p.alt - 2f, 1, 0xFFFF4B5C.toInt())
        }
        if (p.hollow && Random.nextInt(7) == 0) {
            burst(p.x + (Random.nextFloat() - 0.5f) * 14f, p.y - 22f - p.alt, 1, if (Random.nextBoolean()) ICHI_RED else 0xFF1A0610.toInt())
        }
        if (ichiHolPrev && !p.hollow) {
            // a máscara se desfaz
            val gy = p.y + p.radius * 0.55f - p.ground
            ichiFxs.add(IchiFx(1, p.x, gy, p.x, gy, 0.5f, 90f, ICHI_BONE))
            burst(p.x, gy - 18f, 12, ICHI_BONE)
            snd("teleport_out", 0.8f, 0.8f)
        }
        ichiHolPrev = p.hollow
    }

    // ───────────────────────── NINJA ─────────────────────────

    /** Golpe das lâminas; chamado no instante do impacto. O 3º golpe (corte cruzado) é mais largo e mais forte. */
    private fun ninjaStrike(sw: Int) {
        val p = player ?: return
        val cross = sw == 2
        val reach = p.classe.alcance * (if (cross) 1.3f else 1f)
        val dmg = p.danoH * (if (cross) 1.35f else 0.8f)
        var hits = 0
        enemies.forEach { e ->
            if (!e.alive) return@forEach
            val dist = hypot(e.x - p.x, e.y - p.y)
            if (dist < reach + e.radius) {
                val dx = e.x - p.x
                val dy = e.y - p.y
                val dot = dx * p.facingX + dy * p.facingY
                val inArc = if (cross) (dot > -0.2f * dist || dist < 50f) else (dot > 0 || dist < 40f)
                if (inArc) {
                    damageEnemy(e, dmg, p.x, p.y, if (cross) 1.4f else 0.8f)
                    hits++
                }
            }
        }
        shake = max(shake, if (cross) 0.1f else 0.04f)
        val fx = p.x + p.facingX * 30f
        val fy = p.y + p.facingY * 30f + p.radius * 0.55f - p.alt - 8f
        burst(fx, fy, if (hits > 0) 5 else 2, if (hits > 0) 0xFFD8354A.toInt() else 0xFFE0E6EC.toInt())
    }

    /** Pode usar uma skill do ninja? Gasta a mana/recarga e começa a pose; a skill sai no instante de soltar. */
    private fun ninjaSkill(p: Player, k: Int) {
        if (k !in 1..3) return
        if (k == 2) {
            val sh = ninjaShadow
            if (sh != null && sh.canSwap && p.skillK == 0 && p.alive) {
                ninjaSwap(p, sh)
                return
            }
        }
        val now = System.currentTimeMillis()
        if (!p.startSkill(k, now)) {
            snd("ui_error", 0.5f)
            return
        }
        pendingNinjaT = -1f
        queuedAt = 0L
        skillDX = p.facingX
        skillDY = p.facingY
        pendingSkillK = k
        pendingSkillT = p.skillReleaseAt(k)
        // a sombra (parada) começa a imitar a skill junto com o herói, na mesma direção
        val sh0 = ninjaShadow
        if (sh0 != null && sh0.alive && sh0.age >= NinjaShadow.DASH_T && k != 2) {
            sh0.faceDir(skillDX, skillDY)
            if (k == 1) sh0.setMode(1, Ninja3D.Q_DUR) else sh0.setMode(2, Ninja3D.E_DUR)
        }
        snd(when (k) {
            1 -> "bow_draw"
            2 -> "teleport_in"
            else -> "heavy_swing"
        }, 0.8f, if (k == 1) 1.7f else 1f)
    }

    /** Instante em que a skill do ninja sai. */
    private fun ninjaRelease(p: Player, k: Int) {
        var dx = skillDX
        var dy = skillDY
        val l0 = hypot(dx, dy).coerceAtLeast(0.001f)
        dx /= l0
        dy /= l0
        val gy = p.y + p.radius * 0.55f - p.alt
        when (k) {
            1 -> {
                // Shuriken Navalha: o ninja e a sombra atiram juntos, na mesma direção
                shurikens.add(Shuriken(p.x + dx * 22f, p.y + dy * 22f, dx, dy, p.danoH * 1.5f, p.ground))
                val sh = ninjaShadow
                if (sh != null && sh.alive && sh.age >= NinjaShadow.DASH_T) {
                    shurikens.add(Shuriken(sh.x + dx * 22f, sh.y + dy * 22f, dx, dy, p.danoH * 1.5f, sh.gz))
                    burst(sh.x, sh.y + sh.radius * 0.55f - sh.gz, 3, 0xFF9B5DE5.toInt())
                }
                snd("bow_shot", 0.9f, 1.5f * vary())
                shake = max(shake, 0.04f)
            }
            2 -> {
                // Sombra Viva: a sombra dispara para a frente e fica no lugar (W de novo troca de lugar)
                var d = rayLen(p.x, p.y, dx, dy, 200f).coerceAtLeast(0f)
                var tx = p.x + dx * d
                var ty = p.y + dy * d
                while (d > 0f && playerBlocked(p, tx, ty)) {
                    d -= 15f
                    tx = p.x + dx * d.coerceAtLeast(0f)
                    ty = p.y + dy * d.coerceAtLeast(0f)
                }
                if (d <= 0f) {
                    tx = p.x
                    ty = p.y
                }
                ninjaShadow = NinjaShadow(p.x, p.y, tx, ty, map.heightAt(tx, ty), atan2(dx, dy))
                burst(p.x, gy, 6, 0xFF6A4A9A.toInt())
                snd("teleport_out", 0.7f, 1.3f)
            }
            3 -> {
                // Corte Sombrio: giro de lâminas ao redor do ninja e da sombra (cada inimigo só leva uma vez)
                val hitSet = HashSet<Enemy>()
                val dmg = p.danoH * 1.3f
                ninjaSlashAt(p.x, p.y, p.ground, p.alt, 108f, dmg, hitSet)
                val sh = ninjaShadow
                if (sh != null && sh.alive && sh.age >= NinjaShadow.DASH_T) {
                    ninjaSlashAt(sh.x, sh.y, sh.gz, sh.gz, 108f, dmg, hitSet)
                }
                snd("heavy_swing", 0.95f, 1.15f)
                shake = max(shake, 0.1f)
            }
            else -> {}
        }
    }

    private fun ninjaSlashAt(x: Float, y: Float, gz: Float, alt: Float, rad: Float, dmg: Float, hitSet: HashSet<Enemy>) {
        slashFx.add(SlashFx(x, y, gz, rad))
        burst(x, y + 13.2f - alt, 5, 0xFFD8354A.toInt())
        for (e in enemies) {
            if (!e.alive || e in hitSet) continue
            if (e.alt - alt > 40f) continue
            if (hypot(e.x - x, e.y - y) < rad + e.radius * 0.5f) {
                hitSet.add(e)
                damageEnemy(e, dmg, x, y, 1.1f)
                e.slowT = max(e.slowT, 2.2f)
            }
        }
    }

    /** Troca de lugar com a Sombra Viva (a sombra fica onde o ninja estava). */
    private fun ninjaSwap(p: Player, sh: NinjaShadow) {
        val ox = p.x
        val oy = p.y
        val oz = p.ground
        swapFxs.add(SwapFx(ox, oy, oz, sh.x, sh.y, sh.gz, atan2(p.facingX, p.facingY)))
        darkFlash = 1f
        burst(ox, oy + p.radius * 0.55f - p.alt, 12, 0xFF6A2FB8.toInt())
        burst(ox, oy + p.radius * 0.55f - p.alt, 6, 0xFF1A0B2A.toInt())
        burst(sh.x, sh.y + p.radius * 0.55f - sh.gz, 12, 0xFFB3202D.toInt())
        burst(sh.x, sh.y + p.radius * 0.55f - sh.gz, 6, 0xFF1A0B2A.toInt())
        p.x = sh.x
        p.y = sh.y
        p.vz = 0f
        p.phaseT = 0.3f
        p.facingX = sin(sh.yaw)
        p.facingY = cos(sh.yaw)
        sh.x = ox
        sh.y = oy
        sh.gz = oz
        sh.swapFlash = 1f
        pendingNinjaT = -1f
        snd("teleport_out", 0.8f, 1.2f)
        snd("teleport_in", 0.8f, 1.2f)
        shake = max(shake, 0.16f)
    }

    /** Melhor alvo para a Marca da Morte: dentro de 400 px, de preferência na frente e perto. */
    private fun findMarkTarget(p: Player): Enemy? {
        var best: Enemy? = null
        var bestScore = -1f
        for (e in enemies) {
            if (!e.alive) continue
            val ex = e.x - p.x
            val ey = e.y - p.y
            val d = hypot(ex, ey)
            if (d > 400f) continue
            val dot = if (d > 1f) (ex * p.facingX + ey * p.facingY) / d else 1f
            val score = (dot + 1f) * 0.3f + (1f - d / 400f) * 0.4f
            if (score > bestScore) {
                bestScore = score
                best = e
            }
        }
        return best
    }

    private fun updateNinjaFx(dt: Float, p: Player) {
        val sh = ninjaShadow
        if (sh != null) {
            sh.update(dt)
            if (!sh.alive) {
                burst(sh.x, sh.y + sh.radius * 0.55f - sh.gz, 8, 0xFF3A1A5E.toInt())
                ninjaShadow = null
            }
        }
        for (s in shurikens) {
            if (!s.alive) continue
            s.update(dt)
            if (map.isWall(s.x, s.y)) {
                s.alive = false
                burst(s.x, s.y - 14f, 4, 0xFFB0BEC5.toInt())
                sndAt("arrow_wall", s.x, s.y, 0.7f, 1.3f)
                continue
            }
            for (e in enemies) {
                if (e.alive && e !in s.hit && hypot(e.x - s.x, e.y - s.y) < e.radius + 10f) {
                    s.hit.add(e)
                    damageEnemy(e, s.damage, s.x - s.dx * 20f, s.y - s.dy * 20f, 0.6f)
                    burst(e.x, e.y - 14f - e.alt, 3, 0xFFD8354A.toInt())
                }
            }
        }
        shurikens.removeAll { !it.alive }
        slashFx.forEach { it.update(dt) }
        slashFx.removeAll { it.done }
        swapFxs.forEach { it.update(dt) }
        swapFxs.removeAll { it.done }
        darkFlash = max(0f, darkFlash - dt * 2.6f)
        // Marca da Morte: conta o tempo e explode causando dano extra
        val mt = markTarget
        if (mt != null) {
            if (!mt.alive) {
                markTarget = null
            } else {
                markT -= dt
                if (markT <= 0f) {
                    markTarget = null
                    val boom = p.danoH * 1.8f + markDmg * 0.4f
                    damageEnemy(mt, boom, mt.x, mt.y, 1.5f, "hit_heavy")
                    sndAt("thunder_hit", mt.x, mt.y, 1f, 0.8f)
                    burst(mt.x, mt.y - 14f - mt.alt, 16, 0xFFD8354A.toInt())
                    burst(mt.x, mt.y - 14f - mt.alt, 8, 0xFFFFE9E9.toInt())
                    slashFx.add(SlashFx(mt.x, mt.y, mt.alt, 90f))
                    shake = max(shake, 0.28f)
                    markDmg = 0f
                }
            }
        }
    }

    /** Botão de ataque apertado. Guerreiro espera para saber se é toque (combo) ou segurar (golpe forte). */
    fun attackDown() {
        val p = player ?: return
        if (!p.alive || gameOver) return
        if (p.classe == Classe.ARQUEIRO) {
            bowHeld = true
            bowT = 0f
            bowBegin(p)
            return
        }
        if (p.classe == Classe.LUFFY) {
            luffyAutoAtk = true   // segurar o botão = ataque automático no alvo
            tryAttack()
            return
        }
        if (p.classe == Classe.GUERREIRO) {
            if (p.weapon == 1 && p.spearOut) {
                spearHint(p)
                return
            }
            attackHeld = true
            heldT = 0f
        } else {
            if (p.classe == Classe.PIKACHU) pikaHeld = true
            tryAttack()
        }
    }

    /** Começa a puxar o arco (se já puder atacar): a animação para no ponto de mira e espera soltar o botão. */
    private fun bowBegin(p: Player) {
        if (p.bowHold || p.attackAnim > 0f) return
        val now = System.currentTimeMillis()
        if (!p.canAttack(now)) return
        p.doAttack(now)
        p.bowHold = true
        bowT = 0f
        snd("bow_draw", 0.9f)
    }

    /** Solta a corda: a flecha sai no instante do gesto, com força proporcional ao tempo que ficou mirando. */
    private fun bowRelease(p: Player) {
        bowHeld = false
        aimOn = false
        if (p.bowHold) {
            p.bowHold = false
            val tNow = 1f - p.attackAnim
            pendingShotPow = ((bowT - 0.25f) / 0.9f).coerceIn(0f, 1f)
            pendingShotT = ((Archer3D.T_SHOT - tNow) / Player.ARCHER_RATE).coerceAtLeast(0f)
        } else if (p.alive && !gameOver) {
            pendingShotPow = 0f
            tryAttack()
        }
        p.bowPow = 0f
    }

    fun attackUp() {
        val p = player ?: return
        pikaHeld = false
        luffyAutoAtk = false
        if (p.classe == Classe.ARQUEIRO) {
            if (bowHeld) bowRelease(p)
            return
        }
        if (!attackHeld) return
        attackHeld = false
        if (!p.alive || gameOver) {
            p.charging = false
            aimOn = false
            return
        }
        if (p.charging && !p.rolling) {
            heavyAttack(p.chargeT)
        } else {
            p.charging = false
            p.chargeT = 0f
            tryAttack()
        }
        aimOn = false
    }

    private fun spearHint(p: Player) {
        if (hintCd > 0f) return
        hintCd = 1.3f
        snd("ui_error", 0.7f)
        texts.add(DmgText(p.x, p.y - p.radius - 40f, "Sem lança!", 0.9f, 0xFFFF8A80.toInt()))
    }

    /** Estocadas e varrida da lança; chamado no instante em que a ponta acerta. */
    private fun spearStrike(sw: Int) {
        val p = player ?: return
        if (!p.alive) return
        var fx = p.facingX
        var fy = p.facingY
        val fl = hypot(fx, fy).coerceAtLeast(0.001f)
        fx /= fl
        fy /= fl
        val reach = p.classe.alcance * (if (sw == 0) 1.9f else if (sw == 1) 1.45f else 2.4f)
        val dmg = p.danoH * (if (sw == 0) 1.0f else if (sw == 1) 0.9f else 1.75f)
        val half = if (sw == 0) 15f else 24f
        var hits = 0
        enemies.forEach { e ->
            if (!e.alive) return@forEach
            val dx = e.x - p.x
            val dy = e.y - p.y
            val along = dx * fx + dy * fy
            val perp = abs(dx * fy - dy * fx)
            val ok = if (sw == 1) {
                hypot(dx, dy) < reach + e.radius && along > -6f
            } else {
                along > -10f && along < reach + e.radius * 0.6f && perp < e.radius + half
            }
            if (ok) {
                damageEnemy(e, dmg, p.x, p.y, if (sw == 2) 2.4f else if (sw == 1) 1.5f else 1f, if (sw == 2) "hit_heavy" else "hit_blade")
                hits++
            }
        }
        val tx = p.x + fx * reach * 0.8f
        val ty = p.y + fy * reach * 0.8f - 14f
        if (hits > 0) {
            burst(tx, ty, 6 + hits * 2, 0xFFFFF2C0.toInt())
            shake = max(shake, if (sw == 2) 0.24f else if (sw == 1) 0.14f else 0.1f)
        } else {
            burst(tx, ty, 3, 0xFFE0E6EC.toInt())
            shake = max(shake, 0.04f)
        }
    }

    private fun rayLen(x: Float, y: Float, ax: Float, ay: Float, range: Float): Float {
        var d = 24f
        while (d < range) {
            if (map.isWall(x + ax * d, y + ay * d)) return d - 8f
            d += 8f
        }
        return range
    }

    /** Direção da mira (com leve ímã em inimigos próximos da linha) e alcance do arremesso. */
    private fun aimInfo(p: Player, power: Float): Triple<Float, Float, Float> {
        var ax = if (aimOn) aimX else p.facingX
        var ay = if (aimOn) aimY else p.facingY
        val l = hypot(ax, ay).coerceAtLeast(0.001f)
        ax /= l
        ay /= l
        val range = 240f + 400f * power
        var best = 0.94f
        var tgt: Enemy? = null
        for (e in enemies) {
            if (!e.alive) continue
            val ex = e.x - p.x
            val ey = e.y - p.y
            val d = hypot(ex, ey)
            if (d < 24f || d > range + e.radius) continue
            val c = (ex * ax + ey * ay) / d
            if (c > best) {
                best = c
                tgt = e
            }
        }
        aimTarget = tgt
        tgt?.let {
            val ex = it.x - p.x
            val ey = it.y - p.y
            val d = hypot(ex, ey)
            ax = ex / d
            ay = ey / d
        }
        return Triple(ax, ay, range)
    }

    private fun throwSpear(power: Float) {
        val p = player ?: return
        p.doHeavy(System.currentTimeMillis())
        val (ax, ay, range) = aimInfo(p, power)
        throwDX = ax
        throwDY = ay
        throwRange = range
        throwPower = power
        p.facingX = ax
        p.facingY = ay
        pendingThrow = true
        aimTarget = null
    }

    private fun releaseSpear() {
        val p = player ?: return
        // a lança sai exatamente de onde está na mão do herói (lado direito, braço esticado) e tem o mesmo tamanho dela
        val rs = p.radius / 24f * 1.6f
        var sx = p.x + throwDX * 42f * rs - throwDY * 7f * rs
        var sy = p.y + throwDY * 42f * rs * 0.8f + throwDX * 7f * rs
        if (map.isWall(sx, sy)) {
            sx = p.x
            sy = p.y
        }
        thrown = ThrownSpear(sx, sy, throwDX, throwDY, throwRange, p.danoH * (2.4f + 1.8f * throwPower), rs * 0.603f)
        p.spearOut = true
        burst(sx, sy - 18f, 8, 0xFFFFE08A.toInt())
        shake = max(shake, 0.12f)
        snd("spear_throw", 1f, vary())
    }

    private fun updateThrown(dt: Float, p: Player) {
        val ts = thrown ?: return
        ts.update(dt)
        if (ts.flying) {
            if (map.isWall(ts.x, ts.y)) {
                ts.x -= ts.dx * 12f
                ts.y -= ts.dy * 12f
                ts.land(true)
                sndAt("spear_wall", ts.x, ts.y, 1f)
                burst(ts.x + ts.dx * 10f, ts.y - 14f, 8, 0xFFB0BEC5.toInt())
                shake = max(shake, 0.12f)
            } else {
                for (e in enemies) {
                    if (!e.alive || ts.hit.contains(e)) continue
                    if (hypot(e.x - ts.x, e.y - ts.y) < e.radius + 12f) {
                        ts.hit.add(e)
                        damageEnemy(e, ts.damage, ts.x - ts.dx * 30f, ts.y - ts.dy * 30f, 1.8f, "hit_heavy")
                        burst(e.x, e.y - 16f, 10, 0xFFFFE08A.toInt())
                        shake = max(shake, 0.2f)
                    }
                }
            }
        } else if (ts.stuckT > 0.3f && hypot(p.x - ts.x, p.y - ts.y) < p.radius + 40f) {
            p.spearOut = false
            thrown = null
            snd("spear_pickup", 0.9f)
            burst(p.x, p.y - 26f, 12, 0xFFFFD54F.toInt())
            texts.add(DmgText(p.x, p.y - p.radius - 40f, "Lança recuperada!", 0.9f, 0xFFFFD54F.toInt()))
        }
    }

    private fun drawAim(canvas: Canvas, p: Player) {
        val (ax, ay, range) = aimInfo(p, p.chargeT)
        val fy0 = p.y + p.radius * 0.55f - p.ground
        val len = rayLen(p.x, fy0, ax, ay, range)
        val lock = aimTarget
        val col = if (lock != null) 0xFFFF5252.toInt() else 0xFFFFF2C0.toInt()
        paint.style = Paint.Style.FILL
        var d = 34f + ((time * 70f) % 18f)
        while (d < len) {
            val k = d / range
            paint.color = VoxelArt.alpha(col, 0.85f * (1f - 0.5f * k))
            canvas.drawCircle(p.x + ax * d - camX, fy0 + ay * d - camY, 2.2f + 1.4f * (1f - k), paint)
            d += 18f
        }
        val ex = p.x + ax * len - camX
        val ey = fy0 + ay * len - camY
        val pul = 1f + 0.12f * sin(time * 9f)
        val r = (13f + 6f * p.chargeT) * pul
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = VoxelArt.alpha(col, 0.95f)
        canvas.drawOval(ex - r, ey - r * 0.6f, ex + r, ey + r * 0.6f, paint)
        canvas.drawLine(ex - r - 6f, ey, ex - r * 0.4f, ey, paint)
        canvas.drawLine(ex + r * 0.4f, ey, ex + r + 6f, ey, paint)
        canvas.drawLine(ex, ey - r * 0.6f - 6f, ex, ey - r * 0.25f, paint)
        canvas.drawLine(ex, ey + r * 0.25f, ex, ey + r * 0.6f + 6f, paint)
        if (lock != null) {
            val lx = lock.x - camX
            val ly = lock.y - camY + lock.radius * 0.5f
            val rr = lock.radius + 10f
            val c2 = 8f
            paint.strokeWidth = 3.5f
            for (sxn in intArrayOf(-1, 1)) for (syn in intArrayOf(-1, 1)) {
                val cx = lx + sxn * rr
                val cy = ly + syn * rr * 0.6f
                canvas.drawLine(cx, cy, cx - sxn * c2, cy, paint)
                canvas.drawLine(cx, cy, cx, cy - syn * c2, paint)
            }
        }
        paint.style = Paint.Style.FILL
    }

    private fun heavyAttack(power: Float) {
        val p = player ?: return
        if (p.weapon == 1) {
            throwSpear(power)
            return
        }
        val now = System.currentTimeMillis()
        p.doHeavy(now)
        val spear = p.weapon == 1
        snd("heavy_swing", 1f, vary())
        val reach = p.classe.alcance * (if (spear) 1.9f else 1.35f)
        val dmg = p.danoH * (1.7f + 1.3f * power)
        enemies.forEach { e ->
            if (!e.alive) return@forEach
            val dist = hypot(e.x - p.x, e.y - p.y)
            if (dist < reach + e.radius) {
                val dx = e.x - p.x
                val dy = e.y - p.y
                val dot = dx * p.facingX + dy * p.facingY
                if (dot > (if (spear) 0.3f else -0.35f) * dist) damageEnemy(e, dmg, p.x, p.y, 1f, "hit_heavy")
            }
        }
        shake = max(shake, 0.3f)
        burst(p.x + p.facingX * 40f, p.y + p.facingY * 40f, 14, 0xFFFFD54F.toInt())
    }

    /** Inimigos também não sobem penhascos: o degrau máximo é meio nível de relevo. */
    private fun enemyBlocked(x: Float, y: Float, r: Float, alt: Float): Boolean =
        blockedAt(x, y, r) || map.heightAt(x, y) > alt + TileMap.LEVEL_PX * 0.55f

    private fun blockedAt(x: Float, y: Float, r: Float): Boolean =
        map.isWall(x, y) || map.isWall(x + r, y) || map.isWall(x - r, y) ||
            map.isWall(x, y + r) || map.isWall(x, y - r)

    private fun burst(x: Float, y: Float, n: Int, color: Int) {
        if (particles.size > 220) return
        repeat(n) {
            val a = Random.nextFloat() * 6.2832f
            val sp = 60f + Random.nextFloat() * 160f
            val f = 0.8f + Random.nextFloat() * 0.4f
            particles.add(
                Particle(
                    x, y, cos(a) * sp, sin(a) * sp - 70f,
                    0.45f + Random.nextFloat() * 0.4f, VoxelArt.shade(color, f),
                    4f + Random.nextFloat() * 5f
                )
            )
        }
    }

    /** hitSnd: null = som do golpe pela classe; "" = sem som (quem chamou toca o seu). */
    private fun damageEnemy(e: Enemy, dmg0: Float, fromX: Float, fromY: Float, kbMul: Float = 1f, hitSnd: String? = null, noText: Boolean = false) {
        val pl = player
        val crit = pl != null && pl.critH > 0f && Math.random() < pl.critH.toDouble()
        if (crit && pl != null && pl.classe == Classe.ARQUEIRO) sylv.aoCritar()
        // passiva do ninja (Desprezo pelos Fracos): bate mais forte em quem está com menos da metade da vida
        val weak = if (pl != null && pl.classe == Classe.NINJA && e.hp < e.maxHp * 0.5f) 1.12f else 1f
        val hollowMul = if (pl != null && pl.classe == Classe.ICHIGO && pl.hollow) 1.3f else 1f
        val dmg = (if (crit && pl != null) dmg0 * pl.critMulH else dmg0) * weak * hollowMul
        if (e === markTarget) markDmg += dmg
        val estavaVivo = e.alive
        e.takeDamage(dmg)
        val big = dmg >= 28f || crit
        if (hitSnd != "") {
            val nm = hitSnd ?: when (player?.classe) {
                Classe.BARBARO, Classe.LUFFY -> "hit_heavy"
                Classe.ARQUEIRO -> "hit_arrow"
                Classe.MAGO, Classe.PIKACHU -> "hit_magic"
                else -> "hit_blade"
            }
            sndAt(nm, e.x, e.y, 0.95f, vary())
            if (big && nm != "hit_heavy" || (big && player?.classe == Classe.BARBARO && dmg >= 48f)) sndAt("hit_crit", e.x, e.y, 0.7f, vary())
        }
        if (!e.alive) sndAt("enemy_die", e.x, e.y, 0.95f, vary())
        if (estavaVivo && !e.alive && pl != null && pl.classe == Classe.ARQUEIRO) sylv.onKill(e)
        if (!noText) {
            texts.add(
                DmgText(
                    e.x, e.y - e.radius - 30f - e.alt, dmg.toInt().toString(), 0.8f,
                    if (big) 0xFFFFD54F.toInt() else 0xFFFFFFFF.toInt()
                )
            )
        }
        val dx = e.x - fromX
        val dy = e.y - fromY
        val d = hypot(dx, dy).coerceAtLeast(1f)
        if (e.dummy) e.dummyHit(fromX)
        val kb = (if (e.dummy) 0f else if (e.tipo == 2) 6f else 16f) * kbMul
        val nx = e.x + dx / d * kb
        val ny = e.y + dy / d * kb
        if (!blockedAt(nx, ny, e.radius * 0.55f)) {
            e.x = nx
            e.y = ny
        }
        burst(e.x, e.y - 14f - e.alt, 4, e.palette())
        if (!e.alive) {
            burst(e.x, e.y - 10f - e.alt, 16, e.palette())
            burst(e.x, e.y - 14f - e.alt, 6, 0xFFFFD54F.toInt())
        }
    }

    private fun update(dt: Float) {
        if (paused) return
        time += dt
        VoxelArt.time = time
        player?.let { map.update(it.x, it.y, dt) }
        if (shake > 0f) shake = (shake - dt).coerceAtLeast(0f)
        particles.forEach { it.update(dt) }
        particles.removeAll { it.life <= 0f }
        texts.forEach {
            it.y -= 40f * dt
            it.life -= dt
        }
        texts.removeAll { it.life <= 0f }

        val p = player ?: return
        if (!p.alive) {
            if (!gameOver) {
                gameOver = true
                victory = false
                Sfx.stopLoops()
                Sfx.duck(true)
                snd("defeat", 1f)
                onGameOver?.invoke(false)
            }
            return
        }

        if (hintCd > 0f) hintCd -= dt
        if (p.classe == Classe.ARQUEIRO) sylv.update(dt, p)
        if (pendingHitT >= 0f) {
            pendingHitT -= dt
            if (pendingHitT <= 0f) {
                pendingHitT = -1f
                spearStrike(pendingHitSwing)
            }
        }
        if (pendingBarbT >= 0f) {
            pendingBarbT -= dt
            if (pendingBarbT <= 0f) {
                pendingBarbT = -1f
                if (p.alive && !p.rolling) barbStrike(pendingBarbSwing)
            }
        }
        if (pendingPikaT >= 0f) {
            pendingPikaT -= dt
            if (pendingPikaT <= 0f) {
                pendingPikaT = -1f
                if (p.alive && !p.rolling && !p.skilling && p.voltT <= 0f) pikaShock(p)
            }
        }
        if (pikaHeld && p.classe == Classe.PIKACHU && p.alive) {
            if (p.canAttack(System.currentTimeMillis())) tryAttack()
        }
        if (pendingExplT >= 0f) {
            pendingExplT -= dt
            if (pendingExplT <= 0f) {
                pendingExplT = -1f
                if (p.alive && !p.rolling && !p.skilling) explorerStrike(pendingExplSwing)
            }
        }
        // o punho segue o peito do alvo a cada quadro (ele anda durante o soco): é daqui que o desenho tira o ponto exato
        val aimE = luffyAimT
        if (aimE != null && p.classe == Classe.LUFFY && p.alive && aimE.alive && p.attackAnim > 0.001f) {
            val sc = if (aimE.tipo == 2) 1.55f else 1f
            p.luffyTgtOn = true
            p.luffyTgtX = aimE.x
            p.luffyTgtGY = aimE.y + aimE.radius * 0.55f - aimE.alt
            p.luffyTgtY = p.luffyTgtGY - 26f * sc
            p.luffyTgtR = aimE.radius
        } else {
            p.luffyTgtOn = false
            if (aimE != null && (!aimE.alive || p.attackAnim <= 0.001f)) luffyAimT = null
        }
        if (p.classe == Classe.LUFFY) { luffyGatUpdate(p); luffySoruUpdate(p, dt); luffyBazUpdate(p, dt) }
        if (pendingLuffyT >= 0f) {
            pendingLuffyT -= dt
            if (pendingLuffyT <= 0f) {
                pendingLuffyT = -1f
                if (p.alive && !p.rolling && !p.skilling) luffyStrike(pendingLuffySwing)
            }
        }
        if (pendingIchiT >= 0f) {
            pendingIchiT -= dt
            if (pendingIchiT <= 0f) {
                pendingIchiT = -1f
                if (p.alive && !p.rolling && !p.skilling && p.ichiT <= 0f) ichiStrike(pendingIchiSwing)
            }
        }
        if (pendingNinjaT >= 0f) {
            pendingNinjaT -= dt
            if (pendingNinjaT <= 0f) {
                pendingNinjaT = -1f
                if (p.alive && !p.rolling && !p.skilling && p.dashT <= 0f) ninjaStrike(pendingNinjaSwing)
            }
        }
        if (pendingCastT >= 0f) {
            pendingCastT -= dt
            if (pendingCastT <= 0f) {
                pendingCastT = -1f
                if (p.alive && !p.rolling) {
                    snd("spell_cast", 0.95f, vary())
                    projectiles.add(
                        Projectile(
                            p.x + p.facingX * 18f, p.y + p.facingY * 18f,
                            p.facingX * 280f, p.facingY * 280f,
                            p.danoH, 0xFF42A5F5.toInt(), true, 0
                        )
                    )
                } else {
                    p.mp = (p.mp + 12f).coerceAtMost(p.maxMpH) // feitiço interrompido: devolve a mana
                }
            }
        }
        if (pendingShotT >= 0f) {
            pendingShotT -= dt
            if (pendingShotT <= 0f) {
                pendingShotT = -1f
                if (p.alive && !p.rolling && !p.skilling) {
                    val pw = pendingShotPow
                    if (sylv.tiroBasico(pw)) {
                        // combate por alvo: a flecha vai direto no alvo marcado (ou no mais perto)
                        if (pw > 0.55f) snd("bow_shot_strong", 0.95f, vary()) else snd("bow_shot", 0.9f, vary() + 0.12f * pw)
                    } else {
                        val arrow = Projectile(
                            p.x + p.facingX * 20f, p.y + p.facingY * 20f,
                            p.facingX * 380f * (1f + 0.35f * pw), p.facingY * 380f * (1f + 0.35f * pw),
                            p.danoH * (1f + 0.5f * pw), 0xFF8D6E63.toInt(), true, 1
                        )
                        arrow.life = 1.4f * (1f + 0.25f * pw)
                        projectiles.add(arrow)
                        if (pw > 0.55f) snd("bow_shot_strong", 0.95f, vary()) else snd("bow_shot", 0.9f, vary() + 0.12f * pw)
                    }
                }
            }
        }
        if (pendingSkillT >= 0f) {
            pendingSkillT -= dt
            if (pendingSkillT <= 0f) {
                pendingSkillT = -1f
                if (p.alive && p.skilling) releaseSkill(p, pendingSkillK)
            }
        }
        if (pendingThrow && p.attackAnim <= 0.70f) {
            pendingThrow = false
            releaseSpear()
        }
        updateThrown(dt, p)

        // arqueiro segurando o ataque: mira (gira pelo arrasto do dedo) e carrega a força do arco
        if (p.classe == Classe.ARQUEIRO && (bowHeld || p.bowHold)) {
            if (!p.alive || p.rolling || p.skilling || p.flipT > 0f) {
                bowHeld = false
                p.bowHold = false
                p.bowPow = 0f
                p.attackAnim = 0f
                aimOn = false
            } else {
                if (aimOn && bowHeld) {
                    p.facingX = aimX
                    p.facingY = aimY
                }
                if (bowHeld && !p.bowHold) {
                    bowBegin(p) // recarrega a mira se ainda segura o botão depois de um disparo
                } else if (p.bowHold) {
                    bowT += dt
                    p.bowPow = ((bowT - 0.25f) / 0.9f).coerceIn(0f, 1f)
                }
            }
        }
        // segurar o ataque (guerreiro): levanta a espada; soltar = golpe forte
        if (aimOn && attackHeld && !p.rolling && p.weapon == 1) {
            p.facingX = aimX
            p.facingY = aimY
        }
        if (attackHeld) {
            if (p.rolling) {
                attackHeld = false
                p.charging = false
                p.chargeT = 0f
            } else {
                heldT += dt
                if (heldT > 0.22f) {
                    p.charging = true
                    p.chargeT = ((heldT - 0.22f) / 0.8f).coerceIn(0f, 1f)
                }
            }
        }
        val chargeNow = if (p.charging) p.chargeT else if (p.bowHold) p.bowPow else 0f
        if (chargeNow != lastChargeSent) {
            lastChargeSent = chargeNow
            onCharge?.invoke(chargeNow)
        }
        // Luffy: botão segurado = soco automático no alvo assim que a recarga de 1 s acabar
        if (luffyAutoAtk && p.classe == Classe.LUFFY && !gameOver) {
            if (p.canAttack(System.currentTimeMillis())) tryAttack()
        }
        // buffer de input: aperta um pouco antes de poder atacar e o golpe sai assim que liberar
        if (queuedAt != 0L) {
            val nowQ = System.currentTimeMillis()
            if (nowQ - queuedAt > 280L) {
                queuedAt = 0L
            } else if (p.canAttack(nowQ)) {
                queuedAt = 0L
                tryAttack()
            }
        }

        if (p.classe == Classe.ICHIGO && p.alive) {
            if (jumpHeld) {
                jumpHoldT += dt
                if (!p.sky && jumpHoldT > 0.22f && p.mp > 6f && p.alt > p.ground + 2f && !p.rolling && p.skillK == 0 && p.ichiT <= 0f) {
                    p.sky = true
                    p.glide = false
                    val fy = p.y + p.radius * 0.55f - p.alt
                    ichiFxs.add(IchiFx(1, p.x, fy, p.x, fy, 0.5f, 70f, ICHI_RED))
                    burst(p.x, fy, 8, ICHI_RED)
                }
            }
            if (p.sky != ichiSkyWas) {
                ichiSkyWas = p.sky
                if (p.sky) snd("fly_start", 0.7f, 0.8f) else snd("fly_end", 0.6f, 0.8f)
            }
        }
        if (p.classe == Classe.EXPLORADOR && p.alive && jumpHeld) {
            // segurou o pulo desde o chão: assim que está no ar, o chicote prende no céu
            jumpHoldT += dt
            if (!p.swinging && jumpHoldT > 0.16f && !p.grounded && !p.rolling && p.skillK == 0 && p.flipT <= 0f) explorerSwingStart(p)
        }
        if (p.classe == Classe.MAGO && jumpHeld) {
            jumpHoldT += dt
            if (!p.flying && jumpHoldT > 0.22f && p.fuel > 0.25f && p.alt > p.ground + 2f && !p.rolling) {
                p.flying = true
                p.glide = false
                burst(p.x, p.y + p.radius * 0.55f - p.alt, 8, 0xFF5FD4FF.toInt())
            }
        }
        if (p.flying != flyWas) {
            flyWas = p.flying
            if (p.flying) {
                snd("fly_start", 0.9f)
                Sfx.startLoop("fly_loop", 0.5f)
            } else {
                Sfx.stopLoop("fly_loop")
                snd("fly_end", 0.8f)
            }
        }
        if (p.hp < p.maxHp * 0.25f) {
            hbT -= dt
            if (hbT <= 0f) {
                hbT = 0.95f
                snd("heartbeat", 0.7f)
            }
        } else hbT = 0f
        val oldX = p.x
        val oldY = p.y
        p.update(dt)
        val tx2 = p.x
        val ty2 = p.y
        p.x = oldX
        p.y = oldY
        if (!playerBlocked(p, tx2, ty2)) {
            p.x = tx2
            p.y = ty2
        } else {
            if (!playerBlocked(p, tx2, oldY)) p.x = tx2
            if (!playerBlocked(p, p.x, ty2)) p.y = ty2
            if (p.classe == Classe.LUFFY) p.luffyBump(dt)
            if (p.rolling) p.rollT = (p.rollT - dt * 2f).coerceAtLeast(0f)
            if (p.voltT > 0f) p.voltT = (p.voltT - dt * 2f).coerceAtLeast(0f)
            if (p.ichiT > 0f) p.ichiT = (p.ichiT - dt * 2f).coerceAtLeast(0f)
            if (p.dashT > 0f && !p.dashUp) p.dashT = (p.dashT - dt * 2f).coerceAtLeast(0f)
        }
        p.updateVertical(dt, map.heightAt(p.x, p.y))
        if (p.classe == Classe.BARBARO) barbMotion(p, dt)
        if (p.landed > 0f) {
            burst(p.x, p.y + p.radius * 0.55f - p.ground, 6, 0xFFB8B0A8.toInt())
            if (!noLandSnd) {
                if (p.landed > 0.95f) snd("land_hard", 0.9f, vary()) else snd("land_soft", 0.45f + 0.4f * p.landed, vary())
            }
            noLandSnd = false
            p.landed = 0f
        }
        if (p.luffyLaunchReq) {
            p.luffyLaunchReq = false
            snd("dash", 0.95f, 0.9f * vary())
            burst(p.x, p.y + p.radius * 0.55f - p.ground, 8, 0xFFB8B0A8.toInt())
        }
        // passos: um a cada tantos pixels andados no chão
        if (p.grounded && !p.rolling && !p.flying && !p.skilling && p.flipT <= 0f) {
            stepAcc += hypot(p.x - oldX, p.y - oldY)
            val stride = if (p.classe == Classe.BARBARO) 38f else if (p.classe == Classe.ARQUEIRO) 46f else 42f
            if (stepAcc >= stride) {
                stepAcc -= stride
                stepIdx++
                val heavy = p.classe == Classe.BARBARO
                snd("step" + (1 + stepIdx % 3), if (heavy) 0.5f else 0.32f, vary() * (if (heavy) 0.82f else 1f))
            }
        } else stepAcc = 0f
        if (p.rolling && (p.classe != Classe.LUFFY || p.luffyPh == 3) && Random.nextInt(3) == 0) {
            burst(p.x, p.y + p.radius * 0.55f - p.ground, 1, 0xFFB8B0A8.toInt())
        }

        p.x = p.x.coerceIn(p.radius, map.worldWidth() - p.radius)
        p.y = p.y.coerceIn(p.radius, map.worldHeight() - p.radius)

        val now = System.currentTimeMillis()
        enemies.forEach { e ->
            if (!e.alive) return@forEach
            val ox = e.x
            val oy = e.y
            e.update(dt, p)
            if (e.alertFx) {
                e.alertFx = false
                sndAt("enemy_alert", e.x, e.y, 0.8f, vary())
            }
            if (e.swingFx) {
                e.swingFx = false
                sndAt("enemy_swing", e.x, e.y, 0.75f, vary())
            }
            val rr = e.radius * 0.55f
            if (enemyBlocked(e.x, e.y, rr, e.alt)) {
                val nx = e.x
                val ny = e.y
                e.x = if (!enemyBlocked(nx, oy, rr, e.alt)) nx else ox
                e.y = if (!enemyBlocked(e.x, ny, rr, e.alt)) ny else oy
            }
            if (map.inSafeZone(e.x, e.y) && !map.inSafeZone(ox, oy)) { e.x = ox; e.y = oy }
            e.ground = map.heightAt(e.x, e.y)
            e.alt += (e.ground - e.alt) * (dt * 10f).coerceAtMost(1f)
            val dist = hypot(e.x - p.x, e.y - p.y)
            if (!e.dummy && e.stunT <= 0f && !p.invuln && !p.phased && p.alt - e.alt < 20f && dist < e.radius + p.radius && now - e.lastHit > e.hitCooldown) {
                p.takeDamage(e.damage * (1f - p.defesaH))
                snd("player_hurt", 1f, vary())
                e.lastHit = now
                shake = 0.3f
                burst(p.x, p.y - 14f - p.alt, 6, 0xFFD32F2F.toInt())
                texts.add(DmgText(p.x, p.y - 56f - p.alt, "-" + e.damage.toInt(), 0.8f, 0xFFFF5252.toInt()))
            }
        }

        for (i in 0 until enemies.size) {
            val a = enemies[i]
            for (j in i + 1 until enemies.size) {
                val b = enemies[j]
                val dx = b.x - a.x
                val dy = b.y - a.y
                val d = hypot(dx, dy)
                val min = (a.radius + b.radius) * 0.9f
                if (d > 0.01f && d < min) {
                    val push = (min - d) * 0.25f
                    val ux = dx / d * push
                    val uy = dy / d * push
                    if (!blockedAt(b.x + ux, b.y + uy, b.radius * 0.55f)) {
                        b.x += ux
                        b.y += uy
                    }
                    if (!blockedAt(a.x - ux, a.y - uy, a.radius * 0.55f)) {
                        a.x -= ux
                        a.y -= uy
                    }
                }
            }
        }

        updateArcherFx(dt, p)
        updateNinjaFx(dt, p)
        updatePikaFx(dt, p)
        updateIchiFx(dt, p)

        projectiles.forEach { pr ->
            if (!pr.alive) return@forEach
            pr.update(dt)
            if (map.isWall(pr.x, pr.y)) {
                pr.alive = false
                sndAt(if (pr.kind == 1) "arrow_wall" else "spell_wall", pr.x, pr.y, 0.85f, vary())
                burst(pr.x, pr.y - 10f, 4, 0xFFB0BEC5.toInt())
                return@forEach
            }
            if (pr.fromPlayer) {
                enemies.forEach { e ->
                    if (pr.alive && e.alive && hypot(e.x - pr.x, e.y - pr.y) < e.radius + pr.radius) {
                        damageEnemy(e, pr.damage, pr.x - pr.vx * 0.05f, pr.y - pr.vy * 0.05f, 1f, if (pr.kind == 1) "hit_arrow" else "hit_magic")
                        pr.alive = false
                    }
                }
            }
        }
        projectiles.removeAll { !it.alive }
        enemies.removeAll { !it.alive }

        if (hadEnemies && enemies.none { !it.dummy } && !gameOver) {
            gameOver = true
            victory = true
            Sfx.stopLoops()
            Sfx.duck(true)
            snd("victory", 1f)
            onGameOver?.invoke(true)
        }

        // Câmera estilo Minecraft Dungeons: look-ahead + spring suave + zoom dinâmico
        val speed = hypot(p.vx, p.vy)
        val laTargetX = p.facingX * LOOK_AHEAD * (0.35f + (speed / 220f).coerceIn(0f, 1f) * 0.65f)
        val laTargetY = p.facingY * LOOK_AHEAD * (0.35f + (speed / 220f).coerceIn(0f, 1f) * 0.65f)
        lookAheadX += (laTargetX - lookAheadX) * (1f - kotlin.math.exp(-dt * 4.5f))
        lookAheadY += (laTargetY - lookAheadY) * (1f - kotlin.math.exp(-dt * 4.5f))

        val desiredX = camClamp(p.x + lookAheadX - width * FRAME_X, map.worldWidth(), width.toFloat())
        val desiredY = camClamp(p.y + lookAheadY - height * FRAME_Y, map.worldHeight(), height.toFloat())

        // Spring damping (mais cinematográfico que lerp linear)
        // câmera presa no herói: sempre no centro da tela (só para nas bordas do mapa)
        camVX = 0f
        camVY = 0f
        camX = desiredX
        camY = desiredY
        camX = camClamp(camX, map.worldWidth(), width.toFloat())
        camY = camClamp(camY, map.worldHeight(), height.toFloat())

        // Zoom controlado pelo jogador (pinça). Nunca afasta além do padrão (1.0), então
        // a tela sempre fica coberta pelo mapa e não aparecem bordas pretas.
        targetZoom = userZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val zr = if (pinching) 16f else 7f
        camZoom += (targetZoom - camZoom) * (1f - kotlin.math.exp(-dt * zr))
        if (camZoom < MIN_ZOOM) camZoom = MIN_ZOOM
        VoxelArt.zoom = camZoom
    }

    private fun feetRow(e: Entity): Int = ((e.y + e.radius * 0.6f) / map.tileSize).toInt()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        VoxelArt.flash = 0f
        canvas.drawColor(0xFF11181C.toInt())

        canvas.save()
        // Zoom centrado no meio da tela (sensação de profundidade MCD)
        if (camZoom != 1f) {
            canvas.scale(camZoom, camZoom, w * 0.5f, h * 0.5f)
        }
        if (shake > 0f) {
            val intensity = shake * 28f
            val sx = sin(time * 62f) * intensity * 0.55f + (Random.nextFloat() - 0.5f) * intensity * 0.45f
            val sy = cos(time * 51f) * intensity * 0.45f + (Random.nextFloat() - 0.5f) * intensity * 0.55f
            canvas.translate(sx, sy)
        }

        map.drawFloor(canvas, paint, camX, camY, w, h)
        traps.forEach { it.draw(canvas, paint, camX, camY, time) }
        rains.forEach { it.drawGround(canvas, paint, camX, camY, time) }
        if (player?.classe == Classe.ARQUEIRO) sylv.drawGround(canvas, paint, camX, camY, time)
        player?.let { if (it.classe == Classe.LUFFY) { drawLuffyTarget(canvas, it); drawGatArea(canvas, it); drawSoruFx(canvas, it); drawBazFx(canvas) } }

        drawList.clear()
        for (e in enemies) {
            if (e.x + 140f > camX && e.x - 140f < camX + w && e.y + 140f > camY && e.y - 220f < camY + h) drawList.add(e)
        }
        player?.let { drawList.add(it) }
        ninjaShadow?.let { if (it.alive) drawList.add(it) }
        drawList.sortBy { it.y }
        var i = 0
        val startRow = (camY / map.tileSize).toInt().coerceAtLeast(0)
        val endRow = (((camY + h) / map.tileSize).toInt() + 9).coerceAtMost(map.height - 1)
        for (ty in startRow..endRow) {
            map.drawRow(canvas, paint, ty, camX, camY, w)
            while (i < drawList.size && feetRow(drawList[i]) <= ty) {
                drawList[i].draw(canvas, paint, camX, camY)
                i++
            }
        }
        while (i < drawList.size) {
            drawList[i].draw(canvas, paint, camX, camY)
            i++
        }

        player?.let { p ->
            if (p.alive && (map.isWall(p.x, p.y + map.tileSize * 0.9f) || map.heightAt(p.x, p.y + map.tileSize * 0.9f) > p.alt + 30f)) {
                val sx = p.x - camX
                val sy = p.y - camY
                val layer = canvas.saveLayerAlpha(sx - 70f, sy - 100f, sx + 70f, sy + 60f, 110)
                p.draw(canvas, paint, camX, camY)
                canvas.restoreToCount(layer)
            }
        }

        projectiles.forEach { it.draw(canvas, paint, camX, camY) }
        if (player?.classe == Classe.ARQUEIRO) sylv.drawAir(canvas, paint, camX, camY, time)
        rains.forEach { it.drawArrows(canvas, paint, camX, camY) }
        bolts.forEach { it.draw(canvas, paint, camX, camY) }
        zaps.forEach { it.draw(canvas, paint, camX, camY) }
        swapFxs.forEach { it.draw(canvas, paint, camX, camY) }
        slashFx.forEach { it.draw(canvas, paint, camX, camY) }
        ichiFxs.forEach { it.draw(canvas, paint, camX, camY) }
        getsugas.forEach { it.draw(canvas, paint, camX, camY) }
        crossWaves.forEach { it.draw(canvas, paint, camX, camY) }
        shurikens.forEach { it.draw(canvas, paint, camX, camY) }
        markTarget?.let { if (it.alive) DeathMarkFx.draw(canvas, paint, camX, camY, it, markT, markTotal, time) }
        thrown?.draw(canvas, paint, camX, camY, time)
        player?.let { p ->
            if (p.alive && p.charging && p.weapon == 1 && p.classe == Classe.GUERREIRO) drawAim(canvas, p)
        }
        particles.forEach { it.draw(canvas, paint, camX, camY) }
        canvas.restore()

        vignette?.let {
            paint.style = Paint.Style.FILL
            paint.shader = it
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
        }
        if (darkFlash > 0.01f) {
            // troca de sombra: a tela escurece num lampejo roxo-negro
            paint.style = Paint.Style.FILL
            paint.color = ((darkFlash * darkFlash * 70f).toInt() shl 24) or 0x0A0314
            canvas.drawRect(0f, 0f, w, h, paint)
        }

        paint.typeface = pixelFace
        if (player?.classe == Classe.ARQUEIRO) sylv.drawHud(canvas, paint, w, h)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 30f
        texts.forEach { t ->
            val a = (t.life / 0.8f).coerceIn(0f, 1f)
            val sx = t.x - camX
            val sy = t.y - camY
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 6f
            paint.color = VoxelArt.alpha(0xFF000000.toInt(), a)
            canvas.drawText(t.text, sx, sy, paint)
            paint.style = Paint.Style.FILL
            paint.color = VoxelArt.alpha(t.color, a)
            canvas.drawText(t.text, sx, sy, paint)
        }
    }

    fun aliveEnemies(): Int = enemies.count { it.alive && !it.dummy }

    /** Ninja: a Sombra Viva está no ar e dá para trocar de lugar com ela. */
    fun swapReady(): Boolean = ninjaShadow?.canSwap == true

    /** Segundos que faltam para a Sombra Viva sumir (0 = sem sombra). */
    fun shadowLeft(): Float = ninjaShadow?.let { if (it.alive) (NinjaShadow.LIFE - it.age).coerceAtLeast(0f) else 0f } ?: 0f

    fun getPlayer(): Player? = player
}
