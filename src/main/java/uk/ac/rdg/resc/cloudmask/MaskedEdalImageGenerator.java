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

import java.awt.Color;
import java.io.IOException;

import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.graphics.style.ColourScale;
import uk.ac.rdg.resc.edal.graphics.style.RasterLayer;
import uk.ac.rdg.resc.edal.graphics.style.SegmentColourScheme;
import uk.ac.rdg.resc.edal.graphics.style.util.GraphicsUtils;
import uk.ac.rdg.resc.edal.graphics.style.util.SimpleFeatureCatalogue;

public class MaskedEdalImageGenerator extends EdalImageGenerator {
    private Color maskColor = new Color(0, 0, 0, 150);
    private RasterLayer threshold;

    public MaskedEdalImageGenerator(String var, SimpleFeatureCatalogue<?> catalogue)
            throws IOException, EdalException {
        this(var, catalogue, GraphicsUtils.estimateValueRange(catalogue.getDataset(), var));
    }

    public MaskedEdalImageGenerator(String var, SimpleFeatureCatalogue<?> catalogue,
            Extent<Float> scaleRange) throws IOException, EdalException {
        super(var, catalogue, scaleRange);
        setRasterLayer(var);
    }
    
    @Override
    public void setVariable(String var, Extent<Float> scaleRange) throws EdalException {
        super.setVariable(var, scaleRange);
        image.getLayers().remove(threshold);
        setRasterLayer(var);
    }
    
    private void setRasterLayer(String var) {
        threshold = new RasterLayer(var + "-" + MaskedDatasetFactory.MASK_SUFFIX,
                new SegmentColourScheme(new ColourScale(0f, 1f, false), null, null, null,
                        "#00000000,"+GraphicsUtils.colourToString(maskColor), 2));
        image.getLayers().add(threshold);
    }

}
