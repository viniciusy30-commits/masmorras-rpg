package com.viniciusy.masmorras

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Distância do centro da entidade até o chão (mesma conta usada pelos pés do herói). */
private const val FOOT = 13.2f

private fun alphaCol(col: Int, a: Float): Int =
    (((a.coerceIn(0f, 1f) * 255f).toInt()) shl 24) or (col and 0x00FFFFFF)

/**
 * Armadilha plantada pelo salto do arqueiro. Fica armada no chão (runas verdes pulsando);
 * quem pisar leva stun de 1 segundo e as garras fecham com um estalo.
 */
class Trap(val x: Float, val y: Float, val gz: Float) {
    var age = 0f
    var snapT = -1f

    val armed: Boolean get() = age > 0.45f && snapT < 0f
    val done: Boolean get() = snapT > 0.9f || age > 18f

    fun update(dt: Float) {
        age += dt
        if (snapT >= 0f) snapT += dt
    }

    fun trigger() {
        if (snapT < 0f) snapT = 0f
    }

    fun draw(c: Canvas, p: Paint, camX: Float, camY: Float, time: Float) {
        val cx = x - camX
        val cy = y - camY + FOOT - gz
        val plant = (age / 0.22f).coerceIn(0f, 1f)
        val drop = if (age < 0.22f) (1f - plant) * 26f else 0f
        val pop = if (age in 0.22f..0.42f) 1f + 0.18f * sin((age - 0.22f) / 0.2f * PI.toFloat()) else 1f
        val fade = if (snapT > 0.45f) (1f - (snapT - 0.45f) / 0.45f).coerceIn(0f, 1f) else 1f
        val open = if (snapT < 0f) 1f else (1f - snapT / 0.1f).coerceIn(0f, 1f)
        val pulse = 0.5f + 0.5f * sin(time * 5f)
        val rx = 21f * pop
        val ry = 10f * pop
        val oy = cy - drop

        p.style = Paint.Style.FILL
        // brilho verde no chão
        if (snapT < 0f && age > 0.3f) {
            p.color = alphaCol(0xFF7CFF8A.toInt(), (0.10f + 0.12f * pulse) * fade)
            c.drawOval(cx - rx * 1.5f, oy - ry * 1.5f, cx + rx * 1.5f, oy + ry * 1.5f, p)
        }
        // sombra e placa de madeira
        p.color = alphaCol(0xFF000000.toInt(), 0.35f * fade)
        c.drawOval(cx - rx * 1.05f, oy - ry * 0.7f, cx + rx * 1.05f, oy + ry * 1.2f, p)
        p.color = alphaCol(0xFF45301B.toInt(), fade)
        c.drawOval(cx - rx, oy - ry, cx + rx, oy + ry, p)
        p.color = alphaCol(0xFF6E4727.toInt(), fade)
        c.drawOval(cx - rx * 0.86f, oy - ry * 0.86f, cx + rx * 0.86f, oy + ry * 0.86f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.6f
        p.color = alphaCol(0xFFD4A94A.toInt(), fade)
        c.drawOval(cx - rx, oy - ry, cx + rx, oy + ry, p)
        p.style = Paint.Style.FILL
        // runa central
        p.color = alphaCol(if (snapT < 0f) 0xFF7CFF8A.toInt() else 0xFFFFE08A.toInt(), (0.55f + 0.4f * pulse) * fade)
        c.drawRect(cx - 2.5f, oy - 1.6f, cx + 2.5f, oy + 1.6f, p)

        // duas garras de aço com dentes: abertas quando armada, fechadas depois do estalo
        for (side in intArrayOf(-1, 1)) {
            val jx = cx + side * (rx * 0.55f) * open
            val jy = oy - ry * 0.1f - (1f - open) * 0f
            p.color = alphaCol(0xFF7C8A96.toInt(), fade)
            c.drawOval(jx - rx * 0.42f, jy - ry * 0.55f, jx + rx * 0.42f, jy + ry * 0.55f, p)
            p.color = alphaCol(0xFFB7C2CC.toInt(), fade)
            c.drawOval(jx - rx * 0.34f, jy - ry * 0.45f, jx + rx * 0.34f, jy + ry * 0.2f, p)
            // dentes apontando para o centro
            p.color = alphaCol(0xFFEAF1F6.toInt(), fade)
            val path = Path()
            for (i in 0..3) {
                val tx = jx - side * rx * 0.30f
                val ty = jy - ry * 0.45f + i * ry * 0.32f
                path.moveTo(tx, ty - ry * 0.14f)
                path.lineTo(tx - side * rx * (0.34f + 0.1f * open), ty)
                path.lineTo(tx, ty + ry * 0.14f)
                path.close()
            }
            c.drawPath(path, p)
        }
        // clarão do estalo
        if (snapT in 0f..0.2f) {
            val a = 1f - snapT / 0.2f
            p.color = alphaCol(0xFFFFFFFF.toInt(), 0.6f * a)
            c.drawOval(cx - rx * (1f + snapT * 4f), oy - ry * (1f + snapT * 4f), cx + rx * (1f + snapT * 4f), oy + ry * (1f + snapT * 4f), p)
        }
    }
}

/** Uma flecha da chuva: cai do céu até (tx,ty) e fica fincada por um instante. */
class RainArrow(val tx: Float, val ty: Float, val gz: Float) {
    var t = 0f
    var landed = false
    var stuckT = 0f
}

/** Área da Chuva de Flechas: círculo brilhante no chão e dezenas de flechas caindo. */
class RainZone(val x: Float, val y: Float, val gz: Float, val radius: Float) {
    companion object {
        const val DELAY = 0.30f     // as flechas sobem e começam a cair
        const val COUNT = 22
        const val EVERY = 0.05f
        const val FALL = 0.28f
    }

    var age = 0f
    var spawned = 0
    var spawnT = 0f
    val arrows = ArrayList<RainArrow>()

    val done: Boolean get() = age > DELAY + COUNT * EVERY + FALL + 0.8f

    fun update(dt: Float) {
        age += dt
        for (a in arrows) {
            if (!a.landed) a.t += dt else a.stuckT += dt
        }
        arrows.removeAll { it.landed && it.stuckT > 0.7f }
    }

    fun drawGround(c: Canvas, p: Paint, camX: Float, camY: Float, time: Float) {
        val cx = x - camX
        val cy = y - camY + FOOT - gz
        val grow = (age / 0.22f).coerceIn(0f, 1f)
        val fadeOut = if (age > DELAY + COUNT * EVERY + FALL) (1f - (age - (DELAY + COUNT * EVERY + FALL)) / 0.8f).coerceIn(0f, 1f) else 1f
        val rr = radius * (0.4f + 0.6f * grow)
        val ry = rr * 0.55f
        val pulse = 0.5f + 0.5f * sin(time * 9f)
        p.style = Paint.Style.FILL
        p.color = alphaCol(0xFF7CFF8A.toInt(), (0.10f + 0.07f * pulse) * fadeOut)
        c.drawOval(cx - rr, cy - ry, cx + rr, cy + ry, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        p.color = alphaCol(0xFFD6FFD9.toInt(), 0.75f * fadeOut)
        c.drawOval(cx - rr, cy - ry, cx + rr, cy + ry, p)
        p.strokeWidth = 2f
        p.color = alphaCol(0xFF7CFF8A.toInt(), 0.6f * fadeOut)
        c.drawOval(cx - rr * 0.62f, cy - ry * 0.62f, cx + rr * 0.62f, cy + ry * 0.62f, p)
        // marcas girando no aro
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 4f
        p.color = alphaCol(0xFFFFFFFF.toInt(), 0.8f * fadeOut)
        for (k in 0..5) {
            val ang = time * 1.6f + k * 1.0472f
            val px = cx + cos(ang) * rr
            val py = cy + sin(ang) * ry
            c.drawLine(px, py, px + cos(ang + 1.57f) * 7f, py + sin(ang + 1.57f) * 7f * 0.55f, p)
        }
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
    }

    fun drawArrows(c: Canvas, p: Paint, camX: Float, camY: Float) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        for (a in arrows) {
            val fx = a.tx - camX
            val fy = a.ty - camY + FOOT - a.gz
            if (!a.landed) {
                val u = (a.t / FALL).coerceIn(0f, 1f)
                val hx = fx - (1f - u) * 34f
                val hy = fy - (1f - u) * 300f
                // direção da queda (para trás = para cima e para a direita)
                val ux = 34f / hypot(34f, 300f)
                val uy = 300f / hypot(34f, 300f)
                val tx = hx - ux * 40f
                val ty = hy - uy * 40f
                p.strokeWidth = 7f
                p.color = alphaCol(0xFF7CFF8A.toInt(), 0.35f)
                c.drawLine(hx - ux * 70f, hy - uy * 70f, hx, hy, p)
                p.strokeWidth = 3f
                p.color = 0xFFCDA46A.toInt()
                c.drawLine(tx, ty, hx, hy, p)
                p.strokeWidth = 4.5f
                p.color = 0xFFEAF1F6.toInt()
                c.drawLine(hx - ux * 5f, hy - uy * 5f, hx + ux * 4f, hy + uy * 4f, p)
                p.strokeWidth = 4f
                p.color = 0xFFD64545.toInt()
                c.drawLine(tx, ty, tx + ux * 9f, ty + uy * 9f, p)
            } else {
                val k = (1f - a.stuckT / 0.7f).coerceIn(0f, 1f)
                val lean = 4f
                p.strokeWidth = 3f
                p.color = alphaCol(0xFFCDA46A.toInt(), k)
                c.drawLine(fx, fy, fx + lean, fy - 20f, p)
                p.strokeWidth = 4f
                p.color = alphaCol(0xFFD64545.toInt(), k)
                c.drawLine(fx + lean * 0.7f, fy - 15f, fx + lean, fy - 20f, p)
                p.color = alphaCol(0xFFFFFFFF.toInt(), k)
                c.drawLine(fx + lean * 0.5f, fy - 11f, fx + lean * 0.7f, fy - 15f, p)
            }
        }
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
    }
}

/** Flecha Trovão: atravessa todos os inimigos da linha, eletrocuta e salta raios entre eles. */
class ThunderBolt(var x: Float, var y: Float, val dx: Float, val dy: Float, val damage: Float) {
    var alive = true
    var travelled = 0f
    var age = 0f
    val range = 560f
    val hit = HashSet<Enemy>()

    fun update(dt: Float) {
        age += dt
        val step = 640f * dt
        x += dx * step
        y += dy * step
        travelled += step
        if (travelled >= range) alive = false
    }

    fun draw(c: Canvas, p: Paint, camX: Float, camY: Float) {
        val sx = x - camX
        val sy = y - camY - 16f
        // sombra no chão
        p.style = Paint.Style.FILL
        p.color = 0x33000000
        c.drawOval(sx - 12f, y - camY + 8f, sx + 12f, y - camY + 14f, p)
        // rastro elétrico em zigue-zague
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        val nx = -dy
        val ny = dx
        var px = sx
        var py = sy
        for (i in 1..7) {
            val d = i * 11f
            val jit = if (i == 7) 0f else (Random.nextFloat() - 0.5f) * 11f
            val qx = sx - dx * d + nx * jit
            val qy = sy - dy * d + ny * jit
            val a = 1f - i / 8f
            p.strokeWidth = 7f * a + 2f
            p.color = alphaCol(0xFFFFF27A.toInt(), 0.4f * a)
            c.drawLine(px, py, qx, qy, p)
            p.strokeWidth = 2.4f * a + 1f
            p.color = alphaCol(0xFFFFFFFF.toInt(), 0.9f * a)
            c.drawLine(px, py, qx, qy, p)
            px = qx
            py = qy
        }
        p.style = Paint.Style.FILL
        // brilho ao redor
        val pulse = 1f + 0.15f * sin(age * 40f)
        p.color = alphaCol(0xFFFFF27A.toInt(), 0.18f)
        c.drawCircle(sx, sy, 26f * pulse, p)
        p.color = alphaCol(0xFFFFF27A.toInt(), 0.35f)
        c.drawCircle(sx, sy, 15f * pulse, p)
        // flecha: haste, penas e ponta
        val ang = atan2(dy, dx) * 57.29578f
        c.save()
        c.translate(sx, sy)
        c.rotate(ang)
        p.color = 0xFFFFE27A.toInt()
        c.drawRect(-18f, -2f, 8f, 2f, p)
        p.color = 0xFFFFFFFF.toInt()
        c.drawRect(-18f, -0.8f, 8f, 0.8f, p)
        p.color = 0xFFFFF27A.toInt()
        c.drawRect(-22f, -5f, -14f, 5f, p)
        p.color = 0xFFC9F6FF.toInt()
        c.drawRect(-20f, -2.5f, -15f, 2.5f, p)
        val tip = Path()
        tip.moveTo(6f, -5.5f)
        tip.lineTo(18f, 0f)
        tip.lineTo(6f, 5.5f)
        tip.close()
        p.color = 0xFFFFFFFF.toInt()
        c.drawPath(tip, p)
        c.restore()
        p.strokeCap = Paint.Cap.BUTT
    }
}

/** Raio que salta entre dois inimigos (some rápido, cintila a cada quadro). */
class ZapFx(val x0: Float, val y0: Float, val x1: Float, val y1: Float) {
    var life = 0.28f

    fun update(dt: Float) {
        life -= dt
    }

    fun draw(c: Canvas, p: Paint, camX: Float, camY: Float) {
        if (life <= 0f) return
        val a = (life / 0.28f).coerceIn(0f, 1f)
        val ax = x0 - camX
        val ay = y0 - camY
        val bx = x1 - camX
        val by = y1 - camY
        val dx = bx - ax
        val dy = by - ay
        val len = max(1f, hypot(dx, dy))
        val nx = -dy / len
        val ny = dx / len
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        for (pass in 0..1) {
            var px = ax
            var py = ay
            for (i in 1..6) {
                val u = i / 6f
                val jit = if (i == 6) 0f else (Random.nextFloat() - 0.5f) * len * 0.25f
                val qx = ax + dx * u + nx * jit
                val qy = ay + dy * u + ny * jit
                if (pass == 0) {
                    p.strokeWidth = 8f
                    p.color = alphaCol(0xFFFFF27A.toInt(), 0.35f * a)
                } else {
                    p.strokeWidth = 2.6f
                    p.color = alphaCol(0xFFFFFFFF.toInt(), a)
                }
                c.drawLine(px, py, qx, qy, p)
                px = qx
                py = qy
            }
        }
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
    }
}

/** Estrelinhas girando sobre a cabeça de quem está atordoado. */
fun drawStunStars(c: Canvas, p: Paint, cx: Float, headY: Float, scale: Float, time: Float) {
    p.style = Paint.Style.FILL
    for (i in 0..2) {
        val ang = time * 6.5f + i * 2.0944f
        val px = cx + cos(ang) * 15f * scale
        val py = headY + sin(ang) * 5f * scale
        val z = if (sin(ang) > 0f) 1f else 0.75f
        p.color = 0xFFFFD54F.toInt()
        c.drawRect(px - 4.5f * z, py - 1.5f * z, px + 4.5f * z, py + 1.5f * z, p)
        c.drawRect(px - 1.5f * z, py - 4.5f * z, px + 1.5f * z, py + 4.5f * z, p)
        p.color = 0xFFFFFFFF.toInt()
        c.drawRect(px - 1.5f * z, py - 1.5f * z, px + 1.5f * z, py + 1.5f * z, p)
    }
}
