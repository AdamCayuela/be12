package modele;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.DigitalOutput;
import com.pi4j.io.gpio.digital.DigitalState;

/**
 * Contrôle les LEDs physiques du Raspberry Pi via pi4j (provider gpiod).
 * Compatible RPi 5 / kernel 6.x.
 *
 * Brochage physique → BCM :
 *   Broche 40 (BCM 21) → LED Verte  (RAS)
 *   Broche 38 (BCM 20) → LED Jaune  (proximité)
 *   Broche 36 (BCM 16) → LED Rouge  (conflit)
 *   Broche 34           → GND
 *
 * Si pi4j n'est pas disponible (ex: exécution sur Mac),
 * la classe se désactive silencieusement.
 */
public class GestionnaireLEDs {

    private static final int PIN_VERT  = 21; // physique 40
    private static final int PIN_JAUNE = 20; // physique 38
    private static final int PIN_ROUGE = 16; // physique 36

    private Context       pi4j      = null;
    private DigitalOutput ledVert   = null;
    private DigitalOutput ledJaune  = null;
    private DigitalOutput ledRouge  = null;
    private boolean       disponible = false;

    public GestionnaireLEDs() {
        try {
            pi4j = Pi4J.newAutoContext();

            ledVert  = creerSortie(PIN_VERT,  "led-verte");
            ledJaune = creerSortie(PIN_JAUNE, "led-jaune");
            ledRouge = creerSortie(PIN_ROUGE, "led-rouge");

            // Éteindre toutes les LEDs au démarrage
            eteindreTout();

            disponible = true;
            System.out.println("[LED] pi4j initialisé — LEDs prêtes.");
        } catch (Exception e) {
            System.out.println("[LED] pi4j non disponible (hors RPi ?) : " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // API publique
    // ---------------------------------------------------------------

    /**
     * Met à jour l'état des trois LEDs selon la situation courante.
     *
     * @param conflit   vrai si au moins une paire est en alarme    → LED rouge
     * @param proximite vrai si au moins une paire est en approche  → LED jaune
     */
    public void mettreAJour(boolean conflit, boolean proximite) {
        if (!disponible) return;
        try {
            setState(ledRouge,  conflit);
            setState(ledJaune, !conflit && proximite);
            setState(ledVert,  !conflit && !proximite);
        } catch (Exception ignored) {}
    }

    /**
     * Éteint toutes les LEDs.
     * Appelé au stop (pas pause) et en fin de simulation.
     */
    public void eteindreTout() {
        if (ledVert  != null) try { ledVert .low(); } catch (Exception ignored) {}
        if (ledJaune != null) try { ledJaune.low(); } catch (Exception ignored) {}
        if (ledRouge != null) try { ledRouge.low(); } catch (Exception ignored) {}
    }

    /**
     * Libère le contexte pi4j à la fermeture de l'application.
     */
    public void fermer() {
        eteindreTout();
        if (pi4j != null) {
            try { pi4j.shutdown(); } catch (Exception ignored) {}
        }
    }

    // ---------------------------------------------------------------
    // Utilitaires
    // ---------------------------------------------------------------

    private DigitalOutput creerSortie(int bcm, String id) {
        DigitalOutput out = pi4j.digitalOutput().create(bcm);
        out.config().initialState(DigitalState.LOW);
        out.config().shutdownState(DigitalState.LOW);
        return out;
    }

    private void setState(DigitalOutput out, boolean allumer) {
        if (out == null) return;
        if (allumer) out.high();
        else         out.low();
    }
}
