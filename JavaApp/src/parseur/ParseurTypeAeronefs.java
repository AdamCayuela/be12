package parseur;

import modele.TypeAeronef;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class ParseurTypeAeronefs {

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
