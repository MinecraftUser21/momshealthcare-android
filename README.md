# Mom’s Health Care — Android app

Free, open-source Android app for [Mom’s Health Care](https://momshealthcare.site.je/), a diagnostic
centre and clinic in Thakurpukur, Kolkata. Same content and black-and-white design as the website,
with appointment booking that reaches the clinic inbox (**drpubalisarkar@gmail.com**).

The iPhone, iPad and Mac version lives at [momshealthcare-ios](https://github.com/MinecraftUser21/momshealthcare-ios).

<p>
<img src="docs/screenshots/android-home.png" width="200" alt="Home">
<img src="docs/screenshots/android-appointments.png" width="200" alt="Appointments">
<img src="docs/screenshots/android-pregnancy.png" width="200" alt="Pregnancy tracker">
<img src="docs/screenshots/android-booking.png" width="200" alt="Booking">
<img src="docs/screenshots/android-home-light.png" width="200" alt="Home, light appearance">
</p>

## Download

Get `MomsHealthCare.apk` from the [latest release](../../releases/latest), open it on the phone and
allow “install unknown apps” once. Runs on Android 5.0 to Android 16; about 1.8 MB.

## What’s in the app

- **Book a visit**: pick a service, a day and a time; the clinic calls back to confirm.
  Saved patients fill the form in one tap.
- **Appointments**: every request you’ve sent; add one to your calendar, cancel it, or book again.
- **Pregnancy**: enter your last period (or due date) to see your week, due date and when each
  scan is usually done, with a Book button for each.
- **Services**: what each one is and how to prepare.
- **Clinic**: live open/closed status, opening hours, directions, one-tap call, and a 112 emergency button.
- **Appearance**: Dark, Light or Match phone.
- **Shortcuts**: long-press the app icon for Book, Call clinic and Directions.

Everything stays on the phone. Only a booking or cancellation is sent, and only to the clinic.

## How booking reaches the doctor

The app posts to the website’s own FormSubmit form (`formsubmit.co/ajax/drpubalisarkar@gmail.com`),
which is already activated for that inbox. It uses the website’s field names (Patient Name, Phone,
Service, Preferred Date, Preferred Time, Notes) plus “Booked via: Android app”, so every email looks
the same. If sending fails, the app offers **Try again**, **Use email app** (pre-filled to the doctor)
or **Call**.

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

## Code

| File | What it does |
| --- | --- |
| `MainActivity.kt` | Tabs and the floating Book button |
| `HomeFragment.kt` | Status, next appointment, quick actions, services |
| `AppointmentsFragment.kt` | History, add to calendar, cancel |
| `PregnancyFragment.kt`, `Pregnancy.kt` | Week tracker and scan timeline |
| `ClinicFragment.kt` | Hours, services, emergency, settings |
| `ServiceSheet.kt` | Service details and how to prepare |
| `BookingActivity.kt` | Booking form, same rules as the website |
| `BookingSender.kt` | Sends requests to the clinic inbox; survives rotation |
| `Clinic.kt`, `Store.kt` | Clinic data, Kolkata time, saved data |
| `EcgView.kt`, `Motion.kt` | Heartbeat line, shared motion, toast |

## Low-end phones

- Plain Android views, no Jetpack Compose and no WebView. Vector icons only, no images.
- R8 shrinking, and Inter fonts trimmed to the Latin set (~50 KB each).
- Looping animations switch off automatically on low-RAM or slow phones, in battery saver, and
  when animations are turned off in system settings.

## License

The code is released under the [MIT License](LICENSE). The clinic’s name, logo and written
content belong to Mom’s Health Care; if you reuse this app for another clinic, replace them.

Font and icon credits are in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
