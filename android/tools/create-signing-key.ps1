param(
    [string]$KeystorePath = "$env:USERPROFILE\Documents\PayamNegar\payam-negar-release.jks",
    [string]$Alias = "payam-negar-v1"
)
$ErrorActionPreference = 'Stop'
$directory = Split-Path -Parent $KeystorePath
New-Item -ItemType Directory -Force -Path $directory | Out-Null
if (Test-Path $KeystorePath) {
    throw "Keystore already exists: $KeystorePath. Do not overwrite an existing release key."
}
Write-Host "Creating a private Android signing key at: $KeystorePath"
Write-Host "Keytool will prompt you for a strong password. Keep the keystore and password private and backed up."
keytool -genkeypair -v -keystore $KeystorePath -alias $Alias -keyalg RSA -keysize 3072 -validity 10000
if ($LASTEXITCODE -ne 0) { throw "keytool failed with exit code $LASTEXITCODE" }
Write-Host "Keystore created. Configure PAYAM_RELEASE_* in your user-level Gradle properties; never commit the keystore or passwords."
