package com.srhtbynkln.kalmanlogger

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import android.webkit.WebView
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.srhtbynkln.kalmanlogger.databinding.ActivityMainBinding
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private var logger: SensorLogger? = null
    private var lastSession: File? = null
    private val ui = Handler(Looper.getMainLooper())

    // --- canli GPS kalite onizlemesi (kayit oncesi de calisir) ---
    private val lm by lazy { getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    private val sm by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    @Volatile private var pvHacc = -1f
    @Volatile private var pvSpeed = -1f
    @Volatile private var pvBearing = Float.NaN
    @Volatile private var pvLat = 0.0
    @Volatile private var pvLon = 0.0
    @Volatile private var pvLastMs = 0L
    @Volatile private var satUsed = 0
    @Volatile private var satTotal = 0
    private var gpsTicking = false
    private var armed = false         // GPS hazir olunca otomatik baslat bekliyor mu
    private val readyHacc = 15f       // m; bunun altinda "HAZIR"

    // canli sensor monitoru (kayittan bagimsiz, ekranda gostermek icin)
    @Volatile private var mAx = 0f; @Volatile private var mAy = 0f; @Volatile private var mAz = 0f
    @Volatile private var mAmag = 0f
    @Volatile private var mGx = 0f; @Volatile private var mGy = 0f; @Volatile private var mGz = 0f
    @Volatile private var mMx = 0f; @Volatile private var mMy = 0f; @Volatile private var mMz = 0f
    @Volatile private var mYaw = Float.NaN
    @Volatile private var mRelYaw = 0.0          // baslangica gore donus (kayit basinda sifirlanir)
    private var prevGyroNs = 0L
    // son oryantasyon kuaterniyonu (govde->dunya ENU) - IMU rota icin ivmeyi dondurur
    @Volatile private var qw = 1f; @Volatile private var qx = 0f
    @Volatile private var qy = 0f; @Volatile private var qz = 0f
    // IMU dead-reckoning durumu (jiro+ivme ile rota)
    private var drVE = 0.0; private var drVN = 0.0; private var drPE = 0.0; private var drPN = 0.0
    private var prevAccNs = 0L

    // kaydedilen rota (GPS yorungesi)
    private val trkLat = ArrayList<Double>()
    private val trkLon = ArrayList<Double>()
    private var refLat = Double.NaN
    private var refLon = Double.NaN
    // IMU dead-reckoning rotasi (baslangica gore yerel metre: Dogu, Kuzey) - haritada
    // GPS baslangic referansiyla cografi koordinata cevrilip ustune bindirilir
    private val imuTrkE = ArrayList<Double>()
    private val imuTrkN = ArrayList<Double>()

    private val pvLoc = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            pvHacc = if (loc.hasAccuracy()) loc.accuracy else -1f
            pvSpeed = if (loc.hasSpeed()) loc.speed else -1f
            pvBearing = if (loc.hasBearing()) loc.bearing else Float.NaN
            pvLat = loc.latitude; pvLon = loc.longitude
            pvLastMs = SystemClock.elapsedRealtime()
            // rotaya ekle (sadece makul dogruluktaki fix'ler)
            if (loc.hasAccuracy() && loc.accuracy in 0.01f..50f) {
                if (refLat.isNaN()) { refLat = loc.latitude; refLon = loc.longitude }
                trkLat.add(loc.latitude); trkLon.add(loc.longitude)
                val d2r = Math.PI / 180; val re = 6378137.0
                val e = ((loc.longitude - refLon) * d2r * re * Math.cos(refLat * d2r)).toFloat()
                val n = ((loc.latitude - refLat) * d2r * re).toFloat()
                b.trackView.addGps(e, n)
            }
        }
        @Deprecated("deprecated") override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
        override fun onProviderEnabled(p: String) {}
        override fun onProviderDisabled(p: String) {}
    }
    private val gnss = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            val n = status.satelliteCount
            for (i in 0 until n) if (status.usedInFix(i)) used++
            satUsed = used; satTotal = n
        }
    }
    private val sensorMon = object : SensorEventListener {
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        override fun onSensorChanged(e: SensorEvent) {
            when (e.sensor.type) {
                Sensor.TYPE_LINEAR_ACCELERATION -> {
                    mAx = e.values[0]; mAy = e.values[1]; mAz = e.values[2]
                    mAmag = Math.sqrt((mAx*mAx + mAy*mAy + mAz*mAz).toDouble()).toFloat()
                    // --- IMU dead-reckoning rota (jiro yonu + ivme cift entegrali) ---
                    // ivmeyi oryantasyon kuaterniyonuyla dunya (ENU) cercevesine cevir
                    val ww = qw.toDouble(); val xx = qx.toDouble(); val yy = qy.toDouble(); val zz = qz.toDouble()
                    val ax = mAx.toDouble(); val ay = mAy.toDouble(); val az = mAz.toDouble()
                    val aE = (1-2*(yy*yy+zz*zz))*ax + 2*(xx*yy-zz*ww)*ay + 2*(xx*zz+yy*ww)*az
                    val aN = 2*(xx*yy+zz*ww)*ax + (1-2*(xx*xx+zz*zz))*ay + 2*(yy*zz-xx*ww)*az
                    if (prevAccNs != 0L) {
                        val dt = (e.timestamp - prevAccNs) / 1e9
                        if (dt in 0.0..0.2) {
                            // ZUPT: durgunsa hizi sifirla (suruklenmeyi azalt)
                            if (mAmag < 0.4f && Math.abs(mGz) < 0.08f) { drVE = 0.0; drVN = 0.0 }
                            else {
                                // dunya-ivmesi olu-bandi: kucuk gurultuyu entegre etme (yavas kaymayi keser)
                                val dz = 0.15
                                val aEz = if (Math.abs(aE) < dz) 0.0 else aE
                                val aNz = if (Math.abs(aN) < dz) 0.0 else aN
                                drPE += drVE*dt + 0.5*aEz*dt*dt; drPN += drVN*dt + 0.5*aNz*dt*dt
                                drVE += aEz*dt; drVN += aNz*dt
                                // hiz sizintisi (tau~3s): birikmis yanal suruklenmeyi bleed et
                                val leak = (1.0 - dt / 3.0).coerceIn(0.0, 1.0)
                                drVE *= leak; drVN *= leak
                            }
                            b.trackView.addImu(drPE.toFloat(), drPN.toFloat())
                            imuTrkE.add(drPE); imuTrkN.add(drPN)
                        }
                    }
                    prevAccNs = e.timestamp
                }
                Sensor.TYPE_GYROSCOPE -> {
                    mGx = e.values[0]; mGy = e.values[1]; mGz = e.values[2]
                    if (prevGyroNs != 0L) {
                        val dt = (e.timestamp - prevGyroNs) / 1e9
                        if (Math.abs(mGz) > 0.03f && dt < 0.5) mRelYaw += -mGz * dt
                    }
                    prevGyroNs = e.timestamp
                }
                Sensor.TYPE_MAGNETIC_FIELD -> { mMx = e.values[0]; mMy = e.values[1]; mMz = e.values[2] }
                Sensor.TYPE_ROTATION_VECTOR -> {
                    val q = FloatArray(4)
                    SensorManager.getQuaternionFromVector(q, e.values)
                    qw = q[0]; qx = q[1]; qy = q[2]; qz = q[3]
                    mYaw = Math.toDegrees(Math.atan2(
                        (2*(q[0]*q[3] + q[1]*q[2])).toDouble(),
                        (1 - 2*(q[2]*q[2] + q[3]*q[3])).toDouble())).toFloat()
                }
            }
        }
    }

    private val rates = intArrayOf(
        SensorManager.SENSOR_DELAY_NORMAL,
        SensorManager.SENSOR_DELAY_UI,
        SensorManager.SENSOR_DELAY_GAME,
        SensorManager.SENSOR_DELAY_FASTEST
    )

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) beginRecordingFlow()
            else log("Konum izni reddedildi - GPS loglanamaz.")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            listOf("Normal (~5 Hz)", "UI (~16 Hz)", "Oyun (~50 Hz)", "En hizli")
        ).also { b.spinRate.adapter = it }
        b.spinRate.setSelection(2) // varsayilan: Oyun (~50 Hz)

        // --- Kalici ayar: GPS hazir olunca otomatik baslat (bir kere sec, hep hatirlasin) ---
        val prefs = getSharedPreferences("kl", Context.MODE_PRIVATE)
        b.chkAutoStart.isChecked = prefs.getBoolean("autoStart", true)
        b.chkAutoStart.setOnCheckedChangeListener { _, v ->
            prefs.edit().putBoolean("autoStart", v).apply()
        }

        b.btnRecord.setOnClickListener { toggleRecording() }
        b.btnShare.setOnClickListener { shareLast() }
        b.btnClear.setOnClickListener {
            b.txtLog.text = ""
            log("Loglar temizlendi.")
        }

        ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            listOf("GPS", "IMU (jiro+ivme)", "İkisi (GPS+IMU)")
        ).also { b.spinTrackSrc.adapter = it }
        b.spinTrackSrc.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                b.trackView.setSourceMode(pos)
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }

        b.webMap.settings.javaScriptEnabled = true
        b.btnMap.setOnClickListener { showRouteMap() }
        // Yon belirtecine dokununca yonu sifirla (su anki yon = baslangic/Duz)
        b.relHeading.setOnClickListener {
            mRelYaw = 0.0
            log("Yön sıfırlandı: şu anki yön artık 'Düz' (0°).")
        }
        // Track'e dokununca her durumda gorseli sifirla (rota + IMU + harita)
        b.trackView.setOnClickListener {
            resetTrack()
            log("Rota görseli sıfırlandı.")
        }
    }

    private fun resetTrack() {
        b.trackView.clear()
        trkLat.clear(); trkLon.clear(); refLat = Double.NaN; refLon = Double.NaN
        imuTrkE.clear(); imuTrkN.clear()
        drVE = 0.0; drVN = 0.0; drPE = 0.0; drPN = 0.0; prevAccNs = 0L
        b.webMap.visibility = android.view.View.GONE
    }

    /** Secili kaynaga gore (GPS / IMU / ikisi) rotayi OSM haritasinda gosterir.
     *  IMU rotasi, GPS baslangic noktasi referansiyla cografi koordinata cevrilir. */
    private fun showRouteMap() {
        val mode = b.spinTrackSrc.selectedItemPosition   // 0=GPS, 1=IMU, 2=ikisi
        val wantGps = mode == 0 || mode == 2
        val wantImu = mode == 1 || mode == 2

        fun seriesJson(pts: List<Pair<Double, Double>>): String {
            val sb = StringBuilder("[")
            pts.forEachIndexed { i, (la, lo) ->
                if (i > 0) sb.append(",")
                sb.append("[").append(la).append(",").append(lo).append("]")
            }
            return sb.append("]").toString()
        }

        val gps = if (wantGps) trkLat.indices.map { trkLat[it] to trkLon[it] } else emptyList()

        // IMU yerel metre -> cografi koordinat (GPS baslangic referansi gerekli)
        val imu = ArrayList<Pair<Double, Double>>()
        if (wantImu && !refLat.isNaN() && imuTrkE.size >= 2) {
            val d2r = Math.PI / 180; val re = 6378137.0
            for (i in imuTrkE.indices) {
                val lat = refLat + Math.toDegrees(imuTrkN[i] / re)
                val lon = refLon + Math.toDegrees(imuTrkE[i] / (re * Math.cos(refLat * d2r)))
                imu.add(lat to lon)
            }
        }

        val hasGps = gps.size >= 2
        val hasImu = imu.size >= 2
        if (!hasGps && !hasImu) {
            log(if (mode == 1) "IMU rotası için kayıt + GPS başlangıç referansı gerekli."
                else "Harita için yeterli GPS noktası yok (GPS fix bekleniyor).")
            return
        }
        b.webMap.visibility = android.view.View.VISIBLE
        b.webMap.loadDataWithBaseURL(
            "https://www.openstreetmap.org/",
            leafletHtml(if (hasGps) seriesJson(gps) else "[]", if (hasImu) seriesJson(imu) else "[]"),
            "text/html", "UTF-8", null
        )
        val parts = listOfNotNull(
            if (hasGps) "GPS ${gps.size} (mavi)" else null,
            if (hasImu) "IMU ${imu.size} (turuncu)" else null
        )
        log("Harita: ${parts.joinToString(" + ")} nokta gösteriliyor.")
    }

    private fun leafletHtml(gpsJson: String, imuJson: String): String = """
        <!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
        <style>html,body,#map{height:100%;margin:0}</style></head><body><div id="map"></div>
        <script>
          var gps=$gpsJson, imu=$imuJson;
          var map=L.map('map');
          L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
             {maxZoom:19,attribution:'© OpenStreetMap'}).addTo(map);
          var all=[];
          function addLine(pts,color,label){
            if(pts.length<2) return;
            L.polyline(pts,{color:color,weight:5}).addTo(map);
            L.circleMarker(pts[0],{color:'green',radius:7,fillOpacity:1}).addTo(map).bindTooltip(label+' başlangıç');
            L.circleMarker(pts[pts.length-1],{color:'red',radius:7,fillOpacity:1}).addTo(map).bindTooltip(label+' bitiş');
            all=all.concat(pts);
          }
          addLine(gps,'#1f77b4','GPS');
          addLine(imu,'#ff7f0e','IMU');
          if(all.length) map.fitBounds(L.polyline(all).getBounds().pad(0.2));
        </script></body></html>
    """.trimIndent()

    private fun toggleRecording() {
        when {
            logger?.running == true -> stopRecording()
            armed -> cancelArm()                         // bekliyorsa iptal
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED -> beginRecordingFlow()
            else -> permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun gpsReady(): Boolean {
        val fresh = pvLastMs > 0 && (SystemClock.elapsedRealtime() - pvLastMs) < 5000
        return fresh && pvHacc > 0 && pvHacc <= readyHacc && satUsed >= 4
    }

    /** Izin var; otomatik baslatma acik ve GPS hazir degilse 'silahlan', degilse hemen basla. */
    private fun beginRecordingFlow() {
        startGpsPreview()
        if (b.chkAutoStart.isChecked && !gpsReady()) {
            armed = true
            b.btnRecord.text = "GPS BEKLENİYOR… (iptal için bas)"
            b.txtStatus.text = "Durum: GPS bekleniyor → hazır olunca otomatik başlar"
            log("Otomatik başlatma: GPS hazır olunca kayıt başlayacak.")
        } else {
            startRecording()
        }
    }

    private fun cancelArm() {
        armed = false
        b.btnRecord.text = "KAYDI BAŞLAT"
        b.txtStatus.text = "Durum: HAZIR"
        log("Otomatik başlatma iptal edildi.")
    }

    private fun startRecording() {
        val delay = rates[b.spinRate.selectedItemPosition]
        val l = SensorLogger(this, delay)
        if (!l.start()) { log("Kayit baslatilamadi."); return }
        logger = l
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        b.btnRecord.text = "KAYDI DURDUR"
        b.txtStatus.text = "Durum: KAYIT EDILIYOR"
        b.spinRate.isEnabled = false
        mRelYaw = 0.0; prevGyroNs = 0L      // baslangica gore donus kayit aninda sifirlanir
        resetTrack()                         // rota + IMU + harita sifirla
        log("Kayit basladi. gyro=${l.hasGyro} mag=${l.hasMag} orient=${l.hasOri}")
    }

    private fun stopRecording() {
        val l = logger ?: return
        l.stop()
        lastSession = l.sessionDir
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        b.btnRecord.text = "KAYDI BASLAT"
        b.txtStatus.text = "Durum: HAZIR"
        b.spinRate.isEnabled = true
        log("Kayit durdu -> ${l.sessionDir?.name}\nKonum: ${l.sessionDir?.absolutePath}")
        log("Veriyi göndermek için 'Tüm veriyi paylaş' butonunu kullanabilirsiniz.")
    }

    @SuppressLint("MissingPermission")
    private fun startGpsPreview() {
        // sensorler (izin gerektirmez) - her zaman canli goster
        sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.let { sm.registerListener(sensorMon, it, SensorManager.SENSOR_DELAY_UI) }
        sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { sm.registerListener(sensorMon, it, SensorManager.SENSOR_DELAY_UI) }
        sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let { sm.registerListener(sensorMon, it, SensorManager.SENSOR_DELAY_UI) }
        sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sm.registerListener(sensorMon, it, SensorManager.SENSOR_DELAY_UI) }
        // GPS (izin varsa)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            try {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, pvLoc, mainLooper)
                lm.registerGnssStatusCallback(gnss, Handler(mainLooper))
            } catch (_: Exception) {}
        }
        if (!gpsTicking) { gpsTicking = true; gpsTick() }
    }

    private fun stopGpsPreview() {
        gpsTicking = false
        try { sm.unregisterListener(sensorMon) } catch (_: Exception) {}
        try { lm.removeUpdates(pvLoc) } catch (_: Exception) {}
        try { lm.unregisterGnssStatusCallback(gnss) } catch (_: Exception) {}
    }

    private fun gpsTick() {
        if (!gpsTicking) return
        val fresh = pvLastMs > 0 && (SystemClock.elapsedRealtime() - pvLastMs) < 5000
        val ready = gpsReady()
        if (armed && ready) { armed = false; startRecording() }   // hazir -> otomatik basla

        // --- GPS durum paneli ---
        val badge = when {
            !fresh -> "GPS: BEKLE ⏳  (fix bekleniyor)"
            ready -> "GPS: HAZIR ✅  — başlayabilirsin"
            else -> "GPS: BEKLE ⏳  (doğruluk iyileşiyor)"
        }
        b.txtGps.text = badge + "\n" +
            "hAcc: " + (if (pvHacc < 0) "--" else String.format("%.0f m", pvHacc)) +
            "   Uydu: $satUsed/$satTotal" +
            "   hız: " + (if (pvSpeed < 0) "--" else String.format("%.1f m/s", pvSpeed)) +
            (if (fresh) String.format("\nkonum: %.5f, %.5f", pvLat, pvLon) else "")

        // --- sensor detay paneli ---
        val rec = if (logger?.running == true)
            "\n──────────\nKAYIT: acc=${logger!!.accCount} gyr=${logger!!.gyrCount} " +
            "mag=${logger!!.magCount} ori=${logger!!.oriCount} gps=${logger!!.gpsCount}" else ""
        b.txtLive.text =
            String.format("İvme   x%+.2f  y%+.2f  z%+.2f   |a| %.2f m/s²\n", mAx, mAy, mAz, mAmag) +
            String.format("Jiro   x%+.3f y%+.3f z%+.3f rad/s\n", mGx, mGy, mGz) +
            String.format("Manyeto x%+.0f y%+.0f z%+.0f µT\n", mMx, mMy, mMz) +
            String.format("Yön    mutlak %s   başlangıca göre %+.0f°",
                if (mYaw.isNaN()) "--" else String.format("%.0f°", (mYaw + 360f) % 360f),
                Math.toDegrees(mRelYaw)) + rec

        // --- gorsel gostergeler ---
        val moving = pvSpeed > 0.5f && !pvBearing.isNaN()
        val headDeg = if (moving) pvBearing else if (!mYaw.isNaN()) (mYaw + 360f) % 360f else Float.NaN
        b.liveCompass.update(headDeg, moving, if (pvSpeed < 0) 0f else pvSpeed, mAmag)
        b.relHeading.update(Math.toDegrees(mRelYaw).toFloat())

        ui.postDelayed({ gpsTick() }, 400)
    }

    override fun onResume() {
        super.onResume()
        startGpsPreview()
    }

    override fun onPause() {
        super.onPause()
        if (logger?.running != true) stopGpsPreview()   // kayit yokken onizlemeyi durdur
    }

    private fun shareLast() {
        val dir = lastSession ?: logger?.sessionDir
        if (dir == null) { log("Once bir kayit alin."); return }
        log("Paylasim icin zip hazirlaniyor: ${dir.name} ...")
        Thread {
            try {
                val zip = File(cacheDir, "${dir.name}.zip")
                ZipOutputStream(BufferedOutputStream(FileOutputStream(zip))).use { z ->
                    dir.listFiles()?.filter { it.isFile }?.forEach { fch ->
                        z.putNextEntry(ZipEntry(fch.name))
                        fch.inputStream().use { it.copyTo(z) }
                        z.closeEntry()
                    }
                }
                val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", zip)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, dir.name)
                    putExtra(Intent.EXTRA_TEXT, "KalmanLogger kaydi: ${dir.name}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ui.post { startActivity(Intent.createChooser(send, "Veriyi paylaş")) }
            } catch (e: Exception) {
                ui.post { log("Paylasim hatasi: ${e.message}") }
            }
        }.start()
    }

    private fun log(s: String) {
        b.txtLog.text = "› $s\n${b.txtLog.text}".take(2000)
    }

    override fun onDestroy() {
        super.onDestroy()
        logger?.stop()
    }
}
