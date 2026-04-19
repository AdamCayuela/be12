package parseur;

import modele.CircuitAD;
import modele.Phase;
import modele.Point3D;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lit un fichier de circuit aérodrome et construit l'objet {@link CircuitAD}.
 *
 * <h2>Format attendu du fichier</h2>
 * <p>Le fichier texte tabulé contient une ligne d'en-tête puis une ligne par phase :</p>
 * <pre>
 * idPhase  nomPhase  x1,y1,z1  x2,y2,z2  …
 * A        Decollage  0,0,0  100,50,0  …
 * B        Montee     100,50,0  200,100,0  …
 * </pre>
 * <ul>
 *   <li>Les colonnes sont séparées par des espaces (multiples acceptés).</li>
 *   <li>Chaque point est encodé {@code x,y,z} sans espace (virgule séparateur).</li>
 *   <li>Les lignes malformées et les phases avec moins de 2 points sont ignorées.</li>
 * </ul>
 *
 * @see CircuitAD
 * @see Phase
 */
public class ParseurCircuit {

    /**
     * Charge le fichier de circuit et retourne le {@link CircuitAD} correspondant.
     *
     * @param fichier fichier texte décrivant le circuit aérodrome
     * @return circuit aérodrome prêt à être utilisé par la simulation
     * @throws IOException si le fichier ne peut pas être lu
     */
    public static CircuitAD charger(File fichier) throws IOException {
        List<Phase> phases = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(fichier))) {
            String ligne;
            boolean premiereLigne = true;

            while ((ligne = br.readLine()) != null) {
                ligne = ligne.trim();
                if (ligne.isEmpty()) continue;

                // Sauter la ligne d'en-tête
                if (premiereLigne) {
                    premiereLigne = false;
                    if (ligne.startsWith("idPhase")) continue;
                }

                // Séparer sur espace(s) multiples
                String[] tokens = ligne.split("\\s+");
                if (tokens.length < 3) continue;

                String idPhase  = tokens[0];
                String nomPhase = tokens[1];

                List<Point3D> points = new ArrayList<>();
                for (int i = 2; i < tokens.length; i++) {
                    // Nettoyer les virgules parasites en fin de token (ex. "-1000,1000,-200,")
                    String tok = tokens[i].replaceAll(",$", "").trim();
                    if (tok.isEmpty()) continue;

                    String[] coords = tok.split(",");
                    if (coords.length != 3) continue;

                    try {
                        double x = Double.parseDouble(coords[0].trim());
                        double y = Double.parseDouble(coords[1].trim());
                        double z = Double.parseDouble(coords[2].trim());
                        points.add(new Point3D(x, y, z));
                    } catch (NumberFormatException ignored) {
                        // token malformé ignoré
                    }
                }

                if (points.size() >= 2) {
                    phases.add(new Phase(idPhase, nomPhase, points));
                }
            }
        }

        return new CircuitAD(phases);
    }
}
