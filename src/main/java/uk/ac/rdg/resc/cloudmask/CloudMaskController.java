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
import java.util.Stack;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.stage.Stage;

import org.controlsfx.dialog.ExceptionDialog;

import ucar.ma2.InvalidRangeException;
import uk.ac.rdg.resc.cloudmask.CloudMaskDatasetFactory.MaskedDataset;
import uk.ac.rdg.resc.edal.dataset.plugins.VariablePlugin;
import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.exceptions.DataReadingException;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.exceptions.VariableNotFoundException;
import uk.ac.rdg.resc.edal.graphics.style.util.SimpleFeatureCatalogue;
import uk.ac.rdg.resc.edal.util.Extents;
import uk.ac.rdg.resc.edal.util.GridCoordinates2D;

public class CloudMaskController {
    private MaskedDataset activeDataset = null;

    private List<MaskedVariableView> viewWindows;
    /** The variables which can be masked */
    private ObservableList<String> maskableVariables;
    /**
     * The variables which can be plotted - this will include all maskable
     * variables + RGB images
     */
    private ObservableList<String> plottableVariables;
    private Map<String, EdalImageGenerator> dataModels;
    private Map<String, UndoRedoManager<UndoState>> undoStacks;
    private Map<String, MaskedVariableView> views;

    private final CompositeMaskView compositeMaskView;
    private Stack<PixelChange> manualMaskUndoStack;
    private Stack<PixelChange> manualMaskRedoStack;

    private SimpleFeatureCatalogue<MaskedDataset> catalogue;

    private SettingsPane settingsPane;
    
    private Stage mainStage;

    public CloudMaskController(int compositeWidth, int compositeHeight, double scale, Stage primaryStage) {
        dataModels = new HashMap<>();
        undoStacks = new HashMap<>();
        views = new HashMap<>();
        viewWindows = new ArrayList<>();
        maskableVariables = FXCollections.observableArrayList();
        plottableVariables = FXCollections.observableArrayList();
        compositeMaskView = new CompositeMaskView(compositeWidth, compositeHeight, scale, this);
        settingsPane = new SettingsPane(this);
        manualMaskUndoStack = new Stack<>();
        manualMaskRedoStack = new Stack<>();
        mainStage = primaryStage;
    }

    public ObservableList<String> getMaskableVariables() {
        return maskableVariables;
    }

    public ObservableList<String> getPlottableVariables() {
        return plottableVariables;
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
        undoStacks.clear();
        for (String var : unmaskedVariables) {
            dataModels.put(var, new EdalImageGenerator(var, catalogue));
            undoStacks.put(var, new UndoRedoManager<>(new UndoState(dataModels.get(var).scaleRange,
                    activeDataset.getMaskThreshold(var))));
        }

        /*
         * Reset list of available variables - same for both maskable and
         * plottable to start with
         */
        maskableVariables.clear();
        maskableVariables.addAll(unmaskedVariables);
        FXCollections.sort(maskableVariables);

        plottableVariables.clear();
        plottableVariables.addAll(unmaskedVariables);
        FXCollections.sort(plottableVariables);

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

        for (String used : activeDataset.getMaskedVariables()) {
            if (used.endsWith(MaskedDataset.MASK_SUFFIX)) {
                setVariableMasked(used.substring(0,
                        used.length() - 1 - MaskedDataset.MASK_SUFFIX.length()), true);
            }
        }

        settingsPane.setDatasetLoaded(activeDataset);
    }

    public void saveCurrentDataset(File selectedFile) {
        try {
            CloudMaskDatasetFactory.writeDataset(activeDataset, selectedFile.getAbsolutePath());
        } catch (VariableNotFoundException | DataReadingException | IOException
                | InvalidRangeException e) {
            ExceptionDialog exceptionDialog = new ExceptionDialog(e);
            exceptionDialog.show();
        }
    }

    public void addPlugin(VariablePlugin plugin) {
        try {
            activeDataset.addVariablePlugin(plugin);
            plottableVariables.addAll(plugin.providesVariables());
            if (!(plugin instanceof RgbFalseColourPlugin)) {
                maskableVariables.addAll(plugin.providesVariables());
                for (String var : plugin.providesVariables()) {
                    dataModels.put(var, new EdalImageGenerator(var, catalogue));
                    undoStacks.put(var, new UndoRedoManager<>(new UndoState(
                            dataModels.get(var).scaleRange, activeDataset.getMaskThreshold(var))));
                }
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
            undoStacks
                    .put(variable + MaskedDataset.MEDIAN,
                            new UndoRedoManager<>(new UndoState(
                                    dataModels.get(variable).scaleRange, activeDataset
                                            .getMaskThreshold(variable))));
            maskableVariables.add(variable + MaskedDataset.MEDIAN);
            plottableVariables.add(variable + MaskedDataset.MEDIAN);
//            maskableVariables.clear();
//            maskableVariables.addAll(activeDataset.getUnmaskedVariableNames());
        } catch (IOException | EdalException e) {
            e.printStackTrace();
        }
    }

    public void enableStddev(String variable) {
        try {
            activeDataset.enableStddev(variable);
            dataModels.put(variable + MaskedDataset.STDDEV, new EdalImageGenerator(variable
                    + MaskedDataset.STDDEV, catalogue));
            undoStacks
                    .put(variable + MaskedDataset.STDDEV,
                            new UndoRedoManager<>(new UndoState(
                                    dataModels.get(variable).scaleRange, activeDataset
                                            .getMaskThreshold(variable))));
            maskableVariables.add(variable + MaskedDataset.STDDEV);
            plottableVariables.add(variable + MaskedDataset.STDDEV);
//            maskableVariables.clear();
//            maskableVariables.addAll(activeDataset.getUnmaskedVariableNames());
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

        MaskedVariableView view = views.get(var);
        view.redrawImage();
    }

    public void setMaskOpacity(Number newVal) {
        for (EdalImageGenerator ig : dataModels.values()) {
            ig.setMaskOpacity(newVal.floatValue());
        }
        compositeMaskView.imageGenerator.setMaskOpacity(newVal.floatValue());
        compositeMaskView.imageView.updateImage();
    }

    public void maskThresholdChanged(String var, float minThreshold, float maxThreshold) {
        /*
         * Update mask values on dataset
         */
        activeDataset.setMaskThreshold(var, minThreshold, maxThreshold);
        MaskedVariableView view = views.get(var);
        view.redrawImage();
        compositeMaskView.imageView.updateJustThisImage();
    }

    public void setMaskThresholdInclusive(String var, boolean inclusive) {
        activeDataset.setMaskThresholdInclusive(var, inclusive);
        MaskedVariableView view = views.get(var);
        view.redrawImage();
        compositeMaskView.imageView.updateJustThisImage();
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

    public void addUndoState(String var) {
        UndoState undoState = new UndoState(dataModels.get(var).scaleRange,
                activeDataset.getMaskThreshold(var));
        undoStacks.get(var).setCurrentState(undoState);
    }

    public void undoLastAction(String var) {
        UndoState undo = undoStacks.get(var).undo();
        if (undo != null) {
            System.out.println("undo working");
            views.get(var).changeSliderValues(undo.colourScaleRange, undo.maskScaleRange, false);
        }
    }

    public void redoLastAction(String var) {
        UndoState redo = undoStacks.get(var).redo();
        if (redo != null) {
            views.get(var).changeSliderValues(redo.colourScaleRange, redo.maskScaleRange, false);
        }
    }

    public void undoLastManualEdit() {
        if (!manualMaskUndoStack.isEmpty()) {
            PixelChange undo = manualMaskUndoStack.pop();
            setPixelOn(undo.coords, undo.fromValue, false);
            manualMaskRedoStack.push(undo);
        }
    }

    public void redoLastManualEdit() {
        if (!manualMaskRedoStack.isEmpty()) {
            PixelChange redo = manualMaskRedoStack.pop();
            setPixelOn(redo.coords, redo.toValue, false);
            manualMaskUndoStack.push(redo);
        }
    }

    public void setPixelOn(GridCoordinates2D imageCoords, Integer value) {
        setPixelOn(imageCoords, value, true);
    }

    public void setPixelOn(GridCoordinates2D imageCoords, Integer value, boolean saveState) {
        Number oldValue = activeDataset.getManualMask().get(imageCoords.getY(), imageCoords.getX());
        if (oldValue == null) {
            if (value == null) {
                return;
            }
        } else {
            if (oldValue.equals(value)) {
                return;
            }
        }

        if (saveState) {
            PixelChange pixelChange = new PixelChange(imageCoords, oldValue == null ? null
                    : oldValue.intValue(), value);
            manualMaskUndoStack.push(pixelChange);
            manualMaskRedoStack.clear();
        }
        activeDataset.setManualMask(imageCoords, value);
        catalogue.expireFromCache(CompositeMaskPlugin.COMPOSITEMASK);
        catalogue.expireFromCache(MaskedDataset.MANUAL_MASK_NAME);
        compositeMaskView.imageView.updateJustThisImage();
    }
    
    public void toggleFullscreen() {
        mainStage.setFullScreen(!mainStage.isFullScreen());
    }

    public void quit() {
        mainStage.close();
    }

    private class UndoState {
        private Extent<Float> colourScaleRange;
        private Extent<Double> maskScaleRange;

        public UndoState(Extent<Float> colourScaleRange, Extent<Double> maskScaleRange) {
            this.colourScaleRange = colourScaleRange;
            this.maskScaleRange = maskScaleRange;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result
                    + ((colourScaleRange == null) ? 0 : colourScaleRange.hashCode());
            result = prime * result + ((maskScaleRange == null) ? 0 : maskScaleRange.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            UndoState other = (UndoState) obj;
            if (!getOuterType().equals(other.getOuterType()))
                return false;
            if (colourScaleRange == null) {
                if (other.colourScaleRange != null)
                    return false;
            } else if (!colourScaleRange.equals(other.colourScaleRange))
                return false;
            if (maskScaleRange == null) {
                if (other.maskScaleRange != null)
                    return false;
            } else if (!maskScaleRange.equals(other.maskScaleRange))
                return false;
            return true;
        }

        private CloudMaskController getOuterType() {
            return CloudMaskController.this;
        }
    }

    private class PixelChange {
        GridCoordinates2D coords;
        Integer fromValue;
        Integer toValue;

        public PixelChange(GridCoordinates2D coords, Integer fromValue, Integer toValue) {
            super();
            this.coords = coords;
            this.fromValue = fromValue;
            this.toValue = toValue;
        }

        @Override
        public String toString() {
            return "Changing " + coords + " from " + fromValue + " to " + toValue;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + ((coords == null) ? 0 : coords.hashCode());
            result = prime * result + ((fromValue == null) ? 0 : fromValue.hashCode());
            result = prime * result + ((toValue == null) ? 0 : toValue.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            PixelChange other = (PixelChange) obj;
            if (!getOuterType().equals(other.getOuterType()))
                return false;
            if (coords == null) {
                if (other.coords != null)
                    return false;
            } else if (!coords.equals(other.coords))
                return false;
            if (fromValue == null) {
                if (other.fromValue != null)
                    return false;
            } else if (!fromValue.equals(other.fromValue))
                return false;
            if (toValue == null) {
                if (other.toValue != null)
                    return false;
            } else if (!toValue.equals(other.toValue))
                return false;
            return true;
        }

        private CloudMaskController getOuterType() {
            return CloudMaskController.this;
        }
    }
}