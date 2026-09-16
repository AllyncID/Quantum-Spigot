param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string[]]$Tasks = @('applyAllPatches', 'build', 'test', 'createPaperclipJar')
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $JavaHome) {
    throw 'Pass -JavaHome with a JDK 25 directory or set JAVA_HOME.'
}
$java = Join-Path $JavaHome 'bin\java.exe'
if (-not (Test-Path -LiteralPath $java)) { throw "Java executable missing: $java" }
$previousJavaHome = $env:JAVA_HOME
$previousPath = $env:Path
$previousGitConfigCount = $env:GIT_CONFIG_COUNT
$configIndex = if ($previousGitConfigCount) { [int]$previousGitConfigCount } else { 0 }
$keyName = "GIT_CONFIG_KEY_$configIndex"
$valueName = "GIT_CONFIG_VALUE_$configIndex"
$previousKey = [Environment]::GetEnvironmentVariable($keyName, 'Process')
$previousValue = [Environment]::GetEnvironmentVariable($valueName, 'Process')
try {
    $env:JAVA_HOME = $JavaHome
    $env:Path = "$JavaHome\bin;$previousPath"
    # Resource paths in 26.2 exceed Git for Windows' default path limit.
    $env:GIT_CONFIG_COUNT = [string]($configIndex + 1)
    [Environment]::SetEnvironmentVariable($keyName, 'core.longpaths', 'Process')
    [Environment]::SetEnvironmentVariable($valueName, 'true', 'Process')
    Push-Location -LiteralPath $projectRoot
    try {
        # Generate source trees before a parallel build can start compiling them.
        if ($Tasks -contains 'applyAllPatches') {
            & .\gradlew.bat applyAllPatches --console=plain "-Dorg.gradle.java.installations.paths=$JavaHome"
            if ($LASTEXITCODE -ne 0) { throw "Patch application failed with exit code $LASTEXITCODE" }
        }
        $buildTasks = @($Tasks | Where-Object { $_ -ne 'applyAllPatches' })
        if ($buildTasks.Count) {
            & .\gradlew.bat @buildTasks --console=plain "-Dorg.gradle.java.installations.paths=$JavaHome"
            if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
        }
    } finally {
        Pop-Location
    }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:Path = $previousPath
    $env:GIT_CONFIG_COUNT = $previousGitConfigCount
    [Environment]::SetEnvironmentVariable($keyName, $previousKey, 'Process')
    [Environment]::SetEnvironmentVariable($valueName, $previousValue, 'Process')
}
