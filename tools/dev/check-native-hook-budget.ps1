# Run after tools/dev/compile.ps1. Measures the production Java hook bridge,
# excluding fixture setup; disable desktop escape analysis for a repeatable budget.
. (Join-Path $PSScriptRoot 'c17-env.ps1')
. (Join-Path $C17Root 'validation/check-runtime-dependencies.ps1')
$taskClasses = "$C17Root/app/build/intermediates/javac/release/compileReleaseJavaWithJavac/classes"
$taskChecks = Join-Path $C17Out 'checks'
if (-not (Test-Path -LiteralPath "$taskChecks/dev/puitheme/CustomPluginRuntimeCheck.class")) {
    throw 'Compile desktop checks with tools/dev/checks.ps1 first'
}
$taskCore = Get-C17CheckCoreJar $taskChecks
$taskRhino = Get-C17CheckRhinoJar
$taskClasspath = "$taskChecks;$taskClasses;$C17Root/tools/lib/api-101.0.0.jar;$C17Root/validation/test-libs/json-20240303.jar;$taskCore;$taskRhino;$C17AndroidJar"
& "$C17JdkBin/java.exe" -XX:-DoEscapeAnalysis -Xms16m -Xmx128m -XX:+UseSerialGC -classpath $taskClasspath dev.puitheme.CustomPluginRuntimeCheck --native-budget
exit $LASTEXITCODE
