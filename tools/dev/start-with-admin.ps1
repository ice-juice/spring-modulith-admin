# Run interactively: prompts locally, passes the initial password only through the child process environment.
# Existing administrator accounts are never reset by the application bootstrap.
$ErrorActionPreference = 'Stop'
$smaWorkspace = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$smaTarget = Join-Path $smaWorkspace 'backend/target'
$smaSourceJar = Join-Path $smaTarget 'springboot-modulith-admin-0.0.1-SNAPSHOT.jar'
$smaJavaHome = [Environment]::GetEnvironmentVariable('JAVA21_HOME', 'User')
if (!$smaJavaHome) { $smaJavaHome = $env:JAVA21_HOME }
$smaJava = Join-Path $smaJavaHome 'bin/java.exe'
if (!(Test-Path -LiteralPath $smaSourceJar) -or !(Test-Path -LiteralPath $smaJava)) { throw 'Build the backend first and configure JAVA21_HOME.' }

. (Join-Path $PSScriptRoot 'Test-LoginPasswordPolicy.ps1')
. (Join-Path $PSScriptRoot 'load-request-key.ps1')
$smaRequestKey = Get-SmaRequestFingerprintKey
$smaPassword = Read-Host 'Initial admin password (8-12 letters and digits; no weak passwords)' -AsSecureString
$smaPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($smaPassword)
$smaPlain = $null
try {
    $smaPlain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($smaPointer)
    if (!(Test-SmaLoginPassword -Candidate $smaPlain -Username 'admin')) { throw 'Use 8-12 letters and digits, excluding common, repeated or account-related passwords.' }
    $smaListeners = @(Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique)
    foreach ($smaPid in $smaListeners) {
        $smaProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$smaPid"
        if (!$smaProcess -or $smaProcess.CommandLine -notlike '*springboot-modulith-admin.jar*' -or !$smaProcess.CommandLine.Contains($smaWorkspace)) {
            throw 'Port 8080 belongs to another process. Stop that process manually before starting this project.'
        }
    }
    foreach ($smaPid in $smaListeners) { Stop-Process -Id $smaPid }
    $smaRun = Join-Path $smaTarget 'run'
    New-Item -ItemType Directory -Path $smaRun -Force | Out-Null
    $smaJar = Join-Path $smaRun 'springboot-modulith-admin.jar'
    Copy-Item -LiteralPath $smaSourceJar -Destination $smaJar -Force
    $smaEnvironment = @{ SMA_ADMIN_PASSWORD = $smaPlain; SMA_SECURITY_REQUESTS_FINGERPRINT_KEY = $smaRequestKey }
    $smaLocalAuthKeys = Join-Path $smaWorkspace 'data/keys/auth-security.keys'
    if (!$env:SMA_AUTH_SECURITY_KEY_FILE -and (Test-Path -LiteralPath $smaLocalAuthKeys)) {
        $smaEnvironment['SMA_AUTH_SECURITY_KEY_FILE'] = $smaLocalAuthKeys
    }
    $smaEncoding = [Environment]::GetEnvironmentVariable('JAVA_TOOL_OPTIONS', 'User')
    if ($smaEncoding) { $smaEnvironment['JAVA_TOOL_OPTIONS'] = $smaEncoding }
    $smaChild = Start-Process -FilePath $smaJava -ArgumentList @('-jar', ('"' + $smaJar + '"'), '--sma.tenant.bootstrap-hosts=localhost,127.0.0.1,[::1]', '--management.endpoint.health.probes.enabled=true') -WorkingDirectory $smaWorkspace -WindowStyle Hidden -Environment $smaEnvironment -RedirectStandardOutput (Join-Path $smaTarget 'admin-start.log') -RedirectStandardError (Join-Path $smaTarget 'admin-start-error.log') -PassThru
    $smaEnvironment.Clear()
    "Backend started (PID $($smaChild.Id)). Open http://127.0.0.1:5173/ and sign in as admin after readiness is UP."
    "Readiness: http://127.0.0.1:8080/actuator/health/readiness; logs: backend/target/admin-start.log"
}
finally {
    $smaPlain = $null
    $smaRequestKey = $null
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($smaPointer)
    $smaPassword.Dispose()
}
