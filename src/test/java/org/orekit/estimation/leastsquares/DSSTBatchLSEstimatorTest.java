/* Copyright 2002-2018 CS Systèmes d'Information
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
package org.orekit.estimation.leastsquares;

import java.util.ArrayList;
import java.util.List;

import org.hipparchus.exception.LocalizedCoreFormats;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.linear.RealMatrix;
import org.hipparchus.optim.nonlinear.vector.leastsquares.LeastSquaresProblem.Evaluation;
import org.hipparchus.optim.nonlinear.vector.leastsquares.LevenbergMarquardtOptimizer;
import org.junit.Assert;
import org.junit.Test;
import org.orekit.attitudes.LofOffset;
import org.orekit.errors.OrekitException;
import org.orekit.errors.OrekitMessages;
import org.orekit.estimation.DSSTContext;
import org.orekit.estimation.DSSTEstimationTestUtils;
import org.orekit.estimation.measurements.DSSTRangeMeasurementCreator;
import org.orekit.estimation.measurements.DSSTRangeRateMeasurementCreator;
import org.orekit.estimation.measurements.EstimationsProvider;
import org.orekit.estimation.measurements.ObservedMeasurement;
import org.orekit.estimation.measurements.PVMeasurementCreator;
import org.orekit.estimation.measurements.Range;
import org.orekit.estimation.measurements.modifiers.OnBoardAntennaRangeModifier;
import org.orekit.frames.LOFType;
import org.orekit.orbits.Orbit;
import org.orekit.propagation.Propagator;
import org.orekit.propagation.conversion.DSSTPropagatorBuilder;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.ParameterDriver;
import org.orekit.utils.ParameterDriversList;

public class DSSTBatchLSEstimatorTest {

    /**
     * Perfect PV measurements with a perfect start
     * @throws OrekitException
     */
    @Test
    public void testKeplerPV() throws OrekitException {

        DSSTContext context = DSSTEstimationTestUtils.eccentricContext("regular-data:potential:tides");

        final DSSTPropagatorBuilder propagatorBuilder =
                        context.createBuilder(true, 1.0e-6, 60.0, 1.0);

        // create perfect PV measurements
        final Propagator propagator = DSSTEstimationTestUtils.createPropagator(context.initialOrbit,
                                                                           propagatorBuilder);
        final List<ObservedMeasurement<?>> measurements =
                        DSSTEstimationTestUtils.createMeasurements(propagator,
                                                               new PVMeasurementCreator(),
                                                               0.0, 1.0, 300.0);

        // create orbit estimator
        final BatchLSEstimator estimator = new BatchLSEstimator(new LevenbergMarquardtOptimizer(),
                                                                propagatorBuilder);
        for (final ObservedMeasurement<?> measurement : measurements) {
            estimator.addMeasurement(measurement);
        }
        estimator.setParametersConvergenceThreshold(1.0e-2);
        estimator.setMaxIterations(10);
        estimator.setMaxEvaluations(20);

        DSSTEstimationTestUtils.checkFit(context, estimator, 1, 2,
                                     0.0, 2.7e-9,
                                     0.0, 2.4e-8,
                                     0.0, 3.4e-9,
                                     0.0, 1.5e-12);

        RealMatrix normalizedCovariances = estimator.getOptimum().getCovariances(1.0e-10);
        RealMatrix physicalCovariances   = estimator.getPhysicalCovariances(1.0e-10);
        Assert.assertEquals(6,       normalizedCovariances.getRowDimension());
        Assert.assertEquals(6,       normalizedCovariances.getColumnDimension());
        Assert.assertEquals(6,       physicalCovariances.getRowDimension());
        Assert.assertEquals(6,       physicalCovariances.getColumnDimension());
        Assert.assertEquals(0.00258, physicalCovariances.getEntry(0, 0), 1.0e-5);

    }

    /** Test PV measurements generation and backward propagation in least-square orbit determination. */
    @Test
    public void testKeplerPVBackward() throws OrekitException {

        DSSTContext context = DSSTEstimationTestUtils.eccentricContext("regular-data:potential:tides");

        final DSSTPropagatorBuilder propagatorBuilder =
                        context.createBuilder(true, 1.0e-6, 60.0, 1.0);

        // create perfect PV measurements
        final Propagator propagator = DSSTEstimationTestUtils.createPropagator(context.initialOrbit,
                                                                           propagatorBuilder);
        final List<ObservedMeasurement<?>> measurements =
                        DSSTEstimationTestUtils.createMeasurements(propagator,
                                                               new PVMeasurementCreator(),
                                                               0.0, -1.0, 300.0);

        // create orbit estimator
        final BatchLSEstimator estimator = new BatchLSEstimator(new LevenbergMarquardtOptimizer(),
                                                                propagatorBuilder);
        for (final ObservedMeasurement<?> measurement : measurements) {
            estimator.addMeasurement(measurement);
        }
        estimator.setParametersConvergenceThreshold(1.0e-2);
        estimator.setMaxIterations(10);
        estimator.setMaxEvaluations(20);

        DSSTEstimationTestUtils.checkFit(context, estimator, 1, 2,
                                     0.0, 4.8e-9,
                                     0.0, 2.7e-8,
                                     0.0, 3.9e-9,
                                     0.0, 1.9e-12);

        RealMatrix normalizedCovariances = estimator.getOptimum().getCovariances(1.0e-10);
        RealMatrix physicalCovariances   = estimator.getPhysicalCovariances(1.0e-10);
        Assert.assertEquals(6,       normalizedCovariances.getRowDimension());
        Assert.assertEquals(6,       normalizedCovariances.getColumnDimension());
        Assert.assertEquals(6,       physicalCovariances.getRowDimension());
        Assert.assertEquals(6,       physicalCovariances.getColumnDimension());
        Assert.assertEquals(0.00258, physicalCovariances.getEntry(0, 0), 1.0e-5);

    }

    /**
     * Perfect range measurements with a biased start
     * @throws OrekitException
     */
    @Test
    public void testKeplerRange() throws OrekitException {

        DSSTContext context = DSSTEstimationTestUtils.eccentricContext("regular-data:potential:tides");

        final DSSTPropagatorBuilder propagatorBuilder =
                        context.createBuilder(true, 1.0e-6, 60.0, 1.0);

        // create perfect range measurements
        final Propagator propagator = DSSTEstimationTestUtils.createPropagator(context.initialOrbit,
                                                                           propagatorBuilder);
        final List<ObservedMeasurement<?>> measurements =
                        DSSTEstimationTestUtils.createMeasurements(propagator,
                                                               new DSSTRangeMeasurementCreator(context),
                                                               1.0, 3.0, 300.0);

        // create orbit estimator
        final BatchLSEstimator estimator = new BatchLSEstimator(new LevenbergMarquardtOptimizer(),
                                                                propagatorBuilder);
        for (final ObservedMeasurement<?> range : measurements) {
            estimator.addMeasurement(range);
        }
        estimator.setParametersConvergenceThreshold(1.0e-2);
        estimator.setMaxIterations(10);
        estimator.setMaxEvaluations(20);
        estimator.setObserver(new BatchLSObserver() {
            int lastIter = 0;
            int lastEval = 0;
            /** {@inheritDoc} */
            @Override
            public void evaluationPerformed(int iterationsCount, int evaluationscount,
                                            Orbit[] orbits,
                                            ParameterDriversList estimatedOrbitalParameters,
                                            ParameterDriversList estimatedPropagatorParameters,
                                            ParameterDriversList estimatedMeasurementsParameters,
                                            EstimationsProvider evaluationsProvider, Evaluation lspEvaluation)
                throws OrekitException {
                if (iterationsCount == lastIter) {
                    Assert.assertEquals(lastEval + 1, evaluationscount);
                } else {
                    Assert.assertEquals(lastIter + 1, iterationsCount);
                }
                lastIter = iterationsCount;
                lastEval = evaluationscount;
                Assert.assertEquals(measurements.size(), evaluationsProvider.getNumber());
                try {
                    evaluationsProvider.getEstimatedMeasurement(-1);
                    Assert.fail("an exception should have been thrown");
                } catch (OrekitException oe) {
                    Assert.assertEquals(LocalizedCoreFormats.OUT_OF_RANGE_SIMPLE, oe.getSpecifier());
                }
                try {
                    evaluationsProvider.getEstimatedMeasurement(measurements.size());
                    Assert.fail("an exception should have been thrown");
                } catch (OrekitException oe) {
                    Assert.assertEquals(LocalizedCoreFormats.OUT_OF_RANGE_SIMPLE, oe.getSpecifier());
                }
                AbsoluteDate previous = AbsoluteDate.PAST_INFINITY;
                for (int i = 0; i < evaluationsProvider.getNumber(); ++i) {
                    AbsoluteDate current = evaluationsProvider.getEstimatedMeasurement(i).getDate();
                    Assert.assertTrue(current.compareTo(previous) >= 0);
                    previous = current;
                }
            }
        });

        ParameterDriver aDriver = estimator.getOrbitalParametersDrivers(true).getDrivers().get(0);
        Assert.assertEquals("a", aDriver.getName());
        aDriver.setValue(aDriver.getValue() + 1.2);
        aDriver.setReferenceDate(AbsoluteDate.GALILEO_EPOCH);

        DSSTEstimationTestUtils.checkFit(context, estimator, 2, 3,
                                     0.0, 3.1e-6,
                                     0.0, 5.7e-6,
                                     0.0, 1.3e-6,
                                     0.0, 5.2e-10);

        // after the call to estimate, the parameters lacking a user-specified reference date
        // got a default one
        for (final ParameterDriver driver : estimator.getOrbitalParametersDrivers(true).getDrivers()) {
            if ("a".equals(driver.getName())) {
                // user-specified reference date
                Assert.assertEquals(0, driver.getReferenceDate().durationFrom(AbsoluteDate.GALILEO_EPOCH), 1.0e-15);
            } else {
                // default reference date
                Assert.assertEquals(0, driver.getReferenceDate().durationFrom(propagatorBuilder.getInitialOrbitDate()), 1.0e-15);
            }
        }

    }

    /**
     * Perfect range measurements with a biased start and an on-board antenna range offset 
     * @throws OrekitException
     */
    @Test
    public void testKeplerRangeWithOnBoardAntennaOffset() throws OrekitException {

        DSSTContext context = DSSTEstimationTestUtils.eccentricContext("regular-data:potential:tides");

        final DSSTPropagatorBuilder propagatorBuilder =
                        context.createBuilder(true, 1.0e-6, 60.0, 1.0);
        propagatorBuilder.setAttitudeProvider(new LofOffset(propagatorBuilder.getFrame(), LOFType.LVLH));
        final Vector3D antennaPhaseCenter = new Vector3D(-1.2, 2.3, -0.7);

        // create perfect range measurements with antenna offset
        final Propagator propagator = DSSTEstimationTestUtils.createPropagator(context.initialOrbit,
                                                                           propagatorBuilder);
        final List<ObservedMeasurement<?>> measurements =
                        DSSTEstimationTestUtils.createMeasurements(propagator,
                                                               new DSSTRangeMeasurementCreator(context, antennaPhaseCenter),
                                                               1.0, 3.0, 300.0);

        // create orbit estimator
        final BatchLSEstimator estimator = new BatchLSEstimator(new LevenbergMarquardtOptimizer(),
                                                                propagatorBuilder);
        final OnBoardAntennaRangeModifier obaModifier = new OnBoardAntennaRangeModifier(antennaPhaseCenter);
        for (final ObservedMeasurement<?> range : measurements) {
            ((Range) range).addModifier(obaModifier);
            estimator.addMeasurement(range);
        }
        estimator.setParametersConvergenceThreshold(1.0e-2);
        estimator.setMaxIterations(10);
        estimator.setMaxEvaluations(20);
        estimator.setObserver(new BatchLSObserver() {
            int lastIter = 0;
            int lastEval = 0;
            /** {@inheritDoc} */
            @Override
            public void evaluationPerformed(int iterationsCount, int evaluationscount,
                                            Orbit[] orbits,
                                            ParameterDriversList estimatedOrbitalParameters,
                                            ParameterDriversList estimatedPropagatorParameters,
                                            ParameterDriversList estimatedMeasurementsParameters,
                                            EstimationsProvider evaluationsProvider, Evaluation lspEvaluation)
                throws OrekitException {
                if (iterationsCount == lastIter) {
                    Assert.assertEquals(lastEval + 1, evaluationscount);
                } else {
                    Assert.assertEquals(lastIter + 1, iterationsCount);
                }
                lastIter = iterationsCount;
                lastEval = evaluationscount;
                Assert.assertEquals(measurements.size(), evaluationsProvider.getNumber());
                try {
                    evaluationsProvider.getEstimatedMeasurement(-1);
                    Assert.fail("an exception should have been thrown");
                } catch (OrekitException oe) {
                    Assert.assertEquals(LocalizedCoreFormats.OUT_OF_RANGE_SIMPLE, oe.getSpecifier());
                }
                try {
                    evaluationsProvider.getEstimatedMeasurement(measurements.size());
                    Assert.fail("an exception should have been thrown");
                } catch (OrekitException oe) {
                    Assert.assertEquals(LocalizedCoreFormats.OUT_OF_RANGE_SIMPLE, oe.getSpecifier());
                }
                AbsoluteDate previous = AbsoluteDate.PAST_INFINITY;
                for (int i = 0; i < evaluationsProvider.getNumber(); ++i) {
                    AbsoluteDate current = evaluationsProvider.getEstimatedMeasurement(i).getDate();
                    Assert.assertTrue(current.compareTo(previous) >= 0);
                    previous = current;
                }
            }
        });

        ParameterDriver aDriver = estimator.getOrbitalParametersDrivers(true).getDrivers().get(0);
        Assert.assertEquals("a", aDriver.getName());
        aDriver.setValue(aDriver.getValue() + 1.2);
        aDriver.setReferenceDate(AbsoluteDate.GALILEO_EPOCH);

        DSSTEstimationTestUtils.checkFit(context, estimator, 2, 3,
                                     0.0, 2.3e-5,
                                     0.0, 5.9e-5,
                                     0.0, 2.7e-5,
                                     0.0, 1.1e-8);

        // after the call to estimate, the parameters lacking a user-specified reference date
        // got a default one
        for (final ParameterDriver driver : estimator.getOrbitalParametersDrivers(true).getDrivers()) {
            if ("a".equals(driver.getName())) {
                // user-specified reference date
                Assert.assertEquals(0, driver.getReferenceDate().durationFrom(AbsoluteDate.GALILEO_EPOCH), 1.0e-15);
            } else {
                // default reference date
                Assert.assertEquals(0, driver.getReferenceDate().durationFrom(propagatorBuilder.getInitialOrbitDate()), 1.0e-15);
            }
        }

    }

    @Test
    public void testWrappedException() throws OrekitException {

        DSSTContext context = DSSTEstimationTestUtils.eccentricContext("regular-data:potential:tides");

        final DSSTPropagatorBuilder propagatorBuilder =
                        context.createBuilder(true, 1.0e-6, 60.0, 1.0);

        // create perfect range measurements
        final Propagator propagator = DSSTEstimationTestUtils.createPropagator(context.initialOrbit,
                                                                               propagatorBuilder);
        final List<ObservedMeasurement<?>> measurements =
                        DSSTEstimationTestUtils.createMeasurements(propagator,
                                                               new DSSTRangeMeasurementCreator(context),
                                                               1.0, 3.0, 300.0);

        // create orbit estimator
        final BatchLSEstimator estimator = new BatchLSEstimator(new LevenbergMarquardtOptimizer(),
                                                                propagatorBuilder);
        for (final ObservedMeasurement<?> range : measurements) {
            estimator.addMeasurement(range);
        }
        estimator.setParametersConvergenceThreshold(1.0e-2);
        estimator.setMaxIterations(10);
        estimator.setMaxEvaluations(20);
        estimator.setObserver(new BatchLSObserver() {
            /** {@inheritDoc} */
            @Override
            public void evaluationPerformed(int iterationsCount, int evaluationscount,
                                           Orbit[] orbits,
                                           ParameterDriversList estimatedOrbitalParameters,
                                           ParameterDriversList estimatedPropagatorParameters,
                                           ParameterDriversList estimatedMeasurementsParameters,
                                           EstimationsProvider evaluationsProvider, Evaluation lspEvaluation) throws DummyException {
                throw new DummyException();
            }
        });

        try {
            DSSTEstimationTestUtils.checkFit(context, estimator, 3, 4,
                                         0.0, 1.5e-6,
                                         0.0, 3.2e-6,
                                         0.0, 3.8e-7,
                                         0.0, 1.5e-10);
            Assert.fail("an exception should have been thrown");
        } catch (DummyException de) {
            // expected
        }

    }

    private static class DummyException extends OrekitException {
        private static final long serialVersionUID = 1L;
        public DummyException() {
            super(OrekitMessages.INTERNAL_ERROR);
        }
    }

    /**
     * Perfect range rate measurements with a perfect start
     * @throws OrekitException
     */
    @Test
    public void testKeplerRangeRate() throws OrekitException {

        DSSTContext context = DSSTEstimationTestUtils.eccentricContext("regular-data:potential:tides");

        final DSSTPropagatorBuilder propagatorBuilder =
                        context.createBuilder(true, 1.0e-6, 60.0, 1.0);

        // create perfect range rate measurements
        final Propagator propagator = DSSTEstimationTestUtils.createPropagator(context.initialOrbit,
                                                                           propagatorBuilder);
        final List<ObservedMeasurement<?>> measurements1 =
                        DSSTEstimationTestUtils.createMeasurements(propagator,
                                                               new DSSTRangeRateMeasurementCreator(context, false),
                                                               1.0, 3.0, 300.0);

        final List<ObservedMeasurement<?>> measurements = new ArrayList<ObservedMeasurement<?>>();
        measurements.addAll(measurements1);

        // create orbit estimator
        final BatchLSEstimator estimator = new BatchLSEstimator(new LevenbergMarquardtOptimizer(),
                                                                propagatorBuilder);
        for (final ObservedMeasurement<?> rangerate : measurements) {
            estimator.addMeasurement(rangerate);
        }
        estimator.setParametersConvergenceThreshold(1.0e-3);
        estimator.setMaxIterations(10);
        estimator.setMaxEvaluations(20);

        DSSTEstimationTestUtils.checkFit(context, estimator, 3, 6,
                                     0.0, 1.6e-2,
                                     0.0, 3.4e-2,
                                     0.0, 170.0,  // we only have range rate...
                                     0.0, 6.5e-2);
    }

    /**
     * Perfect range and range rate measurements with a perfect start
     * @throws OrekitException
     */
    @Test
    public void testKeplerRangeAndRangeRate() throws OrekitException {

        DSSTContext context = DSSTEstimationTestUtils.eccentricContext("regular-data:potential:tides");

        final DSSTPropagatorBuilder propagatorBuilder =
                        context.createBuilder(true, 1.0e-6, 60.0, 1.0);

        // create perfect range measurements
        final Propagator propagator = DSSTEstimationTestUtils.createPropagator(context.initialOrbit,
                                                                           propagatorBuilder);

        final List<ObservedMeasurement<?>> measurementsRange =
                        DSSTEstimationTestUtils.createMeasurements(propagator,
                                                               new DSSTRangeMeasurementCreator(context),
                                                               1.0, 3.0, 300.0);
        final List<ObservedMeasurement<?>> measurementsRangeRate =
                        DSSTEstimationTestUtils.createMeasurements(propagator,
                                                               new DSSTRangeRateMeasurementCreator(context, false),
                                                               1.0, 3.0, 300.0);

        // concat measurements
        final List<ObservedMeasurement<?>> measurements = new ArrayList<ObservedMeasurement<?>>();
        measurements.addAll(measurementsRange);
        measurements.addAll(measurementsRangeRate);

        // create orbit estimator
        final BatchLSEstimator estimator = new BatchLSEstimator(new LevenbergMarquardtOptimizer(),
                                                                propagatorBuilder);
        for (final ObservedMeasurement<?> meas : measurements) {
            estimator.addMeasurement(meas);
        }
        estimator.setParametersConvergenceThreshold(1.0e-3);
        estimator.setMaxIterations(10);
        estimator.setMaxEvaluations(20);

        // we have low correlation between the two types of measurement. We can expect a good estimate.
        DSSTEstimationTestUtils.checkFit(context, estimator, 1, 2,
                                     0.0, 0.16,
                                     0.0, 0.40,
                                     0.0, 1.9e-3,
                                     0.0, 7.3e-7);
    }

}
