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

## Why vendored instead of a Maven dependency

The original FFmpegKit retired in January 2025 and its binaries were removed
from Maven Central in April 2025; every app that declared it as a remote
dependency stopped building. The binary lives in this repository so the build
cannot be broken by a registry decision. To upgrade: download the new AAR from
Maven Central, replace the file, update the version + hash here, and record
the change in git.
