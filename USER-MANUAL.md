# RATIG — Buku Panduan Pengguna (User Manual)

**Sistem Pemeriksaan Kelelahan Berbasis Waktu Reaksi**
Versi aplikasi 1.0.0 · Android

---

## Daftar Isi

1. [Pengenalan](#1-pengenalan)
2. [Persyaratan Perangkat](#2-persyaratan-perangkat)
3. [Setup Awal & Masuk](#3-setup-awal--masuk)
4. [Mengenal Peran Pengguna](#4-mengenal-peran-pengguna)
5. [Menambah Pekerja Baru](#5-menambah-pekerja-baru)
6. [Pemeriksaan Pekerja (Alur Utama)](#6-pemeriksaan-pekerja-alur-utama)
7. [Menggunakan Pemindai NIK/QR](#7-menggunakan-pemindai-nikqr)
8. [Memahami Hasil Pemeriksaan](#8-memahami-hasil-pemeriksaan)
9. [Mode Pemeriksaan](#9-mode-pemeriksaan)
10. [Riwayat & Detail Sesi](#10-riwayat--detail-sesi)
11. [Bekerja Tanpa Internet (Offline)](#11-bekerja-tanpa-internet-offline)
12. [Halaman Super Admin](#12-halaman-super-admin)
13. [Halaman Admin](#13-halaman-admin)
14. [Halaman Pengguna](#14-halaman-pengguna)
15. [Laporan & Ekspor](#15-laporan--ekspor)
16. [Menangani Masalah (Troubleshooting)](#16-menangani-masalah-troubleshooting)
17. [Pertanyaan Umum (FAQ)](#17-pertanyaan-umum-faq)

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
| Akun | Akun email/kata sandi yang dibuat oleh Admin (tidak ada pendaftaran mandiri) |

---

## 3. Setup Awal & Masuk

Saat pertama kali dibuka, aplikasi menampilkan **Setup Awal** dengan pratinjau
langsung — setiap pilihan langsung terlihat:

1. **Bahasa** — Bahasa Indonesia atau English.
2. **Mode Tampilan** — Ikut Ponsel, Terang, atau Gelap.
3. **Ukuran Tulisan** — Normal, Besar, atau Sangat Besar.

Ketuk **Simpan & Lanjut**, lalu ikuti **panduan singkat (tutorial)** dan tekan
**Mulai Menggunakan** (atau **Lewati**). Keduanya dapat dibuka lagi dari
**Profil → Pengaturan Tampilan** dan **Profil → Panduan Penggunaan**.

Cara masuk:

1. Isi **Email** dan **Kata sandi** yang diberikan administrator.
2. Centang **Ingat saya di perangkat ini** bila ingin email terisi otomatis
   pada pembukaan berikutnya.
3. Tekan **Masuk**.

> **Tidak ada pendaftaran mandiri.** Akun dibuat oleh administrator. Bila belum
> punya akun, hubungi administrator untuk didaftarkan.

**Jika akun baru:** akun berstatus *Menunggu*. Anda akan melihat layar
**Menunggu Persetujuan**. Hubungi Admin untuk mengaktifkan akun, lalu tekan
**Periksa Status**.

**Jika aplikasi belum dikonfigurasi:** muncul layar *Konfigurasi*. Hubungi
administrator teknis (lihat `docs/deployment.md`).

**Lupa keluar / ganti akun:** buka **Profil → Keluar dari Akun**.

### 3.1 Akun Demo (untuk mencoba)

Akun uji yang tersedia. Kata sandi semua akun: **`Ratig-T3st-2026!`**

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
| **Super Admin** | Dasbor Super Admin | Semua hal: kelola admin & pengguna, protokol, aturan, data master, audit, pemantauan |
| **Admin** | Dasbor Admin | Kelola pekerja & pengguna (peran *Pengguna*) + pemantauan & laporan |
| **Pengguna** | Beranda Pengguna | Menjalankan tes + melihat riwayat pemeriksaan sendiri |

Menu yang tampil menyesuaikan peran Anda secara otomatis. Pembatasan ini
**ditegakkan juga di server** (RLS). Jadi walaupun Admin mencoba memanggil API
langsung, Admin tetap tidak dapat mengubah protokol/aturan/data master, tidak
dapat mengangkat Pengguna menjadi Admin, dan tidak dapat membuat akun Admin.

---

## 5. Menambah Pekerja Baru

**Siapa:** Admin atau Super Admin. Buka **menu Pekerja → tombol (+) Tambah**
(bukan menu Pengguna — lihat §13 untuk akun aplikasi).

Isi kolom berikut:

| Kolom | Wajib? | Keterangan |
|---|---|---|
| Nomor Induk Pekerja | Ya | 4–32 digit angka (NIP/NIK). Tidak boleh duplikat. |
| Nama Lengkap | Ya | Nama yang tampil di daftar pekerja. |
| Email login | Opsional | Boleh kosong. Boleh juga pakai ID internal seperti `320123...@ratig.local` — **tidak perlu hosting email**. |
| Kata Sandi Login | Opsional | Min. 8 karakter. Isi sandi untuk membuat akun login. |
| Departemen / Area Kerja / Shift | Opsional | Area Kerja aktif setelah Departemen dipilih. |
| Jabatan | Opsional | Contoh: Operator, Ketua Regu. |

Dua kemungkinan hasil:

- **Sandi diisi** → aplikasi membuat **data pekerja + akun login** sekaligus di
  server. Akun langsung aktif. Jika nomor induk/email sudah terdaftar, Simpan
  ditolak dengan pesan duplikat — tidak ada akun setengah jadi.
- **Sandi dikosongkan** → hanya **data pekerja** yang dibuat. Pekerja ini belum
  punya login; pemeriksa yang menjalankan tes atas namanya dari menu Pekerja.

**ID login tanpa email nyata:** bila sandi diisi tetapi kolom email dikosongkan,
aplikasi membuat ID otomatis `<nomor induk>@ratig.local` (mis.
`3201234567890001@ratig.local`). ID ini hanya untuk masuk, tidak ada email yang
dikirim. Berikan ID + sandi langsung ke pekerja.

> Tidak perlu hosting email atau email kantor. Domain `@ratig.local` sengaja
> dipakai sebagai pengenal internal; Supabase tetap menerimanya sebagai ID login
> karena akun sudah dikonfirmasi saat dibuat.

---

## 6. Pemeriksaan Pekerja (Alur Utama)

Alur singkat:

```
Pilih pekerja → (opsional) pindai NIK → mulai tes → pekerja menyelesaikan tes
        → tinjau hasil → finalisasi
```

Langkah rinci:

1. **Buka menu Pekerja** (ikon orang di bawah).
2. **Cari pekerja** dengan nama/nomor induk, atau gunakan filter departemen
   dan shift. Anda juga dapat memindai NIK/QR (lihat bagian 7).
3. Ketuk nama pekerja untuk membuka **Detail Pekerja**.
4. Ketuk **Mulai Tes**.
5. **Pilih jenis tes** pada dialog:
   - **Tes Kelelahan Standar** — reaksi terhadap warna (hasil diklasifikasi).
   - **Warna Acak** — tekan saat muncul warna/bentuk target.
   - **Tombol Acak** — cari dan tekan tombol berlambang target.
   - **Fokus (Go/No-Go)** — tekan saat hijau, tahan saat merah.
6. **Bacakan instruksi** yang muncul (layar Petunjuk), centang "Pekerja
   memahami instruksi", lalu ketuk **Mulai Tes**.
7. Pekerja menyelesaikan tes waktu reaksi.
8. Setelah selesai, **tinjau hasil** pada layar hasil.
9. Ketuk **Selesai** untuk kembali. Setelah finalisasi, hasil **tidak dapat
   diubah** (menjaga keaslian data).

> Jika pemeriksaan terputus di tengah jalan, sesi dapat dilanjutkan atau
> ditandai terputus, lalu dibuat ulang.

---

## 7. Menggunakan Pemindai NIK/QR

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

## 8. Memahami Hasil Pemeriksaan

Setelah tes selesai, Anda melihat ringkasan hasil:

| Istilah | Arti |
|---|---|
| **Rata-rata (mean)** | Rata-rata waktu reaksi (ms) |
| **Median** | Nilai tengah waktu reaksi |
| **Std. Deviasi** | Sebaran waktu reaksi |
| **Percobaan valid** | Jumlah percobaan yang dihitung |
| **Respons lambat** | Jumlah reaksi melewati ambang lambat |
| **Kategori** | Klasifikasi kelelahan: Normal, Fatigue Ringan, Fatigue Sedang, Fatigue Berat |

**Kategori** dihitung dari aturan yang **disetujui perusahaan**. Bila aturan
belum divalidasi, aplikasi menampilkan **"Perlu validasi"** (bukan angka yang
menyesatkan).

> Hasil mode **Klasik** yang dipakai untuk klasifikasi. Mode lain memberi
> **metrik pengamatan** (akurasi, kelalaian) dan tidak digabung menjadi satu
> skor.

### 8.1 Skrining Kesehatan (Paramedis)

Selain tes waktu reaksi, petugas paramedis dapat mencatat pemeriksaan fisik
pada sesi yang sama. Dari layar hasil, ketuk **Skrining Kesehatan
(Paramedis)**, lalu isi:

| Kolom | Satuan | Keterangan |
|---|---|---|
| Sistolik | mmHg | Tekanan darah atas |
| Diastolik | mmHg | Tekanan darah bawah |
| Nadi | bpm | Denyut jantung saat istirahat |
| Alkohol | % | Hasil tes napas |

Kosongkan kolom yang tidak diukur — nilai kosong **tidak** dianggap normal.

Aplikasi langsung menampilkan **temuan** dan **rekomendasi**:

| Temuan | Rentang |
|---|---|
| Hipotensi / Normal / Hipertensi Ringan / Sedang / Berat | Sistolik <100 / <140 / <160 / <180 / ≥180 |
| Hipotensi / Normal / Hipertensi Ringan / Sedang / Berat | Diastolik <70 / <90 / <100 / <120 / ≥120 |
| Bradikardia / Normal / Takikardia Ringan / Sedang / Berat | Nadi <60 / <100 / <110 / <120 / ≥120 |
| Lolos / Tidak Lolos | Alkohol <0,08 % / ≥0,08 % |

**Rekomendasi** menggabungkan skrining dengan tingkat kelelahan, mengambil
kondisi terburuk: **Layak bekerja**, **Layak dengan catatan**, atau
**Tidak layak bekerja**.

> Rekomendasi bersifat saran berbasis angka. **Keputusan akhir kelayakan kerja
> tetap pada pemeriksa yang berwenang**, sesuai SOP perusahaan.

---

## 9. Mode Pemeriksaan

| Mode | Deskripsi | Klasifikasi |
|---|---|---|
| **Klasik (RGB)** | Stimulus warna; standar kelelahan | ✅ Ya |
| **Warna Acak** | Stimulus warna acak | Data pengamatan |
| **Tombol Acak** | Mencari target di grid tombol | Data pengamatan |
| **Inhibisi (Go/No-Go)** | Menahan diri pada stimulus tertentu | Data pengamatan |

Mode ditentukan oleh **protokol** yang dipilih. Perubahan mode/protokol
dilakukan oleh Super Admin.

---

## 10. Riwayat & Detail Sesi

- **Menu Riwayat** menampilkan daftar pemeriksaan (Semua / 7 / 30 / 90 hari),
  dengan filter departemen, shift, status, dan kategori.
- Ketuk salah satu untuk membuka **Detail Sesi**, berisi:
  - Informasi sesi (pekerja, pemeriksa, shift, protokol, waktu, status)
  - Metadata perangkat (model, OS, versi aplikasi)
  - Hasil pemeriksaan (metrik + kategori)
  - **Rincian percobaan (trial)** per nomor: valid/tidak valid, waktu (ms),
    status respons

---

## 11. Bekerja Tanpa Internet (Offline)

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

## 12. Halaman Super Admin

**Dasbor Super Admin** menampilkan: pekerja aktif, total pengguna, sesi hari
ini/total, hal yang perlu perhatian, klasifikasi hasil, sesi per
departemen/shift, dan tren harian.

**Menu khusus Super Admin** (dari Profil):

- **Pengguna** — kelola akun: setujui, nonaktifkan, ubah peran, dan **tambah
  pengguna** (tombol **+**). Super Admin boleh membuat akun Admin maupun
  Pengguna.
- **Pekerja** — seperti Admin, dapat menambah pekerja sekaligus akun login
  lewat **Pekerja → (+)** (lihat §5).
- **Protokol Uji** — daftar & pengaturan protokol pemeriksaan. Menu **Ubah**
  tersedia untuk protokol berstatus apa pun. Untuk protokol **Aktif**, perubahan
  disimpan pada **versi yang sama** dan langsung berlaku untuk sesi baru; layar
  edit menampilkan peringatan ini sebelum Anda menyimpan.
- **Aturan Klasifikasi** — ambang batas kategori kelelahan (dapat divalidasi
  perusahaan; versi terpisah).
- **Data Master** — departemen, area kerja, dan shift.
- **Audit Log** — jejak seluruh tindakan penting (mis. `create_user`,
  `approve_account`).

---

## 13. Halaman Admin

Admin melihat **Dasbor Admin** (pemantauan: sesi, tren, klasifikasi hasil,
per departemen/shift) dan dapat:

- **Mengelola pekerja** (menu Pekerja): menambah, mencari, mengubah, dan
  menonaktifkan data pekerja — termasuk membuat akun login bila diperlukan
  (lihat §5).
- **Mengelola pengguna** (Profil → Pengguna): menyetujui, menonaktifkan,
  mengaktifkan kembali, dan **menambah akun Pengguna** (bukan Admin).
- **Laporan** & **Jadwal & Shift** & **Tindak Lanjut**.

> Admin **tidak** dapat mengubah Protokol/Aturan/Data Master — itu wewenang
> Super Admin. Aturan ini berlaku baik lewat aplikasi maupun lewat API.

---

## 14. Halaman Pengguna

Peran **Pengguna** (pekerja/pemeriksa lapangan) melihat:

- **Beranda Pengguna** — ringkasan sesi pribadi + panduan langkah pemeriksaan.
- **Pekerja** — menjalankan pemeriksaan.
- **Riwayat** — hasil pemeriksaan sendiri.
- **Profil** — akun dan keluar.

Menu lebih ringkas karena hanya menampilkan fungsi yang relevan.

---

## 15. Laporan & Ekspor

1. Buka **Profil → Laporan**.
2. Pilih **periode** (7/30/90 hari atau kustom) dan filter departemen/shift.
3. Lihat **pratinjau**, lalu **ekspor** (CSV).
4. Berkas disimpan di penyimpanan khusus aplikasi dan dibuka melalui
   **dialog berbagi** (kirim ke email, penyimpanan, dsb.).

---

## 16. Menangani Masalah (Troubleshooting)

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

## 17. Pertanyaan Umum (FAQ)

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
J: Buka menu **Pekerja → tombol (+)**. Isi nomor induk dan nama. Isi **email +
kata sandi** bila pekerja perlu login di HP sendiri; bila tidak, cukup data
pekerjanya yang dibuat. Rincian di §5.

**T: Siapa yang boleh menambah akun (pengguna aplikasi)?**
J: Admin hanya boleh membuat akun **Pengguna**, lewat **Profil → Pengguna →
(+)**. Super Admin boleh membuat akun **Admin** maupun **Pengguna**. Untuk
pekerja sekaligus akun login, gunakan menu **Pekerja → (+)**.

**T: Tidak ada hosting email. Bagaimana cara undangannya?**
J: **Tidak ada email undangan.** Admin mengisi **ID Login** (boleh alamat
internal seperti `nama@ratig.local`) dan **kata sandi sementara**, lalu
menyampaikan keduanya langsung ke orang yang bersangkutan. Akun langsung aktif;
tidak perlu mail server.

**T: Bolehkah Admin mengubah aturan klasifikasi atau data master?**
J: Tidak. Menu tersebut hanya untuk Super Admin, dan server menolak percobaan
akses langsung lewat API. Ini mencegah satu admin mengubah kriteria hasil
pemeriksaan seluruh perusahaan.

**T: Kenapa grafik di dasbor bisa kosong?**
J: Grafik mengikuti data yang ada. **Klasifikasi Hasil Pemeriksaan** hanya
menghitung tes Klasik yang sudah selesai & final; **Sesi per Departemen**
menghitung sesi yang pekerjanya memiliki departemen. Bila belum ada data /
pekerja belum ditetapkan departemennya, grafik belum menampilkan batang.

---

*Panduan ini untuk pengguna aplikasi. Untuk pemasangan/konfigurasi teknis,
lihat `docs/deployment.md` dan `docs/oauth-setup.md`. Untuk gambaran proyek,
lihat `PROPOSAL.md`.*
