package modele;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.DigitalOutput;
import com.pi4j.io.gpio.digital.DigitalState;

/**
 * Contrôle les LEDs et le buzzer physiques du Raspberry Pi via pi4j (provider gpiod).
 *
 * <p>Compatible Raspberry Pi 5 / kernel Linux 6.x (utilise le driver {@code gpiod},
 * le driver {@code sysfs} étant déprécié sur ce noyau).</p>
 *
 * <h2>Brochage physique → BCM</h2>
 * <table border="1">
 *   <tr><th>Broche physique</th><th>GPIO BCM</th><th>Rôle</th></tr>
 *   <tr><td>40</td><td>BCM 21</td><td>LED Verte  — RAS (aucun conflit)</td></tr>
 *   <tr><td>38</td><td>BCM 20</td><td>LED Jaune  — Proximité détectée</td></tr>
 *   <tr><td>36</td><td>BCM 16</td><td>LED Rouge  — Conflit (alarme)</td></tr>
 *   <tr><td>37</td><td>BCM 26</td><td>Buzzer     — Conflit actif</td></tr>
 *   <tr><td>34</td><td>GND</td><td>Masse commune</td></tr>
 * </table>
 *
 * <p>Si pi4j n'est pas disponible (exécution hors RPi), la classe se dégrade
 * silencieusement : {@code disponible = false}, toutes les méthodes sont des no-ops.</p>
 *
 * @see modele.GestionnaireLCD
 * @see controleur.ControleurPrincipal
 */
public class GestionnaireLEDs {

    private Context       pi4j      = null;
    private DigitalOutput ledVert   = null;  // BCM 21 - physique 40
    private DigitalOutput ledJaune  = null;  // BCM 20 - physique 38
    private DigitalOutput ledRouge  = null;  // BCM 16 - physique 36
    private DigitalOutput buzzer    = null;  // BCM 26 - physique 37
    private boolean       disponible = false;

    public GestionnaireLEDs() {
        try {
            pi4j = Pi4J.newAutoContext();

            // ── Initialisation des broches ───────────────────────────────
            ledVert  = pi4j.digitalOutput().create(21); // physique 40
            ledJaune = pi4j.digitalOutput().create(20); // physique 38
            ledRouge = pi4j.digitalOutput().create(16); // physique 36
            buzzer   = pi4j.digitalOutput().create(26); // physique 37

            // ── Tout éteindre au démarrage ───────────────────────────────
            ledVert .low();
            ledJaune.low();
            ledRouge.low();
            buzzer  .low();

            disponible = true;
            System.out.println("[LED] pi4j initialisé — LEDs + buzzer prêts (BCM 21/20/16/26).");
        } catch (Exception e) {
            System.out.println("[LED] pi4j non disponible (hors RPi ?) : " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // API publique
    // ---------------------------------------------------------------

    /**
     * Met à jour les LEDs et le buzzer en fonction de la situation courante.
     *
     * <p>Logique d'allumage :</p>
     * <ul>
     *   <li>Conflit → LED rouge allumée</li>
     *   <li>Conflit ET buzzer non coupé → buzzer actif</li>
     *   <li>Pas de conflit, proximité → LED jaune allumée</li>
     *   <li>RAS (ni conflit ni proximité) → LED verte allumée</li>
     * </ul>
     *
     * @param conflit     {@code true} si au moins une paire d'aéronefs est en conflit (distance ≤ seuil)
     * @param proximite   {@code true} si au moins une paire est en approche (seuil &lt; dist ≤ 2×seuil)
     * @param buzzerCoupe {@code true} si l'utilisateur a désactivé le buzzer dans l'interface
     */
    public void mettreAJour(boolean conflit, boolean proximite, boolean buzzerCoupe) {
        if (!disponible) return;
        try {
            // LED Rouge (conflit)
            if (conflit) ledRouge.high();
            else         ledRouge.low();

            // Buzzer (conflit ET non coupé)
            if (conflit && !buzzerCoupe) buzzer.high();
            else                         buzzer.low();

            // LED Jaune (proximité, seulement si pas de conflit)
            if (!conflit && proximite) ledJaune.high();
            else                       ledJaune.low();

            // LED Verte (RAS)
            if (!conflit && !proximite) ledVert.high();
            else                        ledVert.low();

        } catch (Exception ignored) {}
    }

    /**
     * Éteint toutes les LEDs et le buzzer.
     * Appelé à l'arrêt de la simulation (pas à la pause) et à la fermeture de l'application.
     */
    public void eteindreTout() {
        if (!disponible) return;
        try {
            ledVert .low();
            ledJaune.low();
            ledRouge.low();
            buzzer  .low();
        } catch (Exception ignored) {}
    }

    /**
     * Éteint les LEDs/buzzer et libère le contexte pi4j.
     * À appeler une seule fois, à la fermeture de l'application.
     */
    public void fermer() {
        eteindreTout();
        if (pi4j != null) {
            try { pi4j.shutdown(); } catch (Exception ignored) {}
        }
    }
}
