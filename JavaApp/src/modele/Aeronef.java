package modele;

import java.util.List;


public class Aeronef {

    private final String      indicatif;
    private final TypeAeronef type;
    private final int         nbToursMax;
    private final double      tempsDepart;    // heure de départ en secondes simulées

    // état de vol courant
    private int     segmentIndex   = 0;
    private double  distSurSegment = 0.0;
    private int     toursEffectues = 0;
    private boolean actif          = false;
    private boolean termine        = false;

    /** Position 3D courante, mise à jour à chaque tick de la simulation. */
    private Point3D positionCourante;

    /** Direction du segment courant en coordonnées circuit (dx et dz, non normalisés). */
    private double segDx = 1.0;
    private double segDz = 0.0;

    public Aeronef(String indicatif, TypeAeronef type, int nbToursMax, double tempsDepart) {
        this.indicatif   = indicatif;
        this.type        = type;
        this.nbToursMax  = nbToursMax;
        this.tempsDepart = tempsDepart;
    }

    /**
     * Déplace l'aéronef de {@code dt} secondes simulées.
     *
     * L'avion suit trois circuits différents selon son avancement :
     *  - Premier tour   : segmentsNormal (inclut A, le décollage depuis la piste)
     *  - Tours suivants : segmentsLoop   (repart de B, là où K finit)
     *  - Dernier tour   : segmentsFinal  (finit par J, l'atterrissage)
     *
     * @param segmentsNormal liste de segments pour le premier tour (A→…→K)
     * @param segmentsLoop   liste de segments pour les tours intermédiaires (B→…→K)
     * @param segmentsFinal  liste de segments pour le dernier tour (B→…→J)
     * @param dt             durée à simuler en secondes
     * @param tempsEcoule    temps total écoulé depuis le début de la simulation
     */
    public void avancer(List<CircuitAD.Segment> segmentsNormal,
                        List<CircuitAD.Segment> segmentsLoop,
                        List<CircuitAD.Segment> segmentsFinal,
                        double dt, double tempsEcoule) {
        if (termine) return;

        // L'avion ne décolle pas avant son heure de départ
        if (!actif) {
            if (tempsEcoule >= tempsDepart) {
                actif            = true;
                segmentIndex     = 0;
                distSurSegment   = 0.0;
                List<CircuitAD.Segment> segs = segmentsActuels(segmentsNormal, segmentsLoop, segmentsFinal);
                positionCourante = segs.get(0).getDebut();
                mettreAJourDirection(segs);
            } else {
                return;
            }
        }

        double distAParc = type.getVitesseMps() * dt;

        while (distAParc > 0 && !termine) {
            List<CircuitAD.Segment> segments = segmentsActuels(segmentsNormal, segmentsLoop, segmentsFinal);
            CircuitAD.Segment seg = segments.get(segmentIndex);
            double resteSurSeg   = seg.getLongueur() - distSurSegment;

            if (distAParc < resteSurSeg) {
                // L'avion reste sur ce segment
                distSurSegment += distAParc;
                distAParc = 0;
            } else {
                // L'avion dépasse la fin du segment, on passe au suivant
                distAParc -= resteSurSeg;
                segmentIndex++;

                if (segmentIndex < segments.size()) {
                    mettreAJourDirection(segments);
                }

                if (segmentIndex >= segments.size()) {
                    // Fin d'un tour complet
                    toursEffectues++;
                    if (toursEffectues >= nbToursMax) {
                        // Tous les tours sont faits, l'avion atterrit
                        termine          = true;
                        actif            = false;
                        positionCourante = seg.getFin();
                        return;
                    }
                    // Tour suivant : repart du début de la prochaine liste (B, pas A)
                    segmentIndex   = 0;
                    distSurSegment = 0.0;
                } else {
                    distSurSegment = 0.0;
                }
            }
        }

        // Calcul de la position exacte par interpolation sur le segment courant
        if (!termine) {
            List<CircuitAD.Segment> segments = segmentsActuels(segmentsNormal, segmentsLoop, segmentsFinal);
            CircuitAD.Segment seg = segments.get(segmentIndex);
            double t = (seg.getLongueur() > 0)
                    ? distSurSegment / seg.getLongueur()
                    : 0;
            positionCourante = seg.positionA(Math.min(t, 1.0));
        }
    }

    /**
     * Choisit la bonne liste de segments selon le nombre de tours déjà effectués.
     * Premier tour → Normal, tours du milieu → Loop, dernier tour → Final.
     */
    private List<CircuitAD.Segment> segmentsActuels(List<CircuitAD.Segment> segmentsNormal,
                                                     List<CircuitAD.Segment> segmentsLoop,
                                                     List<CircuitAD.Segment> segmentsFinal) {
        if (toursEffectues >= nbToursMax - 1) return segmentsFinal;
        if (toursEffectues == 0)              return segmentsNormal;
        return segmentsLoop;
    }

    /** Met à jour la direction (dx, dz) d'après le segment courant. */
    private void mettreAJourDirection(List<CircuitAD.Segment> segments) {
        if (segmentIndex < segments.size()) {
            Point3D debut = segments.get(segmentIndex).getDebut();
            Point3D fin   = segments.get(segmentIndex).getFin();
            segDx = fin.getX() - debut.getX();
            segDz = fin.getZ() - debut.getZ();
        }
    }

    public String      getIndicatif()        { return indicatif;       }
    public TypeAeronef getType()             { return type;            }
    public int         getNbToursMax()       { return nbToursMax;      }
    public double      getTempsDepart()      { return tempsDepart;     }
    public boolean     isActif()             { return actif;           }
    public boolean     isTermine()           { return termine;         }
    public int         getToursEffectues()   { return toursEffectues;  }
    public Point3D     getPositionCourante() { return positionCourante; }
    /** Direction X du segment courant (coordonnées circuit, non normalisé). */
    public double      getSegDx()            { return segDx;           }
    /** Direction Z du segment courant (coordonnées circuit, non normalisé). */
    public double      getSegDz()            { return segDz;           }

    /** Remet l'avion à son état initial, prêt pour un nouveau départ. */
    public void reinitialiser() {
        segmentIndex     = 0;
        distSurSegment   = 0.0;
        toursEffectues   = 0;
        actif            = false;
        termine          = false;
        positionCourante = null;
    }

    @Override
    public String toString() { return indicatif + " (" + type.getNom() + ")"; }
}
