# RATIG — Reaction Time Fatigue

**Poster Ilmiah Capstone Project · Program Studi Informatika, Universitas Siliwangi · 2026**

> **Format cetak:** A2 portrait (420 × 594 mm), 2480 × 3508 px @150 dpi.
> **Berkas siap pakai:** `RATIG-Poster.png`, `RATIG-Poster.pdf`.
> **Gaya:** akademik editorial — hijau hutan, krem, amber; tiga kolom.

---

## Abstrak

Kelelahan kerja menurunkan kewaspadaan dan berkontribusi pada kecelakaan
industri. Pemeriksaan manual sering subjektif, memakan waktu, dan sulit
diaudit. Penelitian pengembangan ini merancang **RATIG**, aplikasi Android
untuk mengukur waktu reaksi pekerja dengan empat mode tes, klasifikasi
berbasis aturan, dan operasi *offline-first*. Sistem dibangun menggunakan
Kotlin/Jetpack Compose, Room/WorkManager, dan Supabase dengan *Row Level
Security* tiga peran. Evaluasi fungsional mencakup 140 unit test JVM,
pengujian finalisasi idempoten, sinkronisasi offline, dan matriks akses
peran. Hasil menunjukkan prototipe berfungsi sesuai rancangan; validasi
klinis dan studi lapangan masih diperlukan sebelum klaim efektivitas.

**Kata kunci:** kelelahan kerja · waktu reaksi · PVT · offline-first ·
Row Level Security · Android

---

## 01 · Latar Belakang

Kelelahan berkontribusi pada kecelakaan industri dan transportasi.¹²
*Psychomotor vigilance task* (PVT) singkat di ponsel dapat menilai penurunan
kewaspadaan.³⁴ Pencatatan manual menyulitkan pemantauan tren dan audit,
terutama di lokasi dengan konektivitas terbatas.

> **Rumusan masalah:** bagaimana merancang pemeriksaan waktu reaksi yang
> cepat, konsisten, aman, dan dapat dipakai offline?

---

## 02 · Tujuan

- Merancang alur pemeriksaan waktu reaksi multi-pekerja di Android.
- Menyediakan klasifikasi kelelahan yang mengikuti aturan organisasi.
- Menjaga integritas data melalui pengukuran monotonik dan hasil final yang
  permanen.
- Membatasi akses data dengan RLS tiga peran.

---

## 03 · Metode

Pengembangan dilakukan melalui analisis kebutuhan, perancangan arsitektur,
implementasi, pengujian unit, dan evaluasi fungsional. **Tidak ada klaim uji
klinis pada tahap ini.**

- **Pengukuran:** monotonic clock (`elapsedRealtimeNanos`), bukan jam dinding.
- **Data:** trial tidak wajar ditandai, bukan dihapus diam-diam.
- **Pengujian:** 140 unit test JVM, finalisasi idempoten, RLS, dan
  sinkronisasi.

---

## 04 · Alur Pemeriksaan

1. **Identifikasi** pekerja melalui NIK/QR; NIK disamarkan.
2. **Pilih mode** dan jalankan tes waktu reaksi.
3. **Tinjau** metrik, trial, dan kategori kelelahan.
4. **Finalisasi**; simpan lokal lalu sinkronkan otomatis.

---

## 05 · Arsitektur Sistem

```
Android (Kotlin + Compose)
  ├── Room             cache + outbox
  ├── WorkManager      sinkronisasi bertahap
  └── MonotonicClock   pengukuran waktu reaksi
              │
              ▼
Supabase
  ├── PostgreSQL + RLS
  ├── Auth
  └── Edge Functions
```

Desain *local-first*⁵ menjaga tes tetap berjalan saat jaringan tidak tersedia;
server tetap menjadi sumber kebenaran.

---

## 06 · Hasil

| Aspek | Capaian |
|---|---|
| Mode | 4 mode (Klasik, Warna Acak, Tombol Acak, Go/No-Go) |
| Peran | 3 peran dengan RLS: Super Admin, Admin, Pengguna |
| Pengujian | **140 unit test JVM lulus** |
| Service key | **0** service key di APK |
| Pengukuran | Monotonic clock; trial tidak wajar tetap tercatat |
| Klasifikasi | Aturan tervalidasi; “Perlu validasi” bila belum |
| Offline | Room + WorkManager; finalisasi idempoten |
| Keamanan | RLS server-side; hasil final immutable; audit log |

---

## 07 · Implementasi Antarmuka

**Gambar 1.** Antarmuka operasional RATIG: pekerja & filter, detail pekerja,
hasil/trial, protokol & versi, serta audit log. Tangkapan layar dari prototipe.

---

## 08 · Pembahasan

- **Waktu monotonik** mengurangi galat akibat perubahan jam sistem.
- **RLS di server** mencegah UI menjadi satu-satunya kontrol akses; Admin
  tidak dapat mengubah konfigurasi atau mengangkat admin.
- **Hasil final immutable** dan audit log menjaga ketertelusuran.
- **Offline-first** memperluas konteks penggunaan, dengan sinkronisasi
  idempoten saat online.

---

## 09 · Keterbatasan

- Belum ada validasi klinis atau uji lapangan berskala besar.
- Ambang klasifikasi bergantung pada SOP dan validasi organisasi.
- Skrining kesehatan paramedis masih disimpan lokal pada perangkat.
- Perubahan protokol aktif disimpan pada versi yang sama; tata kelola versi
  perlu diperkuat.

---

## 10 · Kesimpulan

RATIG menunjukkan bahwa pemeriksaan waktu reaksi multi-pekerja dapat
dirancang sebagai aplikasi Android yang offline-first, terlacak, dan berlapis
keamanan. Prototipe ini layak dilanjutkan ke evaluasi kegunaan dan validasi
lapangan, tetapi belum dapat menggantikan diagnosis atau keputusan akhir
kelayakan kerja.

---

## 11 · Referensi

1. **Dawson, D., et al.** (2021). Determining the contributing role of fatigue
   in industrial and transportation accidents. *Sleep Medicine Reviews*, 56,
   101411.
2. **Williamson, A., et al.** (2011). The compounding link between fatigue,
   sleep loss, and unintentional industrial accidents. *Safety Science*, 49(4),
   498–515.
3. **Basner, M., & Dinges, D. F.** (2011). Maximizing sensitivity of the
   psychomotor vigilance test to sleep loss. *Sleep*, 34(5), 581–591.
4. **Grant, D. A., et al.** (2017). 3-minute smartphone-based and tablet-based
   psychomotor vigilance tests. *Behavior Research Methods*, 49(3), 1020–1029.
5. **Kleppmann, M., et al.** (2019). Local-first software: you own your data,
   in spite of the cloud. *Onward! 2019*, 154–178.
6. **Chhetri, B., et al.** (2024). Human-in-the-loop AI frameworks for
   occupational safety decision support. *Safety Science*, 170, 106360.

---

## Footer

**Alat bantu keputusan, bukan diagnosis medis.** Keputusan akhir tetap
mengikuti SOP perusahaan dan penilaian pemeriksa yang berwenang. NIK
disamarkan; data pribadi dilindungi berlapis. Evaluasi ini merupakan pengujian
fungsional prototipe, bukan uji klinis.

QR repositori: `repo-qr.png` · `github.com/marsellinus/rating`
