package modele;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Contrôle les LEDs physiques du Raspberry Pi via l'interface sysfs GPIO.
 * Aucune dépendance externe requise.
 *
 * Brochage physique → BCM :
 *   Broche 40 (BCM 21) → LED Verte  (RAS)
 *   Broche 38 (BCM 20) → LED Jaune  (proximité)
 *   Broche 36 (BCM 16) → LED Rouge  (conflit)
 *   Broche 34           → GND
 *
 * Si le GPIO n'est pas disponible (ex: exécution sur Mac),
 * la classe se désactive silencieusement.
 */
public class GestionnaireLEDs {

    private static final String GPIO_ROOT = "/sys/class/gpio";

    // BCM pin numbers
    private static final int PIN_VERT  = 21; // physique 40
    private static final int PIN_JAUNE = 20; // physique 38
    private static final int PIN_ROUGE = 16; // physique 36

    private boolean disponible = false;

    public GestionnaireLEDs() {
        try {
            initPin(PIN_VERT);
            initPin(PIN_JAUNE);
            initPin(PIN_ROUGE);
            disponible = true;
            System.out.println("[LED] GPIO initialisé — LEDs prêtes.");
        } catch (Exception e) {
            System.out.println("[LED] GPIO non disponible (hors RPi ?) : " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // API publique
    // ---------------------------------------------------------------

    /**
     * Met à jour l'état des trois LEDs selon la situation courante.
     *
     * @param conflit   vrai si au moins une paire est en alarme (LED rouge)
     * @param proximite vrai si au moins une paire est proche mais hors alarme (LED jaune)
     */
    public void mettreAJour(boolean conflit, boolean proximite) {
        if (!disponible) return;
        try {
            ecrire(PIN_ROUGE,  conflit               ? 1 : 0);
            ecrire(PIN_JAUNE, !conflit && proximite   ? 1 : 0);
            ecrire(PIN_VERT,  !conflit && !proximite  ? 1 : 0);
        } catch (Exception e) {
            // Erreur GPIO non bloquante
        }
    }

    /** Éteint toutes les LEDs (arrêt ou fin de simulation). */
    public void eteindreTout() {
        if (!disponible) return;
        try {
            ecrire(PIN_VERT,  0);
            ecrire(PIN_JAUNE, 0);
            ecrire(PIN_ROUGE, 0);
        } catch (Exception ignored) {}
    }

    // ---------------------------------------------------------------
    // Initialisation GPIO sysfs
    // ---------------------------------------------------------------

    private void initPin(int bcm) throws IOException {
        Path pinDir = Path.of(GPIO_ROOT + "/gpio" + bcm);
        if (!Files.exists(pinDir)) {
            Files.writeString(Path.of(GPIO_ROOT + "/export"), String.valueOf(bcm));
            // Laisser le temps au kernel de créer l'entrée sysfs
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        }
        Files.writeString(pinDir.resolve("direction"), "out");
        ecrire(bcm, 0); // LED éteinte par défaut
    }

    private void ecrire(int bcm, int valeur) throws IOException {
        Files.writeString(Path.of(GPIO_ROOT + "/gpio" + bcm + "/value"),
                String.valueOf(valeur));
    }
}
