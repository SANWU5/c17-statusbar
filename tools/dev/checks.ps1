# Desktop checks: compile validation/checks-src against the compiled release classes and run them.
#   powershell -File tools/dev/checks.ps1                 every check listed in validation/run-checks.ps1
#   powershell -Command "& tools/dev/checks.ps1 -Names 'Code102Check','SpeedPositionCheck'"
# Run tools/dev/compile.ps1 first. A check that needs an Android method the stubs lack: add it to the stub under validation/checks-src.
param([string[]]$Names = @(), [switch]$NoCompile)
. (Join-Path $PSScriptRoot 'c17-env.ps1')
Set-Location -LiteralPath $C17Root
$classes = "$C17Root/app/build/intermediates/javac/release/compileReleaseJavaWithJavac/classes"
$xposed = "$C17Root/tools/lib/api-101.0.0.jar"
$json = "$C17Root/validation/test-libs/json-20240303.jar"
$out = Join-Path $C17Out 'checks'
New-Item -ItemType Directory -Force -Path $out | Out-Null
# Match validation/run-checks.ps1: Windows may expose TEMP through an 8.3 alias,
# which correctly fails the production icon repository's canonical-path check.
$taskJavaTmp=Join-Path $out 'java-tmp'
New-Item -ItemType Directory -Force -Path $taskJavaTmp | Out-Null
. (Join-Path $C17Root 'validation/check-runtime-dependencies.ps1')
$taskCoreJar = Get-C17CheckCoreJar $out
$taskRhinoJar = Get-C17CheckRhinoJar
if (-not (Test-Path -LiteralPath $classes)) { Write-Output 'FAILED: no compiled classes, run tools/dev/compile.ps1'; exit 1 }
if (-not $NoCompile) {
    $sources = Get-ChildItem -LiteralPath "$C17Root/validation/checks-src" -Filter '*.java' -Recurse | ForEach-Object { $_.FullName }
    $all = @($sources) + "$C17Root/validation/SettingsCheck.java"
    $quoted = $all | ForEach-Object { '"' + $_.Replace('\', '/') + '"' }
    [System.IO.File]::WriteAllLines("$out/check-sources.args", [string[]]$quoted, (New-Object System.Text.UTF8Encoding($false)))
    & "$C17JdkBin/javac.exe" '-J-Dfile.encoding=UTF-8' -J-Xms16m -J-Xmx256m -J-XX:+UseSerialGC -encoding UTF-8 `
        -classpath "$classes;$xposed;$json;$taskCoreJar;$taskRhinoJar;$C17AndroidJar" -d $out "@$out/check-sources.args" > "$out/javac.log" 2>&1
    if ($LASTEXITCODE -ne 0) { Get-Content "$out/javac.log" -Tail 40; Write-Output 'FAILED: check compilation'; exit 1 }
}
if ($Names.Count -eq 0) {
    $text = [System.IO.File]::ReadAllText("$C17Root/validation/run-checks.ps1", [System.Text.Encoding]::UTF8)
    $m = [regex]::Match($text, "foreach\(\`$c17Name in @\((.*?)\)\) \{")
    $Names = [regex]::Matches($m.Groups[1].Value, "'([A-Za-z0-9_]+)'") | ForEach-Object { $_.Groups[1].Value }
    $Names += 'NotificationNegativeSpacingCheck'
}
$failed = @()
foreach ($name in $Names) {
    $output = & "$C17JdkBin/java.exe" "-Djava.io.tmpdir=$taskJavaTmp" -Xms16m -Xmx128m -XX:+UseSerialGC -classpath "$out;$classes;$xposed;$json;$taskCoreJar;$taskRhinoJar;$C17AndroidJar" "dev.puitheme.$name" 2>&1
    if ($LASTEXITCODE -ne 0) { $failed += $name; Write-Output "FAILED: $name"; ($output | Select-Object -First 12) | ForEach-Object { Write-Output "    $_" } }
    elseif ($Names.Count -le 6) { Write-Output "ok $name : $($output | Select-Object -Last 1)" }
}
Write-Output "TOTAL $($Names.Count) FAILED $($failed.Count)"
if ($failed.Count -gt 0) { exit 1 }
