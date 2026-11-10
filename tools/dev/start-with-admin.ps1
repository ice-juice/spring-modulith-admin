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

$smaPassword = Read-Host 'Initial admin password (at least 12 characters)' -AsSecureString
$smaPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($smaPassword)
$smaPlain = $null
try {
    $smaPlain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($smaPointer)
    if ($smaPlain.Length -lt 12 -or [Text.Encoding]::UTF8.GetByteCount($smaPlain) -gt 72) { throw 'Password must contain at least 12 characters and at most 72 UTF-8 bytes.' }
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
    $smaEnvironment = @{ SMA_ADMIN_PASSWORD = $smaPlain }
    $smaEncoding = [Environment]::GetEnvironmentVariable('JAVA_TOOL_OPTIONS', 'User')
    if ($smaEncoding) { $smaEnvironment['JAVA_TOOL_OPTIONS'] = $smaEncoding }
    $smaChild = Start-Process -FilePath $smaJava -ArgumentList @('-jar', ('"' + $smaJar + '"')) -WorkingDirectory $smaWorkspace -WindowStyle Hidden -Environment $smaEnvironment -RedirectStandardOutput (Join-Path $smaTarget 'admin-start.log') -RedirectStandardError (Join-Path $smaTarget 'admin-start-error.log') -PassThru
    $smaEnvironment.Clear()
    "Backend started (PID $($smaChild.Id)). Open http://127.0.0.1:5173/ and sign in as admin after health is UP."
    "Health: http://127.0.0.1:8080/actuator/health; logs: backend/target/admin-start.log"
}
finally {
    $smaPlain = $null
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($smaPointer)
    $smaPassword.Dispose()
}
