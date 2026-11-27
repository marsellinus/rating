# RATIG — Guidebook

**Panduan operasional aplikasi pemeriksaan kelelahan berbasis waktu reaksi**

Versi dokumen 1.0 · Android · 2026

> **RATIG adalah alat bantu keputusan.** Hasilnya bukan diagnosis medis dan
> bukan keputusan akhir kelayakan kerja. Keputusan tetap mengikuti SOP
> perusahaan dan penilaian pemeriksa yang berwenang.

---

## Daftar Isi

1. [Tentang RATIG](#1-tentang-ratig)
2. [Persiapan Perangkat](#2-persiapan-perangkat)
3. [Setup Awal & Login](#3-setup-awal--login)
4. [Peran & Hak Akses](#4-peran--hak-akses)
5. [Menambah Pekerja & Akun Login](#5-menambah-pekerja--akun-login)
6. [Alur Pemeriksaan](#6-alur-pemeriksaan)
7. [Memahami Hasil](#7-memahami-hasil)
8. [Riwayat & Detail Sesi](#8-riwayat--detail-sesi)
9. [Dasbor Admin](#9-dasbor-admin)
10. [Konfigurasi Super Admin](#10-konfigurasi-super-admin)
11. [Offline & Sinkronisasi](#11-offline--sinkronisasi)
12. [Onboarding Tanpa Email Hosting](#12-onboarding-tanpa-email-hosting)
13. [Troubleshooting](#13-troubleshooting)
14. [Keamanan & Privasi](#14-keamanan--privasi)
15. [Checklist Demo](#15-checklist-demo)

---

## 1. Tentang RATIG

RATIG mengukur **waktu reaksi** pekerja sebagai indikator kelelahan. Satu
petugas dapat memeriksa banyak pekerja dari satu perangkat Android.

Yang membedakan RATIG:

- **Cepat** — pemeriksaan berlangsung beberapa menit dan hasil langsung tampil.
- **Objektif** — waktu diukur dengan monotonic clock, bukan perkiraan.
- **Offline-first** — tes tetap berjalan tanpa sinyal; data tersinkron kemudian.
- **Terdokumentasi** — hasil final permanen, perubahan penting masuk audit log.
- **Aman per peran** — hak akses ditegakkan di server dengan Row Level Security.

---

## 2. Persiapan Perangkat

| Item | Kebutuhan |
|---|---|
| Sistem | Android 8.0 (API 26) atau lebih baru |
| Kamera | Untuk pemindai barcode/QR (opsional) |
| Penyimpanan | ±100 MB ruang kosong |
| Jaringan | Internet opsional; diperlukan untuk sinkronisasi |
| Akun | Dibuat administrator; tidak ada pendaftaran mandiri |

Pasang APK yang diberikan administrator, lalu buka aplikasi.

---

## 3. Setup Awal & Login

Saat pertama kali dibuka:

1. Pilih **Bahasa** — Indonesia atau English.
2. Pilih **Mode Tampilan** — Ikut Ponsel, Terang, atau Gelap.
3. Pilih **Ukuran Tulisan** — Normal, Besar, atau Sangat Besar.
4. Ketuk **Simpan & Lanjut**, lalu ikuti panduan singkat.

Semua pilihan dapat diubah lagi dari **Profil → Pengaturan Tampilan**.

### Login

1. Masukkan **Email / ID Login** dan **Kata Sandi** dari administrator.
2. Centang **Ingat saya** bila perangkat dipakai sendiri.
3. Ketuk **Masuk**.

> Login memakai email/kata sandi. Email hanya berfungsi sebagai **ID login**;
> tidak harus berupa mailbox yang aktif. Ini penting untuk organisasi yang
> belum memiliki hosting email.

Jika akun berstatus **Menunggu**, hubungi Admin untuk aktivasi.

---

## 4. Peran & Hak Akses

| Peran | Halaman | Bisa melakukan |
|---|---|---|
| **Super Admin** | Dasbor Super Admin | semua: akun admin, protokol, aturan klasifikasi, data master, audit, pemantauan |
| **Admin** | Dasbor Admin | pekerja, akun pengguna biasa, jadwal, tindak lanjut, laporan, pemantauan |
| **Pengguna** | Beranda Pengguna | tes, riwayat sendiri, profil |

Pembatasan ditegakkan di server:

- Admin **tidak** dapat mengubah protokol, aturan, atau data master.
- Admin **tidak** dapat mengangkat pengguna menjadi admin.
- Admin **tidak** dapat membuat akun admin.
- Pengguna hanya melihat sesi miliknya sendiri.

---

## 5. Menambah Pekerja & Akun Login

Buka **Pekerja → tombol (+)**. Menu ini untuk **data pekerja**, bukan akun
aplikasi umum.

| Kolom | Wajib | Catatan |
|---|---|---|
| Nomor Induk Pekerja | Ya | 4–32 digit; tidak boleh duplikat |
| Nama Lengkap | Ya | tampil di daftar pekerja |
| Email login | Tidak | boleh `nama@ratig.local`; tidak perlu mailbox |
| Kata Sandi Login | Tidak | min. 8 karakter; isi untuk membuat akun login |
| Departemen / Area / Shift | Tidak | area kerja mengikuti departemen |
| Jabatan | Tidak | contoh: Operator |

Dua kemungkinan:

- **Sandi diisi** → data pekerja + akun login dibuat sekaligus. Akun langsung
  aktif. Bila email dikosongkan, ID login menjadi
  `<nomor-induk>@ratig.local`.
- **Sandi kosong** → hanya data pekerja; pemeriksa yang menjalankan tes atas
  namanya.

Berikan ID login dan sandi **secara langsung** kepada pekerja. Tidak ada email
undangan yang dikirim.

### Menambah akun aplikasi (Admin/Super Admin)

Buka **Profil → Pengguna → (+)**:

- Admin hanya dapat membuat peran **Pengguna**.
- Super Admin dapat membuat **Admin** atau **Pengguna**.
- Isi **ID Login** dan **kata sandi sementara** (min. 8 karakter).
- Akun langsung aktif; sampaikan kredensial langsung ke pengguna.

---

## 6. Alur Pemeriksaan

```
Pilih pekerja → pilih jenis tes → baca instruksi → tes
      → tinjau hasil → finalisasi
```

1. Buka menu **Pekerja**.
2. Cari nama/nomor induk, gunakan filter, atau pindai QR `RATIG1:<NIK>`.
3. Ketuk pekerja → **Mulai Tes**.
4. Pilih mode:
   - **Klasik (RGB)** — untuk klasifikasi kelelahan.
   - **Warna Acak** — metrik pengamatan.
   - **Tombol Acak** — koordinasi mata–tangan.
   - **Fokus (Go/No-Go)** — kontrol impuls.
5. Bacakan instruksi; centang pemahaman pekerja.
6. Jalankan tes. Jangan menutup aplikasi atau berpindah aplikasi.
7. Tinjau hasil, lalu **Selesai/Finalisasi**.

> Jika aplikasi terhenti karena panggilan/notifikasi saat finalisasi, sesi yang
> sudah selesai tidak boleh terputus. Gunakan tombol kembali dengan hati-hati.

---

## 7. Memahami Hasil

| Istilah | Arti |
|---|---|
| Mean | rata-rata waktu reaksi (ms) |
| Median | nilai tengah |
| Std. Deviasi | sebaran respons |
| Percobaan valid | trial yang dihitung |
| Respons lambat | respons melewati ambang |
| Kategori | Normal / Fatigue Ringan / Sedang / Berat |

Aturan klasifikasi harus **disetujui perusahaan**. Bila belum, aplikasi
menampilkan **“Perlu validasi”** — bukan angka yang menyesatkan.

Mode non-Klasik menampilkan **metrik pengamatan** (akurasi, kelalaian) dan
tidak digabung menjadi satu skor kelelahan.

### Skrining paramedis

Pada layar hasil, petugas dapat mengisi tekanan darah, nadi, dan alkohol.
Temuan serta rekomendasi bersifat saran; keputusan tetap pada pemeriksa.

---

## 8. Riwayat & Detail Sesi

- **Riwayat** menampilkan sesi berdasarkan periode, shift, status, dan kategori.
- Admin dapat memfilter departemen; **Pengguna** hanya melihat
  **Departemen Anda** karena riwayatnya sudah dibatasi ke sesi sendiri.
- Ketuk sesi untuk melihat:
  - informasi pekerja, pemeriksa, shift, protokol, waktu;
  - metrik dan kategori;
  - rincian trial per nomor;
  - tindak lanjut (untuk admin).

Hasil yang sudah final tidak dapat diubah.

---

## 9. Dasbor Admin

Dasbor Admin menampilkan:

- pekerja aktif, total pengguna, sesi hari ini/total;
- sesi belum selesai, tindak lanjut terbuka, akun menunggu;
- **klasifikasi hasil**;
- **sesi per departemen** dan **per shift**;
- tren harian.

Menu operasional:

- **Pengguna** — kelola akun pengguna biasa.
- **Laporan** — pratinjau dan ekspor CSV.
- **Jadwal & Shift** — lihat dan atur jadwal.
- **Tindak Lanjut** — catat tindakan atas temuan.
- **Audit Log** — jejak tindakan penting.

---

## 10. Konfigurasi Super Admin

Super Admin dapat:

- **Protokol Uji** — buat/ubah protokol. Menu **Ubah** tersedia untuk semua
  status. Untuk protokol aktif, perubahan disimpan pada versi yang sama dan
  berlaku untuk sesi baru — layar edit menampilkan peringatan.
- **Aturan Klasifikasi** — atur ambang kategori dan validasi aturan.
- **Data Master** — departemen, area kerja, shift.
- **Pengguna** — buat admin/pengguna, ubah peran, aktivasi akun.
- **Audit Log** — periksa perubahan konfigurasi dan akun.

---

## 11. Offline & Sinkronisasi

1. Tes tetap berjalan tanpa sinyal.
2. Data disimpan di perangkat.
3. Saat online, WorkManager mengirim data secara bertahap.
4. Buka **Status Sinkronisasi** untuk melihat antrean dan kegagalan.
5. **Sinkronkan sekarang** memaksa pengiriman segera.

Status penting:

| Status | Arti |
|---|---|
| Menunggu koneksi | tersimpan lokal, menunggu jaringan |
| Sedang disinkronkan | sedang dikirim |
| Tersinkronisasi | sudah diterima server |
| Gagal, akan dicoba | gangguan sementara |
| Perlu pemeriksaan | perlu tindakan manual |

---

## 12. Onboarding Tanpa Email Hosting

Tidak perlu mail server atau undangan email.

### Untuk pekerja

1. Buka **Pekerja → (+)**.
2. Isi NIP, nama, dan kata sandi.
3. Kosongkan email → ID login otomatis `<NIP>@ratig.local`.
4. Simpan. Berikan ID + sandi langsung ke pekerja.

### Untuk admin/pengguna aplikasi

1. Buka **Profil → Pengguna → (+)**.
2. Isi nama, **ID Login** (boleh `nama@ratig.local`), dan sandi sementara.
3. Simpan; akun langsung aktif.
4. Sampaikan ID + sandi langsung.

> `@ratig.local` adalah pengenal internal. Tidak ada email yang dikirim, jadi
> tidak memerlukan hosting email.

---

## 13. Troubleshooting

| Gejala | Solusi |
|---|---|
| Tidak bisa login | cek ID login, sandi, status akun; pastikan jaringan untuk login pertama |
| Tambah pekerja ditolak | NIP/email duplikat, atau sandi < 8 karakter |
| Tambah pengguna ditolak | email/ID sudah dipakai, atau sandi < 8 karakter |
| QR tidak dikenali | gunakan format `RATIG1:<NIK>` atau input NIK manual |
| Kamera tidak muncul | izinkan kamera di Pengaturan Android → RATIG → Izin |
| Data belum terkirim | buka Status Sinkronisasi; cek jaringan; tekan Sinkronkan sekarang |
| Grafik dasbor kosong | belum ada sesi final dengan klasifikasi/departemen |
| Hasil “Perlu validasi” | aturan klasifikasi belum divalidasi; hubungi Super Admin |
| “Koneksi jaringan bermasalah” | periksa DNS/jaringan perangkat; server mungkin tidak terjangkau |

---

## 14. Keamanan & Privasi

- NIK adalah kunci bisnis; ditampilkan **disamarkan**.
- Foreign key memakai UUID, bukan NIK.
- Service-role key **tidak pernah** disimpan di APK.
- RLS membatasi data per peran dan per pemilik sesi.
- Hasil final **immutable**.
- Audit log mencatat tindakan penting.
- Skrining kesehatan disimpan lokal di perangkat pada versi ini.

---

## 15. Checklist Demo

Gunakan urutan ini untuk demo 5–7 menit:

1. **Login** sebagai Super Admin.
2. Tunjukkan **Dasbor** dan grafik klasifikasi/departemen.
3. Buka **Pekerja → Detail → Mulai Tes**; jelaskan 4 mode.
4. Tunjukkan **Hasil** dan **Detail Sesi** (metrik + trial).
5. Buka **Protokol Uji → Ubah**; jelaskan versi dan peringatan.
6. Buka **Data Master**; tunjukkan departemen/shift.
7. Buka **Pengguna → (+) Tambah Pengguna**; jelaskan sandi sementara tanpa
   email undangan.
8. Buka **Audit Log**; tunjukkan jejak tindakan.
9. Login sebagai **Pengguna/Worker**; tunjukkan Beranda dan Riwayat
   (Departemen Anda, tanpa filter departemen).
10. Tutup dengan batasan: **alat bantu keputusan, bukan diagnosis medis**.

---

## Lampiran — Akun Demo

Kata sandi akun demo: **`Ratig-T3st-2026!`** (hanya untuk lingkungan uji).

| Email | Peran |
|---|---|
| `admin@ratig.test` | Super Admin |
| `examiner@ratig.test` | Admin |
| `mgmt@ratig.test` | Admin |
| `worker@ratig.test` | Pengguna |

> Ganti/hapus akun demo sebelum produksi.

---

*Dokumen ini melengkapi `USER-MANUAL.md` dan `docs/poster/RATIG-Poster.md`.*
