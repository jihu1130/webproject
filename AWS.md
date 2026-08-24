# AWS.md — 배포 작업 기록

이 문서는 webschool을 AWS에 배포하는 작업을 추적하는 문서다. **이 파일에는 절대
액세스 키/시크릿 키 같은 실제 자격증명 값을 적지 않는다** — 그런 값은 항상
로컬 전용 파일(`application.yml`, `~/.aws/credentials`)에만 두고 git에 올라가는
이 문서에는 IP·리소스 ID·설정값 같은 비밀 아닌 정보만 기록한다.

## 참고 링크
- IAM 액세스 키 관리: https://us-east-1.console.aws.amazon.com/iam/home?region=ap-northeast-2#/users/details/webschool-deploy/create-access-key
- AWS 요금 안내: https://aws.amazon.com/ko/pricing/

## 계정 정보
- AWS 계정 ID: `938436186735`
- 기본 리전: `ap-northeast-2` (서울)
- IAM 사용자: `webschool-deploy` (`AdministratorAccess` 정책 연결, 2026-08-24 생성)
- 가입 플랜: 무료 (6개월, 최대 200 USD 크레딧)

## ⚠️ 반드시 지킬 것 — 자격증명 보관 위치

**AWS CLI 자격증명 설정(`aws configure`)은 개인 컴퓨터(집)에서만 진행한다.**
학원 등 여러 사람이 같이 쓰고 매일 초기화되는 공용 컴퓨터에는 액세스 키를
절대 저장하지 않는다.

- 2026-08-24에 학원 공용 컴퓨터에서 실수로 `~/.aws/credentials`에 액세스 키를
  저장했다가, 공용 컴퓨터라는 걸 뒤늦게 확인하고 즉시 파일을 삭제한 사건이
  있었다. 이미 디스크에 한 번 쓰인 키라 안전하게 재발급하는 게 원칙.
- 같은 날 git 히스토리(최초 커밋)에 `application.yml`이 한 번 커밋됐던 이력이
  발견되어(MySQL root 비밀번호 `1234`, 예전 NEIS API 키 평문 노출) 두 값 모두
  교체 완료함(레포는 면접용이라 히스토리 자체는 보존, 값만 무효화).
- 이유: 액세스 키가 유출되면 공격자가 그 계정으로 서버를 마음대로 띄워서
  거액의 요금이 청구되는 사고가 실제로 발생한다.

## EC2 접속 방식 — SSM 권장 (SSH 포트 개방 안 함)

집↔학원을 오가며 개발하다 보니 SSH를 IP 화이트리스트로 여는 방식은 매번 보안
그룹을 수정해야 해서 번거롭다. 대신 **AWS Systems Manager Session Manager**를
쓰면:
- 보안 그룹에 22번 포트 규칙 자체가 필요 없음(SSH 포트 완전히 닫아둠)
- 인증은 IP가 아니라 AWS 로그인 자격(IAM)으로 하므로 접속 장소가 바뀌어도
  설정 변경 불필요
- 단, 접속하는 컴퓨터에도 AWS CLI + 자격증명이 설정돼 있어야 함 → 위 원칙과
  마찬가지로 **개인 컴퓨터에서만** 사용
- EC2 인스턴스에 `AmazonSSMManagedInstanceCore` 정책이 붙은 IAM 역할을
  연결해야 동작함(인스턴스 생성 시 같이 설정)

## 배포 로드맵 (5단계, 의존성 순서로 진행)

`todo.md`에서 "호스팅 프로바이더 결정 전까지 보류"였던 항목들 — AWS로 결정되며
착수 가능해짐.

1. **클라우드 서버 환경 구축** (EC2) — 지금 이 단계
2. **데이터베이스 운영 및 백업 환경 구축** — EC2에 MySQL 설치, 기존
   `scripts/backup-db.ps1`을 리눅스 cron 기반으로 재작성
3. **파일 저장소 분리** (S3) — 게시글 이미지가 지금은 로컬 디스크
   (`../uploads`)에 저장 중, 인스턴스 교체 시 유실 위험 → 서비스 안정화 후 이전
4. **HTTPS 적용 및 보안 설정 강화** — 도메인 + nginx + Let's Encrypt, 구글
   OAuth 리디렉션 URI를 실제 도메인으로 갱신 필요(`todo.md` "0. 소셜 로그인"
   섹션 참고 — 한 글자만 달라도 `redirect_uri_mismatch`)
5. **CI/CD 자동 배포 환경 구축** — 1~4단계를 수동으로 익힌 뒤 마지막에 자동화

## 1단계 체크리스트 — EC2 서버 구축

- [ ] 집 컴퓨터에 AWS CLI 설치
- [ ] 위 IAM 링크에서 `webschool-deploy` 사용자용 새 액세스 키 발급
      (학원에서 만들었던 키는 폐기 예정 — 새로 발급)
- [ ] `aws configure`로 CLI 인증 설정 (Access Key / Secret / 리전
      `ap-northeast-2` / 출력형식 `json`)
- [ ] EC2 인스턴스 생성
  - AMI: Amazon Linux 2023
  - 인스턴스 유형: `t2.micro` 또는 `t3.micro` (프리티어 대상)
  - 리전: `ap-northeast-2` (서울)
  - 보안 그룹: 인바운드 `8888`(앱 직접 테스트용), `80`/`443`(4단계 nginx용)만
    열기 — `22`(SSH)는 열지 않음
  - IAM 역할: `AmazonSSMManagedInstanceCore` 정책 붙은 역할 연결(SSM 접속용)
- [ ] `aws ssm start-session --target <인스턴스ID>`로 접속 확인
- [ ] Java 21 설치 (`sudo dnf install java-21-amazon-corretto` 등)
- [ ] MySQL 8 설치 + `webschool` 데이터베이스 생성 + 강한 root 비밀번호 설정
- [ ] 프로젝트 빌드: `./gradlew bootJar` (로컬에서 빌드 후 업로드, 또는
      서버에서 git clone 후 직접 빌드)
- [ ] 서버용 `application.yml` 작성 — 로컬 파일과 마찬가지로 **git에 올리지
      않음**, DB 비밀번호/NEIS 키/구글 자격증명은 서버에만 존재
- [ ] `systemd` 서비스 등록 (재부팅 시 자동 시작, 크래시 시 자동 재시작)
- [ ] 보안 그룹의 `8888` 포트로 공인 IP 접속 확인
      (`http://<EC2 공인 IP>:8888`)

## 완료 후 기록할 것 (진행하면서 채워나가기)

- EC2 인스턴스 ID:
- EC2 퍼블릭 IP:
- 배포일:
- 사용한 AMI/인스턴스 타입:
