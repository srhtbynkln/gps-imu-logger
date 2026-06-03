package com.srhtbynkln.kalmanlogger

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/**
 * Kaydedilen rotayi uygulama icinde cizer. Iki kaynak: GPS yorungesi (mavi) ve
 * IMU dead-reckoning (jiro+ivme, turuncu). mode: 0=GPS, 1=IMU, 2=ikisi.
 * Noktalar yerel metre (Dogu, Kuzey). Auto-olcek + ortalama. clear() ile sifir.
 */
class TrackView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val gpsE = ArrayList<Float>(); private val gpsN = ArrayList<Float>()
    private val imuE = ArrayList<Float>(); private val imuN = ArrayList<Float>()
    private var gpsDist = 0f; private var imuDist = 0f
    var mode = 0   // 0=GPS, 1=IMU, 2=ikisi

    private fun paintLine(c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 5f; color = c
        strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val lineGps = paintLine(Color.parseColor("#1f77b4"))
    private val lineImu = paintLine(Color.parseColor("#ff7f0e"))
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1f; color = Color.parseColor("#33888888")
    }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCCCCC"); textSize = 28f
    }
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888"); textSize = 32f; textAlign = Paint.Align.CENTER
    }

    fun clear() { gpsE.clear(); gpsN.clear(); imuE.clear(); imuN.clear(); gpsDist = 0f; imuDist = 0f; invalidate() }
    fun addGps(e: Float, n: Float) {
        if (gpsE.isNotEmpty()) gpsDist += hypot(e - gpsE.last(), n - gpsN.last())
        gpsE.add(e); gpsN.add(n); if (mode != 1) invalidate()
    }
    fun addImu(e: Float, n: Float) {
        if (imuE.isNotEmpty()) imuDist += hypot(e - imuE.last(), n - imuN.last())
        imuE.add(e); imuN.add(n); if (mode != 0) invalidate()
    }
    fun setSourceMode(m: Int) { mode = m; invalidate() }

    private fun showGps() = mode == 0 || mode == 2
    private fun showImu() = mode == 1 || mode == 2

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        c.drawRect(1f, 1f, w - 1, h - 1, border)

        // gorunur serilerden ortak sinirlar
        val xs = ArrayList<Float>(); val ys = ArrayList<Float>()
        if (showGps()) { xs.addAll(gpsE); ys.addAll(gpsN) }
        if (showImu()) { xs.addAll(imuE); ys.addAll(imuN) }
        if (xs.size < 2) {
            val msg = when (mode) {
                1 -> "IMU rotası: kayıt başlayınca çizilir"
                else -> "Rota için GPS fix bekleniyor…"
            }
            c.drawText(msg, w / 2, h / 2, hint); return
        }
        val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
        val spanX = (maxX - minX).coerceAtLeast(1f); val spanY = (maxY - minY).coerceAtLeast(1f)
        val pad = 36f
        val s = min((w - 2 * pad) / spanX, (h - 2 * pad) / spanY)
        val offX = (w - spanX * s) / 2f - minX * s
        val offY = (h - spanY * s) / 2f - minY * s
        fun sx(e: Float) = offX + e * s
        fun sy(n: Float) = h - (offY + n * s)

        fun drawSeries(E: List<Float>, N: List<Float>, p: Paint) {
            if (E.size < 2) return
            val path = Path(); path.moveTo(sx(E[0]), sy(N[0]))
            for (i in 1 until E.size) path.lineTo(sx(E[i]), sy(N[i]))
            c.drawPath(path, p)
            dot.color = Color.parseColor("#4CAF50"); c.drawCircle(sx(E[0]), sy(N[0]), 11f, dot)
            dot.color = Color.parseColor("#E53935"); c.drawCircle(sx(E.last()), sy(N.last()), 11f, dot)
        }
        if (showGps()) drawSeries(gpsE, gpsN, lineGps)
        if (showImu()) drawSeries(imuE, imuN, lineImu)

        val label = when (mode) {
            0 -> "GPS  %.0f m  •  %d nokta".format(gpsDist, gpsE.size)
            1 -> "IMU  %.0f m  •  %d nokta".format(imuDist, imuE.size)
            else -> "GPS %.0f m (mavi)  |  IMU %.0f m (turuncu)".format(gpsDist, imuDist)
        }
        c.drawText(label, 12f, h - 14f, txt)
    }
}
