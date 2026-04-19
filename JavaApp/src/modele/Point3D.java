package modele;

/**
 * Point dans l'espace 3D — coordonnées en mètres (référentiel du circuit).
 *
 * <p>Convention d'axes utilisée dans le circuit aérodrome :</p>
 * <ul>
 *   <li>{@code x} — position Est-Ouest (positif vers l'Est)</li>
 *   <li>{@code y} — altitude MSL en mètres (positif vers le haut)</li>
 *   <li>{@code z} — position Nord-Sud (positif vers le Nord)</li>
 * </ul>
 *
 * <p>La conversion vers le repère JavaFX 3D est réalisée dans {@link vue.Vue3D} :
 * {@code FX(x/S, −y/S, z/S)} avec {@code S = 10}.</p>
 *
 * @see vue.Vue3D
 * @see modele.CircuitAD.Segment#positionA(double)
 */
public class Point3D {

    /** Coordonnée Est-Ouest en mètres. */
    private final double x;
    /** Altitude en mètres (MSL). */
    private final double y;
    /** Coordonnée Nord-Sud en mètres. */
    private final double z;

    /**
     * Construit un point 3D à partir de ses coordonnées.
     *
     * @param x position Est-Ouest en mètres
     * @param y altitude en mètres
     * @param z position Nord-Sud en mètres
     */
    public Point3D(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** @return coordonnée Est-Ouest en mètres */
    public double getX() { return x; }
    /** @return altitude en mètres */
    public double getY() { return y; }
    /** @return coordonnée Nord-Sud en mètres */
    public double getZ() { return z; }

    /**
     * Calcule la distance euclidienne 3D entre ce point et {@code other}.
     *
     * @param other point cible
     * @return distance en mètres (toujours ≥ 0)
     */
    public double distanceTo(Point3D other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Retourne le point situé à la fraction {@code t} entre {@code this} et {@code other}.
     * Interpolation linéaire : {@code t = 0} → {@code this}, {@code t = 1} → {@code other}.
     *
     * @param other point d'arrivée
     * @param t     fraction du trajet ∈ [0, 1]
     * @return nouveau point interpolé
     */
    public Point3D interpoler(Point3D other, double t) {
        return new Point3D(
                x + t * (other.x - x),
                y + t * (other.y - y),
                z + t * (other.z - z)
        );
    }

    /** @return représentation {@code "(x, y, z)"} arrondie à l'unité */
    @Override
    public String toString() {
        return String.format("(%.0f, %.0f, %.0f)", x, y, z);
    }
}
