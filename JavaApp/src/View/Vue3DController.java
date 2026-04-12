package View;

import javafx.scene.*;
import javafx.scene.control.Label;
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

import modele.Aeronef;
import modele.CircuitAD;
import modele.GestionnaireConflits;

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
    private static final Color COLOR_CIRCUIT = Color.web("#aaaaaa");
    private static final Color COLOR_LABEL   = Color.WHITE;

    // ---------------------------------------------------------------
    // Composants 3D
    // ---------------------------------------------------------------
    private SubScene          subScene;
    private PerspectiveCamera camera;
    private Group             groupe3D;
    private Group             groupeAeronefs;

    /** Sphère par indicatif. */
    private final Map<String, Sphere> sphereMap = new HashMap<>();
    /** Label Text 3D par indicatif. */
    private final Map<String, Text>   textMap   = new HashMap<>();

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
     * Affiche le circuit sous forme de cylindres (segments A→J).
     * À appeler depuis le thread FX.
     */
    public void afficherCircuit(CircuitAD circuit) {
        List<Node> tubes = new ArrayList<>();
        for (CircuitAD.Segment seg : circuit.getSegmentsCircuit()) {
            Node tube = creerTube(seg.getDebut(), seg.getFin(), COLOR_CIRCUIT);
            if (tube != null) tubes.add(tube);
        }
        groupe3D.getChildren().addAll(tubes);
    }

    /** Cylindre orienté entre deux points 3D du circuit. */
    private Node creerTube(modele.Point3D p1, modele.Point3D p2, Color couleur) {
        javafx.geometry.Point3D fx1 = toFx(p1);
        javafx.geometry.Point3D fx2 = toFx(p2);
        javafx.geometry.Point3D dir = fx2.subtract(fx1);
        double longueur = dir.magnitude();
        if (longueur < 0.001) return null;

        Cylinder cyl = new Cylinder(RAYON_TUBE, longueur);
        cyl.setMaterial(new PhongMaterial(couleur));

        javafx.geometry.Point3D milieu = fx1.midpoint(fx2);
        cyl.setTranslateX(milieu.getX());
        cyl.setTranslateY(milieu.getY());
        cyl.setTranslateZ(milieu.getZ());

        // Aligner le cylindre (axe par défaut = Y) vers la direction du segment
        javafx.geometry.Point3D axeY  = new javafx.geometry.Point3D(0, 1, 0);
        javafx.geometry.Point3D dirN  = dir.normalize();
        javafx.geometry.Point3D axeRot = axeY.crossProduct(dirN);
        double angle = Math.toDegrees(Math.acos(
                Math.max(-1.0, Math.min(1.0, axeY.dotProduct(dirN)))
        ));

        if (axeRot.magnitude() > 1e-6) {
            cyl.getTransforms().add(new Rotate(angle, axeRot));
        }

        return cyl;
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
        textMap.clear();

        for (Aeronef a : aeronefs) {
            // --- Sphère ---
            Sphere sphere = new Sphere(RAYON_SPHERE);
            sphere.setMaterial(new PhongMaterial(
                    couleurParCategorie(a.getType().getCategorie())));
            sphere.setVisible(false);
            sphereMap.put(a.getIndicatif(), sphere);
            groupeAeronefs.getChildren().add(sphere);

            // --- Label : "INDICATIF\nNomType" ---
            Text label = new Text(a.getIndicatif() + "\n" + a.getType().getNom());
            label.setFill(COLOR_LABEL);
            label.setFont(Font.font("System", FontWeight.BOLD, 9));
            label.setTextAlignment(TextAlignment.CENTER);
            label.setVisible(false);
            textMap.put(a.getIndicatif(), label);
            groupeAeronefs.getChildren().add(label);
        }
    }

    /**
     * Met à jour positions, couleurs des sphères et position des labels.
     * À appeler via Platform.runLater() depuis le thread simulation.
     */
    public void rafraichirAeronefs(List<Aeronef> aeronefs,
                                   List<GestionnaireConflits.Conflit> conflits) {

        // Indicatifs en conflit (pour couleur rouge)
        List<String> enConflit = new ArrayList<>();
        for (GestionnaireConflits.Conflit c : conflits) {
            enConflit.add(c.getA1().getIndicatif());
            enConflit.add(c.getA2().getIndicatif());
        }

        for (Aeronef a : aeronefs) {
            Sphere sphere = sphereMap.get(a.getIndicatif());
            Text   label  = textMap  .get(a.getIndicatif());

            if (sphere == null) continue;

            if (a.isActif() && a.getPositionCourante() != null) {
                javafx.geometry.Point3D pos = toFx(a.getPositionCourante());

                // --- Sphère ---
                sphere.setTranslateX(pos.getX());
                sphere.setTranslateY(pos.getY());
                sphere.setTranslateZ(pos.getZ());
                sphere.setVisible(true);

                Color couleur = enConflit.contains(a.getIndicatif())
                        ? COLOR_CONFLIT
                        : couleurParCategorie(a.getType().getCategorie());
                ((PhongMaterial) sphere.getMaterial()).setDiffuseColor(couleur);

                // --- Label : positionné au-dessus de la sphère ---
                if (label != null) {
                    label.setTranslateX(pos.getX() + LABEL_OFFSET_X);
                    label.setTranslateY(pos.getY() - LABEL_OFFSET_Y); // −Y = vers le haut en FX
                    label.setTranslateZ(pos.getZ());
                    label.setFill(enConflit.contains(a.getIndicatif())
                            ? Color.RED : COLOR_LABEL);
                    label.setVisible(true);
                }

            } else {
                sphere.setVisible(false);
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

    private Color couleurParCategorie(String categorie) {
        switch (categorie) {
            case "LIGHT":  return COLOR_LIGHT;
            case "MEDIUM": return COLOR_MEDIUM;
            case "HIGH":   return COLOR_HIGH;
            default:       return Color.WHITE;
        }
    }
}
