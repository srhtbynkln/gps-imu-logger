package com.srhtbynkln.gpsimulogger

/**
 * Basit 1B sabit-hiz Kalman filtresi. Durum: [konum, hiz].
 *
 * - predict(a, dt): IMU ivmesini kontrol girisi olarak kullanip durumu ileri tasir
 *   (IMU "besler"). GPS gelmese de cagrilabilir -> tahmin IMU ile devam eder.
 * - update(z): GPS konum olcumuyle durumu duzeltir.
 *
 * Dogu (E) ve Kuzey (N) eksenleri bagimsiz oldugundan iki ayri ornek kullanilir.
 * Kasitli olarak sade: tek eksende 2x2 kovaryans, harici kutuphane yok.
 *
 * q: surec gurultusu (IMU'ya ne kadar guvenildigi - buyukse GPS'e daha cok yaslanir)
 * r: olcum gurultusu (GPS'e ne kadar guvenildigi - buyukse GPS daha az duzeltir)
 */
class Kf1D(private val q: Double = 0.5, private val r: Double = 9.0) {
    var p = 0.0; private set   // konum (m)
    var v = 0.0; private set   // hiz (m/s)

    // kovaryans 2x2: [[c00, c01], [c10, c11]]
    private var c00 = 1.0; private var c01 = 0.0
    private var c10 = 0.0; private var c11 = 1.0
    private var inited = false

    fun isInited() = inited

    fun reset() {
        p = 0.0; v = 0.0
        c00 = 1.0; c01 = 0.0; c10 = 0.0; c11 = 1.0
        inited = false
    }

    /** Ilk GPS fix'i ile baslat (konum = olcum, hiz = 0). */
    fun init(pos: Double) {
        p = pos; v = 0.0
        c00 = 1.0; c01 = 0.0; c10 = 0.0; c11 = 1.0
        inited = true
    }

    /** Durumu ivme ve dt ile ileri tasir. A=[[1,dt],[0,1]], B=[0.5dt^2, dt]. */
    fun predict(a: Double, dt: Double) {
        if (!inited || dt <= 0.0) return
        // durum
        p += v * dt + 0.5 * a * dt * dt
        v += a * dt
        // kovaryans: C = A C A^T + Q
        val a00 = c00 + dt * c10; val a01 = c01 + dt * c11   // (A C) ust satir
        val a10 = c10;            val a11 = c11               // (A C) alt satir
        val n00 = a00 + a01 * dt; val n01 = a01               // (A C) A^T
        val n10 = a10 + a11 * dt; val n11 = a11
        c00 = n00 + q * dt * dt; c01 = n01
        c10 = n10;               c11 = n11 + q
    }

    /** GPS konum olcumuyle duzelt. H=[1,0]. Ilk cagri filtreyi baslatir. */
    fun update(z: Double) {
        if (!inited) { init(z); return }
        val s = c00 + r                 // S = H C H^T + R
        val k0 = c00 / s; val k1 = c10 / s   // Kalman kazanci K = C H^T / S
        val y = z - p                   // yenilik
        p += k0 * y; v += k1 * y
        // C = (I - K H) C
        val n00 = (1 - k0) * c00; val n01 = (1 - k0) * c01
        val n10 = c10 - k1 * c00; val n11 = c11 - k1 * c01
        c00 = n00; c01 = n01; c10 = n10; c11 = n11
    }
}
