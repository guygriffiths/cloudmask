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

import java.awt.image.BufferedImage;
import java.io.IOException;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

import org.controlsfx.control.RangeSlider;

import uk.ac.rdg.resc.cloudmask.CloudMaskDatasetFactory.MaskedDataset;
import uk.ac.rdg.resc.cloudmask.ZoomableImageView.ImageGenerator;
import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.exceptions.VariableNotFoundException;
import uk.ac.rdg.resc.edal.graphics.style.util.SimpleFeatureCatalogue;
import uk.ac.rdg.resc.edal.util.Extents;

public class MaskingPane extends BorderPane {
    private LinkedZoomableImageView imageView = null;
    private ListView<String> variables;
    private VBox bottomPanel;
    private HBox rightPanel;
    private RangeSlider maskRangeSlider;
    private ColourbarSlider colourbarSlider;
    private CheckBox exclusiveThreshold;
    private EdalImageGenerator imageGenerator = null;

    private String currentVar = null;
    private int imageWidth;
    private int imageHeight;
    private final CompositeMaskPane compositePane;

    public MaskingPane(int imageWidth, int imageHeight, CompositeMaskPane compositePane)
            throws EdalException, IOException {
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        
        variables = new ListView<>();
        
        bottomPanel = new VBox();
        rightPanel = new HBox();
        
        maskRangeSlider = new RangeSlider();
        maskRangeSlider.setShowTickMarks(true);
        maskRangeSlider.setShowTickLabels(true);
        
        colourbarSlider = new ColourbarSlider();
        colourbarSlider.setOrientation(Orientation.VERTICAL);
        
        exclusiveThreshold = new CheckBox("Mask outside threshold");
        
        bottomPanel.getChildren().add(exclusiveThreshold);
        bottomPanel.getChildren().add(maskRangeSlider);
        bottomPanel.getChildren().add(colourbarSlider);
        
        rightPanel.getChildren().add(colourbarSlider);
        rightPanel.getChildren().add(variables);

        if(compositePane == null) {
            throw new IllegalArgumentException("You must provide a non-null composite pane");
        }
        this.compositePane = compositePane;
        SimpleFeatureCatalogue<MaskedDataset> catalogue = compositePane.getCatalogue();
        setCatalogue(catalogue);
        setRight(rightPanel);
        setBottom(bottomPanel);
        
        compositePane.addMaskingPane(this);
    }

    void setCatalogue(SimpleFeatureCatalogue<MaskedDataset> catalogue) throws EdalException,
            IOException {
        if(catalogue != null) {
            MaskedDataset dataset = catalogue.getDataset();
    
    
            ObservableList<String> variableNames = catalogue.getDataset().getUnmaskedVariableNames();
            if (variableNames.size() == 0) {
                throw new EdalException("No variables are present.");
            }
    
            imageGenerator = new MaskedEdalImageGenerator(variableNames.get(0), catalogue);
            imageView = new LinkedZoomableImageView(imageWidth, imageHeight, imageGenerator);
    
            variables.setItems(variableNames);
            variables.getSelectionModel().selectedItemProperty()
                    .addListener(new ChangeListener<String>() {
                        @Override
                        public void changed(ObservableValue<? extends String> observable,
                                String oldVar, String newVar) {
                            try {
                                currentVar = newVar;
                                imageGenerator.setVariable(currentVar);
                                Extent<Double> maskRange = dataset.getMaskThreshold(newVar);
                                Extent<Float> scaleRange = imageGenerator.getScaleRange();
                                maskRangeSlider.setMin(scaleRange.getLow());
                                maskRangeSlider.setLowValue(maskRange.getLow());
                                maskRangeSlider.setMax(scaleRange.getHigh());
                                maskRangeSlider.setHighValue(maskRange.getHigh());
                                
                                
                                
                            } catch (EdalException e) {
                                e.printStackTrace();
                            }
                        }
                    });
            variables.getSelectionModel().select(0);
    
            compositePane.imageView.addLinkedView(imageView);
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
    
            maskRangeSlider.lowValueProperty().addListener(new ChangeListener<Number>() {
                @Override
                public void changed(ObservableValue<? extends Number> observer, Number oldVal,
                        Number newVal) {
                    dataset.setMaskMinThreshold(currentVar, newVal.floatValue());
                    imageView.updateImage();
                }
            });
            maskRangeSlider.highValueProperty().addListener(new ChangeListener<Number>() {
                @Override
                public void changed(ObservableValue<? extends Number> observer, Number oldVal,
                        Number newVal) {
                    dataset.setMaskMaxThreshold(currentVar, newVal.floatValue());
                    imageView.updateImage();
                }
            });
            
            colourbarSlider.setImageGenerator(imageGenerator);
            colourbarSlider.lowValueProperty().addListener(new ChangeListener<Number>() {
                @Override
                public void changed(ObservableValue<? extends Number> observer, Number oldVal,
                        Number newVal) {
                    Extent<Float> scaleRange = imageGenerator.getScaleRange();
                    Extent<Float> newScaleRange = Extents.newExtent(newVal.floatValue(), scaleRange.getHigh());
                    imageGenerator.setScaleRange(newScaleRange);
                    catalogue.getDataset().setCurrentScaleRange(currentVar, newScaleRange);
                    imageView.updateImage();
                }
            });
            colourbarSlider.highValueProperty().addListener(new ChangeListener<Number>() {
                @Override
                public void changed(ObservableValue<? extends Number> observer, Number oldVal,
                        Number newVal) {
                    Extent<Float> scaleRange = imageGenerator.getScaleRange();
                    imageGenerator.setScaleRange(Extents.newExtent(scaleRange.getLow(), newVal.floatValue()));
                    imageView.updateImage();
                }
            });
            
            exclusiveThreshold.selectedProperty().addListener(new ChangeListener<Boolean>() {
                @Override
                public void changed(ObservableValue<? extends Boolean> observer, Boolean oldVal,
                        Boolean newVal) {
                    dataset.setMaskThresholdInclusive(currentVar, !newVal);
                    imageView.updateImage();
                }
            });
        } else {
            imageView = new LinkedZoomableImageView(imageWidth, imageHeight, new ImageGenerator() {
                @Override
                public double getMinValidY() {
                    return 0;
                }
                
                @Override
                public double getMinValidX() {
                    return 0;
                }
                
                @Override
                public double getMaxValidY() {
                    return imageWidth;
                }
                
                @Override
                public double getMaxValidX() {
                    return imageHeight;
                }
                
                @Override
                public BufferedImage generateImage(double minX, double minY, double maxX, double maxY,
                        int width, int height) {
                    return new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
                }
            });
        }

        setCenter(imageView);
    }
}
