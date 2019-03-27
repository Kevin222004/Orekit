/* Copyright 2002-2019 CS Systèmes d'Information
 * Licensed to CS Systèmes d'Information (CS) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * CS licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.orekit.estimation.measurements.gnss;

import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.stream.Collectors;

import org.hipparchus.linear.RealMatrix;
import org.hipparchus.util.FastMath;
import org.orekit.errors.OrekitIllegalArgumentException;
import org.orekit.errors.OrekitMessages;
import org.orekit.utils.ParameterDriver;

/** Base class for integer ambiguity solving algorithms.
 * @see LambdaMethod
 * @see ModifiedLambdaMethod
 * @author Luc Maisonobe
 * @since 10.0
 */
public class AmbiguitySolver {

    /** Number of solutions of the ILS problem to find. */
    private static final int NB_SOLUTIONS = 2;

    /** Drivers for ambiguity drivers. */
    private final List<ParameterDriver> ambiguityDrivers;

    /** Solver for the underlying Integer Least Square problem. */
    private final IntegerLeastSquareSolver solver;

    /** Simple constructor.
     * @param ambiguityDrivers drivers for ambiguity parameters
     * @param solver solver for the underlying Integer Least Square problem
     * @see LambdaMethod
     * @see ModifiedLambdaMethod
     */
    public AmbiguitySolver(final List<ParameterDriver> ambiguityDrivers,
                           final IntegerLeastSquareSolver solver) {
        this.ambiguityDrivers = ambiguityDrivers;
        this.solver           = solver;
    }

    /** Get all the ambiguity parameters drivers.
     * @return all ambiguity parameters drivers
     */
    public List<ParameterDriver> getAllAmbiguityDrivers() {
        return Collections.unmodifiableList(ambiguityDrivers);
    }

    /** Get the ambiguity parameters drivers that have not been fixed yet.
     * @return ambiguity parameters drivers that have not been fixed yet
     */
    protected List<ParameterDriver> getFreeAmbiguityDrivers() {
        return ambiguityDrivers.
                        stream().
                        filter(d -> {
                            if (d.isSelected()) {
                                final double near   = FastMath.rint(d.getValue());
                                final double gapMin = near - d.getMinValue();
                                final double gapMax = d.getMaxValue() - near;
                                return FastMath.max(FastMath.abs(gapMin), FastMath.abs(gapMax)) > 1.0e-15;
                            } else {
                                return false;
                            }
                        }).
                        collect(Collectors.toList());
    }

    /** Get ambiguity indirection array for ambiguity parameters drivers that have not been fixed yet.
     * @param startIndex start index for measurements parameters in global covariance matrix
     * @param measurementsParametersDrivers measurements parameters drivers in global covariance matrix order
     * @return indirection array between full covariance matrix and ambiguity covariance matrix
     */
    protected int[] getFreeAmbiguityIndirection(final int startIndex,
                                                final List<ParameterDriver> measurementsParametersDrivers) {

        // set up indirection array
        final List<ParameterDriver> freeDrivers = getFreeAmbiguityDrivers();
        final int n = freeDrivers.size();
        final int[] indirection = new int[n];
        for (int i = 0; i < n; ++i) {
            indirection[i] = -1;
            final String name = freeDrivers.get(i).getName();
            for (int k = 0; k < measurementsParametersDrivers.size(); ++k) {
                if (name.equals(measurementsParametersDrivers.get(k).getName())) {
                    indirection[i] = startIndex + k;
                    break;
                }
            }
            if (indirection[i] < 0) {
                // the parameter was not found
                final StringBuilder builder = new StringBuilder();
                for (final ParameterDriver driver : measurementsParametersDrivers) {
                    if (builder.length() > 0) {
                        builder.append(", ");
                    }
                    builder.append(driver.getName());
                }
                throw new OrekitIllegalArgumentException(OrekitMessages.UNSUPPORTED_PARAMETER_NAME,
                                                         name, builder.toString());
            }
        }

        return indirection;

    }

    /** Un-fix an integer ambiguity (typically after a phase cycle slip).
     * @param ambiguityDriver driver for the ambiguity to un-fix
     */
    public void unFixAmbiguity(final ParameterDriver ambiguityDriver) {
        ambiguityDriver.setMinValue(Double.NEGATIVE_INFINITY);
        ambiguityDriver.setMaxValue(Double.POSITIVE_INFINITY);
    }

    /** Fix integer ambiguities.
     * @param startIndex start index for measurements parameters in global covariance matrix
     * @param measurementsParametersDrivers measurements parameters drivers in global covariance matrix order
     * @param covariance global covariance matrix
     * @return list of newly fixed ambiguities (ambiguities already fixed before the call are not counted)
     */
    public List<ParameterDriver> fixIntegerAmbiguities(final int startIndex,
                                                       final List<ParameterDriver> measurementsParametersDrivers,
                                                       final RealMatrix covariance) {

        // set up Integer Least Square problem
        final List<ParameterDriver> ambiguities      = getAllAmbiguityDrivers();
        final double[]              floatAmbiguities = ambiguities.stream().mapToDouble(d -> d.getValue()).toArray();
        final int[]                 indirection      = getFreeAmbiguityIndirection(startIndex, measurementsParametersDrivers);

        // solve the ILS problem
        final double chi = 100; // TODO
        final SortedSet<IntegerLeastSquareSolution> solutions =
                        solver.solveILS(floatAmbiguities, indirection, covariance, NB_SOLUTIONS, chi);
        if (solutions.size() < NB_SOLUTIONS) {
            return Collections.emptyList();
        }

        // TODO
        return null;

    }

}
