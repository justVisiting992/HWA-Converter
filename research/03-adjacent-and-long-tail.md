# Adjacent media toolboxes + long-tail sweep — research findings

(Research pass 3 of 4, completed 2026-11-25. Verified via GitHub API unless marked *(unverified)*.)

## Substantial apps

### ImageToolbox — T8RIN
- https://github.com/T8RIN/ImageToolbox · Apache-2.0 · ★15,768 · last push 2026-11-25 (daily activity). Kotlin + Compose, single-activity multi-module. Our closest living relative — for images (native APIs, no FFmpeg).
- Dozens of tools; **feature organization is the key lesson:** searchable categorized tool list as home screen + favorites/bookmarks + tool chaining; every tool screen shares one scaffold (pick → options → preview → save). Batch is a first-class mode of every tool. SAF done right: default output folder + **long-press Save for a one-time folder override**. Free, donations via crypto; FOSS vs Play builds with a published difference table.
- **Steal:** searchable categorized home + favorites; long-press-save override; batch-as-mode; consistent per-tool scaffold. **Avoid:** the sprawl (100+ AI models, enormous settings, UI churn).

### Seal — JunkFood02
- https://github.com/JunkFood02/Seal · GPL-3.0 · ★31,019 · last push 2026-11-25. Kotlin + Compose, Material 3 dynamic color. yt-dlp front-end; conversion = format selection + FFmpeg post-processing.
- Home is ruthlessly narrow: one URL field, one action; complexity deferred to a post-probe format-selection sheet (real sizes/codecs/resolutions). Saved **custom command templates** as the power-user escape hatch. No ads/IAP.
- **Steal:** one-input-one-action front door; probe-first concrete options sheet; command templates tier. **Avoid:** download-centric IA; GPL code copying.

### ZenConverter — Jasonzhu1207 (also covered in pass 1)
- AGPL-3.0 · ★1,995 · very active. **Mixed-type queue with per-item settings** — best queue design in the sweep. Tool list grouped by media type; import via share sheet / open-with / folder. Scope creeps into Office/PDF/fonts — **avoid that creep**; "always re-encode" (no remux fast path, no HW) is our opening.

### YTDLnis — deniscerri
- https://github.com/deniscerri/ytdlnis · GPL-3.0 · ★11,312 · last push 2026-11-25. Kotlin + Compose. yt-dlp downloader; relevant for job management.
- Most sophisticated queue found: scheduling by date/time, simultaneous jobs, **bulk edit** (select many → change format/path/template for all), .txt batch input, watch-sources, **filename templates with live preview**, command templates with syntax highlighting + backup/share, incognito mode, full backup/restore.
- **Steal:** bulk-edit of queued items; live-preview filename templates; backup/restore of settings+templates. **Avoid:** its density — our default surface stays Seal-simple, depth one layer down.

### FFShare — caydey (also in pass 1)
- GPL-3.0 · ★1,148. Share-flow compressor (FFmpeg 8.1.2 via ffmpeg-kit-next per F-Droid changelog). No home screen; settings-as-presets; custom FFmpeg command in settings. **Steal the entry point + settings-as-presets pattern.**

### Open Video Editor — devhyper
- https://github.com/devhyper/open-video-editor · GPL-3.0 · ★747 · active. Kotlin + Compose + **Media3 Transformer** export (MediaCodec HW encode). Proof the Compose + Transformer combo ships; study for export-progress UX. Don't import timeline ambitions into v1.

### Video Transcoder — brarcher (brief)
- GPL-3.0 · ★589 · dormant 2024-03. Museum piece structurally; **steal its restraint** (one screen, four knobs).

### Simple Audio Converter — TechnicJelle
- MPL-2.0 · ★36 · active, F-Droid. Dart/Flutter + FFmpeg, audio only. Deliberate minimalism ("if you want knobs, look elsewhere") — a legitimate "Simple mode" tier. Its 42 MB APK (bundled FFmpeg per ABI) is a packaging warning.

### Unitto — sadellie (brief, UX reference only)
- GPL-3.0 · ★751 · Kotlin + Compose calculator/unit converter. Category catalogue behind search + favorites — borrow the organization for from→to pairs.

### Transcoder — deepmedia (library, brief)
- Apache-2.0 · ★867 · slowed (2024-11). Canonical MediaCodec library (decoder → GL surface → encoder). Steal the GL-surface architecture pattern as reference; don't depend on it (see pass 2 for detail).

## Long tail (verified via API unless noted)
| Name | Repo | What | Alive? |
|---|---|---|---|
| EzConvert | XiaoHao560/EzConvert | FFmpeg(Kit) video/audio converter, Java, GPL-3.0, ★61 | Yes |
| FFmpeg GUI | marcomorosi06/FFmpegGUI | Privacy-first FFmpeg editor, Compose M3 Expressive, EUPL-1.2, ★167, alpha | Yes |
| snapConverter | caork/snapConverter | **Hardware-first**: MediaCodec + GL surface pipeline, Kotlin, Apache-2.0, ★0 — closest thesis to ours, tiny/new | Yes |
| Sigha (صيغة) | ahmadmhmdsy/sigha | Offline converter, 48 formats, Compose + FFmpeg, Apache-2.0, RTL/Arabic-first | Yes |
| mill | SimoesCTT/mill | Batch converter, 18 presets, watch folder, Kotlin, license unclear | Yes |
| LibreMediaConverter | JMR-dev/LibreMediaConverter | On-device FOSS converter, Kotlin, MIT, ★3 | Yes |
| FormatFactory (Android) | Starsky16/FormatFactory | Flutter/M3, batch task queue + niche music decryptors, GPL-3.0 | Yes |
| WebMChan | jpeg91/WebMChan | WebM converter with **target-size smart encoding**, Kotlin | Yes |
| FFmpegMediaEncoder | rise-crystal/FFmpegMediaEncoder | FFmpegKit + Compose transcoder, ★1 | Yes |
| offline_video_compressor | XataBq/offline_video_compressor | Compose + Media3 + Hilt compressor — clean-architecture sample | Yes |
| ConvertIt | nasahackerhq/Convertit | Ad-free audio converter, Apache-2.0 | Stale (2024) |
| Phiola | stsaz/phiola (F-Droid) | C audio player/recorder/converter, minimal-footprint | On F-Droid |
| SponsorCut / TS Converter / PhotoChiotte / Create PDF / Exif Thumbnail Adder | F-Droid | Niche one-job FFmpeg/toolbox apps *(details unverified)* | On F-Droid |
| Timbre | was naman14/Timbre | **Repo 404 — gone** | Dead |

Also: RxFFmpeg (★4,752, stale 2023) and Mp4Composer-android (★983, stale 2020) — pre-Transformer-era libraries, historical references only. Desktop GUIs (Videomass, MystiQ) useful only for preset taxonomy.

## Summary — UX organization patterns for a many-tool converter
1. **Searchable categorized tool home + favorites** (ImageToolbox, Unitto) — the only pattern proven at 30+ tools. Home = search + favorites row + category sections; one shared scaffold per tool. *Our home screen.*
2. **Pipeline job model** (ZenConverter): Pick → Configure → Queue → Service → Engine; mixed queue with per-item settings + bulk-edit (YTDLnis).
3. **Progressive disclosure, dumb-simple front door** (Seal, Simple Audio Converter): one input + one action; concrete options (real codecs/sizes) appear after probing, as a sheet.
4. **Templates/presets as the power tier** (Seal, YTDLnis, FFShare): presets for everyone + saved custom-command escape hatch. Nobody successful leads with raw FFmpeg flags.
5. **Intent-first entry points** (FFShare, ZenConverter): share sheet / open-with capture the highest-frequency job ("shrink before sending").

Cross-cutting: SAF with one-time folder override beats all-files-access and fixed Downloads; backup/restore of settings+templates is cheap loyalty; and the maintenance graveyard says **the engine wrapper layer is where these apps die** — argues for our dual-path design (hardware path + FFmpeg) over betting on a single wrapper.
