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

package uk.ac.rdg.resc.cloudmask.widgets;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Orientation;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;

import javax.imageio.ImageIO;

import org.controlsfx.control.RangeSlider;

import uk.ac.rdg.resc.cloudmask.EdalImageGenerator;

public class ColourbarSlider extends RangeSlider {
    private EdalImageGenerator imageGenerator;
    private Orientation orientation;

    public ColourbarSlider() {
        super();
        getStyleClass().add("colourbar-slider");
        lowValueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observer, Number oldVal,
                    Number newVal) {
                updateValue();
            }
        });
        highValueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observer, Number oldVal,
                    Number newVal) {
                updateValue();
            }
        });
        heightProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observer, Number oldVal,
                    Number newVal) {
                updateValue();
            }
        });
        widthProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observer, Number oldVal,
                    Number newVal) {
                updateValue();
            }
        });
        
        setHighValue(getMax());
        setLowValue(getMin());

        orientationProperty().addListener(new ChangeListener<Orientation>() {
            @Override
            public void changed(ObservableValue<? extends Orientation> observer,
                    Orientation oldVal, Orientation newVal) {
                ColourbarSlider.this.orientation = newVal;
            }
        });
        
        updateValue();
    }

    public void setImageGenerator(EdalImageGenerator imageGenerator) {
        this.imageGenerator = imageGenerator;
        updateValue();
    }

    public void updateValue() {
        if (imageGenerator != null) {
            float range = (float) (getHighValue() - getLowValue());
            if (range == 0.0) {
                range = 0.001f;
            }

            float belowMin = (float) ((getLowValue() - getMin()) / range);
            float aboveMax = (float) ((getMax() - getHighValue()) / range);
            BufferedImage legend = imageGenerator.getLegend((int) getHeight(), belowMin, aboveMax,
                    orientation == Orientation.VERTICAL);
            try {
                ImageIO.write(legend, "png", new File("/home/guy/test.png"));
            } catch (IOException e) {
                e.printStackTrace();
            }
            WritableImage fxImage = SwingFXUtils.toFXImage(legend, null);
            setBackground(new Background(new BackgroundImage(fxImage, null, null, null, null)));
        }
    }
}
