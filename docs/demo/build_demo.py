"""Build the RATIG demo video from real UI screenshots + Indonesian TTS.

Requirements: Pillow, edge-tts, ffmpeg/ffprobe.
Run from the repository root:
    python docs/demo/build_demo.py
"""

from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
import sys
import urllib.request
import wave
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

FFMPEG = shutil.which("ffmpeg") or r"C:\Users\Administrator\AppData\Local\Microsoft\WinGet\Links\ffmpeg.exe"
FFPROBE = shutil.which("ffprobe") or r"C:\Users\Administrator\AppData\Local\Microsoft\WinGet\Links\ffprobe.exe"

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "docs" / "proposal" / "ui"
OUT = ROOT / "docs" / "demo"
AUDIO = OUT / "audio"
OUT.mkdir(parents=True, exist_ok=True)
AUDIO.mkdir(parents=True, exist_ok=True)

W, H, FPS = 1920, 1080, 30
BG = (244, 241, 232)
INK = (18, 59, 42)
GREEN = (16, 78, 54)
MUTED = (91, 102, 94)
ACCENT = (201, 148, 58)
CARD = (255, 255, 255)
LINE = (217, 211, 198)
BLACK = (8, 18, 13)

SANS = "C:/Windows/Fonts/segoeui.ttf"
SANS_BOLD = "C:/Windows/Fonts/segoeuib.ttf"
SERIF_BOLD = "C:/Windows/Fonts/georgiab.ttf"
MONO = "C:/Windows/Fonts/consola.ttf"


@dataclass(frozen=True)
class Scene:
    kicker: str
    headline: str
    body: tuple[str, ...]
    screenshot: str | None
    narration: str
    diagram: bool = False


SCENES = (
    Scene(
        "RATIG · DEMO APLIKASI",
        "Kelelahan, diukur\nlebih jernih.",
        (
            "Pemeriksaan kelelahan berbasis waktu reaksi.",
            "Cepat · objektif · tetap berjalan tanpa internet.",
        ),
        "01_login.png",
        "RATIG. Aplikasi pemeriksaan kelelahan berbasis waktu reaksi. "
        "Cepat, objektif, dan tetap berjalan tanpa internet.",
    ),
    Scene(
        "01 / MASALAH",
        "Kelelahan sering\ntidak terlihat.",
        (
            "Pekerja merasa kuat — refleksnya sudah menurun.",
            "Pemeriksaan manual lambat dan sulit dibandingkan.",
            "Catatan tersebar di kertas dan spreadsheet.",
        ),
        "02_dashboard_superadmin.png",
        "Kelelahan sering tidak terlihat. Pekerja bisa merasa kuat, padahal "
        "refleksnya sudah menurun. Pencatatan manual juga membuat riwayat sulit "
        "dibandingkan.",
    ),
    Scene(
        "02 / ALUR",
        "Empat langkah.\nSatu alur.",
        (
            "Pilih pekerja atau pindai NIK/QR.",
            "Jalankan tes waktu reaksi.",
            "Tinjau hasil dan finalisasi.",
            "Sinkronkan otomatis saat online.",
        ),
        "08_daftar_pekerja.png",
        "RATIG mempersingkat alur pemeriksaan. Petugas memilih pekerja, "
        "menjalankan tes, dan hasil langsung tampil. Data tersinkron otomatis "
        "saat jaringan tersedia.",
    ),
    Scene(
        "03 / MODE",
        "Empat cara\nmengukur respons.",
        (
            "Klasik (RGB) — klasifikasi kelelahan.",
            "Warna Acak — metrik pengamatan.",
            "Tombol Acak — koordinasi mata–tangan.",
            "Fokus (Go/No-Go) — kontrol impuls.",
        ),
        "17_protokol.png",
        "Empat mode pemeriksaan. Klasik untuk klasifikasi kelelahan. Warna "
        "acak, tombol acak, dan fokus go no go untuk metrik pengamatan. Semua "
        "mode tidak digabung menjadi satu skor.",
    ),
    Scene(
        "04 / HASIL",
        "Hasil langsung.\nJejak tetap.",
        (
            "Mean, median, dan sebaran waktu reaksi.",
            "Jumlah percobaan valid dan respons lambat.",
            "Kategori sesuai aturan perusahaan.",
            "Hasil final permanen untuk audit.",
        ),
        "11_detail_sesi.png",
        "Setelah tes, hasil menampilkan waktu reaksi, jumlah percobaan valid, "
        "dan kategori kelelahan sesuai aturan perusahaan. Hasil final tersimpan "
        "permanen untuk audit.",
    ),
    Scene(
        "05 / PEMANTAUAN",
        "Dasbor untuk\nkeputusan cepat.",
        (
            "Sesi hari ini dan total sesi.",
            "Klasifikasi hasil per kategori.",
            "Sebaran per departemen dan shift.",
            "Tren harian dan ekspor CSV.",
        ),
        "03_dashboard_superadmin_bawah.png",
        "Dasbor admin menampilkan sesi hari ini, klasifikasi hasil, sebaran per "
        "departemen dan shift, serta tren harian. Laporan dapat diekspor ke CSV.",
    ),
    Scene(
        "06 / PERAN",
        "Tiga peran.\nBatas yang jelas.",
        (
            "Super Admin — konfigurasi sistem.",
            "Admin — operasional, pekerja, laporan.",
            "Pengguna — tes dan riwayat sendiri.",
            "RLS menjaga batas ini di server.",
        ),
        "04_profil_superadmin.png",
        "Akses dibagi tiga peran. Super admin untuk konfigurasi, admin untuk "
        "operasional dan pengguna, pengguna untuk tes dan riwayat sendiri. Semua "
        "ditegakkan di server dengan row level security.",
    ),
    Scene(
        "07 / OFFLINE",
        "Tanpa sinyal,\ntetap bisa.",
        (
            "Tes berjalan sepenuhnya offline.",
            "Data tersimpan aman di perangkat.",
            "WorkManager mengirim otomatis saat online.",
            "Server tetap menjadi sumber kebenaran.",
        ),
        None,
        "RATIG tetap bisa dipakai tanpa sinyal. Data disimpan di perangkat dan "
        "dikirim otomatis lewat WorkManager saat online.",
        diagram=True,
    ),
    Scene(
        "RATIG · 2026",
        "Alat bantu keputusan,\nbukan diagnosis.",
        (
            "Keputusan akhir tetap mengikuti SOP perusahaan.",
            "Dokumentasi & repositori tersedia di GitHub.",
        ),
        "21_profil_user.png",
        "RATIG adalah alat bantu keputusan, bukan diagnosis medis. Repositori dan "
        "dokumentasi tersedia di GitHub.",
    ),
)


NARRATION_PATH = OUT / "narration.json"
NARRATION = json.loads(NARRATION_PATH.read_text(encoding="utf-8")) if NARRATION_PATH.exists() else {}
NARRATION_LINES = NARRATION.get("lines", [])


def font(path: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(path, size)


def wrap(draw: ImageDraw.ImageDraw, text: str, fnt, max_width: int) -> list[str]:
    words = text.split()
    lines: list[str] = []
    current = ""
    for word in words:
        candidate = f"{current} {word}".strip()
        if draw.textlength(candidate, font=fnt) <= max_width:
            current = candidate
        else:
            if current:
                lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines


def rounded_mask(size: tuple[int, int], radius: int) -> Image.Image:
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius=radius, fill=255)
    return mask


def paste_phone(base: Image.Image, filename: str, x: int, y: int, height: int) -> Image.Image:
    source = Image.open(UI / filename).convert("RGB")
    width = int(source.width * height / source.height)
    shot = source.resize((width, height), Image.Resampling.LANCZOS)
    radius = 30
    mask = rounded_mask((width, height), radius)

    overlay = Image.new("RGBA", base.size, (0, 0, 0, 0))
    shadow = Image.new("RGBA", base.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.rounded_rectangle(
        (x - 18 + 12, y - 18 + 26, x + width + 18 + 12, y + height + 18 + 26),
        radius=42,
        fill=(11, 27, 19, 95),
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(18))
    overlay = Image.alpha_composite(overlay, shadow)

    frame = Image.new("RGBA", base.size, (0, 0, 0, 0))
    fd = ImageDraw.Draw(frame)
    fd.rounded_rectangle(
        (x - 18, y - 18, x + width + 18, y + height + 18),
        radius=42,
        fill=(10, 23, 17, 255),
    )
    overlay = Image.alpha_composite(overlay, frame)

    phone_layer = Image.new("RGBA", base.size, (0, 0, 0, 0))
    phone_layer.paste(shot.convert("RGBA"), (x, y), mask)
    overlay = Image.alpha_composite(overlay, phone_layer)
    return Image.alpha_composite(base.convert("RGBA"), overlay).convert("RGB")


def draw_diagram(base: Image.Image) -> Image.Image:
    d = ImageDraw.Draw(base)
    x, y, w = 1370, 230, 410
    boxes = (
        ("ANDROID", "Kotlin · Compose", GREEN),
        ("ROOM", "cache + outbox", (34, 104, 74)),
        ("WORKMANAGER", "sync otomatis", (48, 124, 88)),
        ("SUPABASE", "PostgreSQL · RLS", ACCENT),
    )
    for i, (title, subtitle, color) in enumerate(boxes):
        top = y + i * 160
        d.rounded_rectangle((x, top, x + w, top + 116), radius=18, fill=color)
        d.text((x + 24, top + 18), title, font=font(SANS_BOLD, 27), fill=(255, 255, 255))
        d.text((x + 24, top + 61), subtitle, font=font(SANS, 21), fill=(242, 239, 230))
        if i < len(boxes) - 1:
            d.line((x + w // 2, top + 116, x + w // 2, top + 150), fill=MUTED, width=3)
            d.polygon(
                (
                    (x + w // 2 - 7, top + 148),
                    (x + w // 2 + 7, top + 148),
                    (x + w // 2, top + 160),
                ),
                fill=MUTED,
            )
    d.text((x, 892), "OFFLINE-FIRST", font=font(SANS_BOLD, 18), fill=ACCENT)
    return base


def draw_base(scene: Scene, index: int) -> Image.Image:
    base = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(base)

    # Quiet editorial background: rules + large translucent circle.
    d.line((80, 0, 80, H), fill=(229, 222, 208), width=2)
    d.line((112, 0, 112, H), fill=(229, 222, 208), width=1)
    d.ellipse((1500, -260, 2200, 440), fill=(236, 231, 218))
    d.ellipse((1530, -230, 2170, 410), fill=(242, 237, 224))
    d.line((112, 96, 1200, 96), fill=INK, width=4)

    # Header
    d.rounded_rectangle((120, 36, 166, 82), radius=12, fill=GREEN)
    d.text((132, 44), "R", font=font(SERIF_BOLD, 28), fill=BG)
    d.text((184, 42), "RATIG", font=font(SANS_BOLD, 28), fill=INK)
    d.text((184, 74), "Reaction Time Fatigue", font=font(SANS, 16), fill=MUTED)
    d.text((1670, 46), f"{index + 1:02d} / {len(SCENES):02d}", font=font(MONO, 18), fill=MUTED)

    # Kicker + headline
    d.text((120, 168), scene.kicker.upper(), font=font(SANS_BOLD, 20), fill=ACCENT)
    headline_size = 98 if scene.headline.startswith("Kelelahan") else 86
    hfont = font(SANS_BOLD, headline_size)
    hlines = scene.headline.split("\n")
    y = 218
    for line in hlines:
        d.text((120, y), line, font=hfont, fill=INK)
        y += int(headline_size * 0.96)

    # Body
    body_top = y + 26
    bfont = font(SANS, 28)
    for i, item in enumerate(scene.body):
        d.ellipse((124, body_top + 12, 138, body_top + 26), fill=ACCENT)
        lines = wrap(d, item, bfont, 990)
        for line in lines:
            d.text((156, body_top), line, font=bfont, fill=MUTED)
            body_top += 40
        body_top += 12

    # Screenshot or diagram
    if scene.diagram:
        base = draw_diagram(base)
    elif scene.screenshot:
        height = 880
        width = int(1080 * height / 2400)
        x = 1370 + (410 - width) // 2
        y = 118
        base = paste_phone(base, scene.screenshot, x, y, height)
        d = ImageDraw.Draw(base)
        d.text((x, y + height + 34), "UI ASLI RATIG", font=font(SANS_BOLD, 16), fill=MUTED)

    # Footer + progress track
    d.line((120, 1002, 1800, 1002), fill=LINE, width=2)
    d.text(
        (120, 1024),
        "Capstone Project Informatika · Universitas Siliwangi · 2026",
        font=font(SANS, 17),
        fill=MUTED,
    )
    d.text((1770, 1024), "RATIG", font=font(SANS_BOLD, 17), fill=GREEN)
    return base


def run(cmd: list[str], **kwargs) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, check=True, **kwargs)


def duration(path: Path) -> float:
    out = run(
        [
            FFPROBE,
            "-v",
            "error",
            "-show_entries",
            "format=duration",
            "-of",
            "default=noprint_wrappers=1:nokey=1",
            str(path),
        ],
        capture_output=True,
        text=True,
    )
    return float(out.stdout.strip())


def make_audio() -> tuple[Path, list[float]]:
    wavs: list[Path] = []
    durations: list[float] = []
    for index, scene in enumerate(SCENES):
        mp3 = AUDIO / f"scene{index + 1:02d}.mp3"
        wav = AUDIO / f"scene{index + 1:02d}.wav"
        if not mp3.exists():
            run(
                [
                    sys.executable,
                    "-m",
                    "edge_tts",
                    "--voice",
                    "id-ID-ArdiNeural",
                    "--rate",
                    "+4%",
                    "--text",
                    scene.narration,
                    "--write-media",
                    str(mp3),
                ]
            )
        run(
            [
                FFMPEG,
                "-y",
                "-loglevel",
                "error",
                "-i",
                str(mp3),
                "-ar",
                "44100",
                "-ac",
                "2",
                "-c:a",
                "pcm_s16le",
                str(wav),
            ]
        )
        wavs.append(wav)
        durations.append(duration(wav))

    out_wav = AUDIO / "narration.wav"
    silence = b"\x00\x00\x00\x00"
    rate_bytes = 44100 * 2 * 2  # 16-bit stereo

    with wave.open(str(out_wav), "wb") as out:
        out.setnchannels(2)
        out.setsampwidth(2)
        out.setframerate(44100)
        for index, wav in enumerate(wavs):
            with wave.open(str(wav), "rb") as src:
                frames = src.readframes(src.getnframes())
            if index == 0:
                out.writeframes(b"\x00" * int(rate_bytes * 0.45))
            out.writeframes(frames)
            tail = 1.15 if index == len(wavs) - 1 else 0.85
            out.writeframes(b"\x00" * int(rate_bytes * tail))
    return out_wav, durations


def make_video(audio_path: Path, narration_durations: list[float]) -> None:
    leads = [0.45] + [0.0] * (len(SCENES) - 1)
    tails = [0.85] * (len(SCENES) - 1) + [1.15]
    scene_durations = [lead + d + tail for lead, d, tail in zip(leads, narration_durations, tails)]
    total = sum(scene_durations)
    bases = [draw_base(scene, i) for i, scene in enumerate(SCENES)]

    ffmpeg = [
        FFMPEG,
        "-y",
        "-loglevel",
        "error",
        "-f",
        "rawvideo",
        "-pix_fmt",
        "rgb24",
        "-s",
        f"{W}x{H}",
        "-r",
        str(FPS),
        "-i",
        "-",
        "-i",
        str(audio_path),
        "-c:v",
        "libx264",
        "-preset",
        "medium",
        "-crf",
        "20",
        "-pix_fmt",
        "yuv420p",
        "-c:a",
        "aac",
        "-b:a",
        "160k",
        "-shortest",
        "-movflags",
        "+faststart",
        str(OUT / "RATIG-Demo.mp4"),
    ]
    proc = subprocess.Popen(ffmpeg, stdin=subprocess.PIPE)
    assert proc.stdin is not None

    fade = 0.38
    global_start = 0.0
    for index, (base, seconds) in enumerate(zip(bases, scene_durations)):
        frames_n = max(1, round(seconds * FPS))
        for frame_index in range(frames_n):
            t = frame_index / FPS
            img = base.copy()
            # Fade from the previous scene for a soft editorial cut.
            if index > 0 and t < fade:
                alpha = t / fade
                img = Image.blend(bases[index - 1], img, alpha)
            # Fade from the background on the opening scene.
            if index == 0 and t < fade:
                img = Image.blend(bases[0].point(lambda p: 245), img, t / fade)
            draw = ImageDraw.Draw(img)
            progress = (global_start + t) / total
            draw.rectangle((120, 1002, 120 + int(1680 * progress), 1006), fill=ACCENT)
            proc.stdin.write(img.tobytes())
        global_start += seconds

    proc.stdin.close()
    proc.wait()
    if proc.returncode != 0:
        raise SystemExit(f"ffmpeg exited with {proc.returncode}")


def main() -> None:
    print("Generating narration…")
    audio_path, narration_durations = make_audio()
    print("Rendering frames…")
    make_video(audio_path, narration_durations)
    print(f"Done: {OUT / 'RATIG-Demo.mp4'}")


if __name__ == "__main__":
    main()
