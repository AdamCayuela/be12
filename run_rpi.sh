#!/bin/bash
# ============================================================
# Script de compilation et lancement - Simulation Tour de Piste
# Raspberry Pi - Java 23 + JavaFX 25
# ============================================================

JAVA=/opt/java23/bin/java
JAVAC=/opt/java23/bin/javac
JAVAFX=/opt/javafx-25/lib
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
