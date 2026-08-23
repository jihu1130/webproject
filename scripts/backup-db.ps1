# webschool DB 백업 스크립트 (mysqldump).
#
# 사용법(PowerShell): .\scripts\backup-db.ps1
# 복구는 restore-db.ps1을 쓰거나 직접:
#   & "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p1234 webschool < backups\webschool_20260823_161500.sql
#
# CLAUDE.md에 이미 정리된 로컬 개발 DB 접속 정보(root/1234, webschool)를 그대로 재사용한다.
# mysql 클라이언트가 PATH에 없어 CLAUDE.md와 동일하게 풀 경로로 직접 실행한다.

$ErrorActionPreference = "Stop"

$mysqldump = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe"
$dbUser = "root"
$dbPassword = "1234"
$dbName = "webschool"
$keepCount = 14

$repoRoot = Split-Path -Parent $PSScriptRoot
$backupDir = Join-Path $repoRoot "backups"
if (-not (Test-Path $backupDir)) {
    New-Item -ItemType Directory -Path $backupDir | Out-Null
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$outFile = Join-Path $backupDir "webschool_$timestamp.sql"

& $mysqldump "-u$dbUser" "-p$dbPassword" --routines --single-transaction $dbName | Out-File -FilePath $outFile -Encoding utf8
if ($LASTEXITCODE -ne 0) {
    throw "mysqldump 실패 (exit code $LASTEXITCODE)"
}

Write-Host "백업 완료: $outFile"

# 오래된 백업 정리 - 최근 $keepCount개만 보관
$backups = Get-ChildItem -Path $backupDir -Filter "webschool_*.sql" | Sort-Object LastWriteTime -Descending
if ($backups.Count -gt $keepCount) {
    $toDelete = $backups | Select-Object -Skip $keepCount
    foreach ($f in $toDelete) {
        Remove-Item $f.FullName -Force
        Write-Host "오래된 백업 삭제: $($f.Name)"
    }
}
