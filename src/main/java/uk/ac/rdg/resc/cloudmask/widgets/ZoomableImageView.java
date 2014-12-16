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

import uk.ac.rdg.resc.edal.position.HorizontalPosition;
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
import javafx.scene.input.TouchEvent;
import javafx.scene.input.ZoomEvent;

/**
 * A class to provide an {@link ImageView} which is smoothly zoomable,
 * scrollable, and uses dynamically-generated images from an
 * {@link ImageGenerator}.
 *
 * @author Guy Griffiths
 */
public class ZoomableImageView extends ImageView {
    /** The minimum valid x co-ordinate for image generation */
    private double minXBound;
    /** The minimum valid y co-ordinate for image generation */
    private double minYBound;
    /** The maximum valid x co-ordinate for image generation */
    private double maxXBound;
    /** The maximum valid y co-ordinate for image generation */
    private double maxYBound;
    /** The width in pixels of the {@link ImageView} widget */
    private final int width;
    /** The height in pixels of the {@link ImageView} widget */
    private final int height;
    /** The {@link ImageGenerator} which will be used to generate images */
    protected ImageGenerator imageGenerator;

    /*
     * Store the co-ordinate of the last drag point so that image can be updated
     * whilst dragging
     */
    private double lastDragX;
    private double lastDragY;

    /** The current minimum visible x co-ordinate */
    protected double minX;
    /** The current minimum visible y co-ordinate */
    protected double minY;
    /** The current maximum visible x co-ordinate */
    protected double maxX;
    /** The current maximum visible y co-ordinate */
    protected double maxY;
    /**
     * The current minimum x co-ordinate generated in the image (to allow
     * dragging)
     */
    protected double minXBorder;
    /**
     * The current minimum y co-ordinate generated in the image (to allow
     * dragging)
     */
    protected double minYBorder;
    /**
     * The current maximum x co-ordinate generated in the image (to allow
     * dragging)
     */
    protected double maxXBorder;
    /**
     * The current maximum y co-ordinate generated in the image (to allow
     * dragging)
     */
    protected double maxYBorder;

    /** The width of the currently generated image */
    private int currentImageWidth;
    /** The height of the currently generated image */
    private int currentImageHeight;

    /**
     * A thread on the main JFX thread which allows a timer to be set before
     * image regeneration.
     */
    private Task<Boolean> regenerationTask = null;
    private int touchCount = 0;

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
            throw new IllegalArgumentException("ImageGenerator cannot be null");
        }

        /*
         * Height of the widget
         */
        this.width = width;
        this.height = height;

        /*
         * The current image with
         */
        this.currentImageWidth = width;
        this.currentImageHeight = height;

        this.imageGenerator = imageGenerator;

        /*
         * Set the widget size
         */
        setFitWidth(width);
        setFitHeight(height);

        /*
         * Set bounds
         */
        minXBound = imageGenerator.getMinValidX();
        minYBound = imageGenerator.getMinValidY();
        maxXBound = imageGenerator.getMaxValidX();
        maxYBound = imageGenerator.getMaxValidY();

        /*
         * Set initial values to image bounds
         */
        minX = minXBound;
        minY = minYBound;
        maxX = maxXBound;
        maxY = maxYBound;

        double widgetRatio = ((double) width) / height;
        double viewportRatio = (maxX - minX) / (maxY - minY);

        if (widgetRatio < viewportRatio) {
            double desiredViewportWidth = widgetRatio * (maxY - minY);
            double midpoint = (minX + maxX) / 2.0;
            minX = midpoint - desiredViewportWidth / 2.0;
            maxX = midpoint + desiredViewportWidth / 2.0;
        } else if (viewportRatio < widgetRatio) {
            double desiredViewportHeight = (maxX - minX) / widgetRatio;
            double midpoint = (minY + maxY) / 2.0;
            minY = midpoint - desiredViewportHeight / 2.0;
            maxY = midpoint + desiredViewportHeight / 2.0;
        }

        /*
         * Set initial border values to image bounds
         */
        minXBorder = minX;
        minYBorder = minY;
        maxXBorder = maxX;
        maxYBorder = maxY;

        /*
         * Now generate the initial image to be displayed
         */
        setImage(SwingFXUtils.toFXImage(
                imageGenerator.generateImage(minX, minY, maxX, maxY, width, height), null));
        setViewport(new Rectangle2D(0, 0, width, height));

        setOnTouchPressed(new EventHandler<TouchEvent>() {
            @Override
            public void handle(TouchEvent event) {
                touchCount = event.getTouchPoints().size();
            }
        });

        setOnTouchReleased(new EventHandler<TouchEvent>() {
            @Override
            public void handle(TouchEvent event) {
                touchCount = event.getTouchPoints().size() - 1;
            }
        });

        setOnZoomStarted(new EventHandler<ZoomEvent>() {
            @Override
            public void handle(ZoomEvent event) {
                cancelRegeneration();
            }
        });

        setOnZoom(new EventHandler<ZoomEvent>() {
            @Override
            public void handle(ZoomEvent event) {
                doPixelZoom(event.getZoomFactor(), event.getX(), event.getY());
                updateImageQuick();
            }
        });

        setOnZoomFinished(new EventHandler<ZoomEvent>() {
            @Override
            public void handle(ZoomEvent event) {
                regenerateImageIn(1000L);
            }
        });

        /*
         * Add handlers for mouse events
         */
        setOnScroll(new EventHandler<ScrollEvent>() {
            @Override
            public void handle(ScrollEvent event) {
                if (touchCount == 2) {
                    /*
                     * We have a touch scroll event, which should be treated as
                     * a pan if done with 2 fingers
                     */
                    if (!event.isInertia()) {
                        doPixelDrag(event.getDeltaX(), -event.getDeltaY());
                        updateImageQuick();
                    }
                } else if (touchCount == 0 && !event.isInertia()) {
                    /*
                     * We have a mouse scroll event, which should be treated as
                     * a zoom
                     */
                    /*
                     * Adjust zoom by a constant factor per scroll wheel click
                     */
                    if (event.getDeltaY() > 0) {
                        doPixelZoom(1.1, event.getX(), event.getY());
                    } else {
                        doPixelZoom(1 / 1.1, event.getX(), event.getY());
                    }

                    /*
                     * Now update the image based on the new limits
                     */
                    updateImageQuick();
                    regenerateImageIn(1000L);
                }
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
                if (touchCount == 0 && event.getButton() == MouseButton.PRIMARY) {
                    double offsetX = (event.getX() - lastDragX);
                    double offsetY = (event.getY() - lastDragY);
                    lastDragX = event.getX();
                    lastDragY = event.getY();
                    doPixelDrag(offsetX, -offsetY);
                    updateImageQuick();
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
                    /*
                     * Leave it a second in case a user wants to click and drag
                     * again further
                     */
                    regenerateImageIn(1000L);
                }
            }
        });
    }

    /**
     * Updates appropriate variables to represent a zoom. Does not update the
     * image, just sets new limits
     * 
     * @param factor
     *            The factor to zoom by. Values greater than one represent a
     *            zoom in, and values between 0 and 1 represent a zoom out
     * @param pixelCentreX
     *            The x co-ordinate of the centre of the zoom in pixels relative
     *            to the viewport
     * @param pixelCentreY
     *            The y co-ordinate of the centre of the zoom in pixels relative
     *            to the viewport
     */
    private void doPixelZoom(double factor, double pixelCentreX, double pixelCentreY) {
        /*
         * The centre of the zoom in co-ordinate space
         */
        double coordCentreX = minX + (pixelCentreX / width) * (maxX - minX);
        double coordCentreY = minY + ((height - pixelCentreY - 1) / height) * (maxY - minY);
        doZoom(factor, coordCentreX, coordCentreY);
    }

    /**
     * Updates appropriate variables to represent a zoom. Does not update the
     * image, just sets new limits
     * 
     * @param factor
     *            The factor to zoom by. Values greater than one represent a
     *            zoom in, and values between 0 and 1 represent a zoom out
     * @param centreX
     *            The x co-ordinate of the centre of the zoom
     * @param centreY
     *            The y co-ordinate of the centre of the zoom
     */
    protected void doZoom(double factor, double centreX, double centreY) {
        /*
         * Convenient values
         */
        double widthX = maxX - minX;
        double widthY = maxY - minY;

        /*
         * The final width in co-ordinate space after the zoom
         */
        double finalWidthX = widthX / factor;
        double finalWidthY = widthY / factor;

        double imageFactor = ((double) width) / height;
        double zoomedFactor = finalWidthX / finalWidthY;
        if (imageFactor > zoomedFactor) {
            finalWidthY = finalWidthX / imageFactor;
        } else if (zoomedFactor > imageFactor) {
            finalWidthX = finalWidthY * imageFactor;
        }

        /*
         * How much to shift each side (min/max) by (relatively)
         */
        double minXShiftFactor = (centreX - minX) / widthX;
        double maxXShiftFactor = (maxX - centreX) / widthX;
        double minYShiftFactor = (centreY - minY) / widthY;
        double maxYShiftFactor = (maxY - centreY) / widthY;

        /*
         * Now adjust the co-ordinates
         */
        minX = centreX - finalWidthX * minXShiftFactor;
        maxX = centreX + finalWidthX * maxXShiftFactor;
        minY = centreY - finalWidthY * minYShiftFactor;
        maxY = centreY + finalWidthY * maxYShiftFactor;

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
        cancelRegeneration();
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

    private void cancelRegeneration() {
        if (regenerationTask != null) {
            regenerationTask.cancel();
        }
    }

    /**
     * Updates appropriate variables to represent a drag. Does not update the
     * image, just sets new limits
     * 
     * @param xPixels
     *            the number of pixels dragged in the x-direction
     * @param yPixels
     *            the number of pixels dragged in the y-direction
     */
    private void doPixelDrag(double xPixels, double yPixels) {
        /*
         * The amount the co-ordinates have changed
         */
        double coordsChangeX = xPixels * (maxX - minX) / width;
        double coordsChangeY = yPixels * (maxY - minY) / height;
        doDrag(coordsChangeX, coordsChangeY);
    }

    /**
     * Updates appropriate variables to represent a drag. Does not update the
     * image, just sets new limits
     * 
     * @param coordsChangeX
     *            the amount to change the x co-ordinate by
     * @param coordsChangeY
     *            the amount to change the y co-ordinate by
     */
    protected void doDrag(double coordsChangeX, double coordsChangeY) {
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
    }

    /**
     * Updates the image by setting the viewport of the {@link ImageView}. This
     * is quick, but zoom artifacts will be present, and if dragged beyond the
     * border there will be missing data
     */
    protected void updateImageQuick() {
        /*
         * Update the offsets
         */
        double xfactor = (maxX - minX) / (maxXBorder - minXBorder);
        double yfactor = (maxY - minY) / (maxYBorder - minYBorder);

        double xoff = (minX - minXBorder) * (currentImageWidth) / (maxXBorder - minXBorder);
        double yoff = (maxYBorder - maxY) * (currentImageHeight) / (maxYBorder - minYBorder);
        setViewport(new Rectangle2D(xoff, yoff, (currentImageWidth * xfactor),
                (currentImageHeight * yfactor)));
    }

    /**
     * Updates the image by requesting a new one from the {@link ImageGenerator}
     * The new image will have a border up to the size of the image on all sides
     * to allow for dragging
     */
    public void updateImage() {
        /*
         * Generate an image with a border around it to allow for smooth panning
         */
        minXBorder = 2 * minX - maxX;
        maxXBorder = 2 * maxX - minX;

        minYBorder = 2 * minY - maxY;
        maxYBorder = 2 * maxY - minY;

        minXBorder = minX;
        minYBorder = minY;
        maxXBorder = maxX;
        maxYBorder = maxY;

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

        /*
         * Calculate the size of the image which needs to be generated, and
         * store for future use
         */
        currentImageWidth = (int) (width * (maxXBorder - minXBorder) / (maxX - minX));
        currentImageHeight = (int) (height * (maxYBorder - minYBorder) / (maxY - minY));
        /*
         * Generate a new BufferedImage and convert it to a WritableImage for
         * display.
         */
        if (imageGenerator != null) {
            WritableImage fxImage = SwingFXUtils.toFXImage(imageGenerator.generateImage(minXBorder,
                    minYBorder, maxXBorder, maxYBorder, currentImageWidth, currentImageHeight),
                    null);
            double xoff = (minX - minXBorder) * width / (maxX - minX);
            double yoff = (maxYBorder - maxY) * (height) / (maxY - minY);
            setImage(fxImage);
            setViewport(new Rectangle2D(xoff, yoff, width, height));
        }
    }

    /**
     * Gets the underlying co-ordinates from the pixel position on the image.
     * Useful in conjunction with adding various mouse handlers etc. to this
     * {@link ImageView}
     * 
     * @param x
     *            The x co-ordinate in pixel space (such as is returned from
     *            {@link MouseEvent#getX()}
     * @param y
     *            The y co-ordinate in pixel space (such as is returned from
     *            {@link MouseEvent#getX()}
     * @return A {@link HorizontalPosition} containing the co-ordinates in the
     *         image generator space
     */
    public HorizontalPosition getCoordinateFromImagePosition(double x, double y) {
        return new HorizontalPosition(minX + (maxX - minX) * (x / width), minY + (maxY - minY)
                * (1.0 - (y / height)), null);
    }

    public interface ImageGenerator {
        /**
         * Generates an image to display in the zoomable image view.
         * 
         * @param minX
         *            the minimum x-coordinate
         * @param minY
         *            the minimum y-coordinate - this represents the bottom of
         *            the image (note that this is *not* the standard behaviour
         *            for a {@link BufferedImage})
         * @param maxX
         *            the maximum x-coordinate
         * @param maxY
         *            the maximum y-coordinate - this represents the top of the
         *            image (note that this is *not* the standard behaviour for
         *            a {@link BufferedImage})
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
