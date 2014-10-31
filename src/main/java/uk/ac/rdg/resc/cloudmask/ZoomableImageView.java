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

import javafx.embed.swing.SwingFXUtils;
import javafx.event.EventHandler;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;

/**
 * A class to provide an {@link ImageView} which is zoomable, scrollable, and
 * calls back to generate new images after a zoom/scroll, avoiding any zooming
 * artifacts.
 *
 * @author Guy Griffiths
 */
public class ZoomableImageView extends ImageView {
    private final double minXBound;
    private final double minYBound;
    private final double maxXBound;
    private final double maxYBound;
    private final int width;
    private final int height;
    private final ImageGenerator imageGenerator;

    private double lastDragX;
    private double lastDragY;

    private double minX;
    private double minY;
    private double maxX;
    private double maxY;

    /**
     * Constructs a new {@link ZoomableImageView}
     * 
     * @param width
     *            the width of the view onto the image
     * @param height
     *            the height of the view onto the image
     * @param imageGenerator
     *            an {@link ImageGenerator} to generate new images.
     */
    public ZoomableImageView(int width, int height, ImageGenerator imageGenerator) {
        super();

        if (imageGenerator == null) {
            throw new IllegalArgumentException("imageGenerator cannot be null");
        }

        this.minXBound = imageGenerator.getMinValidX();
        this.minYBound = imageGenerator.getMinValidY();
        this.maxXBound = imageGenerator.getMaxValidX();
        this.maxYBound = imageGenerator.getMaxValidY();

        this.minX = minXBound;
        this.minY = minYBound;
        this.maxX = maxXBound;
        this.maxY = maxYBound;

        this.width = width;
        this.height = height;
        this.imageGenerator = imageGenerator;

        setFitWidth(width);
        setFitHeight(height);

        setImage(SwingFXUtils.toFXImage(
                imageGenerator.generateImage(minX, minY, maxX, maxY, width, height), null));

        setOnScroll(new EventHandler<ScrollEvent>() {
            @Override
            public void handle(ScrollEvent event) {
                doZoom(0.2 * event.getDeltaY() / height, event.getX(), event.getY());
            }
        });

        setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                if (event.getButton() == MouseButton.PRIMARY) {
                    lastDragX = event.getX();
                    lastDragY = event.getY();
                }
            }
        });
        setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                if (event.getButton() == MouseButton.PRIMARY) {
                    double offsetX = (event.getX() - lastDragX);
                    double offsetY = (event.getY() - lastDragY);
                    lastDragX = event.getX();
                    lastDragY = event.getY();
                    doDrag(offsetX, offsetY);
                }
            }
        });
    }

    private void doZoom(double amount, double centreX, double centreY) {
        /*
         * TODO - depending on the speed of the image generator we may want to
         * do a trivial zoom first and then fire off the image generation on a
         * separate thread
         */

        /*
         * Change the image limits based on the zoom amount and the centre of
         * the zoom
         */
        double xFactor = (maxX - minX) / (maxXBound - minXBound);
        double yFactor = (maxY - minY) / (maxYBound - minYBound);

        minX += (centreX * amount) * xFactor;
        maxX -= (width - centreX) * amount * xFactor;
        minY += centreY * amount * yFactor;
        maxY -= (height - centreY) * amount * yFactor;

        /*
         * Check if the zoom goes far enough out that the view goes outside the
         * image. If so, effectively move the zoom centre
         */
        if (minX < minXBound) {
            maxX += minXBound - minX;
            if (maxX > maxXBound) {
                maxX = maxXBound;
            }
            minX = minXBound;
        }
        if (maxX > maxXBound) {
            minX -= maxX - maxXBound;
            if (minX < minXBound) {
                minX = minXBound;
            }
            maxX = maxXBound;
        }
        if (minY < minYBound) {
            maxY += minYBound - minY;
            if (maxY > maxYBound) {
                maxY = maxYBound;
            }
            minY = minYBound;
        }
        if (maxY > maxYBound) {
            minY -= maxY - maxYBound;
            if (minY < minYBound) {
                minY = minYBound;
            }
            maxY = maxYBound;
        }
        
        updateImage();
    }

    private void doDrag(double xPixels, double yPixels) {
        double coordsChangeX = xPixels * (maxX - minX) / width;
        double coordsChangeY = yPixels * (maxY - minY) / height;
        
        if(minX - coordsChangeX < minXBound) {
            coordsChangeX = minX - minXBound;
        }
        if(maxX - coordsChangeX > maxXBound) {
            coordsChangeX = maxX - maxXBound;
        }
        if(minY - coordsChangeY < minYBound) {
            coordsChangeY = minY - minYBound;
        }
        if(maxY - coordsChangeY > maxYBound) {
            coordsChangeY = maxY - maxYBound;
        }
        minX -= coordsChangeX;
        maxX -= coordsChangeX;
        minY -= coordsChangeY;
        maxY -= coordsChangeY;
        
        updateImage();
    }
    
    private void updateImage() {
        /*
         * Now generate a new image with the appropriate co-ordinates
         */
        setImage(SwingFXUtils.toFXImage(
                imageGenerator.generateImage(minX, minY, maxX, maxY, width, height), null));

    }

    public interface ImageGenerator {
        /**
         * Generates an image to display in the zoomable image view.
         * 
         * @param minX
         *            the minimum x-coordinate
         * @param minY
         *            the minimum y-coordinate
         * @param maxX
         *            the maximum x-coordinate
         * @param maxY
         *            the maximum y-coordinate
         * @param width
         *            the desired width of the image
         * @param height
         *            the desired height of the image
         * @return the resulting image
         */
        public BufferedImage generateImage(double minX, double minY, double maxX, double maxY,
                int width, int height);

        /**
         * @return the minimum valid x-coordinate of the image
         */
        public double getMinValidX();

        /**
         * @return the maximum valid x-coordinate of the image
         */
        public double getMaxValidX();

        /**
         * @return the minimum valid y-coordinate of the image
         */
        public double getMinValidY();

        /**
         * @return the maximum valid y-coordinate of the image
         */
        public double getMaxValidY();
    }
}
