# Publishes HomeControl Companion as a self-contained single-file build and
# packages it into a Setup.exe with Inno Setup. Run from anywhere; paths
# below are relative to this script's own location.
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectDir = Join-Path $scriptDir "..\src\HomeControl.Companion"
$publishDir = Join-Path $projectDir "publish"
$isccPaths = @(
    "C:\Program Files (x86)\Inno Setup 6\ISCC.exe",
    "C:\Program Files\Inno Setup 6\ISCC.exe",
    "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe"
)

Write-Host "Publishing self-contained build..." -ForegroundColor Cyan
dotnet publish "$projectDir\HomeControl.Companion.csproj" -c Release -r win-x64 `
    --self-contained true `
    -p:PublishSingleFile=true `
    -p:IncludeNativeLibrariesForSelfExtract=true `
    -o "$publishDir"

$iscc = $isccPaths | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $iscc) {
    throw "Inno Setup (ISCC.exe) not found. Install it first: winget install JRSoftware.InnoSetup"
}

Write-Host "Building installer..." -ForegroundColor Cyan
& $iscc "$scriptDir\HomeControlCompanion.iss"

Write-Host "Done — see $scriptDir\output\HomeControlCompanionSetup.exe" -ForegroundColor Green
