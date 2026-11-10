param()
$ErrorActionPreference = 'Stop'
foreach ($name in 'JAVA21_HOME', 'JAVA_TOOL_OPTIONS') {
    $value = [Environment]::GetEnvironmentVariable($name, 'User')
    if ($value) { Set-Item -Path "env:$name" -Value $value }
}
$jar = Join-Path $PSScriptRoot '../../backend/target/springboot-modulith-admin-0.0.1-SNAPSHOT.jar'
& (Join-Path $env:JAVA21_HOME 'bin/java.exe') -jar $jar
exit $LASTEXITCODE
