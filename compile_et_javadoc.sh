#!/bin/bash
# ============================================================
#  compile_et_javadoc.sh
#  Compilation + génération Javadoc — Simulation Tour de Piste
#  Raspberry Pi — Java 23 + JavaFX 25 + pi4j (gpiod)
#
#  Usage :
#    chmod +x compile_et_javadoc.sh
#    ./compile_et_javadoc.sh           # compile + javadoc + lance
#    ./compile_et_javadoc.sh --doc     # compile + javadoc seulement (pas de lancement)
#    ./compile_et_javadoc.sh --run     # lance sans recompiler ni regénérer la doc
#
#  Architecture MVC du projet :
#    src/modele/      → logique métier (Simulation, Aeronef, Gestionnaires…)
#    src/vue/         → vues JavaFX (Vue3D, VuePanneauControle)
#    src/controleur/  → contrôleur principal (ControleurPrincipal)
#    src/parseur/     → lecteurs de fichiers de données
# ============================================================

# ─── Chemins (adapter si vos dossiers diffèrent) ─────────────────────────────
JAVA=/opt/java23/bin/java
JAVAC=/opt/java23/bin/javac
JAVADOC=/opt/java23/bin/javadoc

JAVAFX=/opt/javafx-25/lib
PI4J=/opt/pi4j/lib
LIB=/opt/libs/JavaFX17_Obj.jar

SRC=~/be12/JavaApp/src
OUT=~/be12/out
DOC=~/be12/docs

# ─── Classpath (lib perso + pi4j + slf4j) ────────────────────────────────────
CP=$LIB\
:$PI4J/pi4j-core.jar\
:$PI4J/pi4j-plugin-gpiod.jar\
:$PI4J/pi4j-library-gpiod.jar\
:$PI4J/pi4j-plugin-linuxfs.jar\
:$PI4J/pi4j-library-linuxfs.jar\
:$(find /opt/pi4j /usr/share/java -name "slf4j*.jar" 2>/dev/null | tr '\n' ':')

# ─── Options Javadoc ─────────────────────────────────────────────────────────
DOC_TITLE="Simulation Tour de Piste — Documentation API"
DOC_PACKAGES="modele:vue:controleur:parseur"

# ─── Analyse des arguments ───────────────────────────────────────────────────
MODE_DOC=true
MODE_COMPILE=true
MODE_RUN=true

for arg in "$@"; do
  case "$arg" in
    --doc)  MODE_RUN=false  ;;
    --run)  MODE_COMPILE=false; MODE_DOC=false ;;
    --help|-h)
      echo "Usage : $0 [--doc | --run]"
      echo "  (aucun argument) : compile + javadoc + lance l'application"
      echo "  --doc            : compile + javadoc uniquement (pas de lancement)"
      echo "  --run            : lance l'application déjà compilée (pas de compilation)"
      exit 0 ;;
  esac
done

# ═════════════════════════════════════════════════════════════════════════════
#  ÉTAPE 1 — COMPILATION
# ═════════════════════════════════════════════════════════════════════════════
if $MODE_COMPILE; then
  echo ""
  echo "┌─────────────────────────────────────────┐"
  echo "│  ÉTAPE 1/3 — Compilation Java            │"
  echo "└─────────────────────────────────────────┘"
  mkdir -p "$OUT"

  $JAVAC \
    --module-path "$JAVAFX" \
    --add-modules javafx.controls,javafx.fxml,javafx.graphics \
    -cp "$CP" \
    -d "$OUT" \
    -encoding UTF-8 \
    $(find "$SRC" -name "*.java")

  if [ $? -ne 0 ]; then
    echo ""
    echo "✗ Erreur de compilation. Abandon."
    exit 1
  fi
  echo "✓ Compilation réussie → $OUT"

  echo ""
  echo "  Copie des ressources (CSS, images)…"
  cp -r "$SRC/resources" "$OUT/resources" 2>/dev/null && echo "  ✓ Ressources copiées."
fi

# ═════════════════════════════════════════════════════════════════════════════
#  ÉTAPE 2 — GÉNÉRATION JAVADOC
# ═════════════════════════════════════════════════════════════════════════════
if $MODE_DOC; then
  echo ""
  echo "┌─────────────────────────────────────────┐"
  echo "│  ÉTAPE 2/3 — Génération Javadoc          │"
  echo "└─────────────────────────────────────────┘"
  mkdir -p "$DOC"

  # Recenser tous les fichiers sources
  SOURCES=$(find "$SRC" -name "*.java")

  $JAVADOC \
    --module-path "$JAVAFX" \
    --add-modules javafx.controls,javafx.fxml,javafx.graphics \
    -cp "$CP" \
    -d "$DOC" \
    -encoding UTF-8 \
    -docencoding UTF-8 \
    -charset UTF-8 \
    -windowtitle "$DOC_TITLE" \
    -doctitle "<h1>$DOC_TITLE</h1>" \
    -header "<b>Simulation Tour de Piste — BE12</b>" \
    -footer "<i>Architecture MVC — IUT Projet BE12</i>" \
    -author \
    -version \
    -use \
    -splitindex \
    -noqualifier "java.*:javafx.*" \
    -subpackages modele:vue:controleur:parseur \
    -sourcepath "$SRC" \
    $SOURCES \
    2>&1 | grep -v "^Chargement\|^Construction\|^Génération\|^Loading\|^Constructing\|^Generating\|Note:"

  if [ $? -ne 0 ]; then
    echo ""
    echo "✗ Erreur lors de la génération de la Javadoc."
    exit 2
  fi
  echo ""
  echo "✓ Javadoc générée → $DOC"
  echo "  Ouvrir dans un navigateur : file://$DOC/index.html"
fi

# ═════════════════════════════════════════════════════════════════════════════
#  ÉTAPE 3 — LANCEMENT DE L'APPLICATION
# ═════════════════════════════════════════════════════════════════════════════
if $MODE_RUN; then
  echo ""
  echo "┌─────────────────────────────────────────┐"
  echo "│  ÉTAPE 3/3 — Lancement de l'application  │"
  echo "└─────────────────────────────────────────┘"

  export EGL_PLATFORM=x11

  $JAVA \
    -Dprism.order=es2 \
    -Dprism.forceGPU=true \
    --module-path "$JAVAFX" \
    --add-modules javafx.controls,javafx.fxml,javafx.graphics \
    -cp "$OUT:$CP" \
    Main

  echo ""
  echo "Application terminée."
fi

echo ""
echo "═══ Terminé ═══"
