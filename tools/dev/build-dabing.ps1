# Build the private customer APK from the shared latest app. Does not install, restart, push or send anything.
param()
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
# Reuse the optional ignored workstation toolchain when no explicit environment path was supplied.
if (-not $env:C17_SDK -and (Test-Path -LiteralPath (Join-Path $taskRoot '.local-toolchain/sdk'))) {
    $env:C17_SDK = Join-Path $taskRoot '.local-toolchain/sdk'
}
if (-not $env:C17_JDK) {
    $taskJdkRoot = Join-Path $taskRoot '.local-toolchain/jdk'
    if (Test-Path -LiteralPath $taskJdkRoot) {
        $taskLocalJdk = Get-ChildItem -LiteralPath $taskJdkRoot -Directory | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin/java.exe') } | Select-Object -First 1
        if ($taskLocalJdk) { $env:C17_JDK = $taskLocalJdk.FullName }
    }
}
if (-not $env:GRADLE_USER_HOME -and (Test-Path -LiteralPath (Join-Path $taskRoot '.local-toolchain/gradle-home'))) {
    $env:GRADLE_USER_HOME = Join-Path $taskRoot '.local-toolchain/gradle-home'
}
. (Join-Path $PSScriptRoot 'c17-env.ps1')
Set-Location -LiteralPath $C17Root
$taskVersion = '1.1.0'
$taskCode = 4
$taskPackage = 'dev.puitheme.dabingstack'
$taskLog = Join-Path $C17Out 'build-dabing-custom-1.1.0.log'
$taskExit = 1
C17-WithSdk {
    & $C17Gradle ':app:compileDebugJavaWithJavac' ':app:assembleRelease' ':app:lintVitalRelease' '-Pc17Dabing=true' @C17GradleFlags > $taskLog 2>&1
    $script:taskExit = $LASTEXITCODE
}
if ($taskExit -ne 0) { Get-Content -LiteralPath $taskLog -Tail 60; throw "Dabing customer build failed ($taskExit)" }

$taskClassesCount = 0
foreach ($taskVariant in @('debug', 'release')) {
    $taskTitle = $taskVariant.Substring(0, 1).ToUpper() + $taskVariant.Substring(1)
    $taskClassesDirectory = Join-Path $C17Root "app/build/intermediates/javac/$taskVariant/compile${taskTitle}JavaWithJavac/classes"
    if (-not (Test-Path -LiteralPath $taskClassesDirectory)) { throw "Java output missing: $taskVariant" }
    foreach ($taskClass in [System.IO.Directory]::GetFiles($taskClassesDirectory, '*.class', [System.IO.SearchOption]::AllDirectories)) {
        $taskBytes = [System.IO.File]::ReadAllBytes($taskClass)
        $taskMajor = ([int]$taskBytes[6]) * 256 + [int]$taskBytes[7]
        if ($taskMajor -ne 52) { throw "Not Java 8: $taskClass ($taskMajor)" }
        $taskClassesCount++
    }
}

$taskApk = Join-Path $C17Root 'app/build/outputs/apk/release/app-release.apk'
$taskSignatureLog = Join-Path $C17Out 'signature-dabing-custom-1.1.0.log'
& "$C17JdkBin/java.exe" -Xms16m -Xmx128m -XX:+UseSerialGC -jar "$C17BuildTools/lib/apksigner.jar" verify --verbose --print-certs $taskApk > $taskSignatureLog 2>&1
if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath $taskSignatureLog -Tail 20; throw 'Release signature verification failed' }
& "$C17BuildTools/zipalign.exe" -c -P 16 -v 4 $taskApk > (Join-Path $C17Out 'align-dabing-custom-1.1.0.log') 2>&1
if ($LASTEXITCODE -ne 0) { throw '16 KB APK alignment verification failed' }
$taskBadgingLog = Join-Path $C17Out 'badging-dabing-custom-1.1.0.log'
& "$C17BuildTools/aapt2.exe" dump badging $taskApk > $taskBadgingLog 2>&1
if ($LASTEXITCODE -ne 0) { throw 'APK manifest verification failed' }
$taskPackageLine = (Get-Content -LiteralPath $taskBadgingLog | Select-String -Pattern '^package:').Line
if ($taskPackageLine -notmatch "name='$([regex]::Escape($taskPackage))'" -or
    $taskPackageLine -notmatch "versionCode='$taskCode'" -or
    $taskPackageLine -notmatch "versionName='$([regex]::Escape($taskVersion))'") { throw "APK identity mismatch: $taskPackageLine" }

$taskDestination = Join-Path $C17Root "artifacts/dabing-custom-$taskVersion"
New-Item -ItemType Directory -Force -Path $taskDestination | Out-Null
$taskName = "Dabing-Custom-$taskVersion-code$taskCode.apk"
$taskOutput = Join-Path $taskDestination $taskName
Copy-Item -LiteralPath $taskApk -Destination $taskOutput -Force
$taskHash = (Get-FileHash -LiteralPath $taskOutput -Algorithm SHA256).Hash.ToLowerInvariant()
[System.IO.File]::WriteAllText((Join-Path $taskDestination 'SHA256.txt'), "$taskHash  $taskName`n", (New-Object System.Text.UTF8Encoding($false)))
Write-Output "APK: $taskOutput"
Write-Output "SIZE: $((Get-Item -LiteralPath $taskOutput).Length)"
Write-Output "SHA256: $taskHash"
Write-Output "Java 8 classes: $taskClassesCount"
Write-Output $taskPackageLine
