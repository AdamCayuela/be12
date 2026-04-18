#!/bin/bash
# ============================================================
# Script de compilation et lancement - Simulation Tour de Piste
# Raspberry Pi - Java 21 (Temurin) + JavaFX 21
# ============================================================

JAVA=/opt/java21/bin/java
JAVAC=/opt/java21/bin/javac
JAVAFX=/opt/javafx-21/lib
LIB=/home/cayuelad/libs/JavaFX17_Obj.jar
SRC=~/be12/JavaApp/src
OUT=~/be12/out

echo ">>> Compilation..."
mkdir -p $OUT

$JAVAC \
  --module-path $JAVAFX \
  --add-modules javafx.controls,javafx.fxml,javafx.graphics \
  -cp $LIB \
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
  -Dprism.order=es2 \
  --module-path $JAVAFX \
  --add-modules javafx.controls,javafx.fxml,javafx.graphics \
  -cp $OUT:$LIB \
  Main
