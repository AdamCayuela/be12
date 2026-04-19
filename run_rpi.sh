#!/bin/bash
# ============================================================
# Script de compilation et lancement - Simulation Tour de Piste
# Raspberry Pi - Java 23 + JavaFX 25 + pi4j (gpiod)
# ============================================================

JAVA=/opt/java23/bin/java
JAVAC=/opt/java23/bin/javac
JAVAFX=/opt/javafx-25/lib
PI4J=/opt/pi4j/lib
LIB=/opt/libs/JavaFX17_Obj.jar
SRC=~/be12/JavaApp/src
OUT=~/be12/out

# Classpath : lib perso + pi4j (core + gpiod plugin + library gpiod)
CP=$LIB:$PI4J/pi4j-core.jar:$PI4J/pi4j-plugin-gpiod.jar:$PI4J/pi4j-library-gpiod.jar

echo ">>> Compilation..."
mkdir -p $OUT

$JAVAC \
  --module-path $JAVAFX \
  --add-modules javafx.controls,javafx.fxml,javafx.graphics \
  -cp $CP \
  -d $OUT \
  $(find $SRC -name "*.java")

if [ $? -ne 0 ]; then
  echo "!!! Erreur de compilation. Arret."
  exit 1
fi

echo ">>> Copie des ressources..."
cp -r $SRC/resources $OUT/resources

echo ">>> Lancement..."
export EGL_PLATFORM=x11
$JAVA \
  -Dprism.order=es2 \
  -Dprism.forceGPU=true \
  --module-path $JAVAFX \
  --add-modules javafx.controls,javafx.fxml,javafx.graphics \
  -cp $OUT:$CP \
  Main
