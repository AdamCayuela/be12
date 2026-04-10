package modele;

public class TypeAeronef {

    private final int    id;
    private final String nom;
    private final String categorie;
    private final double vitesse;   // km/h

    public TypeAeronef(int id, String nom, String categorie, double vitesse) {
        this.id        = id;
        this.nom       = nom;
        this.categorie = categorie;
        this.vitesse   = vitesse;
    }

    public int    getId()        { return id;        }
    public String getNom()       { return nom;       }
    public String getCategorie() { return categorie; }
    /** Vitesse en km/h. */
    public double getVitesse()   { return vitesse;   }
    /** Vitesse en m/s (pour les calculs de déplacement). */
    public double getVitesseMps() { return vitesse / 3.6; }

    @Override
    public String toString() { return nom + " (" + categorie + ")"; }
}
