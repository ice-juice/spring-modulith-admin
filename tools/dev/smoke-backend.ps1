# Live, anonymous HTTP checks; successful authenticated flows are covered by MySQL/MockMvc integration tests.
param(
    [string]$BaseUri = 'http://127.0.0.1:8080',
    [string[]]$Endpoints = @('/api/iam/users', '/api/file/files', '/api/iam/profile')
)
$ErrorActionPreference = 'Stop'
$health = Invoke-RestMethod ($BaseUri.TrimEnd('/') + '/actuator/health')
if ($health.status -ne 'UP') { throw 'Application health is not UP' }
foreach ($endpoint in $Endpoints) {
    $messages = @{}
    foreach ($locale in @('zh-CN', 'en-US')) {
        $response = Invoke-WebRequest ($BaseUri.TrimEnd('/') + $endpoint) -Headers @{ 'Accept-Language' = $locale } -SkipHttpErrorCheck
        $body = $response.Content | ConvertFrom-Json
        if ([int]$response.StatusCode -ne 401 -or $body.code -ne 401 -or $body.error -ne 'common.error.unauthorized') {
            throw "Expected localized anonymous rejection: $endpoint ($locale)"
        }
        $messages[$locale] = $body.message
    }
    if (!$messages['zh-CN'] -or $messages['zh-CN'] -eq $messages['en-US']) { throw "Missing localization: $endpoint" }
    "PASS anonymous 401 and both locales: $endpoint"
}
'PASS health UP'
