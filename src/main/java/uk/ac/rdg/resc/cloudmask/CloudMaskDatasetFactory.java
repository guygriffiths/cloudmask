/*******************************************************************************
 * Copyright (c) 2013 The University of Reading
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import ucar.ma2.Array;
import ucar.ma2.ArrayFloat;
import ucar.ma2.ArrayShort;
import ucar.ma2.DataType;
import ucar.ma2.Index;
import ucar.ma2.InvalidRangeException;
import ucar.ma2.Range;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFileWriter;
import ucar.nc2.NetcdfFileWriter.Version;
import ucar.nc2.Variable;
import ucar.nc2.dataset.NetcdfDataset;
import uk.ac.rdg.resc.cloudmask.CloudMaskController.PixelChange;
import uk.ac.rdg.resc.edal.dataset.DataReadingStrategy;
import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.dataset.DatasetFactory;
import uk.ac.rdg.resc.edal.dataset.GridDataSource;
import uk.ac.rdg.resc.edal.dataset.GriddedDataset;
import uk.ac.rdg.resc.edal.dataset.cdm.NetcdfDatasetAggregator;
import uk.ac.rdg.resc.edal.dataset.plugins.VariablePlugin;
import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.exceptions.DataReadingException;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.exceptions.VariableNotFoundException;
import uk.ac.rdg.resc.edal.feature.GridFeature;
import uk.ac.rdg.resc.edal.graphics.utils.GraphicsUtils;
import uk.ac.rdg.resc.edal.grid.HorizontalGrid;
import uk.ac.rdg.resc.edal.grid.RegularAxis;
import uk.ac.rdg.resc.edal.grid.RegularAxisImpl;
import uk.ac.rdg.resc.edal.grid.RegularGridImpl;
import uk.ac.rdg.resc.edal.grid.TimeAxis;
import uk.ac.rdg.resc.edal.grid.VerticalAxis;
import uk.ac.rdg.resc.edal.metadata.GridVariableMetadata;
import uk.ac.rdg.resc.edal.metadata.Parameter;
import uk.ac.rdg.resc.edal.metadata.VariableMetadata;
import uk.ac.rdg.resc.edal.position.HorizontalPosition;
import uk.ac.rdg.resc.edal.util.Array2D;
import uk.ac.rdg.resc.edal.util.Array4D;
import uk.ac.rdg.resc.edal.util.Extents;
import uk.ac.rdg.resc.edal.util.GridCoordinates2D;
import uk.ac.rdg.resc.edal.util.ValuesArray2D;

/**
 * {@link DatasetFactory} that creates {@link Dataset}s representing gridded
 * data using native grid co-ordinates and adding a mask for each variable
 * 
 * @author Guy Griffiths
 */
public final class CloudMaskDatasetFactory extends DatasetFactory {
//    private static final Logger log = LoggerFactory.getLogger(CloudMaskDatasetFactory.class);

    private Dimension xDimension;

    private Dimension yDimension;
    
    /* Warnings about not closing nc were invalid - it gets closed by CdmUtils method */
    @Override
    public MaskedDataset createDataset(String id, String location, boolean forceRefresh) throws IOException,
            EdalException {
        NetcdfDataset nc = null;
        try {
            /*
             * Open the dataset, using the cache for NcML aggregations
             */
            nc = NetcdfDatasetAggregator.getDataset(location);

            List<GridVariableMetadata> vars = new ArrayList<GridVariableMetadata>();
            Map<String, ThresholdSettings> thresholdMap = new HashMap<>();
            VerticalAxis zDomain = null;
            TimeAxis tDomain = null;
            xDimension = null;
            yDimension = null;
            Array2D<Number> values = null;
            String[] maskComponents = null;
            for (Variable var : nc.getVariables()) {
                if (var.isCoordinateVariable()) {
                    continue;
                }
                String varId = var.getFullName();

                if (varId.endsWith(MaskedDataset.MASK_SUFFIX)) {
                    try {
                        Attribute thresholdMax = var.findAttribute("threshold_max");
                        Attribute thresholdMin = var.findAttribute("threshold_min");
                        Attribute thresholdInclusive = var.findAttribute("threshold_inclusive");
                        ThresholdSettings threshold = new ThresholdSettings(
                                (Double) thresholdMin.getValue(0),
                                (Double) thresholdMax.getValue(0),
                                Boolean.parseBoolean(thresholdInclusive.getValue(0).toString()));
                        thresholdMap.put(
                                varId.substring(0,
                                        varId.length() - MaskedDataset.MASK_SUFFIX.length() - 1),
                                threshold);
                    } catch (Exception e) {
                    }
                    continue;
                } else if (varId.equals(MaskedDataset.MANUAL_MASK_NAME)) {
                    Array data = var.read();
                    Index index = data.getIndex();
                    int[] shape = data.getShape();
                    int xSize = shape[1];
                    int ySize = shape[0];
                    values = new ValuesArray2D(ySize, xSize);
                    for (int i = 0; i < xSize; i++) {
                        for (int j = 0; j < ySize; j++) {
                            index.set(j, i);
                            float value = data.getFloat(index);
                            if (!Float.isNaN(value)) {
                                values.set(value, j, i);
                            }
                        }
                    }
                    continue;
                } else if (varId.equals(CompositeMaskPlugin.COMPOSITEMASK)) {
                    try {
                        Attribute components = var.findAttribute("mask_components");
                        String componentsString = (String) components.getValue(0);
                        maskComponents = componentsString.replaceFirst("manual-cloudmask,", "")
                                .split(",");
                    } catch (Exception e) {
                    }
                    continue;
                }

                List<Dimension> dimensions = var.getDimensions();
                if (dimensions.size() != 2) {
                    throw new IllegalArgumentException(
                            "Currently all (non-coordinate) variables must be 2d");
                }
                if (xDimension == null) {
                    xDimension = dimensions.get(1);
                } else if (!xDimension.equals(dimensions.get(1))) {
                    throw new IllegalArgumentException(
                            "All variables must share the same dimensions for this NativeGridDataset");
                }
                if (yDimension == null) {
                    yDimension = dimensions.get(0);
                } else if (!yDimension.equals(dimensions.get(0))) {
                    throw new IllegalArgumentException(
                            "All variables must share the same dimensions for this NativeGridDataset");
                }

                RegularAxis xAxis = new RegularAxisImpl("x-axis", 0, 1.0, xDimension.getLength(),
                        false);
                RegularAxis yAxis = new RegularAxisImpl("y-axis", 0, 1.0, yDimension.getLength(),
                        false);
                HorizontalGrid hDomain = new RegularGridImpl(xAxis, yAxis, null);

                String name = getVariableName(var);

                Parameter parameter = new Parameter(varId, var.getShortName(),
                        var.getDescription(), var.getUnitsString(), name);

                GridVariableMetadata metadata = new GridVariableMetadata(parameter, hDomain,
                        zDomain, tDomain, true);

                for (Attribute attr : var.getAttributes()) {
                    String attrName = attr.getFullName();
                    if (!attrName.startsWith("_") && !attrName.equals("units")
                            && !attrName.equals("long_name")) {
                        metadata.getVariableProperties().put(attrName, attr.getValue(0));
                    }
                }

                vars.add(metadata);
            }

            if (values == null) {
                values = new ValuesArray2D(yDimension.getLength(), xDimension.getLength());
            }
            /*
             * We want to always read with a bounding box - other data reading
             * strategies will cause median/stddev to fail
             */
            MaskedDataset maskedDataset = new MaskedDataset(id, location, vars,
                    DataReadingStrategy.BOUNDING_BOX, thresholdMap, values);
            if (maskComponents != null) {
                maskedDataset.setMaskedVariables(maskComponents);
            }

            return maskedDataset;
        } finally {
            NetcdfDatasetAggregator.releaseDataset(nc);
        }
    }

    private final class ThresholdSettings {
        private double min;
        private double max;
        private boolean inclusive;

        public ThresholdSettings(double min, double max, boolean inclusive) {
            super();
            this.min = min;
            this.max = max;
            this.inclusive = inclusive;
        }
    }

    /**
     * An {@link GriddedDataset} which:
     * 
     * Adds masks to each variable
     * 
     * Works in native grid co-ordinates
     * 
     * Allows median and standard deviation filters to be added in a moving 3x3
     * window to any original variable
     *
     * @author Guy Griffiths
     */
    @SuppressWarnings("serial")
    public final class MaskedDataset extends GriddedDataset {
        public final static String MANUAL_MASK_NAME = "manual-cloudmask";
        public final static int MANUAL_CLEAR = 0;
        public final static int MANUAL_PROBABLY_CLEAR = 1;
        public final static int MANUAL_PROBABLY_CLOUDY = 2;
        public final static int MANUAL_CLOUDY = 3;
        public final static int MANUAL_DUST = 4;
        public final static int MANUAL_SMOKE = 5;

        public final static String MASK_SUFFIX = "MASK";
        public final static String MEDIAN = "-median3x3";
        public final static String STDDEV = "-stddev3x3";

        private final String location;
        private final DataReadingStrategy dataReadingStrategy;
        private Map<String, ThresholdMaskPlugin> thresholds;
        private ObservableList<String> unmaskedVariables;
        private ObservableList<String> originalVariables;

        private CompositeMaskPlugin compositePlugin;

        private Array2D<Number> manualMask;

        public MaskedDataset(String id, String location, Collection<GridVariableMetadata> vars,
                DataReadingStrategy dataReadingStrategy,
                Map<String, ThresholdSettings> thresholdSettings, Array2D<Number> manualMaskVals)
                throws EdalException {
            super(id, filterVars(vars));
            this.location = location;
            this.dataReadingStrategy = dataReadingStrategy;

            this.thresholds = new HashMap<>();
            unmaskedVariables = FXCollections.observableArrayList(getVariableIds());
            originalVariables = FXCollections.observableArrayList(getVariableIds());

            Set<String> variableIds = new HashSet<>(getVariableIds());
            String[] allVars = new String[variableIds.size()];
            int i = 0;
            for (String var : variableIds) {
                addMaskToVariable(var);
                allVars[i++] = var;
                if (thresholdSettings.containsKey(var)) {
                    ThresholdSettings ts = thresholdSettings.get(var);
                    setMaskMinThreshold(var, ts.min);
                    setMaskMaxThreshold(var, ts.max);
                    setMaskThresholdInclusive(var, ts.inclusive);
                }
            }

            /*
             * All variables for the masked dataset must share the same domain.
             * We can use any of their domains to create the GVM for the manual
             * mask
             */
            GridVariableMetadata metadata = vars.iterator().next();
            manualMask = manualMaskVals;
            this.vars
                    .put(MANUAL_MASK_NAME,
                            new GridVariableMetadata(
                                    new Parameter(
                                            MANUAL_MASK_NAME,
                                            "Manual mask",
                                            "Manually defined cloud mask",
                                            "0: clear; 1: probably clear; 2: probably cloudy; 3: cloudy; 4: dust; 5: smoke",
                                            ""), metadata.getHorizontalDomain(), metadata
                                            .getVerticalDomain(), metadata.getTemporalDomain(),
                                    true));
            /*
             * We now add the composite mask plugin. This will have a domain
             * which is compatible with all other masked variables. However, no
             * variables will be used for masking until they are explicitly set
             */
            compositePlugin = new CompositeMaskPlugin(MANUAL_MASK_NAME);
            super.addVariablePlugin(compositePlugin);
        }

        @Override
        public void addVariablePlugin(VariablePlugin plugin) throws EdalException {
            super.addVariablePlugin(plugin);
            for (String newVar : plugin.providesVariables()) {
                unmaskedVariables.add(newVar);
                addMaskToVariable(newVar);
            }
        }

        private void addMaskToVariable(String varId) throws EdalException {
            Extent<Float> valueRange = GraphicsUtils.estimateValueRange(this, varId);
            ThresholdMaskPlugin thresholdPlugin = new ThresholdMaskPlugin(varId,
                    valueRange.getLow(), valueRange.getHigh());
            super.addVariablePlugin(thresholdPlugin);
            thresholds.put(varId, thresholdPlugin);
        }

        public void setMaskMaxThreshold(String varId, double max) {
            thresholds.get(varId).setMaxThreshold(max);
        }

        public void setMaskMinThreshold(String varId, double min) {
            thresholds.get(varId).setMinThreshold(min);
        }

        public void setMaskThreshold(String varId, double min, double max) {
            thresholds.get(varId).setThreshold(min, max);
        }

        public void setMaskThresholdInclusive(String varId, boolean inclusive) {
            thresholds.get(varId).setThresholdInclusive(inclusive);
        }

        public boolean isMaskThresholdInclusive(String varId) {
            return thresholds.get(varId).inclusive;
        }

        public void setMaskedVariables(String... vars) {
            compositePlugin.setMasks(vars);
        }

        public String[] getMaskedVariables() {
            return compositePlugin.usesVariables();
        }

        public Extent<Double> getMaskThreshold(String varId) {
            ThresholdMaskPlugin plugin = thresholds.get(varId);
            return Extents.newExtent(plugin.min, plugin.max);
        }

        public ObservableList<String> getUnmaskedVariableNames() {
            return unmaskedVariables;
        }

        public ObservableList<String> getOriginalVariableNames() {
            return originalVariables;
        }

        /**
         * Enables a median filter with a 3x3 moving window for the given
         * variable
         * 
         * @param variable
         *            The variable to add a median filter to
         */
        public void enableMedian(String variable) {
            if (variable.endsWith(MEDIAN) || variable.endsWith(STDDEV)) {
                throw new UnsupportedOperationException(
                        "Cannot apply median/stddev to variables which already have it");
            }
            if (unmaskedVariables.contains(variable)) {
                try {
                    GridVariableMetadata variableMetadata = (GridVariableMetadata) getVariableMetadata(variable);
                    Parameter p = variableMetadata.getParameter();
                    VariableMetadata newMetadata = new GridVariableMetadata(new Parameter(variable
                            + MEDIAN, "Median of " + p.getTitle(), "Median of ("
                            + p.getDescription() + ") over a 3x3 moving window", p.getUnits(),
                            p.getStandardName()), variableMetadata.getHorizontalDomain(),
                            variableMetadata.getVerticalDomain(),
                            variableMetadata.getTemporalDomain(), true);
                    vars.put(variable + MEDIAN, newMetadata);
                    unmaskedVariables.add(variable + MEDIAN);
                    addMaskToVariable(variable + MEDIAN);
                } catch (EdalException e) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * Enables a standard deviation filter with a 3x3 moving window for the
         * given variable
         * 
         * @param variable
         *            The variable to add a std dev. filter to
         */
        public void enableStddev(String variable) {
            if (variable.endsWith(MEDIAN) || variable.endsWith(STDDEV)) {
                throw new UnsupportedOperationException(
                        "Cannot apply median/stddev to variables which already have it");
            }
            if (unmaskedVariables.contains(variable)) {
                try {
                    GridVariableMetadata variableMetadata = (GridVariableMetadata) getVariableMetadata(variable);
                    Parameter p = variableMetadata.getParameter();
                    VariableMetadata newMetadata = new GridVariableMetadata(new Parameter(variable
                            + STDDEV, "Stddev of " + p.getTitle(), "Standard deviation of ("
                            + p.getDescription() + ") over a 3x3 moving window", p.getUnits(),
                            p.getStandardName()), variableMetadata.getHorizontalDomain(),
                            variableMetadata.getVerticalDomain(),
                            variableMetadata.getTemporalDomain(), true);
                    vars.put(variable + STDDEV, newMetadata);
                    unmaskedVariables.add(variable + STDDEV);
                    addMaskToVariable(variable + STDDEV);
                } catch (EdalException e) {
                    e.printStackTrace();
                }
            }
        }

        public String getMaskedVariableName(String varId) throws VariableNotFoundException {
            if (getVariableIds().contains(varId) && !thresholds.containsKey(varId)) {
                return varId + "-" + MASK_SUFFIX;
            } else {
                throw new VariableNotFoundException(varId);
            }
        }
        
        @Override
        protected GridDataSource openDataSource() {
            NetcdfDataset nc;
            try {
                nc = NetcdfDatasetAggregator.getDataset(location);
            } catch (EdalException | IOException e) {
                throw new DataReadingException("Problem aggregating datasets", e);
            }
            return new GridDataSource() {

                private Array4D<Number> readNormal(String variableId, int tmin, int tmax, int zmin,
                        int zmax, final int ymin, final int ymax, final int xmin, final int xmax)
                        throws IOException, DataReadingException {
                    /*
                     * Reads a variable which doesn't have median/stddev applied
                     * to it.
                     * 
                     * Those cases are more complex because usually more data
                     * needs to be read than returned
                     */
                    if (MANUAL_MASK_NAME.equals(variableId)) {
                        return new Array4D<Number>(1, 1, 1 + (ymax - ymin), 1 + (xmax - xmin)) {
                            @Override
                            public Number get(int... coords) {
                                int y = ymin + coords[2];
                                int x = xmin + coords[3];
                                return manualMask.get(y, x);
                            }

                            @Override
                            public void set(Number value, int... coords) {
                                throw new UnsupportedOperationException("Immutable array");
                            }
                        };
                    } else {
                        List<Range> ranges = new ArrayList<>();
                        Array arr;
                        try {
                            ranges.add(new Range(ymin, ymax));
                            ranges.add(new Range(xmin, xmax));
                            arr = nc.findVariable(variableId).read(ranges);
                        } catch (InvalidRangeException e) {
                            e.printStackTrace();
                            throw new DataReadingException("Problem reading data", e);
                        }
                        return new Array4D<Number>(1, 1, (ymax - ymin) + 1, (xmax - xmin) + 1) {
                            @Override
                            public Number get(int... coords) {
                                int y = coords[2];
                                int x = coords[3];

                                /*
                                 * We are reading
                                 */
                                Index index = arr.getIndex();
                                index.setDim(0, y);
                                index.setDim(1, x);

                                return arr.getFloat(index);
                            }

                            @Override
                            public void set(Number value, int... coords) {
                                throw new UnsupportedOperationException("Immutable array");
                            }
                        };
                    }
                }

                private Array4D<Number> read3x3Window(String variableId, int tmin, int tmax,
                        int zmin, int zmax, final int ymin, final int ymax, final int xmin,
                        final int xmax, final boolean median) throws IOException,
                        DataReadingException {
                    /*
                     * For median / stddev, we have to read an extra pixel
                     * either size of the requested data, but not if it hits an
                     * edge.
                     * 
                     * Regardless we want the returned array to have the size
                     * which has been requested, so we store this here.
                     */
                    int dataArraySizeX = xmax - xmin + 1;
                    int dataArraySizeY = ymax - ymin + 1;

                    List<Range> ranges = new ArrayList<>();
                    Array arr;

                    /*
                     * Here we adjust the range of the underlying data to read,
                     * expanding by one pixel at each edge if this is possible
                     * (i.e. if we are not at the edges of the underlying data)
                     */
                    int yminData;
                    if (ymin > 0) {
                        yminData = ymin - 1;
                    } else {
                        yminData = ymin;
                    }
                    int xminData;
                    if (xmin > 0) {
                        xminData = xmin - 1;
                    } else {
                        xminData = xmin;
                    }
                    int ymaxData;
                    if (ymax < yDimension.getLength() - 1) {
                        ymaxData = ymax + 1;
                    } else {
                        ymaxData = ymax;
                    }
                    int xmaxData;
                    if (xmax < yDimension.getLength() - 1) {
                        xmaxData = xmax + 1;
                    } else {
                        xmaxData = xmax;
                    }

                    try {
                        ranges.add(new Range(yminData, ymaxData));
                        ranges.add(new Range(xminData, xmaxData));
                        arr = nc.findVariable(variableId).read(ranges);
                    } catch (InvalidRangeException e) {
                        e.printStackTrace();
                        throw new DataReadingException("Problem reading data", e);
                    }
                    return new Array4D<Number>(1, 1, dataArraySizeY, dataArraySizeX) {
                        @Override
                        public Number get(int... coords) {
                            int y = coords[2];
                            int x = coords[3];

                            /*
                             * Read data in a 3x3 window centred around the
                             * requested pixel
                             */

                            List<Float> values = new ArrayList<>();

                            if ((xmin == 0 && x == 0)
                                    || (ymin == 0 && y == 0)
                                    || (xmax == xDimension.getLength() - 1 && x == dataArraySizeX - 1)
                                    || (ymax == yDimension.getLength() - 1 && y == dataArraySizeY - 1)) {
                                /*
                                 * If have read from the edge of the underlying
                                 * data and are now trying to access the edge of
                                 * the returned data, we cannot generate a
                                 * value, so return NaN
                                 */
                                return Float.NaN;
                            }

                            Index index = arr.getIndex();
                            /*-
                             * The data read and stored in arr is indexed from
                             * 0.
                             * 
                             * IF AND ONLY IF this contains the LOWER edge of
                             * the data (i.e. xmin / ymin was zero), then the
                             * coordinates being requested have matching indices.
                             * 
                             * For example in a 1D case:
                             * 
                             * arr indices            [0,1,2,3,4...
                             * map to return indices  [0,1,2,3,4...
                             * 
                             * and (return indices) 0 will be NaN
                             *                      1 will be calculated from arr indices 0,1,2
                             *                      2 will be calculated from arr indices 1,2,3
                             * 
                             * OTHERWISE we will have an offset, because we will have read data
                             * from an index one lower.  Again, an example:
                             * 
                             * arr indices            [0,1,2,3,4...
                             * map to return indices    [0,1,2,3...
                             * 
                             * and (return indices) 0 will be calculated from arr indices 0,1,2
                             *                      1 will be calculated from arr indices 1,2,3
                             *                      2 will be calculated from arr indices 2,3,4
                             * 
                             * Yes, it's a little complicated, but worth understanding if you
                             * plan on editing this.  Which hopefully you won't have to,
                             * because I've spent a brainachy time getting it right...
                             */
                            for (int i = 0; i <= 2; i++) {
                                for (int j = 0; j <= 2; j++) {
                                    if (xmin == 0) {
                                        index.setDim(1, x + i - 1);
                                    } else {
                                        index.setDim(1, x + i);
                                    }
                                    if (ymin == 0) {
                                        index.setDim(0, y + j - 1);
                                    } else {
                                        index.setDim(0, y + j);
                                    }
                                    values.add(arr.getFloat(index));
                                }
                            }

                            if (median) {
                                /*
                                 * Sort numerically and pick the middle value
                                 */
                                Collections.sort(values);
                                return values.get(4);
                            } else {
                                /*
                                 * We have a standard deviation
                                 */
                                float mean = 0.0f;
                                for (Float value : values) {
                                    mean += value / 9f;
                                }

                                float var = 0.0f;
                                for (Float value : values) {
                                    var += (value - mean) * (value - mean);
                                }

                                return Math.sqrt(var);
                            }
                        }

                        @Override
                        public void set(Number value, int... coords) {
                            throw new UnsupportedOperationException("Immutable array");
                        }
                    };
                }

                @Override
                public Array4D<Number> read(String variableId, int tmin, int tmax, int zmin,
                        int zmax, int ymin, int ymax, int xmin, int xmax) throws IOException,
                        DataReadingException {
                    if (variableId.endsWith(MEDIAN) && unmaskedVariables.contains(variableId)) {
                        return read3x3Window(
                                variableId.substring(0, variableId.length() - MEDIAN.length()),
                                tmin, tmax, zmin, zmax, ymin, ymax, xmin, xmax, true);
                    } else if (variableId.endsWith(STDDEV)
                            && unmaskedVariables.contains(variableId)) {
                        return read3x3Window(
                                variableId.substring(0, variableId.length() - STDDEV.length()),
                                tmin, tmax, zmin, zmax, ymin, ymax, xmin, xmax, false);
                    } else {
                        return readNormal(variableId, tmin, tmax, zmin, zmax, ymin, ymax, xmin,
                                xmax);
                    }
                }

                @Override
                public void close() {
                    NetcdfDatasetAggregator.releaseDataset(nc);
                }
            };
        }

        @Override
        protected DataReadingStrategy getDataReadingStrategy() {
            return dataReadingStrategy;
        }

        public List<PixelChange> setManualMask(GridCoordinates2D coords, Integer value, int radius) {
            List<PixelChange> changes = new ArrayList<>();
            Array4D<Number> values;
            try {
                values = readFeature(CompositeMaskPlugin.COMPOSITEMASK).getValues(
                        CompositeMaskPlugin.COMPOSITEMASK);
            } catch (DataReadingException | VariableNotFoundException e) {
                /*
                 * There's something seriously wrong if we can't read the
                 * composite feature. Don't set the mask, print the stack trace,
                 * and then debug it!
                 */
                e.printStackTrace();
                return null;
            }
            for (int r = 1; r <= radius; r++) {
                for (int xAdd = 0; xAdd < r; xAdd++) {
                    for (int yAdd = 0; yAdd < r; yAdd++) {
                        if (Math.sqrt(xAdd * xAdd + yAdd * yAdd) <= r) {
                            for (int xMult = -1; xMult <= 1; xMult += 2) {
                                for (int yMult = -1; yMult <= 1; yMult += 2) {
                                    int x = coords.getX() + xMult * xAdd;
                                    int y = coords.getY() + yMult * yAdd;
                                    if (x >= 0 && x < values.getXSize() && y >= 0
                                            && y < values.getYSize()) {
                                        Number oldValue = values.get(0, 0, y, x);
                                        Number oldManualValue = manualMask.get(y, x);
                                        if (setManualMaskPixel(x, y, value, oldValue)) {
                                            /*
                                             * Add changed pixel to return list
                                             */
                                            changes.add(new PixelChange(
                                                    new GridCoordinates2D(x, y),
                                                    oldManualValue == null ? null : oldManualValue
                                                            .intValue(), value));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return changes;
        }

        boolean setManualMaskPixel(int x, int y, Integer newValue, Number oldValue) {
            if (newValue == null && oldValue != null) {
                manualMask.set(newValue, y, x);
                return true;
            } else if (oldValue == null
                    || (MANUAL_CLEAR == newValue && oldValue.floatValue() != 0f)
                    || (MANUAL_CLOUDY == newValue && oldValue.floatValue() != 1f)
                    || (MANUAL_PROBABLY_CLEAR == newValue) || (MANUAL_PROBABLY_CLOUDY == newValue)
                    || (MANUAL_DUST == newValue) || (MANUAL_SMOKE == newValue)) {
                /*
                 * Only set cloudy / clear if this changes the composite mask
                 */
                manualMask.set(newValue, y, x);
                return true;
            }
            return false;
        }

        public Array2D<Number> getManualMask() {
            return manualMask;
        }
    }

    /**
     * Filters out variables which should be auto-generated. This will be
     * anything which ends with the mask suffix, manual-cloudmask, and
     * composite-mask. If present, the attributes of these variables should be
     * read and the appropriate automatic variables generated correctly
     * 
     * @param vars
     *            The variables to filter
     * @return A new list containing the filtered variables
     */
    private static Collection<GridVariableMetadata> filterVars(Collection<GridVariableMetadata> vars) {
        return vars;
    }

    /**
     * Returns the phenomenon that the given variable represents.
     * 
     * This name will be, in order of preference:
     * 
     * The standard name
     * 
     * The long name
     * 
     * The variable name
     */
    private static String getVariableName(Variable var) {
        Attribute stdNameAtt = var.findAttributeIgnoreCase("standard_name");
        if (stdNameAtt == null || stdNameAtt.getStringValue().trim().equals("")) {
            Attribute longNameAtt = var.findAttributeIgnoreCase("long_name");
            if (longNameAtt == null || longNameAtt.getStringValue().trim().equals("")) {
                return var.getFullName();
            } else {
                return longNameAtt.getStringValue();
            }
        } else {
            return stdNameAtt.getStringValue();
        }
    }

    /**
     * A {@link VariablePlugin} which masks data based on 2 threshold values
     *
     * @author Guy Griffiths
     */
    private class ThresholdMaskPlugin extends VariablePlugin {
        private double min;
        private double max;
        private VariableMetadata diffMeta = null;
        private boolean inclusive = false;

        public ThresholdMaskPlugin(String var, double min, double max) {
            super(new String[] { var }, new String[] { MaskedDataset.MASK_SUFFIX });
            setThreshold(min, max);
        }

        public void setMinThreshold(double min) {
            setThreshold(min, max);
        }

        public void setMaxThreshold(double max) {
            setThreshold(min, max);
        }

        public void setThreshold(double min, double max) {
            this.min = min;
            this.max = max;
            if (diffMeta != null) {
                diffMeta.getVariableProperties().put("threshold_min", min);
                diffMeta.getVariableProperties().put("threshold_max", max);
            }
        }

        public void setThresholdInclusive(boolean inclusive) {
            this.inclusive = inclusive;
            if (diffMeta != null) {
                diffMeta.getVariableProperties().put("threshold_inclusive",
                        inclusive ? "true" : "false");
            }
        }

        @Override
        protected VariableMetadata[] doProcessVariableMetadata(VariableMetadata... metadata)
                throws EdalException {
            VariableMetadata meta = metadata[0];

            diffMeta = newVariableMetadataFromMetadata(new Parameter(
                    getFullId(MaskedDataset.MASK_SUFFIX), meta.getParameter().getTitle() + " Mask",
                    "Mask of " + meta.getParameter().getDescription(), "0: unmasked, 1: masked",
                    null), true, meta);
            diffMeta.getVariableProperties().put("threshold_min", min);
            diffMeta.getVariableProperties().put("threshold_max", max);
            diffMeta.getVariableProperties().put("threshold_inclusive",
                    inclusive ? "true" : "false");
            diffMeta.setParent(meta.getParent(), null);
            return new VariableMetadata[] { diffMeta };
        }

        @Override
        protected Number generateValue(String varSuffix, HorizontalPosition pos,
                Number... sourceValues) {
            try {
                if (sourceValues[0].doubleValue() <= min || sourceValues[0].doubleValue() >= max)
                    return inclusive ? 0 : 1;
                return inclusive ? 1 : 0;
            } catch (NullPointerException e) {
                return null;
            }
        }
    }

    public static void writeDataset(MaskedDataset dataset, String location) throws IOException,
            VariableNotFoundException, InvalidRangeException, DataReadingException {
        NetcdfFileWriter fileWriter = NetcdfFileWriter.createNew(Version.netcdf3, location);

        Set<String> outputVariables = new LinkedHashSet<>();
        outputVariables.addAll(dataset.getOriginalVariableNames());
        for (String var : dataset.compositePlugin.usesVariables()) {
            outputVariables.add(var);
            if (var.endsWith(MaskedDataset.MASK_SUFFIX)) {
                outputVariables.add(var.substring(0,
                        var.length() - MaskedDataset.MASK_SUFFIX.length() - 1));
            }
        }
        outputVariables.add(CompositeMaskPlugin.COMPOSITEMASK);

        Map<Variable, Array> dataToWrite = new HashMap<>();
        boolean firstVar = true;
        ArrayList<Dimension> dims = null;
        for (String varId : outputVariables) {
            GridVariableMetadata metadata = (GridVariableMetadata) dataset
                    .getVariableMetadata(varId);
            int xSize = metadata.getHorizontalDomain().getXSize();
            int ySize = metadata.getHorizontalDomain().getYSize();
            if (firstVar) {
                dims = new ArrayList<Dimension>();
                Dimension yDim = fileWriter.addDimension(null, "y", ySize);
                Dimension xDim = fileWriter.addDimension(null, "x", xSize);
                /*
                 * Define dimensions
                 */
                dims.add(yDim);
                dims.add(xDim);

                firstVar = false;
            }

            GridFeature feature = dataset.readFeature(varId);
            Array4D<Number> array4d = feature.getValues(varId);

            Variable variable;
            if (metadata.getId().endsWith(MaskedDataset.MASK_SUFFIX)) {
                ArrayShort.D2 values = new ArrayShort.D2(ySize, xSize, false);

                for (int y = 0; y < ySize; y++) {
                    for (int x = 0; x < xSize; x++) {
                        /*
                         * Masks can not have missing values - they are either 0
                         * or 1.
                         */
                        Number number = array4d.get(0, 0, y, x);
                        values.set(y, x, number.shortValue());
                    }
                }
                variable = fileWriter.addVariable(null, metadata.getId(), DataType.SHORT, dims);
                dataToWrite.put(variable, values);
            } else if (metadata.getId().equals(CompositeMaskPlugin.COMPOSITEMASK)) {
                ArrayFloat.D2 values = new ArrayFloat.D2(ySize, xSize);

                for (int y = 0; y < ySize; y++) {
                    for (int x = 0; x < xSize; x++) {
                        Number number = array4d.get(0, 0, y, x);
                        if (number == null || number.floatValue() > 1f) {
                            /*
                             * Write unset values as NaNs, and remove aerosol
                             * values from the composite mask (these are
                             * included in the manual mask, but are not part of
                             * the composite, which only applies to clouds)
                             */
                            number = Float.NaN;
                        }
                        values.set(y, x, number.floatValue());
                    }
                }
                variable = fileWriter.addVariable(null, metadata.getId(), DataType.FLOAT, dims);
                dataToWrite.put(variable, values);
            } else {
                ArrayFloat.D2 values = new ArrayFloat.D2(ySize, xSize);

                for (int y = 0; y < ySize; y++) {
                    for (int x = 0; x < xSize; x++) {
                        Number number = array4d.get(0, 0, y, x);
                        if (number == null) {
                            number = Float.NaN;
                        }
                        values.set(y, x, number.floatValue());
                    }
                }
                variable = fileWriter.addVariable(null, metadata.getId(), DataType.FLOAT, dims);
                dataToWrite.put(variable, values);
            }

            for (Entry<String, Object> entry : metadata.getVariableProperties().entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String) {
                    fileWriter.addVariableAttribute(variable, new Attribute(entry.getKey(),
                            (String) value));
                } else if (value instanceof Number) {
                    fileWriter.addVariableAttribute(variable, new Attribute(entry.getKey(),
                            (Number) value));
                }
            }

            fileWriter.addVariableAttribute(variable, new Attribute("units", metadata
                    .getParameter().getUnits()));
            fileWriter.addVariableAttribute(variable, new Attribute("long_name", metadata
                    .getParameter().getDescription()));
        }

        fileWriter.create();
        for (Entry<Variable, Array> entry : dataToWrite.entrySet()) {
            fileWriter.write(entry.getKey(), entry.getValue());
        }
        fileWriter.close();
    }
}