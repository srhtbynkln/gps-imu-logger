package com.srhtbynkln.kalmanlogger

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Canli pusula: hareket yonunu (GPS rotasi yoksa IMU yaw) buyuk bir okla,
 * ortada hizi, dis halka kalinligiyla anlik ivme buyuklugunu gosterir.
 * MainActivity her tick'te update(...) cagirir.
 */
class LiveCompassView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var headingDeg = Float.NaN   // hareket yonu (0=Kuzey, saat yonu)
    private var speedMps = 0f
    private var accMag = 0f
    private var fromGps = false          // yon GPS'ten mi (true) yoksa IMU'dan mi

    private val dial = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 6f; color = Color.parseColor("#555555")
    }
    private val accRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 14f; color = Color.parseColor("#FF7043")
    }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f; color = Color.parseColor("#888888")
    }
    private val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.parseColor("#2196F3")
    }
    private val arrowImu = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.parseColor("#9E9E9E")
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAAAAA"); textAlign = Paint.Align.CENTER; textSize = 34f
    }
    private val speedTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0"); textAlign = Paint.Align.CENTER
        textSize = 64f; isFakeBoldText = true
    }
    private val subTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9E9E9E"); textAlign = Paint.Align.CENTER; textSize = 30f
    }

    fun update(headingDeg: Float, fromGps: Boolean, speedMps: Float, accMag: Float) {
        this.headingDeg = headingDeg; this.fromGps = fromGps
        this.speedMps = speedMps; this.accMag = accMag
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(cx, cy) - 24f

        // dis halka (ivme: 0..6 m/s^2 -> kalinlik)
        accRing.strokeWidth = (6f + 26f * (accMag / 6f).coerceIn(0f, 1f))
        c.drawCircle(cx, cy, r, accRing)
        c.drawCircle(cx, cy, r, dial)

        // N/E/S/W ticks + harfler
        val dirs = listOf("K" to 0.0, "D" to 90.0, "G" to 180.0, "B" to 270.0)
        for ((name, deg) in dirs) {
            val a = Math.toRadians(deg)
            val sx = cx + (r - 10) * sin(a).toFloat();  val sy = cy - (r - 10) * cos(a).toFloat()
            val ex = cx + (r - 34) * sin(a).toFloat();  val ey = cy - (r - 34) * cos(a).toFloat()
            c.drawLine(sx, sy, ex, ey, tick)
            val lx = cx + (r - 62) * sin(a).toFloat();  val ly = cy - (r - 62) * cos(a).toFloat()
            c.drawText(name, lx, ly + 12, label)
        }

        // hareket yonu oku
        if (!headingDeg.isNaN()) {
            val a = Math.toRadians(headingDeg.toDouble())
            val ux = sin(a).toFloat();  val uy = -cos(a).toFloat()        // ileri yon
            val px = -uy;               val py = ux                        // dik (taban genisligi)
            val tip = r - 40f;          val baseLen = r * 0.22f;           val baseW = r * 0.16f
            val p = Path()
            p.moveTo(cx + ux * tip, cy + uy * tip)                                  // uc
            p.lineTo(cx - ux * baseLen + px * baseW, cy - uy * baseLen + py * baseW)
            p.lineTo(cx - ux * baseLen - px * baseW, cy - uy * baseLen - py * baseW)
            p.close()
            c.drawPath(p, if (fromGps) arrow else arrowImu)
        }

        // ortada hiz + alt bilgi
        c.drawText(String.format("%.1f", speedMps), cx, cy - 4, speedTxt)
        c.drawText("m/s", cx, cy + 30, subTxt)
        val src = if (headingDeg.isNaN()) "yon: --"
                  else String.format("%s %.0f°", if (fromGps) "GPS" else "IMU", headingDeg)
        c.drawText(src, cx, cy + r * 0.55f, subTxt)
    }
}
