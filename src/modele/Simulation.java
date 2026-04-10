package modele;

import java.util.ArrayList;
import java.util.List;

/**
 * Moteur de simulation : maintient le temps écoulé,
 * fait avancer tous les aéronefs et détecte les conflits à chaque tick.
 * Conçu pour tourner dans un thread dédié.
 */
public class Simulation {

    public interface TickListener {
        /**
         * Appelé après chaque tick (depuis le thread simulation).
         * @param tempsEcoule  temps simulé en secondes
         * @param actifs       aéronefs actuellement actifs
         * @param conflits     conflits détectés à ce tick
         */
        void onTick(double tempsEcoule,
                    List<Aeronef> actifs,
                    List<GestionnaireConflits.Conflit> conflits);
    }

    // ---------------------------------------------------------------

    private final CircuitAD           circuit;
    private final List<Aeronef>       aeronefs;
    private final GestionnaireConflits gestConflits;
    private       TickListener        listener;

    private volatile boolean enCours  = false;
    private volatile boolean pause    = false;
    private double           tempsEcoule = 0.0;

    /** Facteur d'accélération de la simulation (20 = 20× temps réel). */
    private double simSpeed = 20.0;
    /** Durée d'un tick en ms (≈30 fps). */
    private static final int TICK_MS = 33;

    public Simulation(CircuitAD circuit,
                      List<Aeronef> aeronefs,
                      GestionnaireConflits gestConflits) {
        this.circuit      = circuit;
        this.aeronefs     = aeronefs;
        this.gestConflits = gestConflits;
    }

    public void setTickListener(TickListener l) { this.listener = l; }
    public void setSimSpeed(double s)           { this.simSpeed = s; }
    public double getTempsEcoule()              { return tempsEcoule; }
    public boolean isEnCours()                  { return enCours; }
    public boolean isPause()                    { return pause;   }
    public List<Aeronef> getAeronefs()          { return aeronefs; }

    /** Lance la simulation dans le thread courant (à appeler depuis un Thread dédié). */
    public void demarrer() {
        reinitialiser();
        enCours = true;
        pause   = false;

        List<CircuitAD.Segment> segmentsNormal = circuit.getSegmentsNormal();
        List<CircuitAD.Segment> segmentsFinal  = circuit.getSegmentsFinal();
        double dtSimParTick = (TICK_MS / 1000.0) * simSpeed;  // secondes simulées par tick

        while (enCours) {
            if (!pause) {
                tempsEcoule += dtSimParTick;

                for (Aeronef a : aeronefs) {
                    a.avancer(segmentsNormal, segmentsFinal, dtSimParTick, tempsEcoule);
                }

                List<GestionnaireConflits.Conflit> conflits = gestConflits.detecter(aeronefs);

                List<Aeronef> actifs = new ArrayList<>();
                for (Aeronef a : aeronefs) {
                    if (a.isActif()) actifs.add(a);
                }

                if (listener != null) {
                    listener.onTick(tempsEcoule, actifs, conflits);
                }

                // Arrêt automatique quand tous ont terminé
                boolean tousTermines = true;
                for (Aeronef a : aeronefs) {
                    if (!a.isTermine()) { tousTermines = false; break; }
                }
                if (tousTermines) {
                    enCours = false;
                    break;
                }
            }

            try {
                Thread.sleep(TICK_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                enCours = false;
            }
        }
    }

    public void mettreEnPause()    { pause   = true;  }
    public void reprendreDepauze() { pause   = false; }

    public void arreter() {
        enCours = false;
        pause   = false;
    }

    private void reinitialiser() {
        tempsEcoule = 0.0;
        for (Aeronef a : aeronefs) a.reinitialiser();
    }
}