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
import java.util.Optional;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.util.Callback;

import org.controlsfx.control.RangeSlider;

import uk.ac.rdg.resc.cloudmask.widgets.ColourbarSlider;
import uk.ac.rdg.resc.cloudmask.widgets.LinkedZoomableImageView;
import uk.ac.rdg.resc.cloudmask.widgets.PaletteSelector;
import uk.ac.rdg.resc.cloudmask.widgets.ZoomableImageView.ImageGenerator;
import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.exceptions.VariableNotFoundException;
import uk.ac.rdg.resc.edal.graphics.style.util.GraphicsUtils;

public class MaskedVariableView extends HBox {

    private String currentVariable = null;
    private CompositeMaskView compositeMaskView;
    private LinkedZoomableImageView imageView;

    private final CloudMaskController controller;
    private final int imageWidth;
    private final int imageHeight;

    private VBox titleMapMask;
    private HBox colourbarSettings;

    private ListView<String> variables;
    private Label varLabel;

    private RangeSlider maskRangeSlider;
    private ColourbarSlider colourbarSlider;
    private CheckBox exclusiveThreshold;
    private CheckBox includedInMask;
    private Button selectPalette;
    private Button resetView;

    /*
     * Used to prevent new variable selection events from being triggered when
     * the variable list changes
     */
    private boolean disabledCallbacks = false;
    private VBox settings;

    public MaskedVariableView(int width, int height, CloudMaskController controller) {
        if (controller == null) {
            throw new IllegalArgumentException("CloudMaskController cannot be null");
        }
        controller.addMaskedVariableView(this);

        this.imageWidth = width;
        this.imageHeight = height;

        this.controller = controller;
        compositeMaskView = controller.getCompositeMaskView();

        /*
         * Instantiate the sliders, tickbox, title label, etc. and add the
         * callbacks with the controller
         */
        variables = new ListView<>();
        variables.setItems(controller.getAvailableVariables());

        variables.setPrefWidth(10000);

        maskRangeSlider = new RangeSlider();
        maskRangeSlider.setShowTickMarks(true);
        maskRangeSlider.setShowTickLabels(true);
        maskRangeSlider.getStyleClass().add("mask-slider");

        colourbarSlider = new ColourbarSlider();
        colourbarSlider.setOrientation(Orientation.VERTICAL);

        exclusiveThreshold = new CheckBox("Mask outside threshold");
        includedInMask = new CheckBox("Included in composite");

        selectPalette = new Button("Choose colour palette");
//        selectPalette = new Button("", new ImageView(new Image(getClass().getResourceAsStream(
//                "/colours.png"))));

        resetView = new Button("Reset");

        addCallbacks();

        /*
         * Create a new image view with a dummy ImageGenerator
         */
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
                return 1;
            }

            @Override
            public double getMaxValidX() {
                return 1;
            }

            @Override
            public BufferedImage generateImage(double minX, double minY, double maxX, double maxY,
                    int width, int height) {
                return new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
            }
        });

        varLabel = new Label("No variable selected");

        titleMapMask = new VBox();
        colourbarSettings = new HBox();

        titleMapMask.getChildren().add(imageView);
        titleMapMask.getChildren().add(maskRangeSlider);

        VBox.setVgrow(imageView, Priority.ALWAYS);
        VBox.setVgrow(varLabel, Priority.NEVER);
        VBox.setVgrow(maskRangeSlider, Priority.NEVER);

        colourbarSettings.getChildren().add(colourbarSlider);
        settings = new VBox();
        settings.getChildren().add(varLabel);
        settings.getChildren().add(variables);

//        HBox exclusivePalette = new HBox();
        VBox exclusivePalette = new VBox();
        exclusivePalette.getChildren().add(exclusiveThreshold);
        exclusivePalette.getChildren().add(includedInMask);
        exclusivePalette.getChildren().add(selectPalette);
        exclusivePalette.getChildren().add(resetView);

        settings.getChildren().add(exclusivePalette);
        VBox.setVgrow(settings, Priority.NEVER);
        colourbarSettings.getChildren().add(settings);

        getChildren().add(titleMapMask);
        getChildren().add(colourbarSettings);

        HBox.setHgrow(titleMapMask, Priority.NEVER);
        HBox.setHgrow(colourbarSettings, Priority.ALWAYS);
    }

    private void addCallbacks() {
        variables.getSelectionModel().selectedItemProperty()
                .addListener(new ChangeListener<String>() {
                    @Override
                    public void changed(ObservableValue<? extends String> observable,
                            String oldVar, String newVar) {
                        controller.setVariable(MaskedVariableView.this, newVar);
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
                            setText(item
                                    + " ("
                                    + controller.getDataset().getVariableMetadata(item)
                                            .getParameter().getDescription() + ")");
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
                if (!disabledCallbacks) {
                    controller.maskThresholdChanged(currentVariable, newVal.floatValue(),
                            (float) maskRangeSlider.getHighValue());
                }
            }
        });
        maskRangeSlider.highValueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observer, Number oldVal,
                    Number newVal) {
                if (!disabledCallbacks) {
                    controller.maskThresholdChanged(currentVariable,
                            (float) maskRangeSlider.getLowValue(), newVal.floatValue());
                }
            }
        });

        colourbarSlider.lowValueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observer, Number oldVal,
                    Number newVal) {
                if (!disabledCallbacks) {
                    controller.colourScaleChanged(currentVariable, newVal.floatValue(),
                            (float) colourbarSlider.getHighValue());
                }
            }
        });
        colourbarSlider.highValueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observer, Number oldVal,
                    Number newVal) {
                if (!disabledCallbacks) {
                    controller.colourScaleChanged(currentVariable,
                            (float) colourbarSlider.getLowValue(), newVal.floatValue());
                }
            }
        });
        colourbarSlider.highValueChangingProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> arg0, Boolean wasChanging, Boolean changing) {
                /*
                 * TODO add lots of these to push events to the undo stack.
                 */
                if(!changing) {
                    System.out.println("High value changed");
                }
            }
        });

        exclusiveThreshold.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> observer, Boolean oldVal,
                    Boolean newVal) {
                if (!disabledCallbacks) {
                    controller.setMaskThresholdInclusive(currentVariable, !newVal);
                }
            }
        });
        
        includedInMask.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> observer, Boolean oldVal,
                    Boolean newVal) {
                if (!disabledCallbacks) {
                    controller.setVariableMasked(currentVariable, newVal);
                }
            }
        });

        selectPalette.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                PaletteSelector ps = new PaletteSelector();
                Optional<String> result = ps.showAndWait();
                if (result.isPresent()) {
                    controller.paletteChanged(currentVariable, result.get());
                }
            }
        });

        resetView.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                Extent<Float> maxScaleRange = GraphicsUtils.estimateValueRange(
                        controller.getDataset(), currentVariable);
                colourbarSlider.setLowValue(maxScaleRange.getLow());
                colourbarSlider.setHighValue(maxScaleRange.getHigh());
                maskRangeSlider.setLowValue(maxScaleRange.getLow());
                maskRangeSlider.setHighValue(maxScaleRange.getHigh());
                exclusiveThreshold.setSelected(false);
            }
        });
    }

    public String getCurrentVariable() {
        return currentVariable;
    }

    public void newModelSelected(EdalImageGenerator imageGenerator) {
        /*
         * Remove the title and the image view
         */
        settings.getChildren().remove(varLabel);
        titleMapMask.getChildren().remove(imageView);

        currentVariable = imageGenerator.getVariable();

        /*
         * Set all scale slider values
         */
        Extent<Float> maxScaleRange = GraphicsUtils.estimateValueRange(controller.getDataset(),
                currentVariable);

        /*
         * Stop the value changes triggering callbacks to the controller
         */
        disabledCallbacks = true;
        colourbarSlider.setMax(maxScaleRange.getHigh());
        colourbarSlider.setMin(maxScaleRange.getLow());

        Extent<Float> scaleRange = imageGenerator.getScaleRange();
        /*
         * Set low, then high, then low.
         * 
         * The first setLow may fail if it's higher than the current high value.
         * We could do a test to see which order to apply the high/low setting
         * in, but this works equally well
         */
        colourbarSlider.setLowValue(scaleRange.getLow());
        colourbarSlider.setHighValue(scaleRange.getHigh());
        colourbarSlider.setLowValue(scaleRange.getLow());

        colourbarSlider.setImageGenerator(imageGenerator);

        maskRangeSlider.setMin(scaleRange.getLow());
        maskRangeSlider.setMax(scaleRange.getHigh());

        Extent<Double> maskThreshold = controller.getDataset().getMaskThreshold(currentVariable);
        /*
         * Similarly to above
         */
        maskRangeSlider.adjustLowValue(maskThreshold.getLow());
        maskRangeSlider.adjustHighValue(maskThreshold.getHigh());
        maskRangeSlider.adjustLowValue(maskThreshold.getLow());

        exclusiveThreshold.setSelected(!controller.getDataset().isMaskThresholdInclusive(
                currentVariable));
        includedInMask.setSelected(controller.isVariableInComposite(currentVariable));

        disabledCallbacks = false;

        /*
         * Delink the old view
         * 
         * Create a new view
         * 
         * Link the new view with the the composite view
         */
        compositeMaskView.unlinkView(imageView);
        imageView = new LinkedZoomableImageView(imageWidth, imageHeight, imageGenerator);
        compositeMaskView.linkView(imageView);

        /*
         * Set the title
         */
        varLabel = new Label(currentVariable);
        varLabel.setFont(new Font(24));

        settings.getChildren().add(0, varLabel);
        titleMapMask.getChildren().add(0, imageView);
    }

    public void setMaskSliderLimits(Extent<Float> scaleRange) {
        /*
         * Change the limits on the mask slider (but not the values? - do these
         * change automatically?)
         */
        maskRangeSlider.setMin(scaleRange.getLow());
        maskRangeSlider.setMax(scaleRange.getHigh());
    }

    public void redrawImage() {
        /*
         * Update the view area
         */
        imageView.updateImage();
        colourbarSlider.updateValue();
    }

    public void setIncludedInMask(boolean included) {
        disabledCallbacks = true;
        includedInMask.setSelected(included);
        disabledCallbacks = false;
    }
}