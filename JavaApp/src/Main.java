import controleur.ControleurPrincipal;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Point d'entrée — Simulation Tour de Piste.
 *
 * Architecture MVC :
 *   - modele/     : logique métier (Simulation, Aeronef, GestionnaireConflits…)
 *   - vue/        : composants visuels (Vue3D, VuePanneauControle)
 *   - controleur/ : orchestration des événements (ControleurPrincipal)
 *   - parseur/    : lecture des fichiers de données
 */
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        ControleurPrincipal controleur = new ControleurPrincipal(primaryStage);
        Scene scene = controleur.buildScene();

        scene.getStylesheets().add(
                getClass().getResource("/resources/style.css").toExternalForm()
        );

        primaryStage.setTitle("Simulation Tour de Piste");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
