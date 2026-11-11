# Local development only. The deployment environment can supply its own key/ring.
function Get-SmaRequestFingerprintKey {
    param([string]$KeyFile = (Join-Path $PSScriptRoot '../../data/keys/request-fingerprint.key'))
    if ($env:SMA_SECURITY_REQUESTS_FINGERPRINT_KEY) {
        $smaKey = $env:SMA_SECURITY_REQUESTS_FINGERPRINT_KEY
    }
    else {
        if ($env:SMA_SECURITY_REQUESTS_FINGERPRINT_KEY_ID -and $env:SMA_SECURITY_REQUESTS_FINGERPRINT_KEY_ID -ne 'v1') {
            throw 'A rotated request fingerprint key must be supplied explicitly with its retained old keys.'
        }
        $smaKeyPath = [IO.Path]::GetFullPath($KeyFile)
        if (!(Test-Path -LiteralPath $smaKeyPath)) {
            [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($smaKeyPath)) | Out-Null
            $smaStream = $null
            try {
                $smaStream = [IO.File]::Open($smaKeyPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read)
                if ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT) {
                    $smaAcl = Get-Acl -LiteralPath $smaKeyPath
                    $smaAcl.SetAccessRuleProtection($true, $false)
                    $smaRule = New-Object System.Security.AccessControl.FileSystemAccessRule([Security.Principal.WindowsIdentity]::GetCurrent().User, 'FullControl', 'Allow')
                    $smaAcl.SetAccessRule($smaRule)
                    Set-Acl -LiteralPath $smaKeyPath -AclObject $smaAcl
                }
                else { [IO.File]::SetUnixFileMode($smaKeyPath, [IO.UnixFileMode]::UserRead -bor [IO.UnixFileMode]::UserWrite) }
                $smaBytes = New-Object byte[] 32
                $smaRandom = [Security.Cryptography.RandomNumberGenerator]::Create()
                try { $smaRandom.GetBytes($smaBytes) } finally { $smaRandom.Dispose() }
                $smaEncoded = [Text.Encoding]::ASCII.GetBytes([Convert]::ToBase64String($smaBytes))
                $smaStream.Write($smaEncoded, 0, $smaEncoded.Length)
                $smaStream.Flush($true)
                [Array]::Clear($smaBytes, 0, $smaBytes.Length)
                [Array]::Clear($smaEncoded, 0, $smaEncoded.Length)
            }
            finally { if ($smaStream) { $smaStream.Dispose() } }
        }
        $smaKey = [IO.File]::ReadAllText($smaKeyPath).Trim()
    }
    try { if ([Convert]::FromBase64String($smaKey).Length -ne 32) { throw 'Invalid key length' } }
    catch { throw 'The request fingerprint key must be a Base64 encoded 32-byte value. The existing file was not overwritten.' }
    return $smaKey
}
