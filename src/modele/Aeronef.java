package modele;

import java.util.List;


public class Aeronef {

    private final String      indicatif;
    private final TypeAeronef type;
    private final int         nbToursMax;
    private final double      tempsDepart;    // secondes (temps simulation)

    // --- état courant ---
    private int     segmentIndex   = 0;
    private double  distSurSegment = 0.0;
    private int     toursEffectues = 0;
    private boolean actif          = false;
    private boolean termine        = false;

    /** Position 3D courante (mise à jour à chaque tick). */
    private Point3D positionCourante;

    public Aeronef(String indicatif, TypeAeronef type, int nbToursMax, double tempsDepart) {
        this.indicatif   = indicatif;
        this.type        = type;
        this.nbToursMax  = nbToursMax;
        this.tempsDepart = tempsDepart;
    }

    /**
     * Fait avancer l'aéronef de {@code dt} secondes (temps simulation). A retravailler !!!
     *
     * @param segmentsNormal segments A→I→K (remise de gaz, tours non-finaux)
     * @param segmentsFinal  segments A→I→J (atterrissage, dernier tour)
     * @param dt             durée du tick en secondes (temps simulé)
     * @param tempsEcoule    temps total écoulé depuis le départ de la simulation
     */
    public void avancer(List<CircuitAD.Segment> segmentsNormal,
                        List<CircuitAD.Segment> segmentsFinal,
                        double dt, double tempsEcoule) {
        if (termine) return;

        // Activation au bon moment
        if (!actif) {
            if (tempsEcoule >= tempsDepart) {
                actif            = true;
                segmentIndex     = 0;
                distSurSegment   = 0.0;
                positionCourante = segmentsActuels(segmentsNormal, segmentsFinal).get(0).getDebut();
            } else {
                return;
            }
        }

        double distAParc = type.getVitesseMps() * dt;

        while (distAParc > 0 && !termine) {
            List<CircuitAD.Segment> segments = segmentsActuels(segmentsNormal, segmentsFinal);
            CircuitAD.Segment seg = segments.get(segmentIndex);
            double resteSurSeg   = seg.getLongueur() - distSurSegment;

            if (distAParc < resteSurSeg) {
                // Reste sur ce segment
                distSurSegment += distAParc;
                distAParc = 0;
            } else {
                // Avance au segment suivant
                distAParc -= resteSurSeg;
                segmentIndex++;

                if (segmentIndex >= segments.size()) {
                    // Fin d'un tour complet
                    toursEffectues++;
                    if (toursEffectues >= nbToursMax) {
                        // Dernier tour terminé → atterrissage
                        termine          = true;
                        actif            = false;
                        positionCourante = seg.getFin();
                        return;
                    }
                    // Prochain tour : réinitialiser (remise de gaz → retour en circuit donc erreur a changer )
                    segmentIndex   = 0;
                    distSurSegment = 0.0;
                } else {
                    distSurSegment = 0.0;
                }
            }
        }

        // Mise à jour de la position par interpolation
        if (!termine) {
            List<CircuitAD.Segment> segments = segmentsActuels(segmentsNormal, segmentsFinal);
            CircuitAD.Segment seg = segments.get(segmentIndex);
            double t = (seg.getLongueur() > 0)
                    ? distSurSegment / seg.getLongueur()
                    : 0;
            positionCourante = seg.positionA(Math.min(t, 1.0));
        }
    }

    private List<CircuitAD.Segment> segmentsActuels(List<CircuitAD.Segment> segmentsNormal,
                                                     List<CircuitAD.Segment> segmentsFinal) {
        return (toursEffectues >= nbToursMax - 1) ? segmentsFinal : segmentsNormal;
    }

    public String      getIndicatif()        { return indicatif;       }
    public TypeAeronef getType()             { return type;            }
    public int         getNbToursMax()       { return nbToursMax;      }
    public double      getTempsDepart()      { return tempsDepart;     }
    public boolean     isActif()             { return actif;           }
    public boolean     isTermine()           { return termine;         }
    public int         getToursEffectues()   { return toursEffectues;  }
    public Point3D     getPositionCourante() { return positionCourante; }

    /** Réinitialise l'aéronef pour un nouveau run de simulation. */
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