package modele;

/**
 * Contrôle les LEDs physiques du Raspberry Pi via la commande pinctrl.
 * Compatible RPi 5 / kernel 6.x (sysfs GPIO déprécié sur ces versions).
 *
 * Brochage physique → BCM :
 *   Broche 40 (BCM 21) → LED Verte  (RAS)
 *   Broche 38 (BCM 20) → LED Jaune  (proximité)
 *   Broche 36 (BCM 16) → LED Rouge  (conflit)
 *   Broche 34           → GND
 *
 * Si pinctrl n'est pas disponible (ex: exécution sur Mac),
 * la classe se désactive silencieusement.
 */
public class GestionnaireLEDs {

    private static final int PIN_VERT  = 21; // physique 40
    private static final int PIN_JAUNE = 20; // physique 38
    private static final int PIN_ROUGE = 16; // physique 36

    private boolean disponible = false;

    public GestionnaireLEDs() {
        try {
            // Initialiser les 3 broches en sortie éteintes
            pinctrl(PIN_VERT,  false);
            pinctrl(PIN_JAUNE, false);
            pinctrl(PIN_ROUGE, false);
            disponible = true;
            System.out.println("[LED] pinctrl initialisé — LEDs prêtes.");
        } catch (Exception e) {
            System.out.println("[LED] pinctrl non disponible (hors RPi ?) : " + e.getMessage());
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
            pinctrl(PIN_ROUGE,  conflit);
            pinctrl(PIN_JAUNE, !conflit && proximite);
            pinctrl(PIN_VERT,  !conflit && !proximite);
        } catch (Exception ignored) {}
    }

    /** Éteint toutes les LEDs (arrêt ou fin de simulation). */
    public void eteindreTout() {
        if (!disponible) return;
        try {
            pinctrl(PIN_VERT,  false);
            pinctrl(PIN_JAUNE, false);
            pinctrl(PIN_ROUGE, false);
        } catch (Exception ignored) {}
    }

    // ---------------------------------------------------------------
    // Commande pinctrl
    // ---------------------------------------------------------------

    /**
     * Appelle : pinctrl set <bcm> op dh   (output high = LED allumée)
     *       ou : pinctrl set <bcm> op dl   (output low  = LED éteinte)
     */
    private void pinctrl(int bcm, boolean allumer) throws Exception {
        String etat = allumer ? "dh" : "dl";
        Process p = Runtime.getRuntime().exec(
                new String[]{"pinctrl", "set", String.valueOf(bcm), "op", etat});
        p.waitFor();
    }
}
