# Sally Launcher

Launcher Android untuk Minecraft Bedrock Edition (pemilik lisensi resmi). Ditulis ulang dari nol, bertahap.
Package `com.sally.launcher`, UI Kotlin + Jetpack Compose, Material Design 3.

## Build
Build lewat GitHub Actions (`.github/workflows/android.yml`): push ke `main`, lalu unduh APK dari tab Actions > Artifacts. Bisa juga dibuka di Android Studio (JDK 21).
Jika sync mengeluh soal versi dependency, naikkan versinya di `gradle/libs.versions.toml`.

## Tahapan
- [x] 1. Fondasi: project, tema MD3, navigasi, penyimpanan profil, pengaturan
- [ ] 2. Impor APK, validasi, manajer versi terisolasi
- [ ] 3. Ekstrak library dan runtime launch
- [ ] 4. Loader mod native dan config mod
- [ ] 5. Manajer konten (world, resource pack, server, backup)
- [ ] 6. Akun Xbox
- [ ] 7. Overlay, berita, polesan
