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

import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.EventHandler;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;

/**
 * A class to provide an {@link ImageView} which is smoothly zoomable,
 * scrollable, and uses dynamically-generated images from an
 * {@link ImageGenerator}.
 *
 * @author Guy Griffiths
 */
public class ZoomableImageView extends ImageView {
    /** The minimum valid x co-ordinate for image generation */
    private final double minXBound;
    /** The minimum valid y co-ordinate for image generation */
    private final double minYBound;
    /** The maximum valid x co-ordinate for image generation */
    private final double maxXBound;
    /** The maximum valid y co-ordinate for image generation */
    private final double maxYBound;
    /** The width in pixels of the {@link ImageView} widget */
    private final int width;
    /** The height in pixels of the {@link ImageView} widget */
    private final int height;
    /** The {@link ImageGenerator} which will be used to generate images */
    private final ImageGenerator imageGenerator;

    private double lastDragX;
    private double lastDragY;

    private double minX;
    private double minY;
    private double maxX;
    private double maxY;
    private double minXBorder;
    private double maxXBorder;
    private double minYBorder;
    private double maxYBorder;

    private int currentImageWidth;
    private int currentImageHeight;

    private Task<Boolean> regenerationTask = null;

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

        minXBound = imageGenerator.getMinValidX();
        minYBound = imageGenerator.getMinValidY();
        maxXBound = imageGenerator.getMaxValidX();
        maxYBound = imageGenerator.getMaxValidY();

        minX = minXBound;
        minY = minYBound;
        maxX = maxXBound;
        maxY = maxYBound;

        minXBorder = minX;
        minYBorder = minY;
        maxXBorder = maxX;
        maxYBorder = maxY;

        this.width = width;
        this.height = height;

        this.currentImageWidth = width;
        this.currentImageHeight = height;

        this.imageGenerator = imageGenerator;

        setFitWidth(width);
        setFitHeight(height);

        setImage(SwingFXUtils.toFXImage(
                imageGenerator.generateImage(minX, minY, maxX, maxY, width, height), null));
        setViewport(new Rectangle2D(0, 0, width, height));

        setOnScroll(new EventHandler<ScrollEvent>() {
            @Override
            public void handle(ScrollEvent event) {
                doZoom(0.2 * event.getDeltaY() / height, event.getX(), event.getY());
            }
        });

        setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                /*
                 * Register the start of a drag event
                 */
                if (event.getButton() == MouseButton.PRIMARY) {
                    lastDragX = event.getX();
                    lastDragY = event.getY();
                }
            }
        });
        setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                /*
                 * Process the drag by moving the image (without regenerating
                 * it)
                 */
                if (event.getButton() == MouseButton.PRIMARY) {
                    double offsetX = (event.getX() - lastDragX);
                    double offsetY = (event.getY() - lastDragY);
                    lastDragX = event.getX();
                    lastDragY = event.getY();
                    doDrag(offsetX, offsetY);
                }
            }
        });
        setOnMouseReleased(new EventHandler<MouseEvent>() {
            /*
             * Register the end of a drag event by regenerating the image with
             * the new limits
             */
            @Override
            public void handle(MouseEvent event) {
                if (event.getButton() == MouseButton.PRIMARY) {
                    updateImage();
                }
            }
        });
    }

    private void doZoom(double amount, double centreX, double centreY) {
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

        /*
         * Now update the image based on the new limits
         */
        updateImageQuick();
        regenerateImageIn(500L);
    }

    /**
     * Starts a timer to sleep for a given amount of time and then call the
     * {@link ZoomableImageView#updateImage()} method to regenerate the image.
     * Subsequent calls to this method before the timer has run out will reset
     * the timer.
     * 
     * @param delay
     *            The length of the timer in ms
     */
    private void regenerateImageIn(long delay) {
        if (regenerationTask != null) {
            regenerationTask.cancel();
        }
        regenerationTask = new Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                Thread.sleep(delay);
                return true;
            }
        };
        regenerationTask.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {
                updateImage();
            }
        });
        new Thread(regenerationTask).start();
    }

    private void doDrag(double xPixels, double yPixels) {
        double coordsChangeX = xPixels * (maxX - minX) / width;
        double coordsChangeY = yPixels * (maxY - minY) / height;

        if (minX - coordsChangeX < minXBound) {
            coordsChangeX = minX - minXBound;
        }
        if (maxX - coordsChangeX > maxXBound) {
            coordsChangeX = maxX - maxXBound;
        }
        if (minY - coordsChangeY < minYBound) {
            coordsChangeY = minY - minYBound;
        }
        if (maxY - coordsChangeY > maxYBound) {
            coordsChangeY = maxY - maxYBound;
        }
        minX -= coordsChangeX;
        maxX -= coordsChangeX;
        minY -= coordsChangeY;
        maxY -= coordsChangeY;

        updateImageQuick();
    }

    private void updateImageQuick() {
        /*
         * Update the offsets
         */
        double xfactor = (maxX - minX) / (maxXBorder - minXBorder);
        double yfactor = (maxY - minY) / (maxYBorder - minYBorder);

        double xoff = (minX - minXBorder) * (currentImageWidth) / (maxXBorder - minXBorder);
        double yoff = (minY - minYBorder) * (currentImageHeight) / (maxYBorder - minYBorder);
        setViewport(new Rectangle2D(xoff, yoff, (currentImageWidth * xfactor),
                (currentImageHeight * yfactor)));
    }

    /**
     * Updates the image.
     * 
     * First sets the viewport of the current image to reflect the recent
     * zoom/pan.
     * 
     * Then fires a request off to the image generator in a new thread to
     * calculate a new image with an appropriately-sized border (to allow for
     * smooth panning).
     * 
     * This means that even if the image generator takes an appreciable amount
     * of time to generate an image, we still get smooth transitions.
     */
    private void updateImage() {
        /*
         * Generate an image with a border around it to allow for smooth panning
         */
        minXBorder = 2 * minX - maxX;
        maxXBorder = 2 * maxX - minX;

        minYBorder = 2 * minY - maxY;
        maxYBorder = 2 * maxY - minY;

        if (minXBorder < minXBound) {
            minXBorder = minXBound;
        }
        if (maxXBorder > maxXBound) {
            maxXBorder = maxXBound;
        }

        if (minYBorder < minYBound) {
            minYBorder = minYBound;
        }
        if (maxYBorder > maxYBound) {
            maxYBorder = maxYBound;
        }

        currentImageWidth = (int) (width * (maxXBorder - minXBorder) / (maxX - minX));
        currentImageHeight = (int) (height * (maxYBorder - minYBorder) / (maxY - minY));
        WritableImage fxImage = SwingFXUtils.toFXImage(imageGenerator.generateImage(minXBorder,
                minYBorder, maxXBorder, maxYBorder, currentImageWidth, currentImageHeight), null);
        double xoff = (minX - minXBorder) * width / (maxX - minX);
        double yoff = (minY - minYBorder) * width / (maxY - minY);
        setImage(fxImage);
        setViewport(new Rectangle2D(xoff, yoff, width, height));
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
