param(
    [string]$HostIp = "127.0.0.1",
    [int]$WebPort = 5173,
    [int]$ApiPort = 8080
)

$ErrorActionPreference = "Stop"

function Test-Request {
    param(
        [string]$Name,
        [string]$Uri,
        [string]$Method = "GET",
        [int[]]$ExpectedStatus = @(200)
    )

    try {
        $response = Invoke-WebRequest -Uri $Uri -Method $Method -UseBasicParsing -TimeoutSec 10
        $status = [int]$response.StatusCode
    } catch {
        $status = 0
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            $status = [int]$_.Exception.Response.StatusCode
        } else {
            Write-Host "[FAIL] $Name -> $($_.Exception.Message)"
            return $false
        }
    }

    if ($ExpectedStatus -contains $status) {
        Write-Host "[OK]   $Name -> HTTP $status"
        return $true
    }

    Write-Host "[FAIL] $Name -> HTTP $status, expected $($ExpectedStatus -join '/')"
    return $false
}

function Test-WebSocket {
    param(
        [string]$Name,
        [string]$Uri
    )

    $socket = [System.Net.WebSockets.ClientWebSocket]::new()
    $cancellation = [System.Threading.CancellationTokenSource]::new()
    $cancellation.CancelAfter([TimeSpan]::FromSeconds(10))
    try {
        $socket.ConnectAsync([Uri]$Uri, $cancellation.Token).GetAwaiter().GetResult()
        if ($socket.State -eq [System.Net.WebSockets.WebSocketState]::Open) {
            Write-Host "[OK]   $Name -> connected"
            return $true
        }
        Write-Host "[FAIL] $Name -> state $($socket.State)"
        return $false
    } catch {
        Write-Host "[FAIL] $Name -> $($_.Exception.Message)"
        return $false
    } finally {
        if ($socket.State -eq [System.Net.WebSockets.WebSocketState]::Open) {
            $socket.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, "health-check", [System.Threading.CancellationToken]::None).GetAwaiter().GetResult()
        }
        $socket.Dispose()
        $cancellation.Dispose()
    }
}

$webBase = "http://$HostIp`:$WebPort"
$webSocketBase = "ws://$HostIp`:$WebPort"
$apiBase = "http://127.0.0.1:$ApiPort"
$checks = @()

$checks += Test-Request -Name "Frontend login page" -Uri "$webBase/login" -ExpectedStatus @(200)
$checks += Test-Request -Name "Backend health direct" -Uri "$apiBase/api/health" -ExpectedStatus @(200)
$checks += Test-Request -Name "Backend health through Vite proxy" -Uri "$webBase/api/health" -ExpectedStatus @(200)
$checks += Test-Request -Name "Upload API through Vite proxy" -Uri "$webBase/api/uploads/images" -Method "POST" -ExpectedStatus @(401, 403, 400)
$checks += Test-WebSocket -Name "Realtime WebSocket through Vite proxy" -Uri "$webSocketBase/ws/realtime"

if ($checks -contains $false) {
    Write-Host ""
    Write-Host "Dev health check failed. Check whether backend 8080 and frontend 5173 are running, and whether Vite proxy points to 127.0.0.1:8080."
    exit 1
}

Write-Host ""
Write-Host "Dev health check passed."
