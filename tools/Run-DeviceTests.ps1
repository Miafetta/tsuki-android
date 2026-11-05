<#
.SYNOPSIS
Builds, signs and installs the Debug and AndroidTest packages, runs device scenarios and writes one log per scenario.

.DESCRIPTION
Scenario names map to instrumentation arguments read by WidgetRenderInstrumentation.onCreate and AppUiVerification.run.
A custom scenario may be given as key=value. The script never uninstalls, clears data, changes system settings or
saves accounts or widget configuration. Use -WhatIf to print the commands without touching the device.
Keep the device unlocked with the screen on: a locked screen stalls the instrumentation until the timeout.

.PARAMETER Serial
adb serial of the target device; required when more than one device is connected.

.PARAMETER Scenarios
Scenario names from the catalog below, or custom key=value pairs passed to the instrumentation.

.PARAMETER SkipInstall
Run against the Debug and AndroidTest packages that are already installed.

.PARAMETER RestoreRelease
Path of a signed release APK to reinstall after the run, for example build/verification/v1/Tsuki-<version>-release.apk.

.PARAMETER PullArtifacts
Copy the screenshots, recordings and logs the scenarios write on the device during this run into the output directory.
Files left by earlier runs stay on the device.
#>
[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$Serial,
    [string[]]$Scenarios = @('render', 'appUi', 'lifecycle', 'read-lifecycle', 'session-restore', 'widget-editor',
        'widget-navigation', 'top-refresh', 'errors', 'menu', 'dock', 'page-swipe'),
    [string]$OutputDirectory,
    [switch]$SkipInstall,
    [string]$RestoreRelease,
    [switch]$PullArtifacts,
    [int]$TimeoutMinutes = 30
)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
$projectRoot = Split-Path -Parent $PSScriptRoot
$package = 'cafe.miafetta.tsuki'
$runner = "$package.test/$package.widget.WidgetRenderInstrumentation"
if (!$OutputDirectory) { $OutputDirectory = Join-Path $projectRoot "build/verification/device-$(Get-Date -Format 'yyyyMMdd-HHmm')" }
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory, (Get-Location).Path)
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
if (!(Test-Path -LiteralPath $adb -PathType Leaf)) { $adb = (Get-Command adb -ErrorAction Stop).Source }
$adbPrefix = if ($Serial) { @('-s', $Serial) } else { @() }

# Arguments are key/value pairs passed with am instrument -e; 'render' runs the default RemoteViews render cases.
$catalog = [ordered]@{
    'render' = @()
    'appUi' = @('appUi', 'true')
    'lifecycle' = @('lifecycle', 'true')
    'widgetStartup' = @('widgetStartup', 'true')
    'comparison' = @('comparison', 'true')
}
# widget-editor-save and widget-editor-change are opt-in: they save an existing desktop instance. The change scenario edits the
# palette and role, then restores the original configuration and verifies it.
foreach ($name in @('read-lifecycle', 'session-restore', 'widget-editor', 'widget-editor-save', 'widget-editor-change',
    'widget-navigation', 'top-refresh', 'errors', 'menu', 'secondary-glass', 'dock-tone', 'page-swipe', 'nav-corner',
    'dock', 'visual', 'probe', 'baseline', 'theme-header')) {
    $catalog[$name] = @('redesignV4', $name)
}

function Invoke-Adb([string[]]$Arguments) {
    $output = & $adb @adbPrefix @Arguments 2>&1 | ForEach-Object { "$_" }
    [pscustomobject]@{ Code = $LASTEXITCODE; Text = ($output -join "`n") }
}

function Get-ScenarioArguments([string]$Name) {
    if ($catalog.Contains($Name)) { return , $catalog[$Name] }
    if ($Name -match '^([A-Za-z][A-Za-z0-9]*)=(.+)$') { return , @($Matches[1], $Matches[2]) }
    throw "Unknown scenario: $Name"
}

function Read-Instrumentation([string]$Text) {
    $results = [ordered]@{}
    $code = $null
    $key = $null
    foreach ($line in $Text -split "`r?`n") {
        if ($line -match '^INSTRUMENTATION_RESULT: ([^=]+)=(.*)$') {
            $key = $Matches[1]
            $results[$key] = $Matches[2]
        } elseif ($line -match '^INSTRUMENTATION_CODE: (-?\d+)') {
            $code = [int]$Matches[1]
            $key = $null
        } elseif ($line -match '^INSTRUMENTATION_') {
            $key = $null
        } elseif ($key) {
            $results[$key] += "`n$line"
        }
    }
    [pscustomobject]@{ Code = $code; Results = $results }
}

function Get-InstrumentArguments([string[]]$Pairs) {
    $arguments = @('shell', 'am', 'instrument', '-w', '-r')
    for ($index = 0; $index -lt $Pairs.Count; $index += 2) { $arguments += @('-e', $Pairs[$index], $Pairs[$index + 1]) }
    $arguments + $runner
}

function Invoke-Scenario([string]$Name, [string[]]$Pairs) {
    $arguments = Get-InstrumentArguments $Pairs
    $log = Join-Path $OutputDirectory "$Name.log"
    $start = [Diagnostics.ProcessStartInfo]::new($adb)
    foreach ($argument in @($adbPrefix + $arguments)) { $start.ArgumentList.Add($argument) }
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.StandardOutputEncoding = [Text.Encoding]::UTF8
    $start.StandardErrorEncoding = [Text.Encoding]::UTF8
    $start.UseShellExecute = $false
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $process = [Diagnostics.Process]::Start($start)
    $stdout = $process.StandardOutput.ReadToEndAsync()
    $stderr = $process.StandardError.ReadToEndAsync()
    $finished = $process.WaitForExit($TimeoutMinutes * 60000)
    if (!$finished) { $process.Kill() }
    $process.WaitForExit()
    $text = $stdout.Result + $stderr.Result
    Set-Content -LiteralPath $log -Encoding utf8 -Value "> adb $(($adbPrefix + $arguments) -join ' ')`n`n$text"
    $parsed = Read-Instrumentation $text
    $values = $parsed.Results
    $detail = @($values['failure'], $values['result'], $values['stream'], $values['shortMsg'], $values['surfaceOptions']) |
        Where-Object { $_ } | Select-Object -First 1
    $status = if (!$finished) { 'FAIL' }
        elseif ($Name -like 'widget-editor*' -and "$detail" -match 'Requires an existing (configured )?widget') { 'SKIP' }
        elseif ($parsed.Code -eq -1 -and !$values.Contains('failure') -and
            ($values.Contains('result') -or $values.Contains('surfaceOptions') -or "$($values['stream'])" -match '^PASS')) { 'PASS' }
        else { 'FAIL' }
    $reason = if (!$finished) { "Timed out after $TimeoutMinutes minutes" }
        elseif ($status -eq 'SKIP') { 'No placed Tsuki widget instance' }
        elseif ($detail) { ("$detail" -split "`n")[0].Trim() }
        else { "INSTRUMENTATION_CODE=$($parsed.Code)" }
    [pscustomobject]@{ Scenario = $Name; Status = $status; Seconds = [int]$watch.Elapsed.TotalSeconds; Reason = $reason }
}

if ($PSCmdlet.ShouldProcess($OutputDirectory, 'Create output directory')) {
    New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
}
$plan = foreach ($name in $Scenarios) { [pscustomobject]@{ Name = $name; Pairs = (Get-ScenarioArguments $name) } }
if ($RestoreRelease) {
    if (!(Test-Path -LiteralPath $RestoreRelease -PathType Leaf)) { throw "Missing release APK: $RestoreRelease" }
    $RestoreRelease = (Resolve-Path -LiteralPath $RestoreRelease).Path
}
if ($PSCmdlet.ShouldProcess('device', 'Check adb connection')) {
    $state = Invoke-Adb @('get-state')
    if ($state.Code -ne 0 -or $state.Text.Trim() -ne 'device') { throw "No authorized device: $($state.Text)" }
}
if (!$SkipInstall) {
    if ($PSCmdlet.ShouldProcess('app', 'gradlew :app:assembleDebug :app:assembleDebugAndroidTest')) {
        & (Join-Path $projectRoot 'gradlew.bat') -p $projectRoot :app:assembleDebug :app:assembleDebugAndroidTest *>&1 |
            Set-Content -LiteralPath (Join-Path $OutputDirectory 'build.log') -Encoding utf8
        if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed; see build.log' }
    }
    foreach ($kind in @('debug', 'test')) {
        $apk = Join-Path $OutputDirectory "Tsuki-$kind.apk"
        if ($PSCmdlet.ShouldProcess($apk, "Sign-Debug.ps1 -Kind $kind")) {
            & (Join-Path $PSScriptRoot 'Sign-Debug.ps1') -Kind $kind -OutputDirectory $OutputDirectory
        }
        if ($PSCmdlet.ShouldProcess($apk, 'adb install -r')) {
            $install = Invoke-Adb @('install', '-r', $apk)
            if ($install.Code -ne 0 -or $install.Text -notmatch 'Success') { throw "Install failed for ${kind}: $($install.Text)" }
        }
    }
}
$artifactRoot = "/sdcard/Android/data/$package/files"
$runMarker = '/data/local/tmp/tsuki-device-tests.marker'
if ($PullArtifacts -and $PSCmdlet.ShouldProcess($runMarker, 'Mark the run start for artifact selection')) {
    Invoke-Adb @('shell', "touch $runMarker") | Out-Null
}
$summary = foreach ($entry in $plan) {
    if ($PSCmdlet.ShouldProcess($entry.Name, "adb $((Get-InstrumentArguments $entry.Pairs) -join ' ')")) {
        $result = Invoke-Scenario $entry.Name $entry.Pairs
        Write-Host ("{0,-18} {1} {2}s {3}" -f $result.Scenario, $result.Status, $result.Seconds, $result.Reason)
        $result
    }
}
if ($PullArtifacts -and $PSCmdlet.ShouldProcess($artifactRoot, 'adb pull files written during this run')) {
    # Only this run's files: earlier runs may have left captures that are not part of this result.
    $listing = Invoke-Adb @('shell', "find $artifactRoot -type f -newer $runMarker")
    $files = $listing.Text -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_.StartsWith("$artifactRoot/") }
    $pullLog = foreach ($file in $files) {
        $target = Join-Path $OutputDirectory "device-files/$($file.Substring($artifactRoot.Length + 1))"
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
        (Invoke-Adb @('pull', '-a', $file, $target)).Text
    }
    Invoke-Adb @('shell', "rm -f $runMarker") | Out-Null
    Set-Content -LiteralPath (Join-Path $OutputDirectory 'pull.log') -Encoding utf8 -Value (@("$($files.Count) file(s)") + $pullLog)
}
if ($RestoreRelease) {
    if ($PSCmdlet.ShouldProcess($RestoreRelease, 'adb install -r; dumpsys package')) {
        $install = Invoke-Adb @('install', '-r', $RestoreRelease)
        if ($install.Code -ne 0 -or $install.Text -notmatch 'Success') { throw "Release restore failed: $($install.Text)" }
        # Right after an install the package manager can briefly report the package without its block; retry before failing.
        $block = ''
        for ($attempt = 1; $attempt -le 5 -and !$block; $attempt++) {
            if ($attempt -gt 1) { Start-Sleep -Seconds 1 }
            $dump = (Invoke-Adb @('shell', 'dumpsys', 'package', $package)).Text
            $block = [regex]::Match($dump, "(?ms)^\s*Package \[$([regex]::Escape($package))\].*?(?=^\s*Package \[|^\S|\z)").Value
        }
        $flags = ($block -split "`n") -match '^\s*(pkg)?[Ff]lags=\['
        $version = ($block -split "`n") -match '^\s*version(Code|Name)='
        Set-Content -LiteralPath (Join-Path $OutputDirectory 'installed-release.txt') -Encoding utf8 -Value (
            "SHA256 $((Get-FileHash -LiteralPath $RestoreRelease -Algorithm SHA256).Hash)`n$($version -join "`n")`n$($flags -join "`n")")
        if (!$block -or !$flags -or ($flags -join ' ') -match 'DEBUGGABLE') { throw 'Restored package is missing or still DEBUGGABLE' }
        Write-Host "Restored release: $(($version | ForEach-Object Trim) -join ' ')"
    }
}
if ($summary) {
    $table = @('| Scenario | Status | Seconds | Reason |', '| --- | --- | --- | --- |') +
        ($summary | ForEach-Object { "| $($_.Scenario) | $($_.Status) | $($_.Seconds) | $($_.Reason -replace '\|', '/') |" })
    Set-Content -LiteralPath (Join-Path $OutputDirectory 'summary.md') -Encoding utf8 -Value $table
    Write-Host ($summary | Format-Table -AutoSize -Wrap | Out-String)
    if ($summary.Status -contains 'FAIL') { exit 1 }
}
