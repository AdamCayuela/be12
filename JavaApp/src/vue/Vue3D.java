package vue;

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
 * VUE 3D — affiche le circuit aérodrome et les aéronefs en temps réel.
 *
 * Responsabilité MVC : vue pure.
 *   - Construit et affiche le SubScene JavaFX 3D.
 *   - Se met à jour via rafraichirAeronefs() appelé par le contrôleur.
 *   - Ne contient aucune logique métier.
 *
 * Coordinate mapping : circuit(x, y, z) → FX(x/S, −y/S, z/S)  (SCALE S = 10)
 *   x : position Est-Ouest  (mètres)  → FX.X
 *   y : altitude            (mètres)  → FX.Y  (signe inversé : haute altitude = Y négatif)
 *   z : position Nord-Sud   (mètres)  → FX.Z
 */
public class Vue3D {

    // ---------------------------------------------------------------
    // Constantes
    // ---------------------------------------------------------------
    private static final double SCALE           = 10.0;
    private static final double RAYON_SPHERE    = 8.0;
    private static final double RAYON_TUBE      = 1.5;
    private static final double LABEL_OFFSET_Y  = RAYON_SPHERE * 3.2;
    private static final double LABEL_OFFSET_X  = -14.0;

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

    private final Map<String, Sphere>  sphereMap  = new HashMap<>();
    private final Map<String, Node>    noeudMap   = new HashMap<>();
    private final Map<String, Rotate>  rotCapMap  = new HashMap<>();
    private final Map<String, Text>    textMap    = new HashMap<>();
    private final Map<String, Aeronef> aeronefMap = new HashMap<>();
    private final Map<String, List<javafx.scene.shape.MeshView>> meshViewsMap      = new HashMap<>();
    private final Map<String, Map<javafx.scene.shape.MeshView, Color>> meshOrigColorsMap = new HashMap<>();

    private File modele3DFile = null;
    private final Map<String, File> modeles3DParType = new HashMap<>();

    // Transforms caméra
    private final Translate camTranslate = new Translate(0, 0, 0);
    private final Rotate    camRotX      = new Rotate(0, Rotate.X_AXIS);
    private final Rotate    camRotY      = new Rotate(0, Rotate.Y_AXIS);
    private final Rotate    camRotZ      = new Rotate(0, Rotate.Z_AXIS);

    private enum VueMode { HAUTE, BASSE }
    private VueMode vueMode = VueMode.HAUTE;

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

        AmbientLight ambiant = new AmbientLight(Color.gray(0.35));
        PointLight   spot    = new PointLight(Color.WHITE);
        spot.setTranslateX(10); spot.setTranslateY(-300); spot.setTranslateZ(-100);
        groupe3D.getChildren().addAll(ambiant, spot);

        camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1);
        camera.setFarClip(15000);
        camera.setFieldOfView(50);
        camera.getTransforms().addAll(camTranslate, camRotX, camRotY, camRotZ);

        subScene = new SubScene(groupe3D, 800, 600, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.web("#87CEEB"));
        subScene.setCamera(camera);

        subScene.widthProperty() .bind(conteneur.widthProperty());
        subScene.heightProperty().bind(conteneur.heightProperty());

        conteneur.getChildren().add(subScene);

        Box sol = new Box(2000, 2, 2000);
        sol.setTranslateX(0);
        sol.setTranslateY(2);
        sol.setTranslateZ(0);
        PhongMaterial matSol = new PhongMaterial(Color.LAWNGREEN);
        matSol.setSpecularColor(Color.BLACK);
        sol.setMaterial(matSol);
        groupe3D.getChildren().add(sol);

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

    public void afficherCircuit(CircuitAD circuit) {
        PhongMaterial mat = new PhongMaterial(COLOR_CIRCUIT);
        mat.setSpecularColor(Color.BLACK);

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

            File fichierModele = modeles3DParType.getOrDefault(a.getType().getNom(), modele3DFile);
            Node noeudPrincipal;
            if (fichierModele != null) {
                Node modele = chargerNoeudModele(fichierModele);
                if (modele != null) {
                    Rotate rotCap = new Rotate(0, Rotate.Y_AXIS);
                    modele.getTransforms().add(0, rotCap);
                    rotCapMap.put(a.getIndicatif(), rotCap);
                    modele.setVisible(false);
                    modele.setCursor(javafx.scene.Cursor.HAND);
                    modele.setOnMouseClicked(e -> ouvrirFenetreInfo(a));
                    noeudPrincipal = modele;

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
                    noeudPrincipal = creerSphere(a);
                    sphereMap.put(a.getIndicatif(), (Sphere) noeudPrincipal);
                }
            } else {
                noeudPrincipal = creerSphere(a);
                sphereMap.put(a.getIndicatif(), (Sphere) noeudPrincipal);
            }
            noeudMap.put(a.getIndicatif(), noeudPrincipal);
            groupeAeronefs.getChildren().add(noeudPrincipal);

            Text label = new Text(a.getIndicatif() + "\n" + a.getType().getNom());
            label.setFill(COLOR_LABEL);
            label.setFont(Font.font("System", FontWeight.BOLD, 9));
            label.setTextAlignment(TextAlignment.CENTER);
            label.setVisible(false);
            label.setScaleX(-1);
            label.setCursor(javafx.scene.Cursor.HAND);
            label.setOnMouseClicked(e -> ouvrirFenetreInfo(a));
            textMap.put(a.getIndicatif(), label);
            groupeAeronefs.getChildren().add(label);
        }
    }

    private Sphere creerSphere(Aeronef a) {
        Sphere sphere = new Sphere(RAYON_SPHERE);
        sphere.setMaterial(new PhongMaterial(couleurParCategorie(a.getType().getCategorie())));
        sphere.setVisible(false);
        sphere.setCursor(javafx.scene.Cursor.HAND);
        sphere.setOnMouseClicked(e -> ouvrirFenetreInfo(a));
        return sphere;
    }

    private Node chargerNoeudModele(File fichier) {
        try {
            ObjViewer3D viewer = new ObjViewer3D(1, 1);
            viewer.loadObj(fichier.getAbsolutePath());
            javafx.scene.Group world = (javafx.scene.Group) viewer.getSubScene().getRoot();
            javafx.scene.Group modelGroup = null;
            for (Node n : world.getChildren()) {
                if (n instanceof javafx.scene.Group g) modelGroup = g;
            }
            if (modelGroup == null) return null;
            world.getChildren().remove(modelGroup);
            modelGroup.getTransforms().add(new Rotate(-180, Rotate.X_AXIS));
            return modelGroup;
        } catch (Exception ex) {
            System.err.println("[Vue3D] Échec chargement modèle .obj («" + fichier.getName() + "») : " + ex.getMessage());
            return null;
        }
    }

    public void setModele3D(File f)                          { this.modele3DFile = f; }
    public void setModele3DPourType(String typeNom, File f)  {
        if (f != null) modeles3DParType.put(typeNom, f);
        else           modeles3DParType.remove(typeNom);
    }
    public void setModeles3DParType(Map<String, File> map) {
        modeles3DParType.clear();
        modeles3DParType.putAll(map);
    }

    /**
     * Met à jour positions et couleurs des aéronefs dans la vue.
     * Appelée par le contrôleur via Platform.runLater() à chaque tick du modèle.
     */
    public void rafraichirAeronefs(List<Aeronef> aeronefs,
                                   List<GestionnaireConflits.Conflit> conflits,
                                   List<GestionnaireConflits.Conflit> proximites) {

        if (aeronefs == null || conflits == null || proximites == null) return;

        List<String> enConflit   = new ArrayList<>();
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
            Sphere sphere = sphereMap.get(a.getIndicatif());
            Text   label  = textMap  .get(a.getIndicatif());

            if (noeud == null) continue;

            if (a.isActif() && a.getPositionCourante() != null) {
                javafx.geometry.Point3D pos = toFx(a.getPositionCourante());

                noeud.setTranslateX(pos.getX());
                noeud.setTranslateY(pos.getY());
                noeud.setTranslateZ(pos.getZ());
                noeud.setVisible(true);

                Rotate rotCap = rotCapMap.get(a.getIndicatif());
                if (rotCap != null) {
                    double angle = Math.toDegrees(Math.atan2(-a.getSegDz(), a.getSegDx()));
                    rotCap.setAngle(angle);
                }

                if (sphere != null) {
                    Color couleur = enConflit.contains(a.getIndicatif()) ? COLOR_CONFLIT
                            : enProximite.contains(a.getIndicatif())    ? COLOR_PROCHE
                            : couleurParCategorie(a.getType().getCategorie());
                    ((PhongMaterial) sphere.getMaterial()).setDiffuseColor(couleur);
                }

                List<javafx.scene.shape.MeshView> meshes = meshViewsMap.get(a.getIndicatif());
                if (meshes != null) {
                    boolean conflit   = enConflit.contains(a.getIndicatif());
                    boolean proximite = enProximite.contains(a.getIndicatif());
                    Map<javafx.scene.shape.MeshView, Color> origColors = meshOrigColorsMap.get(a.getIndicatif());
                    for (javafx.scene.shape.MeshView mv : meshes) {
                        if (!(mv.getMaterial() instanceof PhongMaterial pm)) continue;
                        if (conflit)          pm.setDiffuseColor(COLOR_CONFLIT);
                        else if (proximite)   pm.setDiffuseColor(COLOR_PROCHE);
                        else if (origColors != null)
                            pm.setDiffuseColor(origColors.getOrDefault(mv, Color.WHITE));
                    }
                }

                if (label != null) {
                    label.setTranslateX(pos.getX() + LABEL_OFFSET_X);
                    label.setTranslateY(pos.getY() - LABEL_OFFSET_Y);
                    label.setTranslateZ(pos.getZ());
                    label.setFill(enConflit.contains(a.getIndicatif()) ? Color.RED : COLOR_LABEL);
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

    public void deplacerCamera(double dx, double dz) {
        if (vueMode == VueMode.BASSE) {
            camTranslate.setX(camTranslate.getX() - dx);
            camTranslate.setY(camTranslate.getY() + dz);
        } else {
            camTranslate.setX(camTranslate.getX() - dx);
            camTranslate.setZ(camTranslate.getZ() + dz);
        }
        majLabelCam();
    }

    public void rotationCamera(double dAngle) {
        camRotY.setAngle(camRotY.getAngle() + dAngle);
        majLabelCam();
    }

    public void zoomer(double delta) {
        if (vueMode == VueMode.BASSE) {
            camTranslate.setZ(camTranslate.getZ() - delta);
        } else {
            double angRad = Math.toRadians(camRotX.getAngle());
            camTranslate.setY(camTranslate.getY() + (-Math.sin(angRad)) * delta);
            camTranslate.setZ(camTranslate.getZ() + Math.cos(angRad) * delta);
        }
        majLabelCam();
    }

    private void majLabelCam() {
        if (labelCamPos == null) return;
        labelCamPos.setText(String.format(
                "Cam — X: %.0f  Y: %.0f  Z: %.0f  rotX: %.0f°  rotY: %.0f°  rotZ: %.0f°",
                camTranslate.getX(), camTranslate.getY(), camTranslate.getZ(),
                camRotX.getAngle(), camRotY.getAngle(), camRotZ.getAngle()
        ));
    }

    // ---------------------------------------------------------------
    // Utilitaires privés
    // ---------------------------------------------------------------

    private javafx.geometry.Point3D toFx(modele.Point3D p) {
        return new javafx.geometry.Point3D(
                 p.getX() / SCALE,
                -p.getY() / SCALE,
                 p.getZ() / SCALE
        );
    }

    private void ouvrirFenetreInfo(Aeronef a) {
        Platform.runLater(() -> {
            Stage fenetre = new Stage();
            fenetre.setTitle(a.getIndicatif());
            fenetre.setResizable(false);

            GridPane grid = new GridPane();
            grid.setHgap(14); grid.setVgap(8);
            grid.setPadding(new Insets(16));

            int row = 0;
            grid.add(info("Indicatif",  a.getIndicatif()),                    0, row++);
            grid.add(info("Type",       a.getType().getNom()),                 0, row++);
            grid.add(info("Catégorie",  a.getType().getCategorie()),           0, row++);
            grid.add(info("Vitesse",    String.format("%.0f m/s  (%.0f km/h)",
                          a.getType().getVitesseMps(), a.getType().getVitesseMps() * 3.6)), 0, row++);
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

    private Label info(String cle, String valeur) {
        Label l = new Label(cle + " :  " + valeur);
        l.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");
        return l;
    }

    private void collecterMeshViews(Node node, List<javafx.scene.shape.MeshView> result) {
        if (node instanceof javafx.scene.shape.MeshView mv) {
            result.add(mv);
        } else if (node instanceof Group g) {
            for (Node enfant : g.getChildren()) collecterMeshViews(enfant, result);
        }
    }

    private Color couleurParCategorie(String categorie) {
        return switch (categorie) {
            case "LIGHT"  -> COLOR_LIGHT;
            case "MEDIUM" -> COLOR_MEDIUM;
            case "HIGH"   -> COLOR_HIGH;
            default       -> Color.WHITE;
        };
    }
}
