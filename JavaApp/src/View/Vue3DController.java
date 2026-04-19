package View;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.*;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import javafx.geometry.Pos;
import javafx.stage.Stage;
import javafx.scene.Scene;

import javafx17obj.ObjViewer3D;

import modele.Aeronef;
import modele.CircuitAD;
import modele.GestionnaireConflits;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vue 3D JavaFX : circuit (cylindres) + aéronefs (sphères + labels Text).
 *
 * Coordinate mapping : circuit(x, y, z) → FX(x/S, −y/S, z/S)  (SCALE S = 10)
 *   x : position Est-Ouest  (mètres)  → FX.X
 *   y : altitude            (mètres)  → FX.Y  (signe inversé : haute altitude = Y négatif)
 *   z : position Nord-Sud   (mètres)  → FX.Z
 *
 * Circuit FX range (d'après circuitAD4.txt) :
 *   X ≈ [−120, 140]   (circuit.x de −1200 à 1400 m)
 *   Y ≈ [−100, 0]     (altitude de 0 à 1000 m)
 *   Z ≈ [−20, 100]    (circuit.z de −200 à 1000 m)
 *
 * Vue de base = CONTRE PLONGÉE :
 *   Caméra sous la piste (FX Y > 0), inclinée vers le haut (rotX > 0).
 *   La caméra regarde vers −Y et +Z (vers les aéronefs en altitude négative).
 */
public class Vue3DController {

    // ---------------------------------------------------------------
    // Constantes
    // ---------------------------------------------------------------
    private static final double SCALE           = 10.0;
    private static final double RAYON_SPHERE    = 8.0;
    private static final double RAYON_TUBE      = 1.5;
    private static final double LABEL_OFFSET_Y  = RAYON_SPHERE * 3.2;  // au-dessus de la sphère
    private static final double LABEL_OFFSET_X  = -14.0;               // centrage horizontal approximatif

    // Couleurs par catégorie
    private static final Color COLOR_LIGHT   = Color.DODGERBLUE;
    private static final Color COLOR_MEDIUM  = Color.LIMEGREEN;
    private static final Color COLOR_HIGH    = Color.ORANGE;
    private static final Color COLOR_CONFLIT = Color.RED;
    private static final Color COLOR_PROCHE  = Color.ORANGE;
    private static final Color COLOR_CIRCUIT = Color.web("#aaaaaa");
    private static final Color COLOR_LABEL   = Color.WHITE;

    // ---------------------------------------------------------------
    // Composants 3D
    // ---------------------------------------------------------------
    private SubScene          subScene;
    private PerspectiveCamera camera;
    private Group             groupe3D;
    private Group             groupeAeronefs;

    /** Sphère par indicatif (null si on utilise un modèle .obj). */
    private final Map<String, Sphere>  sphereMap  = new HashMap<>();
    /** Nœud 3D principal par indicatif (sphère OU modèle .obj chargé). */
    private final Map<String, Node>                       noeudMap      = new HashMap<>();
    /** Rotation de cap (Y) par indicatif — mise à jour à chaque tick pour les modèles .obj. */
    private final Map<String, Rotate>                     rotCapMap     = new HashMap<>();
    /** Label Text 3D par indicatif. */
    private final Map<String, Text>                       textMap       = new HashMap<>();
    /** Aéronef par indicatif — pour la fenêtre de détail au clic. */
    private final Map<String, Aeronef>                    aeronefMap    = new HashMap<>();
    /** MeshViews du modèle .obj par indicatif — pour changer la couleur comme les sphères. */
    private final Map<String, List<javafx.scene.shape.MeshView>> meshViewsMap = new HashMap<>();
    /** Couleurs diffuses d'origine par MeshView — pour restaurer après un conflit/proximité. */
    private final Map<String, Map<javafx.scene.shape.MeshView, Color>> meshOrigColorsMap = new HashMap<>();

    /** Modèle .obj global (fallback pour tous les types sans modèle spécifique). null = sphères. */
    private File modele3DFile = null;
    /** Modèle .obj par nom de type aéronef. Prioritaire sur le modèle global. */
    private final Map<String, File> modeles3DParType = new HashMap<>();

    // Transforms caméra
    private final Translate camTranslate = new Translate(0, 0, 0);
    private final Rotate    camRotX      = new Rotate(0, Rotate.X_AXIS);
    private final Rotate    camRotY      = new Rotate(0, Rotate.Y_AXIS);
    private final Rotate    camRotZ      = new Rotate(0, Rotate.Z_AXIS);

    // Vue active — détermine comment les boutons de déplacement se comportent
    private enum VueMode { HAUTE, BASSE }
    private VueMode vueMode = VueMode.HAUTE;

    // Label 2D affichant la position de la caméra en bas de la vue
    private Label labelCamPos;

    // ---------------------------------------------------------------
    // Initialisation
    // ---------------------------------------------------------------

    /**
     * Crée le SubScene 3D et l'insère dans le conteneur.
     * Vue par défaut : contre-plongée.
     */
    public void initialiser(StackPane conteneur) {
        groupe3D       = new Group();
        groupeAeronefs = new Group();
        groupe3D.getChildren().add(groupeAeronefs);

        // Lumières
        AmbientLight ambiant = new AmbientLight(Color.gray(0.35));
        PointLight   spot    = new PointLight(Color.WHITE);
        spot.setTranslateX(10); spot.setTranslateY(-300); spot.setTranslateZ(-100);
        groupe3D.getChildren().addAll(ambiant, spot);

        // Caméra
        camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1);
        camera.setFarClip(15000);
        camera.setFieldOfView(50);
        camera.getTransforms().addAll(camTranslate, camRotX, camRotY, camRotZ);

        // SubScene
        subScene = new SubScene(groupe3D, 800, 600, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.web("#87CEEB")); // bleu ciel
        subScene.setCamera(camera);

        subScene.widthProperty() .bind(conteneur.widthProperty());
        subScene.heightProperty().bind(conteneur.heightProperty());

        conteneur.getChildren().add(subScene);

        // Grand carré vert plat représentant le sol (de -1000 à +1000 en X et Z)
        Box sol = new Box(2000, 2, 2000);
        sol.setTranslateX(0);
        sol.setTranslateY(2);   // juste en dessous du niveau 0 (FX Y=0 = altitude 0)
        sol.setTranslateZ(0);
        PhongMaterial matSol = new PhongMaterial(Color.LAWNGREEN);
        matSol.setSpecularColor(Color.BLACK);  // pas de reflet brillant → sol mat et uniforme
        sol.setMaterial(matSol);
        groupe3D.getChildren().add(sol);

        // Label 2D position caméra, affiché en bas à gauche de la vue
        labelCamPos = new Label("Cam — X: 0  Y: 0  Z: 0  rotX: 0°  rotY: 0°");
        labelCamPos.setId("labelCamPos");
        labelCamPos.setStyle(
                "-fx-text-fill: rgba(255,255,255,0.7);" +
                "-fx-font-size: 10px;" +
                "-fx-font-family: monospace;" +
                "-fx-background-color: rgba(0,0,0,0.4);" +
                "-fx-padding: 2 6 2 6;" +
                "-fx-background-radius: 3;"
        );
        StackPane.setAlignment(labelCamPos, Pos.BOTTOM_LEFT);
        conteneur.getChildren().add(labelCamPos);
    }

    // ---------------------------------------------------------------
    // Circuit
    // ---------------------------------------------------------------

    /**
     * Affiche le circuit sous forme de petites sphères aux extrémités de chaque segment.
     * À appeler depuis le thread FX.
     */
    public void afficherCircuit(CircuitAD circuit) {
        PhongMaterial mat = new PhongMaterial(COLOR_CIRCUIT);
        mat.setSpecularColor(Color.BLACK);

        // Utiliser un Set de positions déjà placées pour éviter les doublons aux jonctions
        java.util.Set<String> vus = new java.util.HashSet<>();
        List<Node> noeuds = new ArrayList<>();

        for (CircuitAD.Segment seg : circuit.getSegmentsCircuit()) {
            for (modele.Point3D p : new modele.Point3D[]{seg.getDebut(), seg.getFin()}) {
                String cle = Math.round(p.getX()) + "," + Math.round(p.getY()) + "," + Math.round(p.getZ());
                if (vus.add(cle)) {
                    javafx.geometry.Point3D fx = toFx(p);
                    Sphere s = new Sphere(RAYON_TUBE);
                    s.setMaterial(mat);
                    s.setTranslateX(fx.getX());
                    s.setTranslateY(fx.getY());
                    s.setTranslateZ(fx.getZ());
                    noeuds.add(s);
                }
            }
        }
        groupe3D.getChildren().addAll(noeuds);
    }

    // ---------------------------------------------------------------
    // Aéronefs : sphères + labels
    // ---------------------------------------------------------------

    /**
     * Crée une sphère ET un label Text pour chaque aéronef.
     * À appeler depuis le thread FX après parsage.
     */
    public void initialiserSpheres(List<Aeronef> aeronefs) {
        groupeAeronefs.getChildren().clear();
        sphereMap.clear();
        noeudMap.clear();
        rotCapMap.clear();
        textMap.clear();
        aeronefMap.clear();
        meshViewsMap.clear();
        meshOrigColorsMap.clear();

        for (Aeronef a : aeronefs) {
            aeronefMap.put(a.getIndicatif(), a);

            // --- Nœud principal : modèle .obj si dispo, sinon sphère ---
            // Priorité : modèle spécifique au type > modèle global > sphère
            File fichierModele = modeles3DParType.getOrDefault(a.getType().getNom(), modele3DFile);
            Node noeudPrincipal;
            if (fichierModele != null) {
                Node modele = chargerNoeudModele(fichierModele);
                if (modele != null) {
                    // Rotation de cap (Y) insérée en tête des transforms, avant le flip X
                    Rotate rotCap = new Rotate(0, Rotate.Y_AXIS);
                    modele.getTransforms().add(0, rotCap);
                    rotCapMap.put(a.getIndicatif(), rotCap);

                    modele.setVisible(false);
                    modele.setCursor(javafx.scene.Cursor.HAND);
                    modele.setOnMouseClicked(e -> ouvrirFenetreInfo(a));
                    noeudPrincipal = modele;

                    // Collecter tous les MeshViews du modèle et mémoriser leurs couleurs d'origine
                    List<javafx.scene.shape.MeshView> meshes = new ArrayList<>();
                    collecterMeshViews(modele, meshes);
                    meshViewsMap.put(a.getIndicatif(), meshes);
                    Map<javafx.scene.shape.MeshView, Color> origColors = new HashMap<>();
                    for (javafx.scene.shape.MeshView mv : meshes) {
                        if (mv.getMaterial() instanceof PhongMaterial pm) {
                            origColors.put(mv, pm.getDiffuseColor() != null
                                    ? pm.getDiffuseColor() : Color.WHITE);
                        }
                    }
                    meshOrigColorsMap.put(a.getIndicatif(), origColors);
                } else {
                    // Échec du chargement → fallback sphère
                    noeudPrincipal = creerSphere(a);
                    sphereMap.put(a.getIndicatif(), (Sphere) noeudPrincipal);
                }
            } else {
                noeudPrincipal = creerSphere(a);
                sphereMap.put(a.getIndicatif(), (Sphere) noeudPrincipal);
            }
            noeudMap.put(a.getIndicatif(), noeudPrincipal);
            groupeAeronefs.getChildren().add(noeudPrincipal);

            // --- Label texte : "INDICATIF\nNomType" ---
            Text label = new Text(a.getIndicatif() + "\n" + a.getType().getNom());
            label.setFill(COLOR_LABEL);
            label.setFont(Font.font("System", FontWeight.BOLD, 9));
            label.setTextAlignment(TextAlignment.CENTER);
            label.setVisible(false);
            label.setScaleX(-1);  // corrige l'effet miroir causé par rotZ=180 / rotY=180
            label.setCursor(javafx.scene.Cursor.HAND);
            label.setOnMouseClicked(e -> ouvrirFenetreInfo(a));
            textMap.put(a.getIndicatif(), label);
            groupeAeronefs.getChildren().add(label);
        }
    }

    /** Crée et retourne une sphère configurée pour un aéronef. */
    private Sphere creerSphere(Aeronef a) {
        Sphere sphere = new Sphere(RAYON_SPHERE);
        sphere.setMaterial(new PhongMaterial(couleurParCategorie(a.getType().getCategorie())));
        sphere.setVisible(false);
        sphere.setCursor(javafx.scene.Cursor.HAND);
        sphere.setOnMouseClicked(e -> ouvrirFenetreInfo(a));
        return sphere;
    }

    /**
     * Charge un modèle .obj via ObjViewer3D (même API que l'exemple),
     * extrait le Group modèle de la SubScene interne, le détache,
     * puis le redimensionne pour que sa plus grande dimension = diamètre de la sphère.
     * Retourne null en cas d'échec → fallback sphère automatique.
     *
     * Appelée une fois par aéronef au démarrage de la simulation.
     */
    private Node chargerNoeudModele(File fichier) {
        try {
            // Créer un viewer temporaire juste pour charger le modèle
            ObjViewer3D viewer = new ObjViewer3D(1, 1);
            viewer.loadObj(fichier.getAbsolutePath());

            // Le modèle est le dernier Group enfant du monde 3D interne
            // (les autres enfants sont des lumières)
            javafx.scene.Group world = (javafx.scene.Group) viewer.getSubScene().getRoot();
            javafx.scene.Group modelGroup = null;
            for (Node n : world.getChildren()) {
                if (n instanceof javafx.scene.Group g) modelGroup = g;
            }
            if (modelGroup == null) return null;

            // Détacher du viewer interne pour l'intégrer dans notre scène
            world.getChildren().remove(modelGroup);

            // Rotation -180° sur X pour corriger l'orientation du modèle
            modelGroup.getTransforms().add(new Rotate(-180, Rotate.X_AXIS));

            return modelGroup;
        } catch (Exception ex) {
            System.err.println("[Vue3D] Échec chargement modèle .obj («" + fichier.getName() + "») : " + ex.getMessage());
            return null;  // → fallback sphère
        }
    }

    /** Définit le fichier .obj global (tous types sans modèle propre). null = sphères. */
    public void setModele3D(File f) {
        this.modele3DFile = f;
    }

    /** Assigne un fichier .obj à un type spécifique. null = retirer (retombe sur le global). */
    public void setModele3DPourType(String typeNom, File f) {
        if (f != null) modeles3DParType.put(typeNom, f);
        else           modeles3DParType.remove(typeNom);
    }

    /** Remplace toute la map type→fichier (appelé au démarrage de la simulation). */
    public void setModeles3DParType(Map<String, File> map) {
        modeles3DParType.clear();
        modeles3DParType.putAll(map);
    }

    /**
     * Met à jour positions, couleurs des sphères et position des labels.
     * À appeler via Platform.runLater() depuis le thread simulation.
     */
    public void rafraichirAeronefs(List<Aeronef> aeronefs,
                                   List<GestionnaireConflits.Conflit> conflits,
                                   List<GestionnaireConflits.Conflit> proximites) {

        if (aeronefs == null || conflits == null || proximites == null) return;

        // Indicatifs en conflit (rouge) et en proximité (orange)
        List<String> enConflit  = new ArrayList<>();
        List<String> enProximite = new ArrayList<>();
        for (GestionnaireConflits.Conflit c : conflits) {
            enConflit.add(c.getA1().getIndicatif());
            enConflit.add(c.getA2().getIndicatif());
        }
        for (GestionnaireConflits.Conflit c : proximites) {
            enProximite.add(c.getA1().getIndicatif());
            enProximite.add(c.getA2().getIndicatif());
        }

        for (Aeronef a : aeronefs) {
            Node   noeud  = noeudMap .get(a.getIndicatif());
            Sphere sphere = sphereMap.get(a.getIndicatif()); // null si modèle .obj
            Text   label  = textMap  .get(a.getIndicatif());

            if (noeud == null) continue;

            if (a.isActif() && a.getPositionCourante() != null) {
                javafx.geometry.Point3D pos = toFx(a.getPositionCourante());

                // --- Positionner le nœud principal (sphère ou modèle .obj) ---
                noeud.setTranslateX(pos.getX());
                noeud.setTranslateY(pos.getY());
                noeud.setTranslateZ(pos.getZ());
                noeud.setVisible(true);

                // Orienter le modèle .obj vers la direction du segment courant
                Rotate rotCap = rotCapMap.get(a.getIndicatif());
                if (rotCap != null) {
                    double angle = Math.toDegrees(Math.atan2(-a.getSegDz(), a.getSegDx()));
                    rotCap.setAngle(angle);
                }

                // ── Couleur sphère ──────────────────────────────────────────
                if (sphere != null) {
                    Color couleur = enConflit.contains(a.getIndicatif()) ? COLOR_CONFLIT
                            : enProximite.contains(a.getIndicatif())    ? COLOR_PROCHE
                            : couleurParCategorie(a.getType().getCategorie());
                    ((PhongMaterial) sphere.getMaterial()).setDiffuseColor(couleur);
                }

                // ── Couleur modèle .obj (via ses MeshViews) ─────────────────
                List<javafx.scene.shape.MeshView> meshes = meshViewsMap.get(a.getIndicatif());
                if (meshes != null) {
                    boolean conflit   = enConflit.contains(a.getIndicatif());
                    boolean proximite = enProximite.contains(a.getIndicatif());
                    Map<javafx.scene.shape.MeshView, Color> origColors =
                            meshOrigColorsMap.get(a.getIndicatif());

                    for (javafx.scene.shape.MeshView mv : meshes) {
                        if (!(mv.getMaterial() instanceof PhongMaterial pm)) continue;
                        if (conflit) {
                            pm.setDiffuseColor(COLOR_CONFLIT);
                        } else if (proximite) {
                            pm.setDiffuseColor(COLOR_PROCHE);
                        } else if (origColors != null) {
                            // Restaurer la couleur d'origine du matériau
                            pm.setDiffuseColor(origColors.getOrDefault(mv, Color.WHITE));
                        }
                    }
                }

                // --- Label : positionné au-dessus du nœud ---
                if (label != null) {
                    label.setTranslateX(pos.getX() + LABEL_OFFSET_X);
                    label.setTranslateY(pos.getY() - LABEL_OFFSET_Y);
                    label.setTranslateZ(pos.getZ());
                    label.setFill(enConflit.contains(a.getIndicatif())
                            ? Color.RED : COLOR_LABEL);
                    label.setVisible(true);
                }

            } else {
                noeud.setVisible(false);
                if (label != null) label.setVisible(false);
            }
        }
    }

    // ---------------------------------------------------------------
    // Caméra
    // ---------------------------------------------------------------

    /**
     * Vue haute = PLONGEANTE depuis le dessus.
     * Mapping corrigé : circuit.y = altitude → FX.Y (négatif = haut).
     * Circuit altitude max ≈ 1000 m → FX Y ≈ −100.
     * Caméra à FX Y=−200 (au-dessus de tout), regardant fortement vers le bas.
     * direction = (0, −sin(−55°), cos(−55°)) = (0, +0.82, +0.57) → vers +Y (bas) et +Z.
     */
    public void setCameraVueHaute() {
        vueMode = VueMode.HAUTE;
        camTranslate.setX(20);
        camTranslate.setY(-400);
        camTranslate.setZ(60);
        camRotX.setAngle(-90);
        camRotY.setAngle(0);
        camRotZ.setAngle(180);
        majLabelCam();
    }

    /**
     * Vue basse = NIVEAU PISTE.
     * Circuit piste : altitude y=0 → FX Y=0.
     * Caméra juste sous la piste (FX Y=+3), devant l'approche (FX Z=−20), quasi-horizontal.
     * direction = (0, −sin(+3°), cos(+3°)) ≈ (0, −0.05, +1) → quasi horizontal, légèrement vers le haut.
     * Simule un observateur au bord de la piste regardant les aéronefs.
     */
    public void setCameraVueBasse() {
        vueMode = VueMode.BASSE;
        camTranslate.setX(10);
        camTranslate.setY(-28);
        camTranslate.setZ(408);
        camRotX.setAngle(-15);
        camRotY.setAngle(180);
        camRotZ.setAngle(0);
        majLabelCam();
    }

    /**
     * Contre-plongée (vue de base au démarrage).
     * Caméra sous la piste (FX Y=+12 > 0), inclinée vers le haut (rotX > 0).
     * direction = (0, −sin(+25°), cos(+25°)) = (0, −0.42, +0.91) → vers −Y (altitude) et +Z.
     * Le circuit en altitude (FX Y ≈ −50 à −100) est bien dans le champ de vue.
     */

    /**
     * Vue haute  : dx = strafe X,  dz = strafe Z (plan horizontal).
     * Vue basse  : dx = strafe X,  dz = monter/descendre (axe Y).
     */
    public void deplacerCamera(double dx, double dz) {
        if (vueMode == VueMode.BASSE) {
            camTranslate.setX(camTranslate.getX() - dx);  // inversé en vue basse
            camTranslate.setY(camTranslate.getY() + dz);
        } else {
            camTranslate.setX(camTranslate.getX() - dx);  // inversé en vue haute
            camTranslate.setZ(camTranslate.getZ() + dz);
        }
        majLabelCam();
    }

    /** Rotation horizontale de la caméra (autour de Y). */
    public void rotationCamera(double dAngle) {
        camRotY.setAngle(camRotY.getAngle() + dAngle);
        majLabelCam();
    }

    /**
     * Vue haute  : zoom le long de la direction de vue (avant/arrière).
     * Vue basse  : avance/recule en Z (profondeur de scène).
     */
    public void zoomer(double delta) {
        if (vueMode == VueMode.BASSE) {
            camTranslate.setZ(camTranslate.getZ() - delta);  // inversé en vue basse
        } else {
            double angRad = Math.toRadians(camRotX.getAngle());
            camTranslate.setY(camTranslate.getY() + (-Math.sin(angRad)) * delta);
            camTranslate.setZ(camTranslate.getZ() + Math.cos(angRad) * delta);
        }
        majLabelCam();
    }

    /** Met à jour le label de position caméra affiché en bas de la vue. */
    private void majLabelCam() {
        if (labelCamPos == null) return;
        labelCamPos.setText(String.format(
                "Cam — X: %.0f  Y: %.0f  Z: %.0f  rotX: %.0f°  rotY: %.0f°  rotZ: %.0f°",
                camTranslate.getX(),
                camTranslate.getY(),
                camTranslate.getZ(),
                camRotX.getAngle(),
                camRotY.getAngle(),
                camRotZ.getAngle()
        ));
    }

    // ---------------------------------------------------------------
    // Utilitaires
    // ---------------------------------------------------------------

    /**
     * circuit(x, y, z) → FX(x/S, −y/S, z/S)
     * circuit.y = altitude → FX.Y négatif (haute altitude = Y plus négatif = "vers le haut" en JavaFX).
     * circuit.z = position Nord-Sud → FX.Z (profondeur).
     */
    private javafx.geometry.Point3D toFx(modele.Point3D p) {
        return new javafx.geometry.Point3D(
                p.getX() / SCALE,
               -p.getY() / SCALE,   // altitude → FX Y (inversé : haut = négatif)
                p.getZ() / SCALE    // Nord-Sud → FX Z (profondeur)
        );
    }

    /**
     * Ouvre une petite fenêtre flottante avec les infos de l'aéronef cliqué.
     * Fonctionne quel que soit le thread appelant.
     */
    private void ouvrirFenetreInfo(Aeronef a) {
        Platform.runLater(() -> {
            Stage fenetre = new Stage();
            fenetre.setTitle(a.getIndicatif());
            fenetre.setResizable(false);

            GridPane grid = new GridPane();
            grid.setHgap(14);
            grid.setVgap(8);
            grid.setPadding(new Insets(16));

            int row = 0;
            grid.add(info("Indicatif",  a.getIndicatif()),                    0, row++);
            grid.add(info("Type",       a.getType().getNom()),                 0, row++);
            grid.add(info("Catégorie",  a.getType().getCategorie()),           0, row++);
            grid.add(info("Vitesse",    String.format("%.0f m/s  (%.0f km/h)",
                          a.getType().getVitesseMps(),
                          a.getType().getVitesseMps() * 3.6)),                 0, row++);
            grid.add(info("Tours max",  String.valueOf(a.getNbToursMax())),    0, row++);
            grid.add(info("Départ",     String.format("%.0f s", a.getTempsDepart())), 0, row++);

            if (a.getPositionCourante() != null) {
                modele.Point3D pos = a.getPositionCourante();
                grid.add(info("Position",
                        String.format("x=%.0f  y=%.0f  z=%.0f", pos.getX(), pos.getY(), pos.getZ())),
                        0, row);
            }

            fenetre.setScene(new Scene(grid));
            fenetre.show();
        });
    }

    /** Crée un Label "clé : valeur" pour la grille d'infos. */
    private Label info(String cle, String valeur) {
        Label l = new Label(cle + " :  " + valeur);
        l.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");
        return l;
    }

    /**
     * Parcourt récursivement un nœud et ajoute tous les MeshView trouvés dans la liste.
     * Utilisé pour collecter les surfaces colorables d'un modèle .obj chargé.
     */
    private void collecterMeshViews(Node node, List<javafx.scene.shape.MeshView> result) {
        if (node instanceof javafx.scene.shape.MeshView mv) {
            result.add(mv);
        } else if (node instanceof Group g) {
            for (Node enfant : g.getChildren()) {
                collecterMeshViews(enfant, result);
            }
        }
    }

    private Color couleurParCategorie(String categorie) {
        switch (categorie) {
            case "LIGHT":  return COLOR_LIGHT;
            case "MEDIUM": return COLOR_MEDIUM;
            case "HIGH":   return COLOR_HIGH;
            default:       return Color.WHITE;
        }
    }
}
