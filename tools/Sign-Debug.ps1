param([ValidateSet('debug', 'test')][string]$Kind = 'debug', [string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$keyDirectory = Join-Path $env:USERPROFILE '.android/tsuki-release'
$keyFile = Join-Path $keyDirectory 'tsuki-release.p12'
$passwordFile = Join-Path $keyDirectory 'password.dpapi'
$lineageFile = Join-Path $keyDirectory 'debug-to-release.lineage'
$debugKey = Join-Path $env:USERPROFILE '.android/debug.keystore'
$sdkTools = Join-Path $env:LOCALAPPDATA 'Android/Sdk/build-tools/36.0.0'

if (!(Test-Path -LiteralPath $lineageFile -PathType Leaf)) { throw 'Existing signing lineage required' }
if (!$OutputDirectory) { $OutputDirectory = Join-Path $projectRoot 'build/verification/debug-signing' }
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
foreach ($kind in @($Kind)) {
    $unsignedApk = if ($kind -eq 'debug') { Join-Path $projectRoot 'app/build/outputs/apk/debug/app-debug.apk' } else { Join-Path $projectRoot 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk' }
    $alignedApk = Join-Path $outputDirectory "Tsuki-$kind-aligned.apk"
    $signedApk = Join-Path $outputDirectory "Tsuki-$kind.apk"
    $securePassword = (Get-Content -LiteralPath $passwordFile -Raw).Trim() | ConvertTo-SecureString
    $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    try {
        $env:TSUKI_SIGNING_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
        $passwordPointer = [IntPtr]::Zero
        & "$sdkTools/zipalign.exe" -f -p 4 $unsignedApk $alignedApk
        if ($LASTEXITCODE -ne 0) { throw 'APK alignment failed' }
        & "$sdkTools/apksigner.bat" sign --ks $debugKey --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --next-signer --ks $keyFile --ks-key-alias tsuki-release --ks-pass env:TSUKI_SIGNING_PASSWORD --key-pass env:TSUKI_SIGNING_PASSWORD --lineage $lineageFile --rotation-min-sdk-version 33 --debuggable-apk-permitted true --out $signedApk $alignedApk
        if ($LASTEXITCODE -ne 0) { throw 'APK signing failed' }
        foreach ($apiLevel in @(33, 37)) {
            $verification = & "$sdkTools/apksigner.bat" verify --verbose --print-certs --min-sdk-version $apiLevel --max-sdk-version $apiLevel $signedApk
            if ($LASTEXITCODE -ne 0 -or ($verification -join "`n") -notmatch '22e27016a1e62b2ea47d33b35f3c1010b546b9e3625b37ea259820b3fed5bdf3') {
                throw "Release signature verification failed for API $apiLevel"
            }
        }
        & "$sdkTools/zipalign.exe" -c -p 4 $signedApk
        if ($LASTEXITCODE -ne 0) { throw 'Signed APK alignment verification failed' }
        Write-Output "Verified $kind APK signature and alignment"
    } finally {
        if ($passwordPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer) }
        Remove-Item Env:TSUKI_SIGNING_PASSWORD -ErrorAction SilentlyContinue
        $securePassword.Dispose()
    }
}
