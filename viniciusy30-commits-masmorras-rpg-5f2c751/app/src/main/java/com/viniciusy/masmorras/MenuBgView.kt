package com.viniciusy.masmorras

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fundo do menu: parede de tijolos de masmorra, duas tochas tremeluzindo e brasas subindo.
 * Os tijolos são desenhados uma vez só num bitmap; só tocha/brasas animam.
 */
class MenuBgView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rnd = Random(42)

    private var bricks: Bitmap? = null
    private var vignette: Shader? = null
    private val glow: Shader = RadialGradient(
        0f, 0f, 100f,
        intArrayOf(0x66FFA030, 0x33FF7A18, 0x00FF5000),
        floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
    )

    private var t = 0f
    private var running = false
    private var last = 0L
    private var spawnAcc = 0f

    private class Ember(
        var x: Float, var y: Float, var vy: Float,
        var sway: Float, var ph: Float, var size: Float, var life: Float, val max: Float
    )

    private val embers = ArrayList<Ember>()

    private val loop = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.nanoTime()
            val dt = ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            last = now
            step(dt)
            invalidate()
            postOnAnimation(this)
        }
    }

    fun setRunning(on: Boolean) {
        if (on == running) return
        running = on
        if (on) {
            last = System.nanoTime()
            postOnAnimation(loop)
        } else {
            removeCallbacks(loop)
        }
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(loop)
        super.onDetachedFromWindow()
    }

    private fun torchX(i: Int): Float = if (i == 0) width * 0.055f else width * 0.945f
    private fun torchY(): Float = height * 0.40f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bricks?.recycle()
        bricks = if (w > 0 && h > 0) buildBricks(w, h) else null
        vignette = RadialGradient(
            w / 2f, h / 2f, max(w, h) * 0.72f,
            intArrayOf(0x00000000, 0x55000000, 0xF2000000.toInt()),
            floatArrayOf(0.30f, 0.70f, 1f), Shader.TileMode.CLAMP
        )
    }

    private fun shade(base: Int, delta: Int): Int = Color.rgb(
        (Color.red(base) + delta).coerceIn(0, 255),
        (Color.green(base) + delta).coerceIn(0, 255),
        (Color.blue(base) + delta).coerceIn(0, 255)
    )

    private fun buildBricks(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint()
        val r = Random(7)
        c.drawColor(0xFF121110.toInt())
        val bh = (30 * d).toInt()
        val bw = (64 * d).toInt()
        val gap = max(1, (1.5f * d).toInt())
        val two = max(1, (2 * d).toInt())
        val three = max(2, (3 * d).toInt())
        var y = 0
        var row = 0
        while (y < h) {
            var x = if (row % 2 == 0) 0 else -bw / 2
            while (x < w) {
                val ww = bw + r.nextInt(-two * 3, two * 3 + 1)
                val base = shade(0xFF2A2826.toInt(), r.nextInt(-9, 10))
                val l = x + gap
                val t0 = y + gap
                val rr = x + ww - gap
                val bb = y + bh - gap
                p.color = base
                c.drawRect(l.toFloat(), t0.toFloat(), rr.toFloat(), bb.toFloat(), p)
                p.color = 0x1FFFFFFF
                c.drawRect(l.toFloat(), t0.toFloat(), rr.toFloat(), (t0 + two).toFloat(), p)
                p.color = 0x44000000
                c.drawRect(l.toFloat(), (bb - two).toFloat(), rr.toFloat(), bb.toFloat(), p)
                var k = 0
                while (k < 4) {
                    p.color = if (r.nextBoolean()) 0x18FFFFFF else 0x26000000
                    val sx = l + r.nextInt(max(1, ww - 2 * gap - three))
                    val sy = t0 + two + r.nextInt(max(1, bh - 2 * gap - three * 2))
                    c.drawRect(sx.toFloat(), sy.toFloat(), (sx + three).toFloat(), (sy + two).toFloat(), p)
                    k++
                }
                x += ww
            }
            y += bh
            row++
        }
        return bmp
    }

    private fun spawn() {
        val i = if (rnd.nextBoolean()) 0 else 1
        val life = 2.2f + rnd.nextFloat() * 2.6f
        embers.add(
            Ember(
                torchX(i) + (rnd.nextFloat() - 0.5f) * 22f * d,
                torchY() - 8f * d,
                -(22f + rnd.nextFloat() * 38f) * d,
                (6f + rnd.nextFloat() * 14f) * d,
                rnd.nextFloat() * 6.28f,
                (1.6f + rnd.nextFloat() * 1.8f) * d,
                life, life
            )
        )
    }

    private fun step(dt: Float) {
        t += dt
        spawnAcc += dt * 16f
        while (spawnAcc >= 1f) {
            spawnAcc -= 1f
            if (embers.size < 48) spawn()
        }
        val iter = embers.iterator()
        while (iter.hasNext()) {
            val e = iter.next()
            e.life -= dt
            e.y += e.vy * dt
            e.x += sin(t * 2.2f + e.ph) * e.sway * dt
            if (e.life <= 0f) iter.remove()
        }
    }

    private fun drawTorch(canvas: Canvas, cx: Float, cy: Float, seed: Float) {
        val fl = 0.55f * sin(t * 11f + seed) + 0.35f * sin(t * 23f + seed * 2f) + 0.1f * sin(t * 41f + seed)

        // brilho
        val s = (1f + 0.07f * fl) * 3.1f * d
        canvas.save()
        canvas.translate(cx, cy - 18f * d)
        canvas.scale(s, s)
        paint.style = Paint.Style.FILL
        paint.shader = glow
        canvas.drawCircle(0f, 0f, 100f, paint)
        paint.shader = null
        canvas.restore()

        // suporte de ferro + cabo
        paint.style = Paint.Style.FILL
        paint.color = 0xFF050505.toInt()
        canvas.drawRect(cx - 10f * d, cy + 4f * d, cx + 10f * d, cy + 12f * d, paint)
        canvas.drawRect(cx - 6f * d, cy - 2f * d, cx + 6f * d, cy + 40f * d, paint)
        paint.color = 0xFF3A3633.toInt()
        canvas.drawRect(cx - 8f * d, cy + 6f * d, cx + 8f * d, cy + 10f * d, paint)
        paint.color = 0xFF6B4423.toInt()
        canvas.drawRect(cx - 4f * d, cy, cx + 4f * d, cy + 38f * d, paint)
        paint.color = 0xFF8A5A30.toInt()
        canvas.drawRect(cx - 4f * d, cy, cx - 1f * d, cy + 38f * d, paint)

        // chama em blocos
        val hh = (24f + 6f * fl) * d
        paint.color = 0xFF050505.toInt()
        canvas.drawRect(cx - 9f * d, cy - hh - 2f * d, cx + 9f * d, cy + 1f * d, paint)
        paint.color = 0xFFFF6A14.toInt()
        canvas.drawRect(cx - 7f * d, cy - hh, cx + 7f * d, cy, paint)
        paint.color = 0xFFFFB02E.toInt()
        canvas.drawRect(cx - 5f * d, cy - hh * 0.75f, cx + 5f * d, cy, paint)
        paint.color = 0xFFFFE680.toInt()
        canvas.drawRect(cx - 2.5f * d, cy - hh * 0.45f, cx + 2.5f * d, cy, paint)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val b = bricks
        if (b != null) canvas.drawBitmap(b, 0f, 0f, null) else canvas.drawColor(0xFF121110.toInt())

        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = 0x55000000
        canvas.drawRect(0f, 0f, w, h, paint)

        drawTorch(canvas, torchX(0), torchY(), 0f)
        drawTorch(canvas, torchX(1), torchY(), 2.7f)

        for (e in embers) {
            val a = (e.life / e.max).coerceIn(0f, 1f)
            val alpha = (a * 255).toInt().coerceIn(0, 255)
            paint.color = Color.argb(alpha, 255, (120 + 110 * a).toInt().coerceIn(0, 255), (30 + 40 * a).toInt())
            canvas.drawRect(e.x, e.y, e.x + e.size, e.y + e.size, paint)
        }

        vignette?.let {
            paint.shader = it
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
        }
    }
}
