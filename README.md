# RATIG — Reaction Time Fatigue

Aplikasi Android untuk **pemeriksaan kelelahan pekerja berbasis waktu reaksi**
(reaction time), dirancang untuk operasional tambang/pabrik dengan pola
**multi-pekerja** melalui NIK dan pemindai barcode/QR, serta dukungan penuh
**offline-first**.

> RATIG adalah **alat bantu keputusan** bagi pemeriksa (paramedis/SHE) — bukan
> diagnosis medis dan bukan keputusan akhir kelayakan kerja.

---

## Fitur Utama

1. **Empat Mode Pemeriksaan**
   - **Klasik (RGB)** — stimulus warna; dipakai untuk klasifikasi kelelahan.
   - **Warna Acak** — stimulus warna acak.
   - **Tombol Acak** — pencarian target pada grid (koordinasi mata–tangan).
   - **Inhibisi (Go/No-Go)** — kontrol fokus & impuls.
   - Hanya mode **Klasik** yang diklasifikasi; mode lain memberi metrik
     pengamatan (akurasi, kelalaian) dan **tidak** digabung menjadi satu skor.

2. **Akurasi Tinggi**
   - Waktu diukur dengan **monotonic clock** (`SystemClock.elapsedRealtimeNanos`),
     bukan jam dinding — akurat meski perangkat lambat atau waktu sistem berubah.

3. **Offline-First & Sinkronisasi Latar Belakang**
   - Pemeriksaan berjalan **tanpa internet**; data disimpan di database lokal
     (Room) dan tersinkron otomatis via **WorkManager** (idempotent, retry
     bertahap).
   - Halaman **Status Sinkronisasi** menampilkan data menunggu/gagal/perlu
     pemeriksaan.

4. **Multi-Pekerja & Identifikasi Cepat**
   - Pemeriksa login sekali, lalu memeriksa banyak pekerja.
   - Input NIK manual (disamarkan) atau pemindai **CameraX + ML Kit Barcode**.

5. **Tiga Peran dengan Halaman Berbeda**
   - `super_admin` — semua: kelola admin & pengguna, protokol, aturan, data
     master, audit, pemantauan.
   - `admin` — kelola pengguna + pemantauan & laporan.
   - `user` — menjalankan tes + melihat riwayat sendiri.
   - Hak akses ditegakkan oleh **Row Level Security** di server, bukan hanya
     di aplikasi.

6. **Keamanan & Privasi**
   - Kunci layanan (service key) **tidak pernah** disimpan di aplikasi.
   - NIK adalah kunci bisnis; dipakai untuk pencarian, selalu disamarkan di UI.
   - Hasil final bersifat **permanen** (tidak dapat diubah).
   - Tindakan penting tercatat di **Audit Log**.

---

## Stack Teknologi

| Lapisan | Teknologi |
|---|---|
| Platform | Android Native (Kotlin), minSdk 26, targetSdk 36 |
| UI | Jetpack Compose (Material 3) |
| Arsitektur | MVVM/MVI + Hilt Dependency Injection |
| Database lokal | Room 2.8 (SQLite) |
| Sinkronisasi | WorkManager 2.12 |
| Pemindai | CameraX + ML Kit Barcode Scanning |
| Backend | Supabase (PostgreSQL, Auth, Edge Functions, RLS) |
| Klien backend | `supabase-kt` 3.8 |
| Build | Gradle 9.8.0, AGP 9.4.1, Kotlin 2.4.20, JDK 26 |

---

## Struktur Repositori

```
app/                     Aplikasi Android (Kotlin + Compose)
  src/main/java/com/ratig/app/
    core/                config, identity, timing, sync, json, result
    domain/              model, repository (interface), usecase (logika murni)
    data/                remote (Supabase), local (Room), offline, sync, di
    feature/             layar per fitur (auth, dashboard, workers, testflow, …)
    ui/                  navigasi, komponen bersama, tema
  src/test/              Unit test JVM
supabase/
  migrations/            0001 skema · 0002 keamanan · 0003 mode · 0004 peran
  functions/             approve-account · admin-create-user · export-report
  seed.sql, config.toml
docs/                    architecture, deployment, oauth-setup, proposal
USER-MANUAL.md           Buku panduan pengguna
PROPOSAL.md              Proposal proyek
CONTRACT.md / CONTRACT-2.md  Kontrak antarmuka & arsitektur
```

---

## Akun Demo (Lingkungan Uji)

Aplikasi memakai **login email/kata sandi**. Akun **dibuat oleh administrator**
(tidak ada pendaftaran mandiri). Untuk mencoba, gunakan akun berikut. Kata
sandi semua akun: **`Ratig-T3st-2026!`**

| Email | Peran | Halaman |
|---|---|---|
| `admin@ratig.test` | **Super Admin** | Dasbor Super Admin — semua fitur |
| `examiner@ratig.test` | **Admin** | Dasbor Admin — kelola pengguna + pemantauan |
| `mgmt@ratig.test` | **Admin** | Dasbor Admin — kelola pengguna + pemantauan |
| `worker@ratig.test` | **Pengguna** | Beranda Pengguna — jalankan tes + riwayat |

> **Peringatan keamanan:** akun ini hanya untuk **pengujian**. Ganti/hapus
> sebelum dipakai di produksi dan **jangan** memakai kata sandi ini di
> lingkungan nyata.

---

## Setup Awal (Sekali)

Saat pertama dibuka, aplikasi menampilkan **Setup Awal** dengan **pratinjau
langsung** (perubahan langsung terlihat):

1. **Bahasa** — Bahasa Indonesia / English.
2. **Mode Tampilan** — Ikut Ponsel / Terang / Gelap.
3. **Ukuran Tulisan** — Normal / Besar / Sangat Besar.

Setelah itu muncul **panduan singkat (tutorial)** 5 halaman. Keduanya dapat
dibuka lagi dari **Profil → Pengaturan Tampilan** dan **Profil → Panduan
Penggunaan**.

---

## Memulai (Build)

1. **Konfigurasi lokal** — salin `local.properties.example` → `local.properties`
   dan isi nilai publik:

   ```properties
   sdk.dir=/path/to/Android/Sdk
   supabase.url=https://xxxx.supabase.co
   supabase.anonKey=<anon key publik — BUKAN service_role>
   google.webClientId=<Google OAuth Web Client ID>
   ```

2. **Build APK debug:**

   ```bash
   ./gradlew :app:assembleDebug
   ```

   Hasil: `app/build/outputs/apk/debug/app-debug.apk`.

3. **Jalankan unit test:**

   ```bash
   ./gradlew :app:testDebugUnitTest
   ```

4. **Backend** — terapkan migrasi dan deploy Edge Function (lihat
   [`supabase/README.md`](supabase/README.md)).

---

## Dokumentasi

| Dokumen | Isi |
|---|---|
| [USER-MANUAL.md](USER-MANUAL.md) | Buku panduan pengguna aplikasi |
| [PROPOSAL.md](PROPOSAL.md) | Proposal proyek (juga `.docx`/`.pdf` di `docs/proposal/`) |
| [docs/architecture.md](docs/architecture.md) | Arsitektur, timing, offline, sync |
| [docs/deployment.md](docs/deployment.md) | Toolchain, konfigurasi, signing, checklist |
| [docs/oauth-setup.md](docs/oauth-setup.md) | Google OAuth + Credential Manager |
| [supabase/README.md](supabase/README.md) | Migrasi, RPC, skenario RLS |
| [CONTRACT.md](CONTRACT.md) / [CONTRACT-2.md](CONTRACT-2.md) | Kontrak antarmuka & arsitektur |

Galeri antarmuka: `docs/proposal/RATIG-UI-Gallery.pdf` (dan 21 gambar di
`docs/proposal/ui/`).

---

## Pengujian

- Unit test JVM mencakup: kalkulasi metrik, klasifikasi kelelahan, validasi &
  masking NIK, payload QR, klasifikasi kegagalan sinkronisasi, penulis CSV, dan
  jembatan `jsonb`.

```bash
./gradlew :app:testDebugUnitTest
```

---

## Lisensi & Penggunaan

Proyek internal. Hubungi pemilik repositori untuk penggunaan dan distribusi.

---

_Dibuat untuk operasional yang efisien — sederhana bagi pengguna, kuat di
belakang layar._
