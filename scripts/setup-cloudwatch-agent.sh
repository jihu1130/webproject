#!/usr/bin/env bash
# webschool 애플리케이션/nginx 로그를 CloudWatch Logs로 전송하는 CloudWatch Agent
# 설치/설정 스크립트 (EC2, Amazon Linux 2023 운영 서버 전용). SSM Session
# Manager로 서버에 접속해서(`aws ssm start-session --target <인스턴스ID>`) 이
# 스크립트 내용을 그대로 붙여넣어 실행한다 - 설치 배경/IAM 준비 절차는
# AWS.md "6단계" 참고.
#
# 사전 준비: webschool-ec2-ssm-role에 CloudWatch Logs 쓰기 권한
# (webschool-cloudwatch-logs-write 정책)이 먼저 연결돼 있어야 한다 - 없으면
# 에이전트는 정상적으로 뜨지만 로그 전송만 조용히 실패한다(AccessDenied가
# 에이전트 자체 로그 /opt/aws/amazon-cloudwatch-agent/logs/amazon-cloudwatch-agent.log
# 에만 남고 서비스 상태는 계속 active로 보여서 눈에 띄기 어렵다).
#
# 여러 번 실행해도 안전(idempotent, TestDataSeeder류 스크립트와 동일 원칙) -
# 이미 설치돼 있으면 설정 파일만 최신 내용으로 덮어쓰고 에이전트를 재시작한다.

set -euo pipefail

CONFIG_PATH="/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json"

if ! rpm -q amazon-cloudwatch-agent >/dev/null 2>&1; then
    sudo dnf install -y amazon-cloudwatch-agent
fi

# 로그 그룹은 3개로 분리 - 앱 로그(webschool.log)와 nginx 접근/에러 로그를
# 섞으면 CloudWatch Logs Insights에서 쿼리하기 불편해진다. retention_in_days로
# 보관 기간을 14일로 제한(DB 백업 스크립트의 KEEP_COUNT=14와 동일한 기준) -
# 기본값(무제한 보관)으로 두면 시간이 지날수록 저장 비용이 계속 늘어난다.
sudo tee "$CONFIG_PATH" > /dev/null <<'EOF'
{
  "logs": {
    "logs_collected": {
      "files": {
        "collect_list": [
          {
            "file_path": "/opt/webschool/logs/webschool.log",
            "log_group_name": "/webschool/app",
            "log_stream_name": "{instance_id}",
            "retention_in_days": 14
          },
          {
            "file_path": "/var/log/nginx/access.log",
            "log_group_name": "/webschool/nginx-access",
            "log_stream_name": "{instance_id}",
            "retention_in_days": 14
          },
          {
            "file_path": "/var/log/nginx/error.log",
            "log_group_name": "/webschool/nginx-error",
            "log_stream_name": "{instance_id}",
            "retention_in_days": 14
          }
        ]
      }
    }
  }
}
EOF

sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
    -a fetch-config -m ec2 -s -c "file:${CONFIG_PATH}"

# webschool.service와 동일하게 재부팅 시 자동 시작되도록 활성화
sudo systemctl enable amazon-cloudwatch-agent

echo "CloudWatch Agent 설치/설정 완료."
echo "상태 확인: sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -a status"
echo "에이전트 자체 로그(전송 실패 등 확인용): /opt/aws/amazon-cloudwatch-agent/logs/amazon-cloudwatch-agent.log"
