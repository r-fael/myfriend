@echo off
rem Compila e abre o simulador de PC do myfriend (precisa de JDK 17+).
cd /d "%~dp0"
set SRC=..\app\src\main\java\com\myfriend
if exist out rmdir /s /q out
mkdir out
dir /s /b shim\*.java src\*.java > sources.txt
echo %SRC%\PetEngine.java>> sources.txt
echo %SRC%\PetState.java>> sources.txt
echo %SRC%\PixelGrid.java>> sources.txt
echo %SRC%\PetAccessoryRenderer.java>> sources.txt
echo %SRC%\PetConfig.java>> sources.txt
echo %SRC%\PetClock.java>> sources.txt
echo %SRC%\PetSignals.java>> sources.txt
javac -encoding UTF-8 -d out @sources.txt || (pause & exit /b 1)
del sources.txt
start "" javaw -cp out com.myfriend.sim.DesktopSim sim-prefs.properties
