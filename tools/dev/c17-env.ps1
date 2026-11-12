# Shared paths for the C17 development scripts (dot-source it). Nothing here is machine specific: every path comes from an
# environment variable first, then from the usual install locations. Set these on a new machine when they are elsewhere:
#   C17_SDK       Android SDK with platforms/android-37.0 and build-tools/36.0.0
#   C17_JDK       JDK 17 home
#   C17_GRADLE    gradle.bat of Gradle 9.6 (optional; the project's gradlew.bat is used when it is not set)
#   C17_OFFLINE   1 = pass --offline to Gradle (only once every dependency is cached)
#   C17_KEYSTORE, C17_STORE_PASSWORD, C17_KEY_PASSWORD, C17_KEY_ALIAS   signing (or .local-private/signing.properties)
$ErrorActionPreference = 'Continue'
$C17Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$C17Codex = "$env:LOCALAPPDATA/Packages/OpenAI.Codex_2p2nqsd0c76g0/LocalCache/Local/CodexAndroidTools"

function C17-FirstExisting([string[]]$candidates) {
    foreach ($candidate in $candidates) { if ($candidate -and (Test-Path -LiteralPath $candidate)) { return $candidate } }
    return $null
}

$C17Sdk = C17-FirstExisting @($env:C17_SDK, $env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, "$env:LOCALAPPDATA/Android/Sdk", "$C17Codex/sdk")
$C17Jdk = C17-FirstExisting @($env:C17_JDK, $env:JAVA_HOME, 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot')
$C17Gradle = C17-FirstExisting @($env:C17_GRADLE, "$C17Codex/gradle-9.6.0/bin/gradle.bat")
if (-not $C17Gradle) { $C17Gradle = Join-Path $C17Root 'gradlew.bat' }
if (-not $C17Sdk) { throw 'Android SDK not found: set C17_SDK' }
if (-not $C17Jdk) { throw 'JDK 17 not found: set C17_JDK' }
$C17Sdk = $C17Sdk.Replace('\', '/')
$env:JAVA_HOME = $C17Jdk
$C17JdkBin = Join-Path $C17Jdk 'bin'
$C17AndroidJar = "$C17Sdk/platforms/android-37.0/android.jar"
$C17BuildTools = "$C17Sdk/build-tools/36.0.0"
$C17Out = Join-Path $C17Root 'build/c17-dev'
New-Item -ItemType Directory -Force -Path $C17Out | Out-Null
# Signing uses explicitly supplied environment variables or the ignored local
# signing.properties file; never infer a key from another checkout.
$C17GradleFlags = @('-Pc17LocalPuiAssets=false', '--console=plain')
if ($env:C17_OFFLINE -eq '1') { $C17GradleFlags += '--offline' }

# Gradle reads the SDK from local.properties: write it for the run and always put the original back.
function C17-WithSdk([scriptblock]$body) {
    $props = Join-Path $C17Root 'local.properties'
    $backup = $null
    if (Test-Path -LiteralPath $props) { $backup = [System.IO.File]::ReadAllText($props) }
    try {
        $sdkProperty = $C17Sdk.Replace(':', '\:')
        [System.IO.File]::WriteAllText($props, "sdk.dir=$sdkProperty`n")
        & $body
    } finally {
        if ($null -ne $backup) { [System.IO.File]::WriteAllText($props, $backup) }
        else { Remove-Item -LiteralPath $props -Force -ErrorAction SilentlyContinue }
    }
}
