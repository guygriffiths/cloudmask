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
import java.util.Optional;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Slider;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.util.Callback;
import uk.ac.rdg.resc.cloudmask.CloudMaskController.MaskVariable;
import uk.ac.rdg.resc.cloudmask.CloudMaskDatasetFactory.MaskedDataset;
import uk.ac.rdg.resc.cloudmask.widgets.ColourbarSlider;
import uk.ac.rdg.resc.cloudmask.widgets.LinkedZoomableImageView;
import uk.ac.rdg.resc.cloudmask.widgets.PaletteSelector;
import uk.ac.rdg.resc.cloudmask.widgets.ZoomableImageView.ImageGenerator;
import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.graphics.utils.SimpleFeatureCatalogue;
import uk.ac.rdg.resc.edal.grid.HorizontalGrid;
import uk.ac.rdg.resc.edal.position.HorizontalPosition;
import uk.ac.rdg.resc.edal.util.Extents;
import uk.ac.rdg.resc.edal.util.GridCoordinates2D;

public class CompositeMaskView extends HBox {
    LinkedZoomableImageView imageView = null;
    CompositeMaskEdalImageGenerator imageGenerator = null;
    private ColourbarSlider colourbarSlider;
    private boolean disableCallbacks = false;
    private TableView<MaskVariable> variables;
    private Button selectPalette;

    private TitledPane pixelType;
    private Slider manualSetRadius;

    private final int imageWidth;
    private final int imageHeight;
    private final double scale;
    private CloudMaskController controller;

    private Integer manualMaskValue = MaskedDataset.MANUAL_CLOUDY;
    private Label varLabel;
    private CheckBox showSetPixels;

    public CompositeMaskView(int imageWidth, int imageHeight, double scale,
            CloudMaskController cloudMaskController) {
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.scale = scale;
        this.controller = cloudMaskController;

        varLabel = new Label("No variable selected");
        varLabel.setFont(new Font(24));

        variables = new TableView<>();
        variables.setEditable(true);
        variables.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        variables.getSelectionModel().selectedItemProperty()
                .addListener(new ChangeListener<MaskVariable>() {
                    @Override
                    public void changed(ObservableValue<? extends MaskVariable> observable,
                            MaskVariable oldVal, MaskVariable newVal) {
                        if (newVal == null) {
                            return;
                        }
                        try {
                            imageGenerator.setVariable(newVal.variableName.getValue());
                            Extent<Float> scaleRange = imageGenerator.getScaleRange();
                            if (imageGenerator.isRgb()) {
                                colourbarSlider.setDisable(true);
                                getChildren().remove(colourbarSlider);
                            } else {
                                colourbarSlider.setDisable(false);
                                if (!getChildren().contains(colourbarSlider)) {
                                    getChildren().add(1, colourbarSlider);
                                }
                                /*
                                 * Set the range on the colourbar slider.
                                 */
                                disableCallbacks = true;
                                colourbarSlider.setMin(scaleRange.getLow());
                                colourbarSlider.setMax(scaleRange.getHigh());
                                colourbarSlider.setLowValue(scaleRange.getLow());
                                colourbarSlider.setHighValue(scaleRange.getHigh());
                                colourbarSlider.setLowValue(scaleRange.getLow());
                                disableCallbacks = false;
                            }
                            imageView.updateJustThisImage();
                            varLabel.textProperty().set(newVal.variableName.getValue());
                        } catch (EdalException e) {
                            e.printStackTrace();
                        }
                    }
                });

        TableColumn<MaskVariable, Boolean> includedCol = new TableColumn<>("");
        TableColumn<MaskVariable, String> nameCol = new TableColumn<>("Name");
        TableColumn<MaskVariable, String> valCol = new TableColumn<>("Value");

        nameCol.prefWidthProperty().bind(variables.widthProperty().subtract(140));
        includedCol.setPrefWidth(28);
        valCol.setPrefWidth(100);

        nameCol.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<MaskVariable, String>, ObservableValue<String>>() {
            @Override
            public ObservableValue<String> call(CellDataFeatures<MaskVariable, String> var) {
                return var.getValue().variableName;
            }
        });
        nameCol.setEditable(false);

        includedCol
                .setCellValueFactory(new Callback<TableColumn.CellDataFeatures<MaskVariable, Boolean>, ObservableValue<Boolean>>() {
                    @Override
                    public ObservableValue<Boolean> call(CellDataFeatures<MaskVariable, Boolean> var) {
                        return var.getValue().includedInComposite;
                    }
                });
        includedCol.setCellFactory(CheckBoxTableCell.forTableColumn(includedCol));
        includedCol.setEditable(true);

        valCol.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<MaskVariable, String>, ObservableValue<String>>() {
            @Override
            public ObservableValue<String> call(CellDataFeatures<MaskVariable, String> var) {
                return var.getValue().selectedValue;
            }
        });
        valCol.setEditable(false);
        valCol.setSortable(false);

        variables.getColumns().add(includedCol);
        variables.getColumns().add(nameCol);
        variables.getColumns().add(valCol);

        variables.setItems(controller.getPlottableVariables());

        colourbarSlider = new ColourbarSlider();
        colourbarSlider.setOrientation(Orientation.VERTICAL);
        colourbarSlider.lowValueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observer, Number oldVal,
                    Number newVal) {
                if (!disableCallbacks && newVal.floatValue() < colourbarSlider.getHighValue()) {
                    imageGenerator.setScaleRange(Extents.newExtent(newVal.floatValue(),
                            (float) colourbarSlider.getHighValue()));
                    imageView.updateJustThisImage();
                }
            }
        });
        colourbarSlider.highValueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observer, Number oldVal,
                    Number newVal) {
                if (!disableCallbacks && newVal.floatValue() > colourbarSlider.getLowValue()) {
                    imageGenerator.setScaleRange(Extents.newExtent(
                            (float) colourbarSlider.getLowValue(), newVal.floatValue()));
                    imageView.updateJustThisImage();
                }
            }
        });

        pixelType = new TitledPane();
        pixelType.setText("Manual masking");
        VBox types = new VBox(MaskedVariableView.WIDGET_SPACING);
        ToggleGroup group = new ToggleGroup();
        RadioButton unset = new RadioButton("Unset");
        unset.setToggleGroup(group);
        unset.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                manualMaskValue = null;
            }
        });
        types.getChildren().add(unset);
        RadioButton clear = new RadioButton("Clear");
        clear.setToggleGroup(group);
        clear.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                manualMaskValue = MaskedDataset.MANUAL_CLEAR;
            }
        });
        types.getChildren().add(clear);
        RadioButton probClear = new RadioButton("Probably clear");
        probClear.setToggleGroup(group);
        probClear.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                manualMaskValue = MaskedDataset.MANUAL_PROBABLY_CLEAR;
            }
        });
        types.getChildren().add(probClear);
        RadioButton probCloudy = new RadioButton("Probably cloudy");
        probCloudy.setToggleGroup(group);
        probCloudy.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                manualMaskValue = MaskedDataset.MANUAL_PROBABLY_CLOUDY;
            }
        });
        types.getChildren().add(probCloudy);
        RadioButton cloudy = new RadioButton("Cloudy");
        cloudy.setToggleGroup(group);
        cloudy.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                manualMaskValue = MaskedDataset.MANUAL_CLOUDY;
            }
        });
        cloudy.setSelected(true);
        types.getChildren().add(cloudy);
        RadioButton dusty = new RadioButton("Dust");
        dusty.setToggleGroup(group);
        dusty.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                manualMaskValue = MaskedDataset.MANUAL_DUST;
            }
        });
        types.getChildren().add(dusty);
        RadioButton smoke = new RadioButton("Smoke");
        smoke.setToggleGroup(group);
        smoke.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                manualMaskValue = MaskedDataset.MANUAL_SMOKE;
            }
        });
        types.getChildren().add(smoke);

        showSetPixels = new CheckBox("Highlight manually set pixels");
        showSetPixels.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldVal,
                    Boolean newVal) {
                imageGenerator.showMaskedPixels(newVal);
                imageView.updateJustThisImage();
            }
        });
        types.getChildren().add(showSetPixels);

        manualSetRadius = new Slider(1.0, 50.0, 1.0);
        manualSetRadius.setShowTickLabels(true);
        manualSetRadius.setShowTickMarks(true);
        manualSetRadius.setMajorTickUnit(7.0);
        manualSetRadius.setSnapToTicks(true);
        types.getChildren().add(new Label("Manual mask size:"));
        types.getChildren().add(manualSetRadius);

        pixelType.setContent(types);

        selectPalette = new Button("Choose colour palette");
        selectPalette.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                PaletteSelector ps = new PaletteSelector();
                Optional<String> result = ps.showAndWait();
                if (result.isPresent()) {
                    imageGenerator.setPalette(result.get());
                    imageView.updateJustThisImage();
                }
            }
        });

        HBox undoRedo = new HBox(MaskedVariableView.WIDGET_SPACING);
        Button undo = new Button("Undo");
        undo.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                controller.undoLastManualEdit();
            }
        });
        Button redo = new Button("Redo");
        redo.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                controller.redoLastManualEdit();
            }
        });
        undoRedo.getChildren().add(undo);
        undoRedo.getChildren().add(redo);

        VBox widgets = new VBox(MaskedVariableView.WIDGET_SPACING);
        widgets.getChildren().add(varLabel);

        widgets.getChildren().add(variables);
        widgets.getChildren().add(pixelType);
        widgets.getChildren().add(selectPalette);
        widgets.getChildren().add(undoRedo);
        VBox.setVgrow(variables, Priority.ALWAYS);

        try {
            /*
             * This sets the catalogue to null which also adds the ImageView to
             * this panel
             */
            setCatalogue(null);
        } catch (EdalException | IOException e) {
            e.printStackTrace();
        }
        getChildren().add(colourbarSlider);
        getChildren().add(widgets);
        HBox.setHgrow(colourbarSlider, null);
        HBox.setHgrow(widgets, Priority.ALWAYS);
    }

    public void linkView(LinkedZoomableImageView imageView) {
        this.imageView.addLinkedView(imageView);
    }

    public void unlinkView(LinkedZoomableImageView imageView) {
        this.imageView.removeLinkedView(imageView);
    }

    public void setCatalogue(SimpleFeatureCatalogue<MaskedDataset> catalogue) throws EdalException,
            IOException {
        if (imageView != null) {
            getChildren().remove(imageView);
        }
        
        showSetPixels.setSelected(false);

        if (catalogue != null) {
            ObservableList<String> variableNames = catalogue.getDataset()
                    .getUnmaskedVariableNames();
            if (variableNames.size() == 0) {
                throw new EdalException("No variables are present.");
            }

            HorizontalGrid maskGrid = catalogue.getDataset()
                    .getVariableMetadata(MaskedDataset.MANUAL_MASK_NAME).getHorizontalDomain();

            imageGenerator = new CompositeMaskEdalImageGenerator(variableNames.get(0), catalogue);
            colourbarSlider.setImageGenerator(imageGenerator);
            Extent<Float> scaleRange = imageGenerator.getScaleRange();
            colourbarSlider.setMax(scaleRange.getHigh());
            colourbarSlider.setMin(scaleRange.getLow());
            /*
             * Set low, then high, then low.
             * 
             * The first setLow may fail if it's higher than the current high
             * value. We could do a test to see which order to apply the
             * high/low setting in, but this works equally well
             */
            colourbarSlider.setLowValue(scaleRange.getLow());
            colourbarSlider.setHighValue(scaleRange.getHigh());
            colourbarSlider.setLowValue(scaleRange.getLow());

            varLabel.textProperty().set(variableNames.get(0));
            imageView = new LinkedZoomableImageView(imageWidth, imageHeight, imageGenerator);
            imageView.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent event) {
                    if (event.getButton() == MouseButton.SECONDARY) {
                        HorizontalPosition coords = imageView.getCoordinateFromImagePosition(
                                event.getX(), event.getY());
                        GridCoordinates2D imageCoords = maskGrid.findIndexOf(coords);
                        controller.setManualMask(imageCoords, (int) manualSetRadius.getValue(),
                                manualMaskValue);
                    } else if (event.getButton() == MouseButton.MIDDLE) {
                        HorizontalPosition coords = imageView.getCoordinateFromImagePosition(
                                event.getX(), event.getY());
                        controller.setDataSelectedPosition(coords);
                    }
                }
            });
            imageView.addEventHandler(ScrollEvent.SCROLL, new EventHandler<ScrollEvent>() {
                @Override
                public void handle(ScrollEvent event) {
                    if (event.getTouchCount() == 1) {
                        HorizontalPosition coords = imageView.getCoordinateFromImagePosition(
                                event.getX(), event.getY());
                        GridCoordinates2D imageCoords = maskGrid.findIndexOf(coords);
                        controller.setManualMask(imageCoords, (int) manualSetRadius.getValue(),
                                manualMaskValue);
                    }
                }
            });

            imageView.addEventHandler(MouseEvent.MOUSE_DRAGGED, new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent event) {
                    if (event.isSecondaryButtonDown()) {
                        HorizontalPosition coords = imageView.getCoordinateFromImagePosition(
                                event.getX(), event.getY());
                        GridCoordinates2D imageCoords = maskGrid.findIndexOf(coords);
                        controller.setManualMask(imageCoords, (int) manualSetRadius.getValue(),
                                manualMaskValue);
                    }
                }
            });

            variables.getSelectionModel().select(0);
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
                    return imageHeight;
                }

                @Override
                public double getMaxValidX() {
                    return imageWidth;
                }

                @Override
                public BufferedImage generateImage(double minX, double minY, double maxX,
                        double maxY, int width, int height) {
                    return new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
                }
            });
            imageView.setViewport(new Rectangle2D(0, 0, imageWidth, imageHeight));
        }
        imageView.setFitHeight(imageHeight * scale);
        imageView.setFitWidth(imageWidth * scale);
        getChildren().add(0, imageView);
        HBox.setHgrow(imageView, null);
    }
}
