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

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import uk.ac.rdg.resc.edal.dataset.AbstractGridDataset;
import uk.ac.rdg.resc.edal.dataset.cdm.NativeCdmGridDatasetFactory;
import uk.ac.rdg.resc.edal.dataset.plugins.VariablePlugin;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.graphics.style.util.SimpleFeatureCatalogue;
import uk.ac.rdg.resc.edal.metadata.GridVariableMetadata;
import uk.ac.rdg.resc.edal.metadata.Parameter;
import uk.ac.rdg.resc.edal.metadata.VariableMetadata;
import uk.ac.rdg.resc.edal.position.HorizontalPosition;
import uk.ac.rdg.resc.edal.util.Extents;

public class CloudMask extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    double offsetX = 0;
    double offsetY = 0;
    double zoom = 1.0;

    private class DiffPlugin extends VariablePlugin {

        public DiffPlugin(String xVar, String yVar) {
            super(new String[] { xVar, yVar }, new String[] { "diff" });
        }

        @Override
        protected VariableMetadata[] doProcessVariableMetadata(VariableMetadata... metadata)
                throws EdalException {
            GridVariableMetadata xMeta = (GridVariableMetadata) metadata[0];
            GridVariableMetadata yMeta = (GridVariableMetadata) metadata[1];

            System.out.println(xMeta.getParameter().getTitle() + ", "
                    + xMeta.getParameter().getDescription());
            VariableMetadata diffMeta = newVariableMetadataFromMetadata(new Parameter(
                    getFullId("diff"), xMeta.getParameter().getTitle() + " - "
                            + yMeta.getParameter().getTitle(),
                    "Auto-generated difference between variables " + xMeta.getId() + " and "
                            + yMeta.getId(), xMeta.getParameter().getUnits(), null), true, xMeta,
                    yMeta);
            diffMeta.setParent(xMeta.getParent(), null);
            return new VariableMetadata[] { diffMeta };
        }

        @Override
        protected Number generateValue(String varSuffix, HorizontalPosition pos,
                Number... sourceValues) {
            return sourceValues[0].doubleValue() - sourceValues[1].doubleValue();
        }
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle("Cloud Masker");

        GridPane grid = new GridPane();

        NativeCdmGridDatasetFactory df = new NativeCdmGridDatasetFactory();
        AbstractGridDataset dataset = df.createDataset("test", "/home/guy/test_file.nc");
        SimpleFeatureCatalogue catalogue = new SimpleFeatureCatalogue(dataset, true);

        EdalImageGenerator temp1 = new EdalImageGenerator("btemp_nadir_0370", catalogue, Extents.newExtent(260f, 300f));
        EdalImageGenerator temp2 = new EdalImageGenerator("btemp_nadir_1100", catalogue, Extents.newExtent(260f, 300f));
        EdalImageGenerator temp3 = new EdalImageGenerator("btemp_nadir_1200", catalogue, Extents.newExtent(260f, 300f));

        VariablePlugin diffPlugin = new DiffPlugin("btemp_nadir_1100", "btemp_nadir_1200");

        LinkedZoomableImageView temp1View = new LinkedZoomableImageView(512, 512, temp1);
        LinkedZoomableImageView temp2View = new LinkedZoomableImageView(512, 512, temp2);
        LinkedZoomableImageView temp3View = new LinkedZoomableImageView(512, 512, temp3);

        temp1View.addLinkedView(temp2View);
        temp1View.addLinkedView(temp3View);
        grid.add(temp1View, 0, 0);
        grid.add(temp2View, 1, 0);
        grid.add(temp3View, 0, 1);
        
        Button button = new Button("Press me!");
        button.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                try {
                    dataset.addVariablePlugin(diffPlugin);
                    for (String var : dataset.getVariableIds()) {
                        System.out.println(var);
                    }
                    LinkedZoomableImageView diffView = new LinkedZoomableImageView(512, 512, new EdalImageGenerator(
                            "btemp_nadir_1100btemp_nadir_1200-diff", catalogue));
                    temp1View.addLinkedView(diffView);
                    grid.add(diffView, 1, 1);
                    grid.getChildren().remove(button);
                } catch (EdalException | IOException e) {
                    e.printStackTrace();
                }
            }
        });
        grid.add(button, 1, 1);

        primaryStage.setScene(new Scene(grid, 300, 250));
        primaryStage.show();
    }
}
