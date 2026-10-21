Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "   Launching Vignesh Expense Tracker (Interactive Web App)" -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Starting local web server at http://localhost:8085 ..." -ForegroundColor Yellow

$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location "$scriptPath"

Start-Process "http://localhost:8085"
py serve.py 8085
