# MediaCodec-native Android transcoding: libraries & apps — research findings

(Research pass 2 of 4, completed 2026-11-25. Verified via GitHub API unless marked *[unverified]*.)

## 1. Transcoder — deepmedia (ex-natario1)
- https://github.com/deepmedia/Transcoder · Apache-2.0 · 867★ · last push 2024-11-05 (slow cadence; production use at ShareChat).
- Pipeline: MediaExtractor → MediaCodec decoder → OpenGL pass (own EGL core; crop/rotate/scale/speed) → MediaCodec encoder → own MP4 writer. Encoders shared across concatenated segments because MediaCodec mutates timestamps internally and per-segment instances produced non-monotonic timestamps that crashed the muxer (documented in source).
- Detection: weakest part — enumerates encoders only to log them; `createEncoderByType` and configure; philosophy "try and let the codec fail." Validators can skip transcoding (source already small enough).
- Fallback: none automatic; `onTranscodeFailed(Throwable)`. Progress: listener, progress as Double 0..1, cancellable future.
- **Steal:** fluent builder, per-track strategies (passthrough/remove/re-encode), validators. **Avoid:** configure-and-pray codec selection — FFmpeg gives worse error visibility than a CodecException, so we must pre-validate ourselves.

## 2. LiTr — LinkedIn
- https://github.com/linkedin/LiTr · BSD-2 · 662★ · last push 2025-11-19 — most actively maintained library here.
- Pipeline: 5-stage interfaces `MediaSource → Decoder → Renderer → Encoder → MediaSink`, MediaCodec defaults, GL renderer with filter chain, passthrough components for copy tracks. Async callbacks, queued by requestId.
- Detection: **best in class.** `CodecUtils` per-mime profile rank maps (AVC Baseline→High444, HEVC Main→Main10HDR10Plus, VP9, AV1) gated by SDK, picks best profile/level the device encoder advertises via MediaCodecList + getCapabilitiesForType. Typed TrackTranscoderExceptions classify failures. No silent reconfiguration — fails typed, caller decides fallback.
- **Steal:** the capability-detection design — profile ranking + capability-driven format construction in front of FFmpeg; component separation (passthrough vs re-encode per track).

## 3. Media3 Transformer — Google (androidx/media)
- https://github.com/androidx/media · Apache-2.0 · 3,093★ (monorepo) · pushed 2026-11-25, release 1.11.1. Fully active.
- Architecture: Media3 extractors → MediaCodec decoder → GL effects → MediaCodec encoder → Media3 muxer. One Transformer = one export at a time. Google maintains **CodecDbLite**, an embedded per-device database of tested encoder settings.
- Detection/fallback — **the reference implementation.** DefaultEncoderFactory: resolution fallback ON by default (retry supported size, incl. aligning W/H to multiples of 2 or 16 "as often required by hardware encoders"); separate format-fallback toggle; `isVideoFormatSupported` pre-check; CodecDbLite recommendations.
- Limitations: **output always MP4**; poll-based progress (~500 ms) with explicit states incl. "unavailable"; cancel frees HW codecs promptly (docs stress it).
- MediaTek evidence: androidx/media issue #3347 — `c2.mtk.hevc.decoder` (MT8696) delivers ~30% of frames 31–131 ms late on certain x265 HEVC. Decoder-side, but proof MTK-specific timing behavior exists in Google's own stack.
- **Steal the fallback policy wholesale:** try requested → align dims (÷16/÷2) retry → reduce resolution/bitrate → fail, **reporting which fallback was applied**. Plus poll-progress with an "unavailable" state, and a small per-device settings DB seeded with Dimensity entries.

## 4. Mp4Composer-android — MasayukiSuda
- https://github.com/MasayukiSuda/Mp4Composer-android · MIT · 983★ · last push 2020-11-25 — unmaintained.
- ypresto descendant + GL filter layer (30+ filters). Detection: essentially none (fixed AVC/AAC).
- **Avoid as dependency; steal only the filter-chain concept.** Note: FFmpeg mediacodec can't easily do GL-in-the-middle; filters in HW mode are the native stack's turf.

## 5. android-transcoder — ypresto (the ancestor)
- https://github.com/ypresto/android-transcoder · Apache-2.0 · 854★ · dormant since 2022-12-20.
- Decoder output surface = encoder input surface directly (no GL). Fixed safe presets only; detection deliberately conservative (Baseline, 16:9, throw early otherwise).
- Lesson: the two extremes (reject-anything vs try-anything) both lost; Media3's degrade-gracefully middle won.

## 6. LightCompressor — AbedElazizShe (+ fork)
- https://github.com/AbedElazizShe/LightCompressor · Apache-2.0 · 585★ · archived (2024-08-17). Fork: davotoula/LightCompressor-enhanced, pushed 2026-11-23.
- Core is a Kotlin port of **Telegram's** video converter (ypresto lineage) with own MP4 builder (fast-start output).
- Detection: `CompressorUtils` queries MediaCodecList, picks highest advertised AVC profile (High > Main > Baseline), forces BITRATE_MODE_CBR + COLOR_FormatSurface, special-cases Qualcomm (`qti.avc`) by name, and has a **minimum-bitrate no-op gate** (default 2 Mbps floor — refuses generation-loss recompression).
- **Steal:** profile-negotiation pattern; the no-op gate for our Compress preset. Codebase itself: reference only.

## 7–8. VideoCompressor (fishwjy) & SiliCompressor — AVOID
- fishwjy/VideoCompressor: 947★, dead 2021, fixed presets, no detection. SiliCompressor: 1,394★, dead 2022, 128 open issues, blocking API. Stars ≠ maintenance.

## Apps on this stack
- **Telegram for Android** — most battle-tested MediaCodec compression pipeline in existence; LightCompressor is its extract.
- LightCompressor-enhanced (LCe) — maintained fork with an actual app. Squish-Android — new Compose/MediaCodec compressor, 0★, *unverified beyond description*.

## Category summary — detection patterns (ranked) & encoder quirks

**Detection patterns to copy:**
1. Pre-flight via MediaCodecList + getCapabilitiesForType (profiles, size/bitrate ranges, color formats) BEFORE configuring (LiTr, LightCompressor). Our app does this in Kotlin, then composes the FFmpeg command — FFmpeg will not negotiate.
2. Profile ranking tables intersected with device advertisements (LiTr).
3. Degradation ladder with reporting (Media3) — never silently change settings; surface the applied fallback.
4. Per-device settings DB (CodecDbLite) — seed with Dimensity data from the 15T Pro.
5. Validators / no-op gates (Transcoder, LightCompressor) — skip when source already meets target.
6. Vendor-name awareness: `c2.mtk.*` / legacy `OMX.MTK.VIDEO.ENCODER.*`, `c2.qti.*`, `c2.exynos.*`, `c2.android.*` (software). Expect MTK special-casing by name.

**Encoder quirks our FFmpeg *_mediacodec path must handle:**
- **Dimension alignment is the #1 configure-killer** — HW encoders commonly require W/H multiples of 16 (some 2/32). Pre-align in preset math; on failure retry 16-aligned then 2-aligned.
- **Platform guarantees < silicon:** CDD mandates HEVC encode only at Main Profile L3 ≤512×512; 1080p HEVC encode never platform-guaranteed (9400+ has it — detection must confirm). AV1 HW encode effectively absent on MediaTek — don't offer `av1_mediacodec` there unchecked.
- **No CRF on HW encoders** — bitrate modes only (CQ often unsupported). HW presets must be bitrate-based (resolution×fps tables); UI must not promise quality mode in HW.
- **Input 8-bit yuv420p/nv12 only** in FFmpeg mediacodec path — 10-bit/HDR down-converts; HDR belongs to software mode.
- **MTK buffer-allocation quirks** (`requires-allocate-on-input/output-ports` on c2.mtk.*) — surface input is the safer path.
- **MTK decoder timing** (androidx #3347): systematic A/V drift on MTK HEVC decode → suspect the decoder, not timestamps.
- **MediaCodec mutates timestamps internally** — mid-stream encoder restarts are where muxer breakage resurfaces.
- **Codec instances are scarce** — HW jobs strictly sequential in the queue; always release on cancel/failure.
- **Failure opacity through FFmpeg** (dead process + log line vs typed CodecException) — compensate with pre-flight checks, a tiny probe encode before long jobs on untested combos, and a retry ladder keyed on FFmpeg exit/log signatures.

**Bottom line:** survivors converge on *query capabilities → rank profiles → configure conservatively → degrade in steps → report what happened*. That Kotlin layer in front of FFmpeg is the highest-value steal in this category.
