# Dual Polaroid Camera - Android Project

Aplicație nativă Android în Kotlin și Jetpack Compose cu suport pentru camere concurente (CameraX 1.4.0), ramă Polaroid repoziționabilă prin Drag & Drop și salvare în MediaStore.

## Cum obții APK-ul gata compilat:

### Opțiunea 1: Direct cu GitHub Actions (Fără Android Studio instalat)
1. Încarcă conținutul acestei arhive într-un depozit nou pe GitHub (ex: `dual-polaroid-camera`).
2. Mergi în tab-ul **Actions** pe GitHub.
3. Se va declanșa automat fluxul **Build Android APK** (definit în `.github/workflows/build.yml`).
4. După 2 minute, la finalul rulării, găsești fișierul **`DualPolaroidCamera-APK.zip`** la secțiunea Artifacts.
5. Descarcă-l și instalează fișierul `app-debug.apk` direct pe telefon!

### Opțiunea 2: În Android Studio
1. Deschide Android Studio (Koala / Ladybug).
2. Alege **File -> Open...** și selectează folderul dezarhivat.
3. Așteaptă sincronizarea Gradle (**Sync Project with Gradle Files**).
4. Conectează telefonul Samsung Galaxy sau alt dispozitiv compatibil cu modul Dual Camera prin USB.
5. Apasă pe **Run ('app')** sau din meniu: **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**.
