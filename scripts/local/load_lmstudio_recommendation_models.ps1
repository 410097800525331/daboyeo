param(
    [string]$FastModel = "gemma-4-e2b-it",
    [string]$PreciseModel = "gemma-4-e4b-it",
    [int]$ContextLength = 2048,
    [int]$Parallel = 1,
    [int]$TtlSeconds = 3600,
    [string]$Gpu = "max",
    [int]$ServerPort = 1234,
    [string]$BindAddress = "127.0.0.1",
    [switch]$SkipServerStart,
    [switch]$EnableCors
)

$ErrorActionPreference = "Stop"

if (-not $SkipServerStart) {
    $serverArguments = @("server", "start", "--port", "$ServerPort", "--bind", $BindAddress)
    if ($EnableCors) {
        $serverArguments += "--cors"
    }
    & lms @serverArguments
}

function Load-RecommendationModel {
    param([string]$Model)

    Write-Host "Loading $Model with context=$ContextLength parallel=$Parallel ttl=$TtlSeconds gpu=$Gpu"
    $arguments = @(
        "load",
        $Model,
        "--context-length",
        "$ContextLength",
        "--parallel",
        "$Parallel",
        "--ttl",
        "$TtlSeconds",
        "--gpu",
        $Gpu,
        "--yes"
    )
    & lms @arguments
}

Load-RecommendationModel -Model $FastModel
Load-RecommendationModel -Model $PreciseModel
& lms ps
