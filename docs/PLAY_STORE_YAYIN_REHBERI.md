# Google Play Store Yayın Rehberi — GPS, IMU & Hybrid Sensor Logger

Bu rehber, uygulamayı sıfırdan Google Play'de yayına almak için adım adım yapılacakları içerir.
Son güncelleme: 2026-06-03.

## Sabit bilgiler (değiştirme)

| Alan | Değer |
|---|---|
| Uygulama adı | GPS, IMU & Hybrid Sensor Logger |
| Paket adı (applicationId) | `com.srhtbynkln.gpsimulogger` *(Play'de KALICI — asla değiştirme)* |
| Gizlilik politikası URL | https://srhtbynkln.github.io/gps-imu-hybrid-sensor-logger/privacy-policy.html |
| Play Developer Hesap Kimliği | 7877616481470677961 |
| Release keystore | `gps-imu-release.jks` (alias `gpsimu`) — bkz. memory `gps_imu_release_keystore.md` |
| Mevcut sürüm | versionCode 1 / versionName "1.0" |

---

## 0. Ön koşullar (BİTTİ ✅ / yapılacak)

- [x] Public GitHub repo + MIT lisans
- [x] Gizlilik politikası canlı (GitHub Pages, yukarıdaki URL)
- [x] Release imzalama keystore üretildi (`gps-imu-release.jks`) ve yedeklendi (Claude memory)
- [x] İmzalı **AAB** üretildi: `app/build/outputs/bundle/release/app-release.aab`
- [ ] Play Console'da geliştirici hesabı aktif ($25 tek seferlik kayıt ücreti ödenmiş olmalı)
- [ ] Mağaza görselleri hazır (aşağıda Adım 4)

> Not: README'de paket adı hâlâ `com.srhtbynkln.kalmanlogger` yazıyor (bayat) —
> gerçek `gpsimulogger`. İstersen düzelt; yayını etkilemez.

### ⚠️ Yayın öncesi DÜZELTİLMESİ gereken hata: OSM harita döşemeleri engelli

Harita görünümü (`MainActivity.kt` `leafletHtml`) bir WebView içinde Leaflet ile
`{s}.tile.openstreetmap.org`'dan döşeme çekiyor. WebView varsayılan User-Agent
kullandığı için OSM döşeme sunucusu **"Access blocked"** döndürüyor (test edildi).
Bu hem görünür bir kullanıcı hatası hem de OSM Tile Usage Policy ihlali.

**Asgari düzeltme:** Map WebView'ine geçerli, uygulamayı tanımlayan bir User-Agent ata:
```kotlin
b.webMap.settings.userAgentString =
    "gps-imu-hybrid-sensor-logger/1.0 (+https://github.com/srhtbynkln/gps-imu-hybrid-sensor-logger)"
```
**Daha doğrusu:** Dağıtılan uygulamada ham OSM döşemesi gömme; API anahtarlı bir
sağlayıcı (MapTiler/Thunderforest) kullan ya da kendi tile proxy'ni koy. Düşük hacimli
açık kaynak demo için User-Agent + atıf asgari kabul edilebilir.

---

## 1. İmzalı App Bundle (AAB) üret

Play Store APK değil **AAB** ister. Zaten üretildi; tekrar üretmek için:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\openjdk\jdk-21.0.8"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
cd C:\Users\RSMLAB1\Documents\GitHub\srhtbynkln\gps-imu-hybrid-sensor-logger
.\gradlew.bat bundleRelease
# Çıktı: app\build\outputs\bundle\release\app-release.aab
```

`keystore.properties` kök dizinde olmalı (gitignore'lu); yoksa imzasız üretir ve Play reddeder.

---

## 2. Play App Signing'i anla (ÖNEMLİ)

- Play'e yüklediğin keystore (`gps-imu-release.jks`) **upload key** olur.
- Google, gerçek **app signing key**'i kendi tarafında üretip saklar (Play App Signing — yeni uygulamalarda zorunlu/varsayılan).
- Sonuç: upload key'i kaybedersen Google üzerinden sıfırlayabilirsin, ama yine de
  `gps-imu-release.jks` + şifresini **güvende tut** (şifre repoda DEĞİL; gitignore'lu
  `keystore.properties`'te ve özel notlarda) — bundan sonraki
  her güncellemeyi bu anahtarla imzalayacaksın.

---

## 3. Play Console'da uygulama oluştur

1. https://play.google.com/console → hesabına gir.
2. **Tüm uygulamalar → Uygulama oluştur**.
3. Doldur:
   - Uygulama adı: `GPS, IMU & Hybrid Sensor Logger`
   - Varsayılan dil: Türkçe veya İngilizce (tercihine göre)
   - Uygulama / Oyun: **Uygulama**
   - Ücretsiz / Ücretli: **Ücretsiz** (sonradan ücretliye çevrilemez)
   - Bildirimleri (developer program policies + US export laws) onayla → **Uygulama oluştur**.

---

## 4. Mağaza girişi (Store listing) + görseller

**Main store listing** bölümünde:
- **Kısa açıklama** (≤80 karakter): örn. "GPS, IMU ve hibrit Kalman füzyonu ile telefon konumunu karşılaştır."
- **Tam açıklama** (≤4000 karakter): README'nin Türkçe özetini kullan.
- **Uygulama simgesi**: 512×512 PNG (32-bit, alfa).
- **Özellik grafiği (Feature graphic)**: 1024×500 PNG/JPG.
- **Telefon ekran görüntüleri**: en az **2 adet** (önerilen 4-8). Cihazdan al:
  ```powershell
  $adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
  & $adb -s R6CX7011LZR exec-out screencap -p > C:\Users\RSMLAB1\Desktop\ss1.png
  ```
  (Ana ekran, harita görünümü, sensör paneli, CSV/paylaşım ekranı önerilir.)

---

## 5. Uygulama içeriği (App content) — politika beyanları

Sol menü **Policy → App content**. Sırayla doldur:
- **Privacy policy**: yukarıdaki URL'i yapıştır.
- **Ads**: Reklam YOK → "No".
- **App access**: tüm işlevler giriş/şifre olmadan erişilebilir → "All functionality available without special access".
- **Content ratings**: anketi doldur (yardımcı araç, şiddet/içerik yok → büyük olasılıkla "Everyone / 3+").
- **Target audience**: 13+ (çocuklara yönelik DEĞİL → "Designed for families" = Hayır).
- **News app**: Hayır.
- **Data safety**: bkz. Adım 6.
- **Government apps / Financial features / Health**: hepsi Hayır.

---

## 6. Data Safety formu (konum verisi — dikkat)

**App content → Data safety**. Uygulama verileri:

- **Veri topluyor/paylaşıyor musun?**
  - Uygulama veriyi **cihazda tutuyor**, sunucuya YÜKLEMİYOR, analitik YOK, hesap YOK.
  - Play'in tanımına göre "collected" = uygulamadan cihaz dışına çıkan veri. Bizde GPS verisi
    yalnızca kullanıcının kendi paylaşım niyetiyle (share sheet) dışa aktarılır → bu genelde
    **"collected: No"** olarak beyan edilebilir, ama emin değilsen şöyle beyan et:
  - **Location → Approximate/Precise location**: "Collected" işaretle, **amaç: App functionality**,
    **paylaşım: No**, **isteğe bağlı/zorunlu**: kayıt için gerekli.
- **Veri şifreleme (transit)**: cihazdan çıkmadığı için uygulanmıyor; sorulursa "data is not transferred off the device".
- **Kullanıcı verisini silebilir mi?**: Veriler cihazda; uygulamadan silinebilir/oturum dosyaları kullanıcıda.

> Konum (FINE_LOCATION) hassas izin olduğu için Play, gizlilik politikası + Data Safety
> tutarlılığını kontrol eder. Politika metni "veri cihazda kalır, yüklenmez" diyor; formu
> bununla TUTARLI doldur.

---

## 7. Hassas izin beyanı — FINE_LOCATION

`ACCESS_FINE_LOCATION` kullandığın için Play, **Permissions declaration** isteyebilir
(App content içinde "Sensitive app permissions" çıkarsa):
- Çekirdek işlev: kullanıcı kayıt yaparken GPS rotasını loglamak.
- Arka plan konumu (`ACCESS_BACKGROUND_LOCATION`) **kullanmıyorsun** → ekstra video gerekmez.
  (Kullanmadığından emin ol; AndroidManifest'te background location izni OLMAMALI.)

---

## 8. Sürüm oluştur (Production veya önce Internal testing)

Önerilen sıra: **önce Internal testing**, sorun yoksa Production.

1. Sol menü **Testing → Internal testing** (veya **Production**) → **Create new release**.
2. **App signing**: ilk seferde Play App Signing'i kabul et (Google anahtarı yönetir).
3. **App bundles**: `app-release.aab` dosyasını sürükle-bırak.
4. **Release name**: otomatik (1 (1.0)) — bırakabilirsin.
5. **Release notes**: `<tr-TR>İlk sürüm.</tr-TR>` gibi.
6. **Save → Review release → Start rollout**.

Internal testing için test kullanıcılarının e-postalarını "Testers" sekmesine ekle, çıkan
opt-in linkini cihazda aç → Play üzerinden kur.

---

## 9. İncelemeye gönder

- Tüm bölümler yeşil (tamamlandı) olunca **Production → Create new release → Rollout**.
- İlk inceleme genelde birkaç saat–birkaç gün sürer.
- Reddedilirse Play sebebi bildirir (çoğunlukla Data Safety / gizlilik politikası tutarsızlığı
  veya izin gerekçesi) → düzelt, yeni sürüm gönder.

---

## 10. Sonraki güncellemeler

Her yeni sürümde:
1. `app/build.gradle` içinde **versionCode'u artır** (2, 3, ...) ve istersen versionName'i güncelle.
2. `.\gradlew.bat bundleRelease` → yeni AAB.
3. Play Console → yeni release → AAB yükle → rollout.
4. **Aynı `gps-imu-release.jks` ile imzala** (upload key sabit).

---

## Hızlı kontrol listesi

- [ ] Play geliştirici hesabı aktif ($25 ödenmiş)
- [x] İmzalı AAB hazır
- [ ] App oluşturuldu (paket `com.srhtbynkln.gpsimulogger`)
- [ ] Store listing + simge + feature graphic + ≥2 ekran görüntüsü
- [ ] Privacy policy URL girildi
- [ ] Data safety formu (konum, cihazda kalır) tutarlı dolduruldu
- [ ] Content rating anketi
- [ ] Target audience 13+
- [ ] Internal testing'te denendi
- [ ] Production rollout → incelemeye gönderildi
