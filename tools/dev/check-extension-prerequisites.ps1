param(
    [string]$ContainerEngine = 'docker',
    [string]$JavaImage = $env:SMA_WORKER_JAVA_IMAGE,
    [string]$ScannerImage = $env:SMA_WORKER_SCANNER_IMAGE,
    [string]$Report = 'backend/target/extension-prerequisites.json'
)
$ErrorActionPreference = 'Stop'
$checks = [Collections.Generic.List[object]]::new()
$engine = Get-Command $ContainerEngine -ErrorAction SilentlyContinue
$checks.Add(@{ check='container-engine'; passed=($null -ne $engine); detail=if($engine){$engine.Source}else{'Not installed or unavailable on PATH'} })
foreach ($entry in @(@{name='java-image';image=$JavaImage},@{name='scanner-image';image=$ScannerImage})) {
    $pinned = $entry.image -and $entry.image -match '^[a-zA-Z0-9][a-zA-Z0-9./:_-]*@sha256:[0-9a-f]{64}$'
    $present = $false
    if ($pinned -and $engine) {
        $raw = & $engine.Source image inspect $entry.image --format '{{json .RepoDigests}}' 2>$null
        if ($LASTEXITCODE -eq 0) { $digests = $raw | ConvertFrom-Json; $present = $digests -contains $entry.image }
    }
    $checks.Add(@{check=$entry.name;passed=($pinned -and $present);detail=if(-not $pinned){'Pinned image digest required'}elseif(-not $present){'Exact image is not installed'}else{'Exact local image found'}})
}
$result = @{checkedAt=[DateTimeOffset]::UtcNow.ToString('o'); checks=$checks.ToArray(); ready=(@($checks | Where-Object { -not $_.passed }).Count -eq 0); imageExecutionPerformed=$false; scanPerformed=$false}
$destination=[IO.Path]::GetFullPath((Join-Path (Get-Location) $Report))
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination)) | Out-Null
[IO.File]::WriteAllText($destination,($result | ConvertTo-Json -Depth 6))
$result | ConvertTo-Json -Depth 6
if(-not $result.ready){exit 1}
