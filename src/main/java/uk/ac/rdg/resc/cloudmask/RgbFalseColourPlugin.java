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

import uk.ac.rdg.resc.edal.dataset.plugins.VariablePlugin;
import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.metadata.GridVariableMetadata;
import uk.ac.rdg.resc.edal.metadata.Parameter;
import uk.ac.rdg.resc.edal.metadata.VariableMetadata;
import uk.ac.rdg.resc.edal.position.HorizontalPosition;

public class RgbFalseColourPlugin extends VariablePlugin {
    public static final String RGB = "rgb";
    private Extent<Float> rScaleRange;
    private Extent<Float> gScaleRange;
    private Extent<Float> bScaleRange;

    public RgbFalseColourPlugin(String rVar, String gVar, String bVar, Extent<Float> rScaleRange,
            Extent<Float> gScaleRange, Extent<Float> bScaleRange) {
        super(new String[] { rVar, gVar, bVar }, new String[] { RGB });
        this.rScaleRange = rScaleRange;
        this.gScaleRange = gScaleRange;
        this.bScaleRange = bScaleRange;
    }

    @Override
    protected VariableMetadata[] doProcessVariableMetadata(VariableMetadata... metadata)
            throws EdalException {
        GridVariableMetadata rMeta = (GridVariableMetadata) metadata[0];
        GridVariableMetadata gMeta = (GridVariableMetadata) metadata[1];
        GridVariableMetadata bMeta = (GridVariableMetadata) metadata[2];

        VariableMetadata rgbMeta = newVariableMetadataFromMetadata(new Parameter(getFullId(RGB),
                "RGB False Colour Image", "False colour image using variables R: " + rMeta.getId()
                        + ", G: " + gMeta.getId() + ", B: " + bMeta.getId(), "rgbint", null), true,
                rMeta, gMeta, bMeta);
        rgbMeta.setParent(rMeta.getParent(), null);
        return new VariableMetadata[] { rgbMeta };
    }

    @Override
    protected Number generateValue(String varSuffix, HorizontalPosition pos, Number... sourceValues) {
        float r = (sourceValues[0].floatValue() - rScaleRange.getLow())
                / (rScaleRange.getHigh() - rScaleRange.getLow());
        if (r < 0.0)
            r = 0.0f;
        if (r > 1.0)
            r = 1.0f;
        float g = (sourceValues[1].floatValue() - gScaleRange.getLow())
                / (gScaleRange.getHigh() - gScaleRange.getLow());
        if (g < 0.0)
            g = 0.0f;
        if (g > 1.0)
            g = 1.0f;
        float b = (sourceValues[2].floatValue() - bScaleRange.getLow())
                / (bScaleRange.getHigh() - bScaleRange.getLow());
        if (b < 0.0)
            b = 0.0f;
        if (b > 1.0)
            b = 1.0f;
        return new Color(r, g, b).getRGB();
    }
}
