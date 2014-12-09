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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import uk.ac.rdg.resc.cloudmask.CloudMaskDatasetFactory.MaskedDataset;
import uk.ac.rdg.resc.edal.dataset.plugins.VariablePlugin;
import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.graphics.style.util.SimpleFeatureCatalogue;
import uk.ac.rdg.resc.edal.util.Extents;

public class CloudMaskController {
    private MaskedDataset activeDataset = null;

    private List<MaskedVariableView> viewWindows;
    private ObservableList<String> availableVariables;
    private Map<String, EdalImageGenerator> dataModels;
    private Map<String, MaskedVariableView> views;

    private final CompositeMaskView compositeMaskView;

    private SimpleFeatureCatalogue<MaskedDataset> catalogue;

    private SettingsPane settingsPane;

    public CloudMaskController(int compositeWidth, int compositeHeight) {
        dataModels = new HashMap<>();
        views = new HashMap<>();
        viewWindows = new ArrayList<>();
        availableVariables = FXCollections.observableArrayList();
        compositeMaskView = new CompositeMaskView(compositeWidth, compositeHeight, this);
        settingsPane = new SettingsPane(this);
    }

    public ObservableList<String> getAvailableVariables() {
        return availableVariables;
    }

    public void addMaskedVariableView(MaskedVariableView view) {
        viewWindows.add(view);
    }

    public CompositeMaskView getCompositeMaskView() {
        return compositeMaskView;
    }

    public SettingsPane getSettingsPane() {
        return settingsPane;
    }

    public MaskedDataset getDataset() {
        return activeDataset;
    }

    public void loadDataset(File datasetLocation) throws IOException, EdalException {
        /*
         * Store dataset
         */
        CloudMaskDatasetFactory mdf = new CloudMaskDatasetFactory();
        activeDataset = mdf.createDataset(datasetLocation.getName(),
                datasetLocation.getAbsolutePath());
        catalogue = new SimpleFeatureCatalogue<>(activeDataset, true);
        ObservableList<String> unmaskedVariables = activeDataset.getUnmaskedVariableNames();

        compositeMaskView.setCatalogue(catalogue);

        /*
         * Clear data models & repopulate with new ones
         */
        dataModels.clear();
        for (String var : unmaskedVariables) {
            dataModels.put(var, new EdalImageGenerator(var, catalogue));
        }

        /*
         * Reset list of available variables
         */
        availableVariables.clear();
        availableVariables.addAll(unmaskedVariables);
        FXCollections.sort(availableVariables);

        /*
         * Go through list of available variables, sending one to each view in
         * turn until we run out of views or variables
         */
        List<String> varsToSet = new ArrayList<>();
        int i = 0;
        for (String var : unmaskedVariables) {
            if (i++ < viewWindows.size()) {
                varsToSet.add(var);
            } else {
                break;
            }
        }
        i = 0;
        for (String var : varsToSet) {
            setVariable(viewWindows.get(i++), var);
        }

        settingsPane.setDatasetLoaded(activeDataset);
    }

    public void addPlugin(VariablePlugin plugin) {
        try {
            activeDataset.addVariablePlugin(plugin);
            availableVariables.addAll(plugin.providesVariables());
//            FXCollections.sort(availableVariables);
            for (String var : plugin.providesVariables()) {
                dataModels.put(var, new EdalImageGenerator(var, catalogue));
            }
        } catch (EdalException | IOException e) {
            e.printStackTrace();
        }
    }

    public void enableMedian(String variable) {
        try {
            activeDataset.enableMedian(variable);
            dataModels.put(variable + MaskedDataset.MEDIAN, new EdalImageGenerator(variable
                    + MaskedDataset.MEDIAN, catalogue));
            availableVariables.clear();
            availableVariables.addAll(activeDataset.getUnmaskedVariableNames());
//            FXCollections.sort(availableVariables);
        } catch (IOException | EdalException e) {
            e.printStackTrace();
        }
    }

    public void enableStddev(String variable) {
        try {
            activeDataset.enableStddev(variable);
            dataModels.put(variable + MaskedDataset.STDDEV, new EdalImageGenerator(variable
                    + MaskedDataset.STDDEV, catalogue));
            availableVariables.clear();
            availableVariables.addAll(activeDataset.getUnmaskedVariableNames());
//            FXCollections.sort(availableVariables);
        } catch (IOException | EdalException e) {
            e.printStackTrace();
        }
    }

    public void setVariable(MaskedVariableView view, String newVar) {
        /*
         * Get model state for new variable
         */
        EdalImageGenerator imageGenerator = dataModels.get(newVar);

        /*
         * Remove mapping of old variable to view (if it exists)
         */
        String oldVar = view.getCurrentVariable();

        if (views.containsKey(newVar)) {
            /*
             * We already have a view showing this variable.
             * 
             * Swap the variables
             */
            MaskedVariableView viewToSwap = views.get(newVar);
            EdalImageGenerator swapImageGenerator = dataModels.get(oldVar);
            views.put(oldVar, viewToSwap);
            viewToSwap.newModelSelected(swapImageGenerator);
            viewToSwap.redrawImage();
        } else {
            if (oldVar != null) {
                views.remove(oldVar);
            }
        }

        /*
         * Update map of variables to views
         */
        views.put(newVar, view);

        /*
         * Send new model to view
         */
        view.newModelSelected(imageGenerator);
        view.redrawImage();
    }

    public boolean isVariableActive(String var) {
        return views.containsKey(var);
    }

    public void colourScaleChanged(String var, float minScale, float maxScale) {
        /*
         * Update model state
         */
        EdalImageGenerator modelState = dataModels.get(var);
        Extent<Float> scaleRange = Extents.newExtent(minScale, maxScale);
        modelState.setScaleRange(scaleRange);

        MaskedVariableView view = views.get(var);
        view.setMaskSliderLimits(scaleRange);
        view.redrawImage();
    }

    public void paletteChanged(String var, String palette) {
        /*
         * Update model state
         */
        EdalImageGenerator modelState = dataModels.get(var);
        modelState.setPalette(palette);
        ;

        MaskedVariableView view = views.get(var);
        view.redrawImage();
    }

    public void maskThresholdChanged(String var, float minThreshold, float maxThreshold) {
        /*
         * Update mask values on dataset
         */
        activeDataset.setMaskThreshold(var, minThreshold, maxThreshold);
        MaskedVariableView view = views.get(var);
        view.redrawImage();
    }

    public void setMaskThresholdInclusive(String var, boolean inclusive) {
        activeDataset.setMaskThresholdInclusive(var, inclusive);
        MaskedVariableView view = views.get(var);
        view.redrawImage();
    }

    public void setVariableMasked(String variable, boolean masked) {
        if (masked) {
            compositeMaskView.addToMask(variable);
        } else {
            compositeMaskView.removeFromMask(variable);
        }
    }
    
    public boolean isVariableInComposite(String variable) {
        return compositeMaskView.isVariableIncluded(variable);
    }

    public void setMaskedVariables(String[] mask) {
        catalogue.expireFromCache(CompositeMaskPlugin.COMPOSITEMASK);
        activeDataset.setMaskedVariables(mask);

        List<String> maskedVariables = Arrays.asList(mask);
        for (Entry<String, MaskedVariableView> viewEntry : views.entrySet()) {
            MaskedVariableView view = viewEntry.getValue();
            view.setIncludedInMask(maskedVariables.contains(viewEntry.getKey() + "-"
                    + MaskedDataset.MASK_SUFFIX));
        }
        compositeMaskView.imageView.updateImage();
    }
}