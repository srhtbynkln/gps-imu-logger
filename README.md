# GPS &amp; IMU Logger

An open-source Android app that records and visualises a phone's **GPS** and **IMU**
(accelerometer, gyroscope, magnetometer, orientation) data in real time, and lets you
export each session as CSV files.

Built originally as a data-collection tool for comparing Kalman-filter based
sensor-fusion methods (GNSS/IMU integration), released here for general use.

> Türkçe: Telefonun **GPS** ve **IMU** (ivmeölçer, jiroskop, manyetometre, yönelim)
> verilerini gerçek zamanlı kaydeden ve görselleştiren açık kaynak bir Android
> uygulaması. Her oturumu CSV dosyaları olarak dışa aktarır.

## Features

- **Live sensor panel** — accelerometer, gyroscope, magnetometer, heading.
- **GPS quality readout** — horizontal accuracy, satellites used/total, speed; auto-start
  recording once a good fix is acquired.
- **Track view + OpenStreetMap** — draws the recorded route from GPS, and an IMU
  dead-reckoning track (gyro heading + double-integrated acceleration) for comparison.
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

Requirements: Android Studio (Giraffe+) or the command-line Android SDK, JDK 17.

```bash
git clone https://github.com/srhtbynkln/gps-imu-integration.git
cd gps-imu-integration
./gradlew assembleDebug      # debug APK in app/build/outputs/apk/debug/
```

- `compileSdk` / `targetSdk` 34, `minSdk` 24 (Android 7.0+).
- Package: `com.srhtbynkln.kalmanlogger`.

## License

[MIT](LICENSE) © 2026 Serhat Boynukalın
