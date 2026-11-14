## RATIG v1.3.0 — Skrining Paramedis, Empat Mode Aktif, Indikator Seragam

Rilis ini menutup celah yang membuat data pengujian tidak dapat dipercaya dan
membuat tiga dari empat mode tidak dapat dipakai.

### Skrining kesehatan paramedis (baru)
- Catat tekanan darah, nadi, dan tes alkohol pada sesi yang sama dengan tes
  waktu reaksi.
- Temuan otomatis dengan ambang yang sama seperti aplikasi desktop RATIG
  (Sistolik/Diastolik/Nadi/Alkohol), plus rekomendasi **Layak bekerja /
  Layak dengan catatan / Tidak layak bekerja**.
- Nilai kosong tidak pernah dianggap normal.
- Skrining tampil kembali di layar hasil dan di detail riwayat sesi.

### Empat mode pemeriksaan kini benar-benar dapat dipakai
- Sebelumnya sesi selalu dibuat sebagai mode Klasik, sehingga Warna Acak, Kisi
  Tombol, dan Fokus & Inhibisi tidak pernah bisa dijalankan. Kini ada pemilih
  mode; benih acak dan konfigurasi mode dibekukan saat sesi dibuat.

### Perbaikan ketelitian pengukuran
- Waktu reaksi tidak lagi membengkak hingga satu frame (16,7 ms pada 60 Hz):
  penanda stimulus kini memakai waktu frame yang benar-benar tampil.
- Pembulatan ke milidetik terdekat, bukan pemotongan.

### Perbaikan engine mode
- Sesi tidak lagi hilang saat selesai: event penyelesaian dapat diterima
  walaupun pengamat terlambat terpasang.
- Mode Fokus & Inhibisi dijamin memuat minimal satu stimulus No-Go dan satu
  stimulus Go, sehingga yang diukur benar-benar inhibisi.
- Menekan sel kosong pada Kisi Tombol kini tercatat sebagai respons salah
  (sebelumnya tidak terhitung).

### Indikator warna yang seragam dan informatif
- Satu skala warna untuk semua layar (Normal / Ringan / Sedang / Berat).
- Legenda kini menampilkan **rentang waktu reaksi** yang berlaku beserta
  **arti operasionalnya**, bukan sekadar empat kata.
- Mode pengamatan memperoleh indikator **Kualitas Respons** sendiri (akurasi,
  dengan kelalaian sebagai penentu utama) dan tidak digabung menjadi skor
  kelelahan.

### Lain-lain
- Tombol **Periksa Pekerja Berikutnya** pada layar hasil, sesuai alur
  pemeriksaan banyak pekerja.
- Umpan balik getar saat mengetuk, dipicu setelah waktu respons dicatat.
- Rumus akurasi sisi server diperbaiki: penolakan benar (correct rejection)
  sebelumnya tidak pernah terhitung.

**Pengujian:** 124 unit test JVM lulus tanpa kegagalan (sebelumnya 105),
mencakup keempat mode, ketelitian waktu, jaminan cakupan No-Go, skrining, dan
rentang legenda.
