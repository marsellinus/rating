# RATIG — Buku Panduan Pengguna (User Manual)

**Sistem Pemeriksaan Kelelahan Berbasis Waktu Reaksi**
Versi aplikasi 1.0.0 · Android

---

## Daftar Isi

1. [Pengenalan](#1-pengenalan)
2. [Persyaratan Perangkat](#2-persyaratan-perangkat)
3. [Masuk ke Aplikasi](#3-masuk-ke-aplikasi)
4. [Mengenal Peran Pengguna](#4-mengenal-peran-pengguna)
5. [Pemeriksaan Pekerja (Alur Utama)](#5-pemeriksaan-pekerja-alur-utama)
6. [Menggunakan Pemindai NIK/QR](#6-menggunakan-pemindai-nikqr)
7. [Memahami Hasil Pemeriksaan](#7-memahami-hasil-pemeriksaan)
8. [Mode Pemeriksaan](#8-mode-pemeriksaan)
9. [Riwayat & Detail Sesi](#9-riwayat--detail-sesi)
10. [Bekerja Tanpa Internet (Offline)](#10-bekerja-tanpa-internet-offline)
11. [Halaman Super Admin](#11-halaman-super-admin)
12. [Halaman Admin](#12-halaman-admin)
13. [Halaman Pengguna](#13-halaman-pengguna)
14. [Laporan & Ekspor](#14-laporan--ekspor)
15. [Menangani Masalah (Troubleshooting)](#15-menangani-masalah-troubleshooting)
16. [Pertanyaan Umum (FAQ)](#16-pertanyaan-umum-faq)

---

## 1. Pengenalan

**RATIG** (Reaction Time Fatigue) adalah aplikasi Android untuk memeriksa
tingkat kelelahan pekerja melalui pengukuran **waktu reaksi**. Pemeriksaan
berlangsung hanya beberapa menit dan hasilnya langsung tampil.

> **Penting:** RATIG adalah **alat bantu keputusan**. Hasilnya **bukan**
> diagnosis medis dan **bukan** keputusan akhir kelayakan kerja. Keputusan
> tetap mengikuti SOP perusahaan dan penilaian pemeriksa yang berwenang.

Aplikasi dapat digunakan **tanpa internet**: hasil pemeriksaan disimpan di
perangkat lalu terkirim otomatis saat kembali online.

---

## 2. Persyaratan Perangkat

| Item | Keterangan |
|---|---|
| Sistem | Android 8.0 (API 26) atau lebih baru |
| Kamera | Diperlukan untuk memindai barcode/QR |
| Penyimpanan | Ruang kosong ± 100 MB |
| Jaringan | Internet **opsional** (untuk sinkronisasi) |
| Akun | Akun Google, atau akun yang dibuat oleh Admin |

---

## 3. Masuk ke Aplikasi

1. Buka aplikasi **RATIG**.
2. Ketuk **Masuk dengan Google** dan pilih akun Anda.

**Jika akun baru:** akun berstatus *Menunggu*. Anda akan melihat layar
**Menunggu Persetujuan**. Hubungi Admin untuk mengaktifkan akun, lalu tekan
**Periksa Status**.

**Jika aplikasi belum dikonfigurasi:** muncul layar *Konfigurasi* dengan pesan
bahwa aplikasi belum diatur. Hubungi administrator teknis (lihat
`docs/deployment.md`).

**Lupa keluar / ganti akun:** buka **Profil → Keluar**.

> Pada **build debug** (khusus pengujian), tersedia juga login email/kata sandi.
> Fitur ini **tidak aktif** pada aplikasi produksi.

### 3.1 Akun Demo (untuk mencoba)

Pada build **debug** tersedia akun uji berikut. Kata sandi semua akun:
**`Ratig-T3st-2026!`**

| Email | Peran | Yang Anda lihat |
|---|---|---|
| `admin@ratig.test` | Super Admin | Dasbor Super Admin + semua menu |
| `examiner@ratig.test` | Admin | Dasbor Admin + pemantauan |
| `mgmt@ratig.test` | Admin | Dasbor Admin + pemantauan |
| `worker@ratig.test` | Pengguna | Beranda Pengguna |

> Akun ini **hanya untuk pengujian**. Jangan dipakai di lingkungan produksi.

---

## 4. Mengenal Peran Pengguna

Aplikasi memiliki **tiga peran**, masing-masing dengan halaman berbeda:

| Peran | Halaman Utama | Bisa Melakukan |
|---|---|---|
| **Super Admin** | Dasbor Super Admin | Semua hal: kelola pengguna & admin, protokol, aturan, data master, audit, pemantauan |
| **Admin** | Dasbor Admin | Kelola pengguna (peran *Pengguna*) + pemantauan & laporan |
| **Pengguna** | Beranda Pengguna | Menjalankan tes + melihat riwayat pemeriksaan sendiri |

Menu yang tampil menyesuaikan peran Anda secara otomatis.

---

## 5. Pemeriksaan Pekerja (Alur Utama)

Alur singkat:

```
Pilih pekerja → (opsional) pindai NIK → mulai tes → pekerja menyelesaikan tes
        → tinjau hasil → finalisasi
```

Langkah rinci:

1. **Buka menu Pekerja** (ikon orang di bawah).
2. **Cari pekerja** dengan nama/nomor induk, atau gunakan filter departemen
   dan shift. Anda juga dapat memindai NIK/QR (lihat bagian 6).
3. Ketuk nama pekerja untuk membuka **Detail Pekerja**.
4. Ketuk **Mulai Tes**.
5. **Bacakan instruksi** yang muncul (layar Petunjuk), lalu ketuk **Mulai**.
6. Pekerja menyelesaikan tes waktu reaksi (mis. menyentuh layar saat muncul
   stimulus).
7. Setelah selesai, **tinjau hasil** pada layar hasil.
8. Ketuk **Finalisasi** untuk menyimpan. Setelah finalisasi, hasil **tidak
   dapat diubah** (menjaga keaslian data).

> Jika pemeriksaan terputus di tengah jalan, sesi dapat dilanjutkan atau
> ditandai terputus, lalu dibuat ulang.

---

## 6. Menggunakan Pemindai NIK/QR

1. Dari Beranda atau menu Pekerja, buka **Identifikasi** / tombol pindai.
2. **Izinkan akses kamera** saat diminta (hanya saat pertama kali).
3. Arahkan kamera ke **barcode/QR** pada kartu pekerja.
   - Format QR yang dikenali: `RATIG1:<NIK>`.
4. Jika berhasil, data pekerja muncul otomatis dan NIK **disamarkan**
   (mis. `3201**********99`) demi privasi.
5. Bila kamera tidak memungkinkan, gunakan **input NIK manual**:
   masukkan 16 digit NIK lalu konfirmasi.

> **Catatan keamanan:** hasil pemindaian **bukan** autentikasi. QR asing akan
> ditolak dengan pesan "QR tidak dikenali".

---

## 7. Memahami Hasil Pemeriksaan

Setelah tes selesai, Anda melihat ringkasan hasil:

| Istilah | Arti |
|---|---|
| **Rata-rata (mean)** | Rata-rata waktu reaksi (ms) |
| **Median** | Nilai tengah waktu reaksi |
| **Std. Deviasi** | Sebaran waktu reaksi |
| **Percobaan valid** | Jumlah percobaan yang dihitung |
| **Respons lambat** | Jumlah reaksi melewati ambang lambat |
| **Kategori** | Klasifikasi kelelahan (mis. "Normal", "Perlu perhatian") |

**Kategori** dihitung dari aturan yang **disetujui perusahaan**. Bila aturan
belum divalidasi, aplikasi menampilkan **"Perlu validasi"** (bukan angka yang
menyesatkan).

> Hasil mode **Klasik** yang dipakai untuk klasifikasi. Mode lain memberi
> **metrik pengamatan** (akurasi, kelalaian) dan tidak digabung menjadi satu
> skor.

---

## 8. Mode Pemeriksaan

| Mode | Deskripsi | Klasifikasi |
|---|---|---|
| **Klasik (RGB)** | Stimulus warna; standar kelelahan | ✅ Ya |
| **Warna Acak** | Stimulus warna acak | Data pengamatan |
| **Tombol Acak** | Mencari target di grid tombol | Data pengamatan |
| **Inhibisi (Go/No-Go)** | Menahan diri pada stimulus tertentu | Data pengamatan |

Mode ditentukan oleh **protokol** yang dipilih. Perubahan mode/protokol
dilakukan oleh Super Admin.

---

## 9. Riwayat & Detail Sesi

- **Menu Riwayat** menampilkan daftar pemeriksaan (Semua / 7 / 30 / 90 hari),
  dengan filter departemen, shift, status, dan kategori.
- Ketuk salah satu untuk membuka **Detail Sesi**, berisi:
  - Informasi sesi (pekerja, pemeriksa, shift, protokol, waktu, status)
  - Metadata perangkat (model, OS, versi aplikasi)
  - Hasil pemeriksaan (metrik + kategori)
  - **Rincian percobaan (trial)** per nomor: valid/tidak valid, waktu (ms),
    status respons

---

## 10. Bekerja Tanpa Internet (Offline)

RATIG dirancang **offline-first**:

1. Pemeriksaan tetap dapat dilakukan **tanpa sinyal**.
2. Data disimpan aman di perangkat, ditandai **"Tersimpan di perangkat"**.
3. Saat kembali online, data terkirim otomatis (WorkManager, dengan percobaan
   ulang bertahap).
4. Buka **Status Sinkronisasi** untuk melihat:
   - Jumlah data menunggu, sedang dikirim, dan sudah tersinkron
   - Data yang **perlu pemeriksaan administrator**
5. Tombol **Sinkronkan sekarang** memaksa sinkronisasi segera.

**Arti status:**

| Status | Arti |
|---|---|
| Menunggu koneksi | Tersimpan lokal, menunggu jaringan |
| Tersimpan di perangkat | Tersimpan lokal, siap dikirim |
| Sedang disinkronkan | Sedang dikirim ke server |
| Tersinkronisasi ke server | Berhasil terkirim |
| Gagal, akan dicoba kembali | Gangguan sementara |
| Perlu pemeriksaan administrator | Perlu tindakan manual |

---

## 11. Halaman Super Admin

**Dasbor Super Admin** menampilkan: pekerja aktif, total pengguna, sesi hari
ini/total, hal yang perlu perhatian, klasifikasi hasil, sesi per
departemen/shift, dan tren harian.

**Menu khusus Super Admin** (dari Profil):

- **Pengguna** — kelola akun: setujui, nonaktifkan, ubah peran, dan **tambah
  pengguna** (tombol **+**). Super Admin boleh membuat akun Admin maupun
  Pengguna.
- **Protokol Uji** — daftar & pengaturan protokol pemeriksaan.
- **Aturan Klasifikasi** — ambang batas kategori kelelahan (dapat divalidasi
  perusahaan; versi terpisah).
- **Data Master** — departemen, area kerja, dan shift.
- **Audit Log** — jejak seluruh tindakan penting (mis. `create_user`,
  `approve_account`).

---

## 12. Halaman Admin

Admin melihat **Dasbor Admin** (pemantauan: sesi, tren, per departemen/shift)
dan dapat:

- **Mengelola pengguna** (menu Pengguna): menyetujui, menonaktifkan,
  mengaktifkan kembali, dan **menambah akun Pengguna** (bukan Admin).
- **Laporan** & **Jadwal** & **Tindak Lanjut**.

> Admin **tidak** dapat mengubah Protokol/Aturan/Data Master — itu wewenang
> Super Admin.

---

## 13. Halaman Pengguna

Peran **Pengguna** (pekerja/pemeriksa lapangan) melihat:

- **Beranda Pengguna** — ringkasan sesi pribadi + panduan langkah pemeriksaan.
- **Pekerja** — menjalankan pemeriksaan.
- **Riwayat** — hasil pemeriksaan sendiri.
- **Profil** — akun dan keluar.

Menu lebih ringkas karena hanya menampilkan fungsi yang relevan.

---

## 14. Laporan & Ekspor

1. Buka **Profil → Laporan**.
2. Pilih **periode** (7/30/90 hari atau kustom) dan filter departemen/shift.
3. Lihat **pratinjau**, lalu **ekspor** (CSV).
4. Berkas disimpan di penyimpanan khusus aplikasi dan dibuka melalui
   **dialog berbagi** (kirim ke email, penyimpanan, dsb.).

---

## 15. Menangani Masalah (Troubleshooting)

| Gejala | Penyebab & Solusi |
|---|---|
| Tidak bisa masuk | Akun belum aktif → minta Admin menyetujui. Cek juga koneksi. |
| "QR tidak dikenali" | QR bukan format RATIG → gunakan input NIK manual. |
| Kamera tidak muncul | Izin kamera ditolak → aktifkan di Pengaturan Android → Aplikasi → RATIG → Izin. |
| Data tidak terkirim | Belum ada internet → buka **Status Sinkronisasi**, tunggu online, atau **Sinkronkan sekarang**. |
| Hasil "Perlu validasi" | Aturan klasifikasi belum divalidasi perusahaan → hubungi Super Admin. |
| Layar "Konfigurasi" | Aplikasi belum diatur → hubungi administrator teknis. |
| Detail sesi gagal dimuat | Tekan **Coba lagi**; bila tetap gagal, sinkronkan lalu ulangi. |

---

## 16. Pertanyaan Umum (FAQ)

**T: Apakah hasil RATIG menentukan pekerja boleh bekerja?**
J: Tidak. RATIG hanya alat bantu. Keputusan mengikuti SOP dan pemeriksa.

**T: Bisakah hasil diubah setelah finalisasi?**
J: Tidak. Hasil yang sudah final bersifat permanen untuk menjaga keaslian.

**T: Apakah perlu internet terus-menerus?**
J: Tidak. Pemeriksaan berjalan offline dan tersinkron otomatis saat online.

**T: Apakah data pribadi pekerja aman?**
J: Ya. NIK disamarkan di layar, akses dibatasi per peran, dan tidak ada kunci
layanan yang disimpan di aplikasi.

**T: Bagaimana menambah pekerja baru?**
J: Buka menu **Pekerja → tombol (+)** untuk membuat data pekerja.

**T: Siapa yang boleh menambah akun (pengguna aplikasi)?**
J: Admin (untuk peran Pengguna) dan Super Admin (termasuk Admin), lewat
**Pengguna → (+)**.

---

*Panduan ini untuk pengguna aplikasi. Untuk pemasangan/konfigurasi teknis,
lihat `docs/deployment.md` dan `docs/oauth-setup.md`. Untuk gambaran proyek,
lihat `PROPOSAL.md`.*
