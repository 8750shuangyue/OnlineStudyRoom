param(
    [int]$Keep = 14,
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'

$dataDir = Join-Path $ProjectRoot 'data'
$backupRoot = Join-Path $ProjectRoot 'backups'

if (-not (Test-Path $dataDir)) {
    Write-Host '未找到 data 目录，跳过备份'
    exit 0
}

$dbFile = Join-Path $dataDir 'studyroom.mv.db'
if (-not (Test-Path $dbFile)) {
    Write-Host '未找到数据库文件 studyroom.mv.db，跳过备份'
    exit 0
}

New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$target = Join-Path $backupRoot "studyroom-$stamp.zip"

$files = @($dbFile)
$traceFile = Join-Path $dataDir 'studyroom.trace.db'
if (Test-Path $traceFile) {
    $files += $traceFile
}

try {
    Compress-Archive -Path $files -DestinationPath $target -CompressionLevel Optimal
    Write-Host "已备份 $($files.Count) 个文件到 $target"
} catch {
    Write-Host "压缩失败：$($_.Exception.Message)"
    Write-Host '提示：后端正在运行时 H2 数据库文件会被锁定，冷备份需先停止后端；'
    Write-Host '运行中的在线备份请使用应用内「设置 → 备份数据」。'
    if (Test-Path $target) {
        Remove-Item -LiteralPath $target -Force -ErrorAction SilentlyContinue
    }
    exit 1
}

# 只保留最近 $Keep 份
Get-ChildItem $backupRoot -Filter 'studyroom-*.zip' |
    Sort-Object Name -Descending |
    Select-Object -Skip $Keep |
    Remove-Item -Force

Write-Host "完成（保留最近 $Keep 份备份）"
