package modele;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.DigitalInput;
import com.pi4j.io.gpio.digital.DigitalState;
import com.pi4j.io.gpio.digital.PullResistance;
import javafx.application.Platform;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gère 4 boutons poussoirs normalement ouverts via GPIO (pi4j).
 * Appuyé = LOW (pull-up interne activé).
 *
 * Brochage BCM :
 *   GPIO  1 → Droite  (EST)
 *   GPIO  7 → Bas     (SUD)
 *   GPIO  8 → Gauche  (OUEST)
 *   GPIO 25 → Haut    (NORD)
 *
 * À chaque appui maintenu, déplace la caméra en continu (toutes les 80 ms).
 * Met à jour les voyants UI via Platform.runLater.
 */
public class GestionnaireBoutons {

    /** Callback appelé sur le thread FX pour déplacer la caméra. */
    public interface CameraCallback {
        void deplacer(double dx, double dz);
    }

    /** Callback appelé sur le thread FX pour mettre à jour un voyant. */
    public interface VoyantCallback {
        void setEtat(Direction dir, boolean appuye);
    }

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

    /** Arrête le thread et libère le contexte. */
    public void fermer() {
        actif.set(false);
        if (thread != null) thread.interrupt();
        if (pi4j != null) try { pi4j.shutdown(); } catch (Exception ignored) {}
    }
}
