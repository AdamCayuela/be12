package modele;

/** Point dans l'espace 3D — coordonnées en mètres (unités du circuit). */
public class Point3D {

    private final double x, y, z;

    public Point3D(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }

    public double distanceTo(Point3D other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Interpolation linéaire entre this et other (t ∈ [0,1]). */
    public Point3D interpoler(Point3D other, double t) {
        return new Point3D(
                x + t * (other.x - x),
                y + t * (other.y - y),
                z + t * (other.z - z)
        );
    }

    @Override
    public String toString() {
        return String.format("(%.0f, %.0f, %.0f)", x, y, z);
    }
}
