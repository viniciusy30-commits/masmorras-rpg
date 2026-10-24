package com.viniciusy.masmorras

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var map = TileMap()
    private var player: Player? = null
    private val enemies = mutableListOf<Enemy>()
    private val projectiles = mutableListOf<Projectile>()

    private var camX = 0f
    private var camY = 0f
    private var lastTime = System.nanoTime()
    private var running = false
    private var gameOver = false
    private var victory = false

    var onGameOver: ((Boolean) -> Unit)? = null // true = vitória

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

    fun startGame(classe: Classe) {
        map = TileMap()
        val (sx, sy) = map.getPlayerSpawn()
        player = Player(sx, sy, classe)
        enemies.clear()
        projectiles.clear()
        val spawns = map.getEnemySpawns(12)
        spawns.forEachIndexed { i, (ex, ey) ->
            enemies.add(Enemy(ex, ey, if (i % 5 == 0) 1 else 0))
        }
        // um inimigo mais forte
        if (spawns.isNotEmpty()) {
            val last = spawns.last()
            enemies.add(Enemy(last.first + 40f, last.second, 2))
        }
        gameOver = false
        victory = false
        running = true
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
            p.vx = nx * p.classe.velocidade * 60f
            p.vy = ny * p.classe.velocidade * 60f
            p.facingX = nx
            p.facingY = ny
        } else {
            p.vx = 0f
            p.vy = 0f
        }
    }

    fun tryAttack() {
        val p = player ?: return
        if (!p.alive || gameOver) return
        val now = System.currentTimeMillis()
        if (!p.canAttack(now)) return

        if (p.classe.isRanged) {
            if (p.classe == Classe.MAGO && p.mp < 12f) return
            if (p.classe == Classe.MAGO) p.mp -= 12f
            p.doAttack(now)
            val speed = 280f
            val vx = p.facingX * speed
            val vy = p.facingY * speed
            val color = if (p.classe == Classe.MAGO) 0xFF42A5F5.toInt() else 0xFF8D6E63.toInt()
            projectiles.add(Projectile(p.x, p.y, vx, vy, p.classe.danoBase, color))
        } else {
            // Melee
            p.doAttack(now)
            val reach = p.classe.alcance
            enemies.forEach { e ->
                if (!e.alive) return@forEach
                val dist = hypot(e.x - p.x, e.y - p.y)
                if (dist < reach + e.radius) {
                    // verificar se está na direção aproximada
                    val dx = e.x - p.x
                    val dy = e.y - p.y
                    val dot = dx * p.facingX + dy * p.facingY
                    if (dot > 0 || dist < 40f) {
                        e.takeDamage(p.classe.danoBase)
                    }
                }
            }
        }
    }

    private fun update(dt: Float) {
        val p = player ?: return
        if (!p.alive) {
            if (!gameOver) {
                gameOver = true
                victory = false
                onGameOver?.invoke(false)
            }
            return
        }

        // Movimento com colisão simples
        val oldX = p.x
        val oldY = p.y
        p.update(dt)
        // Colisão paredes
        if (map.isWall(p.x, p.y) || map.isWall(p.x + p.radius * 0.6f, p.y) ||
            map.isWall(p.x - p.radius * 0.6f, p.y) || map.isWall(p.x, p.y + p.radius * 0.6f) ||
            map.isWall(p.x, p.y - p.radius * 0.6f)
        ) {
            p.x = oldX
            p.y = oldY
            // tenta só X ou só Y
            p.x += p.vx * dt
            if (map.isWall(p.x, p.y)) p.x = oldX
            p.y += p.vy * dt
            if (map.isWall(p.x, p.y)) p.y = oldY
        }

        // Limites do mapa
        p.x = p.x.coerceIn(p.radius, map.worldWidth() - p.radius)
        p.y = p.y.coerceIn(p.radius, map.worldHeight() - p.radius)

        // Inimigos
        val now = System.currentTimeMillis()
        enemies.forEach { e ->
            if (!e.alive) return@forEach
            e.update(dt, p)
            // colisão inimigo-parede simples
            if (map.isWall(e.x, e.y)) {
                // empurra para fora
                e.x += (p.x - e.x) * 0.05f
                e.y += (p.y - e.y) * 0.05f
            }
            // dano ao player
            val dist = hypot(e.x - p.x, e.y - p.y)
            if (dist < e.radius + p.radius && now - e.lastHit > e.hitCooldown) {
                p.takeDamage(e.damage)
                e.lastHit = now
            }
        }

        // Projéteis
        projectiles.forEach { pr ->
            if (!pr.alive) return@forEach
            pr.update(dt)
            if (map.isWall(pr.x, pr.y)) {
                pr.alive = false
                return@forEach
            }
            if (pr.fromPlayer) {
                enemies.forEach { e ->
                    if (e.alive && hypot(e.x - pr.x, e.y - pr.y) < e.radius + pr.radius) {
                        e.takeDamage(pr.damage)
                        pr.alive = false
                    }
                }
            }
        }
        projectiles.removeAll { !it.alive }
        enemies.removeAll { !it.alive }

        // Vitória
        if (enemies.isEmpty() && !gameOver) {
            gameOver = true
            victory = true
            onGameOver?.invoke(true)
        }

        // Câmera segue player
        camX = p.x - width / 2f
        camY = p.y - height / 2f
        camX = camX.coerceIn(0f, (map.worldWidth() - width).coerceAtLeast(0f))
        camY = camY.coerceIn(0f, (map.worldHeight() - height).coerceAtLeast(0f))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // fundo
        canvas.drawColor(0xFF0D0D0D.toInt())

        map.draw(canvas, paint, camX, camY, width.toFloat(), height.toFloat())

        // projéteis
        projectiles.forEach { it.draw(canvas, paint, camX, camY) }

        // inimigos
        enemies.forEach { it.draw(canvas, paint, camX, camY) }

        // player
        player?.draw(canvas, paint, camX, camY)

        // contador inimigos
        paint.color = 0xFFFFFFFF.toInt()
        paint.textSize = 28f
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Inimigos: ${enemies.size}", 20f, height - 40f, paint)
    }

    fun getPlayer(): Player? = player
}
