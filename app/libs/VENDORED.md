# Vendored dependency: FFmpegKit (full-gpl)

- **File:** `ffmpeg-kit-full-gpl-8.1.9.aar` (37,085,788 bytes)
- **SHA-256:** `72094919ac812598ec3f77ba2631e5b2dbd50b1daa98dfa64e9fc565f2b52ede`
- **Source:** Maven Central, `dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl:8.1.9`
  (the maintained continuation of arthenica/ffmpeg-kit)
- **Contents:** FFmpeg 8.1.2 with libx264, libx265, xvid, vidstab; MediaCodec
  hardware acceleration is included (Android system library). ABIs inside the
  AAR: arm64-v8a + x86_64 — the app build keeps arm64-v8a only.
- **Verified on vendoring (2026-11-26):** arm64 `libffmpegkit.so` PT_LOAD
  alignment is 16384 — safe on 16 KB page-size devices.
- **License:** GPL-3.0 (this variant). This app is GPL-3.0 for the same reason.
- **Only transitive dependency:** `com.arthenica:smart-exception-java:0.2.1`
  (declared in `app/build.gradle.kts`).

## The legacy edition's engine: FFmpegKit 6.0

- **File:** `ffmpeg-kit-full-gpl-6.0.aar` (59,625,466 bytes)
- **SHA-256:** `754159ad0d31f658217c25392fbb55d4a0a66de04cf4a50877c308dfac636375`
- **Source:** Maven Central mirror, `com.arthenica:ffmpeg-kit-full-gpl:6.0`
  (the original arthenica build, retired January 2025, unmaintained)
- **Contents:** FFmpeg 6.0 with the same full-gpl library set. ABIs inside
  the AAR: arm64-v8a, armeabi-v7a, x86, x86_64. It is the last FFmpegKit
  that ever shipped an armeabi-v7a build, which is the only reason it is
  here: a `-PlegacyBuild` build links it instead of 8.1.9 and produces
  the `app-armeabi-v7a` APK for old 32-bit phones.
- **Engine gap that matters:** FFmpeg's VP9 and AV1 MediaCodec encoder
  wrappers arrived in FFmpeg 6.1, so the legacy edition never offers
  those two hardware presets (see `BuildConfig.LEGACY_ENGINE` in
  `PresetCommands`). Every FFmpegKit API the app calls was verified
  present in this AAR with the same signature (javap, 2026-11-28), and
  the whole app compiles against its classes unchanged.
- **License:** GPL-3.0 (this variant).

## Why vendored instead of a Maven dependency

The original FFmpegKit retired in January 2025 and its binaries were removed
from Maven Central in April 2025; every app that declared it as a remote
dependency stopped building. The binary lives in this repository so the build
cannot be broken by a registry decision. To upgrade: download the new AAR from
Maven Central, replace the file, update the version + hash here, and record
the change in git.
