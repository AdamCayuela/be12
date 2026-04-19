package modele;

import java.util.ArrayList;
import java.util.List;

/**
 * Détecte et classe les situations de proximité entre aéronefs actifs.
 *
 * <p>Deux niveaux d'alerte sont distingués :</p>
 * <ul>
 *   <li><b>Conflit</b> (alarme rouge) : distance ≤ seuil → LED rouge + buzzer</li>
 *   <li><b>Proximité</b> (alerte orange) : seuil &lt; distance ≤ 2×seuil → LED orange</li>
 * </ul>
 *
 * <p>La distance seuil est configurable par l'utilisateur depuis le panneau
 * de contrôle ou la fenêtre Paramètres (valeur par défaut : 400 m).</p>
 *
 * @see Simulation
 * @see controleur.ControleurPrincipal
 */
public class GestionnaireConflits {

    /**
     * Représente une paire d'aéronefs en situation de proximité ou de conflit.
     *
     * <p>Immuable : créé à chaque appel de {@link GestionnaireConflits#detecter(java.util.List)}.</p>
     */
    public static class Conflit {
        /** Premier aéronef de la paire. */
        private final Aeronef a1;
        /** Second aéronef de la paire. */
        private final Aeronef a2;
        /** Distance 3D en mètres entre les deux aéronefs au moment de la détection. */
        private final double  distance;

        /**
         * Construit un conflit entre deux aéronefs.
         *
         * @param a1       premier aéronef
         * @param a2       second aéronef
         * @param distance distance 3D en mètres
         */
        public Conflit(Aeronef a1, Aeronef a2, double distance) {
            this.a1       = a1;
            this.a2       = a2;
            this.distance = distance;
        }

        /** @return premier aéronef de la paire */
        public Aeronef getA1()       { return a1;       }
        /** @return second aéronef de la paire */
        public Aeronef getA2()       { return a2;       }
        /** @return distance 3D en mètres entre les deux aéronefs */
        public double  getDistance() { return distance; }

        /** @return représentation {@code "INDIC1 ↔ INDIC2 (dist m)"} */
        @Override
        public String toString() {
            return String.format("%s ↔ %s (%.0f m)",
                    a1.getIndicatif(), a2.getIndicatif(), distance);
        }
    }

    // ---------------------------------------------------------------

    /** Distance d'alarme en mètres (LED rouge). Modifiable en cours de simulation. */
    private double        distanceSeuil;
    /** Paires en alarme : distance ≤ seuil → LED rouge + buzzer. */
    private List<Conflit> conflitsActuels = new ArrayList<>();
    /**
     * Paires en approche : seuil &lt; distance ≤ 2×seuil → LED orange.
     * Permet d'alerter les contrôleurs avant que la situation ne devienne critique.
     */
    private List<Conflit> proximites = new ArrayList<>();

    /**
     * Construit le gestionnaire avec une distance seuil initiale.
     *
     * @param distanceSeuil distance d'alarme en mètres (ex. 400)
     */
    public GestionnaireConflits(double distanceSeuil) {
        this.distanceSeuil = distanceSeuil;
    }

    /** @return distance d'alarme actuelle en mètres */
    public double        getDistanceSeuil()         { return distanceSeuil;   }

    /**
     * Modifie la distance d'alarme à la volée (prise en compte au tick suivant).
     * @param d nouvelle distance en mètres
     */
    public void          setDistanceSeuil(double d) { this.distanceSeuil = d; }

    /** @return liste des paires en alarme (distance ≤ seuil) après le dernier appel à {@link #detecter} */
    public List<Conflit> getConflitsActuels()        { return conflitsActuels; }

    /**
     * Retourne les paires en approche (seuil &lt; distance ≤ 2×seuil) après le dernier appel à {@link #detecter}.
     * Ces paires déclenchent la LED orange mais pas le buzzer.
     * @return liste des proximités
     */
    public List<Conflit> getProximites()             { return proximites;      }

    /**
     * Analyse tous les aéronefs actifs et remplit deux listes :
     *  - conflitsActuels : distance ≤ seuil          → LED rouge
     *  - proximites      : seuil < distance ≤ 2×seuil → LED orange
     *
     * @param aeronefs liste complète (actifs et inactifs)
     * @return conflits d'alarme (≤ seuil)
     */
    public List<Conflit> detecter(List<Aeronef> aeronefs) {
        conflitsActuels = new ArrayList<>();
        proximites      = new ArrayList<>();

        List<Aeronef> actifs = new ArrayList<>();
        for (Aeronef a : aeronefs) {
            if (a.isActif() && a.getPositionCourante() != null) actifs.add(a);
        }

        for (int i = 0; i < actifs.size(); i++) {
            for (int j = i + 1; j < actifs.size(); j++) {
                Aeronef a1 = actifs.get(i);
                Aeronef a2 = actifs.get(j);
                double  d  = a1.getPositionCourante().distanceTo(a2.getPositionCourante());
                if (d <= distanceSeuil) {
                    conflitsActuels.add(new Conflit(a1, a2, d));         // alarme rouge
                } else if (d <= distanceSeuil * 2.0) {
                    proximites.add(new Conflit(a1, a2, d));              // approche orange
                }
            }
        }

        return conflitsActuels;
    }

    /**
     * Retourne les indicatifs des aéronefs actuellement en conflit (alarme rouge).
     * Utilisé par la vue 3D pour colorer les sphères en rouge.
     *
     * @return liste d'indicatifs (sans doublons)
     */
    public List<String> getIndicatifsEnConflit() {
        List<String> res = new ArrayList<>();
        for (Conflit c : conflitsActuels) {
            if (!res.contains(c.getA1().getIndicatif()))
                res.add(c.getA1().getIndicatif());
            if (!res.contains(c.getA2().getIndicatif()))
                res.add(c.getA2().getIndicatif());
        }
        return res;
    }
}
