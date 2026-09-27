#!/usr/bin/env bash
# webschool DB의 테이블별 정확한 행 수를 "테이블명 행수" 형식으로 출력한다.
# 운영 DB 컷오버(AWS.md "8단계" 3번)에서 호스트 mysqld → db 컨테이너로 옮긴 뒤
# 데이터가 빠짐없이 복원됐는지 diff로 비교하기 위한 용도
# (information_schema.tables.table_rows는 InnoDB에선 추정값이라 못 씀).
#
# 사용법:
#   ./db-row-counts.sh host   > counts-before.txt   # 호스트 mysqld (~/.my.cnf 인증)
#   ./db-row-counts.sh docker > counts-after.txt    # docker-compose.prod.yml의 db 컨테이너
#   diff counts-before.txt counts-after.txt && echo "행 수 일치"
# docker 모드의 compose 파일/실행파일은 backup-db-docker.sh와 같은 환경변수
# (COMPOSE_FILE, COMPOSE_BIN)로 바꿀 수 있다.

set -euo pipefail

DB_NAME="webschool"
MODE="${1:-}"
COMPOSE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-$COMPOSE_DIR/docker-compose.prod.yml}"
COMPOSE_BIN="${COMPOSE_BIN:-docker-compose}"
DEFAULTS_FILE="${MYSQL_DEFAULTS_FILE:-${HOME:-/root}/.my.cnf}"

# 표준입력으로 받은 SQL을 대상 DB에서 실행하고 결과를 탭 구분(-N -B)으로 출력
run_sql() {
    case "$MODE" in
        host)
            mysql --defaults-file="$DEFAULTS_FILE" -N -B "$DB_NAME"
            ;;
        docker)
            # shellcheck disable=SC2016 # 컨테이너 안에서 확장돼야 한다
            $COMPOSE_BIN -f "$COMPOSE_FILE" exec -T db \
                sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -B "$1"' _ "$DB_NAME"
            ;;
    esac
}

case "$MODE" in
    host|docker) ;;
    *) echo "사용법: $0 host|docker" >&2; exit 1 ;;
esac

# 테이블 목록으로 UNION ALL 카운트 쿼리 하나를 만들어 한 번에 실행한다
# (테이블마다 접속을 새로 열지 않도록).
QUERY="$(echo "SELECT table_name FROM information_schema.tables WHERE table_schema = '$DB_NAME' AND table_type = 'BASE TABLE' ORDER BY table_name;" \
    | run_sql \
    | tr -d '\r' \
    | awk 'NF { printf "%sSELECT '\''%s'\'', COUNT(*) FROM `%s`", sep, $1, $1; sep = " UNION ALL " } END { print ";" }')"

if [ "$QUERY" = ";" ]; then
    echo "$DB_NAME 스키마에 테이블이 없음" >&2
    exit 1
fi

echo "$QUERY" | run_sql | tr -d '\r' | tr '\t' ' '
