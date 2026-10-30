package com.viniciusy.masmorras

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sin

/** Desenho dos heróis e inimigos em blocos 3D (estilo Minecraft Dungeons). */
object Sprites {
    private val V = VoxelArt
    private val tmpPath = Path()
    private val tmpRect = RectF()

    private fun fillRect(c: Canvas, p: Paint, l: Float, t: Float, r: Float, b: Float, color: Int) {
        p.style = Paint.Style.FILL
        p.color = V.tint(color)
        c.drawRect(l, t, r, b, p)
    }

    private fun circle(c: Canvas, p: Paint, x: Float, y: Float, r: Float, color: Int) {
        p.style = Paint.Style.FILL
        p.color = color
        c.drawCircle(x, y, r, p)
    }

    private fun weapon(c: Canvas, x: Float, y: Float, flip: Float, deg: Float, draw: () -> Unit) {
        c.save()
        c.translate(x, y)
        c.scale(flip, 1f)
        c.rotate(deg)
        draw()
        c.restore()
    }

    // ───────────────────────── HERÓIS ─────────────────────────

    fun drawHero(
        c: Canvas, p: Paint, classe: Classe, fx: Float, fy: Float, s0: Float, phase: Float,
        moving: Boolean, faceX: Float, faceY: Float, attack: Float
    ) {
        val back = faceY < -0.55f && abs(faceX) < 0.8f
        val s = s0 * HeroLook.escala
        when (classe) {
            Classe.GUERREIRO -> Warrior3D.draw(c, p, fx, fy, s, phase, moving, faceX, faceY, attack)
            Classe.MAGO -> Mage3D.draw(c, p, fx, fy, s, phase, moving, faceX, faceY, attack)
            Classe.ARQUEIRO -> Archer3D.draw(c, p, fx, fy, s, phase, moving, faceX, faceY, attack)
            Classe.BARBARO -> Barbarian3D.draw(c, p, fx, fy, s, phase, moving, faceX, faceY, attack)
            Classe.NINJA -> Ninja3D.draw(c, p, fx, fy, s, phase, moving, faceX, faceY, attack)
            Classe.PIKACHU -> Pikachu3D.draw(c, p, fx, fy, s0, phase, moving, faceX, faceY, attack)
            Classe.ICHIGO -> Ichigo3D.draw(c, p, fx, fy, s0, phase, moving, faceX, faceY, attack)
            Classe.LUFFY -> Luffy3D.draw(c, p, fx, fy, s0, phase, moving, faceX, faceY, attack)
        }
    }

    private fun warrior(
        c: Canvas, p: Paint, fx: Float, fy: Float, s: Float, phase: Float,
        moving: Boolean, faceX: Float, back: Boolean, attack: Float
    ) {
        V.humanoid(
            c, p, fx, fy, s, phase, moving, faceX, back,
            skin = 0xFFF1C27D.toInt(), shirt = 0xFFC62828.toInt(), pants = 0xFF37474F.toInt(),
            boots = 0xFF3E2723.toInt(), hair = 0xFF5D4037.toInt(), sleeve = 0xFFB0BEC5.toInt()
        )
        val ps = V.pose
        val hx = ps.headCX
        val hb = ps.headBase
        val hw = ps.headW
        val hh = ps.headH
        val tt = ps.torsoTop

        if (!back) {
            fillRect(c, p, fx - 6f * s, tt + 1f * s, fx + 6f * s, tt + 9f * s, 0xFFCFD8DC.toInt())
            fillRect(c, p, fx - 6f * s, tt + 9f * s, fx + 6f * s, tt + 11.5f * s, 0xFF5D4037.toInt())
            fillRect(c, p, fx - 1.2f * s, tt + 9f * s, fx + 1.2f * s, tt + 11.5f * s, 0xFFFFC107.toInt())
        } else {
            fillRect(c, p, fx - 7f * s, tt + 1f * s, fx + 7f * s, tt + 14f * s, 0xFF8E0000.toInt())
        }
        // ombreiras
        V.box(c, p, fx - 10.5f * s, tt + 5f * s, 7f * s, 4.5f * s, 3f * s, 0xFFCFD8DC.toInt())
        V.box(c, p, fx + 10.5f * s, tt + 5f * s, 7f * s, 4.5f * s, 3f * s, 0xFFCFD8DC.toInt())

        // elmo
        V.box(c, p, hx, hb - hh * 0.5f, hw + 2.5f * s, hh * 0.5f + 1.5f * s, 4.5f * s,
            0xFFB0BEC5.toInt(), 0xFFECEFF1.toInt())
        if (!back) {
            fillRect(c, p, hx - hw * 0.36f, hb - hh * 0.5f - 3.2f * s, hx + hw * 0.36f,
                hb - hh * 0.5f - 1.2f * s, 0xFF263238.toInt())
        }
        V.box(c, p, hx, hb - hh - 2f * s, 3.5f * s, 5f * s, 3f * s, 0xFFD32F2F.toInt())

        // escudo
        val shx = ps.handLX - 1f * s
        V.box(c, p, shx, ps.handLY + 7f * s, 10f * s, 14f * s, 2.5f * s,
            0xFF8D6E63.toInt(), 0xFFA1887F.toInt())
        fillRect(c, p, shx - 1.2f * s, ps.handLY - 5f * s, shx + 1.2f * s, ps.handLY + 5f * s, 0xFFD32F2F.toInt())
        fillRect(c, p, shx - 3.5f * s, ps.handLY - 1.2f * s, shx + 3.5f * s, ps.handLY + 1.2f * s, 0xFFD32F2F.toInt())

        // espada
        val ang = if (attack > 0f) -75f + 175f * (1f - attack) else 14f
        weapon(c, ps.handRX, ps.handRY, ps.side, ang) {
            fillRect(c, p, -1.8f * s, -27f * s, 1.8f * s, -3f * s, 0xFFDDE4E8.toInt())
            fillRect(c, p, -0.5f * s, -27f * s, 0.7f * s, -3f * s, 0xFFFFFFFF.toInt())
            fillRect(c, p, -1f * s, -29f * s, 1f * s, -27f * s, 0xFFDDE4E8.toInt())
            fillRect(c, p, -5f * s, -4f * s, 5f * s, -2f * s, 0xFFFFC107.toInt())
            fillRect(c, p, -1.5f * s, -2f * s, 1.5f * s, 4f * s, 0xFF5D4037.toInt())
        }
    }

    private fun mage(
        c: Canvas, p: Paint, fx: Float, fy: Float, s: Float, phase: Float,
        moving: Boolean, faceX: Float, back: Boolean, attack: Float
    ) {
        V.humanoid(
            c, p, fx, fy, s, phase, moving, faceX, back,
            skin = 0xFFF1C27D.toInt(), shirt = 0xFF1E88E5.toInt(), pants = 0xFF1565C0.toInt(),
            boots = 0xFF3E2723.toInt(), hair = 0xFFBDBDBD.toInt(), sleeve = 0xFF1565C0.toInt(),
            robe = true
        )
        val ps = V.pose
        val hx = ps.headCX
        val hb = ps.headBase
        val hw = ps.headW
        val hh = ps.headH
        val tt = ps.torsoTop

        if (!back) {
            fillRect(c, p, fx - 9.5f * s, fy - 5f * s, fx + 9.5f * s, fy - 3.4f * s, 0xFFFFC107.toInt())
            fillRect(c, p, fx - 8f * s, tt + 11f * s, fx + 8f * s, tt + 13f * s, 0xFFFFC107.toInt())
            fillRect(c, p, fx - 1.5f * s, tt + 10.5f * s, fx + 1.5f * s, tt + 13.5f * s, 0xFF80D8FF.toInt())
            // barba
            fillRect(c, p, hx - hw * 0.32f, hb - hh * 0.28f, hx + hw * 0.32f, hb + 6f * s, 0xFFE0E0E0.toInt())
        }

        // chapéu de mago
        val hatY = hb - hh * 0.58f
        V.box(c, p, hx, hatY, hw + 9f * s, 3f * s, 5f * s, 0xFF0D47A1.toInt(), 0xFF1976D2.toInt())
        V.box(c, p, hx, hatY - 2.5f * s, hw * 0.85f, 6f * s, 3f * s, 0xFF1565C0.toInt())
        fillRect(c, p, hx - hw * 0.43f, hatY - 4.5f * s, hx + hw * 0.43f, hatY - 2.5f * s, 0xFFFFC107.toInt())
        V.box(c, p, hx + 1f * s, hatY - 8.5f * s, hw * 0.62f, 6f * s, 3f * s, 0xFF1565C0.toInt())
        V.box(c, p, hx + 2.2f * s, hatY - 14.5f * s, hw * 0.4f, 6f * s, 3f * s, 0xFF1565C0.toInt())
        V.box(c, p, hx + 4f * s, hatY - 20.5f * s, hw * 0.22f, 5f * s, 2.5f * s, 0xFF1565C0.toInt())

        // cajado com orbe brilhante
        val ang = 8f - 35f * attack
        weapon(c, ps.handRX, ps.handRY, ps.side, ang) {
            fillRect(c, p, -1.3f * s, -40f * s, 1.3f * s, 9f * s, 0xFF6D4C41.toInt())
            val pulse = 1f + 0.12f * sin(V.time * 6f) + attack * 0.5f
            circle(c, p, 0f, -43f * s, 10f * s * pulse, 0x2242A5F5)
            circle(c, p, 0f, -43f * s, 6.5f * s * pulse, 0x6642A5F5)
            circle(c, p, 0f, -43f * s, 4f * s, 0xFF90CAF9.toInt())
            circle(c, p, -1f * s, -44.5f * s, 1.6f * s, 0xFFFFFFFF.toInt())
        }
    }

    private fun archer(
        c: Canvas, p: Paint, fx: Float, fy: Float, s: Float, phase: Float,
        moving: Boolean, faceX: Float, faceY: Float, back: Boolean, attack: Float
    ) {
        val sd = if (faceX >= 0f) 1f else -1f
        if (!back) {
            V.box(c, p, fx - sd * 7.5f * s, fy - 15f * s, 5f * s, 16f * s, 2f * s, 0xFF6D4C41.toInt())
            fillRect(c, p, fx - sd * 8.5f * s, fy - 34f * s, fx - sd * 7.5f * s, fy - 31f * s, 0xFFEEEEEE.toInt())
            fillRect(c, p, fx - sd * 6.7f * s, fy - 33f * s, fx - sd * 5.7f * s, fy - 31f * s, 0xFFD32F2F.toInt())
        }
        V.humanoid(
            c, p, fx, fy, s, phase, moving, faceX, back,
            skin = 0xFFF1C27D.toInt(), shirt = 0xFF388E3C.toInt(), pants = 0xFF5D4037.toInt(),
            boots = 0xFF3E2723.toInt(), hair = 0xFF1B5E20.toInt(), sleeve = 0xFF2E7D32.toInt()
        )
        val ps = V.pose
        val hx = ps.headCX
        val hb = ps.headBase
        val hw = ps.headW
        val hh = ps.headH
        val tt = ps.torsoTop

        fillRect(c, p, fx - 8f * s, tt + 11f * s, fx + 8f * s, tt + 13f * s, 0xFF5D4037.toInt())
        // capuz
        if (back) {
            V.box(c, p, hx, hb + 0.5f * s, hw + 3f * s, hh + 1.5f * s, 5f * s,
                0xFF2E7D32.toInt(), 0xFF43A047.toInt())
        } else {
            V.box(c, p, hx, hb - hh * 0.5f, hw + 3f * s, hh * 0.5f + 1.5f * s, 5f * s,
                0xFF2E7D32.toInt(), 0xFF43A047.toInt())
            fillRect(c, p, hx - hw / 2f - 1.5f * s, hb - hh * 0.5f, hx - hw / 2f + 1.5f * s, hb, 0xFF2E7D32.toInt())
            fillRect(c, p, hx + hw / 2f - 1.5f * s, hb - hh * 0.5f, hx + hw / 2f + 1.5f * s, hb, 0xFF2E7D32.toInt())
            fillRect(c, p, hx - hw / 2f, hb - hh * 0.5f, hx + hw / 2f, hb - hh * 0.5f + 1.8f * s, 0xFF1B5E20.toInt())
        }

        // arco
        val aim = atan2(faceY, faceX) * 57.29578f
        val hxp = if (sd > 0f) ps.handRX else ps.handLX
        val hyp = if (sd > 0f) ps.handRY else ps.handLY
        drawBow(c, p, hxp + faceX * 7f * s, hyp + faceY * 7f * s - 2f * s, aim, s,
            5f * s * (1f - attack), attack < 0.6f, 0xFF8D6E63.toInt())
    }

    private fun barbarian(
        c: Canvas, p: Paint, fx: Float, fy: Float, s: Float, phase: Float,
        moving: Boolean, faceX: Float, back: Boolean, attack: Float
    ) {
        V.humanoid(
            c, p, fx, fy, s, phase, moving, faceX, back,
            skin = 0xFFD7A86E.toInt(), shirt = 0xFFD7A86E.toInt(), pants = 0xFF6D4C41.toInt(),
            boots = 0xFF4E342E.toInt(), hair = 0xFFE65100.toInt(), sleeve = 0xFFD7A86E.toInt(),
            torsoW = 18f, armW = 6f
        )
        val ps = V.pose
        val hx = ps.headCX
        val hb = ps.headBase
        val hw = ps.headW
        val hh = ps.headH
        val tt = ps.torsoTop

        if (!back) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2.6f * s
            p.color = V.tint(0xFF5D4037.toInt())
            c.drawLine(fx - 8f * s, tt + 1f * s, fx + 7f * s, tt + 13f * s, p)
            p.style = Paint.Style.FILL
            fillRect(c, p, fx - 0.6f * s, tt + 2f * s, fx + 0.6f * s, tt + 9f * s, 0x33000000)
        }
        fillRect(c, p, fx - 9f * s, tt + 12f * s, fx + 9f * s, tt + 15f * s, 0xFF5D4037.toInt())
        V.box(c, p, fx - 12f * s, tt + 5f * s, 9f * s, 5.5f * s, 3f * s, 0xFF8D6E63.toInt())
        V.box(c, p, fx + 12f * s, tt + 5f * s, 9f * s, 5.5f * s, 3f * s, 0xFF8D6E63.toInt())

        if (!back) V.box(c, p, hx, hb + 5f * s, hw * 0.9f, 8f * s, 3f * s, 0xFFBF360C.toInt())

        // elmo com chifres
        V.box(c, p, hx, hb - hh * 0.5f, hw + 2.5f * s, hh * 0.5f + 1.5f * s, 4.5f * s,
            0xFF78909C.toInt(), 0xFFB0BEC5.toInt())
        val hy = hb - hh - 1f * s
        for (k in 0..1) {
            val sg = if (k == 0) -1f else 1f
            V.box(c, p, hx + sg * (hw / 2f + 1.5f * s), hy + 5f * s, 3.6f * s, 5f * s, 2f * s, 0xFFF5F5DC.toInt())
            V.box(c, p, hx + sg * (hw / 2f + 3.8f * s), hy, 3.2f * s, 5f * s, 2f * s, 0xFFF5F5DC.toInt())
            V.box(c, p, hx + sg * (hw / 2f + 5.6f * s), hy - 4.5f * s, 2.6f * s, 4.5f * s, 2f * s, 0xFFFFFFFF.toInt())
        }

        // machado de duas lâminas
        val ang = if (attack > 0f) -95f + 215f * (1f - attack) else 10f
        weapon(c, ps.handRX, ps.handRY, ps.side, ang) {
            fillRect(c, p, -1.6f * s, -34f * s, 1.6f * s, 8f * s, 0xFF6D4C41.toInt())
            p.style = Paint.Style.FILL
            p.color = V.tint(0xFFCFD8DC.toInt())
            tmpPath.reset()
            tmpPath.moveTo(1.6f * s, -33f * s)
            tmpPath.lineTo(12f * s, -38f * s)
            tmpPath.lineTo(12f * s, -16f * s)
            tmpPath.lineTo(1.6f * s, -21f * s)
            tmpPath.close()
            c.drawPath(tmpPath, p)
            tmpPath.reset()
            tmpPath.moveTo(-1.6f * s, -33f * s)
            tmpPath.lineTo(-12f * s, -38f * s)
            tmpPath.lineTo(-12f * s, -16f * s)
            tmpPath.lineTo(-1.6f * s, -21f * s)
            tmpPath.close()
            c.drawPath(tmpPath, p)
            fillRect(c, p, 5f * s, -35f * s, 6.4f * s, -18.5f * s, 0xFFFFFFFF.toInt())
            fillRect(c, p, -6.4f * s, -35f * s, -5f * s, -18.5f * s, 0xFFFFFFFF.toInt())
        }
    }

    // ───────────────────────── INIMIGOS ─────────────────────────

    fun drawEnemy(
        c: Canvas, p: Paint, tipo: Int, fx: Float, fy: Float, s: Float, phase: Float,
        faceX: Float, faceY: Float
    ) {
        val back = faceY < -0.55f && abs(faceX) < 0.8f
        when (tipo) {
            0 -> zombie(c, p, fx, fy, s, phase, faceX, back)
            1 -> skeleton(c, p, fx, fy, s, phase, faceX, faceY, back)
            else -> boss(c, p, fx, fy, s, phase, faceX, back)
        }
    }

    private fun zombie(c: Canvas, p: Paint, fx: Float, fy: Float, s: Float, phase: Float, faceX: Float, back: Boolean) {
        V.humanoid(
            c, p, fx, fy, s, phase, true, faceX, back,
            skin = 0xFF6FA55A.toInt(), shirt = 0xFF2F7DA6.toInt(), pants = 0xFF3F3F8C.toInt(),
            boots = 0xFF2B2B2B.toInt(), hair = 0xFF3E5E2E.toInt(), sleeve = 0xFF6FA55A.toInt(),
            armsUp = true, eyeColor = 0xFF111111.toInt(), eyeSize = 1.2f
        )
        val tt = V.pose.torsoTop
        if (!back) {
            fillRect(c, p, fx - 6f * s, tt + 9f * s, fx - 2f * s, tt + 15f * s, 0xFF1F5A7A.toInt())
            fillRect(c, p, fx + 3f * s, tt + 11f * s, fx + 7f * s, tt + 15f * s, 0xFF1F5A7A.toInt())
        }
    }

    private fun skeleton(
        c: Canvas, p: Paint, fx: Float, fy: Float, s: Float, phase: Float,
        faceX: Float, faceY: Float, back: Boolean
    ) {
        V.humanoid(
            c, p, fx, fy, s, phase, true, faceX, back,
            skin = 0xFFE0E0D6.toInt(), shirt = 0xFFD6D6CB.toInt(), pants = 0xFFCFCFC4.toInt(),
            boots = 0xFFBDBDB0.toInt(), hair = 0xFFE0E0D6.toInt(), sleeve = 0xFFE0E0D6.toInt(),
            torsoW = 11f, armW = 3.6f, legW = 4.6f, headW = 13f,
            eyeColor = 0xFF1A1A1A.toInt(), eyeSize = 1.7f
        )
        val ps = V.pose
        if (!back) {
            for (k in 0..2) {
                val y0 = ps.torsoTop + 3f * s + k * 3.8f * s
                fillRect(c, p, fx - 4.5f * s, y0, fx + 4.5f * s, y0 + 1.2f * s, 0xFF8D8D80.toInt())
            }
        }
        val aim = atan2(faceY, faceX) * 57.29578f
        val sd = if (faceX >= 0f) 1f else -1f
        val hxp = if (sd > 0f) ps.handRX else ps.handLX
        val hyp = if (sd > 0f) ps.handRY else ps.handLY
        drawBow(c, p, hxp + faceX * 6f * s, hyp + faceY * 6f * s - 2f * s, aim, s, 2f * s, false, 0xFF4E342E.toInt())
    }

    private fun boss(c: Canvas, p: Paint, fx: Float, fy: Float, s: Float, phase: Float, faceX: Float, back: Boolean) {
        // aura
        val pulse = 1f + 0.08f * sin(V.time * 4f)
        p.style = Paint.Style.FILL
        p.color = 0x226A1B9A
        c.drawOval(fx - 17f * s * pulse, fy - 5f * s, fx + 17f * s * pulse, fy + 6f * s, p)
        // capa
        if (!back) V.box(c, p, fx, fy - 8f * s, 22f * s, 30f * s, 3f * s, 0xFF8E0000.toInt())
        V.humanoid(
            c, p, fx, fy, s, phase, true, faceX, back,
            skin = 0xFF8D6E63.toInt(), shirt = 0xFF4A148C.toInt(), pants = 0xFF311B92.toInt(),
            boots = 0xFF212121.toInt(), hair = 0xFF212121.toInt(), sleeve = 0xFF6A1B9A.toInt(),
            torsoW = 20f, armW = 7f, legW = 8f, headW = 15f,
            eyeColor = 0xFFFF1744.toInt(), eyeSize = 1.3f
        )
        val ps = V.pose
        val hx = ps.headCX
        val hb = ps.headBase
        val hw = ps.headW
        val hh = ps.headH
        val tt = ps.torsoTop

        if (!back) {
            fillRect(c, p, fx - 7f * s, tt + 2f * s, fx + 7f * s, tt + 4f * s, 0xFFFFC107.toInt())
            fillRect(c, p, fx - 1f * s, tt + 2f * s, fx + 1f * s, tt + 14f * s, 0xFFFFC107.toInt())
            circle(c, p, fx, tt + 8f * s, 2.2f * s, 0xFFFF1744.toInt())
        }
        // ombreiras com espinhos
        for (k in 0..1) {
            val sg = if (k == 0) -1f else 1f
            V.box(c, p, fx + sg * 13.5f * s, tt + 6f * s, 10f * s, 6f * s, 3.5f * s, 0xFF212121.toInt())
            V.box(c, p, fx + sg * 14.5f * s, tt, 3f * s, 5f * s, 2f * s, 0xFFFFC107.toInt())
        }
        // elmo com chifres dourados
        V.box(c, p, hx, hb - hh * 0.5f, hw + 3f * s, hh * 0.5f + 2f * s, 5f * s,
            0xFF263238.toInt(), 0xFF455A64.toInt())
        val hy = hb - hh - 1f * s
        for (k in 0..1) {
            val sg = if (k == 0) -1f else 1f
            V.box(c, p, hx + sg * (hw / 2f + 2f * s), hy + 5f * s, 4f * s, 6f * s, 2.5f * s, 0xFFFFC107.toInt())
            V.box(c, p, hx + sg * (hw / 2f + 4.5f * s), hy - 1f * s, 3.6f * s, 6f * s, 2.5f * s, 0xFFFFC107.toInt())
            V.box(c, p, hx + sg * (hw / 2f + 6.5f * s), hy - 6.5f * s, 3f * s, 5.5f * s, 2.5f * s, 0xFFFFE082.toInt())
        }
        if (!back) {
            val sh = if (faceX > 0.3f) 1.4f * s else if (faceX < -0.3f) -1.4f * s else 0f
            val ey = hb - hh * 0.45f
            circle(c, p, hx - 3.4f * s + sh, ey, 4f * s, 0x44FF1744)
            circle(c, p, hx + 3.4f * s + sh, ey, 4f * s, 0x44FF1744)
        }
        // espada gigante
        val ang = 10f + sin(V.time * 3f) * 6f
        weapon(c, ps.handRX, ps.handRY, ps.side, ang) {
            fillRect(c, p, -2.6f * s, -34f * s, 2.6f * s, -4f * s, 0xFF607D8B.toInt())
            fillRect(c, p, -0.8f * s, -34f * s, 0.8f * s, -4f * s, 0xFFB388FF.toInt())
            fillRect(c, p, -1.5f * s, -37f * s, 1.5f * s, -34f * s, 0xFF607D8B.toInt())
            fillRect(c, p, -7f * s, -5f * s, 7f * s, -2.5f * s, 0xFFFFC107.toInt())
            fillRect(c, p, -2f * s, -2.5f * s, 2f * s, 5f * s, 0xFF3E2723.toInt())
        }
    }

    // ───────────────────────── EXTRAS ─────────────────────────

    private fun drawBow(
        c: Canvas, p: Paint, x: Float, y: Float, aimDeg: Float, s: Float,
        pull: Float, arrow: Boolean, color: Int
    ) {
        val sw = s.coerceAtLeast(0.8f)
        c.save()
        c.translate(x, y)
        c.rotate(aimDeg)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 2.8f * sw
        p.color = V.tint(color)
        tmpRect.set(-5f * s, -15f * s, 7f * s, 15f * s)
        c.drawArc(tmpRect, -90f, 180f, false, p)
        p.strokeWidth = 1f * sw
        p.color = 0xFFEEEEEE.toInt()
        tmpPath.reset()
        tmpPath.moveTo(1f * s, -15f * s)
        tmpPath.lineTo(1f * s - pull, 0f)
        tmpPath.lineTo(1f * s, 15f * s)
        c.drawPath(tmpPath, p)
        if (arrow) {
            p.strokeWidth = 1.8f * sw
            p.color = 0xFF795548.toInt()
            c.drawLine(1f * s - pull, 0f, 16f * s, 0f, p)
            p.style = Paint.Style.FILL
            p.color = 0xFFB0BEC5.toInt()
            tmpPath.reset()
            tmpPath.moveTo(16f * s, -2.4f * s)
            tmpPath.lineTo(22f * s, 0f)
            tmpPath.lineTo(16f * s, 2.4f * s)
            tmpPath.close()
            c.drawPath(tmpPath, p)
        }
        p.style = Paint.Style.FILL
        p.strokeCap = Paint.Cap.BUTT
        c.restore()
    }

    /** Arco de corte do golpe corpo a corpo. */
    fun drawSlash(
        c: Canvas, p: Paint, cx: Float, cy: Float, faceX: Float, faceY: Float,
        attack: Float, reach: Float, color: Int
    ) {
        if (attack <= 0f) return
        val a = attack.coerceIn(0f, 1f)
        val ang = atan2(faceY, faceX) * 57.29578f
        tmpRect.set(cx - reach * 0.9f, cy - reach * 0.9f, cx + reach * 0.9f, cy + reach * 0.9f)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 7f * a + 2f
        p.color = V.alpha(color, a * 0.22f)
        c.drawArc(tmpRect, ang - 65f, 130f, false, p)
        p.strokeWidth = 2.5f * a + 1f
        p.color = V.alpha(0xFFFFFFFF.toInt(), a * 0.5f)
        c.drawArc(tmpRect, ang - 65f + (1f - a) * 30f, 130f - (1f - a) * 30f, false, p)
        p.style = Paint.Style.FILL
        p.strokeCap = Paint.Cap.BUTT
    }
}
