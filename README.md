# Mom’s Health Care — Android app

Native Android app for https://momshealthcare.site.je/ — same content, same black-and-white design,
and appointment booking that lands in **drpubalisarkar@gmail.com**.

- **Download:** get `MomsHealthCare.apk` from the [latest release](../../releases/latest), open it on
  the phone and allow "install unknown apps" once.
- **Runs on:** Android 5.0 (API 21) through Android 16 (API 36). Release APK is ~1.7 MB.
- **Free and open source** under the [MIT License](LICENSE).

## How booking reaches the doctor

The form posts to the website's own FormSubmit form (`formsubmit.co/ajax/drpubalisarkar@gmail.com`),
which is already activated for that inbox. The app uses the same field names as the website
(Patient Name, Phone, Service, Preferred Date, Preferred Time, Notes), plus “Booked via: Android app”,
so the email looks the same whether a patient books on the site or in the app.

If sending fails (no internet, FormSubmit down), the app offers **Try again**, **Use email app**
(a pre-filled email to the doctor) or **Call**.

## Build

Requires JDK 17 and the Android SDK (platform 36).

```
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
./gradlew assembleDebug
```

Release builds are signed with a key described in `keystore.properties`:

```
storeFile=your-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Without that file the release build is signed with the debug key, which is fine for testing.
The official key is never committed (it is in `.gitignore`); every update published here must be
signed with it, or phones will refuse to install it over the previous version.

Every push to `main` is built by GitHub Actions, which uploads a debug APK as a build artifact.

## Structure

| File | What it does |
| --- | --- |
| `MainActivity.kt` | Home: hero, services, “your visit”, booking call-to-action, location, appearance |
| `BookingActivity.kt` | Booking form, validation (same rules as the website), sent screen |
| `BookingSender.kt` | Sends the request on a background thread; survives rotation |
| `Clinic.kt` | Phone, hours, services, time slots, Kolkata-time rules |
| `EcgView.kt` | The animated heartbeat line from the website’s hero |
| `Motion.kt` | Shared easing, scroll reveals, toast; turns looping motion off on low-end phones |

## Low-end phones

- Plain Android views, no Jetpack Compose and no WebView. Vector icons only, no images.
- R8 shrinking, and Inter fonts trimmed to the Latin set (~50 KB each).
- The looping animations (floating tiles, continuous ECG) switch off automatically on low-RAM
  or slow phones, in battery saver, and when the person has turned animations off in
  system settings.

## License

The code is released under the [MIT License](LICENSE). The clinic's name, logo and written
content belong to Mom’s Health Care; if you reuse this app for another clinic, replace them.

Font and icon credits are in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
