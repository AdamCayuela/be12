package modele;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.DigitalInput;
import com.pi4j.io.gpio.digital.DigitalState;
import com.pi4j.io.gpio.digital.PullResistance;
import javafx.application.Platform;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gère 4 boutons poussoirs GPIO pour le déplacement de la caméra 3D.
 *
 * <h2>Caractéristiques électriques</h2>
 * <p>Boutons normalement ouverts + pull-up interne activé (pi4j) :
 * repos = HIGH, appui = LOW.</p>
 *
 * <h2>Brochage BCM</h2>
 * <table border="1">
 *   <tr><th>GPIO BCM</th><th>Direction</th><th>Point cardinal</th></tr>
 *   <tr><td>1</td><td>Droite</td><td>EST</td></tr>
 *   <tr><td>7</td><td>Bas</td><td>SUD</td></tr>
 *   <tr><td>8</td><td>Gauche</td><td>OUEST</td></tr>
 *   <tr><td>25</td><td>Haut</td><td>NORD</td></tr>
 * </table>
 *
 * <h2>Comportement</h2>
 * <p>Un thread de polling tourne toutes les 80 ms. Tant qu'un bouton est
 * maintenu appuyé, la caméra se déplace de {@code STEP = 15 px} dans la
 * direction correspondante (via {@link CameraCallback}).
 * Les changements d'état (appui/relâchement) déclenchent une mise à jour
 * des voyants de l'interface (via {@link VoyantCallback} et
 * {@code Platform.runLater}).</p>
 *
 * <p>Si pi4j n'est pas disponible (exécution hors RPi), le constructeur
 * échoue silencieusement et aucun thread n'est démarré.</p>
 *
 * @see controleur.ControleurPrincipal
 * @see vue.VuePanneauControle
 */
public class GestionnaireBoutons {

    /**
     * Callback appelé sur le thread JavaFX pour déplacer la caméra 3D.
     * Implémenté par {@link controleur.ControleurPrincipal} via lambda.
     */
    public interface CameraCallback {
        /**
         * Déplace la caméra du delta spécifié.
         * @param dx déplacement horizontal (pixels FX)
         * @param dz déplacement vertical / profondeur (pixels FX)
         */
        void deplacer(double dx, double dz);
    }

    /**
     * Callback appelé sur le thread JavaFX pour mettre à jour un voyant de l'interface.
     * Implémenté par {@link controleur.ControleurPrincipal} via lambda.
     */
    public interface VoyantCallback {
        /**
         * Notifie l'interface du changement d'état d'un bouton.
         * @param dir     direction du bouton concerné
         * @param appuye  {@code true} si le bouton vient d'être appuyé, {@code false} s'il vient d'être relâché
         */
        void setEtat(Direction dir, boolean appuye);
    }

    /** Point cardinal correspondant à chacun des 4 boutons physiques. */
    public enum Direction { HAUT, BAS, GAUCHE, DROITE }

    private static final double STEP = 15.0; // pixels de déplacement caméra

    private Context      pi4j       = null;
    private DigitalInput btnDroite  = null; // BCM 1
    private DigitalInput btnBas     = null; // BCM 7
    private DigitalInput btnGauche  = null; // BCM 8
    private DigitalInput btnHaut    = null; // BCM 25

    private final AtomicBoolean actif = new AtomicBoolean(false);
    private Thread thread = null;
    private boolean disponible = false;

    private CameraCallback cameraCallback;
    private VoyantCallback voyantCallback;

    /**
     * Initialise les entrées GPIO et démarre le thread de polling.
     * Si pi4j n'est pas disponible, le constructeur se termine sans erreur
     * et le thread n'est pas créé.
     *
     * @param cameraCallback callback JavaFX pour déplacer la caméra
     * @param voyantCallback callback JavaFX pour allumer/éteindre les voyants du panneau
     */
    public GestionnaireBoutons(CameraCallback cameraCallback, VoyantCallback voyantCallback) {
        this.cameraCallback = cameraCallback;
        this.voyantCallback = voyantCallback;
        try {
            pi4j = Pi4J.newAutoContext();

            btnDroite = creerEntree(1,  "btn-droite");
            btnBas    = creerEntree(7,  "btn-bas");
            btnGauche = creerEntree(8,  "btn-gauche");
            btnHaut   = creerEntree(25, "btn-haut");

            disponible = true;
            System.out.println("[BTN] Boutons GPIO initialisés (BCM 1/7/8/25).");

            actif.set(true);
            thread = new Thread(this::boucle, "thread-boutons");
            thread.setDaemon(true);
            thread.start();

        } catch (Exception e) {
            System.out.println("[BTN] Boutons GPIO non disponibles (hors RPi ?) : " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // Boucle de polling
    // ---------------------------------------------------------------

    private void boucle() {
        boolean[] precedent = new boolean[4]; // état précédent pour détecter les changements

        while (actif.get()) {
            try {
                boolean haut   = estAppuye(btnHaut);
                boolean bas    = estAppuye(btnBas);
                boolean gauche = estAppuye(btnGauche);
                boolean droite = estAppuye(btnDroite);

                // Déplacement caméra si bouton maintenu
                if (haut)   envoyer(0,     -STEP);
                if (bas)    envoyer(0,      STEP);
                if (gauche) envoyer( STEP,  0);
                if (droite) envoyer(-STEP,  0);

                // Mise à jour voyants UI si changement d'état
                majVoyant(Direction.HAUT,   haut,   precedent, 0);
                majVoyant(Direction.BAS,    bas,    precedent, 1);
                majVoyant(Direction.GAUCHE, gauche, precedent, 2);
                majVoyant(Direction.DROITE, droite, precedent, 3);

                Thread.sleep(80); // 80 ms → ~12 FPS de déplacement

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ignored) {}
        }
    }

    // ---------------------------------------------------------------
    // Utilitaires
    // ---------------------------------------------------------------

    /** Retourne vrai si le bouton est appuyé (LOW = normalement ouvert + pull-up). */
    private boolean estAppuye(DigitalInput btn) {
        return btn != null && btn.state() == DigitalState.LOW;
    }

    /** Envoie le déplacement caméra sur le thread JavaFX. */
    private void envoyer(double dx, double dz) {
        if (cameraCallback != null)
            Platform.runLater(() -> cameraCallback.deplacer(dx, dz));
    }

    /** Met à jour le voyant UI si l'état a changé. */
    private void majVoyant(Direction dir, boolean appuye, boolean[] precedent, int idx) {
        if (appuye != precedent[idx]) {
            precedent[idx] = appuye;
            if (voyantCallback != null)
                Platform.runLater(() -> voyantCallback.setEtat(dir, appuye));
        }
    }

    /** Crée un DigitalInput avec pull-up et debounce. */
    private DigitalInput creerEntree(int bcm, String id) {
        return pi4j.create(
            DigitalInput.newConfigBuilder(pi4j)
                .id(id)
                .name(id)
                .address(bcm)
                .pull(PullResistance.PULL_UP)
                .debounce(3000L)
                .build()
        );
    }

    /**
     * Interrompt le thread de polling et libère le contexte pi4j.
     * À appeler une seule fois à la fermeture de l'application.
     */
    public void fermer() {
        actif.set(false);
        if (thread != null) thread.interrupt();
        if (pi4j != null) try { pi4j.shutdown(); } catch (Exception ignored) {}
    }
}
