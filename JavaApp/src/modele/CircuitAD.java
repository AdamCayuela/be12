package modele;



import java.util.ArrayList;
import java.util.List;

/**
 * Représente le circuit aérodrome complet, composé de phases A à K.
 *
 * On distingue trois variantes du circuit selon le moment du vol :
 *  - Premier tour  : inclut A (décollage depuis la piste), se termine par K (remise de gaz)
 *  - Tours du milieu : repart du point où K finit (entrée de B), se termine par K
 *  - Dernier tour  : repart de B, se termine par J (atterrissage)
 *
 * Pourquoi trois listes et pas deux ?
 * K finit exactement où B commence. Si on remettait l'avion au début de A après K,
 * il redécollerait depuis la piste à chaque tour — ce qui n'est pas le comportement voulu.
 */
public class CircuitAD {

    /** Un segment = droite entre deux points consécutifs d'une phase. */
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

        /** Retourne la position interpolée sur ce segment pour t ∈ [0, 1]. */
        public Point3D positionA(double t) {
            return debut.interpoler(fin, t);
        }
    }

    // ---------------------------------------------------------------

    private final List<Phase>   phases;
    /** Premier tour : A (décollage) → B → … → I → K (remise de gaz). */
    private final List<Segment> segmentsNormal;
    /** Tours intermédiaires : B → … → I → K, sans A car l'avion repart de la fin de K. */
    private final List<Segment> segmentsLoop;
    /** Dernier tour : B → … → I → J (atterrissage), sans A ni K. */
    private final List<Segment> segmentsFinal;

    public CircuitAD(List<Phase> phases) {
        this.phases         = phases;
        this.segmentsNormal = construireSegments(false, true);   // A inclus, J exclu
        this.segmentsLoop   = construireSegments(false, false);  // A exclu,  J exclu
        this.segmentsFinal  = construireSegments(true,  false);  // A exclu,  K exclu, J inclus
    }

    /**
     * Assemble la liste de segments à partir des phases du fichier.
     *
     * @param dernierTour si vrai, on inclut J et on saute K (atterrissage final)
     * @param inclureA    si vrai, on inclut la phase A (décollage depuis la piste)
     */
    private List<Segment> construireSegments(boolean dernierTour, boolean inclureA) {
        List<Segment> liste = new ArrayList<>();
        for (Phase phase : phases) {
            String id = phase.getId();
            if (!inclureA    && "A".equals(id)) continue;
            if (dernierTour  && "K".equals(id)) continue;
            if (!dernierTour && "J".equals(id)) continue;
            List<Point3D> pts = phase.getPoints();
            for (int i = 0; i < pts.size() - 1; i++) {
                liste.add(new Segment(pts.get(i), pts.get(i + 1)));
            }
        }
        return liste;
    }

    public List<Phase>   getPhases()          { return phases;          }
    /** Segments du premier tour : A → B → … → I → K. */
    public List<Segment> getSegmentsNormal()  { return segmentsNormal;  }
    /** Segments des tours intermédiaires : B → … → I → K (sans A). */
    public List<Segment> getSegmentsLoop()    { return segmentsLoop;    }
    /** Segments du dernier tour : B → … → I → J (sans A, sans K). */
    public List<Segment> getSegmentsFinal()   { return segmentsFinal;   }
    /** Alias vers le circuit final, utilisé pour l'affichage 3D. */
    public List<Segment> getSegmentsCircuit() { return segmentsFinal;   }

    /** Longueur totale du dernier tour (utile pour les estimations de durée). */
    public double getLongueurTotale() {
        double t = 0;
        for (Segment s : segmentsFinal) t += s.getLongueur();
        return t;
    }
}
