#!/bin/bash
# ============================================================
# Script de compilation et lancement - Simulation Tour de Piste
# Raspberry Pi - Java 21 (Temurin) + JavaFX 17
# ============================================================

JAVA=/opt/java21/bin/java
JAVAC=/opt/java21/bin/javac
JAVAFX=/opt/javafx-17/lib
SRC=~/be12/JavaApp/src
OUT=~/be12/out

echo ">>> Compilation..."
mkdir -p $OUT

$JAVAC \
  --module-path $JAVAFX \
  --add-modules javafx.controls,javafx.fxml,javafx.graphics \
  -d $OUT \
  $(find $SRC -name "*.java")

if [ $? -ne 0 ]; then
  echo "!!! Erreur de compilation. Arret."
  exit 1
fi

echo ">>> Copie des ressources..."
cp -r $SRC/resources $OUT/resources

echo ">>> Lancement..."
$JAVA \
  --module-path $JAVAFX \
  --add-modules javafx.controls,javafx.fxml,javafx.graphics \
  -cp $OUT \
  Main
