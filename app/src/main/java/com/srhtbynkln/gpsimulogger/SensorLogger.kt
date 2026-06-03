package com.srhtbynkln.gpsimulogger

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Tum sensorleri + GPS'i, mevcut MATLAB/Python pipeline'inin bekledigi CSV
 * formatinda (Accelerometer/Gyroscope/Magnetometer/Location/Orientation) bir
 * oturum klasorune yazar. Tum yazma islemleri tek bir arka-plan thread'inde
 * serilestirilir; canli degerler @Volatile alanlardan UI'a okunur.
 *
 * Zaman tabani: tum akislar SystemClock.elapsedRealtimeNanos saatini kullanir
 * (sensor event.timestamp ve location.elapsedRealtimeNanos ayni saat) -> tum
 * sensorler arasi 'seconds_elapsed' tutarli olur.
 */
class SensorLogger(private val context: Context, private val sensorDelay: Int) {

    @Volatile var running = false; private set
    var sessionDir: File? = null; private set

    // --- canli degerler (UI thread'inden okunur) ---
    @Volatile var lastSpeed = Float.NaN
    @Volatile var lastBearing = Float.NaN
    @Volatile var lastHacc = Float.NaN
    @Volatile var lastAccMag = 0f
    @Volatile var lastYawDeg = Float.NaN
    @Volatile var lastYawRelDeg = 0f       // baslangica gore donus (+ sag, - sol), jiro entegrali
    private var relYawRad = 0.0            // entegre edilen goreli yon (rad)
    private var prevGyroNanos = 0L         // jiro dt icin onceki zaman
    @Volatile var accCount = 0
    @Volatile var gyrCount = 0
    @Volatile var magCount = 0
    @Volatile var oriCount = 0
    @Volatile var gpsCount = 0
    @Volatile var hasGyro = false
    @Volatile var hasMag = false
    @Volatile var hasOri = false

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var t0Nanos = 0L

    private var wAcc: BufferedWriter? = null
    private var wGyr: BufferedWriter? = null
    private var wMag: BufferedWriter? = null
    private var wOri: BufferedWriter? = null
    private var wLoc: BufferedWriter? = null

    private val sensorListener = object : SensorEventListener {
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        override fun onSensorChanged(e: SensorEvent) {
            val se = (e.timestamp - t0Nanos) / 1e9
            when (e.sensor.type) {
                Sensor.TYPE_LINEAR_ACCELERATION -> {
                    wAcc?.write("${e.timestamp},$se,${e.values[0]},${e.values[1]},${e.values[2]}\n")
                    lastAccMag = sqrt(
                        (e.values[0] * e.values[0] + e.values[1] * e.values[1] +
                                e.values[2] * e.values[2]).toDouble()
                    ).toFloat()
                    accCount++
                }
                Sensor.TYPE_GYROSCOPE -> {
                    wGyr?.write("${e.timestamp},$se,${e.values[0]},${e.values[1]},${e.values[2]}\n")
                    // baslangica gore donus = jiro z entegrali. Durgunken (deadband) entegre etme
                    // -> masada dururken ok tam 0'da kalir, sadece gercek donuste oynar.
                    if (prevGyroNanos != 0L) {
                        val dtg = (e.timestamp - prevGyroNanos) / 1e9
                        val gz = e.values[2]
                        if (kotlin.math.abs(gz) > 0.03f && dtg < 0.5) {   // ~1.7°/s alti = durgun
                            relYawRad += -gz * dtg                         // saga donus = +
                            lastYawRelDeg = Math.toDegrees(relYawRad).toFloat()
                        }
                    }
                    prevGyroNanos = e.timestamp
                    gyrCount++
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    wMag?.write("${e.timestamp},$se,${e.values[0]},${e.values[1]},${e.values[2]}\n")
                    magCount++
                }
                Sensor.TYPE_ROTATION_VECTOR -> {
                    val q = FloatArray(4)
                    SensorManager.getQuaternionFromVector(q, e.values) // q[0]=w, [1]=x, [2]=y, [3]=z
                    wOri?.write("${e.timestamp},$se,${q[0]},${q[1]},${q[2]},${q[3]}\n")
                    val w = q[0]; val x = q[1]; val y = q[2]; val z = q[3]
                    // pusula icin mutlak yon (manyetik). Goreli yon jirodan gelir (yukarida).
                    lastYawDeg = Math.toDegrees(
                        atan2((2 * (w * z + x * y)).toDouble(), (1 - 2 * (y * y + z * z)).toDouble())
                    ).toFloat()
                    oriCount++
                }
            }
        }
    }

    private val locListener = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            val se = (loc.elapsedRealtimeNanos - t0Nanos) / 1e9
            val spd = if (loc.hasSpeed()) loc.speed else -1f
            val brg = if (loc.hasBearing()) loc.bearing else -1f
            val hacc = if (loc.hasAccuracy()) loc.accuracy else -1f
            val sacc = if (android.os.Build.VERSION.SDK_INT >= 26 && loc.hasSpeedAccuracy())
                loc.speedAccuracyMetersPerSecond else -1f
            val bacc = if (android.os.Build.VERSION.SDK_INT >= 26 && loc.hasBearingAccuracy())
                loc.bearingAccuracyDegrees else -1f
            wLoc?.write(
                "${loc.time},$se,${loc.latitude},${loc.longitude},${loc.altitude}," +
                        "$hacc,$spd,$brg,$sacc,$bacc\n"
            )
            lastSpeed = spd; lastBearing = brg; lastHacc = hacc; gpsCount++
        }

        @Deprecated("deprecated in API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private fun open(name: String, header: String): BufferedWriter {
        val w = BufferedWriter(FileWriter(File(sessionDir, name)))
        w.write(header); w.write("\n")
        return w
    }

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return false
        accCount = 0; gyrCount = 0; magCount = 0; oriCount = 0; gpsCount = 0
        lastYawRelDeg = 0f; relYawRad = 0.0; prevGyroNanos = 0L   // goreli yon kayit basinda sifir

        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = File(context.getExternalFilesDir(null), "session_$ts")
        dir.mkdirs()
        sessionDir = dir
        t0Nanos = SystemClock.elapsedRealtimeNanos()

        wAcc = open("Accelerometer.csv", "time,seconds_elapsed,x,y,z")
        wGyr = open("Gyroscope.csv", "time,seconds_elapsed,x,y,z")
        wMag = open("Magnetometer.csv", "time,seconds_elapsed,x,y,z")
        wOri = open("Orientation.csv", "time,seconds_elapsed,qw,qx,qy,qz")
        wLoc = open(
            "Location.csv",
            "time,seconds_elapsed,latitude,longitude,altitude,horizontalAccuracy," +
                    "speed,bearing,speedAccuracy,bearingAccuracy"
        )

        val ht = HandlerThread("sensor-io").apply { start() }
        thread = ht
        val h = Handler(ht.looper)
        handler = h

        val acc = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val gyr = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val ori = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        hasGyro = gyr != null; hasMag = mag != null; hasOri = ori != null
        acc?.let { sm.registerListener(sensorListener, it, sensorDelay, h) }
        gyr?.let { sm.registerListener(sensorListener, it, sensorDelay, h) }
        mag?.let { sm.registerListener(sensorListener, it, sensorDelay, h) }
        ori?.let { sm.registerListener(sensorListener, it, sensorDelay, h) }

        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locListener, ht.looper)
        } catch (e: SecurityException) {
            return false
        } catch (e: IllegalArgumentException) {
            // GPS provider yoksa yine de sensorleri loglamaya devam et
        }

        running = true
        return true
    }

    fun stop() {
        if (!running) return
        running = false
        sm.unregisterListener(sensorListener)
        try { lm.removeUpdates(locListener) } catch (_: Exception) {}
        val h = handler
        val ht = thread
        h?.post {
            listOf(wAcc, wGyr, wMag, wOri, wLoc).forEach {
                try { it?.flush(); it?.close() } catch (_: Exception) {}
            }
            ht?.quitSafely()
        }
    }
}
