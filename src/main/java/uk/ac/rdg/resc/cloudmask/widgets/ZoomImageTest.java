package uk.ac.rdg.resc.cloudmask.widgets;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

/**
 * Created by Guy on 05/12/2014.
 */
public class ZoomImageTest extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle("Zoom Image Test");

        ZoomableImageView image = new ZoomableImageView(2800,1600,new MandelbrotImageGenerator());
        HBox box = new HBox();
        box.getChildren().add(image);
        Scene scene = new Scene(box, 2800, 1600);
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}
