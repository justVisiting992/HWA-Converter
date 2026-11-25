# MediaConverter — competitive analysis & production blueprint

Synthesized 2026-11-25 from four verified research passes (detail in `research/01–04`). Target: Android all-in-one multimedia converter, Kotlin + Jetpack Compose, FFmpeg engine, **hardware-first via MediaCodec** — built first for the Xiaomi 15T Pro (Dimensity 9400+), correct on any Android device.

## The one-sentence verdict

**The gap is real and unoccupied:** across ~30 open-source apps surveyed, not one exposes hardware encoding in its UI — the only hardware layers in existence are a ★0 fork (li-yifei/ffmpeggui) and one ★0 newcomer (snapConverter). Everyone else encodes in software or hides MediaCodec behind hand-typed commands.

## What the field taught us

**Engine survival.** FFmpegKit (arthenica) retired Jan 2025; its Maven binaries vanished Apr 2025, and that disappearance is what killed or froze half the category. Survivors converged on two patterns: (a) self-build FFmpegKitNext from pinned sources and **vendor the AAR** (ZenConverter, FFmpegGUI, FFShare), or (b) the maintained Maven fork `dev.ffmpegkit-maintained` (FFmpeg 8.1.2, SDK 35, 16 KB-aligned, MediaCodec in every variant). Timbre's repo is simply gone (404); brarcher, Khang-NT, SiliCompressor are dormant/dead. The wrapper layer is where these apps die — so we pin, vendor, and isolate it behind one wrapper class.

**Architecture consensus.** UI never converts. The proven shape (ZenConverter, stated best): **Pick → Configure → Queue → Service → Engine → Output**, foreground service, SAF file access, typed media-model objects generating FFmpeg args, output verified after writing. Queue gold standard: mixed file types with per-item settings (ZenConverter) + bulk-edit (YTDLnis).

**The capability layer is the product.** The native MediaCodec world (LiTr, Media3 Transformer, Telegram-derived LightCompressor) converged on: query `MediaCodecList` capabilities → rank profiles against what the device advertises → configure conservatively → **degrade in steps and report what happened** (Media3's ladder: requested format → dimension-aligned retry → lower resolution → lower bitrate → fail loudly). Through FFmpeg we get no typed codec errors — just a dead process — so this Kotlin layer in front of FFmpeg, plus a tiny probe encode before long jobs, is our core engineering. Nobody in the FFmpeg-GUI world has built it. li-yifei's fork proves the design (runtime enumeration + explicit `-codec_name c2.mtk.*` selection) and that FFmpeg's MediaCodec path has drain/flush edge cases needing validation.

**MediaCodec realities to design around** (pass 2): dimension alignment (multiples of 16) is the #1 configure-killer; no CRF on hardware — bitrate tables only; 8-bit input only (HDR/10-bit belongs to software mode); AV1 hardware *encode* doesn't exist on MediaTek; CDD guarantees far less than the 9400+ silicon offers, so detection confirms, never assumes; codec instances are scarce — hardware jobs run strictly sequentially; MediaCodec mutates timestamps — avoid mid-stream encoder restarts.

**UX consensus.** Home screen: searchable categorized tool list + favorites (ImageToolbox — the only pattern proven at 30+ tools), every tool on one scaffold (pick → options → preview/progress → save). Front door stays Seal-simple: one input, one action; concrete options (real codecs/sizes from probing the file) appear after selection. Presets always show their generated command (silentlexx) with human speed/quality labels (brarcher). Power tier = saved custom-command templates (Seal/YTDLnis), never raw flags as the primary UI. Entry points: share sheet / open-with from day one (FFShare — "compress before sending" is the killer hardware job). No INTERNET permission, marketed (FFmpegGUI). Per-feature stability labels (ZenConverter). SAF with long-press one-time output-folder override (ImageToolbox). Filename templates with live preview + settings backup/restore (YTDLnis).

## Production decisions (locked by this research)

| Decision | Choice | Why |
|---|---|---|
| Engine | `ffmpeg-kit-full-gpl` 8.1.9 from `dev.ffmpegkit-maintained`, **AAR vendored in repo**, wrapped in one class | Current FFmpeg 8.1.2, MediaCodec everywhere, 16 KB-safe; vendoring = immunity to another Maven disappearance; wrapper = swappable |
| App license | **GPL-3.0** from the first commit | full-gpl (x264/x265 for max-quality mode) requires it; zero cost for an open-source GitHub project |
| ABIs | arm64-v8a only | x86_64 is dead weight; arm64-only APK ≈ 18 MB |
| Stack | Kotlin + Jetpack Compose, Material 3, single-activity | The living apps' stack (ZenConverter, FFmpegGUI, ImageToolbox, Seal) |
| Job model | Pick → Configure → Queue → Foreground Service → Engine → Output; mixed queue, per-item settings, bulk-edit | Category consensus + best queue designs found |
| Hardware layer | Kotlin capability service: MediaCodecList probe → profile ranking → command builder with explicit `-codec_name` → probe encode → Media3-style fallback ladder, every fallback reported in the job log | The differentiator; designed from LiTr + Media3 + li-yifei evidence |
| Hardware presets | Bitrate-based (resolution×fps tables), dimension pre-alignment to 16 | No CRF exists on hardware; alignment is the #1 failure |
| Software mode | libx265/libx264 "Max quality" (CRF), incl. 10-bit/HDR | The archival path hardware can't do |
| Fast path | Stream-copy remux (no re-encode) when only the container changes | ZenConverter's "always re-encode" gap |
| Compress preset | No-op gate: refuse if source already below target bitrate floor | LightCompressor/Telegram lesson — never recompress garbage silently |

## v1 scope (unchanged in outline, sharpened by research)

Video convert/compress (HW-first + SW quality + remux), audio convert/extract, trim (re-encode and copy modes), share/open-with intents, capability detector with a visible "Your hardware" page (c2.mtk.* components, supported profiles/sizes), queue with per-item settings. GIF/images/subtitles/merging are v1.1+ on the same scaffold.

## Risks on the record

1. **Fork bus factor** — ffmpegkit-maintained is one small org with a Pro upsell. Mitigated by vendoring + wrapper isolation; fallback fork exists (maitrungduc1410, LGPL-only).
2. **FFmpeg MediaCodec edge cases** — drain/flush quirks are documented (li-yifei's patches); our probe-encode + ladder design exists precisely for this. On-device testing on the 15T Pro is the real verification — there is no emulator substitute (emulators have no working MediaCodec encoders).
3. **Scope creep** — ZenConverter slid into Office/PDF/fonts. Our scope fence: media files only.
4. **No copying from unlicensed repos** — silentlexx and li-yifei are design references only; our code is written clean.

## Research index
- `research/01-ffmpeg-gui-apps.md` — FFmpeg GUI converters (9 deep + long tail)
- `research/02-mediacodec-native.md` — MediaCodec libraries/apps, detection patterns, encoder quirks
- `research/03-adjacent-and-long-tail.md` — toolboxes (ImageToolbox, Seal, YTDLnis…), UX patterns
- `research/04-embedding-ffmpeg-2026.md` — FFmpegKit forks, licensing matrix, binary route, sizes
