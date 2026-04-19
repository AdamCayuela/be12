package modele;

import java.util.List;

/**
 * Représente une phase du circuit aérodrome.
 *
 * <p>Le circuit est découpé en phases nommées par une lettre :</p>
 * <ul>
 *   <li>{@code A} — Décollage (départ de la piste)</li>
 *   <li>{@code B} — Montée initiale</li>
 *   <li>{@code C} à {@code I} — Branches du circuit (vent traversier, vent arrière…)</li>
 *   <li>{@code J} — Atterrissage (fin de piste, dernier tour uniquement)</li>
 *   <li>{@code K} — Remise de gaz (boucle pour les tours intermédiaires)</li>
 * </ul>
 *
 * <p>Chaque phase est définie par une suite ordonnée de {@link Point3D}.
 * Les segments sont les droites reliant deux points consécutifs.</p>
 *
 * @see CircuitAD
 * @see parseur.ParseurCircuit
 */
public class Phase {

    /** Identifiant de la phase (lettre A à K ou J). */
    private final String id;
    /** Nom descriptif de la phase (ex. {@code "Vent_arriere"}). */
    private final String nom;
    /** Suite ordonnée de points 3D définissant la trajectoire de la phase. */
    private final List<Point3D> points;

    /**
     * Construit une phase avec ses points de trajectoire.
     *
     * @param id     identifiant de la phase (ex. {@code "A"})
     * @param nom    nom descriptif (ex. {@code "Decollage"})
     * @param points liste ordonnée des points 3D (au moins 2)
     */
    public Phase(String id, String nom, List<Point3D> points) {
        this.id     = id;
        this.nom    = nom;
        this.points = points;
    }

    /**
     * Retourne l'identifiant de la phase (lettre unique).
     * @return identifiant (ex. {@code "A"}, {@code "J"})
     */
    public String getId() { return id; }

    /**
     * Retourne le nom descriptif de la phase.
     * @return nom (ex. {@code "Vent_arriere"})
     */
    public String getNom() { return nom; }

    /**
     * Retourne la liste ordonnée des points 3D de la phase.
     * @return liste de {@link Point3D} (non modifiable en pratique)
     */
    public List<Point3D> getPoints() { return points; }

    /**
     * Calcule la longueur totale de la phase en mètres.
     * Somme des distances entre points consécutifs.
     *
     * @return longueur totale en mètres
     */
    public double getLongueur() {
        double total = 0;
        for (int i = 0; i < points.size() - 1; i++) {
            total += points.get(i).distanceTo(points.get(i + 1));
        }
        return total;
    }

    /** @return représentation {@code "ID – Nom"} */
    @Override
    public String toString() { return id + " – " + nom; }
}
