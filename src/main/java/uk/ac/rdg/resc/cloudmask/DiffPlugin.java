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
import uk.ac.rdg.resc.edal.metadata.GridVariableMetadata;
import uk.ac.rdg.resc.edal.metadata.Parameter;
import uk.ac.rdg.resc.edal.metadata.VariableMetadata;
import uk.ac.rdg.resc.edal.position.HorizontalPosition;

class DiffPlugin extends VariablePlugin {

    private static final String DIFF = "diff";

    public DiffPlugin(String xVar, String yVar) {
        super(new String[] { xVar, yVar }, new String[] { DIFF });
    }

    @Override
    protected VariableMetadata[] doProcessVariableMetadata(VariableMetadata... metadata)
            throws EdalException {
        GridVariableMetadata xMeta = (GridVariableMetadata) metadata[0];
        GridVariableMetadata yMeta = (GridVariableMetadata) metadata[1];

        VariableMetadata diffMeta = newVariableMetadataFromMetadata(new Parameter(
                getFullId(DIFF), xMeta.getParameter().getTitle() + " - "
                        + yMeta.getParameter().getTitle(),
                "Difference between variables " + xMeta.getId() + " and "
                        + yMeta.getId(), xMeta.getParameter().getUnits(), null), true, xMeta,
                yMeta);
        diffMeta.setParent(xMeta.getParent(), null);
        return new VariableMetadata[] { diffMeta };
    }

    @Override
    protected Number generateValue(String varSuffix, HorizontalPosition pos,
            Number... sourceValues) {
        return sourceValues[0].doubleValue() - sourceValues[1].doubleValue();
    }
}