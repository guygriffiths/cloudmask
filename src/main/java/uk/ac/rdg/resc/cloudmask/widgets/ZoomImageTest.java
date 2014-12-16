package uk.ac.rdg.resc.cloudmask.widgets;

import uk.ac.rdg.resc.edal.position.HorizontalPosition;
import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
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

        ZoomableImageView image = new ZoomableImageView(500, 500, new MandelbrotImageGenerator());
        HBox box = new HBox();
        box.getChildren().add(image);
        Label label = new Label();
        box.getChildren().add(label);
        image.setOnMouseMoved(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                HorizontalPosition coords = image.getCoordinateFromImagePosition(event.getX(),
                        event.getY());
                label.textProperty().set(
                        event.getX() + "," + event.getY() + "\n" + coords.getX() + ","
                                + coords.getY());
            }
        });

        Scene scene = new Scene(box, 500, 500);
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}
