@echo off
echo ========================================================
echo    Launching Vignesh Expense Tracker (Interactive Web App)
echo ========================================================
echo.
echo Starting local web server at http://localhost:8085 ...
start "" http://localhost:8085
cd /d "%~dp0"
py serve.py 8085
pause
