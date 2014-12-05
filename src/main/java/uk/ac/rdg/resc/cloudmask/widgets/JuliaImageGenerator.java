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

import uk.ac.rdg.resc.cloudmask.widgets.ZoomableImageView.ImageGenerator;
import uk.ac.rdg.resc.edal.graphics.style.util.ColourPalette;

public class JuliaImageGenerator implements ImageGenerator {
    
    private ColourPalette palette;

    public JuliaImageGenerator() {
        this("default");
    }
    
    public JuliaImageGenerator(String paletteName) {
        palette = ColourPalette.fromString(paletteName, 250);
    }
    
    @Override
    public BufferedImage generateImage(double minX, double minY, double maxX, double maxY,
            int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        
        double mux = 0.36237;
        double muy = 0.32;
        
        for(int i=0;i<width; i++) {
            double x = minX + i*(maxX-minX)/width;
            for(int j=0;j<height; j++) {
                double y = minY + (height - 1 - j)*(maxY-minY)/height;
                int iterations = 0;
                double xp = x;
                double yp = y;
                while( xp * xp + yp * yp < 4 && iterations < 250) {
                    double xtemp = xp*xp - yp*yp + mux;
                    yp = 2 * xp*yp + muy;
                    xp = xtemp;
                    iterations++;
                }
                image.setRGB(i, j, palette.getColor((float) (iterations / 250.0)).getRGB());
            }
        }
        
        return image;
    }

    @Override
    public double getMinValidX() {
        return -2;
    }

    @Override
    public double getMaxValidX() {
        return 2;
    }

    @Override
    public double getMinValidY() {
        return -2;
    }

    @Override
    public double getMaxValidY() {
        return 2;
    }

}
