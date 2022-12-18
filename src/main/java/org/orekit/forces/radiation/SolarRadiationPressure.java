/* Copyright 2002-2022 CS GROUP
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
package org.orekit.forces.radiation;

import java.lang.reflect.Array;
import java.util.List;

import org.hipparchus.CalculusFieldElement;
import org.hipparchus.geometry.euclidean.threed.FieldVector3D;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.hipparchus.util.Precision;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.frames.Frame;
import org.orekit.propagation.FieldSpacecraftState;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.FieldAbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.ExtendedPVCoordinatesProvider;
import org.orekit.utils.FrameAdapter;
import org.orekit.utils.OccultationEngine;
import org.orekit.utils.ParameterDriver;

/** Solar radiation pressure force model.
 * <p>
 * Since Orekit 11.0, it is possible to take into account
 * the eclipses generated by Moon in the solar radiation
 * pressure force model using the
 * {@link #addOccultingBody(ExtendedPVCoordinatesProvider, double)}
 * method.
 * <p>
 * Example:<br>
 * <code> SolarRadiationPressure srp = </code>
 * <code>                      new SolarRadiationPressure(CelestialBodyFactory.getSun(), Constants.EIGEN5C_EARTH_EQUATORIAL_RADIUS,</code>
 * <code>                                     new IsotropicRadiationClassicalConvention(50.0, 0.5, 0.5));</code><br>
 * <code> srp.addOccultingBody(CelestialBodyFactory.getMoon(), Constants.MOON_EQUATORIAL_RADIUS);</code><br>
 *
 * @author Fabien Maussion
 * @author &Eacute;douard Delente
 * @author V&eacute;ronique Pommier-Maurussane
 * @author Pascal Parraud
 */
public class SolarRadiationPressure extends AbstractRadiationForceModel {

    /** Reference distance for the solar radiation pressure (m). */
    private static final double D_REF = 149597870000.0;

    /** Reference solar radiation pressure at D_REF (N/m²). */
    private static final double P_REF = 4.56e-6;

    /** Margin to force recompute lighting ratio derivatives when we are really inside penumbra. */
    private static final double ANGULAR_MARGIN = 1.0e-10;

    /** Reference flux normalized for a 1m distance (N). */
    private final double kRef;

    /** Sun model. */
    private final ExtendedPVCoordinatesProvider sun;

    /** Spacecraft. */
    private final RadiationSensitive spacecraft;

    /** Simple constructor with default reference values.
     * <p>When this constructor is used, the reference values are:</p>
     * <ul>
     *   <li>d<sub>ref</sub> = 149597870000.0 m</li>
     *   <li>p<sub>ref</sub> = 4.56 10<sup>-6</sup> N/m²</li>
     * </ul>
     * @param sun Sun model
     * @param centralBody central body shape model (for umbra/penumbra computation)
     * @param spacecraft the object physical and geometrical information
     * @since 12.0
     */
    public SolarRadiationPressure(final ExtendedPVCoordinatesProvider sun,
                                  final OneAxisEllipsoid centralBody,
                                  final RadiationSensitive spacecraft) {
        this(D_REF, P_REF, sun, centralBody, spacecraft);
    }

    /** Complete constructor.
     * <p>Note that reference solar radiation pressure <code>pRef</code> in
     * N/m² is linked to solar flux SF in W/m² using
     * formula pRef = SF/c where c is the speed of light (299792458 m/s). So
     * at 1UA a 1367 W/m² solar flux is a 4.56 10<sup>-6</sup>
     * N/m² solar radiation pressure.</p>
     * @param dRef reference distance for the solar radiation pressure (m)
     * @param pRef reference solar radiation pressure at dRef (N/m²)
     * @param sun Sun model
     * @param centralBody central body shape model (for umbra/penumbra computation)
     * @param spacecraft the object physical and geometrical information
     * @since 12.0
     */
    public SolarRadiationPressure(final double dRef, final double pRef,
                                  final ExtendedPVCoordinatesProvider sun,
                                  final OneAxisEllipsoid centralBody,
                                  final RadiationSensitive spacecraft) {
        super(sun, centralBody);
        this.kRef = pRef * dRef * dRef;
        this.sun  = sun;
        this.spacecraft = spacecraft;
    }

    /** {@inheritDoc} */
    @Override
    public Vector3D acceleration(final SpacecraftState s, final double[] parameters) {

        final AbsoluteDate date         = s.getDate();
        final Frame        frame        = s.getFrame();
        final Vector3D     position     = s.getPosition();
        final Vector3D     sunSatVector = position.subtract(sun.getPosition(date, frame));
        final double       r2           = sunSatVector.getNormSq();

        // compute flux
        final double   ratio = getLightingRatio(s);
        final double   rawP  = ratio  * kRef / r2;
        final Vector3D flux  = new Vector3D(rawP / FastMath.sqrt(r2), sunSatVector);

        return spacecraft.radiationPressureAcceleration(date, frame, position, s.getAttitude().getRotation(),
                                                        s.getMass(), flux, parameters);

    }

    /** {@inheritDoc} */
    @Override
    public <T extends CalculusFieldElement<T>> FieldVector3D<T> acceleration(final FieldSpacecraftState<T> s,
                                                                             final T[] parameters) {

        final FieldAbsoluteDate<T> date         = s.getDate();
        final Frame                frame        = s.getFrame();
        final FieldVector3D<T>     position     = s.getPosition();
        final FieldVector3D<T>     sunSatVector = position.subtract(sun.getPosition(date, frame));
        final T                    r2           = sunSatVector.getNormSq();

        // compute flux
        final T                ratio = getLightingRatio(s);
        final T                rawP  = ratio.multiply(kRef).divide(r2);
        final FieldVector3D<T> flux  = new FieldVector3D<>(rawP.divide(r2.sqrt()), sunSatVector);

        return spacecraft.radiationPressureAcceleration(date, frame, position, s.getAttitude().getRotation(),
                                                        s.getMass(), flux, parameters);

    }

    /** Get the lighting ratio ([0-1]).
     * @param state spacecraft state
     * @return lighting ratio
     * @since 7.1
     */
    public double getLightingRatio(final SpacecraftState state) {

        final Vector3D sunPosition = sun.getPosition(state.getDate(), state.getFrame());
        if (sunPosition.getNorm() < 2 * Constants.SUN_RADIUS) {
            // we are in fact computing a trajectory around Sun (or solar system barycenter),
            // not around a planet, we consider lighting ratio is always 1
            return 1.0;
        }

        final List<OccultationEngine> occultingBodies = getOccultingBodies();
        final int n = occultingBodies.size();

        final OccultationEngine.OccultationAngles[] angles = new OccultationEngine.OccultationAngles[n];
        for (int i = 0; i < n; ++i) {
            angles[i] = occultingBodies.get(i).angles(state);
        }
        final double alphaSunSq = angles[0].getOccultedApparentRadius() * angles[0].getOccultedApparentRadius();

        double result = 0.0;
        for (int i = 0; i < n; ++i) {

            // compute lighting ratio considering one occulting body only
            final OccultationEngine oi  = occultingBodies.get(i);
            final double lightingRatioI = maskingRatio(angles[i]);
            if (lightingRatioI == 0.0) {
                // body totally occults Sun, total eclipse is occurring.
                return 0.0;
            }
            result += lightingRatioI;

            // Mutual occulting body eclipse ratio computations between first and secondary bodies
            for (int j = i + 1; j < n; ++j) {

                final OccultationEngine oj = occultingBodies.get(j);
                final double lightingRatioJ = maskingRatio(angles[j]);
                if (lightingRatioJ == 0.0) {
                    // Secondary body totally occults Sun, no more computations are required, total eclipse is occurring.
                    return 0.0;
                } else if (lightingRatioJ != 1) {
                    // Secondary body partially occults Sun

                    final OccultationEngine oij = new OccultationEngine(new FrameAdapter(oi.getOcculting().getBodyFrame()),
                                                                        oi.getOcculting().getEquatorialRadius(),
                                                                        oj.getOcculting());
                    final OccultationEngine.OccultationAngles aij = oij.angles(state);
                    final double maskingRatioIJ = maskingRatio(aij);
                    final double alphaJSq       = aij.getOccultedApparentRadius() * aij.getOccultedApparentRadius();

                    final double mutualEclipseCorrection = (1 - maskingRatioIJ) * alphaJSq / alphaSunSq;
                    result -= mutualEclipseCorrection;

                }

            }
        }

        // Final term
        result -= n - 1;

        return result;

    }

    /** Get the masking ratio ([0-1]) considering one pair of bodies.
     * @param angles occultation angles
     * @return masking ratio: 0.0 body fully masked, 1.0 body fully visible
     * @since 12.0
     */
    private double maskingRatio(final OccultationEngine.OccultationAngles angles) {

        // Sat-Occulted/ Sat-Occulting angle
        final double sunSatCentralBodyAngle = angles.getSeparation();

        // Occulting apparent radius
        final double alphaCentral = angles.getLimbRadius();

        // Occulted apparent radius
        final double alphaSun = angles.getOccultedApparentRadius();

        // Is the satellite in complete umbra ?
        if (sunSatCentralBodyAngle - alphaCentral + alphaSun <= ANGULAR_MARGIN) {
            return 0.0;
        } else if (sunSatCentralBodyAngle - alphaCentral - alphaSun < -ANGULAR_MARGIN) {
            // Compute a masking ratio in penumbra
            final double sEA2    = sunSatCentralBodyAngle * sunSatCentralBodyAngle;
            final double oo2sEA  = 1.0 / (2. * sunSatCentralBodyAngle);
            final double aS2     = alphaSun * alphaSun;
            final double aE2     = alphaCentral * alphaCentral;
            final double aE2maS2 = aE2 - aS2;

            final double alpha1  = (sEA2 - aE2maS2) * oo2sEA;
            final double alpha2  = (sEA2 + aE2maS2) * oo2sEA;

            // Protection against numerical inaccuracy at boundaries
            final double almost0 = Precision.SAFE_MIN;
            final double almost1 = FastMath.nextDown(1.0);
            final double a1oaS   = FastMath.min(almost1, FastMath.max(-almost1, alpha1 / alphaSun));
            final double aS2ma12 = FastMath.max(almost0, aS2 - alpha1 * alpha1);
            final double a2oaE   = FastMath.min(almost1, FastMath.max(-almost1, alpha2 / alphaCentral));
            final double aE2ma22 = FastMath.max(almost0, aE2 - alpha2 * alpha2);

            final double P1 = aS2 * FastMath.acos(a1oaS) - alpha1 * FastMath.sqrt(aS2ma12);
            final double P2 = aE2 * FastMath.acos(a2oaE) - alpha2 * FastMath.sqrt(aE2ma22);

            return 1. - (P1 + P2) / (FastMath.PI * aS2);
        } else {
            return 1.0;
        }

    }

    /** Get the lighting ratio ([0-1]).
     * @param state spacecraft state
     * @param <T> extends CalculusFieldElement
     * @return lighting ratio
     * @since 7.1
     */
    public <T extends CalculusFieldElement<T>> T getLightingRatio(final FieldSpacecraftState<T> state) {

        final T zero = state.getDate().getField().getZero();
        final T one  = state.getDate().getField().getOne();

        final FieldVector3D<T> sunPosition = sun.getPosition(state.getDate(), state.getFrame());
        if (sunPosition.getNorm().getReal() < 2 * Constants.SUN_RADIUS) {
            // we are in fact computing a trajectory around Sun (or solar system barycenter),
            // not around a planet,we consider lighting ratio is always 1
            return one;
        }

        final List<OccultationEngine> occultingBodies = getOccultingBodies();
        final int n = occultingBodies.size();

        @SuppressWarnings("unchecked")
        final OccultationEngine.FieldOccultationAngles<T>[] angles =
                        (OccultationEngine.FieldOccultationAngles<T>[]) Array.newInstance(OccultationEngine.FieldOccultationAngles.class, n);
        for (int i = 0; i < n; ++i) {
            angles[i] = occultingBodies.get(i).angles(state);
        }
        final T alphaSunSq = angles[0].getOccultedApparentRadius().multiply(angles[0].getOccultedApparentRadius());

        T result = state.getDate().getField().getZero();
        for (int i = 0; i < n; ++i) {

            // compute lighting ratio considering one occulting body only
            final OccultationEngine oi  = occultingBodies.get(i);
            final T lightingRatioI = maskingRatio(angles[i]);
            if (lightingRatioI.isZero()) {
                // body totally occults Sun, total eclipse is occurring.
                return zero;
            }
            result = result.add(lightingRatioI);

            // Mutual occulting body eclipse ratio computations between first and secondary bodies
            for (int j = i + 1; j < n; ++j) {

                final OccultationEngine oj = occultingBodies.get(j);
                final T lightingRatioJ = maskingRatio(angles[j]);
                if (lightingRatioJ.isZero()) {
                    // Secondary body totally occults Sun, no more computations are required, total eclipse is occurring.
                    return zero;
                } else if (lightingRatioJ.getReal() != 1) {
                    // Secondary body partially occults Sun

                    final OccultationEngine oij = new OccultationEngine(new FrameAdapter(oi.getOcculting().getBodyFrame()),
                                                                        oi.getOcculting().getEquatorialRadius(),
                                                                        oj.getOcculting());
                    final OccultationEngine.FieldOccultationAngles<T> aij = oij.angles(state);
                    final T maskingRatioIJ = maskingRatio(aij);
                    final T alphaJSq       = aij.getOccultedApparentRadius().multiply(aij.getOccultedApparentRadius());

                    final T mutualEclipseCorrection = one.subtract(maskingRatioIJ).multiply(alphaJSq).divide(alphaSunSq);
                    result = result.subtract(mutualEclipseCorrection);

                }

            }
        }

        // Final term
        result = result.subtract(n - 1);

        return result;

    }


    /** Get the masking ratio ([0-1]) considering one pair of bodies.
     * @param angles occultation angles
     * @param <T> type of the field elements
     * @return masking ratio: 0.0 body fully masked, 1.0 body fully visible
     * @since 12.0
     */
    private <T extends CalculusFieldElement<T>> T maskingRatio(final OccultationEngine.FieldOccultationAngles<T> angles) {


        // Sat-Occulted/ Sat-Occulting angle
        final T occultedSatOcculting = angles.getSeparation();

        // Occulting apparent radius
        final T alphaOcculting = angles.getLimbRadius();

        // Occulted apparent radius
        final T alphaOcculted = angles.getOccultedApparentRadius();

        // Is the satellite in complete umbra ?
        if (occultedSatOcculting.getReal() - alphaOcculting.getReal() + alphaOcculted.getReal() <= ANGULAR_MARGIN) {
            return occultedSatOcculting.getField().getZero();
        } else if (occultedSatOcculting.getReal() - alphaOcculting.getReal() - alphaOcculted.getReal() < -ANGULAR_MARGIN) {
            // Compute a masking ratio in penumbra
            final T sEA2    = occultedSatOcculting.multiply(occultedSatOcculting);
            final T oo2sEA  = occultedSatOcculting.multiply(2).reciprocal();
            final T aS2     = alphaOcculted.multiply(alphaOcculted);
            final T aE2     = alphaOcculting.multiply(alphaOcculting);
            final T aE2maS2 = aE2.subtract(aS2);

            final T alpha1  = sEA2.subtract(aE2maS2).multiply(oo2sEA);
            final T alpha2  = sEA2.add(aE2maS2).multiply(oo2sEA);

            // Protection against numerical inaccuracy at boundaries
            final double almost0 = Precision.SAFE_MIN;
            final double almost1 = FastMath.nextDown(1.0);
            final T a1oaS   = min(almost1, max(-almost1, alpha1.divide(alphaOcculted)));
            final T aS2ma12 = max(almost0, aS2.subtract(alpha1.multiply(alpha1)));
            final T a2oaE   = min(almost1, max(-almost1, alpha2.divide(alphaOcculting)));
            final T aE2ma22 = max(almost0, aE2.subtract(alpha2.multiply(alpha2)));

            final T P1 = aS2.multiply(a1oaS.acos()).subtract(alpha1.multiply(aS2ma12.sqrt()));
            final T P2 = aE2.multiply(a2oaE.acos()).subtract(alpha2.multiply(aE2ma22.sqrt()));

            return occultedSatOcculting.getField().getOne().subtract(P1.add(P2).divide(aS2.multiply(occultedSatOcculting.getPi())));
        } else {
            return occultedSatOcculting.getField().getOne();
        }

    }

    /** {@inheritDoc} */
    @Override
    public List<ParameterDriver> getParametersDrivers() {
        return spacecraft.getRadiationParametersDrivers();
    }

    /** Compute min of two values, one double and one field element.
     * @param d double value
     * @param f field element
     * @param <T> type fo the field elements
     * @return min value
     */
    private <T extends CalculusFieldElement<T>> T min(final double d, final T f) {
        return (f.getReal() > d) ? f.getField().getZero().newInstance(d) : f;
    }

    /** Compute max of two values, one double and one field element.
     * @param d double value
     * @param f field element
     * @param <T> type fo the field elements
     * @return max value
     */
    private <T extends CalculusFieldElement<T>> T max(final double d, final T f) {
        return (f.getReal() <= d) ? f.getField().getZero().newInstance(d) : f;
    }

}
