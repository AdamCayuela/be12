package modele;



import java.util.ArrayList;
import java.util.List;

/**
 * Représente le circuit aérodrome complet, composé de phases A à K.
 *
 * <p>Le circuit comporte trois variantes selon l'avancement du vol :</p>
 * <ul>
 *   <li><b>Premier tour</b> ({@code segmentsNormal}) : A (décollage) → B → … → I → K (remise de gaz)</li>
 *   <li><b>Tours intermédiaires</b> ({@code segmentsLoop}) : B → … → I → K (sans A)</li>
 *   <li><b>Dernier tour</b> ({@code segmentsFinal}) : B → … → I → J (atterrissage, sans A ni K)</li>
 * </ul>
 *
 * <p><b>Pourquoi trois listes ?</b> K se termine exactement là où B commence.
 * Si l'avion repartait du début de A après chaque remise de gaz, il redécollerait
 * depuis la piste à chaque tour — ce qui ne correspond pas au comportement réel.</p>
 *
 * @see Phase
 * @see parseur.ParseurCircuit
 * @see Aeronef#avancer(java.util.List, java.util.List, java.util.List, double, double)
 */
public class CircuitAD {

    /**
     * Segment rectiligne entre deux points consécutifs d'une phase.
     *
     * <p>La longueur est calculée une seule fois à la construction
     * (distance euclidienne 3D entre {@code debut} et {@code fin}).</p>
     */
    public static class Segment {
        /** Point de départ du segment. */
        private final Point3D debut;
        /** Point d'arrivée du segment. */
        private final Point3D fin;
        /** Longueur du segment en mètres (distance euclidienne 3D). */
        private final double  longueur;

        /**
         * Construit un segment entre deux points.
         * La longueur est calculée automatiquement.
         *
         * @param debut point de départ
         * @param fin   point d'arrivée
         */
        public Segment(Point3D debut, Point3D fin) {
            this.debut    = debut;
            this.fin      = fin;
            this.longueur = debut.distanceTo(fin);
        }

        /** @return point de départ du segment */
        public Point3D getDebut()    { return debut;    }
        /** @return point d'arrivée du segment */
        public Point3D getFin()      { return fin;      }
        /** @return longueur du segment en mètres */
        public double  getLongueur() { return longueur; }

        /**
         * Retourne la position interpolée sur ce segment.
         *
         * @param t fraction du trajet ∈ [0, 1] (0 = début, 1 = fin)
         * @return point 3D interpolé
         */
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
    /** Toutes les phases A→K + J : utilisé uniquement pour l'affichage 3D du tracé complet. */
    private final List<Segment> segmentsAffichage;

    public CircuitAD(List<Phase> phases) {
        this.phases            = phases;
        this.segmentsNormal    = construireSegments(false, true);   // A inclus, J exclu
        this.segmentsLoop      = construireSegments(false, false);  // A exclu,  J exclu
        this.segmentsFinal     = construireSegments(true,  false);  // A exclu,  K exclu, J inclus
        this.segmentsAffichage = construireSegmentsAffichage();     // toutes les phases A, B…K et J
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

    /**
     * Construit la liste de segments pour l'affichage 3D du tracé complet.
     * Inclut toutes les phases : A (décollage), B→I (circuit), J (atterrissage) et K (remise de gaz).
     */
    private List<Segment> construireSegmentsAffichage() {
        List<Segment> liste = new ArrayList<>();
        for (Phase phase : phases) {
            List<Point3D> pts = phase.getPoints();
            for (int i = 0; i < pts.size() - 1; i++) {
                liste.add(new Segment(pts.get(i), pts.get(i + 1)));
            }
        }
        return liste;
    }

    public List<Phase>   getPhases()           { return phases;             }
    /** Segments du premier tour : A → B → … → I → K. */
    public List<Segment> getSegmentsNormal()   { return segmentsNormal;    }
    /** Segments des tours intermédiaires : B → … → I → K (sans A). */
    public List<Segment> getSegmentsLoop()     { return segmentsLoop;      }
    /** Segments du dernier tour : B → … → I → J (sans A, sans K). */
    public List<Segment> getSegmentsFinal()    { return segmentsFinal;     }
    /** Toutes les phases A→K + J : tracé complet affiché en 3D. */
    public List<Segment> getSegmentsCircuit()  { return segmentsAffichage; }

    /** Longueur totale du dernier tour (utile pour les estimations de durée). */
    public double getLongueurTotale() {
        double t = 0;
        for (Segment s : segmentsFinal) t += s.getLongueur();
        return t;
    }
}
