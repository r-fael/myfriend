@echo off
rem Compila e instala o myfriend no relogio. Veja as opcoes no topo do instalar.ps1.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0instalar.ps1" %*
