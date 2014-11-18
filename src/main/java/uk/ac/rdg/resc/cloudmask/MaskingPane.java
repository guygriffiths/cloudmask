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
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.util.Callback;

import org.controlsfx.control.RangeSlider;

import uk.ac.rdg.resc.edal.dataset.cdm.MaskedDatasetFactory.MaskedDataset;
import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.exceptions.VariableNotFoundException;
import uk.ac.rdg.resc.edal.graphics.style.util.FeatureCatalogue;
import uk.ac.rdg.resc.edal.graphics.style.util.SimpleFeatureCatalogue;

public class MaskingPane extends BorderPane {
//    private FeatureCatalogue catalogue;
    private LinkedZoomableImageView imageView;
    private ListView<String> variables;
    private RangeSlider maskRangeSlider;
    private EdalImageGenerator imageGenerator;

    public MaskingPane(SimpleFeatureCatalogue<MaskedDataset> catalogue, int imageWidth,
            int imageHeight) {
//        this.catalogue = catalogue;

        imageView = new LinkedZoomableImageView(imageWidth, imageHeight, null);
        variables = new ListView<>();
        MaskedDataset dataset = catalogue.getDataset();
        variables.setItems(catalogue.getDataset().getUnmaskedVariableNames());
        variables.getSelectionModel().selectedItemProperty()
                .addListener(new ChangeListener<String>() {
                    @Override
                    public void changed(ObservableValue<? extends String> observable,
                            String oldVar, String newVar) {
                        try {
                            imageGenerator = new EdalImageGenerator(newVar, catalogue);
                            imageView.setImageGenerator(imageGenerator);
                            Extent<Float> scaleRange = imageGenerator.getScaleRange();
                            maskRangeSlider.setMin(scaleRange.getLow());
                            maskRangeSlider.setLowValue(scaleRange.getLow());
                            maskRangeSlider.setMax(scaleRange.getHigh());
                            maskRangeSlider.setHighValue(scaleRange.getHigh());
                        } catch (IOException | EdalException e) {
                            e.printStackTrace();
                        }
                    }
                });
        variables.setCellFactory(new Callback<ListView<String>, ListCell<String>>() {
            @Override
            public ListCell<String> call(ListView<String> listView) {
                return new ListCell<String>() {
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        try {
                            setText(dataset.getVariableMetadata(item).getParameter().getTitle());
                        } catch (VariableNotFoundException e) {
                            setText(item);
                        }
                    }
                };
            }
        });

        this.maskRangeSlider = new RangeSlider();
        maskRangeSlider.lowValueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observer, Number oldVal,
                    Number newVal) {
                imageGenerator.setMinThreshold(newVal.floatValue());
                imageView.updateImage();
            }
        });
        maskRangeSlider.highValueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observer, Number oldVal,
                    Number newVal) {
                imageGenerator.setMaxThreshold(newVal.floatValue());
                imageView.updateImage();
            }
        });

        setCenter(imageView);
        setRight(variables);
        setBottom(maskRangeSlider);

        catalogue.getDataset().getUnmaskedVariableNames();
    }
}
