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

import java.util.Properties;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.stage.Stage;

public class CloudMask extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle("Cloud Masker");
        primaryStage.setFullScreen(true);
        
        Properties properties = new Properties();
        properties.load(getClass().getResourceAsStream("/cloudmask.properties"));
        String rowsStr = properties.getProperty("rows", "2");
        String colsStr = properties.getProperty("columns", "2");
        String widthStr = properties.getProperty("imageWidth", "512");
        String heightStr = properties.getProperty("imageHeight", "512");
        String imageScaleStr = properties.getProperty("scale", "1.0");

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
        
        double imageScale = 1.0;
        try {
            imageScale = Double.parseDouble(imageScaleStr);
        } catch (NumberFormatException e) {
        }
        
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(4));

        CloudMaskController controller = new CloudMaskController(width, height, imageScale, primaryStage);

        int col = 0;
        for (int row = 0; row < nRows; row++) {
            for (col = 0; col < nCols; col++) {
                MaskedVariableView view = new MaskedVariableView(width, height, imageScale, controller);
                grid.add(view, col, row);
            }
        }

        SettingsPane settings = controller.getSettingsPane();
        grid.add(settings, col, 0);

        grid.add(controller.getCompositeMaskView(), col, 1);
        
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
        scene.getStylesheets().add("/org/controlsfx/control/rangeslider.css");
        scene.getStylesheets().add(getClass().getResource("/cloudmask.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.show();
        
//        controller.loadDataset(new File("c:/Users/Guy/test_file.nc"));
//        controller.loadDataset(new File("/home/guy/test_file.nc"));4
//        controller.loadDataset(new File("/home/guy/Data/cloudmask.nc"));
    }
}
