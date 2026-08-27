#!/usr/bin/env bash
# webschool DB 복구 스크립트 - backup-db.sh가 만든 덤프 파일을 다시 넣는다.
# 사용법: ./restore-db.sh backups/webschool_20260827_030000.sql
#
# 주의: 대상 DB의 기존 데이터를 덮어쓴다(테이블 DROP/재생성 포함, mysqldump 기본 동작).
# 되돌릴 수 없으니 운영 DB에 실행하기 전 반드시 이 파일 경로가 맞는지 한 번 더 확인할 것.

set -euo pipefail

if [ $# -ne 1 ]; then
    echo "사용법: $0 <백업파일 경로>" >&2
    exit 1
fi

BACKUP_FILE="$1"
DB_NAME="webschool"
DEFAULTS_FILE="${MYSQL_DEFAULTS_FILE:-${HOME:-/root}/.my.cnf}"

if [ ! -f "$BACKUP_FILE" ]; then
    echo "백업 파일을 찾을 수 없음: $BACKUP_FILE" >&2
    exit 1
fi

if [ ! -f "$DEFAULTS_FILE" ]; then
    echo "MySQL 옵션파일을 찾을 수 없음: $DEFAULTS_FILE" >&2
    exit 1
fi

mysql --defaults-file="$DEFAULTS_FILE" "$DB_NAME" < "$BACKUP_FILE"

echo "복구 완료: $BACKUP_FILE -> $DB_NAME"
