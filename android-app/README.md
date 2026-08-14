# CreatorPulse Android

Native Android shell for CreatorPulse.

- Package: `com.creatorpulse.app`
- Min SDK: 26
- Target/compile SDK: 35
- Production backend: `https://creatorpulse.creatorpulseapp.workers.dev`
- Uses Android WebView only for the CreatorPulse origin.
- External OAuth/payment/web links open in the device browser.
- Session cookies remain in Android WebView storage across normal app restarts.
- File upload and authenticated downloads are supported.
- Offline mode never invents analytics; it displays a designed offline state when server features are unavailable.

## Build

Open `android-app` in Android Studio and build the `app` module, or run with Gradle 8.9 + JDK 17:

```text
gradle :app:assembleDebug
```

The debug APK is created at:

```text
android-app/app/build/outputs/apk/debug/app-debug.apk
```

That path is only valid after a successful Gradle build.
