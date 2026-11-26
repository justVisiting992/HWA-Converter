# Media Converter

An all-in-one multimedia converter for Android that encodes on your phone's
hardware first.

Every converter app on Android ships FFmpeg presets that encode on the CPU,
even though the phone has a dedicated media engine that can encode H.264 and
H.265 in hardware. This app asks the device what its media engine can actually
do — at runtime, through MediaCodec — and builds its presets from the answer.
Hardware where it exists, an honest software "max quality" mode where it
doesn't, and no silent fallbacks: if a conversion can't use the path you
picked, the app says so.

## Status

Early development. What exists today:

- Project scaffold (Kotlin + Jetpack Compose, Material 3, arm64-v8a only)
- A "Your hardware" screen that probes the device's MediaCodec codecs live —
  encoders and decoders, component names, hardware vs software, max
  resolution, bitrate range, profiles — the capability report every preset
  is built from
- A preset-to-command builder (`core/engine/PresetCommands.kt`): fifteen
  presets — H.264/HEVC hardware, H.264/HEVC software max-quality, VP9
  (WebM), AV1, audio extraction to AAC/MP3/Opus/FLAC/WAV, remux to
  MP4/MKV without re-encoding, trim, and GIF — turned into exact ffmpeg
  commands from the live report and a probe of the actual input file —
  hardware presets pin the
  probed encoder by name and refuse honestly when the hardware is missing;
  decoding uses the hardware decoder for the input's codec when the
  device has one (a software decode feeding a hardware encode still pegs
  the CPU), with one visible retry on CPU decode if the hardware decoder
  rejects a file; hardware bitrates come from a per-resolution table
  capped at 2x the source bitrate, so a low-bitrate source doesn't
  balloon
- A working Convert tab (`core/engine/ConversionEngine.kt`, `core/queue/`,
  `ui/convert/`): pick files, choose a preset, convert. Jobs run one at a
  time through the vendored FFmpeg with live progress and cancel; inputs
  and outputs move through SAF/MediaStore (outputs in Movies/HWA Converter,
  Music/ for audio), and a failed or cancelled job leaves no half-written
  file behind
- A debug log per finished job: device and app info, the probed input
  (codec, resolution, bitrates), the exact command and the full FFmpeg
  log, written to Download/HWA Converter with a Share log button on the
  job card
- A Settings tab with appearance choices: System/Light/Dark mode and
  seven color scheme templates (Material You plus six fixed palettes,
  each in light and dark), saved between launches
- The app requests the display's highest refresh rate at its native
  resolution (120 Hz on the 15T Pro) instead of accepting a 60 Hz cap
- Presets live in categorized, animated menus (Hardware encode, Software
  encode, Audio only, No re-encode, Create), and a Custom preset opens a
  manual panel: container (MP4/MKV/WebM), video codec (copy, hardware
  H.264/HEVC, software H.264/HEVC/VP9/AV1, none), bitrate or CRF, max
  height, audio codec and bitrate, and a hardware-decode toggle —
  impossible combinations are refused in plain words, like the presets
- Software encoders are told explicitly to use every CPU core
  (`-threads 0`, row-mt where the encoder needs it)
- FFmpeg 8.1.2 (ffmpeg-kit-full-gpl 8.1.9) vendored in `app/libs`, MediaCodec
  included

First target device: Xiaomi 15T Pro (Dimensity 9400+). MediaTek hardware
encoding is the case this app is built for, not an afterthought.

## Design notes

The full research and architecture write-up — a survey of ~30 open-source
converter apps and libraries, and the blueprint this project follows — is in
[ANALYSIS.md](ANALYSIS.md), with the raw research passes in `research/`.

Short version: Pick → Configure → Queue → Service → Engine → Output; a Kotlin
capability layer (probe → rank → explicit hardware encoder → probe encode →
reported fallback ladder) in front of FFmpeg; stream-copy remux as a fast
path; no INTERNET permission, everything happens on-device.

## Building

Open in Android Studio, or:

```
./gradlew assembleDebug
```

Requires JDK 17 and Android SDK 35. The FFmpegKit AAR is vendored, so the
build does not depend on it staying on Maven Central — see
`app/libs/VENDORED.md` for provenance and the hash.

## License

GPL-3.0. The full-gpl FFmpeg build links libx264 and libx265, so the app is
GPL from the first commit.
