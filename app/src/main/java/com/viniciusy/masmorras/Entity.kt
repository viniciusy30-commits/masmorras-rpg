package com.viniciusy.masmorras

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.hypot
import kotlin.math.sin

abstract class Entity(
    var x: Float,
    var y: Float,
    var radius: Float,
    var hp: Float,
    var maxHp: Float
) {
    var alive = true
    var hitFlash = 0f
    var walkPhase = 0f

    fun takeDamage(dmg: Float) {
        if (!alive) return
        hp -= dmg
        hitFlash = 1f
        if (hp <= 0f) {
            hp = 0f
            alive = false
        }
    }

    abstract fun update(dt: Float)
    abstract fun draw(canvas: Canvas, paint: Paint, camX: Float, camY: Float)

    fun getBounds(): RectF = RectF(x - radius, y - radius, x + radius, y + radius)

    protected fun drawBar(canvas: Canvas, paint: Paint, cx: Float, top: Float, w: Float, pct: Float, color: Int) {
        val h = 6f
        paint.style = Paint.Style.FILL
        paint.color = 0xFF111111.toInt()
        canvas.drawRoundRect(cx - w / 2f - 1.5f, top - 1.5f, cx + w / 2f + 1.5f, top + h + 1.5f, 3f, 3f, paint)
        paint.color = 0xFF3A3A3A.toInt()
        canvas.drawRect(cx - w / 2f, top, cx + w / 2f, top + h, paint)
        paint.color = color
        canvas.drawRect(cx - w / 2f, top, cx - w / 2f + w * pct.coerceIn(0f, 1f), top + h, paint)
        paint.color = 0x44FFFFFF
        canvas.drawRect(cx - w / 2f, top, cx - w / 2f + w * pct.coerceIn(0f, 1f), top + h * 0.4f, paint)
    }
}

class Player(
    x: Float,
    y: Float,
    val classe: Classe
) : Entity(x, y, 24f, classe.maxHp, classe.maxHp) {

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
        val sp = hypot(vx, vy)
        if (sp > 1f) walkPhase += sp * dt * 0.05f
        hitFlash = (hitFlash - dt * 4f).coerceAtLeast(0f)
        if (attackAnim > 0f) attackAnim = (attackAnim - dt * (if (classe == Classe.GUERREIRO) 3f else 4f)).coerceAtLeast(0f)
        if (classe.maxMp > 0f && mp < classe.maxMp) {
            mp = (mp + 8f * dt).coerceAtMost(classe.maxMp)
        }
    }

    override fun draw(canvas: Canvas, paint: Paint, camX: Float, camY: Float) {
        val sx = x - camX
        val sy = y - camY
        val s = radius / 24f
        val feet = sy + radius * 0.55f
        val moving = hypot(vx, vy) > 1f

        // Sombra
        paint.style = Paint.Style.FILL
        paint.color = 0x33000000
        canvas.drawOval(sx - radius * 1.05f, feet - radius * 0.32f, sx + radius * 1.05f, feet + radius * 0.38f, paint)
        paint.color = 0x44000000
        canvas.drawOval(sx - radius * 0.8f, feet - radius * 0.22f, sx + radius * 0.8f, feet + radius * 0.28f, paint)

        // Anel da classe no chão (ajuda a achar o herói)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = (classe.cor and 0x00FFFFFF) or 0x99000000.toInt()
        canvas.drawOval(sx - radius * 1.1f, feet - radius * 0.36f, sx + radius * 1.1f, feet + radius * 0.42f, paint)
        paint.style = Paint.Style.FILL

        VoxelArt.flash = hitFlash
        Sprites.drawHero(canvas, paint, classe, sx, feet, s, walkPhase, moving, facingX, facingY, attackAnim)
        VoxelArt.flash = 0f

        if (!classe.isRanged) {
            Sprites.drawSlash(canvas, paint, sx, feet - 16f * s, facingX, facingY, attackAnim, classe.alcance, classe.cor)
        }

        drawBar(canvas, paint, sx, feet - 68f * s, radius * 2.2f, hp / maxHp, 0xFF4CAF50.toInt())
    }
}

class Enemy(
    x: Float,
    y: Float,
    val tipo: Int = 0
) : Entity(x, y, if (tipo == 2) 30f else 20f, 50f + tipo * 25f, 50f + tipo * 25f) {

    var speed = 1.6f + tipo * 0.3f
    var damage = 8f + tipo * 4f
    var lastHit = 0L
    var hitCooldown = 800L
    var faceX = 0f
    var faceY = 1f

    fun palette(): Int = when (tipo) {
        0 -> 0xFF6FA55A.toInt()
        1 -> 0xFFE0E0D6.toInt()
        else -> 0xFF6A1B9A.toInt()
    }

    fun update(dt: Float, player: Player) {
        hitFlash = (hitFlash - dt * 4f).coerceAtLeast(0f)
        if (!alive || !player.alive) return
        val dx = player.x - x
        val dy = player.y - y
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        if (dist > 1f) {
            faceX = dx / dist
            faceY = dy / dist
            x += (dx / dist) * speed * dt * 60f
            y += (dy / dist) * speed * dt * 60f
            walkPhase += speed * dt * 4.2f
        }
    }

    override fun update(dt: Float) {}

    override fun draw(canvas: Canvas, paint: Paint, camX: Float, camY: Float) {
        if (!alive) return
        val sx = x - camX
        val sy = y - camY
        val s = if (tipo == 2) 1.55f else 1f
        val feet = sy + radius * 0.55f

        paint.style = Paint.Style.FILL
        paint.color = 0x33000000
        canvas.drawOval(sx - radius * 1.1f, feet - radius * 0.3f, sx + radius * 1.1f, feet + radius * 0.36f, paint)
        paint.color = 0x44000000
        canvas.drawOval(sx - radius * 0.8f, feet - radius * 0.2f, sx + radius * 0.8f, feet + radius * 0.26f, paint)

        VoxelArt.flash = hitFlash
        Sprites.drawEnemy(canvas, paint, tipo, sx, feet, s, walkPhase, faceX, faceY)
        VoxelArt.flash = 0f

        val barW = if (tipo == 2) radius * 2.4f else radius * 2f
        drawBar(canvas, paint, sx, feet - 46f * s - 8f, barW, hp / maxHp, 0xFFE53935.toInt())
    }
}

class Projectile(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val damage: Float,
    val color: Int,
    val fromPlayer: Boolean = true,
    val kind: Int = 0 // 0 = orbe mágico, 1 = flecha
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
        val sx = x - camX
        val sy = y - camY - 14f
        paint.style = Paint.Style.FILL
        // sombra no chão
        paint.color = 0x33000000
        canvas.drawOval(sx - 8f, y - camY + 8f, sx + 8f, y - camY + 14f, paint)

        if (kind == 1) {
            val ang = kotlin.math.atan2(vy, vx) * 57.29578f
            canvas.save()
            canvas.translate(sx, sy)
            canvas.rotate(ang)
            paint.color = 0xFF8D6E63.toInt()
            canvas.drawRect(-14f, -1.5f, 8f, 1.5f, paint)
            paint.color = 0xFFECEFF1.toInt()
            canvas.drawRect(-14f, -3.5f, -9f, 3.5f, paint)
            paint.color = 0xFFB0BEC5.toInt()
            canvas.drawRect(8f, -3f, 15f, 3f, paint)
            paint.color = 0xFFFFFFFF.toInt()
            canvas.drawRect(11f, -1.5f, 17f, 1.5f, paint)
            canvas.restore()
        } else {
            val pulse = 1f + 0.12f * sin(VoxelArt.time * 18f)
            paint.color = VoxelArt.alpha(color, 0.18f)
            canvas.drawCircle(sx, sy, radius * 2.4f * pulse, paint)
            paint.color = VoxelArt.alpha(color, 0.45f)
            canvas.drawCircle(sx, sy, radius * 1.5f * pulse, paint)
            paint.color = color
            canvas.drawCircle(sx, sy, radius, paint)
            paint.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(sx, sy, radius * 0.45f, paint)
        }
    }
}

class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Int, val size: Float) {
    val maxLife = life

    fun update(dt: Float) {
        x += vx * dt
        y += vy * dt
        vy += 520f * dt
        vx *= 0.98f
        life -= dt
    }

    fun draw(canvas: Canvas, paint: Paint, camX: Float, camY: Float) {
        paint.style = Paint.Style.FILL
        paint.color = VoxelArt.alpha(color, life / maxLife)
        val sx = x - camX
        val sy = y - camY
        canvas.drawRect(sx - size / 2f, sy - size / 2f, sx + size / 2f, sy + size / 2f, paint)
    }
}

class DmgText(var x: Float, var y: Float, val text: String, var life: Float, val color: Int)
