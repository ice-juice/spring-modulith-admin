# Per-user developer toolchain environment setup (no admin required).
param(
    [string]$JavaHome,
    [hashtable]$ExtraVars = @{},
    [string[]]$PathEntries = @()
)
$ErrorActionPreference = 'Stop'
$key = 'HKCU:\Environment'
$backupDir = Join-Path $PSScriptRoot 'logs'
New-Item -ItemType Directory -Force $backupDir | Out-Null
$item = Get-Item $key
$oldPath = $item.GetValue('Path', '', 'DoNotExpandEnvironmentNames')
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
"Path=$oldPath" + [Environment]::NewLine + "JAVA_HOME=" + $item.GetValue('JAVA_HOME', '') | Set-Content -Encoding UTF8 (Join-Path $backupDir "user-env-backup-$stamp.txt")

if ($JavaHome) { Set-ItemProperty -Path $key -Name JAVA_HOME -Value $JavaHome -Type String }
foreach ($k in $ExtraVars.Keys) { Set-ItemProperty -Path $key -Name $k -Value $ExtraVars[$k] -Type String }

$parts = @($oldPath -split ';' | Where-Object { $_ -and $_.Trim() })
$prepend = @()
foreach ($e in $PathEntries) { if ($parts -notcontains $e) { $prepend += $e } }
$newPath = (@($prepend) + $parts) -join ';'
Set-ItemProperty -Path $key -Name Path -Value $newPath -Type ExpandString

$sig = '[DllImport("user32.dll", SetLastError=true, CharSet=CharSet.Auto)] public static extern IntPtr SendMessageTimeout(IntPtr hWnd, uint Msg, UIntPtr wParam, string lParam, uint fuFlags, uint uTimeout, out UIntPtr lpdwResult);'
$t = Add-Type -MemberDefinition $sig -Name WinEnv -Namespace DevSetup -PassThru
$r = [UIntPtr]::Zero
[void]$t::SendMessageTimeout([IntPtr]0xffff, 0x1A, [UIntPtr]::Zero, 'Environment', 2, 5000, [ref]$r)
"JAVA_HOME=" + (Get-Item $key).GetValue('JAVA_HOME', '')
"Path(raw)=" + (Get-Item $key).GetValue('Path', '', 'DoNotExpandEnvironmentNames')

