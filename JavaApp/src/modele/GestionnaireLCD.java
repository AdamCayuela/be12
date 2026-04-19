package modele;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.i2c.I2C;
import com.pi4j.io.i2c.I2CConfig;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Gère un écran LCD 2×16 caractères (HD44780) via adaptateur I2C PCF8574.
 *
 * <h2>Brochage Raspberry Pi</h2>
 * <table border="1">
 *   <tr><th>Broche physique</th><th>Signal</th></tr>
 *   <tr><td>3 (BCM 2)</td><td>SDA (données I2C)</td></tr>
 *   <tr><td>5 (BCM 3)</td><td>SCL (horloge I2C)</td></tr>
 * </table>
 * <p>Adresse I2C par défaut : {@code 0x27} (sinon {@code 0x3F} selon le module).</p>
 *
 * <h2>Protocole</h2>
 * <p>L'écran HD44780 est piloté en mode 4 bits via l'expandeur PCF8574 :
 * chaque octet est envoyé en deux nibbles, chacun avec un pulse {@code Enable}.</p>
 *
 * <h2>Thread dédié</h2>
 * <p>Les écritures LCD (qui peuvent durer plusieurs ms) se font dans un thread
 * {@code daemon} pour ne pas bloquer le fil JavaFX. Le partage est assuré par
 * des {@link java.util.concurrent.atomic.AtomicReference} et un drapeau
 * {@link java.util.concurrent.atomic.AtomicBoolean}.</p>
 *
 * <h2>Contenu affiché</h2>
 * <pre>
 * Conflit   → Ligne 1 : "F-ABCD - F-WXYZ "
 *             Ligne 2 : "Dist: 1234 m    "
 * Proximité → Ligne 1 : "F-ABCD ~ F-WXYZ "
 *             Ligne 2 : "Proxi:1234 m    "
 * RAS       → Ligne 1 : "      RAS       "
 *             Ligne 2 : "                "
 * </pre>
 *
 * <p>Si pi4j n'est pas disponible (exécution hors RPi), la classe se dégrade
 * silencieusement : toutes les méthodes publiques sont des no-ops.</p>
 *
 * @see GestionnaireLEDs
 * @see controleur.ControleurPrincipal
 */
public class GestionnaireLCD {

    // ── Adresse et bus I2C ───────────────────────────────────────────
    private static final int LCD_ADDR = 0x27;
    private static final int I2C_BUS  = 1;

    // ── Flags PCF8574 ────────────────────────────────────────────────
    private static final byte BACKLIGHT = 0x08;
    private static final byte ENABLE    = 0x04;
    private static final byte RS_DATA   = 0x01; // RS=1 → données
    // RS=0 → commande (pas de flag)

    // ── Commandes HD44780 ────────────────────────────────────────────
    private static final byte CMD_CLEAR   = 0x01;
    private static final byte CMD_HOME    = 0x02;
    private static final byte CMD_LINE1   = 0x00; // DDRAM 0x00
    private static final byte CMD_LINE2   = 0x40; // DDRAM 0x40

    // ── État partagé thread-safe ─────────────────────────────────────
    private final AtomicReference<String> ligne1Ref = new AtomicReference<>("");
    private final AtomicReference<String> ligne2Ref = new AtomicReference<>("");
    private final AtomicBoolean           misAJour  = new AtomicBoolean(false);
    private final AtomicBoolean           actif     = new AtomicBoolean(false);

    private I2C     i2c       = null;
    private Context pi4j      = null;
    private boolean disponible = false;
    private Thread  thread    = null;

    // ═══════════════════════════════════════════════════════════════════
    // Constructeur — init I2C + démarrage thread
    // ═══════════════════════════════════════════════════════════════════

    public GestionnaireLCD() {
        try {
            pi4j = Pi4J.newAutoContext();

            I2CConfig config = I2C.newConfigBuilder(pi4j)
                    .id("lcd-hd44780")
                    .bus(I2C_BUS)
                    .device(LCD_ADDR)
                    .build();
            i2c = pi4j.create(config);

            initLCD();
            effacer();

            disponible = true;
            System.out.println("[LCD] Écran I2C initialisé (0x" + Integer.toHexString(LCD_ADDR) + ").");

            // Lancer le thread d'affichage
            actif.set(true);
            thread = new Thread(this::boucleAffichage, "thread-lcd");
            thread.setDaemon(true);
            thread.start();

        } catch (Exception e) {
            System.out.println("[LCD] Écran non disponible (hors RPi ou adresse incorrecte) : " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // API publique
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Affiche une situation de conflit (alarme rouge).
     * <ul>
     *   <li>Ligne 1 : {@code "IND1 - IND2     "} (indicatifs des deux aéronefs)</li>
     *   <li>Ligne 2 : {@code "Dist: 1234 m    "} (distance de séparation)</li>
     * </ul>
     *
     * @param ind1      indicatif du premier aéronef
     * @param ind2      indicatif du second aéronef
     * @param distanceM distance de séparation en mètres
     */
    public void afficherConflit(String ind1, String ind2, double distanceM) {
        if (!disponible) return;
        ligne1Ref.set(formater(ind1 + " - " + ind2, 16));
        ligne2Ref.set(formater(String.format("Dist: %.0f m", distanceM), 16));
        misAJour.set(true);
    }

    /**
     * Affiche une situation de proximité (alerte orange, pas encore alarme rouge).
     * <ul>
     *   <li>Ligne 1 : {@code "IND1 ~ IND2     "} (tilde indique l'approche)</li>
     *   <li>Ligne 2 : {@code "Proxi:1234 m    "} (distance de séparation)</li>
     * </ul>
     *
     * @param ind1      indicatif du premier aéronef
     * @param ind2      indicatif du second aéronef
     * @param distanceM distance de séparation en mètres
     */
    public void afficherProximite(String ind1, String ind2, double distanceM) {
        if (!disponible) return;
        ligne1Ref.set(formater(ind1 + " ~ " + ind2, 16));
        ligne2Ref.set(formater(String.format("Proxi:%.0f m", distanceM), 16));
        misAJour.set(true);
    }

    /**
     * Affiche l'état nominal RAS (aucun conflit ni proximité).
     * <ul>
     *   <li>Ligne 1 : {@code "      RAS       "}</li>
     *   <li>Ligne 2 : vide</li>
     * </ul>
     */
    public void afficherRAS() {
        if (!disponible) return;
        ligne1Ref.set(formater("      RAS       ", 16));
        ligne2Ref.set(formater("                ", 16));
        misAJour.set(true);
    }

    /**
     * Arrête le thread d'affichage, efface l'écran et coupe le rétroéclairage.
     * À appeler une seule fois à la fermeture de l'application.
     */
    public void fermer() {
        actif.set(false);
        if (thread != null) thread.interrupt();
        try {
            effacer();
            retroeclairage(false);
        } catch (Exception ignored) {}
        if (pi4j != null) try { pi4j.shutdown(); } catch (Exception ignored) {}
    }

    // ═══════════════════════════════════════════════════════════════════
    // Boucle thread LCD
    // ═══════════════════════════════════════════════════════════════════

    private void boucleAffichage() {
        while (actif.get()) {
            try {
                if (misAJour.compareAndSet(true, false)) {
                    ecrireLigne(1, ligne1Ref.get());
                    ecrireLigne(2, ligne2Ref.get());
                }
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ignored) {}
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Driver HD44780 via PCF8574
    // ═══════════════════════════════════════════════════════════════════

    /** Initialise l'écran en mode 4 bits. */
    private void initLCD() throws Exception {
        Thread.sleep(50);
        // Séquence d'init 4 bits (standard HD44780)
        envoyerNibble((byte) 0x30, (byte) 0);
        Thread.sleep(5);
        envoyerNibble((byte) 0x30, (byte) 0);
        Thread.sleep(1);
        envoyerNibble((byte) 0x30, (byte) 0);
        Thread.sleep(1);
        envoyerNibble((byte) 0x20, (byte) 0); // passe en 4 bits

        envoyerCommande((byte) 0x28); // 4 bits, 2 lignes, 5×8
        envoyerCommande((byte) 0x0C); // display ON, curseur OFF
        envoyerCommande((byte) 0x06); // incrément gauche→droite
        envoyerCommande(CMD_CLEAR);
        Thread.sleep(2);
    }

    /** Écrit une ligne complète (16 caractères) sur la ligne 1 ou 2. */
    private void ecrireLigne(int ligne, String texte) throws Exception {
        byte adresse = (ligne == 2) ? (byte)(0x80 | CMD_LINE2) : (byte)(0x80 | CMD_LINE1);
        envoyerCommande(adresse);
        for (char c : texte.toCharArray()) {
            envoyerDonnee((byte) c);
        }
    }

    /** Efface l'écran. */
    private void effacer() throws Exception {
        envoyerCommande(CMD_CLEAR);
        Thread.sleep(2);
    }

    /** Active/désactive le rétroéclairage. */
    private void retroeclairage(boolean on) throws Exception {
        i2c.write(on ? BACKLIGHT : 0);
    }

    /** Envoie une commande (RS=0). */
    private void envoyerCommande(byte cmd) throws Exception {
        envoyerOctet(cmd, (byte) 0);
    }

    /** Envoie un caractère (RS=1). */
    private void envoyerDonnee(byte data) throws Exception {
        envoyerOctet(data, RS_DATA);
    }

    /** Envoie un octet en deux nibbles 4 bits avec RS. */
    private void envoyerOctet(byte data, byte rs) throws Exception {
        envoyerNibble((byte) (data & 0xF0), rs);
        envoyerNibble((byte) ((data << 4) & 0xF0), rs);
    }

    /** Envoie un nibble (4 bits hauts) avec pulse Enable. */
    private void envoyerNibble(byte nibble, byte rs) throws Exception {
        byte data = (byte) (nibble | rs | BACKLIGHT);
        i2c.write((byte) (data | ENABLE));   // Enable HIGH
        Thread.sleep(0, 500_000);             // 0.5 ms
        i2c.write((byte) (data & ~ENABLE));  // Enable LOW
        Thread.sleep(0, 100_000);             // 0.1 ms
    }

    // ═══════════════════════════════════════════════════════════════════
    // Utilitaire
    // ═══════════════════════════════════════════════════════════════════

    /** Formate une chaîne à exactement n caractères (tronque ou padde). */
    private String formater(String s, int n) {
        if (s.length() > n) return s.substring(0, n);
        return String.format("%-" + n + "s", s);
    }
}
