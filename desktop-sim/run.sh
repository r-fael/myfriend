#!/bin/sh
# Compila e abre o simulador de PC do myfriend (precisa de JDK 17+).
cd "$(dirname "$0")"
SRC=../app/src/main/java/com/myfriend
rm -rf out && mkdir -p out
javac -encoding UTF-8 -d out $(find shim src -name "*.java") \
  $SRC/PetEngine.java $SRC/PetState.java $SRC/PixelGrid.java $SRC/PetAccessoryRenderer.java $SRC/PetConfig.java $SRC/PetClock.java $SRC/PetSignals.java \
  && java -cp out com.myfriend.sim.DesktopSim sim-prefs.properties
