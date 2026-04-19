package vue;

import controleur.ControleurPrincipal;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/**
 * VUE du panneau de contrôle latéral.
 *
 * Responsabilité MVC : vue pure.
 *   - Construit et affiche les sections caméra, LEDs, boutons, buzzer, conflits.
 *   - Expose des méthodes de mise à jour appelées par le contrôleur.
 *   - Ne contient pas de logique métier ; délègue les actions au ControleurPrincipal.
 */
public class VuePanneauControle {

    private final ControleurPrincipal controleur;
    private Vue3D vue3D;

    private static final double PAS_DEPLACEMENT = 30.0;
    private static final double PAS_ZOOM        = 50.0;

    public void setVue3D(Vue3D v) { this.vue3D = v; }

    // ── Section 1 : Caméra ───────────────────────────────────────────────────
    CheckBox checkVueHaute;
    CheckBox checkVueBasse;

    Button btnCamNO,     btnCamHaut,    btnCamNE;
    Button btnCamGauche, btnZoomPlus,   btnCamDroite;
    Button btnCamSO,     btnCamBas,     btnCamSE;
    Button btnZoomMoins;

    // ── Section 2 : Distance conflit ─────────────────────────────────────────
    TextField tfDistanceSeuil;
    Slider    sliderDistance;
    private boolean enMiseAJour = false;

    // ── Section 3 : LEDs ─────────────────────────────────────────────────────
    Circle ledRouge;
    Circle ledJaune;
    Circle ledVerte;

    // ── Section 4 : Boutons poussoirs ────────────────────────────────────────
    Circle voyantNord;
    Circle voyantSud;
    Circle voyantOuest;
    Circle voyantEst;

    // ── Section 5 : Buzzer ───────────────────────────────────────────────────
    Label  labelBuzzer;
    Button btnToggleBuzzer;
    private boolean buzzerActif = true;

    // ── Section 6 : Conflits ─────────────────────────────────────────────────
    Label labelPremierVol;
    Label labelConflitProche;

    // ─────────────────────────────────────────────────────────────────────────

    public VuePanneauControle(ControleurPrincipal controleur) {
        this.controleur = controleur;
    }

    /** Construit et retourne le VBox du panneau complet. */
    public VBox buildView() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));
        root.getStyleClass().add("panneau-controle");

        root.getChildren().addAll(
                buildSectionCamera(),
                buildSectionDistance(),
                buildSectionLeds(),
                buildSectionBoutons(),
                buildSectionBuzzer(),
                buildSectionConflits()
        );

        return root;
    }

    // =========================================================================
    //  SECTION 1 — CAMÉRA
    // =========================================================================
    private TitledPane buildSectionCamera() {

        checkVueHaute = new CheckBox("vue haute (plongeante)");
        checkVueBasse = new CheckBox("vue basse (piste)");

        checkVueHaute.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isSelected) {
                checkVueBasse.setSelected(false);
                controleur.setStatut("Vue : plongeante");
                if (vue3D != null) vue3D.setCameraVueHaute();
            } else if (!checkVueBasse.isSelected()) {
                controleur.setStatut("Vue : contre-plongée");
                if (vue3D != null) vue3D.setCameraVueHaute();
            }
        });

        checkVueBasse.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isSelected) {
                checkVueHaute.setSelected(false);
                controleur.setStatut("Vue : niveau piste");
                if (vue3D != null) vue3D.setCameraVueBasse();
            } else if (!checkVueHaute.isSelected()) {
                controleur.setStatut("Vue : contre-plongée");
                if (vue3D != null) vue3D.setCameraVueBasse();
            }
        });

        HBox checkBoxRow = new HBox(20, checkVueHaute, checkVueBasse);
        checkBoxRow.setAlignment(Pos.CENTER);

        btnCamNO     = camBtn("↖"); btnCamHaut    = camBtn("↑"); btnCamNE    = camBtn("↗");
        btnCamGauche = camBtn("←"); btnZoomPlus   = camBtn("⊕"); btnCamDroite= camBtn("→");
        btnCamSO     = camBtn("↙"); btnCamBas     = camBtn("↓"); btnCamSE    = camBtn("↘");
        btnZoomMoins = camBtn("⊖");

        btnCamHaut   .setTooltip(new Tooltip("Déplacer vers le haut"));
        btnCamBas    .setTooltip(new Tooltip("Déplacer vers le bas"));
        btnCamGauche .setTooltip(new Tooltip("Déplacer vers la gauche"));
        btnCamDroite .setTooltip(new Tooltip("Déplacer vers la droite"));
        btnZoomPlus  .setTooltip(new Tooltip("Zoom avant"));
        btnZoomMoins .setTooltip(new Tooltip("Zoom arrière"));

        btnCamHaut  .setOnAction(e -> { controleur.setStatut("Caméra ↑");    if (vue3D!=null) vue3D.deplacerCamera(0,  -PAS_DEPLACEMENT); });
        btnCamBas   .setOnAction(e -> { controleur.setStatut("Caméra ↓");    if (vue3D!=null) vue3D.deplacerCamera(0,   PAS_DEPLACEMENT); });
        btnCamGauche.setOnAction(e -> { controleur.setStatut("Caméra ←");    if (vue3D!=null) vue3D.deplacerCamera(-PAS_DEPLACEMENT, 0); });
        btnCamDroite.setOnAction(e -> { controleur.setStatut("Caméra →");    if (vue3D!=null) vue3D.deplacerCamera( PAS_DEPLACEMENT, 0); });
        btnCamNO    .setOnAction(e -> { controleur.setStatut("Caméra ← ↑");  if (vue3D!=null) vue3D.deplacerCamera(-PAS_DEPLACEMENT, -PAS_DEPLACEMENT); });
        btnCamNE    .setOnAction(e -> { controleur.setStatut("Caméra → ↑");  if (vue3D!=null) vue3D.deplacerCamera( PAS_DEPLACEMENT, -PAS_DEPLACEMENT); });
        btnCamSO    .setOnAction(e -> { controleur.setStatut("Caméra ← ↓");  if (vue3D!=null) vue3D.deplacerCamera(-PAS_DEPLACEMENT,  PAS_DEPLACEMENT); });
        btnCamSE    .setOnAction(e -> { controleur.setStatut("Caméra → ↓");  if (vue3D!=null) vue3D.deplacerCamera( PAS_DEPLACEMENT,  PAS_DEPLACEMENT); });
        btnZoomPlus .setOnAction(e -> { controleur.setStatut("Zoom +");       if (vue3D!=null) vue3D.zoomer( PAS_ZOOM); });
        btnZoomMoins.setOnAction(e -> { controleur.setStatut("Zoom −");       if (vue3D!=null) vue3D.zoomer(-PAS_ZOOM); });

        GridPane grid = new GridPane();
        grid.setHgap(4); grid.setVgap(4);
        grid.setAlignment(Pos.CENTER);
        grid.add(btnCamNO,     0, 0); grid.add(btnCamHaut,    1, 0); grid.add(btnCamNE,     2, 0);
        grid.add(btnCamGauche, 0, 1);                                 grid.add(btnCamDroite, 2, 1);
        grid.add(btnCamSO,     0, 2); grid.add(btnCamBas,     1, 2); grid.add(btnCamSE,     2, 2);

        VBox zoomBox = new VBox(4, btnZoomPlus, btnZoomMoins);
        zoomBox.setAlignment(Pos.CENTER);

        HBox paveZoom = new HBox(10, grid, zoomBox);
        paveZoom.setAlignment(Pos.CENTER);

        VBox content = new VBox(8, checkBoxRow, paveZoom);
        content.setPadding(new Insets(8));
        content.setAlignment(Pos.CENTER);

        return sectionPane("Déplacement de la caméra", content, "section-camera");
    }

    // =========================================================================
    //  SECTION 2 — DISTANCE CONFLIT
    // =========================================================================
    private TitledPane buildSectionDistance() {

        Label titre = new Label("Distance conflit :");
        titre.getStyleClass().add("section-titre");

        tfDistanceSeuil = new TextField("400");
        tfDistanceSeuil.setPrefWidth(72);
        tfDistanceSeuil.setAlignment(Pos.CENTER_RIGHT);

        sliderDistance = new Slider(50, 2000, 400);
        sliderDistance.setShowTickLabels(true);
        sliderDistance.setShowTickMarks(true);
        sliderDistance.setMajorTickUnit(500);
        sliderDistance.setMinorTickCount(4);
        sliderDistance.setBlockIncrement(50);

        sliderDistance.valueProperty().addListener((obs, oldV, newV) -> {
            if (enMiseAJour) return;
            enMiseAJour = true;
            int v = (int) Math.round(newV.doubleValue());
            tfDistanceSeuil.setText(String.valueOf(v));
            tfDistanceSeuil.setStyle("");
            controleur.setStatut("Distance conflit : " + v + " m");
            enMiseAJour = false;
        });

        Runnable validerTextField = () -> {
            if (enMiseAJour) return;
            String texte = tfDistanceSeuil.getText().trim().replace(',', '.');
            try {
                double val = Double.parseDouble(texte);
                if (val < 50 || val > 2000) throw new NumberFormatException();
                enMiseAJour = true;
                sliderDistance.setValue(val);
                tfDistanceSeuil.setText(String.valueOf((int) val));
                tfDistanceSeuil.setStyle("");
                controleur.setStatut("Distance conflit : " + (int) val + " m");
                enMiseAJour = false;
            } catch (NumberFormatException ex) {
                tfDistanceSeuil.setStyle("-fx-border-color: #cc3333; -fx-border-width: 2;");
                tfDistanceSeuil.setTooltip(new Tooltip("Entrez un nombre entre 50 et 2000"));
            }
        };

        tfDistanceSeuil.setOnAction(e -> validerTextField.run());
        tfDistanceSeuil.focusedProperty().addListener((obs, hadFocus, hasFocus) -> {
            if (!hasFocus) validerTextField.run();
        });

        HBox ligne = new HBox(8,
                new Label("Distance souhaitée :"),
                tfDistanceSeuil,
                new Label("m")
        );
        ligne.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(8, titre, ligne, sliderDistance);
        content.setPadding(new Insets(8));

        return sectionPane("Distance conflit", content, "section-distance");
    }

    // =========================================================================
    //  SECTION 3 — LEDs
    // =========================================================================
    private TitledPane buildSectionLeds() {
        Label titre = new Label("État des LEDs :");
        titre.getStyleClass().add("section-titre");

        ledRouge = new Circle(14);
        ledJaune = new Circle(14);
        ledVerte = new Circle(14);

        ledRouge.getStyleClass().addAll("led", "led-rouge");
        ledJaune.getStyleClass().addAll("led", "led-orange");
        ledVerte.getStyleClass().addAll("led", "led-verte");

        setLedEteinte(ledRouge);
        setLedEteinte(ledJaune);
        setLedEteinte(ledVerte);

        HBox leds = new HBox(20,
                ledGroup(ledRouge, "ROUGE"),
                ledGroup(ledJaune, "ORANGE"),
                ledGroup(ledVerte, "VERT")
        );
        leds.setAlignment(Pos.CENTER);

        VBox content = new VBox(8, titre, leds);
        content.setPadding(new Insets(8));
        content.setAlignment(Pos.CENTER);

        return sectionPane("État des LEDs", content, "section-leds");
    }

    // =========================================================================
    //  SECTION 4 — BOUTONS POUSSOIRS
    // =========================================================================
    private TitledPane buildSectionBoutons() {
        Label titre = new Label("État des boutons poussoirs :");
        titre.getStyleClass().add("section-titre");

        voyantNord  = new Circle(12);
        voyantSud   = new Circle(12);
        voyantOuest = new Circle(12);
        voyantEst   = new Circle(12);

        voyantNord .getStyleClass().addAll("voyant", "voyant-off");
        voyantSud  .getStyleClass().addAll("voyant", "voyant-off");
        voyantOuest.getStyleClass().addAll("voyant", "voyant-off");
        voyantEst  .getStyleClass().addAll("voyant", "voyant-off");

        GridPane croix = new GridPane();
        croix.setHgap(10); croix.setVgap(6);
        croix.setAlignment(Pos.CENTER);
        croix.add(ledGroup(voyantNord,  "NORD"),  1, 0);
        croix.add(ledGroup(voyantOuest, "OUEST"), 0, 1);
        croix.add(ledGroup(voyantEst,   "EST"),   2, 1);
        croix.add(ledGroup(voyantSud,   "SUD"),   1, 2);

        VBox content = new VBox(8, titre, croix);
        content.setPadding(new Insets(8));
        content.setAlignment(Pos.CENTER);

        return sectionPane("État des boutons poussoirs", content, "section-boutons");
    }

    // =========================================================================
    //  SECTION 5 — BUZZER
    // =========================================================================
    private TitledPane buildSectionBuzzer() {
        Label titre = new Label("Gestion du buzzer :");
        titre.getStyleClass().add("section-titre");

        labelBuzzer = new Label("🔇");
        labelBuzzer.getStyleClass().add("buzzer-icon");

        btnToggleBuzzer = new Button("Désactiver");
        btnToggleBuzzer.setOnAction(e -> {
            buzzerActif = !buzzerActif;
            btnToggleBuzzer.setText(buzzerActif ? "Désactiver" : "Activer");
        });

        HBox ligne = new HBox(10, labelBuzzer, btnToggleBuzzer);
        ligne.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        VBox content = new VBox(8, titre, ligne);
        content.setPadding(new Insets(8));
        content.setAlignment(Pos.CENTER_LEFT);

        return sectionPane("Gestion du buzzer", content, "section-buzzer");
    }

    // =========================================================================
    //  SECTION 6 — GESTION DES CONFLITS
    // =========================================================================
    private TitledPane buildSectionConflits() {
        Label titre = new Label("Gestion des conflits :");
        titre.getStyleClass().add("section-titre");

        labelPremierVol    = new Label("—");
        labelConflitProche = new Label("—");
        labelPremierVol   .getStyleClass().add("lcd-value");
        labelConflitProche.getStyleClass().add("lcd-value");

        HBox ligne1 = new HBox(8, lcdLabel("premier vol :"),    labelPremierVol);
        HBox ligne2 = new HBox(8, lcdLabel("conflit proche :"), labelConflitProche);
        ligne1.setAlignment(Pos.CENTER_LEFT);
        ligne2.setAlignment(Pos.CENTER_LEFT);

        VBox ecranLcd = new VBox(6, ligne1, ligne2);
        ecranLcd.setPadding(new Insets(8));
        ecranLcd.getStyleClass().add("ecran-lcd");

        VBox content = new VBox(8, titre, ecranLcd);
        content.setPadding(new Insets(8));

        return sectionPane("Gestion des conflits", content, "section-conflits");
    }

    // =========================================================================
    //  API PUBLIQUE — mise à jour par le contrôleur
    // =========================================================================

    /** Active la vue plongeante (vue haute). */
    public void activerVueHaute() { checkVueHaute.setSelected(true); }

    /** Active la vue niveau piste (vue basse). */
    public void activerVueBasse() { checkVueBasse.setSelected(true); }

    /** Active la vue contre-plongée (décroche les deux cases). */
    public void activerContrePlongee() {
        checkVueHaute.setSelected(false);
        checkVueBasse.setSelected(false);
    }

    /** Retourne la distance seuil actuellement affichée. */
    public double getDistanceSeuil() { return sliderDistance.getValue(); }

    /** Modifie la distance seuil (ex. depuis la fenêtre Paramètres). */
    public void setDistanceSeuil(double metres) {
        double clamped = Math.max(50, Math.min(2000, metres));
        sliderDistance.setValue(clamped);
    }

    /** Allume une LED. */
    public void allumerLed(Circle led, String classeAllumee) {
        led.getStyleClass().remove("led-eteinte");
        if (!led.getStyleClass().contains(classeAllumee))
            led.getStyleClass().add(classeAllumee);
    }

    /** Éteint une LED (gris neutre). */
    public void eteindreLed(Circle led) {
        led.getStyleClass().removeIf(c -> c.startsWith("led-") && !c.equals("led"));
        setLedEteinte(led);
    }

    /** Active un voyant bouton poussoir. */
    public void activerVoyant(Circle voyant) {
        voyant.getStyleClass().remove("voyant-off");
        if (!voyant.getStyleClass().contains("voyant-on"))
            voyant.getStyleClass().add("voyant-on");
    }

    /** Désactive un voyant bouton poussoir. */
    public void desactiverVoyant(Circle voyant) {
        voyant.getStyleClass().remove("voyant-on");
        if (!voyant.getStyleClass().contains("voyant-off"))
            voyant.getStyleClass().add("voyant-off");
    }

    /** Retourne vrai si le buzzer est activé par l'utilisateur. */
    public boolean isBuzzerActif() { return buzzerActif; }

    /**
     * Met à jour l'icône buzzer selon l'état de conflit.
     * actif=true  → conflit en cours → 🔊
     * actif=false → pas de conflit   → 🔇
     */
    public void setBuzzerActif(boolean actif) {
        labelBuzzer.setText(actif ? "🔊" : "🔇");
    }

    /** Met à jour l'écran LCD de conflits. */
    public void setConflits(String premierVol, String conflitProche) {
        labelPremierVol   .setText(premierVol    == null || premierVol.isBlank()    ? "—" : premierVol);
        labelConflitProche.setText(conflitProche == null || conflitProche.isBlank() ? "—" : conflitProche);
    }

    /** Remet les conflits à zéro. */
    public void reinitialiserConflits() { setConflits("—", "—"); }

    // =========================================================================
    //  UTILITAIRES PRIVÉS
    // =========================================================================

    private Button camBtn(String text) {
        Button b = new Button(text);
        b.setPrefSize(36, 36);
        b.getStyleClass().add("btn-cam");
        return b;
    }

    private VBox ledGroup(Circle c, String libelle) {
        Label lbl = new Label(libelle);
        lbl.getStyleClass().add("led-label");
        VBox vb = new VBox(4, c, lbl);
        vb.setAlignment(Pos.CENTER);
        return vb;
    }

    private Label lcdLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("lcd-key");
        return l;
    }

    private TitledPane sectionPane(String titre, Node content, String styleClass) {
        TitledPane pane = new TitledPane(titre, content);
        pane.setCollapsible(false);
        pane.getStyleClass().add(styleClass);
        return pane;
    }

    private void setLedEteinte(Circle led) {
        led.getStyleClass().add("led-eteinte");
        led.setFill(Color.web("#c8c8d0"));
        led.setStroke(Color.web("#a8a8b8"));
    }
}
