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
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import org.controlsfx.control.CheckListView;

import uk.ac.rdg.resc.cloudmask.CloudMaskDatasetFactory.MaskedDataset;
import uk.ac.rdg.resc.cloudmask.widgets.LinkedZoomableImageView;
import uk.ac.rdg.resc.cloudmask.widgets.PaletteSelector;
import uk.ac.rdg.resc.cloudmask.widgets.ZoomableImageView.ImageGenerator;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.graphics.style.util.SimpleFeatureCatalogue;
import uk.ac.rdg.resc.edal.grid.HorizontalGrid;
import uk.ac.rdg.resc.edal.position.HorizontalPosition;
import uk.ac.rdg.resc.edal.util.GridCoordinates2D;

public class CompositeMaskView extends HBox {
    LinkedZoomableImageView imageView = null;
    CompositeMaskEdalImageGenerator imageGenerator = null;
    private CheckListView<String> variables;
    private Button selectPalette;
    
    private TitledPane pixelType;

    private int imageWidth;
    private int imageHeight;
    private CloudMaskController controller;
    
    private Integer manualMaskValue = MaskedDataset.MANUAL_CLOUDY;
    private Label varLabel;

    public CompositeMaskView(int imageWidth, int imageHeight,
            CloudMaskController cloudMaskController) {
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.controller = cloudMaskController;

        varLabel = new Label("No variable selected");
        varLabel.setFont(new Font(24));
        
        variables = new CheckListView<>();
        variables.setItems(controller.getPlottableVariables());
        variables.setPrefWidth(10000);
        
        pixelType = new TitledPane();
        pixelType.setText("Manual masking");
        pixelType.setCollapsible(false);
        VBox types = new VBox();
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
        
        CheckBox showSetPixels = new CheckBox("Highlight manually set pixels");
        showSetPixels.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldVal, Boolean newVal) {
                imageGenerator.showMaskedPixels(newVal);
                imageView.updateJustThisImage();
            }
        });
        types.getChildren().add(showSetPixels);
        
        
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
        
        HBox undoRedo = new HBox();
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
        
        VBox widgets = new VBox();
        widgets.getChildren().add(varLabel);
        widgets.getChildren().add(variables);
        widgets.getChildren().add(pixelType);
        widgets.getChildren().add(selectPalette);
        widgets.getChildren().add(undoRedo);
        
        try {
            setCatalogue(null);
        } catch (EdalException | IOException e) {
            e.printStackTrace();
        }
        getChildren().add(widgets);
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

        if (catalogue != null) {
            ObservableList<String> variableNames = catalogue.getDataset()
                    .getUnmaskedVariableNames();
            if (variableNames.size() == 0) {
                throw new EdalException("No variables are present.");
            }

            HorizontalGrid maskGrid = (HorizontalGrid) catalogue.getDataset()
                    .getVariableMetadata(MaskedDataset.MANUAL_MASK_NAME).getHorizontalDomain();

            imageGenerator = new CompositeMaskEdalImageGenerator(variableNames.get(0), catalogue);
            varLabel.textProperty().set(variableNames.get(0));
            imageView = new LinkedZoomableImageView(imageWidth, imageHeight, imageGenerator);
            imageView.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent event) {
                    if (event.getButton() == MouseButton.SECONDARY) {
                        HorizontalPosition coords = imageView.getCoordinateFromImagePosition(
                                event.getX(), event.getY());
                        GridCoordinates2D imageCoords = maskGrid.findIndexOf(coords);
                        controller.setPixelOn(imageCoords, manualMaskValue);
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
                        controller.setPixelOn(imageCoords, manualMaskValue);
                    }
                }
            });

            variables.getSelectionModel().selectedItemProperty()
                    .addListener(new ChangeListener<String>() {
                        @Override
                        public void changed(ObservableValue<? extends String> observable,
                                String oldVar, String newVar) {
                            try {
                                imageGenerator.setVariable(newVar);
                                varLabel.textProperty().set(newVar);
                                imageView.updateImage();
                            } catch (EdalException e) {
                                e.printStackTrace();
                            }
                        }
                    });
            variables.getCheckModel().getCheckedItems()
                    .addListener(new ListChangeListener<String>() {
                        @Override
                        public void onChanged(
                                javafx.collections.ListChangeListener.Change<? extends String> c) {
                            String[] mask = new String[variables.getCheckModel().getCheckedItems()
                                    .size()];
                            for (int i = 0; i < variables.getCheckModel().getCheckedItems().size(); i++) {
                                mask[i] = variables.getCheckModel().getCheckedItems().get(i) + "-"
                                        + MaskedDataset.MASK_SUFFIX;
                            }
                            controller.setMaskedVariables(mask);
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

        getChildren().add(0, imageView);
    }

    public void addToMask(String variable) {
        variables.getCheckModel().check(variable);
    }

    public void removeFromMask(String variable) {
        variables.getCheckModel().clearCheck(variable);
    }

    public boolean isVariableIncluded(String variable) {
        return variables.getCheckModel().getCheckedItems().contains(variable);
    }
}
