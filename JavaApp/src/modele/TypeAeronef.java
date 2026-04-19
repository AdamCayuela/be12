package modele;

/**
 * Décrit un type d'aéronef (ex. A320, Cessna 172…).
 *
 * <p>Un type regroupe les caractéristiques communes à tous les aéronefs
 * d'une même flotte : identifiant unique, nom commercial, catégorie OACI
 * et vitesse de croisière en circuit.</p>
 *
 * <p>Les catégories reconnues par la vue 3D sont :</p>
 * <ul>
 *   <li>{@code LIGHT}  — aviation légère (sphère bleue)</li>
 *   <li>{@code MEDIUM} — aviation commerciale courte-/moyen-courrier (sphère verte)</li>
 *   <li>{@code HIGH}   — gros porteurs / haute performance (sphère orange)</li>
 * </ul>
 *
 * @see Aeronef
 * @see parseur.ParseurTypeAeronefs
 */
public class TypeAeronef {

    /** Identifiant numérique unique du type (colonne {@code idTypeAeronef} du fichier). */
    private final int    id;
    /** Nom commercial du type (ex. {@code "A320"}, {@code "C172"}). */
    private final String nom;
    /** Catégorie OACI : {@code LIGHT}, {@code MEDIUM} ou {@code HIGH}. */
    private final String categorie;
    /** Vitesse de croisière en circuit, exprimée en km/h. */
    private final double vitesse;

    /**
     * Construit un type d'aéronef avec les caractéristiques données.
     *
     * @param id        identifiant numérique unique
     * @param nom       nom commercial (ex. {@code "A320"})
     * @param categorie catégorie OACI ({@code LIGHT}, {@code MEDIUM} ou {@code HIGH})
     * @param vitesse   vitesse de croisière en km/h
     */
    public TypeAeronef(int id, String nom, String categorie, double vitesse) {
        this.id        = id;
        this.nom       = nom;
        this.categorie = categorie;
        this.vitesse   = vitesse;
    }

    /**
     * Retourne l'identifiant numérique unique du type.
     * @return identifiant entier du type
     */
    public int    getId()        { return id;        }

    /**
     * Retourne le nom commercial du type.
     * @return nom du type (ex. {@code "A320"})
     */
    public String getNom()       { return nom;       }

    /**
     * Retourne la catégorie OACI du type.
     * @return {@code "LIGHT"}, {@code "MEDIUM"} ou {@code "HIGH"}
     */
    public String getCategorie() { return categorie; }

    /**
     * Retourne la vitesse de croisière en km/h.
     * @return vitesse en km/h
     */
    public double getVitesse()   { return vitesse;   }

    /**
     * Retourne la vitesse de croisière convertie en m/s.
     * Utilisée pour les calculs de déplacement à chaque tick de simulation.
     * @return vitesse en m/s ({@code vitesse / 3.6})
     */
    public double getVitesseMps() { return vitesse / 3.6; }

    /** @return représentation textuelle {@code "Nom (Catégorie)"} */
    @Override
    public String toString() { return nom + " (" + categorie + ")"; }
}
