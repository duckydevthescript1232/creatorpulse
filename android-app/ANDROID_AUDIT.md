# CreatorPulse Android audit

## Architecture decision

CreatorPulse uses Vinext/React with Cloudflare D1 and server-side API routes. Those server routes, D1, YouTube OAuth, token encryption, and live YouTube data cannot be embedded safely into an offline APK. The Android app therefore uses a native Android shell for the production CreatorPulse origin while keeping all secrets and database operations on Cloudflare.

## Android-specific fixes implemented

- Native Android project (`com.creatorpulse.app`) with no browser chrome.
- Production HTTPS origin only; cleartext traffic is disabled.
- Android WebView session/cookie persistence for normal CreatorPulse login.
- External links are opened outside the embedded app.
- Google/YouTube OAuth has an Android-safe browser flow with a server-side, one-time D1 OAuth state and `creatorpulse://oauth/...` return link.
- File picker support for Studio imports.
- Authenticated Android DownloadManager support for exports.
- Designed offline state. No fake analytics are generated when offline.
- Automatic retry when connectivity returns.
- Back navigation, loading progress, splash screen, app icon and dark system bars.
- Mobile bottom navigation: Home, Ideas, Tools, Analytics and Profile.
- GitHub Actions APK build workflow included.

## Important offline boundary

The shell can launch and show the offline state without a network connection. Features that require the Cloudflare backend, D1, AI/server logic, or YouTube APIs require internet access. This is intentional and avoids putting private server credentials in the APK.
