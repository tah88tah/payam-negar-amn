param(
    [Parameter(Mandatory = $true)]
    [string]$ApkPath,

    [string]$Serial,

    [string]$PackageName = "ir.tahmuresi.payamnegar.debug"
)

$ErrorActionPreference = "Stop"

$adbCommand = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adbCommand) {
    throw "ADB پیدا نشد. Android SDK Platform-Tools را نصب کن و مسیر platform-tools را به PATH اضافه کن."
}

$resolvedApk = Resolve-Path -LiteralPath $ApkPath -ErrorAction Stop
if ([IO.Path]::GetExtension($resolvedApk.Path) -ne ".apk") {
    throw "فایل ورودی باید APK باشد."
}

$deviceLines = @(& adb devices | Select-Object -Skip 1 | Where-Object { $_.Trim() -ne "" })
$authorized = @($deviceLines | Where-Object { $_ -match "\sdevice(\s|$)" })

if ($Serial) {
    $selected = @($authorized | Where-Object { ($_ -split "\s+")[0] -eq $Serial })
    if ($selected.Count -ne 1) {
        throw "گوشی با Serial مشخص‌شده پیدا نشد یا مجوز USB debugging را تأیید نکرده است."
    }
} else {
    if ($authorized.Count -eq 0) {
        throw "هیچ گوشی مجاز و متصل پیدا نشد. USB debugging را فعال و پیام تأیید روی گوشی را قبول کن."
    }
    if ($authorized.Count -gt 1) {
        throw "چند گوشی متصل است. برای جلوگیری از نصب روی دستگاه اشتباه، پارامتر -Serial را مشخص کن."
    }
    $Serial = ($authorized[0] -split "\s+")[0]
}

Write-Host "گوشی انتخاب‌شده: $Serial"
Write-Host "بسته: $PackageName"
Write-Host "نصب/به‌روزرسانی بدون حذف داده‌های برنامه..."

& adb -s $Serial install -r -g $resolvedApk.Path
if ($LASTEXITCODE -ne 0) {
    throw "نصب ناموفق بود. برنامه حذف نشده؛ خطای ADB را بررسی کن. اگر خطای امضا دیدی، APK با کلید متفاوتی امضا شده است."
}

$installed = (& adb -s $Serial shell pm list packages $PackageName) -join "
"
if ($installed -notmatch [regex]::Escape("package:$PackageName")) {
    throw "فرمان نصب موفق گزارش شد، اما بسته در فهرست برنامه‌های نصب‌شده پیدا نشد."
}

Write-Host ""
Write-Host "نصب تمام شد. وضعیت مجوزهای SMS را در خود گوشی بررسی کن:"
Write-Host "Settings > Apps > پیام‌نگار امن > Permissions > SMS"
Write-Host "در صورت پشتیبانی دستگاه، ADB باید مجوزهای محدودشدهٔ درخواستی را allowlist و مجوزهای runtime را اعطا کند."
Write-Host "این اسکریپت برنامهٔ قبلی را uninstall نمی‌کند و در صورت ناسازگاری امضا، نصب را متوقف می‌کند."
