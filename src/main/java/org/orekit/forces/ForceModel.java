/* Copyright 2002-2023 CS GROUP
 * Licensed to CS GROUP (CS) under one or more
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
package org.orekit.forces;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.hipparchus.CalculusFieldElement;
import org.hipparchus.Field;
import org.hipparchus.geometry.euclidean.threed.FieldVector3D;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.events.Action;
import org.hipparchus.util.MathArrays;
import org.orekit.propagation.FieldSpacecraftState;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.events.EventDetector;
import org.orekit.propagation.events.FieldDateDetector;
import org.orekit.propagation.events.FieldEventDetector;
import org.orekit.propagation.numerical.FieldTimeDerivativesEquations;
import org.orekit.propagation.numerical.TimeDerivativesEquations;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.FieldAbsoluteDate;
import org.orekit.utils.ParameterDriver;
import org.orekit.utils.ParametersDriversProvider;
import org.orekit.utils.TimeSpanMap.Span;

/** This interface represents a force modifying spacecraft motion.
 *
 * <p>
 * Objects implementing this interface are intended to be added to a
 * {@link org.orekit.propagation.numerical.NumericalPropagator numerical propagator}
 * before the propagation is started.
 *
 * <p>
 * The propagator will call at each step the {@link #addContribution(SpacecraftState,
 * TimeDerivativesEquations)} method. The force model instance will extract all the
 * state data it needs (date, position, velocity, frame, attitude, mass) from the first
 * parameter. From these state data, it will compute the perturbing acceleration. It
 * will then add this acceleration to the second parameter which will take thins
 * contribution into account and will use the Gauss equations to evaluate its impact
 * on the global state derivative.
 * </p>
 * <p>
 * Force models which create discontinuous acceleration patterns (typically for maneuvers
 * start/stop or solar eclipses entry/exit) must provide one or more {@link
 * org.orekit.propagation.events.EventDetector events detectors} to the
 * propagator thanks to their {@link #getEventsDetectors()} method. This method
 * is called once just before propagation starts. The events states will be checked by
 * the propagator to ensure accurate propagation and proper events handling.
 * </p>
 *
 * @author Mathieu Rom&eacute;ro
 * @author Luc Maisonobe
 * @author V&eacute;ronique Pommier-Maurussane
 * @author Melina Vanel
 */
public interface ForceModel extends ParametersDriversProvider {

    /**
     * Initialize the force model at the start of propagation. This method will be called
     * before any calls to {@link #addContribution(SpacecraftState, TimeDerivativesEquations)},
     * {@link #addContribution(FieldSpacecraftState, FieldTimeDerivativesEquations)},
     * {@link #acceleration(SpacecraftState, double[])} or {@link #acceleration(FieldSpacecraftState, CalculusFieldElement[])}
     *
     * <p> The default implementation of this method does nothing.</p>
     *
     * @param initialState spacecraft state at the start of propagation.
     * @param target       date of propagation. Not equal to {@code initialState.getDate()}.
     */
    default void init(SpacecraftState initialState, AbsoluteDate target) {
    }

    /**
     * Initialize the force model at the start of propagation. This method will be called
     * before any calls to {@link #addContribution(SpacecraftState, TimeDerivativesEquations)},
     * {@link #addContribution(FieldSpacecraftState, FieldTimeDerivativesEquations)},
     * {@link #acceleration(SpacecraftState, double[])} or {@link #acceleration(FieldSpacecraftState, CalculusFieldElement[])}
     *
     * <p> The default implementation of this method does nothing.</p>
     *
     * @param initialState spacecraft state at the start of propagation.
     * @param target       date of propagation. Not equal to {@code initialState.getDate()}.
     * @param <T> type of the elements
     */
    default <T extends CalculusFieldElement<T>> void init(FieldSpacecraftState<T> initialState, FieldAbsoluteDate<T> target) {
        init(initialState.toSpacecraftState(), target.toAbsoluteDate());
    }

    /** Compute the contribution of the force model to the perturbing
     * acceleration.
     * <p>
     * The default implementation simply adds the {@link #acceleration(SpacecraftState, double[]) acceleration}
     * as a non-Keplerian acceleration.
     * </p>
     * @param s current state information: date, kinematics, attitude
     * @param adder object where the contribution should be added
     */
    default void addContribution(SpacecraftState s, TimeDerivativesEquations adder) {
        adder.addNonKeplerianAcceleration(acceleration(s, getParameters(s.getDate())));
    }

    /** Compute the contribution of the force model to the perturbing
     * acceleration.
     * @param s current state information: date, kinematics, attitude
     * @param adder object where the contribution should be added
     * @param <T> type of the elements
     */
    default <T extends CalculusFieldElement<T>> void addContribution(FieldSpacecraftState<T> s, FieldTimeDerivativesEquations<T> adder) {
        adder.addNonKeplerianAcceleration(acceleration(s, getParameters(s.getDate().getField(), s.getDate())));
    }


    /** Get total number of spans for all the parameters driver.
     * @return total number of span to be estimated
     * @since 12.0
     */
    default int getNbParametersDriversValue() {
        int totalSpan = 0;
        final List<ParameterDriver> allParameters = getParametersDrivers();
        for (ParameterDriver dragDriver : allParameters) {
            totalSpan += dragDriver.getNbOfValues();
        }
        return totalSpan;
    }

    /** Get force model parameters.
     * @return force model parameters, will throw an
     * exception if one PDriver of the force has several values driven. If
     * it's the case (if at least 1 PDriver of the force model has several values
     * driven) the method {@link #getParameters(AbsoluteDate)} must be used.
     * @since 12.0
     */
    default double[] getParameters() {

        final List<ParameterDriver> drivers = getParametersDrivers();
        final double[] parameters = new double[drivers.size()];
        for (int i = 0; i < drivers.size(); ++i) {
            parameters[i] = drivers.get(i).getValue();
        }
        return parameters;
    }

    /** Get force model parameters.
     * @param date date at which the parameters want to be known, can
     * be new AbsoluteDate() if all the parameters have no validity period
     * that is to say that they have only 1 estimated value over the all
     * interval
     * @return force model parameters
     * @since 12.0
     */
    default double[] getParameters(AbsoluteDate date) {

        final List<ParameterDriver> drivers = getParametersDrivers();
        final double[] parameters = new double[drivers.size()];
        for (int i = 0; i < drivers.size(); ++i) {
            parameters[i] = drivers.get(i).getValue(date);
        }
        return parameters;
    }

    /** Get force model parameters, return a list a all span values
     * of all force parameters.
     * @return force model parameters
     * @since 12.0
     */
    default double[] getParametersAllValues() {

        final List<ParameterDriver> drivers = getParametersDrivers();
        final int nbParametersValues = getNbParametersDriversValue();
        final double[] parameters = new double[nbParametersValues];
        int paramIndex = 0;
        for (int i = 0; i < drivers.size(); ++i) {
            for (Span<Double> span = drivers.get(i).getValueSpanMap().getFirstSpan(); span != null; span = span.next()) {
                parameters[paramIndex++] = span.getData();
            }

        }
        return parameters;
    }

    /** Get force model parameters.
     * @param field field to which the elements belong
     * @param <T> type of the elements
     * @return force model parameters
     * @since 9.0
     */
    default <T extends CalculusFieldElement<T>> T[] getParametersAllValues(final Field<T> field) {
        final List<ParameterDriver> drivers = getParametersDrivers();
        final int nbParametersValues = getNbParametersDriversValue();
        final T[] parameters = MathArrays.buildArray(field, nbParametersValues);
        int paramIndex = 0;
        for (int i = 0; i < drivers.size(); ++i) {
            for (Span<Double> span = drivers.get(i).getValueSpanMap().getFirstSpan(); span != null; span = span.next()) {
                parameters[paramIndex++] = field.getZero().add(span.getData());
            }
        }
        return parameters;
    }


    /** Get force model parameters.
     * @param field field to which the elements belong
     * @param <T> type of the elements
     * @return force model parameters, will throw an
     * exception if one PDriver of the force has several values driven. If
     * it's the case (if at least 1 PDriver of the force model has several values
     * driven) the method {@link #getParameters(Field, FieldAbsoluteDate)} must be used.
     * @since 9.0
     */
    default <T extends CalculusFieldElement<T>> T[] getParameters(final Field<T> field) {
        final List<ParameterDriver> drivers = getParametersDrivers();
        final T[] parameters = MathArrays.buildArray(field, drivers.size());
        for (int i = 0; i < drivers.size(); ++i) {
            parameters[i] = field.getZero().add(drivers.get(i).getValue());
        }
        return parameters;
    }

    /** Get force model parameters.
     * @param field field to which the elements belong
     * @param <T> type of the elements
     * @param date field date at which the parameters want to be known, can
     * be new AbsoluteDate() if all the parameters have no validity period.
     * @return force model parameters
     * @since 9.0
     */
    default <T extends CalculusFieldElement<T>> T[] getParameters(final Field<T> field, final FieldAbsoluteDate<T> date) {
        final List<ParameterDriver> drivers = getParametersDrivers();
        final T[] parameters = MathArrays.buildArray(field, drivers.size());
        for (int i = 0; i < drivers.size(); ++i) {
            parameters[i] = field.getZero().add(drivers.get(i).getValue(date.toAbsoluteDate()));
        }
        return parameters;
    }

    /** Check if force models depends on position only.
     * @return true if force model depends on position only, false
     * if it depends on velocity, either directly or due to a dependency
     * on attitude
     * @since 9.0
     */
    boolean dependsOnPositionOnly();

    /** Compute acceleration.
     * @param s current state information: date, kinematics, attitude
     * @param parameters values of the force model parameters at state date,
     * only 1 value for each parameterDriver
     * @return acceleration in same frame as state
     * @since 9.0
     */
    Vector3D acceleration(SpacecraftState s, double[] parameters);

    /** Compute acceleration.
     * @param s current state information: date, kinematics, attitude
     * @param parameters values of the force model parameters at state date,
     * only 1 value for each parameterDriver
     * @return acceleration in same frame as state
     * @param <T> type of the elements
     * @since 9.0
     */
    <T extends CalculusFieldElement<T>> FieldVector3D<T> acceleration(FieldSpacecraftState<T> s, T[] parameters);

    /** Get the discrete events related to the model.
     * A date detector is used to cleanly stop the propagator and reset
     * the state derivatives at transition dates, useful when force parameter
     * drivers contains several values.
     * @return stream of events detectors
     */
    default Stream<EventDetector> getEventsDetectors() {
        // If force model does not have parameter Driver, an empty stream is given as results
        final ArrayList<AbsoluteDate> transitionDates = new ArrayList<>();
        for (ParameterDriver driver : getParametersDrivers()) {
            // Get the transitions' dates from the TimeSpanMap
            for (AbsoluteDate date : driver.getTransitionDates()) {
                transitionDates.add(date);
            }
        }
        // Either force model does not have any parameter driver or only contains parameter driver with only 1 span
        if (transitionDates.size() == 0) {
            return Stream.empty();

        } else {
            transitionDates.sort(null);
            // Initialize the date detector
            final DateDetector datesDetector = new DateDetector(transitionDates.get(0)).
                    withMaxCheck(60.).
                    withHandler(( state, d, increasing) -> {
                        return Action.RESET_DERIVATIVES;
                    });
            // Add all transitions' dates to the date detector
            for (int i = 1; i < transitionDates.size(); i++) {
                datesDetector.addEventDate(transitionDates.get(i));
            }
            // Return the detector
            return Stream.of(datesDetector);
        }
    }

    /** Get the discrete events related to the model.
     * @param field field to which the state belongs
     * @param <T> extends CalculusFieldElement&lt;T&gt;
     * @return stream of events detectors
     */
    default <T extends CalculusFieldElement<T>> Stream<FieldEventDetector<T>> getFieldEventsDetectors(Field<T> field) {
        // If force model does not have parameter Driver, an empty stream is given as results
        final ArrayList<AbsoluteDate> transitionDates = new ArrayList<>();
        for (ParameterDriver driver : getParametersDrivers()) {
        // Get the transitions' dates from the TimeSpanMap
            for (AbsoluteDate date : driver.getTransitionDates()) {
                transitionDates.add(date);
            }
        }
        // Either force model does not have any parameter driver or only contains parameter driver with only 1 span
        if (transitionDates.size() == 0) {
            return Stream.empty();

        } else {
            transitionDates.sort(null);
            // Initialize the date detector
            final FieldDateDetector<T> datesDetector =
                    new FieldDateDetector<>(new FieldAbsoluteDate<>(field, transitionDates.get(0))).
                    withMaxCheck(field.getZero().add(60.)).
                    withHandler(( state, d, increasing) -> {
                        return Action.RESET_DERIVATIVES;
                    });
            // Add all transitions' dates to the date detector
            for (int i = 1; i < transitionDates.size(); i++) {
                datesDetector.addEventDate(new FieldAbsoluteDate<>(field, transitionDates.get(i)));
            }
            // Return the detector
            return Stream.of(datesDetector);
        }
    }

    /** Get parameter value from its name.
     * @param name parameter name
     * @return parameter value
     * @since 8.0
     */
    ParameterDriver getParameterDriver(String name);

    /** Check if a parameter is supported.
     * <p>Supported parameters are those listed by {@link #getParametersDrivers()}.</p>
     * @param name parameter name to check
     * @return true if the parameter is supported
     * @see #getParametersDrivers()
     */
    boolean isSupported(String name);

}
