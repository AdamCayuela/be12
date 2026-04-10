import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import vue.MainController;
import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.DigitalOutput;
import com.pi4j.io.gpio.digital.DigitalState;
import com.pi4j.plugin.gpiod.provider.gpio.digital.GpioDDigitalOutput;
import java.util.concurrent.TimeUnit;

/**
 * Point d'entrée — Simulation Tour de Piste.
 */
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        MainController mainController = new MainController(primaryStage);
        Scene scene = mainController.buildScene();
        Context pi4j = Pi4J.newAutoContext();
        DigitalOutput output=pi4j.digitalOutput().create(17);
        output.config().shutdownState(DigitalState.LOW);

        /*scene.getStylesheets().add(
                getClass().getResource("/resources/style.css").toExternalForm()
        );*/

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