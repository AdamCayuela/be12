import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import View.MainController;

/**
 * Point d'entrée — Simulation Tour de Piste.
 */
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        MainController mainController = new MainController(primaryStage);
        Scene scene = mainController.buildScene();


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
