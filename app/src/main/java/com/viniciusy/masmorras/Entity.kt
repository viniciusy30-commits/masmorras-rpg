package com.viniciusy.masmorras

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
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
    var alt = 0f     // altitude absoluta (px)
    var ground = 0f  // altura do chão sob os pés (px)

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

    companion object {
        const val ROLL_TIME = 0.42f
        const val LAND_TIME = 0.22f
        const val GRAVITY = 1500f
    }

    var mp = classe.maxMp
    var vx = 0f
    var vy = 0f
    var inX = 0f
    var inY = 0f
    var facingX = 1f
    var facingY = 0f
    var lastAttack = 0L
    var attackAnim = 0f

    // pulo e rolamento
    var vz = 0f
    var rollT = 0f
    var rollDX = 0f
    var rollDY = 1f
    var lastRoll = 0L
    var landed = 0f
    var landT = 0f          // tempo de "amortecimento" depois da queda
    var hopX = 0f           // impulso para frente do pulo de escalada
    var hopY = 0f
    var airT = 0f           // tempo no ar
    var lavaT = 0f
    val trail = ArrayList<FloatArray>() // rastro do rolamento: x, y, alt, vida

    val rolling: Boolean get() = rollT > 0f
    val grounded: Boolean get() = alt <= ground + 0.6f && vz <= 0f

    fun canAttack(now: Long): Boolean = !rolling && now - lastAttack >= classe.cooldownAtaque

    fun doAttack(now: Long) {
        lastAttack = now
        attackAnim = 1f
    }

    fun jump(power: Float = 390f, hx: Float = 0f, hy: Float = 0f): Boolean {
        if (!alive || rolling || !grounded) return false
        vz = power
        alt = ground + 0.7f
        hopX = hx
        hopY = hy
        airT = 0f
        landT = 0f
        return true
    }

    fun startRoll(now: Long): Boolean {
        if (!alive || rolling || !grounded || now - lastRoll < 700L) return false
        var dx = inX
        var dy = inY
        if (hypot(dx, dy) < 0.1f) {
            dx = facingX
            dy = facingY
        }
        val l = hypot(dx, dy).coerceAtLeast(0.001f)
        rollDX = dx / l
        rollDY = dy / l
        facingX = rollDX
        facingY = rollDY
        rollT = ROLL_TIME
        lastRoll = now
        attackAnim = 0f
        return true
    }

    /** Gravidade e acompanhamento do relevo. */
    fun updateVertical(dt: Float, groundPx: Float) {
        ground = groundPx
        if (alt > groundPx + 0.01f || vz > 0f) {
            vz -= GRAVITY * dt
            alt += vz * dt
            airT += dt
            if (alt <= groundPx) {
                if (vz < -140f) {
                    landed = (-vz) / 400f
                    landT = LAND_TIME
                }
                alt = groundPx
                vz = 0f
                hopX = 0f
                hopY = 0f
            }
        } else {
            alt += (groundPx - alt) * min(1f, dt * 30f)
            if (abs(groundPx - alt) < 0.3f) alt = groundPx
            vz = 0f
        }
    }

    override fun update(dt: Float) {
        if (rollT > 0f) {
            rollT -= dt
            if (rollT < 0f) rollT = 0f
            vx = rollDX * 330f
            vy = rollDY * 330f
        } else {
            vx = inX * classe.velocidade * 60f
            vy = inY * classe.velocidade * 60f
            if (!grounded) {
                // controle no ar: um pouco mais rápido + impulso de escalada
                vx = vx * 1.12f + hopX
                vy = vy * 1.12f + hopY
                val k = (1f - dt * 1.6f).coerceAtLeast(0f)
                hopX *= k
                hopY *= k
            }
        }
        if (landT > 0f) landT = (landT - dt).coerceAtLeast(0f)
        if (lavaT > 0f) lavaT = (lavaT - dt).coerceAtLeast(0f)
        // rastro do rolamento
        if (rollT > 0f) trail.add(floatArrayOf(x, y, alt, 0.28f))
        val it2 = trail.iterator()
        while (it2.hasNext()) {
            val g = it2.next()
            g[3] -= dt
            if (g[3] <= 0f) it2.remove()
        }
        x += vx * dt
        y += vy * dt
        val sp = hypot(vx, vy)
        if (sp > 1f) walkPhase += sp * dt * 0.05f
        hitFlash = (hitFlash - dt * 4f).coerceAtLeast(0f)
        if (attackAnim > 0f) {
            attackAnim = (attackAnim - dt * (if (classe == Classe.GUERREIRO) 3f else 4f)).coerceAtLeast(0f)
        }
        if (classe.maxMp > 0f && mp < classe.maxMp) {
            mp = (mp + 8f * dt).coerceAtMost(classe.maxMp)
        }
    }

    override fun draw(canvas: Canvas, paint: Paint, camX: Float, camY: Float) {
        val sx = x - camX
        val sy = y - camY
        val s = radius / 24f
        val gy = sy + radius * 0.55f - ground
        val feet = sy + radius * 0.55f - alt
        val up = (alt - ground).coerceAtLeast(0f)
        val k = (1f - up / 90f).coerceIn(0.4f, 1f)
        val moving = hypot(vx, vy) > 1f

        // Sombra no chão (encolhe quando pula)
        paint.style = Paint.Style.FILL
        paint.color = 0x33000000
        canvas.drawOval(sx - radius * 1.05f * k, gy - radius * 0.32f * k, sx + radius * 1.05f * k, gy + radius * 0.38f * k, paint)
        paint.color = 0x44000000
        canvas.drawOval(sx - radius * 0.8f * k, gy - radius * 0.22f * k, sx + radius * 0.8f * k, gy + radius * 0.28f * k, paint)

        // Anel da classe no chão
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = (classe.cor and 0x00FFFFFF) or 0x99000000.toInt()
        canvas.drawOval(sx - radius * 1.1f * k, gy - radius * 0.36f * k, sx + radius * 1.1f * k, gy + radius * 0.42f * k, paint)
        paint.style = Paint.Style.FILL

        // rastro do rolamento (imagens residuais)
        for (g in trail) {
            val a = (g[3] / 0.28f).coerceIn(0f, 1f)
            val gx = g[0] - camX
            val gf = g[1] - camY + radius * 0.55f - g[2]
            paint.style = Paint.Style.FILL
            paint.color = VoxelArt.alpha(classe.cor, a * 0.30f)
            canvas.drawCircle(gx, gf - 15f * s, radius * 0.72f * a + 4f, paint)
            paint.color = VoxelArt.alpha(0xFFFFFFFF.toInt(), a * 0.16f)
            canvas.drawCircle(gx, gf - 15f * s, radius * 0.4f * a + 2f, paint)
        }

        VoxelArt.flash = hitFlash
        Warrior3D.rollP = if (rollT > 0f) 1f - rollT / ROLL_TIME else -1f
        Warrior3D.inAir = up > 1f
        Warrior3D.airVz = vz
        Warrior3D.crouch = if (landT > 0f) landT / LAND_TIME else 0f

        // Classes 2D: pulo com esticar/amassar e cambalhota girando o corpo todo
        val twoD = classe != Classe.GUERREIRO
        canvas.save()
        if (twoD) {
            val pivotY = feet - 20f * s
            if (rollT > 0f) {
                val u = 1f - rollT / ROLL_TIME
                val e = u * u * (3f - 2f * u)
                val dir = if (facingX >= 0f) 1f else -1f
                canvas.translate(sx, pivotY - 6f * kotlin.math.sin(u * 3.1416f))
                canvas.rotate(e * 360f * dir)
                canvas.scale(1.08f, 0.82f)
                canvas.translate(-sx, -pivotY)
            } else if (up > 1f) {
                val st = (vz / 450f).coerceIn(-1f, 1f)
                canvas.scale(1f - 0.09f * st, 1f + 0.14f * st, sx, feet)
            } else if (landT > 0f) {
                val q = landT / LAND_TIME
                canvas.scale(1f + 0.2f * q, 1f - 0.22f * q, sx, feet)
            }
        }
        Sprites.drawHero(canvas, paint, classe, sx, feet, s, walkPhase, moving, facingX, facingY, attackAnim)
        canvas.restore()
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
    var attackAnim = 0f
    var yaw = 0f
    private var yawInit = false

    fun palette(): Int = when (tipo) {
        0 -> 0xFFE8E4D4.toInt()
        1 -> 0xFFB0BEC5.toInt()
        else -> 0xFFB388FF.toInt()
    }

    fun update(dt: Float, player: Player) {
        hitFlash = (hitFlash - dt * 4f).coerceAtLeast(0f)
        if (attackAnim > 0f) attackAnim = (attackAnim - dt * 2.8f).coerceAtLeast(0f)
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
        // giro suave
        val target = atan2(faceX, faceY)
        if (!yawInit) {
            yaw = target
            yawInit = true
        } else {
            val pi = Math.PI.toFloat()
            var d = target - yaw
            while (d > pi) d -= 2f * pi
            while (d < -pi) d += 2f * pi
            yaw += d * min(1f, dt * 10f)
        }
        val now = System.currentTimeMillis()
        if (attackAnim <= 0f && dist < radius + player.radius + 22f && now - lastHit > hitCooldown - 250L) {
            attackAnim = 1f
        }
    }

    override fun update(dt: Float) {}

    override fun draw(canvas: Canvas, paint: Paint, camX: Float, camY: Float) {
        if (!alive) return
        val sx = x - camX
        val sy = y - camY
        val s = if (tipo == 2) 1.55f else 1f
        val feet = sy + radius * 0.55f - alt

        paint.style = Paint.Style.FILL
        paint.color = 0x33000000
        canvas.drawOval(sx - radius * 1.1f, feet - radius * 0.3f, sx + radius * 1.1f, feet + radius * 0.36f, paint)
        paint.color = 0x44000000
        canvas.drawOval(sx - radius * 0.8f, feet - radius * 0.2f, sx + radius * 0.8f, feet + radius * 0.26f, paint)

        VoxelArt.flash = hitFlash
        Warrior3D.drawSkeleton(canvas, paint, sx, feet, s, walkPhase, yaw, tipo, attackAnim)
        VoxelArt.flash = 0f

        val barW = if (tipo == 2) radius * 2.4f else radius * 2f
        drawBar(canvas, paint, sx, feet - 50f * s - 8f, barW, hp / maxHp, 0xFFE53935.toInt())
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


/** Poção de cura: fica em cima de torres, ilhas de lava e altares. Só dá pra pegar pulando até lá. */
class Potion(x: Float, y: Float, val h: Float) : Entity(x, y, 12f, 1f, 1f) {
    init {
        alt = h
        ground = h
    }

    override fun update(dt: Float) {}

    override fun draw(canvas: Canvas, paint: Paint, camX: Float, camY: Float) {
        if (!alive) return
        val sx = x - camX
        val feet = y - camY + 8f - alt
        val t = VoxelArt.time
        val bob = sin(t * 3.2f + x * 0.05f) * 4f
        paint.style = Paint.Style.FILL
        paint.color = 0x33000000
        canvas.drawOval(sx - 11f, feet - 3f, sx + 11f, feet + 5f, paint)
        // brilho
        paint.color = 0x22FF5252
        canvas.drawCircle(sx, feet - 20f + bob, 26f + sin(t * 5f) * 3f, paint)
        paint.color = 0x33FF8A80
        canvas.drawCircle(sx, feet - 20f + bob, 17f, paint)
        // frasco
        val by = feet - 14f + bob
        paint.color = 0xFFECEFF1.toInt()
        canvas.drawRect(sx - 3f, by - 17f, sx + 3f, by - 9f, paint)
        paint.color = 0xFF8D6E63.toInt()
        canvas.drawRect(sx - 4f, by - 20f, sx + 4f, by - 16f, paint)
        paint.color = 0xFFCFD8DC.toInt()
        canvas.drawCircle(sx, by, 11f, paint)
        paint.color = 0xFFE53935.toInt()
        canvas.drawCircle(sx, by + 1.5f, 9f, paint)
        paint.color = 0xFFFF8A80.toInt()
        canvas.drawCircle(sx - 3f, by - 2f, 3f, paint)
    }
}
