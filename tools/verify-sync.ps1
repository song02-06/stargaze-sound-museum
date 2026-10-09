<#
  tools/verify-sync.ps1

  用途：校验【本地仓库】与【远程仓库】是否完全同步。
  对应实践作业验收标准第 (3)(4) 条：
      - 文件是否遗漏
      - 分支结构是否一致
      - 提交历史是否一致

  用法：
      powershell -ExecutionPolicy Bypass -File tools/verify-sync.ps1
      powershell -ExecutionPolicy Bypass -File tools/verify-sync.ps1 -Remote origin -Branch main

  退出码：0 = 完全同步；1 = 存在差异（详见输出）
#>

[CmdletBinding()]
param(
    [string]$Remote = 'origin',
    [string]$Branch = ''
)

$ErrorActionPreference = 'Stop'

function Invoke-Git {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
    $out = & git @Args 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Args -join ' ') 失败：`n$($out -join "`n")"
    }
    return $out
}

function Write-Section {
    param([string]$Title)
    Write-Host ''
    Write-Host "=== $Title ===" -ForegroundColor Cyan
}

$problems = New-Object System.Collections.Generic.List[string]

# ---- 0. 环境检查 ----
if ((Invoke-Git rev-parse --is-inside-work-tree) -ne 'true') {
    throw '当前目录不是 Git 仓库。'
}

$root = (Invoke-Git rev-parse --show-toplevel)
Set-Location -LiteralPath $root

if ([string]::IsNullOrWhiteSpace($Branch)) {
    $Branch = (Invoke-Git rev-parse --abbrev-ref HEAD)
}

$remoteRef = "$Remote/$Branch"

Write-Host "仓库根目录 : $root"
Write-Host "当前分支   : $Branch"
Write-Host "远端引用   : $remoteRef"

# ---- 1. 拉取远端最新状态（只更新远端跟踪分支，不动工作区） ----
Write-Section '1. fetch 远端'
Invoke-Git fetch $Remote --prune | ForEach-Object { Write-Host $_ }
Write-Host 'fetch 完成（未修改本地工作区）'

# ---- 2. 提交历史是否一致 ----
Write-Section '2. 提交历史（HEAD vs 远端）'
$localSha = (Invoke-Git rev-parse HEAD)
$remoteSha = (Invoke-Git rev-parse $remoteRef)
Write-Host "本地 HEAD  : $localSha"
Write-Host "远端 $remoteRef : $remoteSha"

if ($localSha -eq $remoteSha) {
    Write-Host '提交历史完全一致 ✔' -ForegroundColor Green
}
else {
    $counts = (Invoke-Git rev-list --left-right --count "HEAD...$remoteRef") -split '\s+'
    $ahead = [int]$counts[0]
    $behind = [int]$counts[1]
    $problems.Add("提交历史不一致：本地领先 $ahead 个提交，落后 $behind 个提交")
    Write-Host "提交历史不一致 ✘  本地领先 $ahead / 落后 $behind" -ForegroundColor Red
    Write-Host '本地独有：'
    (& git log --oneline HEAD "^$remoteRef") | ForEach-Object { Write-Host "  $_" }
    Write-Host '远端独有：'
    (& git log --oneline $remoteRef '^HEAD') | ForEach-Object { Write-Host "  $_" }
}

# ---- 3. 文件清单是否一致（逐文件比对，抓遗漏） ----
Write-Section '3. 文件清单（HEAD 树 vs 远端树）'
$localFiles = @(Invoke-Git ls-tree -r --name-only HEAD)
$remoteFiles = @(Invoke-Git ls-tree -r --name-only $remoteRef)
$diff = Compare-Object -ReferenceObject $localFiles -DifferenceObject $remoteFiles

Write-Host "本地文件数：$($localFiles.Count)"
Write-Host "远端文件数：$($remoteFiles.Count)"

if (-not $diff) {
    Write-Host '文件清单完全一致 ✔' -ForegroundColor Green
}
else {
    $problems.Add("文件清单不一致：共 $($diff.Count) 处差异")
    Write-Host "文件清单不一致 ✘  差异 $($diff.Count) 处" -ForegroundColor Red
    foreach ($d in $diff) {
        $side = if ($d.SideIndicator -eq '<=') { '仅本地有' } else { '仅远端有' }
        Write-Host "  [$side] $($d.InputObject)"
    }
}

# ---- 4. 工作区是否干净 ----
Write-Section '4. 工作区状态'
$porcelain = @(Invoke-Git status --porcelain)
if ($porcelain.Count -eq 0) {
    Write-Host '工作区干净 ✔' -ForegroundColor Green
}
else {
    $problems.Add("工作区有 $($porcelain.Count) 个未提交变更")
    Write-Host "工作区有 $($porcelain.Count) 个未提交变更 ✘" -ForegroundColor Yellow
    $porcelain | ForEach-Object { Write-Host "  $_" }
}

# ---- 5. 分支结构 ----
Write-Section '5. 分支结构'
Invoke-Git branch -vv | ForEach-Object { Write-Host $_ }
Write-Host ''
Write-Host '远端分支：'
Invoke-Git branch -r | ForEach-Object { Write-Host $_ }

$upstream = (& git rev-parse --abbrev-ref "$Branch@{upstream}" 2>&1)
if ($LASTEXITCODE -ne 0) {
    $problems.Add("当前分支 $Branch 没有设置上游跟踪分支")
    Write-Host "分支 $Branch 未设置上游跟踪分支 ✘" -ForegroundColor Red
}
else {
    Write-Host "上游跟踪分支：$upstream ✔" -ForegroundColor Green
}

# ---- 6. 结论 ----
Write-Section '结论'
if ($problems.Count -eq 0) {
    Write-Host '本地与远程完全同步 ✔' -ForegroundColor Green
    exit 0
}

Write-Host "发现 $($problems.Count) 个问题：" -ForegroundColor Red
$problems | ForEach-Object { Write-Host "  - $_" }
exit 1
