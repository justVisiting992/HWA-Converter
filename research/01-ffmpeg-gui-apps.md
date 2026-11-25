# Open-source Android FFmpeg GUI converters — research findings

(Research pass 1 of 4, completed 2026-11-25. Facts verified via GitHub API / raw fetches unless marked *unverified*.)

## 1. FFmpeg Media Encoder — silentlexx/ffmpeggui
- Repo: https://github.com/silentlexx/ffmpeggui — ★35, Java, last push 2024-08-06, dormant. **No LICENSE** — source-available only.
- Own static FFmpeg binary (companion buildtool repo); ffmpeg.sh verified: `--enable-mediacodec --enable-jni` + GPL libs (x264, x265, fdk-aac).
- HW accel: binary can do `*_mediacodec` but only via hand-typed raw commands; no preset exposes it; no MediaCodec code in app.
- Architecture: background EncodeService with a job list (real queue with per-job control); user presets + full command editor + live log console.
- **Steal:** every preset is an editable, visible command; service-based job queue. **Avoid:** no license; hardware hidden in UI.

## 2. li-yifei/ffmpeggui (fork) — the most relevant repo in the survey
- Repo: https://github.com/li-yifei/ffmpeggui — ★0, Java, last push 2026-08-14, no license (inherits upstream).
- Adds a working hardware-first layer: `model/HardwareCodecSelector.java` probes MediaCodecList/MediaCodecInfo at runtime, enumerates HW vs SW encoder components per MIME (H.264/HEVC/VP9/AV1), generates `-c:v <codec>_mediacodec -codec_name <component>` (targets FFmpeg 8.1.2; decode uses `-hwaccel mediacodec`). Plus `HardwareCodecCommand.java`, unit tests, `native/ffmpeg/patches/` (three MediaCodec patches: bound decoder drain, avoid NDK encoder flush, validate encoder input), and test scripts (smoke-test-mediacodec.sh, test-mediacodec-encoder-matrix.sh, test-mediacodec-quality.sh).
- **Steal the design wholesale (not code — unlicensed):** runtime codec enumeration, explicit `-codec_name` component selection, device test-matrix approach. Proof FFmpeg's MediaCodec path needs validation around drain/flush edge cases.

## 3. Video Transcoder — brarcher/video-transcoder
- Repo: https://github.com/brarcher/video-transcoder — ★589, GPL-3.0, Java, last push 2024-03-29, F-Droid + Play (`protect.videoeditor`).
- Own FFmpeg AAR executed via process service. **No HW accel, no HEVC, no x264/x265** — codec enum: mpeg4, native h264, mpeg2/1, libvpx, gif.
- Architecture: typed codec/container model classes (MediaInfo, AudioCodec, VideoCodec, MediaContainer) generate FFmpeg args — clean media-model → command-line separation. Single-job service; custom file picker (pre-SAF).
- UX: plain-language speed/quality helper text under codecs ("Fast, Good" / "Slow, Excellent").
- **Steal:** codec-model→args architecture, human speed/quality labels. **Avoid:** custom file picker, frozen 2018 deps.

## 4. ZenConverter — Jasonzhu1207/ZenConverter
- Repo: https://github.com/Jasonzhu1207/ZenConverter — ★1,995, AGPL-3.0, Kotlin + Jetpack Compose (Kotlin 2.2.20), last push 2026-11-24. Most popular + most active in category. Same stack as ours.
- FFmpeg: self-built FFmpegKitNext AAR (arthenica/ffmpeg-kit-next, FFmpeg 7.1.0) vendored in `app/libs/`; Gradle sync fails if AAR missing, with instructions to build the pinned package. Also NCNN+Vulkan for RIFE interpolation; native engines for non-media formats.
- HW accel: none found for video encode (no mediacodec in tree; *unverified* at code level). GPU only for RIFE AI feature.
- Architecture: **Pick → Configure → Queue → Service → Engine → Output**; UI does no conversion; engine routing (FFmpeg "Compatibility" vs Native engines); SAF; foreground service; per-feature stability labels (Stable/Beta); outputs verified after writing.
- **Steal:** almost everything structural — pipeline, engine routing, vendored pinned AAR, stability labels, output verification. Closest architectural sibling; leaves HW encoding on the table = our differentiator. **Avoid:** AGPL if we want a non-copyleft option.

## 5. FFmpeg GUI — marcomorosi06/FFmpegGUI
- Repo: https://github.com/marcomorosi06/FFmpegGUI — ★167, EUPL-1.2, Kotlin + Compose Material 3 Expressive, last push 2026-11-14, `com.cuscus.ffmpeggui`. Most modern UI in category.
- FFmpegKitNext 6.1.1 (FFmpeg n6.1.6) built from pinned sources by `scripts/build-ffmpeg-kit-next.sh` into `media/libs`. Multi-module (:core, :media, :app). 64-bit only deliberately.
- No mediacodec exposure found (*unverified* at code level). Editor-first with live preview; **no INTERNET permission** (badged as feature); fastlane + Obtainium distribution. Pre-1.0.
- **Steal:** M3 Expressive Compose UI, no-internet stance, live preview, pinned-source build script in repo.

## 6. FFShare — caydey/ffshare
- Repo: https://github.com/caydey/ffshare — ★1,148, GPL-3.0, Kotlin, last push 2026-08-14, F-Droid.
- A share-target, not a converter: share media → compress via FFmpeg → forward to share sheet. Zero config screens.
- Self-built FFmpegKitNext 8.1.1 AAR in `libs/` (newest FFmpeg surveyed). No HW path found (*unverified*).
- **Steal:** share-intent entry point — "compress before sending" is the killer use case for HW encoding; removes file-picker friction. Add as secondary entry point.

## 7. Android Media Converter — Khang-NT/Android-Media-Converter
- ★273, GPL-3.0, Kotlin, abandoned 2021. Raw ffmpeg executables bundled in assets/ per arch, extracted + executed as process.
- **Avoid the whole pattern:** fragile under W^X/scoped storage. Historical interest only.

## 8. Timbre — naman14/Timbre
- **Repo returns 404 — gone.** No maintained forks. Cautionary tale; nothing to steal.

## 9. AndroidAudioConverter — adrielcafe (library)
- ★1,352, no license, abandoned 2022. Wrapper around WritingMinds' ancient binary. **Avoid.**

## Long tail (metadata only)
| App | Repo | ★ | License | Notes |
|---|---|---|---|---|
| Simple Audio Converter | TechnicJelle/SimpleAudioConverter | 36 | MPL-2.0 | F-Droid, active, audio only |
| Media Converter Ultimate | simonpham/media_converter_ultimate | 9 | GPL-3.0 | Flutter rewrite of a 1M-download Play app |
| ConvertIt | nasahackerhq/Convertit | 11 | Apache-2.0 | Ad-free audio converter, 2024 |
| ClipFrames | SuhasDissa/ClipFrames | 4 | GPL-3.0 | Simple video converter, 2025 |
| LibreMediaConverter | JMR-dev/LibreMediaConverter | 3 | MIT | On-device FOSS converter, early |
| mmc-apk | danx123/mmc-apk | 4 | MIT | Offline media converter, active |

Other silentlexx forks are untouched 2024 mirrors; mmlo/ffmpeggui pushed 2026-11-23, ★0, *unverified*.

## Category summary
1. **Nobody ships hardware-first.** MediaCodec encoders are never exposed in presets anywhere. Only silentlexx's binary (via raw commands) and li-yifei's ★0 fork have any hardware layer. Our premise is validated — the gap is real and unoccupied.
2. **Survival pattern after FFmpegKit's death:** three independent apps (ZenConverter, FFmpegGUI, FFShare) converged on self-building FFmpegKitNext from pinned sources + vendoring the AAR in `libs/`, build scripts in repo. The maintained Maven fork is a legitimate alternative; vendored-pinned-AAR is what survivors actually do — reproducible, immune to Maven availability.
3. **Architecture consensus:** UI never converts; queue → foreground service; SAF; typed media-model objects generate FFmpeg args. ZenConverter's pipeline is the cleanest statement.
4. **Licensing minefield:** the two most instructive codebases (silentlexx, li-yifei) have no license — study designs, copy nothing. Licensed references: GPL/AGPL (brarcher, ZenConverter, FFShare), EUPL (FFmpegGUI).
5. **UX lessons:** presets show their command (silentlexx); human speed/quality labels (brarcher); share-intent entry point (FFShare); no-INTERNET as a feature (FFmpegGUI); per-feature stability labels (ZenConverter).
6. **Dead ends:** per-arch asset binaries (Khang-NT), WritingMinds-era wrappers, custom file pickers, unlicensed bases.
