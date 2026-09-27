#!/usr/bin/env bash
# webschool DB 백업 스크립트 - 컨테이너 기준 버전 (Docker 도입 2단계, 운영 DB
# 컷오버 이후 사용 예정, AWS.md "8단계" 참고). 기존 backup-db.sh(호스트에 직접
# 설치된 mysqld 대상)와 구조는 동일(mysqldump --routines --single-transaction,
# 최근 $KEEP_COUNT개만 보관)하지만, 운영 compose 파일(/opt/webschool/
# docker-compose.prod.yml)의 db 컨테이너 안에서 mysqldump를 실행한다.
#
# 비밀번호는 호스트에서 따로 읽지 않고 db 컨테이너가 이미 갖고 있는
# MYSQL_ROOT_PASSWORD 환경변수를 컨테이너 안에서 MYSQL_PWD로 넘긴다 - 호스트
# 프로세스 목록(ps)이나 이 스크립트에 비밀번호가 드러나지 않는다.
#
# EC2엔 compose 플러그인(`docker compose`)이 아니라 독립 실행파일
# `docker-compose`(하이픈)만 설치돼 있어서 기본값을 그쪽으로 둔다(커밋 8a9a201).
#
# **컷오버 전까지는 쓰지 않는다** - 지금 크론에 등록된 스크립트는 여전히
# backup-db.sh(호스트 mysqld 대상)이고, 이 스크립트는 db 컨테이너가 실제로
# 떠 있어야 동작한다. 컷오버 완료 후 crontab을 이 스크립트로 교체할 것.
#
# 사용법(컷오버 후): /opt/webschool/scripts/backup-db-docker.sh
# S3 업로드(BACKUP_S3_BUCKET/db/)도 backup-db.sh와 동일하게 동작한다.

set -euo pipefail

DB_NAME="webschool"
KEEP_COUNT=14
COMPOSE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-$COMPOSE_DIR/docker-compose.prod.yml}"
COMPOSE_BIN="${COMPOSE_BIN:-docker-compose}"
BACKUP_DIR="$COMPOSE_DIR/backups"
BACKUP_S3_BUCKET="${BACKUP_S3_BUCKET-webschool-backups-938436186735}"
AWS_REGION="${AWS_REGION:-ap-northeast-2}"

if [ ! -f "$COMPOSE_FILE" ]; then
    echo "compose 파일을 찾을 수 없음: $COMPOSE_FILE" >&2
    exit 1
fi

mkdir -p "$BACKUP_DIR"

TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_FILE="$BACKUP_DIR/webschool_${TIMESTAMP}.sql"

# shellcheck disable=SC2016 # $MYSQL_ROOT_PASSWORD는 컨테이너 안에서 확장돼야 한다
$COMPOSE_BIN -f "$COMPOSE_FILE" exec -T db \
    sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --routines --single-transaction "$1"' _ "$DB_NAME" \
    > "$OUT_FILE"

echo "백업 완료: $OUT_FILE"

UPLOAD_FAILED=0
if [ -n "$BACKUP_S3_BUCKET" ]; then
    S3_URI="s3://$BACKUP_S3_BUCKET/db/$(basename "$OUT_FILE")"
    if aws s3 cp "$OUT_FILE" "$S3_URI" --region "$AWS_REGION" --only-show-errors; then
        echo "S3 업로드 완료: $S3_URI"
    else
        echo "S3 업로드 실패: $S3_URI (로컬 백업은 정상 생성됨)" >&2
        UPLOAD_FAILED=1
    fi
fi

# 오래된 백업 정리 - 최근 $KEEP_COUNT개만 보관 (backup-db.sh와 동일한 원칙)
mapfile -t BACKUPS < <(ls -1t "$BACKUP_DIR"/webschool_*.sql 2>/dev/null)
if [ "${#BACKUPS[@]}" -gt "$KEEP_COUNT" ]; then
    for old in "${BACKUPS[@]:$KEEP_COUNT}"; do
        rm -f "$old"
        echo "오래된 백업 삭제: $(basename "$old")"
    done
fi

exit "$UPLOAD_FAILED"
