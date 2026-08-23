# webschool DB 복구 스크립트 - backup-db.ps1이 만든 덤프 파일을 다시 넣는다.
# 사용법(PowerShell): .\scripts\restore-db.ps1 -BackupFile backups\webschool_20260823_161500.sql
#
# 주의: 대상 DB의 기존 데이터를 덮어쓴다(테이블 DROP/재생성 포함, mysqldump 기본 동작).
# 되돌릴 수 없으니 운영 DB에 실행하기 전 반드시 이 파일 경로가 맞는지 한 번 더 확인할 것.

param(
    [Parameter(Mandatory = $true)]
    [string]$BackupFile
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $BackupFile)) {
    throw "백업 파일을 찾을 수 없음: $BackupFile"
}

$mysql = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
$dbUser = "root"
$dbPassword = "1234"
$dbName = "webschool"

Get-Content $BackupFile -Raw | & $mysql "-u$dbUser" "-p$dbPassword" $dbName
if ($LASTEXITCODE -ne 0) {
    throw "복구 실패 (exit code $LASTEXITCODE)"
}

Write-Host "복구 완료: $BackupFile -> $dbName"
