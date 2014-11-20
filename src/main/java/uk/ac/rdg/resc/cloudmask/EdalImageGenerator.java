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

import uk.ac.rdg.resc.cloudmask.ZoomableImageView.ImageGenerator;
import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.geometry.BoundingBoxImpl;
import uk.ac.rdg.resc.edal.graphics.style.ColourScale;
import uk.ac.rdg.resc.edal.graphics.style.MapImage;
import uk.ac.rdg.resc.edal.graphics.style.RasterLayer;
import uk.ac.rdg.resc.edal.graphics.style.SegmentColourScheme;
import uk.ac.rdg.resc.edal.graphics.style.util.GraphicsUtils;
import uk.ac.rdg.resc.edal.graphics.style.util.SimpleFeatureCatalogue;
import uk.ac.rdg.resc.edal.metadata.GridVariableMetadata;
import uk.ac.rdg.resc.edal.util.PlottingDomainParams;

public class EdalImageGenerator implements ImageGenerator {
    private final int xSize;
    private final int ySize;
    protected MapImage image;
    protected SimpleFeatureCatalogue<?> catalogue;
    protected Extent<Float> scaleRange;
    private RasterLayer rasterLayer;

    public EdalImageGenerator(String var, SimpleFeatureCatalogue<?> catalogue) throws IOException,
            EdalException {
        this(var, catalogue, GraphicsUtils.estimateValueRange(catalogue.getDataset(), var));
    }

    public EdalImageGenerator(String var, SimpleFeatureCatalogue<?> catalogue,
            Extent<Float> scaleRange) throws IOException, EdalException {
        GridVariableMetadata variableMetadata = (GridVariableMetadata) catalogue.getDataset()
                .getVariableMetadata(var);
        xSize = variableMetadata.getHorizontalDomain().getXSize();
        ySize = variableMetadata.getHorizontalDomain().getYSize();
        this.catalogue = catalogue;
        this.scaleRange = scaleRange;
        rasterLayer = new RasterLayer(var, new SegmentColourScheme(new ColourScale(scaleRange,
                false), null, null, null, "seq-cubeYF", 250));
        image = new MapImage();
        image.getLayers().add(rasterLayer);
    }
    
    public void setVariable(String var) throws EdalException {
        this.setVariable(var, GraphicsUtils.estimateValueRange(catalogue.getDataset(), var));
    }

    public void setVariable(String var, Extent<Float> scaleRange) throws EdalException {
        GridVariableMetadata variableMetadata = (GridVariableMetadata) catalogue.getDataset()
                .getVariableMetadata(var);
        if (xSize != variableMetadata.getHorizontalDomain().getXSize()
                || ySize != variableMetadata.getHorizontalDomain().getYSize()) {
            throw new EdalException(
                    "Cannot set this variable - must have the same grid size as existing variable");
        }
        this.scaleRange = scaleRange;
        image.getLayers().remove(rasterLayer);
        rasterLayer = new RasterLayer(var, new SegmentColourScheme(new ColourScale(scaleRange,
                false), null, null, null, "seq-cubeYF", 250));
        image.getLayers().add(0, rasterLayer);
    }

    public Extent<Float> getScaleRange() {
        return scaleRange;
    }

    @Override
    public BufferedImage generateImage(double minX, double minY, double maxX, double maxY,
            int width, int height) {
        try {
            PlottingDomainParams params = new PlottingDomainParams(width, height,
                    new BoundingBoxImpl(minX, minY, maxX, maxY, null), null, null, null, null, null);
            BufferedImage drawImage = image.drawImage(params, catalogue);
            return drawImage;
        } catch (EdalException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public double getMinValidX() {
        return 0;
    }

    @Override
    public double getMaxValidX() {
        return xSize - 1;
    }

    @Override
    public double getMinValidY() {
        return 0;
    }

    @Override
    public double getMaxValidY() {
        return ySize - 1;
    }

}
