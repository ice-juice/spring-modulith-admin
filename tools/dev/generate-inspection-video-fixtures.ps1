param([Parameter(Mandatory=$true)][string]$FfmpegPath)
$ErrorActionPreference='Stop'
$generator=(Resolve-Path -LiteralPath $FfmpegPath).Path
$destination=Join-Path $PSScriptRoot '../../backend/src/test/resources/file-inspection'
New-Item -ItemType Directory -Force -Path $destination | Out-Null
# Synthetic 1-second 64x48 colour/sine fixtures; no downloaded or customer media.
& $generator -hide_banner -loglevel error -f lavfi -i 'color=c=blue:s=64x48:r=10:d=1' -c:v libopenh264 -an -movflags +faststart -y (Join-Path $destination 'synthetic-video.mp4')
if($LASTEXITCODE -ne 0){throw 'Video fixture generation failed'}
& $generator -hide_banner -loglevel error -f lavfi -i 'color=c=green:s=64x48:r=10:d=1' -f lavfi -i 'sine=frequency=440:sample_rate=44100:duration=1' -c:v libopenh264 -c:a aac -shortest -movflags +faststart -y (Join-Path $destination 'synthetic-audio-video.mp4')
if($LASTEXITCODE -ne 0){throw 'Audio/video fixture generation failed'}
