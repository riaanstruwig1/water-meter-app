# Water Meter

An Android app for keeping track of the house water meter.

## What it does

- **More than one meter.** Add each meter under *Meters* (list icon) with a name, its serial/barcode number, how many black and red wheels it has, and an optional account number. Each meter has its own readings and usage.
- **Photo with every reading, read automatically.** Tap *New reading* and take a photo of the meter. On the phone (no internet needed) the app:
  - scans the **barcode** on the meter label and picks the matching meter. If the barcode is covered or unreadable it falls back to the **printed serial number**, or you pick the meter yourself. A barcode it hasn't seen before can be saved to a meter with one tap.
  - reads the **counter digits** and fills in the reading for you to check. Black wheels are kilolitres, red wheels go after the decimal point. Photos taken sideways or upside down work too.
  - warns you if the photo looks like a different meter from the one selected, or the reading is lower than the previous one.
- **Billing period.** Usage is worked out per billing period, which runs from a start day to an end day of the next month (default the 8th to the 9th, set in *Settings*). Use the arrows to look at earlier periods.
- **Readings table**, per meter: date and time of each reading, the meter value, kL used since the previous reading, the days and hours in between, kL per day and the cost. A totals row shows the whole period.
- **Price per kL** (set in *Settings*) is used for the cost of every reading and period, plus a projected cost for the current period.
- **One chart for all meters**: water used so far in the period, a coloured line per meter.
- **Photo records.** Every reading keeps its photo in the app, with date and time, under *Photo history*. A copy with the meter name, date, time and reading stamped on it is also saved to the phone's gallery in *Pictures/Water Meter* (Android 10+, can be switched off in Settings).
- **Serial learning.** The first barcode seen on a meter becomes its serial, and serial-label text from later photos is remembered too, so a meter is still recognised when its barcode is covered or partly unreadable.
- Rename a meter by tapping the pencil next to its name, or under *Meters*.
- **Monthly submission date.** In *Settings* choose the day of the month (1–31; in shorter months it falls on the last day) and the time of the reminder.
- **Submission reminder.** On that day you get a notification, and opening the app shows a pop-up listing the latest reading of every meter. Nothing is sent automatically: use *Share readings and photos* to send them via email, WhatsApp or your municipality's app, then tap *Mark as submitted*. Until you do, the reminder repeats daily.

All data stays on the phone (SQLite database plus photos in app storage).

## Install

Every push builds a debug APK with GitHub Actions. Open the **Actions** tab, pick the latest successful *Build* run, and download the `water-meter-vX.Y.Z` artifact (the last number is the build number and goes up with every build). Unzip it, copy the `.apk` to the phone and open it (allow installing from unknown sources when asked). The installed version is shown under the app title and at the bottom of Settings.

## Build locally

Open the folder in Android Studio, or run:

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Requires JDK 17 and the Android SDK (compileSdk 35). Minimum Android version is 8.0 (API 26).
