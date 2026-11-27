# RATIG — Demo Video Storyboard

## 1. Demo Penggunaan (utama)

**Output vertikal:** `docs/demo/RATIG-Demo-Penggunaan.mp4` (1080×2400, 1:11)
**Output presentasi:** `docs/demo/RATIG-Demo-Presentasi.mp4` (1920×1080, 1:11)
**Voice:** `Alice — Clear, Engaging Educator` (`eleven_multilingual_v2`)
**Audio:** 44.1 kHz stereo, loudnorm `I=-16 LUFS`, `TP=-1.5 dB`
**Subtitle:** soft-subtitle Indonesia + `RATIG-Demo-Penggunaan.srt`
**Versi 16:9:** ffmpeg blur-background + video vertikal di tengah.
**Sumber:** rekaman emulator Android; klip mentah tidak disertakan di repo,
hanya hasil akhir dan transkrip.

| # | Bagian | Rekaman | Narasi inti |
|---|---|---|---|
| 1 | Intro | Kartu judul | RATIG, demo penggunaan |
| 2 | Dasbor | `raw-dash.mp4` | Ringkasan pekerja, sesi, klasifikasi, departemen |
| 3 | Pekerja | `raw-workers3.mp4` | Cari pekerja → detail → pilih tes/protokol/shift |
| 4 | Riwayat | `raw-history2.mp4` | Sesi selesai, metrik, trial, kategori |
| 5 | Pengguna | `raw-users3.mp4` | Tambah akun + sandi sementara, tanpa email |
| 6 | Protokol | `raw-proto.mp4` | Menu Ubah, peringatan versi |
| 7 | Audit | `raw-audit.mp4` | Jejak tindakan penting |
| 8 | Closing | Kartu penutup | Alat bantu keputusan, bukan diagnosis |

Rekaman dibuat dari emulator Android dengan `screenrecord`, termasuk indikator
sentuhan. Tidak ada animasi UI palsu; semua layar berasal dari aplikasi.

---

## 2. Video profil (arsip)

**Output:** `docs/demo/RATIG-Demo.mp4`
**Format:** 1920×1080 · 30 fps · 1:41 · narasi edge-tts
**Sumber visual:** screenshot UI asli di `docs/proposal/ui/`
**Subtitle:** soft-subtitle Indonesia + `RATIG-Demo.srt`

| # | Kicker | Visual |
|---|---|---|
| 1 | RATIG · DEMO | Login |
| 2 | Masalah | Dasbor |
| 3 | Alur | Daftar pekerja |
| 4 | Mode | Protokol |
| 5 | Hasil | Detail sesi |
| 6 | Pemantauan | Dasbor bawah |
| 7 | Peran | Profil |
| 8 | Offline | Diagram |
| 9 | Closing | Profil pengguna |

---

## Arah Visual

- Palet: krem `#F4F1E8`, hijau `#123B2A`, amber `#C9943A`.
- Tipografi: Segoe UI Bold untuk headline, Segoe UI untuk body.
- Perangkat ditampilkan dalam frame gelap dengan bayangan halus.
- Progres bar amber di bawah sebagai penanda durasi.
- Tidak ada data yang direkayasa.

---

## Catatan Reproduksi

Demo penggunaan membutuhkan rekaman mentah `raw-*.mp4` di `docs/demo/`.
API key ElevenLabs dibaca dari environment, tidak ditulis ke repo.
