# SPDX-License-Identifier: GPL-3.0-only
# Copyright (C) 2026 aiingjie
[CmdletBinding()]
param(
    [ValidateSet('Debug', 'Release', 'Both')][string]$Variant = 'Debug',
    [string]$JdkHome = '',
    [string]$AndroidSdk = '',
    [string]$GradleHome = '',
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$c17Project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$c17Utf8 = [Text.UTF8Encoding]::new($false)

if (-not $JdkHome) {
    if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
        $JdkHome = $env:JAVA_HOME
    } else {
        $c17JdkRoot = Join-Path $env:ProgramFiles 'Eclipse Adoptium'
        $c17InstalledJdk = Get-ChildItem -LiteralPath $c17JdkRoot -Directory -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -like 'jdk-17*' } | Sort-Object Name -Descending | Select-Object -First 1
        if ($c17InstalledJdk) { $JdkHome = $c17InstalledJdk.FullName }
    }
}
if (-not $JdkHome -or -not (Test-Path -LiteralPath (Join-Path $JdkHome 'bin\javac.exe'))) {
    throw '没有找到 JDK 17，请设置 JAVA_HOME 或传入 -JdkHome。'
}

if (-not $AndroidSdk) {
    if ($env:ANDROID_SDK_ROOT) { $AndroidSdk = $env:ANDROID_SDK_ROOT }
    elseif ($env:ANDROID_HOME) { $AndroidSdk = $env:ANDROID_HOME }
    elseif ($env:LOCALAPPDATA) {
        $c17BundledSdk = Join-Path $env:LOCALAPPDATA 'CodexAndroidTools\sdk'
        if (Test-Path -LiteralPath $c17BundledSdk) { $AndroidSdk = $c17BundledSdk }
    }
}
if ($AndroidSdk) {
    $c17SdkResolved = [IO.Path]::GetFullPath($AndroidSdk).Replace('\', '/')
    [IO.File]::WriteAllText((Join-Path $c17Project 'local.properties'),
            "sdk.dir=$c17SdkResolved`n", $c17Utf8)
}
if (-not (Test-Path -LiteralPath (Join-Path $c17Project 'local.properties'))) {
    throw '没有 Android SDK 路径，请传入 -AndroidSdk 或创建 local.properties。'
}

if (-not $GradleHome -and $env:C17_GRADLE_HOME) { $GradleHome = $env:C17_GRADLE_HOME }
if (-not $GradleHome -and $env:LOCALAPPDATA) {
    $c17BundledGradle = Join-Path $env:LOCALAPPDATA 'CodexAndroidTools\gradle-9.6.0'
    if (Test-Path -LiteralPath (Join-Path $c17BundledGradle 'bin\gradle.bat')) { $GradleHome = $c17BundledGradle }
}
$c17Gradle = if ($GradleHome) { Join-Path $GradleHome 'bin\gradle.bat' } else { Join-Path $c17Project 'gradlew.bat' }
if (-not (Test-Path -LiteralPath $c17Gradle)) { throw 'Gradle 入口不存在，请检查 -GradleHome。' }
$c17Arguments = @('-p', $c17Project, "-Dorg.gradle.java.home=$JdkHome", '--no-daemon', '--console=plain')
# Java/Gradle does not automatically consume the proxy environment used by other local tools.
foreach ($c17ProxyKind in @('http', 'https')) {
    $c17ProxyAddress = [Environment]::GetEnvironmentVariable($c17ProxyKind.ToUpperInvariant() + '_PROXY')
    if ($c17ProxyAddress) {
        $c17ProxyUri = [Uri]$c17ProxyAddress
        if ($c17ProxyUri.Host -and $c17ProxyUri.Port -gt 0 -and -not $c17ProxyUri.UserInfo) {
            $c17Arguments += "-D$c17ProxyKind.proxyHost=$($c17ProxyUri.Host)"
            $c17Arguments += "-D$c17ProxyKind.proxyPort=$($c17ProxyUri.Port)"
        }
    }
}
if ($Offline) { $c17Arguments += '--offline' }
if ($Variant -in @('Debug', 'Both')) { $c17Arguments += ':app:assembleDebug' }
if ($Variant -in @('Release', 'Both')) { $c17Arguments += ':app:assembleRelease' }

$c17PreviousJavaHome = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $JdkHome
    & $c17Gradle @c17Arguments
    if ($LASTEXITCODE -ne 0) { throw "Compose 构建失败（退出码 $LASTEXITCODE）。" }
} finally {
    $env:JAVA_HOME = $c17PreviousJavaHome
}
Write-Output '构建完成，APK 位于 app/build/outputs/apk/ 对应目录。'
