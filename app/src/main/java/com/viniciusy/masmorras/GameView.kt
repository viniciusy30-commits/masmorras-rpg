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
    private val FRAME_Y = 0.62f
    private val CAM_SMOOTH = 8.5f
    private val LOOK_AHEAD = 70f

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
        enemies.clear()
        projectiles.clear()
        particles.clear()
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
            if (!p.rolling) {
                p.facingX = nx
                p.facingY = ny
            }
        } else {
            p.inX = 0f
            p.inY = 0f
        }
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

    fun roll() {
        val p = player ?: return
        if (gameOver) return
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
        if (!p.canAttack(now)) {
            if (p.classe == Classe.GUERREIRO && !p.rolling) queuedAt = now
            return
        }

        if (p.classe.isRanged) {
            if (p.classe == Classe.MAGO && p.mp < 12f) return
            if (p.classe == Classe.MAGO) p.mp -= 12f
            p.doAttack(now)
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

    /** Botão de ataque apertado. Guerreiro espera para saber se é toque (combo) ou segurar (golpe forte). */
    fun attackDown() {
        val p = player ?: return
        if (!p.alive || gameOver) return
        if (p.classe == Classe.GUERREIRO) {
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
            return
        }
        if (p.charging && !p.rolling) {
            heavyAttack(p.chargeT)
        } else {
            p.charging = false
            p.chargeT = 0f
            tryAttack()
        }
    }

    private fun heavyAttack(power: Float) {
        val p = player ?: return
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

    private fun damageEnemy(e: Enemy, dmg: Float, fromX: Float, fromY: Float) {
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
        val kb = if (e.tipo == 2) 6f else 16f
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
                onGameOver?.invoke(false)
            }
            return
        }

        // segurar o ataque (guerreiro): levanta a espada; soltar = golpe forte
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
            if (blockedAt(e.x, e.y, rr)) {
                val nx = e.x
                val ny = e.y
                e.x = if (!blockedAt(nx, oy, rr)) nx else ox
                e.y = if (!blockedAt(e.x, ny, rr)) ny else oy
            }
            if (map.inSafeZone(e.x, e.y) && !map.inSafeZone(ox, oy)) { e.x = ox; e.y = oy }
            e.ground = map.heightAt(e.x, e.y)
            e.alt += (e.ground - e.alt) * (dt * 10f).coerceAtMost(1f)
            val dist = hypot(e.x - p.x, e.y - p.y)
            if (!p.rolling && p.alt - e.alt < 20f && dist < e.radius + p.radius && now - e.lastHit > e.hitCooldown) {
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
        val ax = (desiredX - camX) * CAM_SMOOTH
        val ay = (desiredY - camY) * CAM_SMOOTH
        camVX += ax * dt
        camVY += ay * dt
        camVX *= (1f - 6.2f * dt).coerceIn(0.5f, 1f)
        camVY *= (1f - 6.2f * dt).coerceIn(0.5f, 1f)
        camX += camVX * dt
        camY += camVY * dt
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
            canvas.scale(camZoom, camZoom, w * 0.5f, h * 0.55f)
        }
        if (shake > 0f) {
            val intensity = shake * 28f
            val sx = sin(time * 62f) * intensity * 0.55f + (Random.nextFloat() - 0.5f) * intensity * 0.45f
            val sy = cos(time * 51f) * intensity * 0.45f + (Random.nextFloat() - 0.5f) * intensity * 0.55f
            canvas.translate(sx, sy)
        }

        map.drawFloor(canvas, paint, camX, camY, w, h)

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
            if (p.alive && map.isWall(p.x, p.y + map.tileSize * 0.9f)) {
                val sx = p.x - camX
                val sy = p.y - camY
                val layer = canvas.saveLayerAlpha(sx - 70f, sy - 100f, sx + 70f, sy + 60f, 110)
                p.draw(canvas, paint, camX, camY)
                canvas.restoreToCount(layer)
            }
        }

        projectiles.forEach { it.draw(canvas, paint, camX, camY) }
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
