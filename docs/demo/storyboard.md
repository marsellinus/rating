# RATIG — Demo Video Storyboard

**Output:** `docs/demo/RATIG-Demo.mp4`
**Format:** 1920×1080 · 30 fps · 1:41 · narasi Bahasa Indonesia
**Sumber visual:** screenshot UI asli di `docs/proposal/ui/`
**Narasi:** TTS `id-ID-ArdiNeural` (edge-tts), tanpa musik pihak ketiga.
**Subtitle:** soft-subtitle Indonesia tertanam di MP4 + `RATIG-Demo.srt`.

> Ini adalah *walkthrough* berbasis screenshot asli, bukan rekaman interaksi
> palsu. Setiap layar yang ditampilkan berasal dari aplikasi RATIG.

---

## Urutan Scene

| # | Kicker | Visual | Narasi inti |
|---|---|---|---|
| 1 | RATIG · DEMO | Login | RATIG mengukur kelelahan lewat waktu reaksi; cepat, objektif, offline. |
| 2 | Masalah | Dasbor | Kelelahan tidak terlihat; pemeriksaan manual dan catatan tersebar. |
| 3 | Alur | Daftar pekerja | Pilih pekerja → tes → hasil → sinkronisasi. |
| 4 | Mode | Protokol | Empat mode; hanya Klasik untuk klasifikasi. |
| 5 | Hasil | Detail sesi | Mean/median, valid trials, kategori, hasil final permanen. |
| 6 | Pemantauan | Dasbor bawah | Klasifikasi, departemen/shift, tren, ekspor CSV. |
| 7 | Peran | Profil | Super Admin/Admin/Pengguna; RLS di server. |
| 8 | Offline | Diagram | Room + WorkManager + Supabase; tetap jalan tanpa sinyal. |
| 9 | Closing | Profil pengguna | Alat bantu keputusan, bukan diagnosis; tautan repo. |

---

## Arah Visual

- Palet: krem `#F4F1E8`, hijau `#123B2A`, amber `#C9943A`.
- Tipografi: Segoe UI Bold untuk headline, Segoe UI untuk body.
- Perangkat ditampilkan dalam frame gelap dengan bayangan halus.
- Progres bar amber di bawah sebagai penanda durasi.
- Tidak ada animasi UI palsu atau data yang direkayasa.

---

## Regenerate

```bash
python docs/demo/build_demo.py
```

Script membutuhkan `edge-tts`, `ffmpeg`, `ffprobe`, dan Pillow. File narasi
sementara disimpan di `docs/demo/audio/` dan dapat dihapus setelah render.
