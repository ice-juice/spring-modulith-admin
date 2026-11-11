param()
$ErrorActionPreference = 'Stop'
foreach ($name in 'JAVA21_HOME', 'JAVA_TOOL_OPTIONS') {
    $value = [Environment]::GetEnvironmentVariable($name, 'User')
    if ($value) { Set-Item -Path "env:$name" -Value $value }
}
$target = Join-Path $PSScriptRoot '../../backend/target'
. (Join-Path $PSScriptRoot 'load-request-key.ps1')
$env:SMA_SECURITY_REQUESTS_FINGERPRINT_KEY = Get-SmaRequestFingerprintKey
$localAuthKeys = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../data/keys/auth-security.keys'))
if (!$env:SMA_AUTH_SECURITY_KEY_FILE -and (Test-Path -LiteralPath $localAuthKeys)) {
    $env:SMA_AUTH_SECURITY_KEY_FILE = $localAuthKeys
}
$run = Join-Path $target 'run'
New-Item -ItemType Directory -Path $run -Force | Out-Null
$jar = Join-Path $run 'springboot-modulith-admin.jar'
Copy-Item -LiteralPath (Join-Path $target 'springboot-modulith-admin-0.0.1-SNAPSHOT.jar') -Destination $jar -Force
& (Join-Path $env:JAVA21_HOME 'bin/java.exe') -jar $jar '--sma.tenant.bootstrap-hosts=localhost,127.0.0.1,[::1]' '--management.endpoint.health.probes.enabled=true'
exit $LASTEXITCODE
