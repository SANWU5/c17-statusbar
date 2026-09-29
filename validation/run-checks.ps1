param(
    [Parameter(Mandatory=$true)][string]$CompiledClasses,
    [Parameter(Mandatory=$true)][string]$AndroidJar,
    [Parameter(Mandatory=$true)][string]$XposedApiJar,
    [string]$JdkBin=''
)
$ErrorActionPreference='Stop'
$c17ValidationRoot=$PSScriptRoot
$c17CheckOutput=Join-Path $c17ValidationRoot 'build-checks'
New-Item -ItemType Directory -Force -Path $c17CheckOutput | Out-Null
$c17Java=if($JdkBin){Join-Path $JdkBin 'java.exe'}else{'java'}
$c17Javac=if($JdkBin){Join-Path $JdkBin 'javac.exe'}else{'javac'}
$c17Sources=Get-ChildItem -LiteralPath (Join-Path $c17ValidationRoot 'checks-src') -Filter '*.java' -Recurse | ForEach-Object {$_.FullName}
& $c17Javac -encoding UTF-8 -classpath "$CompiledClasses;$XposedApiJar;$AndroidJar" -d $c17CheckOutput $c17Sources (Join-Path $c17ValidationRoot 'SettingsCheck.java')
if($LASTEXITCODE-ne 0){throw 'Check compilation failed'}
foreach($c17Name in @('SettingsCheck','AppearanceCheck','SceneAppearanceCheck','LayoutCheck','TimeSignalCheck','TextControlsCheck','ChargeCycleCheck','BatteryControlsCheck','BatteryAppearanceCheck','NetworkOverflowCheck','HsbColorCheck')) {
    & $c17Java -Xmx256m -classpath "$c17CheckOutput;$CompiledClasses;$XposedApiJar;$AndroidJar" "dev.puitheme.$c17Name"
    if($LASTEXITCODE-ne 0){throw "Check failed: $c17Name"}
}
