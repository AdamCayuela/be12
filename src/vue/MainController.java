package vue;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import modele.*;
import parseur.ParseurAeronefs;
import parseur.ParseurCircuit;
import parseur.ParseurTypeAeronefs;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Contrôleur principal — construit la fenêtre entière en Java pur.
 *
 * Fonctions implémentées :
 *  - Menus Fichier : FileChooser pour charger circuit / types / aéronefs
 *  - Menu Quitter  : boîte de confirmation puis Platform.exit()
 *  - Menu Caméra   : délègue vue haute/basse au PanneauControleController
 *  - Menu Paramètres : fenêtre modale de paramètres
 *  - Menu Aide     : fenêtre "À propos"
 *  - Play / Pause / Stop : thread daemon de simulation (SIM_SPEED×20)
 *  - Slider temps  : mis à jour automatiquement par la simulation
 *  - Panneau conflits : mis à jour à chaque tick
 */
public class MainController {

    private final Stage stage;

    // ── Fichiers chargés ──────────────────────────────────────────────────────
    private File fichierCircuit;
    private File fichierTypes;
    private File fichierAeronefs;

    // ── État de la simulation ─────────────────────────────────────────────────
    private enum EtatSimulation { ARRET, EN_COURS, PAUSE }
    private EtatSimulation etat = EtatSimulation.ARRET;

    // ── Modèle ────────────────────────────────────────────────────────────────
    private CircuitAD            circuit;
    private List<TypeAeronef>    types;
    private List<Aeronef>        aeronefs;
    private GestionnaireConflits gestConflits;
    private Simulation           simulation;
    private Thread               threadSimulation;

    // ── Barre de menus ────────────────────────────────────────────────────────
    private MenuItem menuChargerCircuit;
    private MenuItem menuChargerTypes;
    private MenuItem menuChargerAeronefs;
    private MenuItem menuQuitter;
    private MenuItem menuVueHaute;
    private MenuItem menuVueBasse;
    private MenuItem menuParametres;
    private MenuItem menuAide;

    // ── Barre de lecture ──────────────────────────────────────────────────────
    Button btnPlay;
    Button btnPause;
    Button btnStop;
    Label  labelTemps;
    Slider sliderTemps;

    // ── Zone 3D ───────────────────────────────────────────────────────────────
    StackPane         conteneur3D;
    Vue3DController   vue3D;

    // ── Panneau de contrôle ───────────────────────────────────────────────────
    PanneauControleController panneauControle;

    // ── Status bar ────────────────────────────────────────────────────────────
    Label labelStatut;

    // ─────────────────────────────────────────────────────────────────────────

    public MainController(Stage stage) {
        this.stage = stage;
    }

    /** Assemble et retourne la scène principale complète. */
    public Scene buildScene() {
        BorderPane root = new BorderPane();

        VBox topBox = new VBox(buildMenuBar(), buildToolBar());
        root.setTop(topBox);
        root.setCenter(buildCenter());
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root, 1280, 800);

        // Initialiser la vue 3D après que le panneau soit dans la scène
        Platform.runLater(() -> {
            vue3D = new Vue3DController();
            vue3D.initialiser(conteneur3D);
            panneauControle.setVue3DController(vue3D);
        });

        // Confirmation fermeture fenêtre via la croix
        stage.setOnCloseRequest(e -> {
            e.consume();
            demanderConfirmationQuitter();
        });

        return scene;
    }

    // =========================================================================
    //  MENU BAR
    // =========================================================================
    private MenuBar buildMenuBar() {

        menuChargerCircuit  = new MenuItem("Charger circuit AD…");
        menuChargerTypes    = new MenuItem("Charger types aéronefs…");
        menuChargerAeronefs = new MenuItem("Charger aéronefs…");
        menuQuitter         = new MenuItem("Quitter");

        menuChargerCircuit .setOnAction(e -> chargerFichierCircuit());
        menuChargerTypes   .setOnAction(e -> chargerFichierTypes());
        menuChargerAeronefs.setOnAction(e -> chargerFichierAeronefs());
        menuQuitter        .setOnAction(e -> demanderConfirmationQuitter());

        Menu menuFichier = new Menu("Fichier");
        menuFichier.getItems().addAll(
                menuChargerCircuit,
                menuChargerTypes,
                menuChargerAeronefs,
                new SeparatorMenuItem(),
                menuQuitter
        );

        menuVueHaute = new MenuItem("Vue haute");
        menuVueBasse = new MenuItem("Vue basse");

        menuVueHaute.setOnAction(e -> {
            if (panneauControle != null) panneauControle.activerVueHaute();
        });
        menuVueBasse.setOnAction(e -> {
            if (panneauControle != null) panneauControle.activerVueBasse();
        });

        Menu menuCamera = new Menu("Caméra");
        menuCamera.getItems().addAll(menuVueHaute, menuVueBasse);

        menuParametres = new MenuItem("Paramètres…");
        menuParametres.setOnAction(e -> ouvrirParametres());

        Menu menuParam = new Menu("Paramètres");
        menuParam.getItems().add(menuParametres);

        menuAide = new MenuItem("À propos…");
        menuAide.setOnAction(e -> ouvrirAPropos());

        Menu menuAideMenu = new Menu("Aide");
        menuAideMenu.getItems().add(menuAide);

        MenuBar bar = new MenuBar();
        bar.getMenus().addAll(menuFichier, menuCamera, menuParam, menuAideMenu);
        return bar;
    }

    // =========================================================================
    //  TOOLBAR LECTURE
    // =========================================================================
    private ToolBar buildToolBar() {
        btnPlay  = new Button("▶");
        btnPause = new Button("⏸");
        btnStop  = new Button("⏹");
        btnPlay .setPrefSize(40, 32);
        btnPause.setPrefSize(40, 32);
        btnStop .setPrefSize(40, 32);
        btnPlay .getStyleClass().add("btn-lecture");
        btnPause.getStyleClass().add("btn-lecture");
        btnStop .getStyleClass().add("btn-lecture");

        btnPause.setDisable(true);
        btnStop .setDisable(true);

        btnPlay .setOnAction(e -> actionPlay());
        btnPause.setOnAction(e -> actionPause());
        btnStop .setOnAction(e -> actionStop());

        Label lblTempsLabel = new Label("Temps :");
        labelTemps = new Label("00:00:00");
        labelTemps.getStyleClass().add("label-temps");

        sliderTemps = new Slider(0, 3600, 0);
        sliderTemps.setShowTickLabels(true);
        sliderTemps.setShowTickMarks(true);
        sliderTemps.setMajorTickUnit(600);
        sliderTemps.setMinorTickCount(9);
        sliderTemps.setPrefWidth(400);
        sliderTemps.setLabelFormatter(new javafx.util.StringConverter<>() {
            @Override public String toString(Double v) {
                int h = v.intValue() / 3600;
                int m = (v.intValue() % 3600) / 60;
                return String.format("%02d:%02d", h, m);
            }
            @Override public Double fromString(String s) { return 0.0; }
        });
        HBox.setHgrow(sliderTemps, Priority.ALWAYS);

        sliderTemps.valueProperty().addListener((obs, oldV, newV) ->
                labelTemps.setText(formaterTemps(newV.intValue()))
        );

        Separator sep1 = new Separator(Orientation.VERTICAL);
        Separator sep2 = new Separator(Orientation.VERTICAL);

        return new ToolBar(
                btnPlay, btnPause, btnStop,
                sep1,
                lblTempsLabel, labelTemps,
                sep2,
                sliderTemps
        );
    }

    // =========================================================================
    //  CENTRE
    // =========================================================================

    private SplitPane buildCenter() {
        conteneur3D = new StackPane();
        conteneur3D.getStyleClass().add("conteneur-3d");

        // Placeholder visible avant le chargement des fichiers
        Label placeholder = new Label("Vue 3D — circuit aérodrome");
        placeholder.getStyleClass().add("label-3d-placeholder");
        conteneur3D.getChildren().add(placeholder);

        // ── Panneau de contrôle ──────────────────────────────────────────────
        panneauControle = new PanneauControleController(this);
        ScrollPane scrollPanneau = new ScrollPane(panneauControle.buildView());
        scrollPanneau.setFitToWidth(true);
        scrollPanneau.setMinWidth(280);
        scrollPanneau.setPrefWidth(340);
        scrollPanneau.getStyleClass().add("panneau-scroll");

        SplitPane split = new SplitPane(conteneur3D, scrollPanneau);
        split.setDividerPositions(0.68);
        return split;
    }

    // =========================================================================
    //  STATUS BAR
    // =========================================================================
    private HBox buildStatusBar() {
        labelStatut = new Label("Prêt.");
        labelStatut.getStyleClass().add("label-statut");

        HBox bar = new HBox(labelStatut);
        bar.getStyleClass().add("status-bar");
        bar.setPadding(new Insets(4, 10, 4, 10));
        return bar;
    }

    // =========================================================================
    //  ACTIONS FICHIER
    // =========================================================================

    private void chargerFichierCircuit() {
        File f = choisirFichier("Charger le circuit AD");
        if (f != null) {
            fichierCircuit = f;
            setStatut("Circuit chargé : " + f.getName());
        }
    }

    private void chargerFichierTypes() {
        File f = choisirFichier("Charger les types d'aéronefs");
        if (f != null) {
            fichierTypes = f;
            setStatut("Types aéronefs chargés : " + f.getName());
        }
    }

    private void chargerFichierAeronefs() {
        File f = choisirFichier("Charger les aéronefs");
        if (f != null) {
            fichierAeronefs = f;
            setStatut("Aéronefs chargés : " + f.getName());
        }
    }

    private File choisirFichier(String titre) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(titre);
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Fichiers texte (*.txt)", "*.txt"),
                new FileChooser.ExtensionFilter("Tous les fichiers (*.*)", "*.*")
        );
        return chooser.showOpenDialog(stage);
    }

    // =========================================================================
    //  ACTIONS LECTURE
    // =========================================================================

    private void actionPlay() {
        if (etat == EtatSimulation.ARRET) {
            // Vérifier que les 3 fichiers sont chargés
            if (fichierCircuit == null || fichierTypes == null || fichierAeronefs == null) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.initOwner(stage);
                alert.setTitle("Fichiers manquants");
                alert.setHeaderText("Veuillez charger les 3 fichiers de données.");
                alert.setContentText(
                        (fichierCircuit  == null ? "✗ Circuit AD\n"        : "") +
                        (fichierTypes    == null ? "✗ Types aéronefs\n"    : "") +
                        (fichierAeronefs == null ? "✗ Aéronefs\n"          : "")
                );
                alert.showAndWait();
                return;
            }
            demarrerSimulation();
        } else if (etat == EtatSimulation.PAUSE) {
            reprendreSimulation();
        }
    }

    private void actionPause() {
        if (etat == EtatSimulation.EN_COURS && simulation != null) {
            simulation.mettreEnPause();
            etat = EtatSimulation.PAUSE;
            btnPlay .setDisable(false);
            btnPause.setDisable(true);
            setStatut("Simulation en pause.");
        }
    }

    private void actionStop() {
        arreterSimulation();
    }

    // =========================================================================
    //  SIMULATION
    // =========================================================================

    private void demarrerSimulation() {
        try {
            // Parser les fichiers
            circuit  = ParseurCircuit.charger(fichierCircuit);
            types    = ParseurTypeAeronefs.charger(fichierTypes);
            aeronefs = ParseurAeronefs.charger(fichierAeronefs, types);
        } catch (IOException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.initOwner(stage);
            alert.setTitle("Erreur de lecture");
            alert.setHeaderText("Impossible de lire les fichiers.");
            alert.setContentText(ex.getMessage());
            alert.showAndWait();
            return;
        }

        double distSeuil = panneauControle != null
                ? panneauControle.getDistanceSeuil()
                : 400.0;

        gestConflits = new GestionnaireConflits(distSeuil);
        simulation   = new Simulation(circuit, aeronefs, gestConflits);

        // Afficher le circuit et initialiser les sphères dans la vue 3D
        if (vue3D != null) {
            conteneur3D.getChildren().removeIf(n -> n instanceof Label); // retire placeholder
            vue3D.afficherCircuit(circuit);
            vue3D.initialiserSpheres(aeronefs);
            vue3D.setCameraVueBasse();
        }

        // TickListener : mis à jour depuis le thread simulation → Platform.runLater
        simulation.setTickListener((tempsEcoule, actifs, conflits) ->
            Platform.runLater(() -> {
                // Slider et label temps
                sliderTemps.setValue(Math.min(tempsEcoule, sliderTemps.getMax()));

                // Vue 3D
                if (vue3D != null) {
                    vue3D.rafraichirAeronefs(aeronefs, conflits);
                }

                // Distance seuil dynamique
                if (panneauControle != null) {
                    gestConflits.setDistanceSeuil(panneauControle.getDistanceSeuil());
                }

                // Panneau conflits LCD
                if (panneauControle != null) {
                    if (conflits.isEmpty()) {
                        panneauControle.setConflits("aucun", "—");
                    } else {
                        GestionnaireConflits.Conflit premier = conflits.get(0);
                        panneauControle.setConflits(
                                premier.getA1().getIndicatif() + " / " + premier.getA2().getIndicatif(),
                                String.format("%.0f m", premier.getDistance())
                        );
                    }
                }

                // Fin de simulation automatique
                if (!simulation.isEnCours()) {
                    finDeSimulation();
                }
            })
        );

        // Lancer le thread
        threadSimulation = new Thread(() -> simulation.demarrer());
        threadSimulation.setDaemon(true);
        threadSimulation.setName("Thread-Simulation");
        threadSimulation.start();

        etat = EtatSimulation.EN_COURS;
        btnPlay .setDisable(true);
        btnPause.setDisable(false);
        btnStop .setDisable(false);
        setStatut("Simulation en cours…");
    }

    private void reprendreSimulation() {
        if (simulation != null) {
            simulation.reprendreDepauze();
            etat = EtatSimulation.EN_COURS;
            btnPlay .setDisable(true);
            btnPause.setDisable(false);
            setStatut("Simulation reprise.");
        }
    }

    private void arreterSimulation() {
        if (simulation != null) simulation.arreter();
        if (threadSimulation != null) threadSimulation.interrupt();

        simulation       = null;
        threadSimulation = null;
        aeronefs         = null;
        circuit          = null;

        etat = EtatSimulation.ARRET;
        btnPlay .setDisable(false);
        btnPlay .setText("▶");
        btnPause.setDisable(true);
        btnStop .setDisable(true);
        sliderTemps.setValue(0);

        if (panneauControle != null) panneauControle.reinitialiserConflits();
        setStatut("Simulation arrêtée.");
    }

    /** Appelé depuis le TickListener quand tous les aéronefs ont terminé. */
    private void finDeSimulation() {
        etat = EtatSimulation.ARRET;
        btnPlay .setDisable(false);
        btnPause.setDisable(true);
        btnStop .setDisable(true);
        if (panneauControle != null) panneauControle.setConflits("terminé", "—");
        setStatut("Simulation terminée — tous les aéronefs ont atterri.");
    }

    // =========================================================================
    //  ACTIONS MENUS
    // =========================================================================

    private void demanderConfirmationQuitter() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(stage);
        alert.setTitle("Quitter");
        alert.setHeaderText("Quitter l'application ?");
        alert.setContentText("La simulation en cours sera arrêtée.");
        alert.showAndWait().ifPresent(reponse -> {
            if (reponse == ButtonType.OK) {
                if (simulation != null) simulation.arreter();
                Platform.exit();
            }
        });
    }

    private void ouvrirParametres() {
        Stage fenetre = new Stage();
        fenetre.initOwner(stage);
        fenetre.initModality(javafx.stage.Modality.WINDOW_MODAL);
        fenetre.setTitle("Paramètres");
        fenetre.setResizable(false);

        Label lblDist = new Label("Distance conflit par défaut (m) :");
        TextField tfDist = new TextField(
                panneauControle != null
                        ? String.valueOf((int) panneauControle.getDistanceSeuil())
                        : "400"
        );
        tfDist.setPrefWidth(80);

        Button btnOk     = new Button("OK");
        Button btnAnnuler = new Button("Annuler");
        btnOk.setPrefWidth(80);
        btnAnnuler.setPrefWidth(80);

        btnOk.setOnAction(e -> {
            try {
                double val = Double.parseDouble(tfDist.getText().replace(',', '.'));
                if (val < 50 || val > 2000) throw new NumberFormatException();
                if (panneauControle != null) panneauControle.setDistanceSeuil(val);
                if (gestConflits   != null) gestConflits.setDistanceSeuil(val);
                setStatut("Distance conflit mise à jour : " + (int) val + " m");
                fenetre.close();
            } catch (NumberFormatException ex) {
                tfDist.setStyle("-fx-border-color: red;");
                tfDist.setTooltip(new Tooltip("Valeur entre 50 et 2000"));
            }
        });
        btnAnnuler.setOnAction(e -> fenetre.close());

        HBox boutons = new HBox(10, btnOk, btnAnnuler);
        boutons.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        VBox racine = new VBox(12,
                new HBox(8, lblDist, tfDist),
                boutons
        );
        racine.setPadding(new Insets(20));
        racine.setPrefWidth(380);

        fenetre.setScene(new Scene(racine));
        fenetre.showAndWait();
    }

    private void ouvrirAPropos() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(stage);
        alert.setTitle("À propos");
        alert.setHeaderText("Simulation Tour de Piste");
        alert.setContentText(
                "Application de simulation de trafic aérodrome.\n\n" +
                "Gestion des circuits AD, détection de conflits\n" +
                "et pilotage du matériel Raspberry Pi.\n\n" +
                "IUT — Projet BE12"
        );
        alert.showAndWait();
    }

    // =========================================================================
    //  UTILITAIRES
    // =========================================================================

    /** Met à jour la barre de statut (appelable depuis les sous-contrôleurs). */
    public void setStatut(String message) {
        if (labelStatut != null) labelStatut.setText(message);
    }

    private String formaterTemps(int secondes) {
        int h = secondes / 3600;
        int m = (secondes % 3600) / 60;
        int s = secondes % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    public File  getFichierCircuit()  { return fichierCircuit;  }
    public File  getFichierTypes()    { return fichierTypes;     }
    public File  getFichierAeronefs() { return fichierAeronefs; }
    public Stage getStage()           { return stage;           }
}