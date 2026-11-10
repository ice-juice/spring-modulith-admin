param()
$ErrorActionPreference = 'Stop'
Push-Location (Join-Path $PSScriptRoot '..\..\frontend')
try {
    foreach ($step in @(@('exec', 'eslint', '.'), @('run', 'build'), @('run', 'test:unit'), @('run', 'i18n:check'))) {
        & npm @step
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
}
finally { Pop-Location }
