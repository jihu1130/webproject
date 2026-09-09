#!/usr/bin/env bash
# webschool DB 백업 스크립트 - 컨테이너 기준 버전 (Docker 도입 2단계, 운영 DB
# 컷오버 이후 사용 예정, AWS.md "8단계" 참고). 기존 backup-db.sh(호스트에 직접
# 설치된 mysqld 대상)와 구조는 동일(mysqldump --routines --single-transaction,
# 최근 $KEEP_COUNT개만 보관)하지만, 인증 정보를 호스트 옵션파일이 아니라
# docker-compose.yml과 같은 위치의 .env(MYSQL_ROOT_PASSWORD)에서 읽고,
# `docker compose exec`로 db 컨테이너 안에서 mysqldump를 실행한다.
#
# **컷오버 전까지는 쓰지 않는다** - 지금 크론에 등록된 스크립트는 여전히
# backup-db.sh(호스트 mysqld 대상)이고, 이 스크립트는 db 컨테이너가 실제로
# 떠 있어야 동작한다. 컷오버 완료 후 crontab을 이 스크립트로 교체할 것.
#
# 사용법(컷오버 후, docker-compose.yml이 있는 디렉터리에서): ./backup-db-docker.sh

set -euo pipefail

DB_NAME="webschool"
KEEP_COUNT=14
COMPOSE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_DIR="$COMPOSE_DIR/backups"
ENV_FILE="$COMPOSE_DIR/.env"

if [ ! -f "$ENV_FILE" ]; then
    echo ".env를 찾을 수 없음: $ENV_FILE (.env.example을 복사해서 MYSQL_ROOT_PASSWORD를 채울 것)" >&2
    exit 1
fi

# shellcheck disable=SC1090
source "$ENV_FILE"
if [ -z "${MYSQL_ROOT_PASSWORD:-}" ]; then
    echo "MYSQL_ROOT_PASSWORD가 .env에 없음" >&2
    exit 1
fi

mkdir -p "$BACKUP_DIR"

TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_FILE="$BACKUP_DIR/webschool_${TIMESTAMP}.sql"

docker compose -f "$COMPOSE_DIR/docker-compose.yml" exec -T db \
    mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --routines --single-transaction "$DB_NAME" > "$OUT_FILE"

echo "백업 완료: $OUT_FILE"

# 오래된 백업 정리 - 최근 $KEEP_COUNT개만 보관 (backup-db.sh와 동일한 원칙)
mapfile -t BACKUPS < <(ls -1t "$BACKUP_DIR"/webschool_*.sql 2>/dev/null)
if [ "${#BACKUPS[@]}" -gt "$KEEP_COUNT" ]; then
    for old in "${BACKUPS[@]:$KEEP_COUNT}"; do
        rm -f "$old"
        echo "오래된 백업 삭제: $(basename "$old")"
    done
fi
