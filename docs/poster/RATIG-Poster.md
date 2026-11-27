# RATIG — Reaction Time Fatigue

**Poster Capstone Project · Program Studi Informatika, Universitas Siliwangi**

> Format: **A2 portrait (420 × 594 mm)**, margin 18 mm, grid 12 kolom.
> Gaya: *data-editorial* — latar krem hangat, hijau hutan, aksen amber.
> Nada: akademik, tenang, operasional. Bukan poster medis; RATIG adalah alat
> bantu keputusan.

---

## Header

**RATIG**
### Reaction Time Fatigue

Sistem pemeriksaan kelelahan pekerja berbasis **waktu reaksi** — cepat,
objektif, dan tetap berjalan **tanpa internet**.

`Android` · `Kotlin / Jetpack Compose` · `Supabase` · `Offline-first`

---

## Masalah

Kelelahan menurunkan kewaspadaan dan memperlambat pengambilan keputusan.
Namun kelelahan sering tidak terdeteksi karena:

- bersifat **subjektif** — pekerja merasa masih kuat padahal refleks menurun;
- pemeriksaan manual memakan waktu dan sulit dibandingkan;
- catatan tersebar di kertas/spreadsheet sehingga sulit diaudit.

**Pertanyaan proyek:** bagaimana membuat pemeriksaan kelelahan yang cepat,
konsisten, terdokumentasi, dan dapat dipakai di lokasi kerja terpencil?

---

## Solusi

RATIG mengukur **waktu reaksi** sebagai indikator objektif kondisi pekerja.
Pemeriksaan berlangsung beberapa menit dan hasil langsung tampil.

```
Pilih pekerja → jalankan tes → tinjau hasil → finalisasi
       → tersimpan lokal → sinkron otomatis saat online
```

---

## Empat Mode Pemeriksaan

| Mode | Yang diukur | Peran hasil |
|---|---|---|
| **Klasik (RGB)** | reaksi terhadap stimulus warna | **klasifikasi kelelahan** |
| **Warna Acak** | reaksi terhadap warna acak | metrik pengamatan |
| **Tombol Acak** | pencarian target pada grid | metrik pengamatan |
| **Fokus (Go/No-Go)** | menahan respons terhadap distraktor | metrik pengamatan |

> Hanya mode **Klasik** yang dipakai untuk klasifikasi. Mode lain memberi
> metrik akurasi/kelalaian dan **tidak digabung** menjadi satu skor.

---

## Akurasi & Kepercayaan Data

- Waktu diukur dengan **monotonic clock** (`elapsedRealtimeNanos`), bukan jam
  dinding — tetap akurat saat waktu sistem berubah.
- Trial yang tidak wajar ditandai dan **tetap tercatat** untuk audit.
- Hasil yang sudah **final bersifat permanen**.
- Klasifikasi mengikuti aturan yang disetujui perusahaan; bila aturan belum
  valid, aplikasi menampilkan **“Perlu validasi”**, bukan angka menyesatkan.

---

## Offline-First

- Pemeriksaan tetap berjalan **tanpa sinyal**.
- Data disimpan di **Room** dan dikirim otomatis lewat **WorkManager**.
- Status sinkronisasi terlihat jelas: menunggu, sedang dikirim, berhasil, atau
  perlu pemeriksaan administrator.
- Login offline dibatasi waktu tertentu; server tetap menjadi sumber kebenaran.

---

## Tiga Peran, Ditegakkan di Server

| Peran | Tanggung jawab |
|---|---|
| **Super Admin** | konfigurasi sistem: protokol, aturan klasifikasi, data master, akun admin |
| **Admin** | operasional: pekerja, pengguna biasa, jadwal, laporan, pemantauan |
| **Pengguna** | menjalankan tes dan melihat riwayat sendiri |

**Row Level Security (RLS)** membatasi baca/tulis di PostgreSQL. Admin tidak
dapat mengubah protokol/aturan/data master atau mengangkat pengguna menjadi
admin, meskipun memanggil API langsung. Service key **tidak pernah** ada di APK.

---

## Arsitektur

```
Android (Kotlin + Compose)
  ├── Room             cache + outbox offline
  ├── WorkManager      sinkronisasi latar belakang
  └── MonotonicClock   pengukuran waktu reaksi
             │
             ▼
Supabase
  ├── PostgreSQL + RLS
  ├── Auth (email/kata sandi)
  └── Edge Functions (akun, laporan)
```

**Stack:** Kotlin · Jetpack Compose Material 3 · Hilt · Room · WorkManager ·
Supabase (PostgreSQL, Auth, Edge Functions) · CameraX + ML Kit.

---

## Untuk Pengguna & Admin

- Identifikasi pekerja dengan **pemindai QR/barcode** atau NIK manual.
- NIK selalu **disamarkan** di layar.
- Tambah pekerja sekaligus akun login: isi NIP + kata sandi; tanpa email nyata,
  ID login otomatis `<NIP>@ratig.local`.
- Tambah pengguna aplikasi: admin menentukan **kata sandi sementara**; tidak
  perlu email hosting dan tidak ada undangan email.
- Dasbor: sesi, klasifikasi hasil, sebaran departemen/shift, tren, tindak
  lanjut, audit log, ekspor CSV.

---

## Batasan & Etika

RATIG adalah **alat bantu keputusan**, bukan diagnosis medis dan bukan
keputusan akhir kelayakan kerja. Keputusan tetap mengikuti SOP perusahaan dan
penilaian pemeriksa yang berwenang.

---

## Hasil Pengujian Internal

- **138 unit test JVM** lulus.
- Alur finalisasi sesi, RLS tiga peran, dan sinkronisasi offline telah diuji.
- Aplikasi Android: minSdk 26, targetSdk 36.
- Repositori: **github.com/marsellinus/rating**

---

## Footer

**RATIG — Reaction Time Fatigue** · Capstone Project Informatika · 2026
`github.com/marsellinus/rating`

<!--
PRODUCTION NOTES
- Hierarki: judul 120–150 pt; subjudul 50–60 pt; section heading 34–40 pt;
  body 20–22 pt; caption 14–16 pt.
- Warna: #123B2A (hijau), #F4F1E8 (krem), #C9943A (amber), #5C665E (abu).
- Gunakan 3–4 tangkapan layar asli dari docs/proposal/ui/:
  03_dashboard_superadmin_bawah.png, 08_daftar_pekerja.png,
  11_detail_sesi.png, 17_protokol.png.
- Hasil render siap cetak: `RATIG-Poster.png` (2480×3508, A2 @150 dpi)
  dan `RATIG-Poster.pdf` (A2). Sumber tata letak: `RATIG-Poster.html`.
- Jangan menambahkan grafik/angka yang tidak ada di dokumen proyek.
-->
