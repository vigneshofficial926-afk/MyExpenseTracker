Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "   Launching My Expense Tracker (Interactive Web App)" -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Starting local web server at http://localhost:8085 ..." -ForegroundColor Yellow

$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location "$scriptPath\web"

Start-Process "http://localhost:8085"
py -m http.server 8085
