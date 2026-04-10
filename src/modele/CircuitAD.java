package modele;

import java.util.ArrayList;
import java.util.List;

/**
 * Circuit aérodrome : ensemble de phases A→K.  A refaire ERREUR
 *
 * Fournit DEUX listes plates de segments :
 *  - segmentsNormal  : A→B→C→D→E→F→G→H→I→K  (remise de gaz, tours non-finaux)
 *  - segmentsFinal   : A→B→C→D→E→F→G→H→I→J  (atterrissage, dernier tour)
 *
 * Phase J (atterrissage) = uniquement au dernier tour.
 * Phase K (remise de gaz) = à la fin de chaque tour sauf le dernier.
 * Phase I (finale) = toujours exécutée (chemin de H vers le seuil de piste).
 */
public class CircuitAD {

    /** Un segment reliant deux points consécutifs d'une phase. */
    public static class Segment {
        private final Point3D debut;
        private final Point3D fin;
        private final double  longueur;

        public Segment(Point3D debut, Point3D fin) {
            this.debut    = debut;
            this.fin      = fin;
            this.longueur = debut.distanceTo(fin);
        }

        public Point3D getDebut()    { return debut;    }
        public Point3D getFin()      { return fin;      }
        public double  getLongueur() { return longueur; }

        public Point3D positionA(double t) {
            return debut.interpoler(fin, t);
        }
    }

    // ---------------------------------------------------------------

    private final List<Phase>   phases;
    private final List<Segment> segmentsNormal;  // A→I→K (remise de gaz)
    private final List<Segment> segmentsFinal;   // A→I→J (atterrissage)

    public CircuitAD(List<Phase> phases) {
        this.phases          = phases;
        this.segmentsNormal  = construireSegments(false);   // inclut K, exclut J
        this.segmentsFinal   = construireSegments(true);    // inclut J, exclut K
    }

    /**
     * Construit la liste plate des segments.
     * @param dernierTour true → inclure J (atterrissage), exclure K (remise de gaz)
     *                    false → inclure K, exclure J
     */
    private List<Segment> construireSegments(boolean dernierTour) {
        List<Segment> liste = new ArrayList<>();
        for (Phase phase : phases) {
            String id = phase.getId();
            if (dernierTour  && "K".equals(id)) continue;  // dernier tour : skip remise de gaz
            if (!dernierTour && "J".equals(id)) continue;  // tour normal  : skip atterrissage
            List<Point3D> pts = phase.getPoints();
            for (int i = 0; i < pts.size() - 1; i++) {
                liste.add(new Segment(pts.get(i), pts.get(i + 1)));
            }
        }
        return liste;
    }

    public List<Phase>   getPhases()          { return phases;          }
    /** Segments A→I→K pour les tours non-finaux (remise de gaz). */
    public List<Segment> getSegmentsNormal()  { return segmentsNormal;  }
    /** Segments A→I→J pour le dernier tour (atterrissage). */
    public List<Segment> getSegmentsFinal()   { return segmentsFinal;   }
    /** Alias du circuit final pour la compatibilité (affichage). */
    public List<Segment> getSegmentsCircuit() { return segmentsFinal;   }

    public double getLongueurTotale() {
        double t = 0;
        for (Segment s : segmentsFinal) t += s.getLongueur();
        return t;
    }
}