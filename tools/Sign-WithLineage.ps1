# SPDX-License-Identifier: GPL-3.0-only
# Copyright (C) 2026 aiingjie
<#
.SYNOPSIS
    为独立构建包加入既有公开版到当前版的签名升级链。
    Keystore 与密码必须由项目维护者在本地提供，不随源码发布。
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$Apk,
    [Parameter(Mandatory=$true)][string]$OutputApk,
    [Parameter(Mandatory=$true)][string]$JdkHome,
    [Parameter(Mandatory=$true)][string]$AndroidSdk,
    [Parameter(Mandatory=$true)][string]$PreviousKeystore,
    [Parameter(Mandatory=$true)][string]$CurrentKeystore,
    [string]$PreviousAlias = 'local-build',
    [string]$CurrentAlias = 'androiddebugkey',
    [string]$BuildToolsVersion = '35.0.0',
    [string]$SigningLineage = (Join-Path $PSScriptRoot 'signing\c17-public-to-current.lineage')
)
$ErrorActionPreference = 'Stop'
foreach ($c17Required in @($Apk,$PreviousKeystore,$CurrentKeystore,$SigningLineage)) {
    if (-not (Test-Path -LiteralPath $c17Required -PathType Leaf)) { throw "文件不存在：$c17Required" }
}
if (-not $env:C17_PREVIOUS_STORE_PASS -or -not $env:C17_STORE_PASS) {
    throw '请在本地设置 C17_PREVIOUS_STORE_PASS 与 C17_STORE_PASS；禁止把密码写进公开构建文件。'
}
$c17KeyEnv = 'C17_SIGN_CURRENT_KEY_' + [Guid]::NewGuid().ToString('N')
$c17OldKeyEnv = 'C17_SIGN_PREVIOUS_KEY_' + [Guid]::NewGuid().ToString('N')
$c17Java = Join-Path $JdkHome 'bin\java.exe'
$c17Tools = Join-Path $AndroidSdk "build-tools\$BuildToolsVersion"
$c17Signer = Join-Path $c17Tools 'lib\apksigner.jar'
$c17Input = [IO.Path]::GetFullPath($Apk)
$c17Output = [IO.Path]::GetFullPath($OutputApk)
if ($c17Input.Equals($c17Output,[StringComparison]::OrdinalIgnoreCase)) { throw '输入输出请使用不同文件，核对完成后再替换交付文件。' }
[Environment]::SetEnvironmentVariable($c17OldKeyEnv, $(if ($env:C17_PREVIOUS_KEY_PASS) {$env:C17_PREVIOUS_KEY_PASS} else {$env:C17_PREVIOUS_STORE_PASS}), 'Process')
[Environment]::SetEnvironmentVariable($c17KeyEnv, $(if ($env:C17_KEY_PASS) {$env:C17_KEY_PASS} else {$env:C17_STORE_PASS}), 'Process')
try {
    & $c17Java -jar $c17Signer sign --out $c17Output --lineage $SigningLineage --rotation-min-sdk-version 28 --v4-signing-enabled false `
        --ks $PreviousKeystore --ks-key-alias $PreviousAlias --ks-pass env:C17_PREVIOUS_STORE_PASS --key-pass "env:$c17OldKeyEnv" `
        --next-signer --ks $CurrentKeystore --ks-key-alias $CurrentAlias --ks-pass env:C17_STORE_PASS --key-pass "env:$c17KeyEnv" $c17Input
    if ($LASTEXITCODE -ne 0) { throw '升级链签名失败。' }
    & $c17Java -jar $c17Signer verify --verbose --print-certs $c17Output
    if ($LASTEXITCODE -ne 0) { throw '签名核对失败。' }
    & (Join-Path $c17Tools 'zipalign.exe') -c -p 4 $c17Output
    if ($LASTEXITCODE -ne 0) { throw 'APK 对齐核对失败。' }
    $c17Digest = (Get-FileHash -LiteralPath $c17Output -Algorithm SHA256).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText("$c17Output.sha256", "$c17Digest  $([IO.Path]::GetFileName($c17Output))`n", (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "签名完成：$c17Output"
    Write-Host "SHA-256：$c17Digest"
} finally {
    [Environment]::SetEnvironmentVariable($c17OldKeyEnv,$null,'Process')
    [Environment]::SetEnvironmentVariable($c17KeyEnv,$null,'Process')
}
