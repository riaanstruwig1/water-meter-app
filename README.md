# Water Meter

An Android app for keeping track of the house water meter.

## What it does

- **More than one meter.** Add each meter under *Meters* (list icon) with a name, its serial/barcode number, how many black and red wheels it has, and an optional account number. Each meter has its own readings and usage.
- **Photo with every reading, read automatically.** Tap *New reading* and take a photo of the meter. On the phone (no internet needed) the app:
  - scans the **barcode** on the meter label and picks the matching meter. If the barcode is covered or unreadable it falls back to the **printed serial number**, or you pick the meter yourself. A barcode it hasn't seen before can be saved to a meter with one tap.
  - reads the **counter digits** and fills in the reading for you to check. Black wheels are kilolitres, red wheels go after the decimal point. Photos taken sideways or upside down work too.
  - warns you if the photo looks like a different meter from the one selected, or the reading is lower than the previous one.
- **Usage so far this month**, per meter: water used this calendar month (kL and litres), average per day, usage since the previous reading, and since the last submission.
- **Monthly submission date.** In *Settings* choose the day of the month (1–31; in shorter months it falls on the last day) and the time of the reminder.
- **Submission reminder.** On that day you get a notification, and opening the app shows a pop-up listing the latest reading of every meter. Nothing is sent automatically: use *Share readings and photos* to send them via email, WhatsApp or your municipality's app, then tap *Mark as submitted*. Until you do, the reminder repeats daily.

All data stays on the phone (SQLite database plus photos in app storage).

## Install

Every push builds a debug APK with GitHub Actions. Open the **Actions** tab, pick the latest successful *Build* run, and download the `water-meter-debug-apk` artifact. Unzip it, copy `app-debug.apk` to the phone and open it (allow installing from unknown sources when asked).

## Build locally

Open the folder in Android Studio, or run:

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Requires JDK 17 and the Android SDK (compileSdk 35). Minimum Android version is 8.0 (API 26).
