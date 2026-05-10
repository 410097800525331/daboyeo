param(
    [Parameter(Mandatory = $true)]
    [string]$Provider,
    [string]$BundlePath = "",
    [string]$SiteNo = "",
    [string]$MovieNo = "",
    [string]$ScreeningDate = "",
    [string]$ScreenNo = "",
    [string]$ScreenSequence = "",
    [string]$PlayDate = "",
    [string]$CinemaSelector = "",
    [string]$RepresentationMovieCode = "",
    [int]$SeatCinemaId = 0,
    [int]$SeatScreenId = 0,
    [int]$SeatPlaySequence = 0,
    [int]$SeatScreenDivisionCode = 0,
    [string]$AreaCode = "",
    [string]$SeatPlayScheduleNo = "",
    [string]$SeatBranchNo = "",
    [switch]$Write
)

$ErrorActionPreference = "Stop"

function Resolve-GradleCommand {
    $gradle = Get-Command gradle -ErrorAction SilentlyContinue
    if ($gradle) {
        return $gradle.Source
    }

    $candidate = Get-ChildItem -Path (Join-Path $env:USERPROFILE ".gradle\wrapper\dists") -Filter gradle.bat -Recurse -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName

    if (-not $candidate) {
        throw "Gradle command was not found. Install Gradle or update the fallback search in scripts/ingest_collector_to_tidb.ps1."
    }

    return $candidate
}

$providerCode = $Provider.Trim().ToUpperInvariant()
switch ($providerCode) {
    "CGV" { $providerCode = "CGV" }
    "LOTTE" { $providerCode = "LOTTE_CINEMA" }
    "LOTTE_CINEMA" { $providerCode = "LOTTE_CINEMA" }
    "MEGA" { $providerCode = "MEGABOX" }
    "MEGABOX" { $providerCode = "MEGABOX" }
    default { throw "Unsupported provider: $Provider" }
}

$seatCinemaIdExpr = if ($SeatCinemaId) { "$SeatCinemaId" } else { "None" }
$seatScreenIdExpr = if ($SeatScreenId) { "$SeatScreenId" } else { "None" }
$seatPlaySequenceExpr = if ($SeatPlaySequence) { "$SeatPlaySequence" } else { "None" }
$seatScreenDivisionCodeExpr = if ($SeatScreenDivisionCode) { "$SeatScreenDivisionCode" } else { "None" }

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$backendDir = Join-Path $repoRoot "backend"
$tmpDir = Join-Path $backendDir "build\tmp"
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null

if (-not $BundlePath) {
    switch ($providerCode) {
        "CGV" {
            if (-not $SiteNo -or -not $MovieNo) {
                throw "CGV collection requires -SiteNo and -MovieNo when -BundlePath is not provided."
            }
            $BundlePath = Join-Path $tmpDir ("cgv-bundle-{0}-{1}-{2}.json" -f $SiteNo, $MovieNo, (Get-Date -Format "yyyyMMddHHmmss"))
            $collectScript = @"
import json
from pathlib import Path
from collectors.cgv.collector import CgvCollector

bundle = CgvCollector().collect_bundle(
    site_no="$SiteNo",
    mov_no="$MovieNo",
    scn_ymd="$ScreeningDate" or None,
    scns_no="$ScreenNo" or None,
    scn_sseq="$ScreenSequence" or None,
)
Path(r"$BundlePath").write_text(json.dumps(bundle, ensure_ascii=False, indent=2), encoding="utf-8")
print(r"$BundlePath")
"@
        }
        "LOTTE_CINEMA" {
            if (-not $PlayDate -or -not $CinemaSelector -or -not $RepresentationMovieCode) {
                throw "LOTTE collection requires -PlayDate, -CinemaSelector, and -RepresentationMovieCode when -BundlePath is not provided."
            }
            $BundlePath = Join-Path $tmpDir ("lotte-bundle-{0}-{1}-{2}.json" -f $PlayDate, $RepresentationMovieCode, (Get-Date -Format "yyyyMMddHHmmss"))
            $collectScript = @"
import json
from pathlib import Path
from collectors.lotte.collector import LotteCinemaCollector

bundle = LotteCinemaCollector().collect_bundle(
    play_date="$PlayDate",
    cinema_selector="$CinemaSelector",
    representation_movie_code="$RepresentationMovieCode",
    seat_cinema_id=$seatCinemaIdExpr,
    seat_screen_id=$seatScreenIdExpr,
    seat_play_sequence=$seatPlaySequenceExpr,
    seat_screen_division_code=$seatScreenDivisionCodeExpr,
)
Path(r"$BundlePath").write_text(json.dumps(bundle, ensure_ascii=False, indent=2), encoding="utf-8")
print(r"$BundlePath")
"@
        }
        "MEGABOX" {
            if (-not $PlayDate -or -not $MovieNo -or -not $AreaCode) {
                throw "MEGABOX collection requires -PlayDate, -MovieNo, and -AreaCode when -BundlePath is not provided."
            }
            $BundlePath = Join-Path $tmpDir ("megabox-bundle-{0}-{1}-{2}.json" -f $PlayDate, $MovieNo, (Get-Date -Format "yyyyMMddHHmmss"))
            $seatPlayScheduleNoExpr = if ($SeatPlayScheduleNo) { '"' + $SeatPlayScheduleNo + '"' } else { "None" }
            $seatBranchNoExpr = if ($SeatBranchNo) { '"' + $SeatBranchNo + '"' } else { "None" }
            $collectScript = @"
import json
from pathlib import Path
from collectors.megabox.collector import MegaboxCollector

bundle = MegaboxCollector().collect_bundle(
    play_de="$PlayDate",
    movie_no="$MovieNo",
    area_cd="$AreaCode",
    seat_play_schdl_no=$seatPlayScheduleNoExpr,
    seat_brch_no=$seatBranchNoExpr,
)
Path(r"$BundlePath").write_text(json.dumps(bundle, ensure_ascii=False, indent=2), encoding="utf-8")
print(r"$BundlePath")
"@
        }
    }

    Push-Location $repoRoot
    try {
        $collectScript | python -
    } finally {
        Pop-Location
    }
}

$resolvedBundlePath = Resolve-Path $BundlePath
$gradleCommand = Resolve-GradleCommand
$env:GRADLE_USER_HOME = Join-Path $backendDir ".gradle-user-home"
$gradleArgs = @("ingestCollectorBundle", "-Pprovider=$providerCode", "-PbundlePath=$resolvedBundlePath")
if ($Write) {
    $gradleArgs += "-Pwrite=true"
} else {
    Write-Host "Dry-run mode. Add -Write to upsert into TiDB."
}

Push-Location $backendDir
try {
    & $gradleCommand @gradleArgs
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
} finally {
    Pop-Location
}
