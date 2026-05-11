param(
  [string]$EnvPath = ".env",
  [string]$JarPath = "backend\build\libs\daboyeo-backend-0.1.0-SNAPSHOT.jar",
  [string]$BridgeScriptPath = "scripts\ai_bridge_agent.py",
  [string]$RemoteDir = "/opt/daboyeo",
  [string]$ServiceName = "daboyeo",
  [string]$GradleCommand = "gradle",
  [string]$ShowtimeSyncCron = "0 0 * * * *",
  [switch]$Build,
  [switch]$DryRun,
  [switch]$SkipHealthCheck,
  [switch]$SkipCollectorRuntimeUpload
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Step {
  param([string]$Message)
  Write-Host "[deploy] $Message"
}

function Read-DotEnv {
  param([string]$Path)

  if (-not (Test-Path -LiteralPath $Path)) {
    throw "env file not found: $Path"
  }

  $values = @{}
  Get-Content -LiteralPath $Path | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
      $key = $matches[1]
      $value = $matches[2].Trim()
      if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
        $value = $value.Substring(1, $value.Length - 2)
      }
      $values[$key] = $value
    }
  }

  return $values
}

function Require-EnvKey {
  param(
    [hashtable]$Values,
    [string]$Key
  )

  if (-not $Values.ContainsKey($Key) -or [string]::IsNullOrWhiteSpace([string]$Values[$Key])) {
    throw "missing or blank env key: $Key"
  }

  return [string]$Values[$Key]
}

function Assert-SafeRemotePath {
  param(
    [string]$Value,
    [string]$Name
  )

  if ($Value -notmatch '^/[A-Za-z0-9._/-]+$' -or $Value.Contains('//') -or $Value.Contains('/../')) {
    throw "unsafe remote $Name path: $Value"
  }
}

function Assert-SafeServiceName {
  param([string]$Value)

  if ($Value -notmatch '^[A-Za-z0-9_.@-]+$') {
    throw "unsafe service name: $Value"
  }
}

function Convert-ToShellSingleQuotedLiteral {
  param([string]$Value)
  if ($Value.Contains("'")) {
    throw "single quote is not allowed in remote shell literal."
  }
  return "'$Value'"
}

function New-SanitizedDeployEnv {
  param(
    [string]$SourcePath,
    [string]$OutputPath,
    [string]$HourlyCron
  )

  $excluded = @("ORACLE_HOST", "ORACLE_USER", "ORACLE_SSH_KEY_PATH")
  $forced = [ordered]@{
    "DABOYEO_SYNC_ENABLED" = "true"
    "DABOYEO_SYNC_PYTHON" = "python3"
    "DABOYEO_SHOWTIME_SYNC_ENABLED" = "true"
    "DABOYEO_SHOWTIME_SYNC_CRON" = '"' + $HourlyCron + '"'
    "DABOYEO_SHOWTIME_STARTUP_ENABLED" = "false"
    "DABOYEO_PUBLIC_COLLECTION_ENABLED" = "false"
    "DABOYEO_PUBLIC_NEARBY_REFRESH_ENABLED" = "true"
    "DABOYEO_PUBLIC_SEAT_LAYOUT_ENABLED" = "false"
    "DABOYEO_NEARBY_REFRESH_RATE_LIMIT_PER_MINUTE" = "20"
    "DABOYEO_SHOWTIME_NEARBY_REFRESH_RADIUS_KM" = "8"
  }
  $writtenForced = @{}
  $lines = New-Object System.Collections.Generic.List[string]

  Get-Content -LiteralPath $SourcePath | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=') {
      $key = $matches[1]
      if ($excluded -contains $key) {
        return
      }
      if ($forced.Contains($key)) {
        $lines.Add("$key=$($forced[$key])")
        $writtenForced[$key] = $true
        return
      }
    }
    $lines.Add($_)
  }

  foreach ($key in $forced.Keys) {
    if (-not $writtenForced.ContainsKey($key)) {
      $lines.Add("$key=$($forced[$key])")
    }
  }

  [System.IO.File]::WriteAllLines((Resolve-Path -LiteralPath (Split-Path -Parent $OutputPath)).Path + "\" + (Split-Path -Leaf $OutputPath), $lines, [System.Text.UTF8Encoding]::new($false))
}

function Assert-CollectorRuntimePaths {
  $requiredPaths = @("collectors", "scripts\ingest")
  foreach ($path in $requiredPaths) {
    if (-not (Test-Path -LiteralPath $path)) {
      throw "collector runtime path not found: $path"
    }
  }
}

function New-CollectorRuntimeArchive {
  param([string]$OutputPath)

  Assert-CollectorRuntimePaths
  $paths = @("collectors", "scripts/ingest")
  if (Test-Path -LiteralPath "requirements.txt") {
    $paths += "requirements.txt"
  }

  & tar -czf $OutputPath @paths
  if ($LASTEXITCODE -ne 0) {
    throw "package collector runtime failed with exit code $LASTEXITCODE"
  }
}

function Invoke-Checked {
  param(
    [string]$Label,
    [scriptblock]$Command
  )

  Write-Step $Label
  & $Command
  if ($LASTEXITCODE -ne 0) {
    throw "$Label failed with exit code $LASTEXITCODE"
  }
}

function Invoke-PublicHealthChecks {
  param(
    [string]$HostName,
    [int]$Attempts = 6,
    [int]$DelaySeconds = 5
  )

  for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
    try {
      Write-Step "check public health attempt $attempt/$Attempts"
      $health = Invoke-RestMethod -Uri "http://$HostName/api/health" -TimeoutSec 20
      Write-Step "public health status=$($health.status)"

      $providers = Invoke-WebRequest -Uri "http://$HostName/api/recommendation/providers/health" -UseBasicParsing -TimeoutSec 20
      $codexStatus = if ($providers.Content -match '"provider":"codex".*?"status":"([^"]+)"') { $matches[1] } else { "unknown" }
      Write-Step "provider codex status=$codexStatus"
      return
    } catch {
      if ($attempt -ge $Attempts) {
        throw
      }
      Write-Step "public health not ready yet; retrying in ${DelaySeconds}s"
      Start-Sleep -Seconds $DelaySeconds
    }
  }
}

$envValues = Read-DotEnv -Path $EnvPath
$oracleHost = Require-EnvKey -Values $envValues -Key "ORACLE_HOST"
$oracleUser = Require-EnvKey -Values $envValues -Key "ORACLE_USER"
$sshKeyPath = Require-EnvKey -Values $envValues -Key "ORACLE_SSH_KEY_PATH"

if ($oracleHost -match '\s' -or $oracleUser -match '\s') {
  throw "ORACLE_HOST and ORACLE_USER must not contain whitespace."
}

if (-not (Test-Path -LiteralPath $sshKeyPath)) {
  throw "Oracle SSH key path does not exist."
}

Assert-SafeRemotePath -Value $RemoteDir -Name "deploy"
Assert-SafeServiceName -Value $ServiceName

$recommendProvider = if ($envValues.ContainsKey("DABOYEO_RECOMMEND_PROVIDER")) { [string]$envValues["DABOYEO_RECOMMEND_PROVIDER"] } else { "<missing>" }
$bridgeTokenState = if ($envValues.ContainsKey("DABOYEO_AI_BRIDGE_TOKEN") -and -not [string]::IsNullOrWhiteSpace([string]$envValues["DABOYEO_AI_BRIDGE_TOKEN"])) { "present" } else { "missing" }
$bridgeServerState = if ($envValues.ContainsKey("DABOYEO_BRIDGE_SERVER") -and -not [string]::IsNullOrWhiteSpace([string]$envValues["DABOYEO_BRIDGE_SERVER"])) { "present" } else { "missing" }

Write-Step "local env ok: provider=$recommendProvider bridge_token=$bridgeTokenState bridge_server=$bridgeServerState"

if ($Build) {
  if ($DryRun) {
    Write-Step "dry-run: would run backend bootJar with $GradleCommand"
  } else {
    Invoke-Checked -Label "build backend bootJar" -Command { & $GradleCommand -p backend bootJar }
  }
}

if (-not (Test-Path -LiteralPath $JarPath)) {
  throw "jar not found: $JarPath"
}

if (-not (Test-Path -LiteralPath $BridgeScriptPath)) {
  throw "bridge script not found: $BridgeScriptPath"
}

$target = "$oracleUser@$oracleHost"
$sshArgs = @("-o", "StrictHostKeyChecking=accept-new", "-i", $sshKeyPath, $target)
$scpArgs = @("-o", "StrictHostKeyChecking=accept-new", "-i", $sshKeyPath)

New-Item -ItemType Directory -Force -Path ".local" | Out-Null
$deployEnvPath = Join-Path ".local" "daboyeo.deploy.env"
$collectorArchivePath = Join-Path ".local" "daboyeo.collector-runtime.tar.gz"

try {
  New-SanitizedDeployEnv -SourcePath $EnvPath -OutputPath $deployEnvPath -HourlyCron $ShowtimeSyncCron
  Write-Step "sanitized env prepared: ORACLE_* keys excluded; hourly showtime sync and bounded public nearby refresh enforced"
  if (-not $SkipCollectorRuntimeUpload) {
    Assert-CollectorRuntimePaths
    Write-Step "collector runtime paths verified"
  }

  if ($DryRun) {
    Write-Step "dry-run: would upload env, jar, bridge script, and collector runtime to configured Oracle target"
    Write-Step "dry-run: would detect remote service user and restart $ServiceName"
    if (-not $SkipHealthCheck) {
      Write-Step "dry-run: would check public health and provider health"
    }
    return
  }

  if (-not $SkipCollectorRuntimeUpload) {
    New-CollectorRuntimeArchive -OutputPath $collectorArchivePath
    Write-Step "collector runtime archive prepared"
  }

  Invoke-Checked -Label "upload sanitized env" -Command { & scp @scpArgs $deployEnvPath "${target}:/tmp/daboyeo.env" }
  Invoke-Checked -Label "upload app jar" -Command { & scp @scpArgs $JarPath "${target}:/tmp/daboyeo-app.jar" }
  Invoke-Checked -Label "upload bridge worker" -Command { & scp @scpArgs $BridgeScriptPath "${target}:/tmp/ai_bridge_agent.py" }
  if (-not $SkipCollectorRuntimeUpload) {
    Invoke-Checked -Label "upload collector runtime" -Command { & scp @scpArgs $collectorArchivePath "${target}:/tmp/daboyeo-collector-runtime.tar.gz" }
  }

  $remoteDirLiteral = Convert-ToShellSingleQuotedLiteral -Value $RemoteDir
  $serviceLiteral = Convert-ToShellSingleQuotedLiteral -Value $ServiceName
  $remoteInstall = @"
set -e
remote_dir=$remoteDirLiteral
service_name=$serviceLiteral
owner=`$(systemctl show -p User --value "`$service_name" 2>/dev/null || true)
if [ -z "`$owner" ]; then
  owner=`$(stat -c '%U' "`$remote_dir")
fi
group=`$(id -gn "`$owner" 2>/dev/null || stat -c '%G' "`$remote_dir")
sudo install -m 600 -o "`$owner" -g "`$group" /tmp/daboyeo.env "`$remote_dir/.env"
sudo install -m 640 -o "`$owner" -g "`$group" /tmp/daboyeo-app.jar "`$remote_dir/app.jar"
sudo install -d -o "`$owner" -g "`$group" "`$remote_dir/scripts"
sudo install -m 750 -o "`$owner" -g "`$group" /tmp/ai_bridge_agent.py "`$remote_dir/scripts/ai_bridge_agent.py"
if [ -f /tmp/daboyeo-collector-runtime.tar.gz ]; then
  sudo tar -xzf /tmp/daboyeo-collector-runtime.tar.gz -C "`$remote_dir"
  sudo chown -R "`$owner:`$group" "`$remote_dir/collectors" "`$remote_dir/scripts/ingest"
  if [ -f "`$remote_dir/requirements.txt" ]; then
    sudo chown "`$owner:`$group" "`$remote_dir/requirements.txt"
    sudo chmod 640 "`$remote_dir/requirements.txt"
  fi
  sudo find "`$remote_dir/collectors" "`$remote_dir/scripts/ingest" -type d -exec chmod 750 {} +
  sudo find "`$remote_dir/collectors" "`$remote_dir/scripts/ingest" -type f -exec chmod 640 {} +
fi
sudo systemctl restart "`$service_name"
status=`$(sudo systemctl is-active "`$service_name")
printf 'remote_owner=%s\n' "`$owner"
printf 'service_status=%s\n' "`$status"
"@

  $remoteInstall = $remoteInstall -replace "`r`n", "`n"
  Invoke-Checked -Label "install and restart remote service" -Command { & ssh @sshArgs $remoteInstall }

  if (-not $SkipHealthCheck) {
    Invoke-PublicHealthChecks -HostName $oracleHost
  }
} finally {
  if (Test-Path -LiteralPath $deployEnvPath) {
    Remove-Item -LiteralPath $deployEnvPath -Force
    Write-Step "local sanitized env removed"
  }
  if (Test-Path -LiteralPath $collectorArchivePath) {
    Remove-Item -LiteralPath $collectorArchivePath -Force
    Write-Step "local collector runtime archive removed"
  }
}
