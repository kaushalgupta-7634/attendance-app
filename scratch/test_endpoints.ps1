# test_endpoints.ps1
$baseUrl = "http://localhost:8080"

# 1. Login
$loginBody = @{
    username = "teacher1"
    password = "password123"
} | ConvertTo-Json

Write-Host "Logging in as teacher1..."
$loginResponse = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$token = $loginResponse.token
Write-Host "Token obtained: $token"

# 2. Start Session
$sessionBody = @{
    className = "CS101"
    subject = "Computer Science"
    startTime = $null
    endTime = $null
    classroomLat = 0.0
    classroomLng = 0.0
    radiusMeters = 500.0
} | ConvertTo-Json

Write-Host "Starting session..."
$sessionResponse = Invoke-RestMethod -Uri "$baseUrl/sessions/start" -Method Post -Body $sessionBody -ContentType "application/json" -Headers @{ Authorization = "Bearer $token" }
$sessionId = $sessionResponse.id
$isActive = $sessionResponse.active
Write-Host "Session started with ID: $sessionId, Active: $isActive"

# 3. Get QR Code
Write-Host "Fetching QR code..."
try {
    $qrResponse = Invoke-WebRequest -Uri "$baseUrl/sessions/$sessionId/qr" -Method Get -Headers @{ Authorization = "Bearer $token" }
    Write-Host "QR Code Status: $($qrResponse.StatusCode)"
    Write-Host "Content Type: $($qrResponse.Headers['Content-Type'])"
    Write-Host "Content Length: $($qrResponse.Content.Length) bytes"
} catch {
    Write-Host "Error fetching QR code:"
    Write-Host $_
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $errBody = $reader.ReadToEnd()
        Write-Host "Response body: $errBody"
    }
}
