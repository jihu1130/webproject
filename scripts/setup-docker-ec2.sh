#!/usr/bin/env bash
# webschool 운영 서버(EC2, Amazon Linux 2023)에 Docker Engine + Compose 플러그인을
# 설치하는 스크립트 (Docker 도입 2단계 - AWS.md "8단계" 참고). SSM Session Manager로
# 서버에 접속해서(`aws ssm start-session --target <인스턴스ID>`) 이 스크립트 내용을
# 그대로 붙여넣어 실행한다 - setup-cloudwatch-agent.sh와 동일한 사용 방식.
#
# 여러 번 실행해도 안전(idempotent) - 이미 설치돼 있으면 건너뛴다.
#
# 이 스크립트는 Docker/Compose만 설치한다. 실제 컨테이너 기동(docker compose up)은
# 운영 DB 컷오버 절차(AWS.md "8단계" 3번 항목)의 일부라 별도로 진행 - 여기서 같이
# 하면 기존 mysqld/systemctl 기반 서비스와 충돌할 수 있다.

set -euo pipefail

if ! command -v docker >/dev/null 2>&1; then
    sudo dnf install -y docker
    sudo systemctl enable --now docker
    # ec2-user로 docker 명령을 sudo 없이 쓰려면 docker 그룹 추가 필요(재로그인 후 적용).
    sudo usermod -aG docker ec2-user
else
    echo "Docker는 이미 설치돼 있음: $(docker --version)"
fi

# Amazon Linux 2023의 dnf 저장소엔 docker-compose-plugin이 없어서 GitHub 릴리스
# 바이너리를 CLI 플러그인 디렉터리에 직접 설치한다(공식 문서 권장 방식).
COMPOSE_DIR="/usr/libexec/docker/cli-plugins"
if ! docker compose version >/dev/null 2>&1; then
    sudo mkdir -p "$COMPOSE_DIR"
    sudo curl -SL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64" \
        -o "$COMPOSE_DIR/docker-compose"
    sudo chmod +x "$COMPOSE_DIR/docker-compose"
else
    echo "Docker Compose는 이미 설치돼 있음: $(docker compose version)"
fi

echo "설치 완료."
echo "확인: docker --version && docker compose version"
echo "주의: usermod로 docker 그룹에 추가한 효과는 재로그인(SSM 세션 재접속) 후에나 반영됨 -"
echo "      그 전까지는 sudo docker ...로 실행할 것."
