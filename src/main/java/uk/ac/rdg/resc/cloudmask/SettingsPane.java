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

import java.io.File;
import java.io.IOException;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import uk.ac.rdg.resc.cloudmask.CloudMaskDatasetFactory.MaskedDataset;
import uk.ac.rdg.resc.edal.dataset.plugins.DifferencePlugin;
import uk.ac.rdg.resc.edal.dataset.plugins.NormalisedDifferencePlugin;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.graphics.utils.GraphicsUtils;

public class SettingsPane extends TitledPane {
    private VBox content;

    private HBox datasetBox;
    private Label currentDatasetLabel;
    private ChoiceBox<String> diffVar1;
    private ChoiceBox<String> diffVar2;
    private ChoiceBox<String> normDiffVar1;
    private ChoiceBox<String> normDiffVar2;
    private ChoiceBox<String> medianVar;
    private ChoiceBox<String> stddevVar;
    private ChoiceBox<String> rgbVar1;
    private ChoiceBox<String> rgbVar2;
    private ChoiceBox<String> rgbVar3;
    private Button diffButton;
    private Button normDiffButton;
    private Button medianButton;
    private Button stddevButton;
    private Button rgbButton;

    public SettingsPane(CloudMaskController controller) {
        setText("Settings");
        setCollapsible(false);

        content = new VBox();
        content.setPrefHeight(10000);
        content.setSpacing(10);

        datasetBox = new HBox();
        datasetBox.setSpacing(10);
        Button loadButton = new Button("Load Dataset");
        loadButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Choose NetCDF dataset");
                fileChooser.getExtensionFilters().addAll(
                        new ExtensionFilter("NetCDF Files", "*.nc"),
                        new ExtensionFilter("NcML Files", "*.ncml"));
                File selectedFile = fileChooser.showOpenDialog(null);
                if (selectedFile != null) {
                    try {
                        controller.loadDataset(selectedFile);
                    } catch (IOException | EdalException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        datasetBox.getChildren().add(loadButton);
        currentDatasetLabel = new Label("No dataset loaded");
        currentDatasetLabel.setFont(new Font(16));
        datasetBox.getChildren().add(currentDatasetLabel);

        TitledPane operationsBox = new TitledPane();
        operationsBox.setText("Mathematical Operations");
        operationsBox.setCollapsible(false);

        GridPane operations = new GridPane();
        operations.setHgap(MaskedVariableView.WIDGET_SPACING);
        operations.setVgap(MaskedVariableView.WIDGET_SPACING);

        Label diffLabel = new Label("Difference");
        diffLabel.setMinWidth(150);
        diffVar1 = new ChoiceBox<>();
        diffVar1.setMinWidth(150);
        diffVar2 = new ChoiceBox<>();
        diffVar2.setMinWidth(150);
        diffButton = new Button("Generate");
        diffButton.setMinWidth(100);
        diffButton.setDisable(true);
        diffButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                String var1 = diffVar1.getValue();
                String var2 = diffVar2.getValue();
                if (var1 != null && var2 != null) {
                    controller.addPlugin(new DifferencePlugin(var1, var2));
                }
            }
        });

        operations.add(diffLabel, 0, 0);
        operations.add(diffVar1, 1, 0);
        operations.add(diffVar2, 2, 0);
        operations.add(diffButton, 4, 0);

        Label normDiffLabel = new Label("Normalised Difference");
        normDiffVar1 = new ChoiceBox<>();
        normDiffVar1.setMinWidth(150);
        normDiffVar2 = new ChoiceBox<>();
        normDiffVar2.setMinWidth(150);
        normDiffButton = new Button("Generate");
        normDiffButton.setMinWidth(100);
        normDiffButton.setDisable(true);
        normDiffButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                String var1 = normDiffVar1.getValue();
                String var2 = normDiffVar2.getValue();
                if (var1 != null && var2 != null) {
                    controller.addPlugin(new NormalisedDifferencePlugin(var1, var2));
                }
            }
        });

        operations.add(normDiffLabel, 0, 1);
        operations.add(normDiffVar1, 1, 1);
        operations.add(normDiffVar2, 2, 1);
        operations.add(normDiffButton, 4, 1);

        Label medianLabel = new Label("Median filter");
        medianVar = new ChoiceBox<>();
        medianVar.setMinWidth(150);
        medianButton = new Button("Generate");
        medianButton.setMinWidth(100);
        medianButton.setDisable(true);
        medianButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                String var = medianVar.getValue();
                if (var != null) {
                    controller.enableMedian(var);
                }
            }
        });

        operations.add(medianLabel, 0, 2);
        operations.add(medianVar, 1, 2);
        operations.add(medianButton, 4, 2);

        Label stddevLabel = new Label("Std. dev. filter");
        stddevVar = new ChoiceBox<>();
        stddevVar.setMinWidth(150);
        stddevButton = new Button("Generate");
        stddevButton.setMinWidth(100);
        stddevButton.setDisable(true);
        stddevButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                String var = stddevVar.getValue();
                if (var != null) {
                    controller.enableStddev(var);
                }
            }
        });

        operations.add(stddevLabel, 0, 3);
        operations.add(stddevVar, 1, 3);
        operations.add(stddevButton, 4, 3);

        Label rgbLabel = new Label("RGB Image");
        rgbVar1 = new ChoiceBox<>();
        rgbVar1.setMinWidth(150);
        rgbVar2 = new ChoiceBox<>();
        rgbVar2.setMinWidth(150);
        rgbVar3 = new ChoiceBox<>();
        rgbVar3.setMinWidth(150);
        rgbButton = new Button("Generate");
        rgbButton.setMinWidth(100);
        rgbButton.setDisable(true);
        rgbButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                String var1 = rgbVar1.getValue();
                String var2 = rgbVar2.getValue();
                String var3 = rgbVar3.getValue();
                if (var1 != null && var2 != null && var3 != null) {
                    controller.addPlugin(new RgbFalseColourPlugin(var1, var2, var3, GraphicsUtils
                            .estimateValueRange(controller.getDataset(), var1), GraphicsUtils
                            .estimateValueRange(controller.getDataset(), var2), GraphicsUtils
                            .estimateValueRange(controller.getDataset(), var3)));
                }
            }
        });

        operations.add(rgbLabel, 0, 4);
        operations.add(rgbVar1, 1, 4);
        operations.add(rgbVar2, 2, 4);
        operations.add(rgbVar3, 3, 4);
        operations.add(rgbButton, 4, 4);

        operationsBox.setContent(operations);

        Button saveButton = new Button("Save");
        saveButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Choose file to save");
                fileChooser.getExtensionFilters().addAll(
                        new ExtensionFilter("NetCDF Files", "*.nc"),
                        new ExtensionFilter("NcML Files", "*.ncml"));
                File selectedFile = fileChooser.showSaveDialog(null);
                if (selectedFile != null) {
                    controller.saveCurrentDataset(selectedFile);
                }
            }
        });

        Slider maskOpacity = new Slider(0.0, 1.0, 0.75);
        maskOpacity.valueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldVal,
                    Number newVal) {
                controller.setMaskOpacity(newVal);
            }
        });
        TitledPane darknessBox = new TitledPane("Mask Opacity", maskOpacity);
        darknessBox.setCollapsible(false);
        
        Button toggleFullscreenButton = new Button("Toggle fullscreen");
        toggleFullscreenButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                controller.toggleFullscreen();
            }
        });

        Button exitButton = new Button("Quit");
        exitButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                controller.quit();
            }
        });
        
        content.getChildren().add(datasetBox);
        content.getChildren().add(saveButton);
        content.getChildren().add(operationsBox);
        content.getChildren().add(darknessBox);
        content.getChildren().add(toggleFullscreenButton);
        content.getChildren().add(exitButton);

        setContent(content);
        setPrefWidth(10000);
    }

    public void setDatasetLoaded(MaskedDataset dataset) {
        currentDatasetLabel.setText(dataset.getId());

        ObservableList<String> variables = dataset.getUnmaskedVariableNames();
        diffVar1.setItems(variables);
        diffVar1.getSelectionModel().select(0);
        diffVar2.setItems(variables);
        diffVar2.getSelectionModel().select(1);
        normDiffVar1.setItems(variables);
        normDiffVar1.getSelectionModel().select(0);
        normDiffVar2.setItems(variables);
        normDiffVar2.getSelectionModel().select(1);
        medianVar.setItems(dataset.getOriginalVariableNames());
        medianVar.getSelectionModel().select(0);
        stddevVar.setItems(dataset.getOriginalVariableNames());
        stddevVar.getSelectionModel().select(0);

        rgbVar1.setItems(variables);
        rgbVar1.getSelectionModel().select(0);
        rgbVar2.setItems(variables);
        rgbVar2.getSelectionModel().select(1);
        rgbVar3.setItems(variables);
        rgbVar3.getSelectionModel().select(2);

        diffButton.setDisable(false);
        normDiffButton.setDisable(false);
        medianButton.setDisable(false);
        stddevButton.setDisable(false);
        rgbButton.setDisable(false);
    }
}
