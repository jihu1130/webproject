# webschool dev PC setup (Windows 10/11) - WSL2 + Docker Desktop + AWS CLI v2 + Session Manager plugin + JDK 21
# Written after the 2026-09-27 PC reformat. Run from an ADMIN PowerShell:
#   powershell -ExecutionPolicy Bypass -File scripts\setup-dev-windows.ps1
# Then: reboot -> start Docker Desktop once (accept its license) -> open a NEW terminal and run
#   aws configure            (IAM user webschool-deploy, region ap-northeast-2, output json - see docs/AWS.md)
#   git config --global user.name  "jihu1130"
#   git config --global user.email "163612182+jihu1130@users.noreply.github.com"
# Messages are English on purpose: Windows PowerShell 5.1 reads BOM-less .ps1 files as ANSI.

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'   # makes Invoke-WebRequest much faster on PS 5.1

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) { Write-Host 'Run this from an Administrator PowerShell.' -ForegroundColor Red; exit 1 }

$dl = Join-Path $env:TEMP 'webschool-setup'
New-Item -ItemType Directory -Force $dl | Out-Null

function Get-Installer($url, $name) {
    $path = Join-Path $dl $name
    if (-not (Test-Path $path)) {
        Write-Host "Downloading $name ..."
        Invoke-WebRequest -Uri $url -OutFile $path -UseBasicParsing
    }
    return $path
}

# 1. WSL2 (Docker Desktop backend) - enables the Windows features, needs a reboot.
#    BIOS virtualization (SVM/AMD-V or VT-x) must be ON - see docs/todo.md (Docker stage 1) for the symptom.
Write-Host '[1/5] WSL2' -ForegroundColor Cyan
wsl.exe --install --no-distribution
wsl.exe --set-default-version 2 2>$null

# 2. AWS CLI v2
Write-Host '[2/5] AWS CLI v2' -ForegroundColor Cyan
if (-not (Test-Path 'C:\Program Files\Amazon\AWSCLIV2\aws.exe')) {
    $msi = Get-Installer 'https://awscli.amazonaws.com/AWSCLIV2.msi' 'AWSCLIV2.msi'
    Start-Process msiexec.exe -ArgumentList '/i', "`"$msi`"", '/qn', '/norestart' -Wait
} else { Write-Host 'already installed' }

# 3. Session Manager plugin (for 'aws ssm start-session' / DB port forwarding)
Write-Host '[3/5] Session Manager plugin' -ForegroundColor Cyan
if (-not (Test-Path 'C:\Program Files\Amazon\SessionManagerPlugin\bin\session-manager-plugin.exe')) {
    $exe = Get-Installer 'https://s3.amazonaws.com/session-manager-downloads/plugin/latest/windows/SessionManagerPluginSetup.exe' 'SessionManagerPluginSetup.exe'
    Start-Process $exe -ArgumentList '/quiet', '/norestart' -Wait
} else { Write-Host 'already installed' }

# 4. Docker Desktop (WSL2 backend). License is accepted by you on first launch.
Write-Host '[4/5] Docker Desktop' -ForegroundColor Cyan
if (-not (Test-Path 'C:\Program Files\Docker\Docker\Docker Desktop.exe')) {
    $exe = Get-Installer 'https://desktop.docker.com/win/main/amd64/Docker%20Desktop%20Installer.exe' 'DockerDesktopInstaller.exe'
    Start-Process $exe -ArgumentList 'install', '--quiet', '--backend=wsl-2' -Wait
} else { Write-Host 'already installed' }
try { Add-LocalGroupMember -Group 'docker-users' -Member $env:USERNAME -ErrorAction Stop } catch { }

# 5. JDK 21 (Eclipse Temurin) - per-user zip into ~/.jdks (IntelliJ auto-detects this folder),
#    checksum-verified, then user-level JAVA_HOME + PATH. Only needed for ./gradlew / IntelliJ;
#    'docker compose up --build' builds inside a container and does not need it.
Write-Host '[5/5] JDK 21' -ForegroundColor Cyan
$jdks = Join-Path $env:USERPROFILE '.jdks'
$existing = Get-ChildItem $jdks -Directory -ErrorAction SilentlyContinue | Where-Object Name -like 'jdk-21*' | Select-Object -First 1
if (-not $existing) {
    $meta = Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
    $pkg = $meta[0].binary.package
    $zip = Get-Installer $pkg.link $pkg.name
    if ((Get-FileHash $zip -Algorithm SHA256).Hash.ToLower() -ne $pkg.checksum) { throw "JDK checksum mismatch: $zip" }
    New-Item -ItemType Directory -Force $jdks | Out-Null
    Expand-Archive $zip -DestinationPath $jdks -Force
    $existing = Get-ChildItem $jdks -Directory | Where-Object Name -like 'jdk-21*' | Sort-Object Name -Descending | Select-Object -First 1
} else { Write-Host "already installed: $($existing.FullName)" }
[Environment]::SetEnvironmentVariable('JAVA_HOME', $existing.FullName, 'User')
$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
if ($userPath -notlike "*$($existing.FullName)\bin*") {
    [Environment]::SetEnvironmentVariable('Path', ("$($existing.FullName)\bin;" + $userPath).TrimEnd(';'), 'User')
}

Write-Host ''
Write-Host 'Done. REBOOT now, then start Docker Desktop once and accept the license.' -ForegroundColor Green
Write-Host 'After reboot, in a new terminal: aws configure / git config (see the header of this script).'
