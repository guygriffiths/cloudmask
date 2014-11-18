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

package uk.ac.rdg.resc.edal.dataset.cdm;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.dataset.DatasetFactory;
import uk.ac.rdg.resc.edal.dataset.plugins.VariablePlugin;
import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.exceptions.DataReadingException;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.exceptions.VariableNotFoundException;
import uk.ac.rdg.resc.edal.feature.DiscreteFeature;
import uk.ac.rdg.resc.edal.feature.Feature;
import uk.ac.rdg.resc.edal.feature.PointSeriesFeature;
import uk.ac.rdg.resc.edal.feature.ProfileFeature;
import uk.ac.rdg.resc.edal.graphics.style.util.GraphicsUtils;
import uk.ac.rdg.resc.edal.metadata.Parameter;
import uk.ac.rdg.resc.edal.metadata.VariableMetadata;
import uk.ac.rdg.resc.edal.position.HorizontalPosition;
import uk.ac.rdg.resc.edal.util.PlottingDomainParams;

public class MaskedDatasetFactory extends DatasetFactory {

    private final static String MASK_SUFFIX = "MASK";
    
    private DatasetFactory parentDatasetFactory;

    public MaskedDatasetFactory(DatasetFactory parentDatasetFactory) {
        this.parentDatasetFactory = parentDatasetFactory;
    }

    @Override
    public MaskedDataset createDataset(String id, String location) throws IOException,
            EdalException {
        Dataset dataset = parentDatasetFactory.createDataset(id, location);
        return new MaskedDataset(dataset);
    }
    
    

    /**
     * Wraps an existing {@link Dataset} object and adds {@link VariablePlugin}s
     * to each variable to provide a thresholded mask. Then exposes methods to
     * change the thresholds of the masks for individual variables.
     *
     * @author Guy Griffiths
     */
    public class MaskedDataset implements Dataset {
        private final Dataset ds;
        private Map<String, ThresholdMaskPlugin> thresholds;
        private ObservableList<String> unmaskedVariables;

        public MaskedDataset(Dataset dataset) throws EdalException {
            this.ds = dataset;
            thresholds = new HashMap<>();
            unmaskedVariables = FXCollections.observableArrayList(ds.getVariableIds());

            Set<String> variableIds = new HashSet<>(dataset.getVariableIds());
            for (String var : variableIds) {
                addMaskToVariable(var);
            }
        }

        @Override
        public void addVariablePlugin(VariablePlugin plugin) throws EdalException {
            ds.addVariablePlugin(plugin);
            for (String newVar : plugin.providesVariables()) {
                unmaskedVariables.add(newVar);
                addMaskToVariable(newVar);
            }
        }

        private void addMaskToVariable(String varId) throws EdalException {
            Extent<Float> valueRange = GraphicsUtils.estimateValueRange(ds, varId);
            ThresholdMaskPlugin thresholdPlugin = new ThresholdMaskPlugin(varId,
                    valueRange.getLow(), valueRange.getHigh());
            ds.addVariablePlugin(thresholdPlugin);
            thresholds.put(varId, thresholdPlugin);
        }

        public void setMaskThreshold(String varId, double min, double max) {
            thresholds.get(varId).setThreshold(min, max);
        }
        
        public ObservableList<String> getUnmaskedVariableNames() {
            return unmaskedVariables;
        }
        
        public String getMaskedVariableName(String varId) throws VariableNotFoundException {
            if(ds.getVariableIds().contains(varId) && !thresholds.containsKey(varId)) {
                return varId+"-"+MASK_SUFFIX;
            } else {
                throw new VariableNotFoundException(varId);
            }
        }

        /*
         * All following methods just wrap the Dataset object which this is
         * based upon.
         */

        @Override
        public String getId() {
            return ds.getId();
        }

        @Override
        public Set<String> getFeatureIds() {
            return ds.getFeatureIds();
        }

        @Override
        public Class<? extends DiscreteFeature<?, ?>> getFeatureType(String variableId) {
            return ds.getFeatureType(variableId);
        }

        @Override
        public Feature<?> readFeature(String featureId) throws DataReadingException, VariableNotFoundException {
            return ds.readFeature(featureId);
        }

        @Override
        public Set<String> getVariableIds() {
            return ds.getVariableIds();
        }

        @Override
        public VariableMetadata getVariableMetadata(String variableId) throws VariableNotFoundException {
            return ds.getVariableMetadata(variableId);
        }

        @Override
        public Set<VariableMetadata> getTopLevelVariables() {
            return ds.getTopLevelVariables();
        }

        @Override
        public Class<? extends DiscreteFeature<?, ?>> getMapFeatureType(String variableId) {
            return ds.getFeatureType(variableId);
        }

        @Override
        public List<? extends DiscreteFeature<?, ?>> extractMapFeatures(Set<String> varIds,
                PlottingDomainParams params) throws DataReadingException, VariableNotFoundException {
            return ds.extractMapFeatures(varIds, params);
        }

        @Override
        public List<? extends ProfileFeature> extractProfileFeatures(Set<String> varIds,
                PlottingDomainParams params) throws DataReadingException,
                UnsupportedOperationException, VariableNotFoundException {
            return ds.extractProfileFeatures(varIds, params);
        }

        @Override
        public boolean supportsProfileFeatureExtraction(String varId) {
            return ds.supportsProfileFeatureExtraction(varId);
        }

        @Override
        public List<? extends PointSeriesFeature> extractTimeseriesFeatures(Set<String> varIds,
                PlottingDomainParams params) throws DataReadingException,
                UnsupportedOperationException, VariableNotFoundException {
            return ds.extractTimeseriesFeatures(varIds, params);
        }

        @Override
        public boolean supportsTimeseriesExtraction(String varId) {
            return ds.supportsTimeseriesExtraction(varId);
        }
    }

    /**
     * A {@link VariablePlugin} which masks data based on 2 threshold values
     *
     * @author Guy Griffiths
     */
    private class ThresholdMaskPlugin extends VariablePlugin {
        /*
         * TODO add boolean as to which way mask applies
         */
        private double min;
        private double max;

        public ThresholdMaskPlugin(String var, double min, double max) {
            super(new String[] { var }, new String[] { MASK_SUFFIX });
            setThreshold(min, max);
        }

        public void setThreshold(double min, double max) {
            this.min = min;
            this.max = max;
        }

        @Override
        protected VariableMetadata[] doProcessVariableMetadata(VariableMetadata... metadata)
                throws EdalException {
            VariableMetadata meta = metadata[0];

            VariableMetadata diffMeta = newVariableMetadataFromMetadata(
                    new Parameter(getFullId(MASK_SUFFIX), meta.getParameter().getTitle() + " Mask",
                            "Mask of " + meta.getParameter().getDescription(),
                            "0: unmasked, 1: masked", null), true, meta);
            diffMeta.setParent(meta.getParent(), null);
            return new VariableMetadata[] { diffMeta };
        }

        @Override
        protected Number generateValue(String varSuffix, HorizontalPosition pos,
                Number... sourceValues) {
            if (sourceValues[0].doubleValue() <= min || sourceValues[0].doubleValue() >= max)
                return 0;
            return 1;
        }
    }
}
