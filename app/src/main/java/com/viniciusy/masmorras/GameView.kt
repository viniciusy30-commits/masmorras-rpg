package com.viniciusy.masmorras

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.hypot

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // Paints reutilizados (evita alocação por frame = menos lag)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = false
        isDither = false
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 26f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
    }

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

    // Cache de tamanho para não chamar width/height toda hora
    private var viewW = 0f
    private var viewH = 0f

    var onGameOver: ((Boolean) -> Unit)? = null

    private val loop = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.nanoTime()
            val dt = ((now - lastTime) / 1_000_000_000f).coerceIn(0.001f, 0.033f) // cap 30ms
            lastTime = now
            update(dt)
            invalidate()
            postOnAnimation(this)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewW = w.toFloat()
        viewH = h.toFloat()
    }

    fun startGame(classe: Classe) {
        map = TileMap()
        val (sx, sy) = map.getPlayerSpawn()
        player = Player(sx, sy, classe)
        enemies.clear()
        projectiles.clear()

        // Mais inimigos espalhados no mundo aberto, mas não no vilarejo
        val spawns = map.getEnemySpawns(18)
        spawns.forEachIndexed { i, (ex, ey) ->
            val tipo = when {
                i % 7 == 0 -> 2 // mais forte
                i % 3 == 0 -> 1
                else -> 0
            }
            enemies.add(Enemy(ex, ey, tipo))
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
            val speed = 300f
            val color = if (p.classe == Classe.MAGO) 0xFF42A5F5.toInt() else 0xFF8D6E63.toInt()
            projectiles.add(
                Projectile(p.x, p.y, p.facingX * speed, p.facingY * speed, p.classe.danoBase, color)
            )
        } else {
            p.doAttack(now)
            val reach = p.classe.alcance
            // só testa inimigos próximos (otimização)
            for (e in enemies) {
                if (!e.alive) continue
                val dist = hypot(e.x - p.x, e.y - p.y)
                if (dist < reach + e.radius) {
                    val dx = e.x - p.x
                    val dy = e.y - p.y
                    val dot = dx * p.facingX + dy * p.facingY
                    if (dot > 0f || dist < 45f) {
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

        val oldX = p.x
        val oldY = p.y
        p.update(dt)

        // Colisão com paredes (amostragem leve)
        if (map.isWall(p.x, p.y) ||
            map.isWall(p.x + 12f, p.y) || map.isWall(p.x - 12f, p.y) ||
            map.isWall(p.x, p.y + 12f) || map.isWall(p.x, p.y - 12f)
        ) {
            p.x = oldX
            p.y = oldY
            p.x += p.vx * dt
            if (map.isWall(p.x, p.y)) p.x = oldX
            p.y += p.vy * dt
            if (map.isWall(p.x, p.y)) p.y = oldY
        }

        p.x = p.x.coerceIn(20f, map.worldWidth() - 20f)
        p.y = p.y.coerceIn(20f, map.worldHeight() - 20f)

        val now = System.currentTimeMillis()
        // Inimigos: só atualiza os que estão relativamente perto da câmera (economia)
        val margin = 200f
        for (e in enemies) {
            if (!e.alive) continue
            val onScreen = e.x > camX - margin && e.x < camX + viewW + margin &&
                    e.y > camY - margin && e.y < camY + viewH + margin
            if (onScreen) {
                e.update(dt, p)
                if (map.isWall(e.x, e.y)) {
                    e.x += (p.x - e.x) * 0.04f
                    e.y += (p.y - e.y) * 0.04f
                }
                val dist = hypot(e.x - p.x, e.y - p.y)
                if (dist < e.radius + p.radius && now - e.lastHit > e.hitCooldown) {
                    p.takeDamage(e.damage)
                    e.lastHit = now
                }
            }
        }

        // Projéteis
        val it = projectiles.iterator()
        while (it.hasNext()) {
            val pr = it.next()
            if (!pr.alive) {
                it.remove()
                continue
            }
            pr.update(dt)
            if (map.isWall(pr.x, pr.y) || pr.life <= 0f) {
                pr.alive = false
                it.remove()
                continue
            }
            if (pr.fromPlayer) {
                for (e in enemies) {
                    if (e.alive && hypot(e.x - pr.x, e.y - pr.y) < e.radius + pr.radius) {
                        e.takeDamage(pr.damage)
                        pr.alive = false
                        break
                    }
                }
            }
        }

        // Remove mortos periodicamente (evita lista suja)
        enemies.removeAll { !it.alive }

        if (enemies.isEmpty() && !gameOver) {
            gameOver = true
            victory = true
            onGameOver?.invoke(true)
        }

        // Câmera suave centrada no player
        val targetCamX = p.x - viewW * 0.5f
        val targetCamY = p.y - viewH * 0.5f
        camX = targetCamX.coerceIn(0f, (map.worldWidth() - viewW).coerceAtLeast(0f))
        camY = targetCamY.coerceIn(0f, (map.worldHeight() - viewH).coerceAtLeast(0f))
    }

    override fun onDraw(canvas: Canvas) {
        // fundo escuro rápido
        canvas.drawColor(0xFF1A2F1A.toInt())

        if (viewW <= 0f) return

        map.draw(canvas, paint, camX, camY, viewW, viewH)

        // Só desenha o que está na tela
        for (pr in projectiles) {
            if (pr.alive) pr.draw(canvas, paint, camX, camY)
        }
        for (e in enemies) {
            if (e.alive) {
                // culling simples
                if (e.x > camX - 50 && e.x < camX + viewW + 50 &&
                    e.y > camY - 50 && e.y < camY + viewH + 50
                ) {
                    e.draw(canvas, paint, camX, camY)
                }
            }
        }
        player?.draw(canvas, paint, camX, camY)

        // HUD texto (inimigos restantes)
        textPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawText("Inimigos: ${enemies.size}", 16f, viewH - 36f, textPaint)
    }

    fun getPlayer(): Player? = player
}
