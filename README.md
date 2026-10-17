# Sally Launcher

Launcher Android untuk Minecraft Bedrock Edition (pemilik lisensi resmi). Ditulis ulang dari nol, bertahap.
Package `com.sally.launcher`, UI Kotlin + Jetpack Compose, Material Design 3.

## Build
Build lewat GitHub Actions (`.github/workflows/android.yml`): push ke `main`, lalu unduh APK dari tab Actions > Artifacts. Bisa juga dibuka di Android Studio (JDK 21).
Jika sync mengeluh soal versi dependency, naikkan versinya di `gradle/libs.versions.toml`.

## Tahapan
- [x] 1. Fondasi: project, tema MD3, navigasi, penyimpanan profil, pengaturan
- [x] 2. Impor APK (berkas, bundle .apks/.xapk, atau dari Minecraft terpasang), validasi, manajer versi terisolasi
- [x] 3a. Ekstrak library native + validasi ELF
- [x] 3b-1. Pra-peluncuran: baca dependensi ELF (DT_NEEDED) untuk urutan muat, AssetManager dari APK game, uji muat library di proses `:game`
- [ ] 3b-2. Jendela game: glue Java `com.mojang.minecraftpe.*` + GameActivity + jembatan JNI, serta library pendamping (maesdk, PlayFab) yang dilaporkan 3b-1 sebagai "belum tersedia"
- [ ] 4. Loader mod native dan config mod
- [ ] 5. Manajer konten (world, resource pack, server, backup)
- [ ] 6. Akun Xbox
- [ ] 7. Overlay, berita, polesan

## Catatan Tahap 3b-1
- Urutan muat tidak ditulis tangan per versi game: dihitung dari tabel dinamis ELF tiap library (`runtime/ElfReader`, `runtime/LoadOrder`), jadi mengikuti isi APK yang diimpor.
- Library yang dibutuhkan tapi tidak ada di APK dan bukan bagian Android dilaporkan di log layar muat; itulah daftar kerja Tahap 3b-2.
- Layar muat berjalan di proses `:game`. Library native tidak bisa di-unload, jadi proses itu dimatikan saat layar ditutup.
