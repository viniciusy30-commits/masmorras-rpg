package com.viniciusy.masmorras

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.abs
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
    private val groundFx = mutableListOf<GroundFx>()
    private val fxPath = android.graphics.Path()
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
    private val MAX_ZOOM = 1.9f
    private var userZoom = 1f
    private var attackHeld = false
    // lança: golpe pendente (dano sai no instante da estocada), arremesso pendente e a lança no chão
    private var thrown: ThrownSpear? = null
    private var pendingHitT = -1f
    private var pendingHitSwing = 0
    private var pendingCastT = -1f
    private var pendingBarbT = -1f
    private var pendingBarbSwing = 0
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
        return true
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
        enemies.clear()
        projectiles.clear()
        thrown = null
        pendingHitT = -1f
        pendingCastT = -1f
        pendingBarbT = -1f
        pendingThrow = false
        aimTarget = null
        aimOn = false
        particles.clear()
        groundFx.clear()
        texts.clear()
        enemies.addAll(map.createEnemies())
        hadEnemies = enemies.isNotEmpty()
        gameOver = false
        victory = false
        running = true
        shake = 0f
        attackHeld = false
        queuedAt = 0L
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
            if (!p.rolling && !pendingThrow && !(aimOn && attackHeld)) {
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
        if (p.classe != Classe.GUERREIRO || p.weapon != 1) return
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
    }

    fun jump() {
        val p = player ?: return
        if (gameOver) return
        if (p.jump()) burst(p.x, p.y + p.radius * 0.55f - p.ground, 5, 0xFFB8B0A8.toInt())
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
            p.startLeapCharge()
            return
        }
        if (p.classe == Classe.MAGO && p.flying) return
        jump()
    }

    fun jumpUp() {
        jumpHeld = false
        jumpHoldT = 0f
        val p = player ?: return
        if (p.leapCharging) {
            if (p.releaseLeap()) {
                groundSmash(p, 62f, 0.6f, 1.2f, 12, 0.14f)
            }
            return
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
    }

    fun roll() {
        val p = player ?: return
        if (gameOver) return
        if (p.classe == Classe.MAGO) {
            teleport(p)
            return
        }
        if (p.classe == Classe.BARBARO) {
            val ax = if (aimOn) aimX else p.facingX
            val ay = if (aimOn) aimY else p.facingY
            if (p.startDash(System.currentTimeMillis(), ax, ay)) {
                groundSmash(p, 58f, 0.5f, 1f, 12, 0.12f)
            }
            return
        }
        if (p.startRoll(System.currentTimeMillis())) {
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

    fun tryAttack() {
        val p = player ?: return
        if (!p.alive || gameOver) return
        val now = System.currentTimeMillis()
        if (p.classe == Classe.GUERREIRO && p.weapon == 1 && p.spearOut) {
            spearHint(p)
            return
        }
        if (!p.canAttack(now)) {
            if ((p.classe == Classe.GUERREIRO || p.classe == Classe.BARBARO) && !p.rolling) queuedAt = now
            return
        }

        if (p.classe.isRanged) {
            if (p.classe == Classe.MAGO && p.mp < 12f) return
            if (p.classe == Classe.MAGO) p.mp -= 12f
            p.doAttack(now)
            if (p.classe == Classe.MAGO) {
                // o orbe só sai do cajado no instante do gesto (sincronizado com a animação)
                pendingCastT = p.castDelay()
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
                    p.classe.danoBase, color, true, if (isMage) 0 else 1
                )
            )
        } else {
            p.doAttack(now)
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
            val dmg = p.classe.danoBase * (if (spear) (if (p.swing == 0) 0.95f else if (p.swing == 1) 0.85f else 1.45f) else if (thrust) 1.3f else 1f)
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

    /** Destrói o chão ao redor do bárbaro: rachaduras, pedras voando, dano em área e empurrão. */
    private fun groundSmash(p: Player, radius: Float, dmgMul: Float, kb: Float, stones: Int, shk: Float) {
        val gy = p.y + p.radius * 0.55f - p.ground
        groundFx.add(GroundFx(p.x, gy, radius, 2.4f, Random.nextInt()))
        burst(p.x, gy, stones, 0xFF8A7B6A.toInt())
        burst(p.x, gy, stones / 2, 0xFF4A3F34.toInt())
        burst(p.x, gy, 4, 0xFFFF9A3D.toInt())
        shake = max(shake, shk)
        val dmg = p.classe.danoBase * dmgMul
        enemies.forEach { e ->
            if (!e.alive) return@forEach
            if (e.alt - p.alt > 24f) return@forEach
            val dist = hypot(e.x - p.x, e.y - p.y)
            if (dist < radius + e.radius) damageEnemy(e, dmg, p.x, p.y, kb)
        }
    }

    /** Pouso do salto: grande destruição ao redor. */
    private fun leapLand(p: Player) {
        Barbarian3D.slamReq = true
        groundSmash(p, 110f, 1.8f, 2.2f, 26, 0.4f)
        val gy = p.y + p.radius * 0.55f - p.ground
        groundFx.add(GroundFx(p.x, gy, 70f, 2.0f, Random.nextInt()))
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
        val dmg = p.classe.danoBase * (if (slam) 1.5f else 1f)
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
        if (slam) burst(p.x + p.facingX * 26f, p.y + p.facingY * 26f + p.radius * 0.55f - p.ground, 7, 0xFFB8B0A8.toInt())
    }

    /** Botão de ataque apertado. Guerreiro espera para saber se é toque (combo) ou segurar (golpe forte). */
    fun attackDown() {
        val p = player ?: return
        if (!p.alive || gameOver) return
        if (p.classe == Classe.GUERREIRO) {
            if (p.weapon == 1 && p.spearOut) {
                spearHint(p)
                return
            }
            attackHeld = true
            heldT = 0f
        } else {
            tryAttack()
        }
    }

    fun attackUp() {
        val p = player ?: return
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
        val dmg = p.classe.danoBase * (if (sw == 0) 1.0f else if (sw == 1) 0.9f else 1.75f)
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
                damageEnemy(e, dmg, p.x, p.y, if (sw == 2) 2.4f else if (sw == 1) 1.5f else 1f)
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
        thrown = ThrownSpear(sx, sy, throwDX, throwDY, throwRange, p.classe.danoBase * (2.4f + 1.8f * throwPower), rs * 0.603f)
        p.spearOut = true
        burst(sx, sy - 18f, 8, 0xFFFFE08A.toInt())
        shake = max(shake, 0.12f)
    }

    private fun updateThrown(dt: Float, p: Player) {
        val ts = thrown ?: return
        ts.update(dt)
        if (ts.flying) {
            if (map.isWall(ts.x, ts.y)) {
                ts.x -= ts.dx * 12f
                ts.y -= ts.dy * 12f
                ts.land(true)
                burst(ts.x + ts.dx * 10f, ts.y - 14f, 8, 0xFFB0BEC5.toInt())
                shake = max(shake, 0.12f)
            } else {
                for (e in enemies) {
                    if (!e.alive || ts.hit.contains(e)) continue
                    if (hypot(e.x - ts.x, e.y - ts.y) < e.radius + 12f) {
                        ts.hit.add(e)
                        damageEnemy(e, ts.damage, ts.x - ts.dx * 30f, ts.y - ts.dy * 30f, 1.8f)
                        burst(e.x, e.y - 16f, 10, 0xFFFFE08A.toInt())
                        shake = max(shake, 0.2f)
                    }
                }
            }
        } else if (ts.stuckT > 0.3f && hypot(p.x - ts.x, p.y - ts.y) < p.radius + 40f) {
            p.spearOut = false
            thrown = null
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
        val reach = p.classe.alcance * (if (spear) 1.9f else 1.35f)
        val dmg = p.classe.danoBase * (1.7f + 1.3f * power)
        enemies.forEach { e ->
            if (!e.alive) return@forEach
            val dist = hypot(e.x - p.x, e.y - p.y)
            if (dist < reach + e.radius) {
                val dx = e.x - p.x
                val dy = e.y - p.y
                val dot = dx * p.facingX + dy * p.facingY
                if (dot > (if (spear) 0.3f else -0.35f) * dist) damageEnemy(e, dmg, p.x, p.y)
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

    private fun damageEnemy(e: Enemy, dmg: Float, fromX: Float, fromY: Float, kbMul: Float = 1f) {
        e.takeDamage(dmg)
        val big = dmg >= 28f
        texts.add(
            DmgText(
                e.x, e.y - e.radius - 30f - e.alt, dmg.toInt().toString(), 0.8f,
                if (big) 0xFFFFD54F.toInt() else 0xFFFFFFFF.toInt()
            )
        )
        val dx = e.x - fromX
        val dy = e.y - fromY
        val d = hypot(dx, dy).coerceAtLeast(1f)
        val kb = (if (e.tipo == 2) 6f else 16f) * kbMul
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
        time += dt
        VoxelArt.time = time
        player?.let { map.update(it.x, it.y, dt) }
        if (shake > 0f) shake = (shake - dt).coerceAtLeast(0f)
        particles.forEach { it.update(dt) }
        particles.removeAll { it.life <= 0f }
        groundFx.forEach { it.age += dt }
        groundFx.removeAll { it.age >= it.life }
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
                onGameOver?.invoke(false)
            }
            return
        }

        if (hintCd > 0f) hintCd -= dt
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
        if (pendingCastT >= 0f) {
            pendingCastT -= dt
            if (pendingCastT <= 0f) {
                pendingCastT = -1f
                if (p.alive && !p.rolling) {
                    projectiles.add(
                        Projectile(
                            p.x + p.facingX * 18f, p.y + p.facingY * 18f,
                            p.facingX * 280f, p.facingY * 280f,
                            p.classe.danoBase, 0xFF42A5F5.toInt(), true, 0
                        )
                    )
                } else {
                    p.mp = (p.mp + 12f).coerceAtMost(p.classe.maxMp) // feitiço interrompido: devolve a mana
                }
            }
        }
        if (pendingThrow && p.attackAnim <= 0.70f) {
            pendingThrow = false
            releaseSpear()
        }
        updateThrown(dt, p)

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
        val chargeNow = if (p.charging) p.chargeT else 0f
        if (chargeNow != lastChargeSent) {
            lastChargeSent = chargeNow
            onCharge?.invoke(chargeNow)
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

        if (p.classe == Classe.MAGO && jumpHeld) {
            jumpHoldT += dt
            if (!p.flying && jumpHoldT > 0.22f && p.fuel > 0.25f && p.alt > p.ground + 2f && !p.rolling) {
                p.flying = true
                p.glide = false
                burst(p.x, p.y + p.radius * 0.55f - p.alt, 8, 0xFF5FD4FF.toInt())
            }
        }
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
            if (p.rolling) p.rollT = (p.rollT - dt * 2f).coerceAtLeast(0f)
        }
        p.updateVertical(dt, map.heightAt(p.x, p.y))
        if (p.classe == Classe.BARBARO) barbMotion(p, dt)
        if (p.landed > 0f) {
            burst(p.x, p.y + p.radius * 0.55f - p.ground, 6, 0xFFB8B0A8.toInt())
            p.landed = 0f
        }
        if (p.rolling && Random.nextInt(3) == 0) {
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
            if (!p.rolling && !p.phased && p.alt - e.alt < 20f && dist < e.radius + p.radius && now - e.lastHit > e.hitCooldown) {
                p.takeDamage(e.damage)
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

        projectiles.forEach { pr ->
            if (!pr.alive) return@forEach
            pr.update(dt)
            if (map.isWall(pr.x, pr.y)) {
                pr.alive = false
                burst(pr.x, pr.y - 10f, 4, 0xFFB0BEC5.toInt())
                return@forEach
            }
            if (pr.fromPlayer) {
                enemies.forEach { e ->
                    if (pr.alive && e.alive && hypot(e.x - pr.x, e.y - pr.y) < e.radius + pr.radius) {
                        damageEnemy(e, pr.damage, pr.x - pr.vx * 0.05f, pr.y - pr.vy * 0.05f)
                        pr.alive = false
                    }
                }
            }
        }
        projectiles.removeAll { !it.alive }
        enemies.removeAll { !it.alive }

        if (hadEnemies && enemies.isEmpty() && !gameOver) {
            gameOver = true
            victory = true
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
        groundFx.forEach { it.draw(canvas, paint, camX, camY, fxPath) }

        drawList.clear()
        for (e in enemies) {
            if (e.x + 140f > camX && e.x - 140f < camX + w && e.y + 140f > camY && e.y - 220f < camY + h) drawList.add(e)
        }
        player?.let { drawList.add(it) }
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

        paint.typeface = pixelFace
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

    fun aliveEnemies(): Int = enemies.count { it.alive }

    fun getPlayer(): Player? = player
}
