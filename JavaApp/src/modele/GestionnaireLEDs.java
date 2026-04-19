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
 */
public class GestionnaireLEDs {

    private Context       pi4j      = null;
    private DigitalOutput ledVert   = null;  // BCM 21 - physique 40
    private DigitalOutput ledJaune  = null;  // BCM 20 - physique 38
    private DigitalOutput ledRouge  = null;  // BCM 16 - physique 36
    private boolean       disponible = false;

    public GestionnaireLEDs() {
        try {
            pi4j = Pi4J.newAutoContext();

            // ── Initialisation des broches ───────────────────────────────
            ledVert  = pi4j.digitalOutput().create(21); // physique 40
            ledJaune = pi4j.digitalOutput().create(20); // physique 38
            ledRouge = pi4j.digitalOutput().create(16); // physique 36

            // ── Éteindre toutes les LEDs au démarrage ───────────────────
            ledVert .low();
            ledJaune.low();
            ledRouge.low();

            disponible = true;
            System.out.println("[LED] pi4j initialisé — LEDs prêtes (BCM 21/20/16).");
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
     * @param conflit   vrai → LED rouge allumée
     * @param proximite vrai → LED jaune allumée
     *                  aucun des deux → LED verte allumée
     */
    public void mettreAJour(boolean conflit, boolean proximite) {
        if (!disponible) return;
        try {
            // LED Rouge (conflit)
            if (conflit) ledRouge.high();
            else         ledRouge.low();

            // LED Jaune (proximité, seulement si pas de conflit)
            if (!conflit && proximite) ledJaune.high();
            else                       ledJaune.low();

            // LED Verte (RAS)
            if (!conflit && !proximite) ledVert.high();
            else                        ledVert.low();

        } catch (Exception ignored) {}
    }

    /**
     * Éteint toutes les LEDs.
     * Appelé au stop (pas pause) et en fin de simulation.
     */
    public void eteindreTout() {
        if (!disponible) return;
        try {
            ledVert .low();
            ledJaune.low();
            ledRouge.low();
        } catch (Exception ignored) {}
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
}
