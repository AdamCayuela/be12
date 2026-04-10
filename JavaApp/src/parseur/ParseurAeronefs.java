package parseur;

import modele.Aeronef;
import modele.TypeAeronef;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ParseurAeronefs {

    public static List<Aeronef> charger(File fichier,
                                        List<TypeAeronef> types) throws IOException {
        // Index types par id pour recherche rapide
        Map<Integer, TypeAeronef> indexTypes = types.stream()
                .collect(Collectors.toMap(TypeAeronef::getId, t -> t));

        List<Aeronef> aeronefs = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(fichier))) {
            String ligne;
            boolean premiereLigne = true;

            while ((ligne = br.readLine()) != null) {
                ligne = ligne.trim();
                if (ligne.isEmpty()) continue;

                if (premiereLigne) {
                    premiereLigne = false;
                    if (ligne.startsWith("indicatif")) continue;
                }

                String[] tokens = ligne.split("\\s+");
                if (tokens.length < 4) continue;

                try {
                    String     indicatif   = tokens[0];
                    int        idType      = Integer.parseInt(tokens[1]);
                    int        nbTours     = Integer.parseInt(tokens[2]);
                    double     tempsDepart = Double.parseDouble(tokens[3]);
                    TypeAeronef type       = indexTypes.get(idType);

                    if (type == null) {
                        System.err.println("Type inconnu : " + idType + " pour " + indicatif);
                        continue;
                    }

                    aeronefs.add(new Aeronef(indicatif, type, nbTours, tempsDepart));
                } catch (NumberFormatException ignored) {
                    // ligne malformée ignorée
                }
            }
        }

        return aeronefs;
    }
}
