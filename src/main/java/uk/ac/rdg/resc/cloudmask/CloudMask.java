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
import java.util.Properties;

import org.controlsfx.control.RangeSlider;
import org.junit.internal.runners.model.MultipleFailureException;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import uk.ac.rdg.resc.cloudmask.CloudMaskDatasetFactory.MaskedDataset;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.graphics.style.util.GraphicsUtils;
import uk.ac.rdg.resc.edal.graphics.style.util.SimpleFeatureCatalogue;

public class CloudMask extends Application {

    private MaskedDataset dataset;

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
        if(nRows < 2) {
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
        CompositeMaskPane comp = new CompositeMaskPane(null, width, height);
        int col = 0;
        for(int row = 0;row<nRows;row++) {
            for(col = 0;col<nCols;col++) {
                grid.add(new MaskingPane(width, height, comp), col, row);
            }
        }
        grid.add(comp, col, 0);

        Button button = new Button("Press me!");
        button.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                try {
                    CloudMaskDatasetFactory mdf = new CloudMaskDatasetFactory();
                    dataset = mdf.createDataset("test", "/home/guy/test_file.nc");
                    SimpleFeatureCatalogue<MaskedDataset> catalogue = new SimpleFeatureCatalogue<>(
                            dataset, true);
                    comp.setCatalogue(catalogue);
                } catch (EdalException | IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        });
        grid.add(button, col, 1);

//        VariablePlugin diffPlugin = new NormalisedDiffPlugin("btemp_nadir_1100", "btemp_nadir_1200");
//
//        Button button = new Button("Press me!");
//        button.setOnAction(new EventHandler<ActionEvent>() {
//            @Override
//            public void handle(ActionEvent event) {
//                try {
//                    dataset.addVariablePlugin(diffPlugin);
//                } catch (EdalException e) {
//                    e.printStackTrace();
//                }
//            }
//        });
//        grid.add(button, 0, 1);

//        grid.add(view1, 0, 0);
//        grid.add(view2, 0, 1);
//        grid.add(view3, 1, 1);
//        grid.add(view4, 1, 0);

        Scene scene = new Scene(grid, 300, 250);
        scene.getStylesheets().add(getClass().getResource("/colourbar.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
//        CloudMaskDatasetFactory.writeDataset(dataset, "/home/guy/outputtest.nc");
    }
}
