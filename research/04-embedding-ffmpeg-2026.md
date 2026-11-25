# Embedding FFmpeg in an Android app, 2025/2026 — verified findings

(Research pass 4 of 4, completed 2026-11-25. Facts verified via GitHub API, Maven Central listings, fork READMEs unless marked ⚠️.)

## FFmpegKit continuation forks

Original `arthenica/ffmpeg-kit`: archived, 5,856★. Retired 2025-01-06, binaries removed from Maven Central 2025-04-01.

### ffmpegkit-maintained — the credible successor
- Repo: **ffmpegkit-maintained/ffmpeg** (no `ffmpeg-kit` repo under that org). 26★, 6 forks, last push 2026-11-24, LGPL-3.0, 1 open issue. Small org with a commercial "Pro" tier (WhisperKit/OCR) — core AARs free on Maven Central. ⚠️ Sustainability caveat: one small org, upsell model.
- Artifacts on Maven Central under `dev.ffmpegkit-maintained`: `ffmpeg-kit-min/https/audio/video/full` + `-gpl` variants of min/https/full, plus `ffmpeg-kit-free`. Latest **8.1.9**; 6.0.4 and 7.1.7 lines maintained in parallel.
- Three LTS lines: 8.1 → FFmpeg n8.1.2, NDK r27c · 7.1 → FFmpeg n7.1.5, NDK r26c · 6.0 → FFmpeg n6.1.6, NDK r26c. Drop-in: same package `com.arthenica.ffmpegkit`, same API — migration is only the group ID.
- **MediaCodec included in every variant** (README verbatim: "MediaCodec hardware acceleration and zlib are included in every variant"). Even `min` has it.
- **16 KB pages enforced**: all .so built with `-Wl,-z,max-page-size=16384`; CI fails on misalignment. minSdk 24, compile/target SDK 35. ABIs: arm64-v8a + x86_64 only.
- GitHub releases current (8.1.9 / 7.1.7 / 6.0.4, Sep–Nov 2026) with per-variant AAR assets.

### Other forks — not competitive
- maitrungduc1410/ffmpeg-kit — 11★, pushed 2026-11-24, Maven `io.github.maitrungduc1410:ffmpeg-kit-*:7.1.5`, 16 KB support, but LGPL-only (no x264/x265 builds), FFmpeg 7.1 ceiling. Viable fallback.
- salahawad/ffmpeg-kit-community — 2★, last push 2025-04-05. Dead.

## Variant matrix & licensing

| Artifact | Adds | License |
|---|---|---|
| ffmpeg-kit-min | bare FFmpeg + MediaCodec/zlib | LGPL-3.0 |
| ffmpeg-kit-https | + gmp, gnutls | LGPL-3.0 |
| ffmpeg-kit-audio | + lame, opus, vorbis, speex, opencore-amr, soxr… | LGPL-3.0 |
| ffmpeg-kit-video | + dav1d, libvpx, kvazaar, libass, theora, webp, zimg, freetype/fontconfig | LGPL-3.0 |
| ffmpeg-kit-full | audio + video + https | LGPL-3.0 |
| ffmpeg-kit-{min,https,full}-gpl | + x264, x265, xvid, vidstab | GPL-3.0 |

- GPL route: linking a `-gpl` AAR makes the combined app GPL-3.0; source must accompany distribution. Zero cost for a GPL-3.0 open-source GitHub project; bites only if ever closed/relicensed.
- Non-GPL `full` loses: x264/x265, xvid, vidstab. Software encode remaining: kvazaar (HEVC), openh264 (H.264, "where enabled" ⚠️), libvpx (VP8/VP9), native mpeg4/aac/mjpeg. Decoding identical in all variants.
- Key point: the GPL gap only affects the software fallback/archival path. MediaCodec HW encode is in the LGPL build.

## Standalone ffmpeg executable in jniLibs
- Still works on Android 16 (verified June 2025 write-up, Pixel 8a; multiple 2026 projects). Ship binary as `jniLibs/arm64-v8a/libffmpeg.so`; targetSdk 29+ W^X blocks exec from app data dir, but `applicationInfo.nativeLibraryDir` is executable; run via ProcessBuilder.
- Binary must be Bionic-linked/static ELF for Android; glibc static can hit "Bad system call" under untrusted_app seccomp. 16 KB-page devices require `max-page-size=16384` ELF alignment (check `readelf -lW`). Whether the 15T Pro runs a 16 KB kernel is unverified — assume yes, costs nothing.
- Costs: parse stderr for progress, own process lifecycle/cancel, no session API, own the binary per ABI.

## Building FFmpeg yourself (Silent Lexx route)
- His buildtool: 17★, last pushed 2022-07-20, README pins NDK r13b / API 21 — recipe reference only. (His shipped app binary is newer; ⚠️ unverified how he builds now.)
- Realistic effort: cross-compile 15–25 external libs per ABI with NDK r26/r27, FFmpeg configure `--enable-mediacodec --enable-jni` (+GPL set), 16 KB alignment everywhere. Days to first build + recurring maintenance tax. The fork already does this with CI. Don't own it on day one.

## Size impact (verified, 8.1.9, dual-ABI AAR)
- min 17.1 MB · full 31.1 MB · full-gpl 37.1 MB. AAR bundles arm64-v8a + x86_64; arm64-only APK ≈ half (≈15 MB full, ≈18 MB full-gpl, estimated from dual/single ratio). Exclude x86_64 — dead weight on phones.
- Standalone binary ≈ 15–25 MB per ABI (⚠️ depends on lib set).

## Recommendation
**Use `dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl:8.1.9`, arm64-v8a only.** Only continuation with current FFmpeg (8.1.2), enforced 16 KB alignment, SDK 35, MediaCodec everywhere, active releases. GPL cost is zero for a GPL-3.0 app; buys x264/x265 for max-quality mode. Isolate the dependency behind one wrapper class so swapping variants/forks is a one-line change.

**Pitfalls:**
1. Bus factor/upsell: pin the version, vendor the AAR in the repo as insurance against another Maven disappearance.
2. Don't self-build FFmpeg until a concrete need appears.
3. MediaCodec ≠ uniform: per-device firmware (c2.mtk.* on Dimensity 9400+). H.264/HEVC HW encode safe; VP9/AV1 HW encode not. Runtime capability probing + honest software fallback mandatory.
4. 16 KB alignment — free from the fork; #1 silent killer on the binary route.
5. GPL hygiene: app license must be GPL-3.0 from the first commit if full-gpl is chosen.
