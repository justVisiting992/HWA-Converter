<div align="center">

<img src="https://raw.githubusercontent.com/justVisiting992/HWA-Converter/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="128" alt="HWA Converter icon">

# HWA Converter

**The Android media converter that encodes on your phone's hardware first.**

Every other converter app encodes on the CPU — even though your phone has a
dedicated media engine built for exactly this. HWA Converter asks the device
what its media engine can actually do, and builds its presets from the answer.

[![Release](https://img.shields.io/github/v/release/justVisiting992/HWA-Converter?style=for-the-badge&labelColor=404040)](https://github.com/justVisiting992/HWA-Converter/releases)
[![Downloads](https://img.shields.io/github/downloads/justVisiting992/HWA-Converter/total?style=for-the-badge&labelColor=404040)](https://github.com/justVisiting992/HWA-Converter/releases)
[![Stars](https://img.shields.io/github/stars/justVisiting992/HWA-Converter?style=for-the-badge&labelColor=404040)](https://github.com/justVisiting992/HWA-Converter)
[![License](https://img.shields.io/github/license/justVisiting992/HWA-Converter?style=for-the-badge&labelColor=404040)](https://github.com/justVisiting992/HWA-Converter/blob/main/LICENSE)

![Hardware first](https://img.shields.io/badge/HARDWARE--FIRST-MEDIACODEC-00C853?style=for-the-badge&labelColor=404040)
![Internet permission](https://img.shields.io/badge/INTERNET_PERMISSION-NONE-1E88E5?style=for-the-badge&labelColor=404040)

![Conversion](https://img.shields.io/badge/CONVERSION-100%25_ON--DEVICE-FF9800?style=for-the-badge&labelColor=404040)
![Engine](https://img.shields.io/badge/ENGINE-FFMPEG_8.1-00ACC1?style=for-the-badge&labelColor=404040)

</div>

---

## ⬇️ Download

Grab the latest APK from the **[Releases page](https://github.com/justVisiting992/HWA-Converter/releases)**:

| APK | Pick this if… |
|---|---|
| `app-arm64-v8a-debug.apk` | You have a normal Android phone — this is almost everyone, including all recent MediaTek, Qualcomm, Exynos and Tensor devices |
| `app-x86_64-debug.apk` | You run an emulator or an x86 Chromebook |
| `app-universal-debug.apk` | You're not sure — it works everywhere, it's just bigger |

Requires **Android 7.0 (API 24)** or newer.

---

## 🤔 Why hardware-first?

Your phone's chip has a media engine: fixed-function silicon that encodes and
decodes video far faster and cooler than the CPU. On a Dimensity 9400+, a
25-minute 1080p AV1 → HEVC conversion ran at **8.3× realtime** with *both*
decode and encode on the media engine — the CPU mostly watched.

Converter apps almost never use it. They ship FFmpeg presets that default to
software encoding, so your phone heats up, the battery drains, and the media
engine sits idle.

HWA Converter flips the default:

1. **Probe** — at startup it enumerates the device's MediaCodec encoders and
   decoders: component names, hardware vs software, max resolution, bitrate
   range, profiles. MediaTek, Qualcomm, Samsung Exynos, HiSilicon Kirin and
   Google Tensor are all probed the same way.
2. **Offer only what's real** — a hardware preset appears only if your chip
   actually has that encoder. No AV1 hardware preset on a chip with no AV1
   encoder. Nothing is faked.
3. **Convert on the engine** — hardware decode into hardware encode wherever
   the device allows it, with bitrates sized to the input and capped by what
   the encoder component itself reports.
4. **Never fall back silently** — if a path you picked isn't available, the
   job says so in plain language. If a hardware-decoded job fails in its
   first seconds, it retries once on CPU decode — and tells you it did.

There are no vendor settings anywhere. The app detects the hardware and
adapts on its own.

---

## 🎛️ Presets

Seventeen presets in five families, plus a full manual mode:

| Family | Presets |
|---|---|
| ⚡ **Hardware encode** | H.264 · H.265/HEVC · VP9 (WebM) · AV1 (WebM) — each shown only when your media engine has it |
| 🎓 **Software encode** | H.264 max quality (libx264, CRF 18) · H.265/HEVC max quality (libx265, CRF 22) · VP9 (WebM) · AV1 |
| 🎵 **Audio only** | AAC · MP3 · Opus · FLAC · WAV |
| 📦 **No re-encode** | Remux to MP4 · Remux to MKV · Trim — stream copy, seconds-fast, quality untouched |
| 🎨 **Create** | Animated GIF |

**Custom mode** puts it all in your hands: container (MP4/MKV/WebM), video
codec (copy, hardware, software, or none), audio codec, bitrate or CRF,
downscale to 1080p/720p/480p/360p, and a hardware-decode toggle.
Combinations that can't work are refused up front, with the reason written
out — never a silent skip.

Hardware encoding is for speed, sharing and everyday converts. Software
max-quality mode is for archiving, where every detail matters. Both are
first-class citizens here.

---

## ✨ Features

- 🔍 **Your hardware screen** — the full probe report: every video encoder
  and decoder on your device, what it can do, and which engine vendor made it
- 🧵 **Background conversions** — a foreground service keeps jobs alive with
  a live notification: progress, speed, ETA, output size, and a Cancel button
- 📋 **A real queue** — jobs run one at a time (hardware encoders are scarce
  silicon), each card showing live progress, the *exact* ffmpeg command that
  ran, and the output estimate before you start
- 🗂️ **Tidy output** — finished files land in `Movies`, `Music` or
  `Pictures` under a **HWA Converter** folder; failed jobs clean up after
  themselves, no half-written files
- 🪵 **Honest debug logs** — every finished job keeps its full log in memory;
  tap it to save to `Download/HWA Converter`, or share it straight from the card
- 🎨 **Make it yours** — Material You plus six fixed color schemes, light /
  dark / follow-the-phone, and **ten launcher icons** to pick from in Settings
- 🧭 **A guided first run** — a short tour points at where things live;
  replay it any time from Settings
- 🖼️ **Thumbnails and facts** — queue cards show a frame from your video,
  its duration, resolution, codec and size at a glance

---

## 🔒 Privacy

The app has **no `INTERNET` permission** — check the
[manifest](https://github.com/justVisiting992/HWA-Converter/blob/main/app/src/main/AndroidManifest.xml)
yourself. No uploads, no analytics, no ads, no accounts. Your files are
converted on your phone and stay on your phone.

---

## 🛠️ Building from source

- Kotlin + Jetpack Compose (Material 3), minSdk 24, targetSdk 35
- Engine: [FFmpegKit](https://github.com/ffmpegkit-maintained/ffmpeg) full-GPL
  8.1.9 (FFmpeg 8.1.2 — libx264, libx265, libvpx, libaom, LAME, Opus), fetched
  from Maven Central by CI against a pinned SHA-256
- `./gradlew assembleDebug` with JDK 17 — or just read the GitHub Actions
  workflow, which builds every push and attaches split APKs to tagged releases

---

## 📜 License & credits

GPL-3.0 — see [LICENSE](https://github.com/justVisiting992/HWA-Converter/blob/main/LICENSE).
The GPL is inherited from the full-GPL FFmpeg build, and it's embraced, not
tolerated: an app built on free software stays free.

Launcher icon artwork by the maintainer. FFmpeg is a trademark of Fabrice
Bellard; this project is not affiliated with the FFmpeg project.
