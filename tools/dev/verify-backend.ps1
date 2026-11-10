# Backend verification used after every feature (docs/development-progress.md, section 2).
# Loads the current user environment (MVND_HOME, JAVA_TOOL_OPTIONS, ...) so it also works from older shells,
# runs "mvnd verify" (or "mvnw verify" with -Standard) and prints a short summary. Full log: backend/target/verify.log
param([switch]$Standard, [string[]]$MavenArgs = @())
$ErrorActionPreference = 'Stop'
foreach ($name in 'JAVA21_HOME', 'MVND_HOME', 'JAVA_TOOL_OPTIONS') {
    $value = [Environment]::GetEnvironmentVariable($name, 'User')
    if ($value) { Set-Item -Path "env:$name" -Value $value }
}
$backend = Join-Path $PSScriptRoot '..\..\backend'
Push-Location $backend
try {
    New-Item -ItemType Directory -Force target | Out-Null
    $log = Join-Path (Resolve-Path target) 'verify.log'
    if ($Standard) {
        $env:JAVA_HOME = $env:JAVA21_HOME
        & .\mvnw.cmd -B -ntp verify @MavenArgs *> $log
    }
    else {
        & (Join-Path $env:MVND_HOME 'bin\mvnd.cmd') -B verify @MavenArgs *> $log
    }
    $code = $LASTEXITCODE
    Select-String -Path $log -CaseSensitive -Pattern 'Tests run:.*in com\.|Tests run: \d+, Failures: \d+, Errors: \d+, Skipped: \d+$|BUILD (SUCCESS|FAILURE)|\[ERROR\] (?!\s*$)|FAILURE!' |
        Select-Object -First 80 | ForEach-Object { $_.Line -replace '^\[INFO\] ', '' }
    "exit=$code log=$log"
    exit $code
}
finally {
    Pop-Location
}

