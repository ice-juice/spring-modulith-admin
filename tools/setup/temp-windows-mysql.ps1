# Temporary portable MySQL on Windows (no admin). Interim database until WSL MySQL is available.
# Usage: temp-windows-mysql.ps1 -Action init|start|stop|status
param([ValidateSet('init', 'start', 'stop', 'status')][string]$Action = 'status')
$ErrorActionPreference = 'Stop'
$base = Join-Path $env:LOCALAPPDATA 'Programs\mysql-8.4.10-winx64'
$zip = Join-Path $PSScriptRoot 'downloads\mysql-8.4.10-winx64.zip'
# data lives next to the JBolt reference run copy (sibling of the repository); override with SMA_JBOLT_RUN_HOME
$runHome = if ($env:SMA_JBOLT_RUN_HOME) { $env:SMA_JBOLT_RUN_HOME } else { Join-Path $PSScriptRoot '..\..\..\jbolt_reference_run' }
$root = [IO.Path]::GetFullPath((Join-Path $runHome 'temp-mysql'))
$ini = Join-Path $root 'my.ini'
$data = Join-Path $root 'data'
$mysql = Join-Path $base 'bin\mysql.exe'
$mysqld = Join-Path $base 'bin\mysqld.exe'

function Test-Running { [bool](Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'" | Where-Object { $_.CommandLine -like "*$ini*" }) }

switch ($Action) {
    'init' {
        if (-not (Test-Path $base)) { Expand-Archive -Path $zip -DestinationPath (Split-Path $base) -Force }
        New-Item -ItemType Directory -Force $root | Out-Null
        $lines = @(
            '[mysqld]',
            ('basedir=' + ($base -replace '\\', '/')),
            ('datadir=' + ($data -replace '\\', '/')),
            'port=3306',
            'bind-address=127.0.0.1',
            'mysqlx=OFF',
            'character-set-server=utf8mb4',
            'collation-server=utf8mb4_general_ci',
            "default-time-zone='+08:00'",
            'sql_mode=STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION',
            'max_connections=300',
            ('log-error=' + ($root -replace '\\', '/') + '/mysqld.err'),
            '[client]',
            'default-character-set=utf8mb4'
        )
        $lines | Set-Content -Encoding ASCII $ini
        if (-not (Test-Path $data)) { & $mysqld ('--defaults-file=' + $ini) --initialize-insecure --console 2>&1 | Select-Object -Last 3 }
        "initialized: $data"
    }
    'start' {
        if (Test-Running) { 'already running'; break }
        Start-Process -FilePath $mysqld -ArgumentList ('--defaults-file="' + $ini + '"') -WindowStyle Hidden
        foreach ($i in 1..30) {
            Start-Sleep -Seconds 1
            & $mysql -uroot -proot --host=127.0.0.1 -e 'select 1' 2>$null | Out-Null
            if ($LASTEXITCODE -eq 0) { break }
            & $mysql -uroot --host=127.0.0.1 -e 'select 1' 2>$null | Out-Null
            if ($LASTEXITCODE -eq 0) { break }
        }
        "running=$(Test-Running)"
    }
    'stop' {
        Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'" | Where-Object { $_.CommandLine -like "*$ini*" } | ForEach-Object {
            & $mysql -uroot -proot --host=127.0.0.1 -e 'shutdown' 2>$null
            Start-Sleep -Seconds 3
            Stop-Process -Id $_.ProcessId -ErrorAction SilentlyContinue
        }
        'stopped'
    }
    'status' { "running=$(Test-Running)" }
}

