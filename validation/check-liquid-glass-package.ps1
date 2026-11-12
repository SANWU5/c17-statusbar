# Runs the shipped ZIP and same-build product classes against compiled release host classes.
# DEX transfer is verified; desktop execution uses JVM bytecode, not an Android/GPU emulator.
param(
    [Parameter(Mandatory=$true)][string]$PluginZip,
    [Parameter(Mandatory=$true)][string]$RuntimeJar,
    [string]$PreviousPluginZip,
    [string]$PreviousUnifiedPluginZip,
    [switch]$NoCompile,
    [string]$PreviousSettingsPluginZip
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot '../tools/dev/c17-env.ps1')
$taskPlugin=(Resolve-Path -LiteralPath $PluginZip).Path
$taskRuntime=(Resolve-Path -LiteralPath $RuntimeJar).Path
if(-not $NoCompile){
    & (Join-Path $C17Root 'tools/dev/checks.ps1') -Names 'CustomPluginAppsCheck','C17ModuleCheck'
    if($LASTEXITCODE-ne 0){throw 'Host fixture checks failed'}
}
$taskClasses=Join-Path $C17Root 'app/build/intermediates/javac/release/compileReleaseJavaWithJavac/classes'
$taskOutput=Join-Path $C17Out 'checks'
. (Join-Path $C17Root 'validation/check-runtime-dependencies.ps1')
$taskCore=Get-C17CheckCoreJar $taskOutput
$taskRhino=Get-C17CheckRhinoJar
$taskApi=Join-Path $C17Root 'tools/lib/api-101.0.0.jar'
$taskJson=Join-Path $C17Root 'validation/test-libs/json-20240303.jar'
$taskTmp=Join-Path $taskOutput 'java-tmp'
$taskArguments=@($taskPlugin,$taskRuntime)
if($PreviousPluginZip){$taskArguments+=(Resolve-Path -LiteralPath $PreviousPluginZip).Path}
if($PreviousUnifiedPluginZip){
    if(-not $PreviousPluginZip){throw 'PreviousUnifiedPluginZip requires PreviousPluginZip'}
    $taskArguments+=(Resolve-Path -LiteralPath $PreviousUnifiedPluginZip).Path
}
if($PreviousSettingsPluginZip){
    if(-not $PreviousPluginZip -or -not $PreviousUnifiedPluginZip){throw 'PreviousSettingsPluginZip requires PreviousPluginZip and PreviousUnifiedPluginZip'}
    $taskArguments+=(Resolve-Path -LiteralPath $PreviousSettingsPluginZip).Path
}
& (Join-Path $C17JdkBin 'java.exe') "-Djava.io.tmpdir=$taskTmp" -Xms16m -Xmx128m -XX:+UseSerialGC `
    -classpath "$taskOutput;$taskClasses;$taskApi;$taskJson;$taskCore;$taskRhino;$C17AndroidJar" `
    dev.puitheme.LiquidGlassPluginPackageCheck @taskArguments
if($LASTEXITCODE-ne 0){throw 'Liquid Glass package runtime check failed'}
