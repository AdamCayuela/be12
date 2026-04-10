package modele;

import java.util.ArrayList;
import java.util.List;

/**
 * Cœur de la simulation : fait tourner le temps, déplace les avions et détecte les conflits.
 * Tourne dans un thread dédié à ~30 fps (tick toutes les 33 ms).
 */
public class Simulation {

    public interface TickListener {
        /**
         * Appelé après chaque tick depuis le thread simulation.
         * À implémenter pour mettre à jour l'interface graphique.
         *
         * @param tempsEcoule temps simulé en secondes depuis le début
         * @param actifs      avions actuellement en vol
         * @param conflits    paires d'avions trop proches l'une de l'autre
         */
        void onTick(double tempsEcoule,
                    List<Aeronef> actifs,
                    List<GestionnaireConflits.Conflit> conflits);
    }

    // ---------------------------------------------------------------

    private final CircuitAD            circuit;
    private final List<Aeronef>        aeronefs;
    private final GestionnaireConflits gestConflits;
    private       TickListener         listener;

    private volatile boolean enCours    = false;
    private volatile boolean pause      = false;
    private volatile double  seekTarget = -1.0;  // temps cible d'un saut demandé par l'utilisateur
    private double           tempsEcoule = 0.0;

    /** Vitesse de simulation : 20 = 20× le temps réel. */
    private double simSpeed = 20.0;
    /** Durée d'un tick en millisecondes (~30 fps). */
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

    /**
     * Démarre la boucle de simulation. À appeler depuis un thread dédié.
     * La boucle tourne jusqu'à ce que tous les avions aient atterri,
     * ou jusqu'à un appel à arreter().
     */
    public void demarrer() {
        reinitialiser();
        enCours = true;
        pause   = false;

        List<CircuitAD.Segment> segmentsNormal = circuit.getSegmentsNormal();
        List<CircuitAD.Segment> segmentsLoop   = circuit.getSegmentsLoop();
        List<CircuitAD.Segment> segmentsFinal  = circuit.getSegmentsFinal();

        while (enCours) {

            // Si l'utilisateur a déplacé le slider, on saute au bon moment.
            // On saute aussi le tick normal juste après pour éviter que le check
            // "tous terminés" ne stoppe la simulation immédiatement.
            if (seekTarget >= 0) {
                double target = seekTarget;
                seekTarget = -1.0;
                appliquerSeek(target, segmentsNormal, segmentsLoop, segmentsFinal);

                List<GestionnaireConflits.Conflit> conflits = gestConflits.detecter(aeronefs);
                List<Aeronef> actifs = new ArrayList<>();
                for (Aeronef a : aeronefs) { if (a.isActif()) actifs.add(a); }
                if (listener != null) listener.onTick(tempsEcoule, actifs, conflits);

                try { Thread.sleep(TICK_MS); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); enCours = false;
                }
                continue;
            }

            if (!pause) {
                // Chaque tick représente dtSimParTick secondes simulées
                double dtSimParTick = (TICK_MS / 1000.0) * simSpeed;
                tempsEcoule += dtSimParTick;

                for (Aeronef a : aeronefs) {
                    a.avancer(segmentsNormal, segmentsLoop, segmentsFinal, dtSimParTick, tempsEcoule);
                }

                List<GestionnaireConflits.Conflit> conflits = gestConflits.detecter(aeronefs);

                List<Aeronef> actifs = new ArrayList<>();
                for (Aeronef a : aeronefs) {
                    if (a.isActif()) actifs.add(a);
                }

                if (listener != null) {
                    listener.onTick(tempsEcoule, actifs, conflits);
                }

                // Quand tous les avions ont atterri, on s'arrête automatiquement
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

    public void mettreEnPause()    { pause = true;  }
    public void reprendreDepauze() { pause = false; }

    /**
     * Demande un saut au temps indiqué (en secondes simulées).
     * Le saut est appliqué au prochain tick, y compris si la simulation est en pause.
     */
    public void requestSeek(double targetSeconds) {
        seekTarget = Math.max(0, targetSeconds);
    }

    public void arreter() {
        enCours = false;
        pause   = false;
    }

    private void reinitialiser() {
        tempsEcoule = 0.0;
        for (Aeronef a : aeronefs) a.reinitialiser();
    }

    /**
     * Recalcule la position de chaque avion comme s'il avait volé depuis
     * son heure de départ jusqu'à {@code target} secondes.
     */
    private void appliquerSeek(double target,
                                List<CircuitAD.Segment> segNormal,
                                List<CircuitAD.Segment> segLoop,
                                List<CircuitAD.Segment> segFinal) {
        for (Aeronef a : aeronefs) a.reinitialiser();
        for (Aeronef a : aeronefs) {
            if (target >= a.getTempsDepart()) {
                double dt = target - a.getTempsDepart();
                a.avancer(segNormal, segLoop, segFinal, dt, target);
            }
        }
        tempsEcoule = target;
    }
}
