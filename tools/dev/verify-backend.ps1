# Backend verification used after every feature (docs/development-progress.md, section 2).
# Loads the current user environment (MVND_HOME, JAVA_TOOL_OPTIONS, ...) so it also works from older shells,
# runs "mvnd verify" (or "mvnw verify" with -Standard) and prints a short summary. Full log: backend/target/verify.log
param([switch]$Standard, [string[]]$MavenArgs = @(), [string]$LogPath)
$ErrorActionPreference = 'Stop'
foreach ($name in 'JAVA21_HOME', 'MVND_HOME', 'JAVA_TOOL_OPTIONS') {
    $value = [Environment]::GetEnvironmentVariable($name, 'User')
    if ($value) { Set-Item -Path "env:$name" -Value $value }
}
$backend = Join-Path $PSScriptRoot '..\..\backend'
Push-Location $backend
try {
    New-Item -ItemType Directory -Force target | Out-Null
    $log = if ($LogPath) { [IO.Path]::GetFullPath($LogPath) } else { Join-Path (Resolve-Path target) 'verify.log' }
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($log)) | Out-Null
    if ($Standard) {
        $env:JAVA_HOME = $env:JAVA21_HOME
        & .\mvnw.cmd -B -ntp -f ..\pom.xml verify @MavenArgs *> $log
    }
    else {
        & (Join-Path $env:MVND_HOME 'bin\mvnd.cmd') -B -T 2 -f ..\pom.xml verify @MavenArgs *> $log
    }
    $code = $LASTEXITCODE
    Select-String -Path $log -CaseSensitive -Pattern 'Tests run:.*in com\.|Tests run: \d+, Failures: \d+, Errors: \d+, Skipped: \d+$|BUILD (SUCCESS|FAILURE)|\[ERROR\] (?!\s*$)|FAILURE!' |
        Select-Object -First 60 | ForEach-Object {
            $line = $_.Line -replace '^\[INFO\] ', ''
            if ($line.Length -gt 350) { $line.Substring(0, 350) + ' ... (see full log)' } else { $line }
        }
    "exit=$code log=$log"
    exit $code
}
finally {
    Pop-Location
}

