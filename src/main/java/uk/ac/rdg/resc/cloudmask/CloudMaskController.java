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
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;
import java.util.function.Predicate;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import org.controlsfx.dialog.ExceptionDialog;

import uk.ac.rdg.resc.cloudmask.CloudMaskDatasetFactory.MaskedDataset;
import uk.ac.rdg.resc.edal.dataset.plugins.VariablePlugin;
import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.exceptions.DataReadingException;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.exceptions.VariableNotFoundException;
import uk.ac.rdg.resc.edal.graphics.utils.SimpleFeatureCatalogue;
import uk.ac.rdg.resc.edal.position.HorizontalPosition;
import uk.ac.rdg.resc.edal.util.Extents;
import uk.ac.rdg.resc.edal.util.GridCoordinates2D;

public class CloudMaskController {
    private MaskedDataset activeDataset = null;

    private List<MaskedVariableView> viewWindows;
    /** The variables which can be masked */
    private FilteredList<MaskVariable> maskableVariables;
    /** The variables which are included in the composite */
    private ObservableList<String> includedVariables;
    /**
     * The variables which can be plotted - this will include all maskable
     * variables + RGB images
     */
    private ObservableList<MaskVariable> plottableVariables;
    private Map<String, EdalImageGenerator> dataModels;
    private Map<String, UndoRedoManager<UndoState>> undoStacks;
    private Map<String, MaskedVariableView> views;

    private final CompositeMaskView compositeMaskView;
    private Stack<List<PixelChange>> manualMaskUndoStack;
    private Stack<List<PixelChange>> manualMaskRedoStack;

    private SimpleFeatureCatalogue<MaskedDataset> catalogue;

    private SettingsPane settingsPane;

    private Stage mainStage;

    /*
     * This doesn't necessarily represent a change in the data which would be
     * saved, but rather the possibility that this has occurred. False positives
     * are not a problem, and it's usually more efficient to just set the value
     * then check in detail.
     */
    private boolean changedSinceLastSave = false;

    public CloudMaskController(int compositeWidth, int compositeHeight, double scale,
            Stage primaryStage) {
        dataModels = new HashMap<>();
        undoStacks = new HashMap<>();
        views = new HashMap<>();
        viewWindows = new ArrayList<>();
        plottableVariables = FXCollections.observableArrayList();
        maskableVariables = new FilteredList<>(plottableVariables, new Predicate<MaskVariable>() {
            @Override
            public boolean test(MaskVariable t) {
                return t.maskable.get();
            }
        });
        compositeMaskView = new CompositeMaskView(compositeWidth, compositeHeight, scale, this);
        settingsPane = new SettingsPane(this);
        manualMaskUndoStack = new Stack<>();
        manualMaskRedoStack = new Stack<>();
        mainStage = primaryStage;
    }

    public ObservableList<MaskVariable> getMaskableVariables() {
        return maskableVariables;
    }

    public ObservableList<MaskVariable> getPlottableVariables() {
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
                datasetLocation.getAbsolutePath(), false);
        catalogue = new SimpleFeatureCatalogue<>(activeDataset, true);
        ObservableList<String> unmaskedVariables = activeDataset.getUnmaskedVariableNames();

        compositeMaskView.setCatalogue(catalogue);

        includedVariables = FXCollections.observableArrayList();

        /*
         * Clear data models & repopulate with new ones
         */
        dataModels.clear();
        undoStacks.clear();
        plottableVariables.clear();
        for (String var : unmaskedVariables) {
            dataModels.put(var, new EdalImageGenerator(var, catalogue));
            undoStacks.put(var, new UndoRedoManager<>(new UndoState(dataModels.get(var).scaleRange,
                    activeDataset.getMaskThreshold(var))));
            boolean included = false;
            for (String testMasked : activeDataset.getMaskedVariables()) {
                if (var.equalsIgnoreCase(testMasked)) {
                    included = true;
                    includedVariables.add(testMasked);
                    break;
                }
            }
            plottableVariables.add(new MaskVariable(var, true, included, null));
        }
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
                String varName = used.substring(0,
                        used.length() - 1 - MaskedDataset.MASK_SUFFIX.length());
                for (MaskVariable v : plottableVariables) {
                    if (v.variableName.getValue().equals(varName)) {
                        v.includedInComposite.setValue(true);
                    }
                }
            }
        }

        includedVariables.addListener(new ListChangeListener<String>() {
            @Override
            public void onChanged(
                    javafx.collections.ListChangeListener.Change<? extends String> change) {
                /*
                 * When the list of included variables changes, we need to send
                 * this to the dataset and update the composite image
                 */
                catalogue.expireFromCache(CompositeMaskPlugin.COMPOSITEMASK);
                String[] mask = new String[includedVariables.size()];
                for (int i = 0; i < includedVariables.size(); i++) {
                    String maskedVar = includedVariables.get(i);
                    mask[i] = maskedVar + "-" + MaskedDataset.MASK_SUFFIX;
                }
                activeDataset.setMaskedVariables(mask);
                compositeMaskView.imageView.updateImage();
            }
        });

        settingsPane.setDatasetLoaded(activeDataset);
    }

    public void saveCurrentDataset(File selectedFile) {
        try {
            CloudMaskDatasetFactory.writeDataset(activeDataset, selectedFile.getAbsolutePath());
            changedSinceLastSave = false;
        } catch (Throwable e) {
            ExceptionDialog exceptionDialog = new ExceptionDialog(e);
            exceptionDialog.show();
        }
    }

    public void addPlugin(VariablePlugin plugin) {
        try {
            activeDataset.addVariablePlugin(plugin);
            for (String var : plugin.providesVariables()) {
                dataModels.put(var, new EdalImageGenerator(var, catalogue));
                undoStacks.put(var, new UndoRedoManager<>(new UndoState(
                        dataModels.get(var).scaleRange, activeDataset.getMaskThreshold(var))));
                MaskVariable variable = new MaskVariable(var,
                        !(plugin instanceof RgbFalseColourPlugin), false, null);
                plottableVariables.add(variable);
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
            plottableVariables.add(new MaskVariable(variable + MaskedDataset.MEDIAN, true, false,
                    null));
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
            plottableVariables.add(new MaskVariable(variable + MaskedDataset.STDDEV, true, false,
                    null));
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
        changedSinceLastSave = true;
    }

    public void setMaskThresholdInclusive(String var, boolean inclusive) {
        activeDataset.setMaskThresholdInclusive(var, inclusive);
        MaskedVariableView view = views.get(var);
        view.redrawImage();
        compositeMaskView.imageView.updateJustThisImage();
        changedSinceLastSave = true;
    }

    public BooleanProperty variableInMaskProperty(String currentVariable) {
        for (MaskVariable v : plottableVariables) {
            if (v.variableName.getValue().equals(currentVariable)) {
                return v.includedInComposite;
            }
        }
        return null;
    }

    public void addUndoState(String var) {
        UndoState undoState = new UndoState(dataModels.get(var).scaleRange,
                activeDataset.getMaskThreshold(var));
        undoStacks.get(var).setCurrentState(undoState);
        changedSinceLastSave = true;
    }

    public void undoLastAction(String var) {
        UndoState undo = undoStacks.get(var).undo();
        if (undo != null) {
            views.get(var).changeSliderValues(undo.colourScaleRange, undo.maskScaleRange, false);
            changedSinceLastSave = true;
        }
    }

    public void redoLastAction(String var) {
        UndoState redo = undoStacks.get(var).redo();
        if (redo != null) {
            views.get(var).changeSliderValues(redo.colourScaleRange, redo.maskScaleRange, false);
            changedSinceLastSave = true;
        }
    }

    public void undoLastManualEdit() {
        if (!manualMaskUndoStack.isEmpty()) {
            List<PixelChange> undos = manualMaskUndoStack.pop();
            for (PixelChange undo : undos) {
                activeDataset.setManualMaskPixel(undo.coords.getX(), undo.coords.getY(),
                        undo.fromValue, undo.toValue);
            }
            manualMaskRedoStack.push(undos);
            catalogue.expireFromCache(CompositeMaskPlugin.COMPOSITEMASK);
            catalogue.expireFromCache(MaskedDataset.MANUAL_MASK_NAME);
            compositeMaskView.imageView.updateJustThisImage();
            changedSinceLastSave = true;
        }
    }

    public void redoLastManualEdit() {
        if (!manualMaskRedoStack.isEmpty()) {
            List<PixelChange> redos = manualMaskRedoStack.pop();
            for (PixelChange redo : redos) {
                activeDataset.setManualMaskPixel(redo.coords.getX(), redo.coords.getY(),
                        redo.toValue, redo.fromValue);
            }
            manualMaskUndoStack.push(redos);
            catalogue.expireFromCache(CompositeMaskPlugin.COMPOSITEMASK);
            catalogue.expireFromCache(MaskedDataset.MANUAL_MASK_NAME);
            compositeMaskView.imageView.updateJustThisImage();
            changedSinceLastSave = true;
        }
    }

    public void setManualMask(GridCoordinates2D imageCoords, int radius, Integer value) {
        setManualMask(imageCoords, value, radius, true);
        changedSinceLastSave = true;
    }

    private void setManualMask(GridCoordinates2D imageCoords, Integer value, int radius,
            boolean saveState) {
        if (imageCoords == null) {
            return;
        }
        List<PixelChange> changes = activeDataset.setManualMask(imageCoords, value, radius);
        if (saveState && changes.size() > 0) {
            manualMaskUndoStack.push(changes);
            manualMaskRedoStack.clear();
        }
        catalogue.expireFromCache(CompositeMaskPlugin.COMPOSITEMASK);
        catalogue.expireFromCache(MaskedDataset.MANUAL_MASK_NAME);
        compositeMaskView.imageView.updateJustThisImage();
    }

    public void setDataSelectedPosition(HorizontalPosition coords) {
        for (MaskVariable variable : plottableVariables) {
            try {
                Number value = catalogue.getDataset().readSinglePoint(
                        variable.variableName.getValue(), coords, null, null);
                if (value != null) {
                    variable.setValue(value.doubleValue());
                } else {
                    variable.setValue(null);
                }
            } catch (VariableNotFoundException | DataReadingException e) {
                e.printStackTrace();
            }
        }
    }

    public void toggleFullscreen() {
        mainStage.setFullScreen(!mainStage.isFullScreen());
    }

    public void quit() {
        if (!changedSinceLastSave) {
            mainStage.close();
        } else {
            Alert saveWarning = new Alert(AlertType.CONFIRMATION,
                    "You have modified settings since last save.  Really quit?", ButtonType.OK,
                    ButtonType.CANCEL);
            Optional<ButtonType> showAndWait = saveWarning.showAndWait();
            if (showAndWait.isPresent() && showAndWait.get() == ButtonType.OK) {
                mainStage.close();
            }
        }
    }

    class MaskVariable implements Comparable<MaskVariable> {
        /*
         * Plottable variable list is a list of these.
         * 
         * MaskableVariables is a filtered list of these
         * 
         * CMV has a table view of these...
         * 
         * Controller has a setSelectedLocation method which sets the
         * selectedValue on all of these...
         */
        StringProperty variableName;
        final BooleanProperty maskable;
        BooleanProperty includedInComposite;
        StringProperty selectedValue;

        private NumberFormat valueFormat = NumberFormat.getNumberInstance();

        public MaskVariable(String variableName, boolean maskable, boolean includedInComposite,
                Number selectedValue) {
            super();
            valueFormat.setMinimumFractionDigits(0);
            valueFormat.setMaximumFractionDigits(4);
            this.variableName = new SimpleStringProperty(variableName);
            this.maskable = new SimpleBooleanProperty(maskable);
            this.includedInComposite = new SimpleBooleanProperty(includedInComposite);
            this.selectedValue = new SimpleStringProperty();
            setValue(selectedValue);
            /*
             * When this property is changed, add/remove the variable from the
             * list of included ones
             */
            this.includedInComposite.addListener(new ChangeListener<Boolean>() {
                @Override
                public void changed(ObservableValue<? extends Boolean> observable, Boolean oldVal,
                        Boolean newVal) {
                    if (newVal) {
                        includedVariables.add(variableName);
                    } else {
                        includedVariables.remove(variableName);
                    }
                }
            });
        }

        public void setValue(Number value) {
            String str;
            if (!maskable.getValue()) {
                str = "";
            } else if (value == null || Double.isNaN(value.doubleValue())) {
                str = "No data";
            } else {
                str = valueFormat.format(value);
            }
            this.selectedValue.set(str);
        }

        @Override
        public String toString() {
            return variableName.getValue();
        }

        @Override
        public int compareTo(MaskVariable o) {
            if (variableName == null) {
                return o == null ? 0 : 1;
            }
            return variableName.getValue().compareTo(o.variableName.getValue());
        }
    }

    private static class UndoState {
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
    }

    static class PixelChange {
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
    }
}