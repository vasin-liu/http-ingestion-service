@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0health-check.ps1" %*
exit /b %ERRORLEVEL%
