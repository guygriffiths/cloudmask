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

import uk.ac.rdg.resc.edal.dataset.plugins.VariablePlugin;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.metadata.Parameter;
import uk.ac.rdg.resc.edal.metadata.VariableMetadata;
import uk.ac.rdg.resc.edal.position.HorizontalPosition;

public class CompositeMaskPlugin extends VariablePlugin {

    public static final String COMPOSITEMASK = "composite-mask";
    private VariableMetadata compositeMeta;
    private String manualMaskName;

    public CompositeMaskPlugin(String manualMask) {
        super(new String[] { manualMask }, new String[] { "mask" });
        manualMaskName = manualMask;
    }

    @Override
    protected VariableMetadata[] doProcessVariableMetadata(VariableMetadata... metadata)
            throws EdalException {
        compositeMeta = newVariableMetadataFromMetadata(new Parameter(getFullId("mask"),
                "Composite mask", "Composite mask",
                "0: clear; 0.33: probably clear; 0.66: probably cloudy; 1: cloudy", null), true,
                metadata);
        return new VariableMetadata[] { compositeMeta };
    }

    @Override
    protected Number generateValue(String varSuffix, HorizontalPosition pos, Number... sourceValues) {
        /*
         * sourceValues[0] is the manual mask, and generates values:
         * 
         * null - unset
         * 
         * 0 - Clear
         * 
         * 1 - Probably clear
         * 
         * 2 - Probably cloud
         * 
         * 3 - Cloud
         */
        if (sourceValues[0] != null) {
            return sourceValues[0].floatValue() / 3.0f;
        }
        for (int i = 1; i < sourceValues.length; i++) {
            if (sourceValues[i].floatValue() > 0) {
                return 1f;
            }
        }
        return 0f;
    }

    @Override
    protected String combineIds(String... partsToUse) {
        return "composite";
    }

    public void setMasks(String... masks) {
        this.uses = new String[masks.length + 1];
        this.uses[0] = manualMaskName;
        for (int i = 1; i <= masks.length; i++) {
            this.uses[i] = masks[i - 1];
        }
        StringBuilder mComps = new StringBuilder();
        for (String comp : this.uses) {
            mComps.append(comp + ",");
        }
        mComps.deleteCharAt(mComps.length() - 1);
        compositeMeta.getVariableProperties().put("mask_components", mComps.toString());
    }
}