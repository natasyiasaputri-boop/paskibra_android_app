# Aplikasi Android Paskibra Pradita – SMK Negeri 2 Purwakarta

Aplikasi Android yang menampilkan website Paskibra
(`https://paskibra-smkn2pwk.site.je/`) dengan fitur khas aplikasi mobile.
**Website asli tidak diubah sama sekali.** Proyek ini berdiri sendiri di folder terpisah.

> Status: kode sumber sudah dibuat dan lolos pemeriksaan sintaks statis.
> **Build APK dan uji di HP belum dijalankan** (lihat bagian 5 dan 6). Jangan menyebut
> "berhasil" di sidang sebelum Anda sendiri menjalankannya dan APK terpasang di HP.

## 1. Teknologi dan alasan pemilihan
| Teknologi | Fungsi |
|---|---|
| Kotlin | Bahasa pemrograman resmi Android |
| WebView | Menampilkan website yang sudah online di dalam aplikasi |
| Material Components | Menu bawah, tombol, indikator kemajuan |
| SwipeRefreshLayout | Tarik ke bawah untuk memuat ulang |
| SplashScreen API | Splash screen (logo di latar maroon) |
| Gradle (Kotlin DSL) | Sistem build APK |

Mengapa WebView? Website Anda dibuat dengan Laravel dan datanya (berita, galeri, anggota,
dst.) langsung disisipkan ke halaman HTML. **Website tidak punya API**, jadi aplikasi native
penuh membutuhkan perubahan di backend (dilarang) atau scraping HTML yang rapuh.
WebView membuat data aplikasi selalu sama dengan website, termasuk formulir pendaftaran.

## 2. Fitur aplikasi
- Splash screen dengan logo Pradita dan latar maroon.
- Menu bawah: Profil, Berita, Galeri, Agenda, dan Lainnya (Organigram, Anggota, Prestasi, Tentang Kami, Daftar Anggota Baru).
- Tarik ke bawah untuk memuat ulang.
- Tampilan error saat internet mati atau server bermasalah, dengan tombol "Coba Lagi" dan muat ulang otomatis saat internet kembali.
- Tombol Back Android berjalan sesuai riwayat halaman.
- Tautan Instagram, TikTok, telepon, dan email dibuka di aplikasi masing-masing.
- Halaman admin (`/login`, `/admin`, `/dashboard`) tidak dibuka di aplikasi, sesuai ketentuan tidak ada fitur login baru.

## 3. Struktur proyek
```
paskibra-android-app/
├── settings.gradle.kts         Nama proyek dan daftar modul
├── build.gradle.kts            Versi plugin Android dan Kotlin
├── gradle.properties           Pengaturan Gradle
├── gradle/wrapper/             Versi Gradle yang dipakai
├── .github/workflows/build-apk.yml   Build APK otomatis di GitHub (opsional)
└── app/
    ├── build.gradle.kts        ID aplikasi, versi, dependensi, penandatanganan
    └── src/main/
        ├── AndroidManifest.xml Izin internet, ikon, nama, activity utama
        ├── java/.../MainActivity.kt   SELURUH logika aplikasi
        └── res/
            ├── layout/activity_main.xml   Susunan layar
            ├── menu/bottom_nav.xml        Menu bawah
            ├── menu/menu_more.xml         Menu "Lainnya"
            ├── values/strings.xml         Semua teks
            ├── values/colors.xml          Warna maroon & gold dari website
            ├── values/themes.xml          Tema dan splash screen
            ├── drawable/                  Ikon menu, ikon error, ikon splash
            ├── drawable-nodpi/logo_pradita.png   Logo dari website
            └── mipmap-*/                  Ikon aplikasi
```

## 4. Cara mengedit (tanpa perlu paham semua kode)
| Ingin mengubah | File | Bagian |
|---|---|---|
| Alamat website | `MainActivity.kt` | `SITE_HOST` |
| Nama aplikasi | `values/strings.xml` | `app_name` |
| Warna | `values/colors.xml` | nilai `#6B0F1A` dst. |
| Teks error/menu | `values/strings.xml` | teks terkait |
| Menu bawah | `menu/bottom_nav.xml` + `goToSection(...)` di `MainActivity.kt` | id bagian (`profil`, `berita`, ...) |
| Menyembunyikan elemen web di aplikasi | `MainActivity.kt` | `APP_CSS` |
| Versi aplikasi | `app/build.gradle.kts` | `versionCode`, `versionName` |

Cara kerja inti di `MainActivity.kt`:
1. `onCreate` menyiapkan splash screen, WebView, menu bawah, dan tombol Coba Lagi, lalu memuat `START_URL`.
2. `handleUrl` memutuskan tiap tautan: dibuka di WebView, di aplikasi lain, atau diblokir (admin).
3. `goToSection` menggulir halaman ke bagian yang dipilih (`scrollIntoView`).
4. `showError` / `showContent` mengatur tampilan error.

## 5. Cara membangun APK
### Cara A – Tanpa Android Studio (GitHub Actions, paling ringan)
1. Buat repositori GitHub baru (khusus aplikasi ini, bukan repositori website).
2. Upload seluruh isi folder proyek ini.
3. Buka tab **Actions → Build APK → Run workflow**.
4. Setelah selesai, unduh artifact **paskibra-apk** (berisi `app-debug.apk`), lalu pasang di HP.

### Cara B – Android Studio (lokal)
1. Pasang Android Studio, buka folder proyek ini, tunggu sinkronisasi Gradle selesai
   (Android Studio membuat `gradlew` dan jar wrapper otomatis).
2. Sambungkan HP (aktifkan Opsi Pengembang dan Debugging USB), lalu klik **Run**.
3. APK: menu **Build → Build APK(s)**; hasil di `app/build/outputs/apk/debug/`.

## 6. Pengujian yang harus Anda lakukan di HP
- [ ] Splash screen dan ikon tampil.
- [ ] Beranda dan semua bagian terbuka; menu bawah menggulir dengan benar.
- [ ] Organigram, Berita (modal baca), Galeri (lightbox), Anggota (pencarian), Prestasi, Agenda.
- [ ] Formulir Daftar dapat dikirim, dan pesan sukses/error tampil.
- [ ] Mode pesawat: tampilan error muncul; nyalakan internet: halaman termuat ulang.
- [ ] Tombol Back, tarik-untuk-muat-ulang, tautan Instagram/TikTok.
- [ ] Jika hosting menampilkan halaman peringatan/iklan, catat dan laporkan.

## 7. Rilis ke Google Play (AAB)
1. Buat keystore: `keytool -genkey -v -keystore paskibra.jks -keyalg RSA -keysize 2048 -validity 10000 -alias paskibra`. **Simpan file dan sandinya, jangan hilang.**
2. Buat `keystore.properties` di folder utama (sudah di `.gitignore`):
   ```
   storeFile=../paskibra.jks
   storePassword=...
   keyAlias=paskibra
   keyPassword=...
   ```
3. Jalankan `gradle bundleRelease` (atau Android Studio: Build → Generate Signed Bundle).
   Hasil: `app/build/outputs/bundle/release/app-release.aab`.
4. Daftar akun Google Play Console (biaya sekali bayar), unggah AAB, isi data aplikasi dan
   kebijakan privasi, lalu ajukan review.
   Catatan: aplikasi yang hanya membungkus website bisa ditolak jika dianggap kurang fungsi
   asli; nilai tambah menu bawah, error handling, dan splash membantu.

## 8. Temuan keamanan pada website (tidak saya ubah)
- File `.env` ikut dalam zip. Itu berisi kredensial; jangan dibagikan. Jika pernah terbagikan, ganti sandi database dan `APP_KEY`.
- Di `routes/web.php`, `/admin/dashboard` dan `/dashboard` diberi komentar "bisa diakses publik tanpa login". Pertimbangkan memberi middleware `admin.auth`.
- Rute `GET /logout` sebaiknya hanya `POST`.
- Ada file bantu di akar proyek (`recover*.php`, `drop_tables*.php`, `fix_*.php`). Pastikan tidak ikut ter-hosting di folder publik.
