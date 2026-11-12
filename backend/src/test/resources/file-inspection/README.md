# Synthetic image fixtures

These 8×6 WebP images are generated entirely from fixed pixel values by
`tools/dev/generate-inspection-webp-fixtures.py` using Pillow 12.0.0.
No external image, personal data, or third-party artwork is included.

Lossless, lossy, and alpha images exercise the pinned TwelveMonkeys decoder.
Animated WebP is a rejection fixture until a full animation decoder contract is verified.
Python/Pillow are only used to regenerate fixtures, never during runtime or Maven tests.

The two synthetic MP4 clips are one second of 64×48 colour, with an optional 440 Hz sine.
Regenerate with `tools/dev/generate-inspection-video-fixtures.ps1 -FfmpegPath <trusted executable>`.
They were generated using FFmpeg 8.1.2 from Maven Central's
`org.bytedeco:ffmpeg:8.1.2-1.5.14:windows-x86_64` artifact (SHA1 checked against the repository:
`d84fe6debb37938661264be921425c4b81136c0b`). No binary tool is checked into the repository.
Real native tests explicitly select a deployment-owned ffprobe path; they do not infer container isolation.
