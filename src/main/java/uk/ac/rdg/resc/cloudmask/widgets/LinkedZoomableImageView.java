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

import java.util.ArrayList;
import java.util.List;

public class LinkedZoomableImageView extends ZoomableImageView {

    private List<LinkedZoomableImageView> linkedViews;

    public LinkedZoomableImageView(int width, int height, ImageGenerator imageGenerator) {
        super(width, height, imageGenerator);
        linkedViews = new ArrayList<LinkedZoomableImageView>();
    }

    public void addLinkedView(LinkedZoomableImageView view) {
        if (view.imageGenerator.getMinValidX() != imageGenerator.getMinValidX()
                || view.imageGenerator.getMaxValidX() != imageGenerator.getMaxValidX()
                || view.imageGenerator.getMinValidY() != imageGenerator.getMinValidY()
                || view.imageGenerator.getMaxValidY() != imageGenerator.getMaxValidY()) {
            throw new IllegalArgumentException("Linked views must have the same image bounds");
        }

        view.minX = minX;
        view.maxX = maxX;
        view.minY = minY;
        view.maxY = maxY;
        
        view.minXBorder = minXBorder;
        view.maxXBorder = maxXBorder;
        view.minYBorder = minYBorder;
        view.maxYBorder = maxYBorder;
        
        for(LinkedZoomableImageView other : linkedViews) {
            other.linkedViews.add(view);
            view.linkedViews.add(other);
        }
        linkedViews.add(view);
        view.linkedViews.add(this);
    }
    
    public void removeLinkedView(LinkedZoomableImageView view) {
        for(LinkedZoomableImageView other : linkedViews) {
            other.linkedViews.remove(view);
        }
        linkedViews.remove(view);
        view.linkedViews.clear();
    }

    @Override
    protected void doZoom(double factor, double centreX, double centreY) {
        super.doZoom(factor, centreX, centreY);
        for (LinkedZoomableImageView view : linkedViews) {
            view.doLinkedZoom(factor, centreX, centreY);
        }
    }

    private void doLinkedZoom(double factor, double centreX, double centreY) {
        super.doZoom(factor, centreX, centreY);
    }

    @Override
    protected void doDrag(double coordsChangeX, double coordsChangeY) {
        super.doDrag(coordsChangeX, coordsChangeY);
        for (LinkedZoomableImageView view : linkedViews) {
            view.doLinkedDrag(coordsChangeX, coordsChangeY);
        }
    }

    protected void doLinkedDrag(double coordsChangeX, double coordsChangeY) {
        super.doDrag(coordsChangeX, coordsChangeY);
    }

    @Override
    protected void updateImageQuick() {
        super.updateImageQuick();
        for (LinkedZoomableImageView view : linkedViews) {
            view.updateLinkedImageQuick();
        }
    }

    private void updateLinkedImageQuick() {
        super.updateImageQuick();
    }

    @Override
    public void updateImage() {
        super.updateImage();
        for (LinkedZoomableImageView view : linkedViews) {
            view.updateLinkedImage();
        }
    }

    private void updateLinkedImage() {
        super.updateImage();
    }
}
