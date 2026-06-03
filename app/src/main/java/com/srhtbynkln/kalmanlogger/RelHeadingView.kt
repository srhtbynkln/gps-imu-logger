package com.srhtbynkln.kalmanlogger

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Baslangica gore yon: kayit basinda ileri yon = 0 (düz yukari). Sonra saga/sola
 * dondukce ok yatar; uste "Saga/Sola N°" yazar. Referans (sifir) kayit basinda
 * SensorLogger tarafindan alinir -> KAYDI BASLAT ile sifirlanir.
 */
class RelHeadingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var relDeg = 0f

    private val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.parseColor("#444444")
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 10f), 0f)
    }
    private val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.parseColor("#4CAF50")
    }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0"); textAlign = Paint.Align.CENTER
        textSize = 40f; isFakeBoldText = true
    }

    fun update(relDeg: Float) { this.relDeg = relDeg; invalidate() }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val baseY = height - 24f                 // okun tabani altta
        val len = min(height - 70f, width / 2f - 20f)

        // düz ileri kilavuz cizgisi (0 referansi)
        c.drawLine(cx, baseY, cx, baseY - len, guide)

        // ok: yukaridan saat yonunde relDeg kadar yatik
        val a = Math.toRadians(relDeg.toDouble())
        val ux = sin(a).toFloat();  val uy = -cos(a).toFloat()
        val px = -uy;               val py = ux
        val tipx = cx + ux * len;        val tipy = baseY + uy * len
        val bw = 26f
        val p = Path()
        p.moveTo(tipx, tipy)
        p.lineTo(cx + px * bw, baseY + py * bw)
        p.lineTo(cx - px * bw, baseY - py * bw)
        p.close()
        c.drawPath(p, arrow)

        val s = when {
            abs(relDeg) < 3f -> "Düz"
            relDeg > 0 -> String.format("Sağa %.0f°", relDeg)
            else -> String.format("Sola %.0f°", -relDeg)
        }
        c.drawText(s, cx, 44f, txt)
    }
}
