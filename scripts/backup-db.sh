#!/usr/bin/env bash
# webschool DB 백업 스크립트 (mysqldump) - EC2(Amazon Linux 2023) 운영 서버용.
# cron으로 매일 자동 실행하는 걸 전제로 한다(설치 방법은 AWS.md "2단계" 참고).
#
# 로컬 개발용 scripts/backup-db.ps1과 동일한 구조(mysqldump --routines
# --single-transaction, 최근 $KEEP_COUNT개만 보관)이지만, 운영 DB 비밀번호를
# 이 스크립트(git 추적 대상)에 평문으로 넣지 않기 위해 MySQL 옵션파일
# (~/.my.cnf, 권한 600 - git에 안 올라감)에서 인증 정보를 읽는다.
#
# 사용법: ./backup-db.sh
# 복구는 restore-db.sh를 쓰거나 직접:
#   mysql --defaults-file="$HOME/.my.cnf" webschool < backups/webschool_20260827_030000.sql

set -euo pipefail

DB_NAME="webschool"
KEEP_COUNT=14
DEFAULTS_FILE="${MYSQL_DEFAULTS_FILE:-$HOME/.my.cnf}"
BACKUP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/backups"

if [ ! -f "$DEFAULTS_FILE" ]; then
    echo "MySQL 옵션파일을 찾을 수 없음: $DEFAULTS_FILE (AWS.md \"2단계\" 참고 - [client] 섹션에 user/password 설정 필요)" >&2
    exit 1
fi

mkdir -p "$BACKUP_DIR"

TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_FILE="$BACKUP_DIR/webschool_${TIMESTAMP}.sql"

mysqldump --defaults-file="$DEFAULTS_FILE" --routines --single-transaction "$DB_NAME" > "$OUT_FILE"

echo "백업 완료: $OUT_FILE"

# 오래된 백업 정리 - 최근 $KEEP_COUNT개만 보관
mapfile -t BACKUPS < <(ls -1t "$BACKUP_DIR"/webschool_*.sql 2>/dev/null)
if [ "${#BACKUPS[@]}" -gt "$KEEP_COUNT" ]; then
    for old in "${BACKUPS[@]:$KEEP_COUNT}"; do
        rm -f "$old"
        echo "오래된 백업 삭제: $(basename "$old")"
    done
fi
