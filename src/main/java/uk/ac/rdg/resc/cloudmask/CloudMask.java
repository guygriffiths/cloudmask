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

import java.util.Set;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import uk.ac.rdg.resc.cloudmask.MaskedDatasetFactory.MaskedDataset;
import uk.ac.rdg.resc.edal.dataset.cdm.NativeCdmGridDatasetFactory;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.graphics.style.util.SimpleFeatureCatalogue;
import uk.ac.rdg.resc.edal.metadata.VariableMetadata;

public class CloudMask extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle("Cloud Masker");

        GridPane grid = new GridPane();

        MaskedDatasetFactory mdf = new MaskedDatasetFactory(new NativeCdmGridDatasetFactory());
        MaskedDataset dataset = mdf.createDataset("test", "/home/guy/test_file.nc");
        Set<String> variableIds = dataset.getVariableIds();
        VariableMetadata comparison = null;
        for (String var : variableIds) {
            VariableMetadata metadata = dataset.getVariableMetadata(var);
            if (comparison == null) {
                comparison = metadata;
            } else {
                if (!comparison.getHorizontalDomain().equals(metadata.getHorizontalDomain())) {
                    throw new EdalException(
                            "Currently all variables in the dataset must share the same horizontal domain");
                }
            }
        }

        SimpleFeatureCatalogue<MaskedDataset> catalogue = new SimpleFeatureCatalogue<>(dataset,
                true);

        CompositeMaskPane comp = new CompositeMaskPane(catalogue, 512, 512);

        grid.add(new MaskingPane(catalogue, 512, 512, comp), 0, 0);
        grid.add(comp, 0, 1);
//        
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

        primaryStage.setScene(new Scene(grid, 300, 250));
        primaryStage.show();
    }
}
