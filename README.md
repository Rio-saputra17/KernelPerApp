# KernelPerf — Per-App Kernel Manager
**v1.0 by RioDev**

App ringan pengganti fitur per-app FKM, fokus pada:
- CPU Governor per app
- CPU Min/Max Frequency per app
- GPU Governor per app
- I/O Scheduler per app
- Power Mode preset (Powersave / Balanced / Performance / Gaming / Custom)
- Custom sysfs tweaks
- Auto-apply via AccessibilityService

---

## Cara Build di Termux (Android)

### 1. Install dependencies
```bash
pkg update && pkg upgrade -y
pkg install openjdk-17 git -y
```

### 2. Install SDK (pakai sdkmanager via Termux)
```bash
pkg install android-tools
# Download command line tools dari dl.google.com/android/repository/commandlinetools
# atau gunakan AIDE/Acode + Termux Build Plugin
```

### Alternatif termudah: Build via PC
1. Clone / copy folder ini ke PC
2. Buka di **Android Studio**
3. Build → Generate Signed APK

### Atau: Build di Termux langsung
```bash
cd KernelPerApp
chmod +x gradlew
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

---

## Permissions yang dibutuhkan
- **QUERY_ALL_PACKAGES** — baca list app terinstall
- **PACKAGE_USAGE_STATS** — (opsional, alternatif AccessibilityService)
- **AccessibilityService** — deteksi app foreground (WAJIB untuk auto-apply)
- **RECEIVE_BOOT_COMPLETED** — init root shell saat boot
- **Root / KernelSU-Next** — akses sysfs kernel

---

## Setup setelah install
1. Buka app → grant root permission
2. Buka **Settings > Accessibility** → enable "KernelPerf App Detector"
3. Buka tab **Aplikasi** → pilih app → set profil → Save
4. Done! Profil akan otomatis diterapkan saat app dibuka

---

## Struktur Project
```
app/src/main/java/com/riodev/kernelperf/
├── KernelApp.kt              # Application class
├── MainActivity.kt           # Navigation + Bottom Bar
├── data/
│   ├── model/
│   │   ├── Models.kt         # AppProfile, PowerMode, dll
│   │   ├── AppProfileDao.kt  # Room DAO
│   │   └── AppDatabase.kt    # Room Database
│   └── repository/
│       └── AppRepository.kt  # Data layer
├── root/
│   └── RootUtils.kt          # Semua perintah root (libsu)
├── service/
│   ├── AppDetectionService.kt # AccessibilityService
│   └── BootReceiver.kt
└── ui/
    ├── MainViewModel.kt
    ├── theme/
    │   └── Theme.kt          # Dark theme Material You
    └── screens/
        ├── HomeScreen.kt     # Dashboard kernel status
        ├── AppListScreen.kt  # List semua app
        └── ProfileEditorScreen.kt # Editor profil per-app
```
