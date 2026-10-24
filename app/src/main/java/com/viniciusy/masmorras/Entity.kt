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
) : Entity(x, y, 22f, classe.maxHp, classe.maxHp) {

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
        if (attackAnim > 0f) attackAnim = (attackAnim - dt * 4f).coerceAtLeast(0f)
        if (classe.maxMp > 0f && mp < classe.maxMp) {
            mp = (mp + 8f * dt).coerceAtMost(classe.maxMp)
        }
    }

    override fun draw(canvas: Canvas, paint: Paint, camX: Float, camY: Float) {
        val sx = x - camX
        val sy = y - camY

        // Sombra
        paint.color = 0x44000000
        paint.style = Paint.Style.FILL
        canvas.drawOval(sx - radius, sy + radius * 0.6f, sx + radius, sy + radius * 1.1f, paint)

        // Corpo (bloco estilo Minecraft)
        paint.color = classe.cor
        val body = RectF(sx - radius * 0.85f, sy - radius * 0.9f, sx + radius * 0.85f, sy + radius * 0.9f)
        canvas.drawRoundRect(body, 6f, 6f, paint)

        // Cabeça
        paint.color = 0xFFFFCC80.toInt()
        canvas.drawCircle(sx, sy - radius * 0.55f, radius * 0.45f, paint)

        // Olhos
        paint.color = 0xFF000000.toInt()
        canvas.drawCircle(sx - 6f, sy - radius * 0.6f, 3f, paint)
        canvas.drawCircle(sx + 6f, sy - radius * 0.6f, 3f, paint)

        // Arma / efeito de ataque
        if (attackAnim > 0f) {
            paint.color = 0xAAFFFFFF.toInt()
            paint.strokeWidth = 6f
            paint.style = Paint.Style.STROKE
            val reach = radius + 20f + attackAnim * 30f
            canvas.drawLine(sx, sy, sx + facingX * reach, sy + facingY * reach, paint)
            paint.style = Paint.Style.FILL
        }

        // Barra de vida pequena
        val barW = radius * 2.2f
        val barH = 5f
        val barY = sy - radius - 12f
        paint.color = 0xFF333333.toInt()
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
) : Entity(x, y, 18f, 50f + tipo * 25f, 50f + tipo * 25f) {

    var speed = 1.6f + tipo * 0.3f
    var damage = 8f + tipo * 4f
    var lastHit = 0L
    var hitCooldown = 800L

    fun update(dt: Float, player: Player) {
        if (!alive || !player.alive) return
        val dx = player.x - x
        val dy = player.y - y
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        if (dist > 1f) {
            x += (dx / dist) * speed * dt * 60f
            y += (dy / dist) * speed * dt * 60f
        }
    }

    override fun update(dt: Float) {}

    override fun draw(canvas: Canvas, paint: Paint, camX: Float, camY: Float) {
        if (!alive) return
        val sx = x - camX
        val sy = y - camY

        paint.color = 0x44000000
        canvas.drawOval(sx - radius, sy + radius * 0.5f, sx + radius, sy + radius, paint)

        // Corpo inimigo (zumbi / skeleton estilo)
        val cor = when (tipo) {
            0 -> 0xFF6B8E23.toInt() // slime / zumbi fraco
            1 -> 0xFF8B4513.toInt() // esqueleto
            else -> 0xFF4A0080.toInt() // bosszinho
        }
        paint.color = cor
        canvas.drawRoundRect(RectF(sx - radius, sy - radius, sx + radius, sy + radius), 4f, 4f, paint)

        // Olhos vermelhos
        paint.color = 0xFFFF0000.toInt()
        canvas.drawCircle(sx - 5f, sy - 4f, 3.5f, paint)
        canvas.drawCircle(sx + 5f, sy - 4f, 3.5f, paint)

        // HP bar
        val barW = radius * 2f
        val barH = 4f
        val barY = sy - radius - 10f
        paint.color = 0xFF333333.toInt()
        canvas.drawRect(sx - barW / 2, barY, sx + barW / 2, barY + barH, paint)
        paint.color = 0xFFF44336.toInt()
        val pct = (hp / maxHp).coerceIn(0f, 1f)
        canvas.drawRect(sx - barW / 2, barY, sx - barW / 2 + barW * pct, barY + barH, paint)
    }
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
    val radius = 8f
    var life = 2.5f

    fun update(dt: Float) {
        x += vx * dt
        y += vy * dt
        life -= dt
        if (life <= 0f) alive = false
    }

    fun draw(canvas: Canvas, paint: Paint, camX: Float, camY: Float) {
        if (!alive) return
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawCircle(x - camX, y - camY, radius, paint)
        // brilho
        paint.color = 0xAAFFFFFF.toInt()
        canvas.drawCircle(x - camX, y - camY, radius * 0.4f, paint)
    }
}
