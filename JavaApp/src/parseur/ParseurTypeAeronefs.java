package parseur;

import modele.TypeAeronef;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * Lit un fichier de types d'aéronefs et retourne la liste des {@link TypeAeronef}.
 *
 * <h2>Format attendu du fichier</h2>
 * <pre>
 * idTypeAeronef  nom     categorie  vitesse_kmh
 * 1              A320    MEDIUM     850
 * 2              C172    LIGHT      185
 * </pre>
 * <ul>
 *   <li>Colonnes séparées par des espaces (multiples acceptés).</li>
 *   <li>La ligne d'en-tête commençant par {@code idTypeAeronef} est ignorée.</li>
 *   <li>Les catégories reconnues par la vue 3D : {@code LIGHT}, {@code MEDIUM}, {@code HIGH}.</li>
 *   <li>Les lignes malformées sont silencieusement ignorées.</li>
 * </ul>
 *
 * @see TypeAeronef
 */
public class ParseurTypeAeronefs {

    /**
     * Charge le fichier de types d'aéronefs.
     *
     * @param fichier fichier texte décrivant les types
     * @return liste des types chargés (peut être vide si aucune ligne valide)
     * @throws IOException si le fichier ne peut pas être lu
     */
    public static List<TypeAeronef> charger(File fichier) throws IOException {
        List<TypeAeronef> types = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(fichier))) {
            String ligne;
            boolean premiereLigne = true;

            while ((ligne = br.readLine()) != null) {
                ligne = ligne.trim();
                if (ligne.isEmpty()) continue;

                if (premiereLigne) {
                    premiereLigne = false;
                    if (ligne.startsWith("idTypeAeronef")) continue;
                }

                String[] tokens = ligne.split("\\s+");
                if (tokens.length < 4) continue;

                try {
                    int    id        = Integer.parseInt(tokens[0]);
                    String nom       = tokens[1];
                    String categorie = tokens[2];
                    double vitesse   = Double.parseDouble(tokens[3]);
                    types.add(new TypeAeronef(id, nom, categorie, vitesse));
                } catch (NumberFormatException ignored) {
                    // ligne malformée ignorée
                }
            }
        }

        return types;
    }
}
