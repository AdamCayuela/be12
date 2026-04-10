package modele;

import java.util.List;

/**
 * Une phase du circuit aérodrome (ex. A=décollage, E=vent arrière…).
 * Définie par une liste ordonnée de points 3D.
 */
public class Phase {

    private final String id;
    private final String nom;
    private final List<Point3D> points;

    public Phase(String id, String nom, List<Point3D> points) {
        this.id     = id;
        this.nom    = nom;
        this.points = points;
    }

    public String    getId()     { return id;     }
    public String    getNom()    { return nom;    }
    public List<Point3D> getPoints() { return points; }

    /** Longueur totale de la phase (somme des segments). */
    public double getLongueur() {
        double total = 0;
        for (int i = 0; i < points.size() - 1; i++) {
            total += points.get(i).distanceTo(points.get(i + 1));
        }
        return total;
    }

    @Override
    public String toString() { return id + " – " + nom; }
}
