@echo off
:: Launches install.ps1 with ExecutionPolicy Bypass so it runs regardless
:: of the machine's PowerShell execution policy setting.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1" %*
