# Elevated step (run once as administrator): enable WSL 2 prerequisites. A reboot is required afterwards.
$ErrorActionPreference = 'Continue'
$root = $PSScriptRoot
New-Item -ItemType Directory -Force (Join-Path $root 'logs') | Out-Null
Start-Transcript -Path (Join-Path $root 'logs\elevated-enable-wsl.log') -Append | Out-Null
"[$(Get-Date -Format s)] elevated=" + ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
foreach ($feature in 'Microsoft-Windows-Subsystem-Linux', 'VirtualMachinePlatform') {
    & dism.exe /online /enable-feature /featurename:$feature /all /norestart | Select-Object -Last 3
    "[$(Get-Date -Format s)] dism $feature exit=$LASTEXITCODE (3010 = reboot required)"
}
$env:WSL_UTF8 = '1'
& wsl.exe --install --no-distribution --web-download 2>&1 | ForEach-Object { $_ }
"[$(Get-Date -Format s)] wsl --install --no-distribution exit=$LASTEXITCODE"
"[$(Get-Date -Format s)] done. Please reboot Windows to finish enabling WSL 2."
Stop-Transcript | Out-Null

