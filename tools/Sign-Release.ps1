$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$keyDirectory = Join-Path $env:USERPROFILE '.android/tsuki-release'
$keyFile = Join-Path $keyDirectory 'tsuki-release.p12'
$passwordFile = Join-Path $keyDirectory 'password.dpapi'
$lineageFile = Join-Path $keyDirectory 'debug-to-release.lineage'
$debugKey = Join-Path $env:USERPROFILE '.android/debug.keystore'
$sdkTools = Join-Path $env:LOCALAPPDATA 'Android/Sdk/build-tools/36.0.0'
$apkDirectory = Join-Path $projectRoot 'app/build/outputs/apk/release'
$unsignedApk = Join-Path $apkDirectory 'app-release-unsigned.apk'
$metadataFile = Join-Path $apkDirectory 'output-metadata.json'
foreach ($requiredPath in @($keyFile, $passwordFile, $debugKey, $unsignedApk, $metadataFile)) {
    if (!(Test-Path -LiteralPath $requiredPath -PathType Leaf)) { throw "Missing file: $requiredPath" }
}
$metadata = Get-Content -LiteralPath $metadataFile -Raw | ConvertFrom-Json
if ($metadata.variantName -ne 'release' -or $metadata.applicationId -ne 'cafe.miafetta.tsuki' -or $metadata.elements.Count -ne 1) {
    throw 'Expected a single Tsuki Release APK'
}
$version = $metadata.elements[0].versionName
$versionCode = $metadata.elements[0].versionCode
if ($version -notmatch '^\d+\.\d+\.\d+(?:-[a-zA-Z0-9.]+)?$') { throw 'Invalid Release version' }
$outputDirectory = Join-Path $projectRoot 'build/verification/v1'
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
$alignedApk = Join-Path $outputDirectory "Tsuki-$version-aligned.apk"
$signedApk = Join-Path $outputDirectory "Tsuki-$version-release.apk"
$securePassword = (Get-Content -LiteralPath $passwordFile -Raw).Trim() | ConvertTo-SecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $env:TSUKI_SIGNING_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    $passwordPointer = [IntPtr]::Zero
    if (!(Test-Path -LiteralPath $lineageFile)) {
        # AndroidX's signature permission must retain its owner across the upgrade.
        & "$sdkTools/apksigner.bat" rotate --out $lineageFile --old-signer --ks $debugKey --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --set-installed-data true --set-permission true --set-shared-uid false --set-rollback false --set-auth false --new-signer --ks $keyFile --ks-key-alias tsuki-release --ks-pass env:TSUKI_SIGNING_PASSWORD --key-pass env:TSUKI_SIGNING_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'Signing lineage creation failed' }
    }
    & "$sdkTools/zipalign.exe" -f -p 4 $unsignedApk $alignedApk
    if ($LASTEXITCODE -ne 0) { throw 'APK alignment failed' }
    & "$sdkTools/apksigner.bat" sign --ks $debugKey --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --next-signer --ks $keyFile --ks-key-alias tsuki-release --ks-pass env:TSUKI_SIGNING_PASSWORD --key-pass env:TSUKI_SIGNING_PASSWORD --lineage $lineageFile --rotation-min-sdk-version 33 --debuggable-apk-permitted false --out $signedApk $alignedApk
    if ($LASTEXITCODE -ne 0) { throw 'APK signing failed' }
    foreach ($apiLevel in @(33, 37)) {
        $verification = & "$sdkTools/apksigner.bat" verify --verbose --print-certs --min-sdk-version $apiLevel --max-sdk-version $apiLevel $signedApk
        if ($LASTEXITCODE -ne 0 -or ($verification -join "`n") -notmatch '22e27016a1e62b2ea47d33b35f3c1010b546b9e3625b37ea259820b3fed5bdf3') {
            throw "Release signature verification failed for API $apiLevel"
        }
    }
    & "$sdkTools/zipalign.exe" -c -p 4 $signedApk
    if ($LASTEXITCODE -ne 0) { throw 'Signed APK alignment verification failed' }
    $badging = & "$sdkTools/aapt.exe" dump badging $signedApk
    if ($LASTEXITCODE -ne 0 -or ($badging -join "`n") -match 'application-debuggable' -or
        ($badging -join "`n") -notmatch [regex]::Escape("name='cafe.miafetta.tsuki'") -or
        ($badging -join "`n") -notmatch [regex]::Escape("versionCode='$versionCode'") -or
        ($badging -join "`n") -notmatch [regex]::Escape("versionName='$version'")) { throw 'Release package verification failed' }
    [pscustomobject]@{ APK = $signedApk; Version = $version; SHA256 = (Get-FileHash -LiteralPath $signedApk -Algorithm SHA256).Hash }
} finally {
    if ($passwordPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer) }
    Remove-Item Env:TSUKI_SIGNING_PASSWORD -ErrorAction SilentlyContinue
    $securePassword.Dispose()
}
