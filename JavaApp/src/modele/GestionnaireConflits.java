package modele;

import java.util.ArrayList;
import java.util.List;

/**
 * Détecte les conflits de proximité entre aéronefs actifs.
 */
public class GestionnaireConflits {

    /** Paire d'aéronefs en conflit avec leur distance. */
    public static class Conflit {
        private final Aeronef a1;
        private final Aeronef a2;
        private final double  distance;

        public Conflit(Aeronef a1, Aeronef a2, double distance) {
            this.a1       = a1;
            this.a2       = a2;
            this.distance = distance;
        }

        public Aeronef getA1()       { return a1;       }
        public Aeronef getA2()       { return a2;       }
        public double  getDistance() { return distance; }

        @Override
        public String toString() {
            return String.format("%s ↔ %s (%.0f m)",
                    a1.getIndicatif(), a2.getIndicatif(), distance);
        }
    }

    // ---------------------------------------------------------------

    private double        distanceSeuil;  // mètres
    private List<Conflit> conflitsActuels = new ArrayList<>();

    public GestionnaireConflits(double distanceSeuil) {
        this.distanceSeuil = distanceSeuil;
    }

    public double getDistanceSeuil()            { return distanceSeuil;           }
    public void   setDistanceSeuil(double d)    { this.distanceSeuil = d;         }
    public List<Conflit> getConflitsActuels()   { return conflitsActuels;         }

    /**
     * Analyse la liste d'aéronefs actifs et met à jour {@link #conflitsActuels}.
     * @param aeronefs liste complète (actifs et non actifs)
     * @return liste des conflits détectés
     */
    public List<Conflit> detecter(List<Aeronef> aeronefs) {
        conflitsActuels = new ArrayList<>();

        List<Aeronef> actifs = new ArrayList<>();
        for (Aeronef a : aeronefs) {
            if (a.isActif() && a.getPositionCourante() != null) {
                actifs.add(a);
            }
        }

        for (int i = 0; i < actifs.size(); i++) {
            for (int j = i + 1; j < actifs.size(); j++) {
                Aeronef a1 = actifs.get(i);
                Aeronef a2 = actifs.get(j);
                double  d  = a1.getPositionCourante().distanceTo(a2.getPositionCourante());
                if (d <= distanceSeuil) {
                    conflitsActuels.add(new Conflit(a1, a2, d));
                }
            }
        }

        return conflitsActuels;
    }

    /** Retourne les indicatifs en conflit (pour colorer les sphères en rouge / orange ). */
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
