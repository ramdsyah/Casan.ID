# Casan – Aplikasi Android untuk https://casan.id/

Aplikasi Android (Kotlin) yang membungkus situs casan.id dalam WebView.

## Cara build
1. Buka folder ini di **Android Studio** (Koala atau lebih baru), atau build lewat terminal: `./gradlew :app:assembleDebug`.
2. Tunggu *Gradle Sync* selesai, lalu jalankan di emulator/HP (Run ▶).
3. APK debug: **Build > Build APK(s)**. Untuk rilis Play Store: **Build > Generate Signed Bundle / APK**.

## Fitur
- Memuat https://casan.id/ dengan JavaScript, cookie, dan DOM storage aktif
- Tarik ke bawah untuk refresh, bar progres, tombol Back menelusuri riwayat halaman
- Layar "tidak ada koneksi" dengan tombol coba lagi
- Tautan eksternal, tel:, mailto: dibuka di aplikasi lain
- Upload berkas dan unduhan berkas
- Tautan https://casan.id/... dari luar dibuka langsung di aplikasi

## Filter brand di bawah
Di beranda muncul bar logo brand (Maka, Polytron, EMOA) di bagian bawah. Ketuk logo untuk
langsung memfilter (bisa pilih lebih dari satu, ketuk lagi untuk membatalkan), tanpa membuka
tombol filter. Bar ini menggerakkan filter asli situs lewat JavaScript (`BrandFilter.kt`):
membuka dialog filter, mencentang brand, lalu menekan "Terapkan".

- Favorit: tekan lama logo brand untuk menandai/menghapus bintang. Brand berbintang otomatis terfilter setiap
  aplikasi dibuka atau di-refresh (disimpan di SharedPreferences `casan_prefs`). Bila filter situs belum tampil,
  aplikasi mencoba ulang hingga sekitar 8 detik.
- Tambah brand: tambahkan satu baris di `BRANDS` (`BrandFilter.kt`) dan logo PNG di `res/drawable-nodpi/`.
  Nama brand harus sama persis dengan teks di dialog filter situs.
- Logo saat ini dipotong dari screenshot; ganti dengan file logo resmi (nama file sama) agar tajam.
- Bar hanya tampil di beranda (path `/`, `/home`, `/beranda`); ubah `HOME_PATHS` di `MainActivity.kt` bila beda.
- Skrip mengandalkan teks "Terapkan", placeholder "Cari lokasi", dan checkbox di dialog filter. Bila situs
  mengubah struktur itu, sesuaikan `BrandFilterScript`.

## Yang perlu disesuaikan
- `applicationId` / package (`id.casan.app`) di `app/build.gradle.kts` dan `AndroidManifest.xml`
- Warna brand: `res/values/colors.xml`
- Ikon: ganti `res/drawable/ic_launcher_foreground.xml` dengan logo Casan (Android Studio: *New > Image Asset*)
- Kamera, mikrofon, atau lokasi: butuh izin tambahan di manifest dan penanganan di `WebChromeClient`

## GitHub Actions
Setiap push ke `main` atau pull request, workflow `.github/workflows/build.yml` membangun APK debug.
Unduh hasilnya di tab **Actions > (pilih run) > Artifacts > casan-debug-apk**.
