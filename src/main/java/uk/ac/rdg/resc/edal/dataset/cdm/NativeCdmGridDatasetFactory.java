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

package uk.ac.rdg.resc.edal.dataset.cdm;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;
import ucar.nc2.dataset.NetcdfDataset;
import ucar.nc2.dataset.VariableDS;
import ucar.nc2.dt.GridDataset.Gridset;
import ucar.nc2.dt.GridDatatype;
import ucar.nc2.ncml.NcMLReader;
import uk.ac.rdg.resc.edal.dataset.AbstractGridDataset;
import uk.ac.rdg.resc.edal.dataset.DataReadingStrategy;
import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.dataset.DatasetFactory;
import uk.ac.rdg.resc.edal.dataset.GridDataSource;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.grid.HorizontalGrid;
import uk.ac.rdg.resc.edal.grid.RegularAxis;
import uk.ac.rdg.resc.edal.grid.RegularAxisImpl;
import uk.ac.rdg.resc.edal.grid.RegularGridImpl;
import uk.ac.rdg.resc.edal.grid.TimeAxis;
import uk.ac.rdg.resc.edal.grid.VerticalAxis;
import uk.ac.rdg.resc.edal.metadata.GridVariableMetadata;
import uk.ac.rdg.resc.edal.metadata.Parameter;
import uk.ac.rdg.resc.edal.util.cdm.CdmUtils;

/**
 * {@link DatasetFactory} that creates {@link Dataset}s representing gridded
 * data read through the Unidata Common Data Model.
 * 
 * @author Guy Griffiths
 * @author Jon
 */
public final class NativeCdmGridDatasetFactory extends DatasetFactory {
    private static final Logger log = LoggerFactory.getLogger(NativeCdmGridDatasetFactory.class);

    private String ncmlString = null;

    @Override
    public AbstractGridDataset createDataset(String id, String location) throws IOException,
            EdalException {
        NetcdfDataset nc = null;
        try {
            /*
             * Open the dataset, using the cache for NcML aggregations
             */
            nc = openAndAggregateDataset(location);

            ucar.nc2.dt.GridDataset gridDataset = CdmUtils.getGridDataset(nc);
            List<GridVariableMetadata> vars = new ArrayList<GridVariableMetadata>();

            for (Gridset gridset : gridDataset.getGridsets()) {

                VerticalAxis zDomain = null;
                TimeAxis tDomain = null;

                /*
                 * Create a VariableMetadata object for each GridDatatype
                 */
                for (GridDatatype grid : gridset.getGrids()) {
                    Dimension xDimension = grid.getXDimension();
                    Dimension yDimension = grid.getYDimension();
                    
                    RegularAxis xAxis = new RegularAxisImpl("x-axis", -0.5, 1.0, xDimension.getLength(), false);
                    RegularAxis yAxis = new RegularAxisImpl("y-axis", yDimension.getLength() - 0.5, -1.0, yDimension.getLength(), false);
                    HorizontalGrid hDomain = new RegularGridImpl(xAxis, yAxis, null);
                    
                    VariableDS variable = grid.getVariable();
                    String varId = variable.getFullName();
                    String name = getVariableName(variable);

                    Parameter parameter = new Parameter(varId, variable.getShortName(),
                            variable.getDescription(), variable.getUnitsString(), name);
                    GridVariableMetadata metadata = new GridVariableMetadata(
                            variable.getFullName(), parameter, hDomain, zDomain, tDomain, true);
                    vars.add(metadata);
                }
            }

            CdmGridDataset cdmGridDataset = new CdmGridDataset(id, location, vars,
                    CdmUtils.getOptimumDataReadingStrategy(nc));

            return cdmGridDataset;
        } finally {
            CdmUtils.closeDataset(nc);
        }
    }

    private final class CdmGridDataset extends AbstractGridDataset {
        private final String location;
        private final DataReadingStrategy dataReadingStrategy;

        public CdmGridDataset(String id, String location, Collection<GridVariableMetadata> vars,
                DataReadingStrategy dataReadingStrategy) {
            super(id, vars);
            this.location = location;
            this.dataReadingStrategy = dataReadingStrategy;
        }

        @Override
        protected GridDataSource openGridDataSource() throws IOException {
            NetcdfDataset nc;
            try {
                nc = openAndAggregateDataset(location);
            } catch (EdalException e) {
                throw new IOException("Problem aggregating datasets", e);
            }
            return new CdmGridDataSource(CdmUtils.getGridDataset(nc));
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
}