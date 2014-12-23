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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
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
import uk.ac.rdg.resc.edal.metadata.VariableMetadata;

public class MaskedVariableView extends HBox {
    public static final int WIDGET_SPACING = 15;
    
    private String currentVariable = null;
    private CompositeMaskView compositeMaskView;
    private LinkedZoomableImageView imageView;

    private final CloudMaskController controller;
    private final int imageWidth;
    private final int imageHeight;
    private final double scale;

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

    private Button undoButton;
    private Button redoButton;

    /*
     * Used to prevent new variable selection events from being triggered when
     * the variable list changes
     */
    private boolean disabledCallbacks = false;
    private VBox settings;

    public MaskedVariableView(int width, int height, double scale, CloudMaskController controller) {
        if (controller == null) {
            throw new IllegalArgumentException("CloudMaskController cannot be null");
        }
        controller.addMaskedVariableView(this);

        this.imageWidth = width;
        this.imageHeight = height;
        this.scale = scale;

        this.controller = controller;
        compositeMaskView = controller.getCompositeMaskView();

        /*
         * Instantiate the sliders, tickbox, title label, etc. and add the
         * callbacks with the controller
         */
        variables = new ListView<>();
        variables.setItems(controller.getMaskableVariables());

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

        undoButton = new Button("Undo");
        redoButton = new Button("Redo");

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
        imageView.setFitHeight(imageHeight * scale);
        imageView.setFitWidth(imageWidth * scale);
        
        addCallbacks();

        varLabel = new Label("No variable selected");
        varLabel.setFont(new Font(24));

        titleMapMask = new VBox();
        colourbarSettings = new HBox();

        titleMapMask.getChildren().add(imageView);
        titleMapMask.getChildren().add(maskRangeSlider);

        VBox.setVgrow(imageView, Priority.ALWAYS);
        VBox.setVgrow(varLabel, Priority.NEVER);
        VBox.setVgrow(maskRangeSlider, Priority.NEVER);

        colourbarSettings.getChildren().add(colourbarSlider);
        settings = new VBox(WIDGET_SPACING);
        settings.getChildren().add(varLabel);
        settings.getChildren().add(variables);

        VBox exclusivePalette = new VBox(WIDGET_SPACING);
        exclusivePalette.getChildren().add(exclusiveThreshold);
        exclusivePalette.getChildren().add(includedInMask);
        exclusivePalette.getChildren().add(selectPalette);
        HBox historyButtons = new HBox(WIDGET_SPACING);
        historyButtons.getChildren().add(undoButton);
        historyButtons.getChildren().add(redoButton);
        historyButtons.getChildren().add(resetView);
        exclusivePalette.getChildren().add(historyButtons);

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
        maskRangeSlider.lowValueChangingProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> arg0, Boolean wasChanging,
                    Boolean changing) {
                if (!changing) {
                    /*
                     * Finished changing. Add new state to the undo stack
                     */
                    controller.addUndoState(currentVariable);
                }
            }
        });
        maskRangeSlider.highValueChangingProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> arg0, Boolean wasChanging,
                    Boolean changing) {
                if (!changing) {
                    /*
                     * Finished changing. Add new state to the undo stack
                     */
                    controller.addUndoState(currentVariable);
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
        colourbarSlider.lowValueChangingProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> arg0, Boolean wasChanging,
                    Boolean changing) {
                if (!changing) {
                    /*
                     * Finished changing. Add new state to the undo stack
                     */
                    controller.addUndoState(currentVariable);
                }
            }
        });
        colourbarSlider.highValueChangingProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> arg0, Boolean wasChanging,
                    Boolean changing) {
                if (!changing) {
                    /*
                     * Finished changing. Add new state to the undo stack
                     */
                    controller.addUndoState(currentVariable);
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
                controller.addUndoState(currentVariable);
            }
        });

        undoButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                controller.undoLastAction(currentVariable);
            }
        });

        redoButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                controller.redoLastAction(currentVariable);
            }
        });
    }

    public String getCurrentVariable() {
        return currentVariable;
    }

    public void newModelSelected(EdalImageGenerator imageGenerator) {
        /*
         * Remove the image view
         */
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
        imageView.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                System.out.println("mouse click");
                if (event.getButton() == MouseButton.SECONDARY) {
                    System.out.println("right button");
                    try {
                        Alert info = new Alert(AlertType.INFORMATION);
                        info.setTitle(currentVariable);
                        info.setHeaderText("Information about variable " + currentVariable);
                        VariableMetadata variableMetadata = controller.getDataset()
                                .getVariableMetadata(currentVariable);
                        StringBuilder infoText = new StringBuilder();
                        infoText.append("Title: " + variableMetadata.getParameter().getTitle()
                                + "\n");
                        infoText.append("Description: "
                                + variableMetadata.getParameter().getDescription() + "\n");
                        infoText.append("Units: " + variableMetadata.getParameter().getUnits()
                                + "\n");
                        Map<String, Object> variableProperties = variableMetadata
                                .getVariableProperties();
                        if (variableProperties.size() > 0) {
                            infoText.append("Properties: \n");
                            for (Entry<String, Object> prop : variableProperties.entrySet()) {
                                infoText.append("\t"+prop.getKey() + " : " + prop.getValue()+"\n");
                            }
                        }
                        infoText.append("Colour scale range: " + colourbarSlider.getLowValue()
                                + " -> " + colourbarSlider.getHighValue() + "\n");
                        if (exclusiveThreshold.selectedProperty().get()) {
                            infoText.append("Masking below: " + maskRangeSlider.getLowValue()
                                    + " and above " + maskRangeSlider.getHighValue() + "\n");
                        } else {
                            infoText.append("Masking between: " + maskRangeSlider.getLowValue()
                                    + " and " + maskRangeSlider.getHighValue() + "\n");
                        }
                        if(includedInMask.selectedProperty().get()) {
                            infoText.append("Included in mask");
                        }

                        info.setContentText(infoText.toString());
                        info.showAndWait();
                    } catch (VariableNotFoundException e) {
                        e.printStackTrace();
                    }

                }
            }
        });
        imageView.setFitHeight(imageHeight * scale);
        imageView.setFitWidth(imageWidth * scale);
        compositeMaskView.linkView(imageView);

        /*
         * Set the title
         */
        varLabel.textProperty().set(currentVariable);

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

    public void changeSliderValues(Extent<Float> colourScaleValues,
            Extent<Double> maskSliderValues, boolean disableCallbacks) {
        disabledCallbacks = disableCallbacks;
        colourbarSlider.setLowValue(colourScaleValues.getLow());
        colourbarSlider.setHighValue(colourScaleValues.getHigh());
        maskRangeSlider.setLowValue(maskSliderValues.getLow());
        maskRangeSlider.setHighValue(maskSliderValues.getHigh());
        disabledCallbacks = false;
    }

    public void redrawImage() {
        /*
         * Update the view area
         */
        imageView.updateJustThisImage();
        colourbarSlider.updateValue();
    }

    public void setIncludedInMask(boolean included) {
        disabledCallbacks = true;
        includedInMask.setSelected(included);
        disabledCallbacks = false;
    }
}
