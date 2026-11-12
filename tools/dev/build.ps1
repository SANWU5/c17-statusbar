# Release build of one round: assemble, lint, Java 8 bytecode (major 52) scan, signature, 16 KB alignment, version check, then the APK
# and SHA256.txt go to artifacts/<base>-<test build>-code<N>/.
#   powershell -Command "& tools/dev/build.ps1 -Code 102 -Version '1.8.3-beta.19'"
# The version must already be set in app/build.gradle (versionCode / versionName).
param([Parameter(Mandatory = $true)][int]$Code, [Parameter(Mandatory = $true)][string]$Version)
. (Join-Path $PSScriptRoot 'c17-env.ps1')
Set-Location -LiteralPath $C17Root
$log = Join-Path $C17Out "build-code$Code.log"
$exit = 1
C17-WithSdk {
    & $C17Gradle ':app:compileDebugJavaWithJavac' ':app:assembleRelease' ':app:lintVitalRelease' @C17GradleFlags > $log 2>&1
    $script:exit = $LASTEXITCODE
}
if ($exit -ne 0) { Get-Content -LiteralPath $log -Tail 60; Write-Output "BUILD FAILED $exit"; exit 1 }
Write-Output 'gradle build ok'

$count = 0
foreach ($variant in @('debug', 'release')) {
    $v = $variant.Substring(0, 1).ToUpper() + $variant.Substring(1)
    $dir = "$C17Root/app/build/intermediates/javac/$variant/compile${v}JavaWithJavac/classes"
    foreach ($file in [System.IO.Directory]::GetFiles($dir, '*.class', [System.IO.SearchOption]::AllDirectories)) {
        $bytes = [System.IO.File]::ReadAllBytes($file)
        $major = ([int]$bytes[6]) * 256 + [int]$bytes[7]
        if ($major -ne 52) { Write-Output "NON-JAVA8 CLASS: $file ($major)"; exit 1 }
        $count++
    }
}
Write-Output "$count classes major52"

$apk = "$C17Root/app/build/outputs/apk/release/app-release.apk"
& "$C17JdkBin/java.exe" -Xms16m -Xmx128m -XX:+UseSerialGC -jar "$C17BuildTools/lib/apksigner.jar" verify --verbose --print-certs $apk > "$C17Out/signature-code$Code.log" 2>&1
if ($LASTEXITCODE -ne 0) { Get-Content "$C17Out/signature-code$Code.log" -Tail 20; Write-Output 'SIGNATURE FAILED (is the keystore configured?)'; exit 1 }
& "$C17BuildTools/zipalign.exe" -c -P 16 -v 4 $apk > "$C17Out/align-code$Code.log" 2>&1
if ($LASTEXITCODE -ne 0) { Write-Output 'ALIGNMENT FAILED'; exit 1 }
& "$C17BuildTools/aapt2.exe" dump badging $apk > "$C17Out/badging-code$Code.log" 2>&1
$line = (Get-Content "$C17Out/badging-code$Code.log" | Select-String -Pattern '^package:').Line
if ($line -notmatch "versionCode='$Code'" -or $line -notmatch "versionName='$([regex]::Escape($Version))'") { Write-Output "VERSION MISMATCH: $line"; exit 1 }

# artifacts/1.8.3-<test build>-code102 (the folder word is built from code points to keep this file ASCII for PowerShell 5.1).
$base = ($Version -split '[-_]beta')[0]
$folder = if ($Version -match '[-_]beta') {
    "$base-" + [char]0x6D4B + [char]0x8BD5 + [char]0x7248 + "-code$Code"
} else { "$Version-code$Code" }
$release = "$C17Root/artifacts/$folder"
New-Item -ItemType Directory -Path $release -Force | Out-Null
$name = "C17-$Version-code$Code.apk"
Copy-Item -LiteralPath $apk -Destination "$release/$name" -Force
$hash = (Get-FileHash -LiteralPath "$release/$name" -Algorithm SHA256).Hash.ToLowerInvariant()
Set-Content -LiteralPath "$release/SHA256.txt" -Value "$hash  $name" -Encoding utf8
Write-Output "APK: $release/$name"
Write-Output "SIZE: $((Get-Item -LiteralPath "$release/$name").Length)"
Write-Output "SHA256: $hash"
Write-Output $line
