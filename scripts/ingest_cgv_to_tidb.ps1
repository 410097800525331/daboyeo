param(
    [string]$BundlePath = "",
    [string]$SiteNo = "",
    [string]$MovieNo = "",
    [string]$ScreeningDate = "",
    [string]$ScreenNo = "",
    [string]$ScreenSequence = "",
    [switch]$Write
)

$forwardParams = @{
    Provider = "CGV"
}

if ($BundlePath) {
    $forwardParams.BundlePath = $BundlePath
}
if ($SiteNo) {
    $forwardParams.SiteNo = $SiteNo
}
if ($MovieNo) {
    $forwardParams.MovieNo = $MovieNo
}
if ($ScreeningDate) {
    $forwardParams.ScreeningDate = $ScreeningDate
}
if ($ScreenNo) {
    $forwardParams.ScreenNo = $ScreenNo
}
if ($ScreenSequence) {
    $forwardParams.ScreenSequence = $ScreenSequence
}
if ($Write) {
    $forwardParams.Write = $true
}

& (Join-Path $PSScriptRoot "ingest_collector_to_tidb.ps1") @forwardParams
