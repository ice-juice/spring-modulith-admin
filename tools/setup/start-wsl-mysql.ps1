# Start MySQL inside WSL and keep the distro alive in the background (no admin required).
param([string]$Distro = 'Ubuntu-24.04')
$env:WSL_UTF8 = '1'
$keepAlive = Get-CimInstance Win32_Process -Filter "Name='wsl.exe'" | Where-Object { $_.CommandLine -match 'sleep infinity' }
if (-not $keepAlive) {
    Start-Process -FilePath wsl.exe -ArgumentList @('-d', $Distro, '-u', 'root', '--exec', 'sleep', 'infinity') -WindowStyle Hidden
    Start-Sleep -Seconds 3
}
& wsl.exe -d $Distro -u root -- bash -lc 'service mysql start >/dev/null 2>&1 || systemctl start mysql; mysqladmin -uroot -proot status 2>/dev/null'

