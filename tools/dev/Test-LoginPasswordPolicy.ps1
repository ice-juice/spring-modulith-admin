# Pure preflight helper: loading this file never starts or stops a service.
function Test-SmaLoginPassword {
    param([string]$Candidate, [string]$Username = 'admin')
    if ($Candidate -cnotmatch '\A[A-Za-z0-9]{8,12}\z' -or $Candidate -cnotmatch '[A-Za-z]' -or $Candidate -cnotmatch '[0-9]') { return $false }
    $policyRoot = Join-Path $PSScriptRoot '../../backend/src/main/resources/iam/password-policy'
    $policyWords = Join-Path $policyRoot 'weak-passwords.txt'
    $expectedDigest = (Get-Content -Raw -LiteralPath (Join-Path $policyRoot 'weak-passwords.sha256')).Trim()
    if ((Get-FileHash -LiteralPath $policyWords -Algorithm SHA256).Hash.ToLowerInvariant() -cne $expectedDigest) { throw 'Login password policy checksum mismatch.' }
    $candidateLower = $Candidate.ToLowerInvariant()
    $usernamePattern = [regex]::Escape($Username.ToLowerInvariant())
    if ($candidateLower -cmatch ('\A(?:' + $usernamePattern + '[0-9]{0,4}|[0-9]{1,4}' + $usernamePattern + ')\z')) { return $false }
    if ((Get-Content -LiteralPath $policyWords) -ccontains $candidateLower) { return $false }
    if ($candidateLower -cmatch '\A(?:([a-z])\1{2,}([0-9])\2{2,}|([0-9])\3{2,}([a-z])\4{2,})\z') { return $false }
    for ($period = 1; $period -le 4; $period++) {
        if ($candidateLower.Length % $period -eq 0 -and ($candidateLower.Substring(0, $period) * ($candidateLower.Length / $period)) -ceq $candidateLower) { return $false }
    }
    return $true
}
