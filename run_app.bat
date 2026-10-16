@echo off
echo ========================================================
echo    Launching My Expense Tracker (Interactive Web App)
echo ========================================================
echo.
echo Starting local web server at http://localhost:8085 ...
start "" http://localhost:8085
cd /d "%~dp0web"
py -m http.server 8085
pause
