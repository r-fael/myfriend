@echo off
rem Cria a chave fixa de assinatura (rode uma vez so). Detalhes no criar-chave.ps1.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0criar-chave.ps1"
pause
