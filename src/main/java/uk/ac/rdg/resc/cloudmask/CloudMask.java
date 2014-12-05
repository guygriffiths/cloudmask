/*******************************************************************************
 * Copyright (c) 2014 The University of Reading
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the University of Reading, nor the names of the
 *    authors or contributors may be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/

package uk.ac.rdg.resc.cloudmask;

import java.io.IOException;
import java.util.Optional;
import java.util.Properties;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import uk.ac.rdg.resc.cloudmask.widgets.PaletteSelector;
import uk.ac.rdg.resc.edal.dataset.plugins.DifferencePlugin;
import uk.ac.rdg.resc.edal.exceptions.EdalException;

public class CloudMask extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle("Cloud Masker");

        Properties settings = new Properties();
        settings.load(getClass().getResourceAsStream("/cloudmask.properties"));
        String rowsStr = settings.getProperty("rows", "2");
        String colsStr = settings.getProperty("columns", "2");
        String widthStr = settings.getProperty("imageWidth", "512");
        String heightStr = settings.getProperty("imageHeight", "512");

        int nRows = 2;
        try {
            nRows = Integer.parseInt(rowsStr);
        } catch (NumberFormatException e) {
        }
        if (nRows < 2) {
            /*
             * We need at least 2 rows for the composite and the settings panel.
             * 
             * TODO allow a single row and change the layout
             */
            nRows = 2;
        }

        int nCols = 2;
        try {
            nCols = Integer.parseInt(colsStr);
        } catch (NumberFormatException e) {
        }

        int width = 512;
        try {
            width = Integer.parseInt(widthStr);
        } catch (NumberFormatException e) {
        }

        int height = 512;
        try {
            height = Integer.parseInt(heightStr);
        } catch (NumberFormatException e) {
        }

        GridPane grid = new GridPane();

        CloudMaskController controller = new CloudMaskController(width, height);

        int col = 0;
        for (int row = 0; row < nRows; row++) {
            for (col = 0; col < nCols; col++) {
                MaskedVariableView view = new MaskedVariableView(width, height, controller);
                grid.add(view, col, row);
            }
        }
        grid.add(controller.getCompositeMaskView(), col, 0);

        Button button = new Button("Load dataset");
        button.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                try {
                    controller.loadDataset("/home/guy/test_file.nc");
                } catch (EdalException | IOException e) {
                    e.printStackTrace();
                }
            }
        });
        VBox box = new VBox();
        box.getChildren().add(button);
        Button button1 = new Button("Add plugin");
        button1.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                controller.addPlugin(new DifferencePlugin("lat", "lon"));
            }
        });
        box.getChildren().add(button1);
        Button button2 = new Button("Palette selection");
        button2.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                PaletteSelector ps = new PaletteSelector();
                Optional<String> result = ps.showAndWait();
                if(result.isPresent()) {
                    System.out.println(result.get()+" was selected");
                }
            }
        });
        box.getChildren().add(button2);
        grid.add(box, col, 1);

        int WINDOW_WIDTH = 500;
        int WINDOW_HEIGHT = 500;
        nCols++;
        for(int i=0;i<nCols;i++ ) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0/nCols);
            grid.getColumnConstraints().add(column);
        }
        for(int i=0;i<nRows;i++ ) {
            RowConstraints row = new RowConstraints();
            row.setPercentHeight(100.0/nRows);
            grid.getRowConstraints().add(row);
        }
        grid.setPrefSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        grid.setMaxSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        
        Scene scene = new Scene(grid, WINDOW_WIDTH, WINDOW_HEIGHT);
        scene.getStylesheets().add(getClass().getResource("/cloudmask.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
//        CloudMaskDatasetFactory.writeDataset(dataset, "/home/guy/outputtest.nc");
    }
}
