#!/bin/bash
# ============================================================
# Script de compilation et lancement - Simulation Tour de Piste
# Raspberry Pi - Java 23 + JavaFX 25 + pi4j (gpiod)
#
# Architecture MVC :
#   src/modele/     → logique métier (Simulation, Aeronef…)
#   src/vue/        → vues JavaFX (Vue3D, VuePanneauControle)
#   src/controleur/ → contrôleur principal (ControleurPrincipal)
#   src/parseur/    → parseurs de fichiers
# ============================================================

JAVA=/opt/java23/bin/java
JAVAC=/opt/java23/bin/javac
JAVAFX=/opt/javafx-25/lib
PI4J=/opt/pi4j/lib
LIB=/opt/libs/JavaFX17_Obj.jar
SRC=~/be12/JavaApp/src
OUT=~/be12/out

# Classpath : lib perso + pi4j (GPIO + I2C) + slf4j
CP=$LIB:$PI4J/pi4j-core.jar:$PI4J/pi4j-plugin-gpiod.jar:$PI4J/pi4j-library-gpiod.jar:$PI4J/pi4j-plugin-linuxfs.jar:$PI4J/pi4j-library-linuxfs.jar:$(find /opt/pi4j /usr/share/java -name "slf4j*.jar" 2>/dev/null | tr '\n' ':')

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
