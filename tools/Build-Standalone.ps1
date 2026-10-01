# SPDX-License-Identifier: GPL-3.0-only
# Copyright (C) 2026 aiingjie
<#
.SYNOPSIS
    使用 JDK 17 和 Android SDK 直接构建 C17 APK；不需要 Gradle wrapper。
.EXAMPLE
    .\tools\Build-Standalone.ps1 -AndroidSdk 'C:\Android\Sdk' -Offline
.EXAMPLE
    .\tools\Build-Standalone.ps1 -Keystore '.\migration-private\test.keystore' -StorePass $env:C17_STORE_PASS
#>
[CmdletBinding()]
param(
    [string]$JdkHome = '',
    [string]$AndroidSdk = '',
    [string]$BuildToolsVersion = '35.0.0',
    [string]$ApiJar = '',
    [string]$Keystore = '',
    [string]$StorePass = '',
    [string]$KeyPass = '',
    [string]$KeyAlias = 'androiddebugkey',
    [string]$OutputDirectory = '',
    [string]$StagingRoot = '',
    [switch]$Offline,
    [switch]$KeepStaging
)
$ErrorActionPreference = 'Stop'
$c17Project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$c17Main = Join-Path $c17Project 'app\src\main'
$c17Utf8 = New-Object System.Text.UTF8Encoding($false)
$c17Stage = $null
$c17StageParent = $null
$c17StoreVariable = 'C17_BUILD_STORE_' + [Guid]::NewGuid().ToString('N')
$c17KeyVariable = 'C17_BUILD_KEY_' + [Guid]::NewGuid().ToString('N')

function Invoke-C17Tool {
    param([string]$Tool, [string[]]$ToolArguments, [string]$Description)
    & $Tool @ToolArguments
    if ($LASTEXITCODE -ne 0) { throw "$Description 失败（退出码 $LASTEXITCODE）。" }
}

function Resolve-C17Input {
    param([string]$Path)
    if (-not [IO.Path]::IsPathRooted($Path)) { $Path = Join-Path $c17Project $Path }
    return [IO.Path]::GetFullPath($Path)
}

function Get-C17Jdk {
    $c17Candidates = New-Object 'System.Collections.Generic.List[string]'
    if ($JdkHome) { $c17Candidates.Add((Resolve-C17Input $JdkHome)) }
    else {
        if ($env:JAVA_HOME) { $c17Candidates.Add($env:JAVA_HOME) }
        $c17JavacCommand = Get-Command javac.exe -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($c17JavacCommand) { $c17Candidates.Add((Split-Path (Split-Path $c17JavacCommand.Source -Parent) -Parent)) }
        foreach ($c17Vendor in @('Eclipse Adoptium', 'Java', 'Microsoft', 'Amazon Corretto', 'BellSoft')) {
            if (-not $env:ProgramFiles) { continue }
            $c17VendorRoot = Join-Path $env:ProgramFiles $c17Vendor
            if (Test-Path -LiteralPath $c17VendorRoot) {
                foreach ($c17Folder in Get-ChildItem -LiteralPath $c17VendorRoot -Directory | Sort-Object Name -Descending) {
                    $c17Candidates.Add($c17Folder.FullName)
                }
            }
        }
    }
    foreach ($c17Candidate in $c17Candidates | Select-Object -Unique) {
        $c17Bin = Join-Path $c17Candidate 'bin'
        if (@('java.exe', 'javac.exe', 'jar.exe', 'keytool.exe') | Where-Object { -not (Test-Path -LiteralPath (Join-Path $c17Bin $_)) }) { continue }
        $c17Version = (& (Join-Path $c17Bin 'javac.exe') -version 2>&1 | Out-String).Trim()
        if ($LASTEXITCODE -eq 0 -and $c17Version -match '^javac 17(?:\.|\s|$)') { return [IO.Path]::GetFullPath($c17Candidate) }
    }
    throw '没有找到完整 JDK 17。请安装 JDK 17，设置 JAVA_HOME，或传入 -JdkHome（包含 bin\javac.exe 的目录）。'
}

function Get-C17Sdk {
    $c17Candidates = New-Object 'System.Collections.Generic.List[string]'
    if ($AndroidSdk) { $c17Candidates.Add((Resolve-C17Input $AndroidSdk)) }
    else {
        if ($env:ANDROID_SDK_ROOT) { $c17Candidates.Add($env:ANDROID_SDK_ROOT) }
        if ($env:ANDROID_HOME) { $c17Candidates.Add($env:ANDROID_HOME) }
        $c17Properties = Join-Path $c17Project 'local.properties'
        if (Test-Path -LiteralPath $c17Properties) {
            foreach ($c17Line in Get-Content -LiteralPath $c17Properties) {
                if ($c17Line -match '^\s*sdk\.dir\s*=\s*(.+)$') {
                    $c17Candidates.Add($Matches[1].Replace('\:', ':').Replace('\\', '\'))
                }
            }
        }
        if ($env:LOCALAPPDATA) { $c17Candidates.Add((Join-Path $env:LOCALAPPDATA 'Android\Sdk')) }
        $c17PortableSdk = Join-Path $PSScriptRoot 'android-sdk'
        if (Test-Path -LiteralPath $c17PortableSdk) { $c17Candidates.Add($c17PortableSdk) }
    }
    foreach ($c17Candidate in $c17Candidates | Select-Object -Unique) {
        $c17Candidate = Resolve-C17Input $c17Candidate
        if (Test-Path -LiteralPath (Join-Path $c17Candidate 'platforms\android-35\android.jar')) {
            if (Test-Path -LiteralPath (Join-Path $c17Candidate "build-tools\$BuildToolsVersion\aapt2.exe")) { return $c17Candidate }
        }
    }
    throw "没有找到 Android API 35 和 Build Tools $BuildToolsVersion。请在 SDK Manager 安装 platforms;android-35 与 build-tools;$BuildToolsVersion，并指定 -AndroidSdk。"
}

function New-C17Staging {
    # aapt2 对部分 Windows 中文路径处理有问题：所有输入/生成文件使用 ASCII 路径。
    $c17Roots = New-Object 'System.Collections.Generic.List[string]'
    if ($StagingRoot) { $c17Roots.Add((Resolve-C17Input $StagingRoot)) }
    else {
        if ($env:TEMP) { $c17Roots.Add($env:TEMP) }
        if ($env:TMP) { $c17Roots.Add($env:TMP) }
        if ($env:PUBLIC) { $c17Roots.Add((Join-Path $env:PUBLIC 'C17BuildTemp')) }
        if ($env:SystemDrive) { $c17Roots.Add((Join-Path $env:SystemDrive 'C17BuildTemp')) }
    }
    foreach ($c17Root in $c17Roots | Select-Object -Unique) {
        $c17Root = [IO.Path]::GetFullPath($c17Root)
        if ($c17Root -match '[^\x20-\x7e]') { continue }
        $c17Candidate = Join-Path $c17Root ('c17-build-' + [Guid]::NewGuid().ToString('N'))
        try {
            New-Item -ItemType Directory -Path $c17Candidate -Force | Out-Null
            [IO.File]::WriteAllText((Join-Path $c17Candidate 'write-check.txt'), 'C17', $c17Utf8)
            $script:c17StageParent = (Get-Item -LiteralPath $c17Root).FullName.TrimEnd('\')
            return (Get-Item -LiteralPath $c17Candidate).FullName
        } catch { continue }
    }
    throw '没有可写的 ASCII 临时目录。请创建例如 C:\C17Temp，并指定 -StagingRoot C:\C17Temp。'
}

function Add-C17ZipFile {
    param($Archive, [string]$Source, [string]$EntryName, [System.IO.Compression.CompressionLevel]$Compression)
    if ($null -ne $Archive.GetEntry($EntryName)) { throw "APK 中存在重复条目：$EntryName" }
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($Archive, $Source, $EntryName, $Compression) | Out-Null
}

try {
    if (-not (Test-Path -LiteralPath (Join-Path $c17Main 'AndroidManifest.xml'))) { throw '项目不完整：缺少 app/src/main/AndroidManifest.xml。' }
    foreach ($c17Metadata in @('java_init.list', 'module.prop', 'scope.list')) {
        if (-not (Test-Path -LiteralPath (Join-Path $c17Main "resources\META-INF\xposed\$c17Metadata"))) { throw "项目不完整：缺少 Xposed 元数据 $c17Metadata。" }
    }
    $c17Jdk = Get-C17Jdk
    $c17Sdk = Get-C17Sdk
    $c17Bin = Join-Path $c17Jdk 'bin'
    $c17Java = Join-Path $c17Bin 'java.exe'
    $c17Tools = Join-Path $c17Sdk "build-tools\$BuildToolsVersion"
    foreach ($c17Required in @('aapt2.exe', 'zipalign.exe', 'lib\d8.jar', 'lib\apksigner.jar')) {
        if (-not (Test-Path -LiteralPath (Join-Path $c17Tools $c17Required))) { throw "Build Tools 缺少 $c17Required，请重新安装 $BuildToolsVersion。" }
    }
    $c17OutputDir = if ($OutputDirectory) { Resolve-C17Input $OutputDirectory } else { Join-Path $c17Project 'app\build\standalone' }
    New-Item -ItemType Directory -Path $c17OutputDir -Force | Out-Null
    $c17Stage = New-C17Staging
    Write-Host "JDK 17：$c17Jdk"
    Write-Host "Android SDK：$c17Sdk"
    Write-Host '正在准备源码、字体与模块元数据……'
    $c17Source = Join-Path $c17Stage 'main'
    Copy-Item -LiteralPath $c17Main -Destination $c17Source -Recurse
    $c17Android = Join-Path $c17Stage 'android-35.jar'
    Copy-Item -LiteralPath (Join-Path $c17Sdk 'platforms\android-35\android.jar') -Destination $c17Android
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $c17Api = Join-Path $c17Stage 'api-101.0.0.jar'
    $c17LocalApi = if ($ApiJar) { Resolve-C17Input $ApiJar } else { Join-Path $PSScriptRoot 'lib\api-101.0.0.jar' }
    if (Test-Path -LiteralPath $c17LocalApi) {
        Copy-Item -LiteralPath $c17LocalApi -Destination $c17Api
    } elseif ($ApiJar -or $Offline) {
        throw '缺少 LibXposed API 101。请提供 tools/lib/api-101.0.0.jar，或使用 -ApiJar 指定该 JAR。'
    } else {
        Write-Host '从 Maven Central 下载 LibXposed API 101（仅构建依赖）……'
        [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
        $c17Aar = Join-Path $c17Stage 'api-101.0.0.aar'
        Invoke-WebRequest -UseBasicParsing -Uri 'https://repo.maven.apache.org/maven2/io/github/libxposed/api/101.0.0/api-101.0.0.aar' -OutFile $c17Aar
        $c17AarZip = [IO.Compression.ZipFile]::OpenRead($c17Aar)
        try {
            $c17AarClasses = $c17AarZip.GetEntry('classes.jar')
            if ($null -eq $c17AarClasses) { throw '下载的 LibXposed AAR 不包含 classes.jar。' }
            [IO.Compression.ZipFileExtensions]::ExtractToFile($c17AarClasses, $c17Api)
        } finally { $c17AarZip.Dispose() }
    }
    $c17ApiZip = [IO.Compression.ZipFile]::OpenRead($c17Api)
    try {
        if ($null -eq $c17ApiZip.GetEntry('io/github/libxposed/api/XposedModule.class')) { throw 'LibXposed API JAR 无效：未找到 XposedModule.class。' }
    } finally { $c17ApiZip.Dispose() }

    [xml]$c17Manifest = Get-Content -LiteralPath (Join-Path $c17Source 'AndroidManifest.xml') -Raw -Encoding UTF8
    $c17AndroidNamespace = 'http://schemas.android.com/apk/res/android'
    $c17MinApi = $c17Manifest.manifest.'uses-sdk'.GetAttribute('minSdkVersion', $c17AndroidNamespace)
    $c17TargetApi = $c17Manifest.manifest.'uses-sdk'.GetAttribute('targetSdkVersion', $c17AndroidNamespace)
    $c17Version = $c17Manifest.manifest.GetAttribute('versionName', $c17AndroidNamespace)
    if ($c17MinApi -notmatch '^\d+$' -or $c17TargetApi -notmatch '^\d+$') { throw 'AndroidManifest.xml 的 SDK 版本必须是数字。' }
    $c17SafeVersion = [regex]::Replace($c17Version, '[^A-Za-z0-9._-]', '_')
    $c17Classes = Join-Path $c17Stage 'classes'
    $c17Generated = Join-Path $c17Stage 'generated'
    $c17Dex = Join-Path $c17Stage 'dex'
    foreach ($c17Folder in @($c17Classes, $c17Generated, $c17Dex)) { New-Item -ItemType Directory -Path $c17Folder | Out-Null }
    $c17CompiledRes = Join-Path $c17Stage 'compiled-res.zip'
    $c17Unsigned = Join-Path $c17Stage 'unsigned.apk'
    $c17Aligned = Join-Path $c17Stage 'aligned.apk'
    $c17Signed = Join-Path $c17Stage 'signed.apk'
    Write-Host '正在编译 Android 资源……'
    Invoke-C17Tool (Join-Path $c17Tools 'aapt2.exe') @('compile', '--dir', (Join-Path $c17Source 'res'), '-o', $c17CompiledRes) '资源编译'
    $c17LinkArgs = @('link', '-I', $c17Android, '--manifest', (Join-Path $c17Source 'AndroidManifest.xml'), '--min-sdk-version', $c17MinApi, '--target-sdk-version', $c17TargetApi, '--auto-add-overlay', '--java', $c17Generated, '-0', 'ttf', '-0', 'otf', '-0', 'ttc', '-o', $c17Unsigned)
    $c17Assets = Join-Path $c17Source 'assets'
    if (Test-Path -LiteralPath $c17Assets) { $c17LinkArgs += @('-A', $c17Assets) }
    $c17LinkArgs += $c17CompiledRes
    Invoke-C17Tool (Join-Path $c17Tools 'aapt2.exe') $c17LinkArgs '资源链接'

    Write-Host '正在编译全部 Java 源码……'
    $c17JavaFiles = @(Get-ChildItem -LiteralPath (Join-Path $c17Source 'java'), $c17Generated -Recurse -File -Filter '*.java' | Sort-Object FullName)
    if ($c17JavaFiles.Count -eq 0) { throw '缺少 Java 源码。' }
    $c17SourceList = Join-Path $c17Stage 'java-sources.txt'
    [IO.File]::WriteAllLines($c17SourceList, [string[]]@($c17JavaFiles | ForEach-Object { '"' + $_.FullName.Replace('\', '/') + '"' }), $c17Utf8)
    Invoke-C17Tool (Join-Path $c17Bin 'javac.exe') @('-J-Xms16m', '-J-Xmx512m', '-J-XX:+UseSerialGC', '-J-Duser.language=en', '-J-Duser.country=US', '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-Xlint:-options', '-classpath', "$c17Android;$c17Api", '-d', $c17Classes, "@$c17SourceList") 'Java 编译'
    $c17ClassJar = Join-Path $c17Stage 'production-classes.jar'
    Invoke-C17Tool (Join-Path $c17Bin 'jar.exe') @('--create', '--file', $c17ClassJar, '-C', $c17Classes, '.') '类文件归档'
    Write-Host '正在生成 DEX 与 APK……'
    Invoke-C17Tool $c17Java @('-Xms16m', '-Xmx512m', '-XX:+UseSerialGC', '-classpath', (Join-Path $c17Tools 'lib\d8.jar'), 'com.android.tools.r8.D8', '--min-api', $c17MinApi, '--lib', $c17Android, '--classpath', $c17Api, '--output', $c17Dex, $c17ClassJar) 'DEX 编译'
    $c17Archive = [IO.Compression.ZipFile]::Open($c17Unsigned, [IO.Compression.ZipArchiveMode]::Update)
    try {
        # Windows aapt2 某些版本写入反斜线资产名，统一为 APK 标准 '/'。
        foreach ($c17Asset in @($c17Archive.Entries | Where-Object { $_.FullName.StartsWith('assets/') -and $_.FullName.Contains('\') })) {
            $c17AssetName = $c17Asset.FullName.Replace('\', '/')
            if ($null -ne $c17Archive.GetEntry($c17AssetName)) { throw "重复资产：$c17AssetName" }
            $c17Compression = if ($c17AssetName -match '\.(ttf|otf|ttc)$') { [IO.Compression.CompressionLevel]::NoCompression } else { [IO.Compression.CompressionLevel]::Optimal }
            $c17Normalized = $c17Archive.CreateEntry($c17AssetName, $c17Compression)
            $c17Input = $c17Asset.Open(); $c17Output = $c17Normalized.Open()
            try { $c17Input.CopyTo($c17Output) } finally { $c17Output.Dispose(); $c17Input.Dispose() }
            $c17Asset.Delete()
        }
        foreach ($c17DexFile in Get-ChildItem -LiteralPath $c17Dex -File -Filter '*.dex') {
            Add-C17ZipFile $c17Archive $c17DexFile.FullName $c17DexFile.Name ([IO.Compression.CompressionLevel]::Optimal)
        }
        $c17Resources = Join-Path $c17Source 'resources'
        foreach ($c17Resource in Get-ChildItem -LiteralPath $c17Resources -Recurse -File) {
            $c17Entry = $c17Resource.FullName.Substring($c17Resources.Length + 1).Replace('\', '/')
            Add-C17ZipFile $c17Archive $c17Resource.FullName $c17Entry ([IO.Compression.CompressionLevel]::Optimal)
        }
    } finally { $c17Archive.Dispose() }
    # .NET Framework 的 NoCompression 仍可能是 DEFLATE level 0；jar -0 才能保证 ZIP STORED。
    # 字体直接映射加载需要真正未压缩的条目，随后 zipalign 保证其数据偏移对齐。
    if (Test-Path -LiteralPath $c17Assets) {
        $c17FontFiles = @(Get-ChildItem -LiteralPath $c17Assets -Recurse -File | Where-Object { $_.Extension -match '^\.(ttf|otf|ttc)$' })
        if ($c17FontFiles.Count) {
            $c17FontArgs = @('--update', '--file', $c17Unsigned, '--no-compress', '--no-manifest')
            foreach ($c17Font in $c17FontFiles) {
                $c17FontRelative = $c17Font.FullName.Substring($c17Assets.Length + 1).Replace('\', '/')
                $c17FontArgs += @('-C', $c17Source, "assets/$c17FontRelative")
            }
            Invoke-C17Tool (Join-Path $c17Bin 'jar.exe') $c17FontArgs '字体无压缩归档'
        }
    }
    Invoke-C17Tool (Join-Path $c17Tools 'zipalign.exe') @('-f', '-p', '4', $c17Unsigned, $c17Aligned) 'APK 对齐'

    $c17DebugSigning = -not $Keystore
    if ($c17DebugSigning) {
        if (-not $env:USERPROFILE) { throw '缺少 USERPROFILE，请用 -Keystore 指定签名文件。' }
        $c17SigningKey = Join-Path $env:USERPROFILE '.android\debug.keystore'
        $c17StorePassword = 'android'; $c17KeyPassword = 'android'; $c17SigningAlias = 'androiddebugkey'
    } else {
        $c17SigningKey = Resolve-C17Input $Keystore
        if (-not (Test-Path -LiteralPath $c17SigningKey)) { throw '指定的 Keystore 不存在。' }
        $c17StorePassword = if ($StorePass) { $StorePass } else { $env:C17_STORE_PASS }
        if (-not $c17StorePassword) { throw '指定 Keystore 后需要 -StorePass，或环境变量 C17_STORE_PASS。' }
        $c17KeyPassword = if ($KeyPass) { $KeyPass } else { $c17StorePassword }
        $c17SigningAlias = $KeyAlias
    }
    [Environment]::SetEnvironmentVariable($c17StoreVariable, $c17StorePassword, 'Process')
    [Environment]::SetEnvironmentVariable($c17KeyVariable, $c17KeyPassword, 'Process')
    if ($c17DebugSigning -and -not (Test-Path -LiteralPath $c17SigningKey)) {
        New-Item -ItemType Directory -Path (Split-Path $c17SigningKey -Parent) -Force | Out-Null
        Invoke-C17Tool (Join-Path $c17Bin 'keytool.exe') @('-genkeypair', '-noprompt', '-storetype', 'JKS', '-keystore', $c17SigningKey, '-storepass:env', $c17StoreVariable, '-keypass:env', $c17KeyVariable, '-alias', $c17SigningAlias, '-dname', 'CN=Android Debug,O=Android,C=US', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000') '调试签名生成'
    }
    $c17StageKey = Join-Path $c17Stage 'signing.keystore'
    Copy-Item -LiteralPath $c17SigningKey -Destination $c17StageKey
    Write-Host '正在签名并验证 APK……'
    Invoke-C17Tool $c17Java @('-Xms16m', '-Xmx128m', '-XX:+UseSerialGC', '-jar', (Join-Path $c17Tools 'lib\apksigner.jar'), 'sign', '--ks', $c17StageKey, '--ks-pass', "env:$c17StoreVariable", '--key-pass', "env:$c17KeyVariable", '--ks-key-alias', $c17SigningAlias, '--out', $c17Signed, $c17Aligned) 'APK 签名'
    Invoke-C17Tool $c17Java @('-Xms16m', '-Xmx128m', '-XX:+UseSerialGC', '-jar', (Join-Path $c17Tools 'lib\apksigner.jar'), 'verify', '--verbose', $c17Signed) 'APK 签名验证'
    Invoke-C17Tool (Join-Path $c17Tools 'zipalign.exe') @('-c', '-p', '4', $c17Signed) 'APK 对齐验证'
    $c17FinalApk = Join-Path $c17OutputDir "c17-statusbar-$c17SafeVersion-standalone.apk"
    Copy-Item -LiteralPath $c17Signed -Destination $c17FinalApk -Force
    $c17Digest = (Get-FileHash -LiteralPath $c17FinalApk -Algorithm SHA256).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText("$c17FinalApk.sha256", "$c17Digest  $([IO.Path]::GetFileName($c17FinalApk))`n", $c17Utf8)
    Write-Host "构建完成：$c17FinalApk"
    Write-Host "SHA-256：$c17Digest"
    if ($c17DebugSigning) { Write-Host '本次使用本机 Android 调试签名；继续覆盖安装手机已有版本时，请使用迁移包提供的原签名文件。' }
} finally {
    [Environment]::SetEnvironmentVariable($c17StoreVariable, $null, 'Process')
    [Environment]::SetEnvironmentVariable($c17KeyVariable, $null, 'Process')
    if ($c17Stage -and (Test-Path -LiteralPath $c17Stage)) {
        if ($KeepStaging) { Write-Host "保留临时构建目录：$c17Stage（含签名副本，请妥善保管）。" }
        else {
            $c17ResolvedStage = (Get-Item -LiteralPath $c17Stage).FullName
            if (-not $c17ResolvedStage.StartsWith($c17StageParent + '\', [StringComparison]::OrdinalIgnoreCase) -or
                [IO.Path]::GetFileName($c17ResolvedStage) -notmatch '^c17-build-[0-9a-f]{32}$') { throw '临时目录安全检查失败，未执行删除。' }
            Remove-Item -LiteralPath $c17ResolvedStage -Recurse -Force
        }
    }
}
