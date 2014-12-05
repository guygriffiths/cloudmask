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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import uk.ac.rdg.resc.cloudmask.widgets.ZoomableImageView.ImageGenerator;

public class TestImageGenerator implements ImageGenerator {
    @Override
    public BufferedImage generateImage(double minX, double minY, double maxX, double maxY,
            int width, int height) {
//        try {
//            Thread.sleep(500L);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < width; i++) {
            float percentageAlongX = (float) (minX + i * (maxX - minX) / (width - 1));
            for (int j = 0; j < height; j++) {
                float percentageAlongY = (float) (minY + j * (maxY - minY) / height);
                if (percentageAlongX <= 100 && percentageAlongX >= 0 && percentageAlongY <= 100
                        && percentageAlongY >= 0) {
                    int rgb = new Color(25 * (int) (percentageAlongX / 10.0f), 0,
                            25 * (int) (percentageAlongY / 10.0f)).getRGB();
                    image.setRGB(i, height-j-1, rgb);
                } else {
                    image.setRGB(i, j, 0);
                }
            }
        }
        
        Graphics2D g = image.createGraphics();
        String ll = (int)(minX + (maxX - minX/4.0)) + ","+ (int)(minY + (maxY - minY/4.0));
        g.drawString(ll, width/4, 3*height/4);
        String lr = (int)(minX + 3*(maxX - minX/4.0)) + ","+ (int)(minY + (maxY - minY/4.0));
        g.drawString(lr, 3*width/4, 3*height/4);
        String ur = (int)(minX + 3*(maxX - minX/4.0)) + ","+ (int)(minY + 3*(maxY - minY/4.0));
        g.drawString(ur, 3*width/4, height/4);
        String ul = (int)(minX + (maxX - minX/4.0)) + ","+ (int)(minY + 3*(maxY - minY/4.0));
        g.drawString(ul, width/4, height/4);
//        System.out.println("generated "+(count++));
        return image;
    }

    @Override
    public double getMinValidX() {
        return 0;
    }

    @Override
    public double getMaxValidX() {
        return 100;
    }

    @Override
    public double getMinValidY() {
        return 0;
    }

    @Override
    public double getMaxValidY() {
        return 100;
    }

}
