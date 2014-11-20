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

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.layout.BorderPane;

import org.controlsfx.control.CheckListView;

import uk.ac.rdg.resc.cloudmask.MaskedDatasetFactory.MaskedDataset;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.graphics.style.util.SimpleFeatureCatalogue;

public class CompositeMaskPane extends BorderPane {
    LinkedZoomableImageView imageView;
    private CheckListView<String> variables;
    private CompositeMaskEdalImageGenerator imageGenerator;

    private String[] mask = null;

    public CompositeMaskPane(SimpleFeatureCatalogue<MaskedDataset> catalogue, int imageWidth,
            int imageHeight) throws IOException, EdalException {

        MaskedDataset dataset = catalogue.getDataset();

        variables = new CheckListView<>();
        ObservableList<String> variableNames = catalogue.getDataset().getUnmaskedVariableNames();
        if (variableNames.size() == 0) {
            throw new EdalException("No variables are present.");
        }

        imageGenerator = new CompositeMaskEdalImageGenerator(variableNames.get(0), catalogue);
        imageView = new LinkedZoomableImageView(imageWidth, imageHeight, imageGenerator);

        variables.setItems(catalogue.getDataset().getUnmaskedVariableNames());
        variables.getSelectionModel().selectedItemProperty()
                .addListener(new ChangeListener<String>() {
                    @Override
                    public void changed(ObservableValue<? extends String> observable,
                            String oldVar, String newVar) {
                        if (mask == null) {
                            mask = new String[] { newVar };
                        }
                        try {
                            imageGenerator.setVariable(newVar);
                            imageView.updateImage();
                        } catch (EdalException e) {
                            e.printStackTrace();
                        }
                    }
                });
        variables.getSelectionModel().select(0);
        variables.getCheckModel().getCheckedItems().addListener(new ListChangeListener<String>() {
            @Override
            public void onChanged(javafx.collections.ListChangeListener.Change<? extends String> c) {
                mask = new String[variables.getCheckModel().getCheckedItems().size()];
                for (int i = 0; i < variables.getCheckModel().getCheckedItems().size(); i++) {
                    mask[i] = variables.getCheckModel().getCheckedItems().get(i) + "-"
                            + MaskedDatasetFactory.MASK_SUFFIX;
                }
                catalogue.expireFromCache(CompositeMaskPlugin.COMPOSITEMASK);
                dataset.setMaskedVariables(mask);
                imageView.updateImage();
            }
        });

        setCenter(imageView);
        setRight(variables);

        catalogue.getDataset().getUnmaskedVariableNames();
    }
}
