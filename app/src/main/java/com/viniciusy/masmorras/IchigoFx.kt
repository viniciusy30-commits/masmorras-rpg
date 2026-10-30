package com.viniciusy.masmorras

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Cores e números estáveis compartilhados pelos efeitos do Ichigo. */
private object IchiPal {
    const val DARK = 0xFF1A0610.toInt()
    const val RED_D = 0xFF720A1B.toInt()
    const val RED = 0xFFC8102E.toInt()
    const val RED_L = 0xFFFF4B5C.toInt()
    const val HOT = 0xFFFFB3A8.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val BONE = 0xFFF2EFE8.toInt()


    fun alpha(c: Int, a: Float): Int = (((a.coerceIn(0f, 1f) * 255f).toInt()) shl 24) or (c and 0x00FFFFFF)

    /** Número pseudo-aleatório estável (0..1) para a semente [i]. */
    fun rn(i: Int): Float {
        val v = sin(i * 12.9898f + 78.233f) * 43758.547f
        return v - floor(v)
    }
}

/**
 * Pincéis de luz: brilhos radiais, feixes com perfil suave, chamas curvas, estrelas de brilho e luas com gradiente.
 * Tudo usa gradientes e mistura aditiva (nada de formas chapadas), então os efeitos ganham volume e luz de verdade.
 */
internal object Glow {
    private val ADD = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val radCache = HashMap<Int, RadialGradient>()
    private val flameCache = HashMap<Int, LinearGradient>()
    private val profCache = HashMap<Long, LinearGradient>()
    private val moonCache = HashMap<Long, LinearGradient>()
    private val mat = Matrix()
    private val pth = Path()
    private val rct = RectF()

    private val unitFlame = Path().apply {
        moveTo(0f, -1f)
        cubicTo(0.30f, -0.95f, 0.62f, -0.50f, 1f, 0f)
        cubicTo(0.62f, 0.50f, 0.30f, 0.95f, 0f, 1f)
        close()
    }
    private val unitStar = Path().apply {
        moveTo(0f, -1f)

        quadTo(0.07f, -0.07f, 1f, 0f)
        quadTo(0.07f, 0.07f, 0f, 1f)
        quadTo(-0.07f, 0.07f, -1f, 0f)
        quadTo(-0.07f, -0.07f, 0f, -1f)
        close()
    }

    private fun a255(a: Float): Int = (a.coerceIn(0f, 1f) * 255f).toInt()
    private fun deg(r: Float): Float = r * 57.29578f

    private fun rad(color: Int): RadialGradient {
        val rgb = color and 0x00FFFFFF
        var g = radCache[rgb]
        if (g == null) {
            g = RadialGradient(
                0f, 0f, 1f,
                intArrayOf(
                    (0xFF shl 24) or rgb, (0xC8 shl 24) or rgb, (0x70 shl 24) or rgb,
                    (0x30 shl 24) or rgb, (0x10 shl 24) or rgb, rgb
                ),
                floatArrayOf(0f, 0.16f, 0.38f, 0.62f, 0.84f, 1f), Shader.TileMode.CLAMP
            )
            radCache[rgb] = g
        }
        return g
    }

    private fun flameShader(color: Int): LinearGradient {
        val rgb = color and 0x00FFFFFF
        var g = flameCache[rgb]
        if (g == null) {
            g = LinearGradient(

                0f, 0f, 1f, 0f,
                intArrayOf((0xF0 shl 24) or rgb, (0xA8 shl 24) or rgb, (0x40 shl 24) or rgb, rgb),
                floatArrayOf(0f, 0.38f, 0.78f, 1f), Shader.TileMode.CLAMP
            )
            flameCache[rgb] = g
        }
        return g
    }

    private fun profile(core: Int, mid: Int, outer: Int): LinearGradient {
        val key = (mid.toLong() shl 32) xor (outer.toLong() and 0xFFFFFFFFL) xor (core.toLong() * 0x9E3779B1L)
        var g = profCache[key]
        if (g == null) {
            val o = outer and 0x00FFFFFF
            val m = mid and 0x00FFFFFF
            val k = core and 0x00FFFFFF
            g = LinearGradient(
                0f, -1f, 0f, 1f,
                intArrayOf(
                    o, (0x50 shl 24) or o, (0xC0 shl 24) or m, (0xF2 shl 24) or m, (0xFF shl 24) or k,
                    (0xF2 shl 24) or m, (0xC0 shl 24) or m, (0x50 shl 24) or o, o
                ),
                floatArrayOf(0f, 0.12f, 0.30f, 0.42f, 0.5f, 0.58f, 0.70f, 0.88f, 1f), Shader.TileMode.CLAMP
            )
            profCache[key] = g
        }
        return g
    }

    private fun moonShader(dark: Int, mid: Int, hot: Int): LinearGradient {
        val key = (mid.toLong() shl 32) xor (hot.toLong() and 0xFFFFFFFFL) xor (dark.toLong() * 0x9E3779B1L)
        var g = moonCache[key]

        if (g == null) {
            g = LinearGradient(
                -0.35f, 0f, 1.05f, 0f,
                intArrayOf(
                    (0xFF shl 24) or (dark and 0xFFFFFF), (0xFF shl 24) or (dark and 0xFFFFFF),
                    (0xFF shl 24) or (mid and 0xFFFFFF), (0xFF shl 24) or (hot and 0xFFFFFF),
                    (0xFF shl 24) or 0xFFFFFF
                ),
                floatArrayOf(0f, 0.30f, 0.62f, 0.86f, 1f), Shader.TileMode.CLAMP
            )
            moonCache[key] = g
        }
        return g
    }

    private fun reset(p: Paint) {
        p.shader = null
        p.xfermode = null
        p.style = Paint.Style.FILL
    }

    /** Brilho radial macio (elipse de raios [rx], [ry]). */
    fun glow(c: Canvas, p: Paint, x: Float, y: Float, rx: Float, ry: Float, color: Int, a: Float, add: Boolean = true) {
        if (a <= 0.004f || rx <= 0.2f || ry <= 0.2f) return
        p.style = Paint.Style.FILL
        p.shader = rad(color)
        p.xfermode = if (add) ADD else null
        p.color = Color.WHITE
        p.alpha = a255(a)
        c.save()
        c.translate(x, y)
        c.scale(rx, ry)

        c.drawCircle(0f, 0f, 1f, p)
        c.restore()
        reset(p)
    }

    fun glow(c: Canvas, p: Paint, x: Float, y: Float, r: Float, color: Int, a: Float, add: Boolean = true): Unit =
        glow(c, p, x, y, r, r, color, a, add)

    /** Feixe de energia com perfil suave: borda escura, corpo colorido e miolo quente, com as pontas arredondadas por brilho. */
    fun beam(
        c: Canvas, p: Paint, x0: Float, y0: Float, x1: Float, y1: Float, hw: Float,
        core: Int, mid: Int, outer: Int, a: Float, add: Boolean = true, caps: Boolean = true
    ) {
        if (a <= 0.004f || hw <= 0.2f) return
        val dx = x1 - x0
        val dy = y1 - y0
        val len = hypot(dx, dy)
        if (len < 0.5f) return
        p.style = Paint.Style.FILL
        p.shader = profile(core, mid, outer)
        p.xfermode = if (add) ADD else null
        p.color = Color.WHITE
        p.alpha = a255(a)
        c.save()
        c.translate(x0, y0)
        c.rotate(deg(atan2(dy, dx)))
        c.scale(1f, hw)
        c.drawRect(0f, -1f, len, 1f, p)
        c.restore()
        reset(p)
        if (caps) {
            glow(c, p, x0, y0, hw * 1.05f, mid, a * 0.75f, add)

            glow(c, p, x1, y1, hw * 1.05f, mid, a * 0.75f, add)
        }
    }

    /** Língua de chama curva que nasce em (x, y) e aponta para [ang] (rad); [sway] entorta a ponta. */
    fun flame(c: Canvas, p: Paint, x: Float, y: Float, ang: Float, len: Float, w: Float, sway: Float, color: Int, a: Float, add: Boolean = true) {
        if (a <= 0.004f || len <= 0.4f) return
        p.style = Paint.Style.FILL
        p.shader = flameShader(color)
        p.xfermode = if (add) ADD else null
        p.color = Color.WHITE
        p.alpha = a255(a)
        c.save()
        c.translate(x, y)
        c.rotate(deg(ang))
        c.skew(0f, sway)
        c.scale(len, w)
        c.drawPath(unitFlame, p)
        c.restore()
        reset(p)
    }

    /** Estrela de brilho de quatro pontas (reflexo de lente). */
    fun star(c: Canvas, p: Paint, x: Float, y: Float, r: Float, rot: Float, color: Int, a: Float) {
        if (a <= 0.004f || r <= 0.3f) return
        p.style = Paint.Style.FILL
        p.xfermode = ADD
        p.color = IchiPal.alpha(color, a)
        c.save()
        c.translate(x, y)
        c.rotate(deg(rot))
        c.scale(r, r)

        c.drawPath(unitStar, p)
        c.rotate(45f)
        c.scale(0.55f, 0.55f)
        c.drawPath(unitStar, p)
        c.restore()
        reset(p)
    }

    /** Brasa: núcleo quente com halo. */
    fun ember(c: Canvas, p: Paint, x: Float, y: Float, r: Float, color: Int, a: Float) {
        glow(c, p, x, y, r * 2.6f, color, a * 0.55f)
        glow(c, p, x, y, r * 1.1f, IchiPal.WHITE, a * 0.9f)
    }

    /**
     * Lua de energia: a convexidade aponta para [ang]. O preenchimento tem gradiente do escuro (lado de dentro) ao
     * incandescente (gume), com um brilho aditivo fino correndo pelo gume.
     */
    fun moon(
        c: Canvas, p: Paint, cx: Float, cy: Float, r: Float, ang: Float, sweep: Float, thick: Float,
        dark: Int, mid: Int, hot: Int, a: Float, rim: Boolean = true
    ) {
        if (a <= 0.004f || r <= 0.5f) return
        val sq = 0.78f
        val start = deg(ang) - sweep / 2f
        pth.reset()
        rct.set(cx - r, cy - r * sq, cx + r, cy + r * sq)
        pth.arcTo(rct, start, sweep, true)
        val sx = -cos(ang) * r * thick
        val sy = -sin(ang) * r * thick * sq
        rct.set(cx - r * 0.94f + sx, cy - r * sq * 0.94f + sy, cx + r * 0.94f + sx, cy + r * sq * 0.94f + sy)
        pth.arcTo(rct, start + sweep, -sweep, false)

        pth.close()
        val sh = moonShader(dark, mid, hot)
        mat.setScale(r, r)
        mat.postRotate(deg(ang))
        mat.postTranslate(cx, cy)
        sh.setLocalMatrix(mat)
        p.style = Paint.Style.FILL
        p.shader = sh
        p.xfermode = null
        p.color = Color.WHITE
        p.alpha = a255(a)
        c.drawPath(pth, p)
        reset(p)
        if (rim) {
            // brilho no gume externo
            pth.reset()
            rct.set(cx - r, cy - r * sq, cx + r, cy + r * sq)
            pth.arcTo(rct, start + sweep * 0.06f, sweep * 0.88f, true)
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.xfermode = ADD
            p.color = IchiPal.alpha(hot, a * 0.20f)
            p.strokeWidth = r * 0.16f
            c.drawPath(pth, p)
            p.color = IchiPal.alpha(IchiPal.WHITE, a * 0.55f)
            p.strokeWidth = max(1.2f, r * 0.045f)
            c.drawPath(pth, p)
            p.strokeCap = Paint.Cap.BUTT
            reset(p)
        }
    }


    /** Anel elíptico no chão com borda luminosa macia. */
    fun ring(c: Canvas, p: Paint, x: Float, y: Float, rx: Float, ry: Float, w: Float, color: Int, hot: Int, a: Float) {
        if (a <= 0.004f || rx < 1f) return
        p.style = Paint.Style.STROKE
        p.xfermode = ADD
        rct.set(x - rx, y - ry, x + rx, y + ry)
        p.strokeWidth = w * 3.2f
        p.color = IchiPal.alpha(color, a * 0.16f)
        c.drawOval(rct, p)
        p.strokeWidth = w * 1.8f
        p.color = IchiPal.alpha(color, a * 0.38f)
        c.drawOval(rct, p)
        p.strokeWidth = w
        p.color = IchiPal.alpha(hot, a * 0.85f)
        c.drawOval(rct, p)
        p.strokeWidth = max(1f, w * 0.35f)
        p.color = IchiPal.alpha(IchiPal.WHITE, a * 0.9f)
        c.drawOval(rct, p)
        reset(p)
    }

    /** Raio em zigue-zague com brilho (contorno largo aditivo + fio branco). */
    fun bolt(c: Canvas, p: Paint, x0: Float, y0: Float, x1: Float, y1: Float, amp: Float, seed: Int, w: Float, color: Int, a: Float) {
        val dx = x1 - x0
        val dy = y1 - y0
        val len = max(1f, hypot(dx, dy))
        val nx = -dy / len
        val ny = dx / len
        pth.reset()
        pth.moveTo(x0, y0)
        for (i in 1..7) {
            val u = i / 7f

            val j = if (i == 7) 0f else (IchiPal.rn(seed + i * 5) - 0.5f) * amp
            pth.lineTo(x0 + dx * u + nx * j, y0 + dy * u + ny * j)
        }
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.xfermode = ADD
        p.strokeWidth = w * 4f
        p.color = IchiPal.alpha(color, a * 0.20f)
        c.drawPath(pth, p)
        p.strokeWidth = w * 2f
        p.color = IchiPal.alpha(color, a * 0.55f)
        c.drawPath(pth, p)
        p.strokeWidth = max(1f, w * 0.7f)
        p.color = IchiPal.alpha(IchiPal.WHITE, a * 0.95f)
        c.drawPath(pth, p)
        p.strokeCap = Paint.Cap.BUTT
        reset(p)
    }

    /** Reta fina e nítida (risco de corte). */
    fun slash(c: Canvas, p: Paint, x0: Float, y0: Float, x1: Float, y1: Float, w: Float, color: Int, a: Float) {
        beam(c, p, x0, y0, x1, y1, w * 2.4f, IchiPal.WHITE, color, color, a * 0.8f, true, false)
        beam(c, p, x0, y0, x1, y1, max(1f, w * 0.55f), IchiPal.WHITE, IchiPal.WHITE, IchiPal.WHITE, a, true, false)
    }

    /** Fumaça escura (mistura normal, não aditiva). */
    fun smoke(c: Canvas, p: Paint, x: Float, y: Float, rx: Float, ry: Float, a: Float) {
        glow(c, p, x, y, rx, ry, IchiPal.DARK, a, false)
    }
}


/** Getsuga Tenshou: meia-lua negra de gume incandescente, com cauda de cometa, chamas e brasas. */
class GetsugaWave(
    var x: Float, var y: Float, val dx: Float, val dy: Float,
    val dmg: Float, val lift: Float
) {
    var alive = true
    var age = 0f
    var life = 1.0f
    val speed = 470f
    val hit = HashSet<Enemy>()

    fun update(dt: Float) {
        x += dx * speed * dt
        y += dy * speed * dt
        age += dt
        life -= dt
        if (life <= 0f) alive = false
    }

    fun draw(c: Canvas, p: Paint, camX: Float, camY: Float) {
        if (!alive) return
        val G = Glow
        val sx = x - camX
        val sy = y - camY - lift
        val tvx = dx
        val tvy = dy * 0.75f
        val tl = max(0.001f, hypot(tvx, tvy))
        val ux = tvx / tl
        val uy = tvy / tl
        val nx = -uy
        val ny = ux
        val ang = atan2(dy * 0.75f, dx)

        val grow = min(1f, age / 0.10f)
        val fade = min(1f, life / 0.25f)
        val r = (30f + 12f * grow) * (0.5f + 0.5f * grow)
        val fl = (age * 30f).toInt()
        val pi = PI.toFloat()

        // sombra no chão
        G.glow(c, p, sx, sy + lift - r * 0.05f, r * 1.25f, r * 0.34f, IchiPal.DARK, 0.42f * fade, false)

        // cauda de cometa: fumaça escura por baixo e brilho vermelho por cima
        val tr = r * 5.4f
        for (i in 0..11) {
            val t = i / 11f
            val cx = sx - ux * tr * t
            val cy = sy - uy * tr * t
            val fo = (1f - t)
            G.smoke(c, p, cx, cy, r * (0.95f - 0.55f * t), r * (0.95f - 0.55f * t) * 0.9f, 0.34f * fo * fade)
        }
        for (i in 0..11) {
            val t = i / 11f
            val cx = sx - ux * tr * t * 0.92f
            val cy = sy - uy * tr * t * 0.92f
            val fo = (1f - t) * (1f - t)
            G.glow(c, p, cx, cy, r * (0.80f - 0.55f * t), IchiPal.RED, 0.50f * fo * fade)
            if (i < 7) G.glow(c, p, cx, cy, r * (0.38f - 0.26f * t), IchiPal.HOT, 0.45f * fo * fade)
        }
        // chamas arrastadas para trás
        for (k in 0..9) {
            val side = (IchiPal.rn(k + 3) - 0.5f) * 2f
            val off = side * r * 0.85f
            val bx = sx + nx * off - ux * r * 0.1f
            val by = sy + ny * off - uy * r * 0.1f

            val l = r * (1.1f + 1.7f * IchiPal.rn(k + fl * 2)) * (1f - 0.35f * kotlin.math.abs(side))
            val wob = 0.30f * sin(age * 22f + k * 1.7f)
            G.flame(c, p, bx, by, ang + pi + side * 0.28f, l, r * 0.17f, wob, if (k % 2 == 0) IchiPal.RED else IchiPal.RED_L, 0.82f * fade)
        }
        // luas fantasmas
        for (i in 4 downTo 1) {
            G.moon(
                c, p, sx - ux * 15f * i, sy - uy * 15f * i, r * (1f - 0.08f * i), ang, 112f, 0.55f,
                IchiPal.DARK, IchiPal.RED_D, IchiPal.RED, 0.16f * fade, false
            )
        }
        // halo de luz ao redor da lua
        G.glow(c, p, sx + ux * r * 0.1f, sy + uy * r * 0.1f, r * 2.3f, r * 1.9f, IchiPal.RED, 0.34f * fade)
        // chamas na borda externa
        for (k in 0..14) {
            val t = k / 14f - 0.5f
            val a = ang + t * 2.2f
            val bxx = sx + cos(a) * r * 1.10f
            val byy = sy + sin(a) * r * 0.78f * 1.10f
            val l = r * (0.30f + 0.62f * IchiPal.rn(k + fl * 3)) * (1f - kotlin.math.abs(t) * 0.75f)
            val wob = 0.35f * sin(age * 26f + k * 2.1f)
            G.flame(c, p, bxx, byy, a, l, r * 0.12f, wob, if (k % 2 == 0) IchiPal.RED_L else IchiPal.RED, 0.9f * fade)
        }
        // corpo da lua
        G.moon(c, p, sx, sy, r * 1.30f, ang, 132f, 0.52f, IchiPal.DARK, IchiPal.RED_D, IchiPal.RED, 0.70f * fade)
        G.moon(c, p, sx, sy, r * 1.10f, ang, 124f, 0.50f, IchiPal.DARK, IchiPal.RED, IchiPal.RED_L, 0.98f * fade)
        G.moon(c, p, sx, sy, r * 0.88f, ang, 110f, 0.44f, IchiPal.RED_D, IchiPal.RED_L, IchiPal.HOT, 0.95f * fade, false)
        G.moon(c, p, sx, sy, r * 0.66f, ang, 90f, 0.38f, IchiPal.RED, IchiPal.HOT, IchiPal.WHITE, 0.95f * fade, false)
        // brilho quente no meio do gume e reflexo de lente
        G.glow(c, p, sx + ux * r * 0.72f, sy + uy * r * 0.72f, r * 0.55f, IchiPal.WHITE, 0.55f * fade)
        G.star(c, p, sx + ux * r * 0.78f, sy + uy * r * 0.78f, r * (0.85f + 0.15f * sin(age * 40f)), age * 3f, IchiPal.WHITE, 0.75f * fade)
        // brasas soltas

        for (k in 0..11) {
            val ph = (age * 2.6f + k * 0.0833f) % 1f
            val off = (IchiPal.rn(k + 40) - 0.5f) * r * 1.8f
            val px = sx - ux * ph * tr * 0.85f + nx * off
            val py = sy - uy * ph * tr * 0.85f + ny * off - ph * r * 0.5f
            val sz = 1.1f + 2.4f * (1f - ph)
            G.ember(c, p, px, py, sz, if (k % 3 == 0) IchiPal.WHITE else IchiPal.RED_L, (1f - ph) * fade)
        }
    }
}

/**
 * Efeitos curtos do Ichigo. kind: 0 = feixe (Cero), 1 = anel no chão, 2 = rastro de Shunpo,
 * 3 = onda escura que preenche (Mugetsu / transformação), 4 = clarão de corte,
 * 5 = onda lunar rasteira (Golpe Lunar), 6 = corte em linha (garras), 7 = faísca de impacto,
 * 8 = feixe negro (Cero Oscuras). [delay] adia o efeito.
 */
class IchiFx(
    val kind: Int, val x0: Float, val y0: Float, val x1: Float, val y1: Float,
    val maxLife: Float, val r: Float, val col: Int, val delay: Float = 0f
) {
    var life = maxLife + delay
    val done: Boolean get() = life <= 0f
    private val seed = (x0 * 7f + y0 * 13f).toInt() and 0xFFFF

    fun update(dt: Float) {
        life -= dt
    }

    private fun ease(t: Float): Float {
        val v = t.coerceIn(0f, 1f)
        return v * v * (3f - 2f * v)

    }

    fun draw(c: Canvas, p: Paint, camX: Float, camY: Float) {
        if (life <= 0f || life > maxLife) return
        val G = Glow
        val a = (life / maxLife).coerceIn(0f, 1f)
        val u = 1f - a
        val ax = x0 - camX
        val ay = y0 - camY
        val bx = x1 - camX
        val by = y1 - camY
        val tf = (life * 40f).toInt()
        val pi = PI.toFloat()
        when (kind) {
            0, 8 -> {
                val dk = kind == 8
                val w = r * (0.30f + 0.70f * a) * (1f + 0.07f * sin(life * 70f))
                val dxl = bx - ax
                val dyl = by - ay
                val len = max(1f, hypot(dxl, dyl))
                val ux = dxl / len
                val uy = dyl / len
                // fumaça e halo largos
                G.beam(c, p, ax, ay, bx, by, w * 2.6f, IchiPal.DARK, IchiPal.DARK, IchiPal.DARK, 0.55f * a, false, false)
                G.beam(c, p, ax, ay, bx, by, w * 2.0f, IchiPal.RED, IchiPal.RED, IchiPal.RED_D, 0.45f * a)
                // corpo do feixe
                if (dk) {
                    G.beam(c, p, ax, ay, bx, by, w * 1.35f, IchiPal.RED_L, IchiPal.RED, IchiPal.DARK, 0.98f * a, false)
                    G.beam(c, p, ax, ay, bx, by, w * 0.75f, IchiPal.HOT, IchiPal.RED_L, IchiPal.RED, 0.9f * a)
                } else {
                    G.beam(c, p, ax, ay, bx, by, w * 1.35f, IchiPal.WHITE, col, IchiPal.RED, 0.95f * a)
                    G.beam(c, p, ax, ay, bx, by, w * 0.7f, IchiPal.WHITE, IchiPal.HOT, IchiPal.RED_L, 0.95f * a)

                }
                // pulsos de energia correndo pelo feixe
                for (k in 0..9) {
                    val t = (u * 2.4f + k * 0.1f) % 1f
                    val px = ax + dxl * t
                    val py = ay + dyl * t
                    val h = w * (1.45f - 0.5f * t)
                    G.beam(c, p, px - uy * h, py + ux * h, px + uy * h, py - ux * h, max(1.2f, w * 0.14f), IchiPal.WHITE, IchiPal.HOT, IchiPal.RED_L, 0.7f * a, true, false)
                }
                // raios elétricos nas bordas
                for (k in 0..2) {
                    val sgn = if (k % 2 == 0) 1f else -1f
                    val o = w * 1.2f * sgn
                    G.bolt(c, p, ax - uy * o, ay + ux * o, bx - uy * o, by + ux * o, w * 1.5f, tf * 3 + k * 17, max(1.2f, w * 0.10f), IchiPal.RED_L, 0.85f * a)
                }
                // origem: orbe e estrela
                G.glow(c, p, ax, ay, w * 3.4f, IchiPal.RED, 0.7f * a)
                G.glow(c, p, ax, ay, w * 1.7f, IchiPal.WHITE, 0.95f * a)
                G.star(c, p, ax, ay, w * (3.2f + 2f * a), life * 5f, IchiPal.WHITE, 0.85f * a)
                // chamas saindo da origem
                for (k in 0..10) {
                    val an = life * 7f + k * 0.5712f
                    val l = w * (1.6f + 1.8f * IchiPal.rn(k + tf))
                    G.flame(c, p, ax + cos(an) * w, ay + sin(an) * w * 0.8f, an, l, w * 0.28f, 0.3f * sin(life * 30f + k), IchiPal.RED_L, 0.7f * a)
                }
                // fim: explosão
                val ex = r * (1.0f + 2.6f * u)
                G.smoke(c, p, bx, by, ex * 1.35f, ex * 1.1f, 0.40f * a)
                G.glow(c, p, bx, by, ex * 1.5f, ex * 1.2f, col, 0.65f * a)
                G.glow(c, p, bx, by, ex * 0.85f, IchiPal.HOT, 0.8f * a)
                G.glow(c, p, bx, by, ex * 0.45f, IchiPal.WHITE, 0.95f * a)
                G.ring(c, p, bx, by, ex * 1.25f, ex * 0.95f, max(1.5f, r * 0.10f), col, IchiPal.RED_L, 0.8f * a)

                for (k in 0..13) {
                    val an = u * 2f + k * 0.4488f
                    val l = ex * (0.8f + 1.2f * IchiPal.rn(k + 9))
                    G.flame(c, p, bx + cos(an) * ex * 0.5f, by + sin(an) * ex * 0.4f, an, l, max(1.5f, r * 0.12f), 0.2f * sin(life * 25f + k), if (k % 2 == 0) IchiPal.RED_L else IchiPal.WHITE, 0.75f * a)
                }
                G.star(c, p, bx, by, ex * 1.4f, -life * 4f, IchiPal.WHITE, 0.7f * a)
            }
            1 -> {
                val rad = r * (0.2f + 0.8f * ease(u))
                // poça escura e brilho no chão
                G.glow(c, p, ax, ay, rad * 1.05f, rad * 0.66f, IchiPal.DARK, 0.45f * a, false)
                G.glow(c, p, ax, ay, rad * 0.95f, rad * 0.58f, col, 0.30f * a)
                G.ring(c, p, ax, ay, rad, rad * 0.62f, 2f + 6f * a, col, IchiPal.RED_L, 0.95f * a)
                G.ring(c, p, ax, ay, rad * 0.8f, rad * 0.5f, 1.5f + 2.5f * a, IchiPal.DARK, col, 0.75f * a)
                val r2 = r * (0.1f + 0.7f * ease(u * 0.9f))
                G.ring(c, p, ax, ay, r2, r2 * 0.62f, 1.5f + 3f * a, IchiPal.WHITE, IchiPal.WHITE, 0.55f * a)
                // rachaduras luminosas no chão
                for (k in 0..9) {
                    val an = k * 0.628f + IchiPal.rn(seed + k) * 0.3f
                    val l0 = rad * 0.15f
                    val l1 = rad * (0.55f + 0.4f * IchiPal.rn(seed + k + 20))
                    G.beam(
                        c, p, ax + cos(an) * l0, ay + sin(an) * l0 * 0.62f, ax + cos(an) * l1, ay + sin(an) * l1 * 0.62f,
                        1.2f + 2.4f * a, IchiPal.WHITE, col, IchiPal.DARK, 0.85f * a, true, false
                    )
                }
                // colunas de chama subindo do anel
                for (k in 0..19) {
                    val an = k * 0.31416f
                    val l = r * 0.34f * (0.35f + IchiPal.rn(seed + k + 50)) * (0.25f + a)
                    val fx = ax + cos(an) * rad * 0.97f
                    val fy = ay + sin(an) * rad * 0.62f * 0.97f

                    G.flame(c, p, fx, fy, -pi / 2f, l, r * 0.040f, 0.35f * sin(life * 18f + k * 1.9f), if (k % 3 == 0) IchiPal.RED_L else col, 0.8f * a)
                    if (k % 4 == 0) G.ember(c, p, fx, fy - l * (0.6f + 0.6f * u), 1.4f + 1.8f * a, IchiPal.HOT, 0.8f * a)
                }
            }
            2 -> {
                val dxl = bx - ax
                val dyl = by - ay
                val len = max(1f, hypot(dxl, dyl))
                val ux = dxl / len
                val uy = dyl / len
                G.beam(c, p, ax, ay, bx, by, r * (0.5f + 0.7f * a), IchiPal.DARK, IchiPal.DARK, IchiPal.DARK, 0.55f * a, false, false)
                G.beam(c, p, ax, ay, bx, by, r * 0.8f * a + 1f, IchiPal.WHITE, col, col, 0.85f * a)
                G.beam(c, p, ax, ay, bx, by, max(1.2f, r * 0.2f * a), IchiPal.WHITE, IchiPal.WHITE, IchiPal.WHITE, 0.95f * a, true, false)
                // linhas de velocidade paralelas
                for (k in -3..3) {
                    val o = k * r * 0.34f
                    val s0 = 0.1f + 0.5f * IchiPal.rn(seed + k + 9)
                    val s1 = min(1f, s0 + 0.3f + 0.4f * IchiPal.rn(seed + k + 19))
                    G.beam(
                        c, p, ax + dxl * s0 - uy * o, ay + dyl * s0 + ux * o, ax + dxl * s1 - uy * o, ay + dyl * s1 + ux * o,
                        max(1.2f, r * 0.07f), IchiPal.WHITE, if (k % 2 == 0) IchiPal.WHITE else col, col, 0.7f * a, true, false
                    )
                }
                // clarões de partida e de chegada
                G.glow(c, p, ax, ay, r * (1.2f + 1.2f * u), col, 0.55f * a)
                G.star(c, p, ax, ay, r * (1.2f + 1.4f * u), u * 3f, col, 0.8f * a)
                G.glow(c, p, bx, by, r * (1.4f + 1.4f * u), IchiPal.WHITE, 0.55f * a)
                G.star(c, p, bx, by, r * (1.6f + 1.8f * u), -u * 3f, IchiPal.WHITE, 0.85f * a)
                for (k in 0..6) {
                    val sp = (u * 1.5f + k * 0.14f) % 1f
                    G.ember(c, p, ax + dxl * sp + (IchiPal.rn(seed + k) - 0.5f) * r, ay + dyl * sp + (IchiPal.rn(seed + k + 5) - 0.5f) * r, 1.4f + 1.5f * a, col, 0.8f * a)
                }

            }
            3 -> {
                val rad = r * (0.1f + 0.9f * ease(u * 1.4f))
                G.glow(c, p, ax, ay, rad * 1.1f, rad * 0.70f, IchiPal.DARK, 0.62f * a, false)
                G.glow(c, p, ax, ay, rad, rad * 0.62f, col, 0.30f * a)
                // chamas negras e rubras subindo da onda
                for (k in 0..29) {
                    val an = k * 0.20944f
                    val l = r * (0.20f + 0.34f * IchiPal.rn(seed + k + tf / 3)) * (0.4f + a)
                    val fx = ax + cos(an) * rad * 0.96f
                    val fy = ay + sin(an) * rad * 0.62f * 0.96f
                    val tilt = -pi / 2f + (IchiPal.rn(seed + k) - 0.5f) * 0.5f
                    val wob = 0.3f * sin(life * 16f + k * 1.3f)
                    G.flame(c, p, fx, fy, tilt, l * 1.15f, r * 0.045f, wob, IchiPal.DARK, 0.75f * a, false)
                    G.flame(c, p, fx, fy, tilt, l, r * 0.034f, wob, if (k % 3 == 0) col else IchiPal.RED_L, 0.85f * a)
                }
                G.ring(c, p, ax, ay, rad, rad * 0.62f, 3f + 9f * a, col, IchiPal.RED_L, 0.95f * a)
                G.ring(c, p, ax, ay, rad * 0.9f, rad * 0.56f, 2f + 4f * a, IchiPal.WHITE, IchiPal.WHITE, 0.6f * a)
                for (k in 0..17) {
                    val an = u * 1.5f + k * 0.349f
                    val l = rad * (0.4f + 0.5f * u) * (0.6f + 0.6f * IchiPal.rn(seed + k + 21))
                    G.flame(c, p, ax + cos(an) * rad * 0.12f, ay - 10f + sin(an) * rad * 0.1f, an, l, 2f + 4f * a, 0.18f * sin(life * 20f + k), IchiPal.RED_L, 0.55f * a)
                }
                if (u < 0.3f) {
                    val k0 = 1f - u / 0.3f
                    G.glow(c, p, ax, ay - 20f, r * 0.55f * (1f - u) + 6f, IchiPal.WHITE, 0.75f * k0)
                    G.star(c, p, ax, ay - 20f, r * 0.9f * k0, u * 4f, IchiPal.WHITE, 0.8f * k0)
                }
            }
            5 -> {
                // onda lunar rasteira: meia-lua que se abre no chão à frente do golpe
                val ang = atan2(y1 * 0.75f, x1)

                val rad = r * (0.35f + 0.65f * ease(u))
                val cx = ax + x1 * r * 0.25f * u
                val cy = ay + y1 * r * 0.25f * u * 0.75f
                G.glow(c, p, cx, cy, rad * 1.3f, rad * 0.8f, IchiPal.DARK, 0.45f * a, false)
                G.glow(c, p, cx, cy, rad * 1.2f, rad * 0.75f, col, 0.30f * a)
                G.moon(c, p, cx, cy, rad * 1.12f, ang, 152f, 0.42f, IchiPal.DARK, IchiPal.RED_D, col, 0.62f * a, false)
                G.moon(c, p, cx, cy, rad, ang, 142f, 0.36f, IchiPal.DARK, col, IchiPal.RED_L, 0.92f * a)
                G.moon(c, p, cx, cy, rad * 0.82f, ang, 122f, 0.30f, col, IchiPal.HOT, IchiPal.WHITE, 0.95f * a, false)
                for (k in 0..11) {
                    val an = ang + (k / 11f - 0.5f) * 2.4f
                    val l = rad * (0.25f + 0.35f * IchiPal.rn(seed + k + 41)) * (0.4f + a)
                    G.flame(c, p, cx + cos(an) * rad * 1.05f, cy + sin(an) * rad * 0.78f * 1.05f, an, l, rad * 0.05f, 0.3f * sin(life * 22f + k), IchiPal.RED_L, 0.8f * a)
                }
                G.star(c, p, cx + cos(ang) * rad * 0.9f, cy + sin(ang) * rad * 0.7f, rad * 0.5f * a + 4f, u * 2f, IchiPal.WHITE, 0.7f * a)
            }
            6 -> {
                // corte em linha (garra): risca o ar rapidamente e some
                val prog = min(1f, u * 5f)
                val ex = ax + (bx - ax) * prog
                val ey = ay + (by - ay) * prog
                G.beam(c, p, ax, ay, ex, ey, r * 1.5f * a + 1f, IchiPal.DARK, IchiPal.DARK, IchiPal.DARK, 0.5f * a, false, false)
                G.beam(c, p, ax, ay, ex, ey, r * 1.0f * a + 1f, IchiPal.WHITE, col, IchiPal.RED_D, 0.95f * a)
                G.beam(c, p, ax, ay, ex, ey, max(1.2f, r * 0.30f * a), IchiPal.WHITE, IchiPal.WHITE, IchiPal.WHITE, 0.98f * a, true, false)
                G.glow(c, p, ex, ey, r * (1.2f + 1.4f * u), col, 0.6f * a)
                G.star(c, p, ex, ey, r * (1.6f + 1.6f * u), u * 4f, IchiPal.WHITE, 0.85f * a)
                for (k in 0..6) {
                    val an = u * 4f + k * 0.8976f
                    val l = r * (1.0f + 1.6f * IchiPal.rn(seed + k + 61)) * (0.3f + a)
                    G.flame(c, p, ex, ey, an, l, max(1.2f, r * 0.12f), 0.2f * sin(life * 25f + k), IchiPal.RED_L, 0.8f * a)
                }
            }
            7 -> {

                // faísca de impacto sobre quem foi atingido
                G.glow(c, p, ax, ay, r * (0.5f + 0.9f * u), col, 0.6f * a)
                G.glow(c, p, ax, ay, r * (0.25f + 0.5f * u), IchiPal.WHITE, 0.85f * a)
                G.ring(c, p, ax, ay, r * (0.4f + 1.1f * u), r * (0.32f + 0.88f * u), max(1.2f, r * 0.08f * a), col, IchiPal.WHITE, 0.8f * a)
                G.star(c, p, ax, ay, r * (1.1f + 1.3f * u), u * 3f, IchiPal.WHITE, 0.9f * a)
                for (k in 0..8) {
                    val an = u * 3f + k * 0.6981f
                    val l = r * (0.7f + 1.2f * IchiPal.rn(seed + k + 71)) * (0.35f + a)
                    G.flame(c, p, ax + cos(an) * r * 0.3f, ay + sin(an) * r * 0.24f, an, l, max(1.2f, r * 0.08f), 0.2f * sin(life * 28f + k), IchiPal.WHITE, 0.8f * a)
                }
            }
            else -> {
                // clarão de corte: duas luas cruzadas na direção (x1, y1)
                val ang = atan2(y1 * 0.75f, x1)
                val grow = 0.55f + 0.45f * ease(u * 3f)
                G.glow(c, p, ax, ay, r * grow * 1.5f, r * grow * 1.2f, col, 0.38f * a)
                G.moon(c, p, ax, ay, r * grow * 1.15f, ang, 152f, 0.5f, IchiPal.DARK, IchiPal.RED_D, col, 0.55f * a, false)
                G.moon(c, p, ax, ay, r * grow, ang, 142f, 0.45f, IchiPal.DARK, col, IchiPal.RED_L, 0.9f * a)
                G.moon(c, p, ax, ay, r * grow * 0.8f, ang, 120f, 0.4f, col, IchiPal.HOT, IchiPal.WHITE, 0.95f * a, false)
                // risco reto brilhante cortando o ar
                val pa = ang + pi / 2f
                val ll = r * (0.9f + 0.7f * u)
                G.slash(c, p, ax - cos(pa) * ll, ay - sin(pa) * ll * 0.8f, ax + cos(pa) * ll, ay + sin(pa) * ll * 0.8f, max(1.6f, r * 0.05f), IchiPal.RED_L, a)
                G.star(c, p, ax, ay, r * 0.55f * (0.6f + a), ang, IchiPal.WHITE, 0.8f * a)
                for (k in 0..9) {
                    val an = ang + k * 0.6283f
                    val l = r * (0.5f + 0.8f * IchiPal.rn(seed + k + 31)) * (0.3f + a)
                    G.flame(c, p, ax + cos(an) * r * 0.2f, ay + sin(an) * r * 0.16f, an, l, max(1.2f, r * 0.05f), 0.2f * sin(life * 24f + k), IchiPal.RED_L, 0.75f * a)
                }
            }
        }
    }

}


/** Getsuga Jujisho: grande cruz de reiatsu rubro-negro que avança girando e atravessa todos. */
class CrossWave(
    var x: Float, var y: Float, val dx: Float, val dy: Float,
    val dmg: Float, val lift: Float
) {
    var alive = true
    var age = 0f
    var life = 0.95f
    val speed = 400f
    val hit = HashSet<Enemy>()

    fun update(dt: Float) {
        x += dx * speed * dt
        y += dy * speed * dt
        age += dt
        life -= dt
        if (life <= 0f) alive = false
    }

    private fun bar(c: Canvas, p: Paint, sx: Float, sy: Float, ang: Float, half: Float, w: Float, fade: Float, soft: Boolean) {
        val ca = cos(ang)
        val sa = sin(ang)
        val x0 = sx - ca * half
        val y0 = sy - sa * half * 0.8f
        val x1 = sx + ca * half
        val y1 = sy + sa * half * 0.8f
        val G = Glow
        if (soft) {
            G.beam(c, p, x0, y0, x1, y1, w * 1.5f, IchiPal.RED, IchiPal.RED_D, IchiPal.DARK, 0.5f * fade)

            return
        }
        G.beam(c, p, x0, y0, x1, y1, w * 2.1f, IchiPal.DARK, IchiPal.DARK, IchiPal.DARK, 0.55f * fade, false, false)
        G.beam(c, p, x0, y0, x1, y1, w * 1.9f, IchiPal.RED, IchiPal.RED, IchiPal.RED_D, 0.5f * fade)
        G.beam(c, p, x0, y0, x1, y1, w * 1.15f, IchiPal.HOT, IchiPal.RED_L, IchiPal.RED, 0.98f * fade, false)
        G.beam(c, p, x0, y0, x1, y1, w * 0.50f, IchiPal.WHITE, IchiPal.WHITE, IchiPal.HOT, 0.95f * fade)
    }

    fun draw(c: Canvas, p: Paint, camX: Float, camY: Float) {
        if (!alive) return
        val G = Glow
        val sx = x - camX
        val sy = y - camY - lift
        val ang = atan2(dy * 0.75f, dx)
        val grow = min(1f, age / 0.10f)
        val fade = min(1f, life / 0.25f)
        val half = 54f * (0.5f + 0.5f * grow)
        val w = 9f * (0.6f + 0.4f * grow)
        val tl = max(0.001f, hypot(dx, dy * 0.75f))
        val ux = dx / tl
        val uy = dy * 0.75f / tl
        val pi = PI.toFloat()

        // sombra no chão
        G.glow(c, p, sx, sy + lift, half * 0.95f, half * 0.24f, IchiPal.DARK, 0.40f * fade, false)
        // fumaça e brilho ao redor
        G.smoke(c, p, sx - ux * 10f, sy - uy * 10f, half * 1.05f, half * 0.85f, 0.30f * fade)
        G.glow(c, p, sx, sy, half * 1.5f, half * 1.2f, IchiPal.RED, 0.30f * fade)
        // rastro: cruzes fantasmas
        for (i in 3 downTo 1) {
            val gx = sx - ux * 22f * i
            val gy = sy - uy * 22f * i

            bar(c, p, gx, gy, ang + pi / 2f, half * (1f - 0.12f * i), w * 0.8f, 0.30f * fade, true)
            bar(c, p, gx, gy, ang, half * 0.8f * (1f - 0.12f * i), w * 0.8f, 0.30f * fade, true)
        }
        // cruz principal (a barra maior corta de través, a menor segue o avanço)
        bar(c, p, sx, sy, ang + pi / 2f, half, w, fade, false)
        bar(c, p, sx, sy, ang, half * 0.8f, w, fade, false)
        // chamas nas pontas e ao longo das barras
        for (k in 0..3) {
            val a0 = ang + k * pi / 2f
            val hl = if (k % 2 == 0) half * 0.8f else half
            val flick = 0.6f + 0.4f * IchiPal.rn(k + (age * 30f).toInt() * 3)
            val tx = sx + cos(a0) * hl
            val ty = sy + sin(a0) * hl * 0.8f
            G.glow(c, p, tx, ty, w * 2.2f, IchiPal.RED_L, 0.6f * fade)
            G.flame(c, p, tx, ty, a0, 26f * flick, 6f, 0.3f * sin(age * 30f + k), IchiPal.RED_L, 0.9f * fade)
            G.flame(c, p, tx, ty, a0, 14f * flick, 3f, 0.3f * sin(age * 30f + k), IchiPal.WHITE, 0.85f * fade)
        }
        for (k in 0..9) {
            val side = if (k % 2 == 0) 1f else -1f
            val t = (k / 9f) * 0.9f + 0.05f
            val bxx = sx + cos(ang + pi / 2f) * half * t * side
            val byy = sy + sin(ang + pi / 2f) * half * 0.8f * t * side
            G.flame(c, p, bxx, byy, ang + pi + side * 0.3f, 14f * (0.5f + IchiPal.rn(k + (age * 30f).toInt())), 3.5f, 0.35f * sin(age * 24f + k), IchiPal.RED_L, 0.6f * fade)
        }
        // miolo
        G.glow(c, p, sx, sy, w * 3.2f, IchiPal.RED_L, 0.8f * fade)
        G.glow(c, p, sx, sy, w * 1.6f, IchiPal.WHITE, 0.95f * fade)
        G.star(c, p, sx, sy, w * (4.2f + 0.6f * sin(age * 30f)), age * 6f, IchiPal.WHITE, 0.85f * fade)
        // brasas soltas atrás
        for (k in 0..11) {
            val ph = (age * 2.6f + k * 0.0833f) % 1f
            val off = (IchiPal.rn(k + 140) - 0.5f) * half * 1.4f

            val px = sx - ux * ph * 120f - uy * off
            val py = sy - uy * ph * 120f + ux * off - ph * 8f
            val sz = 1.1f + 2.4f * (1f - ph)
            G.ember(c, p, px, py, sz, if (k % 3 == 0) IchiPal.WHITE else IchiPal.RED_L, (1f - ph) * fade)
        }
    }
}
