# Water Meter

An Android app for keeping track of the house water meter.

## What it does

- **Photo with every reading.** Tap *New reading*, take a photo of the meter, then type the number you see on it (in kilolitres). The photo is stored with the reading. You can also use a photo from the gallery.
- **Usage so far this month.** Every time you add a reading the home screen updates: water used this calendar month (kL and litres), average per day, usage since the previous reading, and usage since the last reading you submitted. A reading lower than the previous one is flagged as a likely typo.
- **Monthly submission date.** In *Settings* choose the day of the month (1–31; in shorter months it falls on the last day) and the time of the reminder.
- **Submission reminder.** On that day you get a notification, and opening the app shows a pop-up saying the reading must be submitted. Nothing is sent automatically: use *Share reading and photo* to send it via email, WhatsApp or your municipality's app, then tap *Mark as submitted*. Until you do, the reminder repeats daily.
- Optional account/meter number is included in the shared message.

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
