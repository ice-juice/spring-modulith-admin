param()
$ErrorActionPreference = 'Stop'
foreach ($name in 'JAVA21_HOME', 'JAVA_TOOL_OPTIONS') {
    $value = [Environment]::GetEnvironmentVariable($name, 'User')
    if ($value) { Set-Item -Path "env:$name" -Value $value }
}
$target = Join-Path $PSScriptRoot '../../backend/target'
$run = Join-Path $target 'run'
New-Item -ItemType Directory -Path $run -Force | Out-Null
$jar = Join-Path $run 'springboot-modulith-admin.jar'
Copy-Item -LiteralPath (Join-Path $target 'springboot-modulith-admin-0.0.1-SNAPSHOT.jar') -Destination $jar -Force
& (Join-Path $env:JAVA21_HOME 'bin/java.exe') -jar $jar
exit $LASTEXITCODE
