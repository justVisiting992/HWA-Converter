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
- A "Your hardware" screen that probes the device's MediaCodec encoders live —
  component names, hardware vs software, max resolution, bitrate range,
  profiles — the capability report every preset will be built from
- A preset-to-command builder (`core/engine/PresetCommands.kt`): five v1
  presets (H.264/HEVC hardware, H.264 software max-quality, audio extract,
  trim without re-encode) turned into exact ffmpeg commands from the live
  report — hardware presets pin the probed component by name and refuse
  honestly when the hardware is missing, never silently falling back to CPU
- A working Convert tab (`core/engine/ConversionEngine.kt`, `core/queue/`,
  `ui/convert/`): pick files, choose a preset, convert. Jobs run one at a
  time through the vendored FFmpeg with live progress and cancel; inputs
  and outputs move through SAF/MediaStore (outputs in Movies/HWA Converter,
  Music/ for audio), and a failed or cancelled job leaves no half-written
  file behind
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
