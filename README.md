# GPS &amp; IMU Logger

An open-source Android app that shows, in real time, **how a phone's position track
looks using GPS only, using the IMU only, and using the two together** — so you can
see for yourself how each source behaves and how combining them compares.

It records the phone's **GPS** and **IMU** (accelerometer, gyroscope, magnetometer,
orientation) data, draws the route from each source on screen and on an OpenStreetMap
map, and lets you export every session as CSV files.

> Türkçe: Telefonun konum rotasının **yalnızca GPS ile**, **yalnızca IMU ile** ve
> **ikisinin birlikte** çalıştığı halde nasıl göründüğünü gerçek zamanlı gösteren açık
> kaynak bir Android uygulaması — her kaynağın davranışını ve birleşiminin sonucunu
> kullanıcıya görsel olarak sunar. GPS ve IMU (ivmeölçer, jiroskop, manyetometre,
> yönelim) verilerini kaydedip her oturumu CSV olarak dışa aktarır.

## Features

- **Live sensor panel** — accelerometer, gyroscope, magnetometer, heading.
- **GPS quality readout** — horizontal accuracy, satellites used/total, speed; auto-start
  recording once a good fix is acquired.
- **Track view + OpenStreetMap** — pick the source (**GPS only / IMU only / both**) and
  the recorded route is drawn that way, both on the in-app canvas and on the map: GPS
  trajectory (blue) and IMU dead-reckoning track (gyro heading + double-integrated
  acceleration, orange), overlaid so you can compare them directly.
- **CSV logging** — standard per-sensor CSV files per session
  (`Accelerometer.csv`, `Gyroscope.csv`, `Magnetometer.csv`, `Location.csv`,
  `Orientation.csv`).
- **Share** — bundle a session as a `.zip` and send it via WhatsApp, Gmail or any
  share target. No cloud account or sign-in required.

## Permissions & privacy

- **Location** (`ACCESS_FINE_LOCATION`) — used only to log GPS while you are recording;
  data stays on your device.
- **Internet** — used only to load the OpenStreetMap tiles when you open the map view.
- The app does **not** create an account, does **not** upload your data anywhere, and
  has no analytics. You decide if and where to share a recording via the system share sheet.

## Build

Requirements: JDK 17 and the Android SDK (command-line tools are enough — Android
Studio is optional).

```bash
git clone https://github.com/srhtbynkln/gps-imu-logger.git
cd gps-imu-logger
./gradlew assembleDebug      # debug APK in app/build/outputs/apk/debug/
```

- `compileSdk` / `targetSdk` 34, `minSdk` 24 (Android 7.0+).
- Package: `com.srhtbynkln.kalmanlogger`.

## License

[MIT](LICENSE) © 2026 Serhat Boynukalın
