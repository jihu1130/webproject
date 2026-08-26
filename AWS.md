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

- [x] 집 컴퓨터에 AWS CLI 설치 (winget, `aws-cli/2.36.29`)
- [x] 위 IAM 링크에서 `webschool-deploy` 사용자용 새 액세스 키 발급
      (학원에서 만들었던 키는 폐기 예정 — 새로 발급)
- [x] `aws configure`로 CLI 인증 설정 (Access Key / Secret / 리전
      `ap-northeast-2` / 출력형식 `json`) — `aws sts get-caller-identity`로
      `webschool-deploy` 계정 확인됨
- [x] EC2 인스턴스 생성
  - AMI: Amazon Linux 2023 (`ami-0159816442b921a1c`)
  - 인스턴스 유형: `t3.micro`
  - 리전: `ap-northeast-2` (서울)
  - 보안 그룹: `webschool-sg`(`sg-0cfa1334493058db0`) — 인바운드 `8888`/`80`/`443`만
    열림, `22`(SSH)는 열지 않음. 키페어 없이 생성(SSM 전용, 학원 등 다른
    컴퓨터에서도 AWS CLI 자격증명만 있으면 접속 가능하게 하는 결정)
  - IAM 역할: `webschool-ec2-ssm-role`(`AmazonSSMManagedInstanceCore` 정책)
    + 인스턴스 프로필 `webschool-ec2-ssm-profile`
- [x] `aws ssm start-session --target <인스턴스ID>`로 접속 확인 — SSM 에이전트
      Online 확인 + `send-command`로 원격 명령 실행 성공(`whoami` → `root`).
      대화형 접속용 Session Manager Plugin도 로컬에 설치 완료
      (`winget install Amazon.SessionManagerPlugin`)
- [x] Java 21 설치 (`sudo dnf install -y java-21-amazon-corretto`, Corretto 21.0.12)
- [x] MySQL 8 설치 + `webschool` 데이터베이스 생성 + 강한 root 비밀번호 설정
      (`mysql-community-server` 8.0.46, MySQL 공식 yum repo 추가해서 설치 —
      AL2023 기본 repo엔 없음)
- [x] 프로젝트 빌드: `./gradlew bootJar` (로컬에서 빌드) — **서버에서 git
      clone은 실패함**(레포가 private이라 인증 필요, 서버에 GitHub 토큰을
      두고 싶지 않아서 포기). 대신 로컬 빌드 → S3(`webschool-deploy-938436186735`
      버킷, `webschool.jar`) 업로드 → EC2가 `webschool-ec2-ssm-role`에 붙인
      스코프 제한 S3 읽기 정책으로 다운로드하는 방식 사용. 코드 갱신할 때마다
      이 순서(로컬 빌드 → S3 업로드 → 서버에서 재다운로드 → 서비스 재시작)
      반복하면 됨(4~5단계에서 자동화 예정)
- [x] 서버용 `application.yml` 작성 (`/opt/webschool/application.yml`, 권한 600) —
      로컬 파일과 마찬가지로 git에 올리지 않음. 구글 OAuth 블록은 일부러 제외함
      (리디렉션 URI가 `localhost` 전용으로 등록돼 있어서 IP로 접속하는 지금
      단계에선 어차피 동작 안 함 — 4단계 도메인+HTTPS 확정되면 그때 추가)
- [x] `systemd` 서비스 등록 (`/etc/systemd/system/webschool.service`,
      재부팅 시 자동 시작 확인됨, `Restart=always`로 크래시 시 자동 재시작)
- [x] 보안 그룹의 `8888` 포트로 공인 IP 접속 확인 — Elastic IP
      `54.180.206.13`으로 `/actuator/health` 200, `/` 200 확인됨 (2026-08-26)

### ⚠️ t3.micro 메모리 부족(OOM) 사고 — 겪은 문제와 해결

배포 당일 겪은 문제라 기록해둠. **t2.micro/t3.micro는 RAM 1GB인데, Amazon
Linux 2023은 "실제 메모리가 800MB보다 크면 zram 스왑을 자동으로 설정하지
않는" 정책이라(`zram0: system has too much memory (913MB), limit is 800MB,
ignoring` 로그) 프리티어 인스턴스엔 기본적으로 스왑이 전혀 없다.** MySQL +
Spring Boot(Hibernate/Security/Thymeleaf 다 올라가는 무거운 앱)를 동시에
띄우자마자 메모리가 바닥나 인스턴스 전체가 응답 불능 상태(SSM 명령도 몇
분째 `Pending`)가 됐고, 재부팅해도 부팅 직후 두 서비스가 동시에 다시
자동 시작되며 똑같이 멈추는 상황이 반복됐다.

**해결**: EC2 user-data에 `#cloud-boothook`(매 부팅마다 실행되는 cloud-init
스크립트, 일반 user-data와 달리 최초 부팅 1회로 안 끝남)으로 1GB 스왑파일을
자동 생성하도록 설정. 그리고 `webschool.service`의 `ExecStart`에 JVM 힙을
`-Xms128m -Xmx350m -Xss512k -XX:MaxMetaspaceSize=180m -XX:+UseSerialGC
-XX:TieredStopAtLevel=1`로 제한(기본 힙/GC 설정은 이 정도 메모리에서 감당이
안 됨 — SerialGC가 G1보다 관리 오버헤드가 훨씬 적어서 이런 저사양 환경에
적합). 두 조치 이후로는 재부팅만으로 MySQL→앱 순서로 자동 기동되고 메모리도
안정적(스왑 100~200MB 선에서 유지, OOM 재발 안 함).

**주의할 것**: 이후에도 메모리 관련 이상 증상(SSM 명령이 몇 분째 `Pending`,
외부 접속 안 됨)이 보이면 가장 먼저 `free -h`로 스왑이 살아있는지 확인할 것
— `/swapfile`이 사라졌거나(디스크 공간 부족 등) `swapon`이 안 걸려있으면
같은 사고가 재발한다. 서비스 하나라도 더 무거워지면(이미지 처리 등)
`t3.small`(RAM 2GB) 업그레이드를 고려.

**EC2 stop/start 시 퍼블릭 IP가 바뀐다는 것도 이번에 직접 겪음**(reboot과
달리 stop 후 start는 새 IP 할당) — 그래서 Elastic IP(`54.180.206.13`,
`eipalloc-0d9d2d109408e5a95`)를 할당해 인스턴스에 고정 연결함(러닝 중인
인스턴스에 붙어있는 동안은 무료). 앞으로 IP는 이 값 그대로 유지됨 — 4단계
도메인 연결 전까지는 이 IP를 기준으로 접속.

## 완료 후 기록할 것

- EC2 인스턴스 ID: `i-0992a58038aca44da`
- EC2 퍼블릭 IP(Elastic IP, 고정): `54.180.206.13`
  (`eipalloc-0d9d2d109408e5a95`)
- 배포일: 2026-08-26
- 사용한 AMI/인스턴스 타입: Amazon Linux 2023 (`ami-0159816442b921a1c`) /
  `t3.micro`
- 보안 그룹: `webschool-sg` (`sg-0cfa1334493058db0`)
- IAM 역할/인스턴스 프로필: `webschool-ec2-ssm-role` / `webschool-ec2-ssm-profile`
  (SSM 정책 + S3 아티팩트 읽기 전용 정책 `webschool-s3-artifact-read`)
- 배포 아티팩트 S3 버킷: `webschool-deploy-938436186735` (퍼블릭 접근 완전
  차단, 인스턴스 역할만 읽기 가능)
- 앱 경로: 서버의 `/opt/webschool/webschool.jar` +
  `/opt/webschool/application.yml`(권한 600), 업로드 파일 저장 위치는
  `/opt/uploads`
- 접속 확인: `http://54.180.206.13:8888/actuator/health` → `{"status":"UP"}`,
  `http://54.180.206.13:8888/` → 200

## 다음에 할 일

- 2단계(DB 백업 cron화), 3단계(S3로 업로드 파일 이전)는 아직 안 함
- 4단계(도메인+HTTPS)까지는 구글 로그인 비활성 상태로 둘 것 — 리디렉션 URI가
  `localhost`로 등록돼 있어서 지금은 붙여도 어차피 실패함
- 코드 갱신 시 수동 절차: 로컬에서 `./gradlew bootJar` →
  `aws s3 cp build/libs/webschool-0.0.1-SNAPSHOT.jar
  s3://webschool-deploy-938436186735/webschool.jar` → SSM으로 서버 접속해
  `sudo aws s3 cp s3://webschool-deploy-938436186735/webschool.jar
  /opt/webschool/webschool.jar` 후 `sudo systemctl restart webschool`
