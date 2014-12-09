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
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Rectangle2D;
import javafx.scene.layout.HBox;

import org.controlsfx.control.CheckListView;

import uk.ac.rdg.resc.cloudmask.CloudMaskDatasetFactory.MaskedDataset;
import uk.ac.rdg.resc.cloudmask.widgets.LinkedZoomableImageView;
import uk.ac.rdg.resc.cloudmask.widgets.ZoomableImageView.ImageGenerator;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.graphics.style.util.SimpleFeatureCatalogue;

public class CompositeMaskView extends HBox {
    LinkedZoomableImageView imageView = null;
    private CheckListView<String> variables;
    private CompositeMaskEdalImageGenerator imageGenerator = null;

    private int imageWidth;
    private int imageHeight;
    private CloudMaskController controller;

    public CompositeMaskView(int imageWidth, int imageHeight,
            CloudMaskController cloudMaskController) {
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.controller = cloudMaskController;

        variables = new CheckListView<>();
        variables.setItems(controller.getAvailableVariables());
        variables.setPrefWidth(10000);

        try {
            setCatalogue(null);
        } catch (EdalException | IOException e) {
            e.printStackTrace();
        }
        getChildren().add(variables);
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

            imageGenerator = new CompositeMaskEdalImageGenerator(variableNames.get(0), catalogue);
            imageView = new LinkedZoomableImageView(imageWidth, imageHeight, imageGenerator);

            variables.getSelectionModel().selectedItemProperty()
                    .addListener(new ChangeListener<String>() {
                        @Override
                        public void changed(ObservableValue<? extends String> observable,
                                String oldVar, String newVar) {
                            try {
                                imageGenerator.setVariable(newVar);
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
