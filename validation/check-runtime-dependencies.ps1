# Use AndroidX's real PathParser for desktop vector drawing checks; graphics fixtures stay first on the classpath.
function Get-C17CheckRhinoJar {
    if ($env:C17_RHINO_JAR) {
        if (-not (Test-Path -LiteralPath $env:C17_RHINO_JAR -PathType Leaf)) { throw 'C17_RHINO_JAR does not exist' }
        return [System.IO.Path]::GetFullPath($env:C17_RHINO_JAR)
    }
    $taskGradleHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path ([Environment]::GetFolderPath('UserProfile')) '.gradle' }
    $taskRhinoCache = Join-Path $taskGradleHome 'caches/modules-2/files-2.1/org.mozilla/rhino/1.7.15.1'
    $taskRhinoJar = Get-ChildItem -LiteralPath $taskRhinoCache -Filter 'rhino-1.7.15.1.jar' -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $taskRhinoJar) { throw 'Build the app to cache Rhino 1.7.15.1, or set C17_RHINO_JAR' }
    return $taskRhinoJar.FullName
}

function Get-C17CheckCoreJar([string]$OutputDirectory) {
    if ($env:C17_ANDROIDX_CORE_JAR) {
        if (-not (Test-Path -LiteralPath $env:C17_ANDROIDX_CORE_JAR -PathType Leaf)) { throw 'C17_ANDROIDX_CORE_JAR does not exist' }
        return [System.IO.Path]::GetFullPath($env:C17_ANDROIDX_CORE_JAR)
    }
    $taskGradleHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path ([Environment]::GetFolderPath('UserProfile')) '.gradle' }
    $taskCoreCache = Join-Path $taskGradleHome 'caches/modules-2/files-2.1/androidx.core/core/1.18.0'
    $taskCoreAar = Get-ChildItem -LiteralPath $taskCoreCache -Filter 'core-1.18.0.aar' -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $taskCoreAar) { throw 'Build the app to cache AndroidX core 1.18.0, or set C17_ANDROIDX_CORE_JAR to its classes.jar' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $taskCoreJar = Join-Path $OutputDirectory 'androidx-core-1.18.0.jar'
    $taskCoreZip = [System.IO.Compression.ZipFile]::OpenRead($taskCoreAar.FullName)
    try {
        $taskCoreEntry = $taskCoreZip.GetEntry('classes.jar')
        if (-not $taskCoreEntry) { throw 'AndroidX core AAR is missing classes.jar' }
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($taskCoreEntry, $taskCoreJar, $true)
    } finally { $taskCoreZip.Dispose() }
    return $taskCoreJar
}
