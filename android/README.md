# Starpoint Garage Android

Android WebView wrapper for the Starpoint Garage production web app.

- Package: `com.starpointgarage.app`
- Primary URL: `https://starpointgarage.com/`
- Fallback: `https://rizhidayatullah5758-art.github.io/starpoint-garage/`
- Camera permission is used for staff QR scanning.
- Android file picker is used for payment proofs, avatars, banners, and other web uploads.

The GitHub Actions workflow builds an installable debug APK for direct testing. A production Play Store release should use a private, persistent signing key stored as repository secrets; never commit that key to this public repository.
