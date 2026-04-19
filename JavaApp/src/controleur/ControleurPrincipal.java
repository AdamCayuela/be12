package controleur;

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
import vue.Vue3D;
import vue.VuePanneauControle;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * CONTRÔLEUR PRINCIPAL de l'application.
 *
 * Responsabilité MVC : contrôleur.
 *   - Reçoit les événements utilisateur (menus, boutons, slider).
 *   - Traduit ces événements en actions sur le modèle (Simulation).
 *   - Le modèle (Simulation) notifie ce contrôleur via TickListener.
 *   - Le contrôleur met ensuite à jour les vues (Vue3D, VuePanneauControle).
 *
 * Cycle MVC à chaque tick :
 *   Simulation (modèle) → onTick() → ControleurPrincipal → rafraichirAeronefs() → Vue3D
 *                                                         → setConflits()        → VuePanneauControle
 *                                                         → mettreAJour()        → GestionnaireLEDs / LCD
 */
public class ControleurPrincipal {

    private final Stage stage;

    // ── Fichiers chargés ─────────────────────────────────────────────────────
    private File fichierCircuit;
    private File fichierTypes;
    private File fichierAeronefs;
    private File fichierModele3D;
    private final java.util.Map<String, File> modeles3DParType = new java.util.HashMap<>();
    private List<TypeAeronef> typesCharges = new java.util.ArrayList<>();

    // ── État de la simulation ────────────────────────────────────────────────
    private enum EtatSimulation { ARRET, EN_COURS, PAUSE }
    private EtatSimulation etat = EtatSimulation.ARRET;

    // ── Modèle ───────────────────────────────────────────────────────────────
    private CircuitAD            circuit;
    private List<TypeAeronef>    types;
    private List<Aeronef>        aeronefs;
    private GestionnaireConflits gestConflits;
    private Simulation           simulation;
    private Thread               threadSimulation;

    // ── Matériel RPi ─────────────────────────────────────────────────────────
    private modele.GestionnaireLEDs    gestLEDs    = new modele.GestionnaireLEDs();
    private modele.GestionnaireLCD     gestLCD     = new modele.GestionnaireLCD();
    private modele.GestionnaireBoutons gestBoutons = null; // init après buildScene

    private double dureeSimulation = 1.0;

    // ── Éléments de la barre de menus ────────────────────────────────────────
    private MenuItem menuChargerCircuit;
    private MenuItem menuChargerTypes;
    private MenuItem menuChargerAeronefs;
    private MenuItem menuQuitter;
    private Menu     menuSelection3D;
    private MenuItem menuVueHaute;
    private MenuItem menuVueBasse;
    private MenuItem menuParametres;
    private MenuItem menuAide;

    // ── Contrôles de la barre de lecture ─────────────────────────────────────
    Button            btnPlay;
    Button            btnPause;
    Button            btnStop;
    Label             labelTemps;
    Slider            sliderTemps;
    ChoiceBox<String> choixVitesse;
    private double    vitesseFacteur    = 1.0;
    private boolean   enMiseAJourSlider = false;

    // ── Vues ─────────────────────────────────────────────────────────────────
    StackPane         conteneur3D;
    Vue3D             vue3D;
    VuePanneauControle panneau;

    // ── Barre de statut ───────────────────────────────────────────────────────
    Label labelStatut;

    // ─────────────────────────────────────────────────────────────────────────

    public ControleurPrincipal(Stage stage) {
        this.stage = stage;
    }

    /** Construit et retourne la scène principale. */
    public Scene buildScene() {
        BorderPane root = new BorderPane();

        VBox topBox = new VBox(buildMenuBar(), buildToolBar());
        root.setTop(topBox);
        root.setCenter(buildCenter());
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root, 1280, 800);

        // La vue 3D doit être initialisée une fois le panneau attaché au graphe de scène
        Platform.runLater(() -> {
            vue3D = new Vue3D();
            vue3D.initialiser(conteneur3D);
            panneau.setVue3D(vue3D);

            // Init boutons GPIO physiques (après vue3D)
            gestBoutons = new modele.GestionnaireBoutons(
                (dx, dz) -> { if (vue3D != null) vue3D.deplacerCamera(dx, dz); },
                (dir, appuye) -> {
                    if (panneau == null) return;
                    switch (dir) {
                        case HAUT   -> { if (appuye) panneau.activerVoyant(panneau.voyantNord);
                                         else        panneau.desactiverVoyant(panneau.voyantNord); }
                        case BAS    -> { if (appuye) panneau.activerVoyant(panneau.voyantSud);
                                         else        panneau.desactiverVoyant(panneau.voyantSud); }
                        case GAUCHE -> { if (appuye) panneau.activerVoyant(panneau.voyantOuest);
                                         else        panneau.desactiverVoyant(panneau.voyantOuest); }
                        case DROITE -> { if (appuye) panneau.activerVoyant(panneau.voyantEst);
                                         else        panneau.desactiverVoyant(panneau.voyantEst); }
                    }
                }
            );
        });

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

        menuVueHaute.setOnAction(e -> { if (panneau != null) panneau.activerVueHaute(); });
        menuVueBasse.setOnAction(e -> { if (panneau != null) panneau.activerVueBasse(); });

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

        menuSelection3D = new Menu("Sélection 3D");
        reconstruireMenuSelection3D();

        MenuBar bar = new MenuBar();
        bar.getMenus().addAll(menuFichier, menuSelection3D, menuCamera, menuParam, menuAideMenu);
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
        labelTemps = new Label("00:00");
        labelTemps.getStyleClass().add("label-temps");

        sliderTemps = new Slider(0, 24, 0);
        sliderTemps.setShowTickLabels(true);
        sliderTemps.setShowTickMarks(true);
        sliderTemps.setMajorTickUnit(2);
        sliderTemps.setMinorTickCount(1);
        sliderTemps.setSnapToTicks(false);
        sliderTemps.setPrefWidth(400);
        sliderTemps.setLabelFormatter(new javafx.util.StringConverter<>() {
            @Override public String toString(Double v)   { return String.format("%02.0fh", v); }
            @Override public Double fromString(String s) { return 0.0; }
        });
        HBox.setHgrow(sliderTemps, Priority.ALWAYS);

        sliderTemps.valueProperty().addListener((obs, oldV, newV) -> {
            labelTemps.setText(formaterPositionSlider(newV.doubleValue()));
            if (!enMiseAJourSlider && simulation != null && etat != EtatSimulation.ARRET) {
                double secondesSimulees = (newV.doubleValue() / 24.0) * dureeSimulation;
                simulation.requestSeek(secondesSimulees);
            }
        });

        ChoiceBox<String> choixVitesse = new ChoiceBox<>();
        choixVitesse.getItems().addAll("×0.25", "×0.5", "×1", "×1.5", "×2");
        choixVitesse.setValue("×1");
        choixVitesse.setPrefWidth(75);
        choixVitesse.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            double facteur = switch (newV) {
                case "×0.25" -> 0.25;
                case "×0.5"  -> 0.5;
                case "×1.5"  -> 1.5;
                case "×2"    -> 2.0;
                default      -> 1.0;
            };
            if (simulation != null) simulation.setSimSpeed(20.0 * facteur);
            vitesseFacteur = facteur;
        });
        this.choixVitesse = choixVitesse;

        return new ToolBar(
                btnPlay, btnPause, btnStop,
                new Separator(Orientation.VERTICAL),
                lblTempsLabel, labelTemps,
                new Separator(Orientation.VERTICAL),
                sliderTemps,
                new Separator(Orientation.VERTICAL),
                choixVitesse
        );
    }

    // =========================================================================
    //  CENTRE
    // =========================================================================
    private SplitPane buildCenter() {
        conteneur3D = new StackPane();
        conteneur3D.getStyleClass().add("conteneur-3d");

        Label placeholder = new Label("Vue 3D — circuit aérodrome");
        placeholder.getStyleClass().add("label-3d-placeholder");
        conteneur3D.getChildren().add(placeholder);

        panneau = new VuePanneauControle(this);
        ScrollPane scrollPanneau = new ScrollPane(panneau.buildView());
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
            try {
                typesCharges = ParseurTypeAeronefs.charger(f);
                modeles3DParType.clear();
                reconstruireMenuSelection3D();
                setStatut("Types aéronefs chargés : " + f.getName()
                        + " (" + typesCharges.size() + " types)");
            } catch (IOException ex) {
                setStatut("Erreur lecture types : " + ex.getMessage());
            }
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
            if (fichierCircuit == null || fichierTypes == null || fichierAeronefs == null) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.initOwner(stage);
                alert.setTitle("Fichiers manquants");
                alert.setHeaderText("Veuillez charger les 3 fichiers de données.");
                alert.setContentText(
                        (fichierCircuit  == null ? "✗ Circuit AD\n"     : "") +
                        (fichierTypes    == null ? "✗ Types aéronefs\n" : "") +
                        (fichierAeronefs == null ? "✗ Aéronefs\n"       : "")
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
    //  MENU SÉLECTION 3D
    // =========================================================================

    private void reconstruireMenuSelection3D() {
        menuSelection3D.getItems().clear();

        MenuItem itemGlobalCharger = new MenuItem("Charger modèle global (tous types)…");
        MenuItem itemGlobalRetirer = new MenuItem("Retirer le modèle global");
        itemGlobalRetirer.setDisable(fichierModele3D == null);

        itemGlobalCharger.setOnAction(e -> {
            File f = choisirFichierObj("Modèle global — tous les types");
            if (f != null) {
                fichierModele3D = f;
                itemGlobalRetirer.setDisable(false);
                itemGlobalCharger.setText("Global : " + f.getName());
                if (vue3D != null) vue3D.setModele3D(f);
                setStatut("Modèle global : " + f.getName());
            }
        });
        itemGlobalRetirer.setOnAction(e -> {
            fichierModele3D = null;
            itemGlobalRetirer.setDisable(true);
            itemGlobalCharger.setText("Charger modèle global (tous types)…");
            if (vue3D != null) vue3D.setModele3D(null);
            setStatut("Modèle global retiré — sphères par défaut");
        });

        menuSelection3D.getItems().addAll(itemGlobalCharger, itemGlobalRetirer);

        if (!typesCharges.isEmpty()) {
            menuSelection3D.getItems().add(new SeparatorMenuItem());

            for (TypeAeronef type : typesCharges) {
                String nomType = type.getNom();

                MenuItem itemCharger = new MenuItem("  [" + nomType + "]  Charger .obj…");
                MenuItem itemRetirer = new MenuItem("  [" + nomType + "]  Retirer");
                itemRetirer.setDisable(!modeles3DParType.containsKey(nomType));

                itemCharger.setOnAction(e -> {
                    File f = choisirFichierObj("Modèle pour " + nomType);
                    if (f != null) {
                        modeles3DParType.put(nomType, f);
                        itemRetirer.setDisable(false);
                        itemCharger.setText("  [" + nomType + "]  " + f.getName());
                        if (vue3D != null) vue3D.setModele3DPourType(nomType, f);
                        setStatut("Modèle « " + nomType + " » : " + f.getName());
                    }
                });
                itemRetirer.setOnAction(e -> {
                    modeles3DParType.remove(nomType);
                    itemRetirer.setDisable(true);
                    itemCharger.setText("  [" + nomType + "]  Charger .obj…");
                    if (vue3D != null) vue3D.setModele3DPourType(nomType, null);
                    setStatut("Modèle « " + nomType + " » retiré");
                });

                menuSelection3D.getItems().addAll(itemCharger, itemRetirer);
            }
        } else {
            MenuItem infoItem = new MenuItem("(charger les types d'abord)");
            infoItem.setDisable(true);
            menuSelection3D.getItems().addAll(new SeparatorMenuItem(), infoItem);
        }
    }

    private File choisirFichierObj(String titre) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(titre);
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Modèles 3D (*.obj)", "*.obj"),
                new FileChooser.ExtensionFilter("Tous les fichiers (*.*)", "*.*")
        );
        return chooser.showOpenDialog(stage);
    }

    // =========================================================================
    //  SIMULATION  (orchestration modèle ↔ vues)
    // =========================================================================

    private void demarrerSimulation() {
        try {
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

        dureeSimulation = calculerDureeSimulation();

        double distSeuil = panneau != null ? panneau.getDistanceSeuil() : 400.0;
        gestConflits = new GestionnaireConflits(distSeuil);
        simulation   = new Simulation(circuit, aeronefs, gestConflits);
        simulation.setSimSpeed(20.0 * vitesseFacteur);

        if (vue3D != null) {
            conteneur3D.getChildren().removeIf(n -> n instanceof Label l && !"labelCamPos".equals(l.getId()));
            vue3D.setModele3D(fichierModele3D);
            vue3D.setModeles3DParType(modeles3DParType);
            vue3D.afficherCircuit(circuit);
            vue3D.initialiserSpheres(aeronefs);
            vue3D.setCameraVueBasse();
        }

        // ── Notification du modèle → mise à jour des vues ───────────────────
        // Le modèle (Simulation) appelle onTick() à chaque pas de temps.
        // Le contrôleur reçoit la notification et met à jour toutes les vues.
        simulation.setTickListener((tempsEcoule, actifs, conflits) ->
            Platform.runLater(() -> {

                // ── Avancer le slider ────────────────────────────────────────
                enMiseAJourSlider = true;
                double posSlider = (dureeSimulation > 0)
                        ? Math.min((tempsEcoule / dureeSimulation) * 24.0, 24.0) : 0;
                sliderTemps.setValue(posSlider);
                enMiseAJourSlider = false;

                // ── Mettre à jour la vue 3D ──────────────────────────────────
                if (vue3D != null) {
                    vue3D.rafraichirAeronefs(aeronefs, conflits, gestConflits.getProximites());
                }

                // ── Relire la distance de conflit ────────────────────────────
                if (panneau != null) {
                    gestConflits.setDistanceSeuil(panneau.getDistanceSeuil());
                }

                // ── Mettre à jour le panneau de contrôle ─────────────────────
                if (panneau != null) {
                    if (conflits.isEmpty()) {
                        panneau.setConflits("aucun", "—");
                    } else {
                        GestionnaireConflits.Conflit premier = conflits.get(0);
                        panneau.setConflits(
                                premier.getA1().getIndicatif() + " / " + premier.getA2().getIndicatif(),
                                String.format("%.0f m", premier.getDistance())
                        );
                    }

                    boolean rouge  = !conflits.isEmpty();
                    boolean orange = !gestConflits.getProximites().isEmpty();
                    boolean verte  = !rouge && !orange;

                    if (rouge)  panneau.allumerLed(panneau.ledRouge, "led-rouge");
                    else        panneau.eteindreLed(panneau.ledRouge);

                    if (orange) panneau.allumerLed(panneau.ledJaune, "led-orange");
                    else        panneau.eteindreLed(panneau.ledJaune);

                    if (verte)  panneau.allumerLed(panneau.ledVerte, "led-verte");
                    else        panneau.eteindreLed(panneau.ledVerte);

                    panneau.setBuzzerActif(rouge);

                    // ── LEDs + buzzer physiques RPi ──────────────────────────
                    gestLEDs.mettreAJour(rouge, orange, !panneau.isBuzzerActif());

                    // ── LCD I2C ──────────────────────────────────────────────
                    if (rouge && !conflits.isEmpty()) {
                        GestionnaireConflits.Conflit c = conflits.get(0);
                        gestLCD.afficherConflit(c.getA1().getIndicatif(),
                                                c.getA2().getIndicatif(),
                                                c.getDistance());
                    } else if (orange && !gestConflits.getProximites().isEmpty()) {
                        GestionnaireConflits.Conflit c = gestConflits.getProximites().get(0);
                        gestLCD.afficherProximite(c.getA1().getIndicatif(),
                                                  c.getA2().getIndicatif(),
                                                  c.getDistance());
                    } else {
                        gestLCD.afficherRAS();
                    }
                }

                if (!simulation.isEnCours()) {
                    finDeSimulation();
                }
            })
        );

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
        if (choixVitesse != null) choixVitesse.setValue("×1");
        vitesseFacteur = 1.0;

        if (panneau != null) {
            panneau.reinitialiserConflits();
            panneau.eteindreLed(panneau.ledRouge);
            panneau.eteindreLed(panneau.ledJaune);
            panneau.eteindreLed(panneau.ledVerte);
        }
        gestLEDs.eteindreTout();
        gestLCD.afficherRAS();
        setStatut("Simulation arrêtée.");
    }

    /** Appelé automatiquement quand le dernier avion a atterri. */
    private void finDeSimulation() {
        etat = EtatSimulation.ARRET;
        btnPlay .setDisable(false);
        btnPause.setDisable(true);
        btnStop .setDisable(true);
        if (panneau != null) panneau.setConflits("terminé", "—");
        gestLEDs.eteindreTout();
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
                gestLEDs.fermer();
                gestLCD.fermer();
                if (gestBoutons != null) gestBoutons.fermer();
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
                panneau != null ? String.valueOf((int) panneau.getDistanceSeuil()) : "400"
        );
        tfDist.setPrefWidth(80);

        Button btnOk      = new Button("OK");
        Button btnAnnuler = new Button("Annuler");
        btnOk.setPrefWidth(80);
        btnAnnuler.setPrefWidth(80);

        btnOk.setOnAction(e -> {
            try {
                double val = Double.parseDouble(tfDist.getText().replace(',', '.'));
                if (val < 50 || val > 2000) throw new NumberFormatException();
                if (panneau      != null) panneau.setDistanceSeuil(val);
                if (gestConflits != null) gestConflits.setDistanceSeuil(val);
                setStatut("Distance conflit mise à jour : " + (int) val + " m");
                fenetre.close();
            } catch (NumberFormatException ex) {
                tfDist.setStyle("-fx-border-color: red;");
                tfDist.setTooltip(new Tooltip("Valeur entre 50 et 2000"));
            }
        });
        btnAnnuler.setOnAction(e -> fenetre.close());

        HBox boutons = new HBox(10, btnOk, btnAnnuler);
        boutons.setAlignment(Pos.CENTER_RIGHT);

        VBox racine = new VBox(12, new HBox(8, lblDist, tfDist), boutons);
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

    /** Met à jour le message affiché en bas de fenêtre. */
    public void setStatut(String message) {
        if (labelStatut != null) labelStatut.setText(message);
    }

    private double calculerDureeSimulation() {
        if (circuit == null || aeronefs == null || aeronefs.isEmpty()) return 1.0;

        double longueurNormal = 0;
        for (CircuitAD.Segment s : circuit.getSegmentsNormal()) longueurNormal += s.getLongueur();

        double longueurFinal = 0;
        for (CircuitAD.Segment s : circuit.getSegmentsFinal()) longueurFinal += s.getLongueur();

        double dureeMax = 1.0;
        for (Aeronef a : aeronefs) {
            double vitesse = a.getType().getVitesseMps();
            if (vitesse <= 0) continue;
            int toursNormaux = Math.max(0, a.getNbToursMax() - 1);
            double distTotale = toursNormaux * longueurNormal + longueurFinal;
            double fin = a.getTempsDepart() + distTotale / vitesse;
            if (fin > dureeMax) dureeMax = fin;
        }
        return dureeMax;
    }

    private String formaterPositionSlider(double heures) {
        int h = (int) heures;
        int m = (int) Math.round((heures - h) * 60);
        if (m == 60) { h++; m = 0; }
        return String.format("%02d:%02d", h, m);
    }

    public File  getFichierCircuit()  { return fichierCircuit;  }
    public File  getFichierTypes()    { return fichierTypes;     }
    public File  getFichierAeronefs() { return fichierAeronefs; }
    public Stage getStage()           { return stage;           }
}
