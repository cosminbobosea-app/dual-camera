# Dual Polaroid Camera - Android Project

Aplicație nativă Android în Kotlin și Jetpack Compose cu suport pentru camere concurente (CameraX 1.4.0), ramă Polaroid repoziționabilă prin Drag & Drop și salvare în MediaStore.

## Cum obții APK-ul gata compilat și deblocat:

### Opțiunea 1: Direct cu GitHub Actions (Fără Android Studio instalat)
1. Încarcă conținutul acestei arhive într-un depozit nou pe GitHub (ex: `dual-camera`).
2. Mergi în tab-ul **Actions** pe GitHub.
3. Se va declanșa automat fluxul **Build Android APK** (definit în `.github/workflows/build.yml`).
4. După 2 minute, la finalul rulării, găsești în secțiunea **Artifacts**:
   - **`DualPolaroidCamera-Release-APK.zip`** (Recomandat - APK semnat cu cheie release RSA 2048-bit v2/v3).
   - **`DualPolaroidCamera-Debug-APK.zip`** (APK clasic debug).
5. Descarcă fișierul **`app-release.apk`** direct pe telefon!

### De ce blochează Android instalarea APK-urilor și cum se rezolvă definitiv:
1. **Semnătură Release vs Debug**: APK-urile de tip Debug au cheie generică pe care Google Play Protect o consideră suspectă. Noul workflow GitHub generează o cheie **Release Keystore dedicată (RSA 2048)** și semnează binarul cu **APK Signature Scheme v2 + v3**.
2. **Namespace real**: `applicationId` a fost actualizat din `com.example.*` în `com.cosminbobosea.dualpolaroidcamera`.
3. **Validare Play Protect (Whitelisting)**: Poți trimite hash-ul APK-ului către Google prin portalul gratuit de contestații Play Protect (*Play Protect Developer Dispute*), iar în 24h este adăugat în baza de date sigură globală.

### Opțiunea 2: În Android Studio
1. Deschide Android Studio (Koala / Ladybug).
2. Alege **File -> Open...** și selectează folderul dezarhivat.
3. Așteaptă sincronizarea Gradle (**Sync Project with Gradle Files**).
4. Conectează telefonul Samsung Galaxy sau alt dispozitiv compatibil cu modul Dual Camera prin USB.
5. Apasă pe **Run ('app')** sau din meniu: **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**.
