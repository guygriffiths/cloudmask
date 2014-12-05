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

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ucar.ma2.Array;
import ucar.ma2.ArrayFloat;
import ucar.ma2.ArrayShort;
import ucar.ma2.DataType;
import ucar.ma2.Index;
import ucar.ma2.InvalidRangeException;
import ucar.ma2.Range;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.NetcdfFileWriter;
import ucar.nc2.NetcdfFileWriter.Version;
import ucar.nc2.Variable;
import ucar.nc2.dataset.NetcdfDataset;
import ucar.nc2.ncml.NcMLReader;
import uk.ac.rdg.resc.edal.dataset.AbstractGridDataset;
import uk.ac.rdg.resc.edal.dataset.DataReadingStrategy;
import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.dataset.DatasetFactory;
import uk.ac.rdg.resc.edal.dataset.GridDataSource;
import uk.ac.rdg.resc.edal.dataset.plugins.VariablePlugin;
import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.exceptions.DataReadingException;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.exceptions.VariableNotFoundException;
import uk.ac.rdg.resc.edal.feature.GridFeature;
import uk.ac.rdg.resc.edal.graphics.style.util.GraphicsUtils;
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
import uk.ac.rdg.resc.edal.util.Array4D;
import uk.ac.rdg.resc.edal.util.Extents;
import uk.ac.rdg.resc.edal.util.cdm.CdmUtils;

/**
 * {@link DatasetFactory} that creates {@link Dataset}s representing gridded
 * data using native grid co-ordinates and adding a mask for each variable
 * 
 * @author Guy Griffiths
 */
public final class CloudMaskDatasetFactory extends DatasetFactory {
    private static final Logger log = LoggerFactory.getLogger(CloudMaskDatasetFactory.class);
    public final static String MASK_SUFFIX = "MASK";

    private String ncmlString = null;

    private Dimension xDimension;

    private Dimension yDimension;

    @Override
    public MaskedDataset createDataset(String id, String location) throws IOException,
            EdalException {
        NetcdfDataset nc = null;
        try {
            /*
             * Open the dataset, using the cache for NcML aggregations
             */
            nc = openAndAggregateDataset(location);

            List<GridVariableMetadata> vars = new ArrayList<GridVariableMetadata>();

            VerticalAxis zDomain = null;
            TimeAxis tDomain = null;
            xDimension = null;
            yDimension = null;
            for (Variable var : nc.getVariables()) {
                if (var.isCoordinateVariable()) {
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
                RegularAxis yAxis = new RegularAxisImpl("y-axis", yDimension.getLength() - 1, -1.0,
                        yDimension.getLength(), false);
                HorizontalGrid hDomain = new RegularGridImpl(xAxis, yAxis, null);

                String varId = var.getFullName();
                String name = getVariableName(var);

                Parameter parameter = new Parameter(varId, var.getShortName(),
                        var.getDescription(), var.getUnitsString(), name);
                GridVariableMetadata metadata = new GridVariableMetadata(var.getFullName(),
                        parameter, hDomain, zDomain, tDomain, true);

                for (Attribute attr : var.getAttributes()) {
                    String attrName = attr.getFullName();
                    if (!attrName.startsWith("_") && !attrName.equals("units")
                            && !attrName.equals("long_name")) {
                        metadata.getVariableProperties().put(attrName, attr.getValue(0));
                    }
                }

                vars.add(metadata);
            }

            MaskedDataset cdmGridDataset = new MaskedDataset(id, location, vars,
                    CdmUtils.getOptimumDataReadingStrategy(nc));

            return cdmGridDataset;
        } finally {
            CdmUtils.closeDataset(nc);
        }
    }

    public final class MaskedDataset extends AbstractGridDataset {
        private final String location;
        private final DataReadingStrategy dataReadingStrategy;
        private Map<String, ThresholdMaskPlugin> thresholds;
        private ObservableList<String> unmaskedVariables;

        private CompositeMaskPlugin compositePlugin;

        public MaskedDataset(String id, String location, Collection<GridVariableMetadata> vars,
                DataReadingStrategy dataReadingStrategy) throws EdalException {
            super(id, vars);
            this.location = location;
            this.dataReadingStrategy = dataReadingStrategy;

            thresholds = new HashMap<>();
            unmaskedVariables = FXCollections.observableArrayList(getVariableIds());

            Set<String> variableIds = new HashSet<>(getVariableIds());
            String[] allVars = new String[variableIds.size()];
            int i = 0;
            for (String var : variableIds) {
                addMaskToVariable(var);
                allVars[i++] = var;
            }
            
            /*
             * We now add the composite mask plugin. This will have a domain
             * which is compatible with all other masked variables. However, no
             * variables will be used for masking until they are explicitly set
             */
            compositePlugin = new CompositeMaskPlugin(allVars);
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
            for(String v : vars) {
                System.out.println(v+" added to mask");
            }
            compositePlugin.setMasks(vars);
        }

        public Extent<Double> getMaskThreshold(String varId) {
            ThresholdMaskPlugin plugin = thresholds.get(varId);
            return Extents.newExtent(plugin.min, plugin.max);
        }

        public ObservableList<String> getUnmaskedVariableNames() {
            return unmaskedVariables;
        }

        public String getMaskedVariableName(String varId) throws VariableNotFoundException {
            if (getVariableIds().contains(varId) && !thresholds.containsKey(varId)) {
                return varId + "-" + MASK_SUFFIX;
            } else {
                throw new VariableNotFoundException(varId);
            }
        }

        @Override
        protected GridDataSource openGridDataSource() throws IOException {
            NetcdfDataset nc;
            try {
                nc = openAndAggregateDataset(location);
            } catch (EdalException e) {
                throw new IOException("Problem aggregating datasets", e);
            }
            return new GridDataSource() {
                @Override
                public Array4D<Number> read(String variableId, int tmin, int tmax, int zmin,
                        int zmax, int ymin, int ymax, int xmin, int xmax) throws IOException,
                        DataReadingException {
                    List<Range> ranges = new ArrayList<>();
                    Array arr;
                    try {
                        ranges.add(new Range(ymin, ymax));
                        ranges.add(new Range(xmin, xmax));
                        arr = nc.findVariable(variableId).read(ranges);
                    } catch (InvalidRangeException e) {
                        throw new DataReadingException("Problem reading data", e);
                    }
                    return new Array4D<Number>(1, 1, yDimension.getLength(), xDimension.getLength()) {
                        @Override
                        public Number get(int... coords) {
                            int y = coords[3];
                            int x = coords[2];

                            /*
                             * Create a new index
                             */
                            Index index = arr.getIndex();
                            /*
                             * Set the index values
                             */
                            index.setDim(1, y);
                            index.setDim(0, x);

                            return arr.getFloat(index);
                        }

                        @Override
                        public void set(Number value, int... coords) {
                            throw new UnsupportedOperationException("Immutable array");
                        }
                    };
                }

                @Override
                public void close() throws IOException {
                    nc.close();
                }
            };
        }

        @Override
        protected DataReadingStrategy getDataReadingStrategy() {
            return dataReadingStrategy;
        }
    }

    /**
     * Opens the NetCDF dataset at the given location, using the dataset cache
     * if {@code location} represents an NcML aggregation. We cannot use the
     * cache for OPeNDAP or single NetCDF files because the underlying data may
     * have changed and the NetcdfDataset cache may cache a dataset forever. In
     * the case of NcML we rely on the fact that server administrators ought to
     * have set a "recheckEvery" parameter for NcML aggregations that may change
     * with time. It is desirable to use the dataset cache for NcML aggregations
     * because they can be time-consuming to assemble and we don't want to do
     * this every time a map is drawn.
     * 
     * @param location
     *            The location of the data: a local NetCDF file, an NcML
     *            aggregation file or an OPeNDAP location, {@literal i.e.}
     *            anything that can be passed to
     *            NetcdfDataset.openDataset(location).
     * 
     * @return a {@link NetcdfDataset} object for accessing the data at the
     *         given location.
     * 
     * @throws IOException
     *             if there was an error reading from the data source.
     */
    private NetcdfDataset openAndAggregateDataset(String location) throws IOException,
            EdalException {
        NetcdfDataset nc;
        if (location.startsWith("dods://") || location.startsWith("http://")) {
            /*
             * We have a remote dataset
             */
            nc = CdmUtils.openDataset(location);
        } else {
            /*
             * We have a local dataset
             */
            List<File> files = null;
            try {
                files = CdmUtils.expandGlobExpression(location);
            } catch (NullPointerException e) {
                System.out.println("NPE processing location: " + location);
                throw e;
            }
            if (files.size() == 0) {
                throw new EdalException("The location " + location
                        + " doesn't refer to any existing files.");
            }
            if (files.size() == 1) {
                location = files.get(0).getAbsolutePath();
                nc = CdmUtils.openDataset(location);
            } else {
                /*
                 * We have multiple files in a glob expression. We write some
                 * NcML and use the NetCDF aggregation libs to parse this into
                 * an aggregated dataset.
                 * 
                 * If we have already generated the ncML on a previous call,
                 * just use that.
                 */
                if (ncmlString == null) {
                    /*
                     * Find the name of the time dimension
                     */
                    NetcdfDataset first = openAndAggregateDataset(files.get(0).getAbsolutePath());
                    String timeDimName = null;
                    for (Variable var : first.getVariables()) {
                        if (var.isCoordinateVariable()) {
                            for (Attribute attr : var.getAttributes()) {
                                if (attr.getFullName().equalsIgnoreCase("units")
                                        && attr.getStringValue().contains(" since ")) {
                                    /*
                                     * This is the time dimension. Since this is
                                     * a co-ordinate variable, there is only 1
                                     * dimension
                                     */
                                    Dimension timeDimension = var.getDimension(0);
                                    timeDimName = timeDimension.getFullName();
                                }
                            }
                        }
                    }
                    first.close();
                    if (timeDimName == null) {
                        throw new EdalException(
                                "Cannot join multiple files without time dimensions");
                    }
                    /*
                     * We can't assume that the glob expression will have
                     * returned the files in time order.
                     * 
                     * We could assume that alphabetical == time ordered (and
                     * for properly named files it will - but let's not rely on
                     * our users having sensible naming conventions...
                     * 
                     * Sort the list using a comparator which opens the file and
                     * gets the first value of the time dimension
                     */
                    final String aggDimName = timeDimName;
                    Collections.sort(files, new Comparator<File>() {
                        @Override
                        public int compare(File ncFile1, File ncFile2) {
                            NetcdfFile nc1 = null;
                            NetcdfFile nc2 = null;
                            try {
                                nc1 = NetcdfFile.open(ncFile1.getAbsolutePath());
                                nc2 = NetcdfFile.open(ncFile2.getAbsolutePath());
                                Variable timeVar1 = nc1.findVariable(aggDimName);
                                Variable timeVar2 = nc2.findVariable(aggDimName);
                                long time1 = timeVar1.read().getLong(0);
                                long time2 = timeVar2.read().getLong(0);
                                return Long.compare(time1, time2);
                            } catch (Exception e) {
                                /*
                                 * There was a problem reading the data. Sort
                                 * alphanumerically by filename and hope for the
                                 * best...
                                 * 
                                 * This catches all exceptions because however
                                 * it fails this is still our best option.
                                 * 
                                 * If the error is a genuine problem, it'll show
                                 * up as soon as we try and aggregate.
                                 */
                                return ncFile1.getAbsolutePath().compareTo(
                                        ncFile2.getAbsolutePath());
                            } finally {
                                if (nc1 != null) {
                                    try {
                                        nc1.close();
                                    } catch (IOException e) {
                                        log.error("Problem closing netcdf file", e);
                                    }
                                }
                                if (nc2 != null) {
                                    try {
                                        nc2.close();
                                    } catch (IOException e) {
                                        log.error("Problem closing netcdf file", e);
                                    }
                                }
                            }
                        }
                    });

                    /*
                     * Now create the NcML string and use it to create an
                     * aggregated dataset
                     */
                    StringBuffer ncmlStringBuffer = new StringBuffer();
                    ncmlStringBuffer
                            .append("<netcdf xmlns=\"http://www.unidata.ucar.edu/namespaces/netcdf/ncml-2.2\">");
                    ncmlStringBuffer.append("<aggregation dimName=\"" + timeDimName
                            + "\" type=\"joinExisting\">");
                    for (File file : files) {
                        ncmlStringBuffer.append("<netcdf location=\"" + file.getAbsolutePath()
                                + "\"/>");
                    }
                    ncmlStringBuffer.append("</aggregation>");
                    ncmlStringBuffer.append("</netcdf>");

                    ncmlString = ncmlStringBuffer.toString();
                }
                nc = NcMLReader.readNcML(new StringReader(ncmlString), null);
            }
        }

        return nc;
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
        /*
         * TODO add boolean as to which way mask applies
         */
        private double min;
        private double max;
        private VariableMetadata diffMeta = null;
        private boolean inclusive = true;

        public ThresholdMaskPlugin(String var, double min, double max) {
            super(new String[] { var }, new String[] { MASK_SUFFIX });
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
            if(diffMeta != null) {
                diffMeta.getVariableProperties().put("threshold_min", min);
                diffMeta.getVariableProperties().put("threshold_max", max);
            }
        }
        
        public void setThresholdInclusive(boolean inclusive) {
            this.inclusive  = inclusive;
            if(diffMeta != null) {
                diffMeta.getVariableProperties().put("threshold_inclusive", inclusive ? 1 : 0);
            }
        }

        @Override
        protected VariableMetadata[] doProcessVariableMetadata(VariableMetadata... metadata)
                throws EdalException {
            VariableMetadata meta = metadata[0];

            diffMeta = newVariableMetadataFromMetadata(
                    new Parameter(getFullId(MASK_SUFFIX), meta.getParameter().getTitle() + " Mask",
                            "Mask of " + meta.getParameter().getDescription(),
                            "0: unmasked, 1: masked", null), true, meta);
            diffMeta.getVariableProperties().put("threshold_min", min);
            diffMeta.getVariableProperties().put("threshold_max", max);
            diffMeta.getVariableProperties().put("threshold_inclusive", inclusive ? "true" : "false");
            diffMeta.setParent(meta.getParent(), null);
            return new VariableMetadata[] { diffMeta };
        }

        @Override
        protected Number generateValue(String varSuffix, HorizontalPosition pos,
                Number... sourceValues) {
            try {
                if (sourceValues[0].doubleValue() <= min || sourceValues[0].doubleValue() >= max)
                    return inclusive ? 1 : 0;
                return inclusive ? 0 : 1;
            } catch (NullPointerException e) {
                return null;
            }
        }
    }

    public static void writeDataset(MaskedDataset dataset, String location) throws IOException,
            VariableNotFoundException, InvalidRangeException, DataReadingException {
        NetcdfFileWriter fileWriter = NetcdfFileWriter.createNew(Version.netcdf4, location);

        List<String> outputVariables = new ArrayList<>(Arrays.asList(dataset.compositePlugin
                .usesVariables()));
        outputVariables.addAll(dataset.getUnmaskedVariableNames());
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
                Dimension xDim = fileWriter.addDimension(null, "x", xSize);
                Dimension yDim = fileWriter.addDimension(null, "y", ySize);
                /*
                 * Define dimensions
                 */
                dims.add(xDim);
                dims.add(yDim);

                firstVar = false;
            }

            GridFeature feature = dataset.readFeature(varId);
            Array4D<Number> array4d = feature.getValues(varId);

            Variable variable;
            if (metadata.getId().endsWith(MASK_SUFFIX)) {
                ArrayShort.D2 values = new ArrayShort.D2(ySize, xSize);

                for (int y = 0; y < ySize; y++) {
                    for (int x = 0; x < xSize; x++) {
                        Number number = array4d.get(0, 0, y, x);
                        if (number == null) {
                            number = Float.NaN;
                        }
                        values.set(y, x, number.shortValue());
                    }
                }
                variable = fileWriter.addVariable(null, metadata.getId(), DataType.SHORT, dims);
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