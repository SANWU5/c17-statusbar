param(
    [Parameter(Mandatory=$true)][string]$CompiledClasses,
    [Parameter(Mandatory=$true)][string]$AndroidJar,
    [Parameter(Mandatory=$true)][string]$XposedApiJar,
    [string]$JdkBin='',
    [string]$CheckOutput=''
)
$ErrorActionPreference='Stop'
$c17ValidationRoot=$PSScriptRoot
$c17CheckOutput=if($CheckOutput){[System.IO.Path]::GetFullPath($CheckOutput)}else{Join-Path $c17ValidationRoot 'build-checks'}
$c17Json=Join-Path $c17ValidationRoot 'test-libs\json-20240303.jar'
if(-not(Test-Path -LiteralPath $c17Json)){throw 'Missing desktop JSON test runtime'}
New-Item -ItemType Directory -Force -Path $c17CheckOutput | Out-Null
$c17Java=if($JdkBin){Join-Path $JdkBin 'java.exe'}else{'java'}
$c17Javac=if($JdkBin){Join-Path $JdkBin 'javac.exe'}else{'javac'}
$c17Sources=Get-ChildItem -LiteralPath (Join-Path $c17ValidationRoot 'checks-src') -Filter '*.java' -Recurse | ForEach-Object {$_.FullName}
& $c17Javac -J-Xms16m -J-Xmx256m -J-XX:+UseSerialGC -encoding UTF-8 -classpath "$CompiledClasses;$XposedApiJar;$c17Json;$AndroidJar" -d $c17CheckOutput $c17Sources (Join-Path $c17ValidationRoot 'SettingsCheck.java')
if($LASTEXITCODE-ne 0){throw 'Check compilation failed'}
foreach($c17Name in @('SettingsCheck','AppearanceCheck','SceneAppearanceCheck','LayoutCheck','TimeSignalCheck','TextControlsCheck','NativeClockMeasurementCheck','ChargeCycleCheck','BatteryControlsCheck','BatteryTextStyleCheck','BatteryAppearanceCheck','NetworkOverflowCheck','HsbColorCheck','FeatureOptionsCheck','NetworkBadgeCheck','NativeNetworkBadgeControlsCheck','NativeDataActivityCheck','SingleNetworkLabelControlsCheck','SingleMobileIconControlsCheck','NativeNetworkBadgeBindingsCheck','CarrierPanelsCheck','OverflowControlsCheck','DrawableSwitchCheck','RadioStateCheck','DataBatterySpacingCheck','NetworkIconOrderCheck','NativeStatusIconsCheck','NativeDataSourceCheck','PuiBatteryStyleCheck','CarrierPanelSettingsCheck','NumericPolicyCheck','GitHubUpdatesCheck','AppUpdateInstallerCheck','MaintenanceResetCheck','NumericInputCheck','NumericTrialCheck','TilePageEffectsCheck','ModuleDiagnosticsCheck','QsTileAppearanceCheck','QsNativeGlassFillCheck','QsMediaAppearanceCheck','NotificationClockEdgeCheck','QsTileCornersCheck','QsTileIconSizeCheck','StatusIconTransitionCheck','StatusBarNativeCopyDrawCacheCheck','QsPanelCornersCheck','NotificationClearMotionCheck','NotificationClearAppearanceCheck','ConfigTransferCheck','SettingsCatalogCheck','RuntimeHandshakeCheck','SystemUiRestartCheck','ActivationGuardPreferencesCheck','SettingsStartupLoaderCheck','SettingsFrameworkMirrorCheck','SettingsPersistenceCheck','SafeConfigCheck','NotificationIconAreaCheck','IconPackRepositoryCheck','IconPackDrawingCheck','NotificationIconOverridesCheck','PanelModeCheck','Upgrade66Check','ShadeWallpaperSettingsCheck','ShadeWallpaperRepositoryCheck','ShadeWallpaperRuntimeCheck','ModuleLifecycleCheck','LauncherIconCheck','FontImportGateCheck','FontImportTransactionCheck','FontCatalogCheck','FontDownloadTransportCheck','FontWeightCheck','NotificationBigClockSettingsCheck','NotificationBigClockModelCheck','NotificationLandscapeLayoutCheck','NotificationStackTapCheck','NotificationGroupStackCheck','NotificationNativeStackCheck','NotificationClockIsolationCheck','NotificationClockFontMetadataCheck','C17HighlightRemovalCheck','C17HeadsUpScopeCheck','FreeNoticeCheck','NetworkSpeedControlsCheck','SpeedPositionCheck','LockscreenControlsCheck')) {
    & $c17Java -Xms16m -Xmx128m -XX:+UseSerialGC -classpath "$c17CheckOutput;$CompiledClasses;$XposedApiJar;$c17Json;$AndroidJar" "dev.puitheme.$c17Name"
    if($LASTEXITCODE-ne 0){throw "Check failed: $c17Name"}
}
