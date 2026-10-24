package com.viniciusy.masmorras

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

abstract class Entity(
    var x: Float,
    var y: Float,
    var radius: Float,
    var hp: Float,
    var maxHp: Float
) {
    var alive = true

    fun takeDamage(dmg: Float) {
        if (!alive) return
        hp -= dmg
        if (hp <= 0f) {
            hp = 0f
            alive = false
        }
    }

    abstract fun update(dt: Float)
    abstract fun draw(canvas: Canvas, paint: Paint, camX: Float, camY: Float)

    fun getBounds(): RectF = RectF(x - radius, y - radius, x + radius, y + radius)
}

class Player(
    x: Float,
    y: Float,
    val classe: Classe
) : Entity(x, y, 20f, classe.maxHp, classe.maxHp) {

    var mp = classe.maxMp
    var vx = 0f
    var vy = 0f
    var facingX = 1f
    var facingY = 0f
    var lastAttack = 0L
    var attackAnim = 0f

    fun canAttack(now: Long): Boolean = now - lastAttack >= classe.cooldownAtaque

    fun doAttack(now: Long) {
        lastAttack = now
        attackAnim = 1f
    }

    override fun update(dt: Float) {
        x += vx * dt
        y += vy * dt
        if (attackAnim > 0f) attackAnim = (attackAnim - dt * 5f).coerceAtLeast(0f)
        if (classe.maxMp > 0f && mp < classe.maxMp) {
            mp = (mp + 10f * dt).coerceAtMost(classe.maxMp)
        }
    }

    override fun draw(canvas: Canvas, paint: Paint, camX: Float, camY: Float) {
        val sx = x - camX
        val sy = y - camY

        // Sombra
        paint.color = 0x55000000
        paint.style = Paint.Style.FILL
        canvas.drawOval(sx - radius * 0.9f, sy + radius * 0.55f, sx + radius * 0.9f, sy + radius * 1.05f, paint)

        // Corpo (bloco estilo Minecraft Dungeons)
        paint.color = classe.cor
        val body = RectF(sx - radius * 0.8f, sy - radius * 0.85f, sx + radius * 0.8f, sy + radius * 0.85f)
        canvas.drawRoundRect(body, 5f, 5f, paint)

        // Destaque de luz no corpo
        paint.color = 0x33FFFFFF
        canvas.drawRoundRect(
            RectF(sx - radius * 0.7f, sy - radius * 0.8f, sx + radius * 0.15f, sy - radius * 0.1f),
            4f, 4f, paint
        )

        // Cabeça
        paint.color = 0xFFFFCC80.toInt()
        canvas.drawCircle(sx, sy - radius * 0.5f, radius * 0.42f, paint)

        // Olhos
        paint.color = 0xFF1A1A1A.toInt()
        canvas.drawCircle(sx - 5.5f, sy - radius * 0.55f, 2.8f, paint)
        canvas.drawCircle(sx + 5.5f, sy - radius * 0.55f, 2.8f, paint)
        // brilho nos olhos
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(sx - 4.5f, sy - radius * 0.58f, 1.1f, paint)
        canvas.drawCircle(sx + 6.5f, sy - radius * 0.58f, 1.1f, paint)

        // Arma / efeito de ataque
        if (attackAnim > 0f) {
            paint.color = 0xCCFFFFFF.toInt()
            paint.strokeWidth = 5f
            paint.style = Paint.Style.STROKE
            val reach = radius + 18f + attackAnim * 28f
            canvas.drawLine(sx, sy, sx + facingX * reach, sy + facingY * reach, paint)
            paint.style = Paint.Style.FILL
            // faísca no final
            paint.color = 0xAAFFEB3B.toInt()
            canvas.drawCircle(sx + facingX * reach, sy + facingY * reach, 5f + attackAnim * 4f, paint)
        }

        // Barra de vida
        val barW = radius * 2.1f
        val barH = 4.5f
        val barY = sy - radius - 11f
        paint.color = 0xFF222222.toInt()
        canvas.drawRect(sx - barW / 2, barY, sx + barW / 2, barY + barH, paint)
        paint.color = 0xFF4CAF50.toInt()
        val hpPct = (hp / maxHp).coerceIn(0f, 1f)
        canvas.drawRect(sx - barW / 2, barY, sx - barW / 2 + barW * hpPct, barY + barH, paint)
    }
}

class Enemy(
    x: Float,
    y: Float,
    val tipo: Int = 0
) : Entity(x, y, 16f, 45f + tipo * 30f, 45f + tipo * 30f) {

    var speed = 1.5f + tipo * 0.35f
    var damage = 7f + tipo * 5f
    var lastHit = 0L
    var hitCooldown = 850L

    fun update(dt: Float, player: Player) {
        if (!alive || !player.alive) return
        val dx = player.x - x
        val dy = player.y - y
        val dist = hypot(dx, dy)
        if (dist > 8f) {
            x += (dx / dist) * speed * dt * 55f
            y += (dy / dist) * speed * dt * 55f
        }
    }

    override fun update(dt: Float) {}

    override fun draw(canvas: Canvas, paint: Paint, camX: Float, camY: Float) {
        if (!alive) return
        val sx = x - camX
        val sy = y - camY

        // Sombra
        paint.color = 0x44000000
        paint.style = Paint.Style.FILL
        canvas.drawOval(sx - radius, sy + radius * 0.45f, sx + radius, sy + radius * 0.95f, paint)

        val cor = when (tipo) {
            0 -> 0xFF689F38.toInt() // slime / zumbi fraco
            1 -> 0xFF8D6E63.toInt() // esqueleto
            else -> 0xFF6A1B9A.toInt() // elite
        }
        paint.color = cor
        canvas.drawRoundRect(
            RectF(sx - radius, sy - radius, sx + radius, sy + radius),
            4f, 4f, paint
        )

        // destaque
        paint.color = 0x33FFFFFF
        canvas.drawRoundRect(
            RectF(sx - radius * 0.7f, sy - radius * 0.85f, sx, sy - radius * 0.1f),
            3f, 3f, paint
        )

        // Olhos
        paint.color = 0xFFFF1744.toInt()
        canvas.drawCircle(sx - 5f, sy - 3f, 3.2f, paint)
        canvas.drawCircle(sx + 5f, sy - 3f, 3.2f, paint)
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(sx - 4f, sy - 4f, 1.2f, paint)
        canvas.drawCircle(sx + 6f, sy - 4f, 1.2f, paint)

        // HP bar
        val barW = radius * 2.1f
        val barH = 3.5f
        val barY = sy - radius - 9f
        paint.color = 0xFF222222.toInt()
        canvas.drawRect(sx - barW / 2, barY, sx + barW / 2, barY + barH, paint)
        paint.color = 0xFFF44336.toInt()
        val pct = (hp / maxHp).coerceIn(0f, 1f)
        canvas.drawRect(sx - barW / 2, barY, sx - barW / 2 + barW * pct, barY + barH, paint)
    }

    private fun hypot(a: Float, b: Float) = kotlin.math.sqrt(a * a + b * b)
}

class Projectile(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val damage: Float,
    val color: Int,
    val fromPlayer: Boolean = true
) {
    var alive = true
    val radius = 7f
    var life = 2.2f

    fun update(dt: Float) {
        x += vx * dt
        y += vy * dt
        life -= dt
        if (life <= 0f) alive = false
    }

    fun draw(canvas: Canvas, paint: Paint, camX: Float, camY: Float) {
        if (!alive) return
        val sx = x - camX
        val sy = y - camY
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawCircle(sx, sy, radius, paint)
        paint.color = 0xAAFFFFFF.toInt()
        canvas.drawCircle(sx - 1.5f, sy - 1.5f, radius * 0.4f, paint)
    }
}
