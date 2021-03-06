/*******************************************************************************
 * Copyright (c) 2015 The University of Reading
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

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Stop;

import org.controlsfx.control.RangeSlider;

public class MaskRangeSlider extends RangeSlider {
    private static final Paint LIGHT_GRADIENT = new LinearGradient(0, 0, 0, 1, true,
            CycleMethod.NO_CYCLE, new Stop(0, Color.LIGHTGRAY), new Stop(0.33, Color.WHITE),
            new Stop(1.0, Color.LIGHTGRAY));
    private static final Paint DARK_GRADIENT = new LinearGradient(0, 0, 0, 1, true,
            CycleMethod.NO_CYCLE, new Stop(0, Color.DARKGRAY), new Stop(0.66, Color.BLACK),
            new Stop(1.0, Color.DARKGRAY));
    private boolean inclusive = false;

    public MaskRangeSlider() {
        super();
        getStyleClass().add("mask-slider");
        /*
         * This used to not be necessary, but a Java update changed things.
         * 
         * Previously the setBackgrounds() method only needed to be called
         * within setInclusive()
         */
        lowValueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldValue,
                    Number newValue) {
                setBackgrounds();
            }
        });
        highValueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldValue,
                    Number newValue) {
                setBackgrounds();
            }
        });
        minProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observer, Number oldVal,
                    Number newVal) {
                setTickUnit();
            }
        });
        maxProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observer, Number oldVal,
                    Number newVal) {
                setTickUnit();
            }
        });
        setBackgrounds();
    }
    
    public void setInclusive(boolean inclusive) {
        this.inclusive = inclusive;
        setBackgrounds();
    }

    private void setBackgrounds() {
        BackgroundFill inFill;
        BackgroundFill outFill;

        if (inclusive) {
            inFill = new BackgroundFill(DARK_GRADIENT, null, null);
            outFill = new BackgroundFill(LIGHT_GRADIENT, new CornerRadii(5), null);
        } else {
            inFill = new BackgroundFill(LIGHT_GRADIENT, null, null);
            outFill = new BackgroundFill(DARK_GRADIENT, new CornerRadii(5), null);
        }

        for (Node node : getChildren()) {
            if (node instanceof StackPane) {
                if (node.getStyleClass().contains("track")) {
                    ((StackPane) node).setBackground(new Background(outFill));
                }
                if (node.getStyleClass().contains("range-bar")) {
                    ((StackPane) node).setBackground(new Background(inFill));
                }
            }
        }
    }

    private void setTickUnit() {
        double unit = (getMax() - getMin()) / 10.0;
        for (int i = -10;; i++) {
            if (Math.pow(10, i) / 2 > unit) {
                unit = Math.pow(10, i) / 2;
                break;
            } else if (Math.pow(10, i) > unit) {
                unit = Math.pow(10, i);
                break;
            }
        }
        setMajorTickUnit(unit);
    }
}
