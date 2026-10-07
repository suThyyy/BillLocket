<#
.SYNOPSIS
    Script chuan bi va khoi chay Demo Seeder cho Monthly Recap thang 9/2026 tren thiet bi Android debug.
#>

[CmdletBinding()]
param(
    [string]$ImageSourceDir = "LOCAL_DEMO/monthly-recap-2026-09/images",
    [switch]$SkipBuild = $false
)

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $RepoRoot

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   BillLocket - Monthly Recap Demo Seeder (Thang 9/2026)  " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Phat hien ADB
$AdbPath = $null

if (Get-Command adb -ErrorAction SilentlyContinue) {
    $AdbPath = (Get-Command adb).Source
}

if (-not $AdbPath -and (Test-Path "$RepoRoot\local.properties")) {
    $localProps = Get-Content "$RepoRoot\local.properties"
    foreach ($line in $localProps) {
        if ($line -match "^sdk\.dir=(.+)$") {
            $rawSdk = $matches[1].Replace('\:', ':').Replace('\\', '\').Trim()
            $candidate = Join-Path $rawSdk "platform-tools\adb.exe"
            if (Test-Path $candidate) {
                $AdbPath = $candidate
                break
            }
        }
    }
}

if (-not $AdbPath) {
    $sdkLocations = @(
        $env:ANDROID_HOME,
        $env:ANDROID_SDK_ROOT,
        "$env:LOCALAPPDATA\Android\Sdk",
        "C:\Users\DELL\AppData\Local\Android\Sdk"
    )
    foreach ($sdk in $sdkLocations) {
        if ($sdk -and (Test-Path "$sdk\platform-tools\adb.exe")) {
            $AdbPath = "$sdk\platform-tools\adb.exe"
            break
        }
    }
}

if (-not $AdbPath -or -not (Test-Path $AdbPath)) {
    Write-Error "Khong tim thay ADB. Vui long kiem tra cau hinh Android SDK trong local.properties."
    exit 1
}

Write-Host "[OK] ADB: $AdbPath" -ForegroundColor Green

# 2. Xac thuc dung 1 thiet bi Android
$devicesOutput = & $AdbPath devices
$deviceLines = @($devicesOutput | Where-Object { $_ -match "\bdevice$" })

if ($deviceLines.Count -eq 0) {
    Write-Error "Khong tim thay thiet bi Android nao ket noi qua ADB. Vui long bat USB Debugging."
    exit 1
}

if ($deviceLines.Count -gt 1) {
    Write-Error "Co nhieu hon 1 thiet bi Android ket noi. Yeu cau chi ket noi dung 1 thiet bi."
    exit 1
}

$firstLine = $deviceLines[0].Trim()
$deviceId = ($firstLine -split "\s+")[0]
Write-Host "[OK] Thiet bi: $deviceId" -ForegroundColor Green

# 3. Kiem tra thu muc anh dau vao
$resolvedImageDir = Join-Path $RepoRoot $ImageSourceDir
if (-not (Test-Path $resolvedImageDir)) {
    Write-Error "Thu muc anh dau vao khong ton tai: $resolvedImageDir"
    exit 1
}

$allFiles = Get-ChildItem -Path $resolvedImageDir -File
$imageFiles = @($allFiles | Where-Object {
    $ext = $_.Extension.ToLower()
    $ext -eq ".jpg" -or $ext -eq ".jpeg" -or $ext -eq ".png" -or $ext -eq ".heic" -or $ext -eq ".heif" -or $ext -eq ".webp"
})

$imageCount = $imageFiles.Count
if ($imageCount -eq 0) {
    Write-Error "Khong tim thay anh (.jpg, .jpeg, .png, .heic, .webp) trong $resolvedImageDir"
    exit 1
}

Write-Host "[OK] Phat hien $imageCount anh hop le trong $resolvedImageDir" -ForegroundColor Green

# 4. Day anh vao thu muc external files tren thiet bi
$TargetDeviceDir = "/sdcard/Android/data/com.billlocket.app/files/monthly-recap-seed"
Write-Host "-> Tao thu muc tren thiet bi: $TargetDeviceDir" -ForegroundColor Yellow
& $AdbPath shell "mkdir -p $TargetDeviceDir"

Write-Host "-> Day anh sang thiet bi qua ADB push..." -ForegroundColor Yellow
& $AdbPath push "$resolvedImageDir/." "$TargetDeviceDir/"
if ($LASTEXITCODE -ne 0) {
    Write-Error "Loi khi day anh sang thiet bi qua ADB push."
    exit 1
}
Write-Host "[OK] Da day anh thanh cong vao $TargetDeviceDir" -ForegroundColor Green

# 5. Build va cap nhat Debug APK
$ApkPath = Join-Path $RepoRoot "app\build\outputs\apk\debug\app-debug.apk"
if (-not $SkipBuild -or -not (Test-Path $ApkPath)) {
    Write-Host "-> Build Debug APK (JDK 17)..." -ForegroundColor Yellow
    $env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
    $env:Path = "C:\Program Files\Java\jdk-17\bin;" + $env:Path
    $gradlew = Join-Path $RepoRoot "gradlew.bat"
    & $gradlew assembleDebug
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Build Debug APK that bai."
        exit 1
    }
}

if (Test-Path $ApkPath) {
    Write-Host "-> Cap nhat ung dung tren thiet bi (adb install -r)..." -ForegroundColor Yellow
    & $AdbPath install -r $ApkPath
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Cai dat Debug APK len thiet bi that bai."
        exit 1
    }
    Write-Host "[OK] Da cap nhat Debug APK len thiet bi." -ForegroundColor Green
}

# 6. Khoi chay MonthlyRecapDemoActivity
$ActivityComponent = "com.billlocket.app/com.billlocket.app.debug.MonthlyRecapDemoActivity"
Write-Host "-> Khoi chay giao dien Demo Seeder tren thiet bi..." -ForegroundColor Yellow
& $AdbPath shell am start -n $ActivityComponent
if ($LASTEXITCODE -ne 0) {
    Write-Error "Khong the khoi chay Activity $ActivityComponent."
    exit 1
}

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "[HOAN THANH] Demo Seeder da san sang tren man hinh thiet bi!" -ForegroundColor Green
Write-Host "1. Kiem tra so luong anh tren man hinh ($imageCount anh)." -ForegroundColor Cyan
Write-Host "2. Bam 'Tao du lieu demo thang 9/2026' tren thiet bi de seed du lieu." -ForegroundColor Cyan
Write-Host "3. Mo BillLocket de kiem tra Home calendar, Stats va Map." -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Green
