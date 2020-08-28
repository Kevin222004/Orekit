/* Copyright 2002-2020 CS GROUP
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
package org.orekit.propagation.analytical.tle;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import org.hipparchus.analysis.differentiation.Gradient;
import org.hipparchus.linear.MatrixUtils;
import org.hipparchus.linear.QRDecomposition;
import org.hipparchus.linear.RealMatrix;
import org.hipparchus.linear.RealVector;
import org.hipparchus.util.ArithmeticUtils;
import org.hipparchus.util.FastMath;
import org.hipparchus.util.MathUtils;
import org.orekit.annotation.DefaultDataContext;
import org.orekit.data.DataContext;
import org.orekit.errors.OrekitException;
import org.orekit.errors.OrekitInternalError;
import org.orekit.errors.OrekitMessages;
import org.orekit.orbits.FieldKeplerianOrbit;
import org.orekit.orbits.FieldOrbit;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.OrbitType;
import org.orekit.propagation.FieldSpacecraftState;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.DateComponents;
import org.orekit.time.DateTimeComponents;
import org.orekit.time.FieldAbsoluteDate;
import org.orekit.time.TimeComponents;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeStamped;
import org.orekit.utils.ParameterDriver;

/** This class is a container for a single set of TLE data.
 *
 * <p>TLE sets can be built either by providing directly the two lines, in
 * which case parsing is performed internally or by providing the already
 * parsed elements.</p>
 * <p>TLE are not transparently convertible to {@link org.orekit.orbits.Orbit Orbit}
 * instances. They are significant only with respect to their dedicated {@link
 * TLEPropagator propagator}, which also computes position and velocity coordinates.
 * Any attempt to directly use orbital parameters like {@link #getE() eccentricity},
 * {@link #getI() inclination}, etc. without any reference to the {@link TLEPropagator
 * TLE propagator} is prone to errors.</p>
 * <p>More information on the TLE format can be found on the
 * <a href="https://www.celestrak.com/">CelesTrak website.</a></p>
 * @author Fabien Maussion
 * @author Luc Maisonobe
 */
public class TLE implements TimeStamped, Serializable {

    /** Identifier for SGP type of ephemeris. */
    public static final int SGP = 1;

    /** Identifier for SGP4 type of ephemeris. */
    public static final int SGP4 = 2;

    /** Identifier for SDP4 type of ephemeris. */
    public static final int SDP4 = 3;

    /** Identifier for SGP8 type of ephemeris. */
    public static final int SGP8 = 4;

    /** Identifier for SDP8 type of ephemeris. */
    public static final int SDP8 = 5;

    /** Identifier for default type of ephemeris (SGP4/SDP4). */
    public static final int DEFAULT = 0;

    /** Parameter name for B* coefficient. */
    public static final String B_STAR = "BSTAR";

    /** B* scaling factor.
     * <p>
     * We use a power of 2 to avoid numeric noise introduction
     * in the multiplications/divisions sequences.
     * </p>
     */
    private static final double B_STAR_SCALE = FastMath.scalb(1.0, -20);

    /** Name of the mean motion parameter. */
    private static final String MEAN_MOTION = "meanMotion";

    /** Name of the inclination parameter. */
    private static final String INCLINATION = "inclination";

    /** Name of the eccentricity parameter. */
    private static final String ECCENTRICITY = "eccentricity";

    /** Pattern for line 1. */
    private static final Pattern LINE_1_PATTERN =
        Pattern.compile("1 [ 0-9]{5}[A-Z] [ 0-9]{5}[ A-Z]{3} [ 0-9]{5}[.][ 0-9]{8} (?:(?:[ 0+-][.][ 0-9]{8})|(?: [ +-][.][ 0-9]{7})) " +
                        "[ +-][ 0-9]{5}[+-][ 0-9] [ +-][ 0-9]{5}[+-][ 0-9] [ 0-9] [ 0-9]{4}[ 0-9]");

    /** Pattern for line 2. */
    private static final Pattern LINE_2_PATTERN =
        Pattern.compile("2 [ 0-9]{5} [ 0-9]{3}[.][ 0-9]{4} [ 0-9]{3}[.][ 0-9]{4} [ 0-9]{7} " +
                        "[ 0-9]{3}[.][ 0-9]{4} [ 0-9]{3}[.][ 0-9]{4} [ 0-9]{2}[.][ 0-9]{13}[ 0-9]");

    /** International symbols for parsing. */
    private static final DecimalFormatSymbols SYMBOLS =
        new DecimalFormatSymbols(Locale.US);

    /** Serializable UID. */
    private static final long serialVersionUID = -1596648022319057689L;

    /** The satellite number. */
    private final int satelliteNumber;

    /** Classification (U for unclassified). */
    private final char classification;

    /** Launch year. */
    private final int launchYear;

    /** Launch number. */
    private final int launchNumber;

    /** Piece of launch (from "A" to "ZZZ"). */
    private final String launchPiece;

    /** Type of ephemeris. */
    private final int ephemerisType;

    /** Element number. */
    private final int elementNumber;

    /** the TLE current date. */
    private final AbsoluteDate epoch;

    /** Mean motion (rad/s). */
    private final double meanMotion;

    /** Mean motion first derivative (rad/s²). */
    private final double meanMotionFirstDerivative;

    /** Mean motion second derivative (rad/s³). */
    private final double meanMotionSecondDerivative;

    /** Eccentricity. */
    private final double eccentricity;

    /** Inclination (rad). */
    private final double inclination;

    /** Argument of perigee (rad). */
    private final double pa;

    /** Right Ascension of the Ascending node (rad). */
    private final double raan;

    /** Mean anomaly (rad). */
    private final double meanAnomaly;

    /** Revolution number at epoch. */
    private final int revolutionNumberAtEpoch;

    /** First line. */
    private String line1;

    /** Second line. */
    private String line2;

    /** The UTC scale. */
    private final TimeScale utc;

    /** Driver for ballistic coefficient parameter. */
    private final ParameterDriver bStarParameterDriver;

    /** Simple constructor from unparsed two lines. This constructor uses the {@link
     * DataContext#getDefault() default data context}.
     *
     * <p>The static method {@link #isFormatOK(String, String)} should be called
     * before trying to build this object.<p>
     * @param line1 the first element (69 char String)
     * @param line2 the second element (69 char String)
     * @see #TLE(String, String, TimeScale)
     */
    @DefaultDataContext
    public TLE(final String line1, final String line2) {
        this(line1, line2, DataContext.getDefault().getTimeScales().getUTC());
    }

    /** Simple constructor from unparsed two lines using the given time scale as UTC.
     *
     * <p>The static method {@link #isFormatOK(String, String)} should be called
     * before trying to build this object.<p>
     * @param line1 the first element (69 char String)
     * @param line2 the second element (69 char String)
     * @param utc the UTC time scale.
     * @since 10.1
     */
    public TLE(final String line1, final String line2, final TimeScale utc) {

        // identification
        satelliteNumber = parseInteger(line1, 2, 5);
        final int satNum2 = parseInteger(line2, 2, 5);
        if (satelliteNumber != satNum2) {
            throw new OrekitException(OrekitMessages.TLE_LINES_DO_NOT_REFER_TO_SAME_OBJECT,
                                      line1, line2);
        }
        classification  = line1.charAt(7);
        launchYear      = parseYear(line1, 9);
        launchNumber    = parseInteger(line1, 11, 3);
        launchPiece     = line1.substring(14, 17).trim();
        ephemerisType   = parseInteger(line1, 62, 1);
        elementNumber   = parseInteger(line1, 64, 4);

        // Date format transform (nota: 27/31250 == 86400/100000000)
        final int    year      = parseYear(line1, 18);
        final int    dayInYear = parseInteger(line1, 20, 3);
        final long   df        = 27l * parseInteger(line1, 24, 8);
        final int    secondsA  = (int) (df / 31250l);
        final double secondsB  = (df % 31250l) / 31250.0;
        epoch = new AbsoluteDate(new DateComponents(year, dayInYear),
                                 new TimeComponents(secondsA, secondsB),
                                 utc);

        // mean motion development
        // converted from rev/day, 2 * rev/day^2 and 6 * rev/day^3 to rad/s, rad/s^2 and rad/s^3
        meanMotion                 = parseDouble(line2, 52, 11) * FastMath.PI / 43200.0;
        meanMotionFirstDerivative  = parseDouble(line1, 33, 10) * FastMath.PI / 1.86624e9;
        meanMotionSecondDerivative = Double.parseDouble((line1.substring(44, 45) + '.' +
                                                         line1.substring(45, 50) + 'e' +
                                                         line1.substring(50, 52)).replace(' ', '0')) *
                                     FastMath.PI / 5.3747712e13;

        eccentricity = Double.parseDouble("." + line2.substring(26, 33).replace(' ', '0'));
        inclination  = FastMath.toRadians(parseDouble(line2, 8, 8));
        pa           = FastMath.toRadians(parseDouble(line2, 34, 8));
        raan         = FastMath.toRadians(Double.parseDouble(line2.substring(17, 25).replace(' ', '0')));
        meanAnomaly  = FastMath.toRadians(parseDouble(line2, 43, 8));

        revolutionNumberAtEpoch = parseInteger(line2, 63, 5);
        final double bStarValue = Double.parseDouble((line1.substring(53, 54) + '.' +
                                    line1.substring(54, 59) + 'e' +
                                    line1.substring(59, 61)).replace(' ', '0'));

        // save the lines
        this.line1 = line1;
        this.line2 = line2;
        this.utc = utc;

        // create model parameter drivers
        this.bStarParameterDriver = new ParameterDriver(B_STAR, bStarValue, B_STAR_SCALE,
                                                        Double.NEGATIVE_INFINITY,
                                                        Double.POSITIVE_INFINITY);

    }

    /**
     * <p>
     * Simple constructor from already parsed elements. This constructor uses the
     * {@link DataContext#getDefault() default data context}.
     * </p>
     *
     * <p>
     * The mean anomaly, the right ascension of ascending node Ω and the argument of
     * perigee ω are normalized into the [0, 2π] interval as they can be negative.
     * After that, a range check is performed on some of the orbital elements:
     *
     * <pre>
     *     meanMotion &gt;= 0
     *     0 &lt;= i &lt;= π
     *     0 &lt;= Ω &lt;= 2π
     *     0 &lt;= e &lt;= 1
     *     0 &lt;= ω &lt;= 2π
     *     0 &lt;= meanAnomaly &lt;= 2π
     * </pre>
     *
     * @param satelliteNumber satellite number
     * @param classification classification (U for unclassified)
     * @param launchYear launch year (all digits)
     * @param launchNumber launch number
     * @param launchPiece launch piece (3 char String)
     * @param ephemerisType type of ephemeris
     * @param elementNumber element number
     * @param epoch elements epoch
     * @param meanMotion mean motion (rad/s)
     * @param meanMotionFirstDerivative mean motion first derivative (rad/s²)
     * @param meanMotionSecondDerivative mean motion second derivative (rad/s³)
     * @param e eccentricity
     * @param i inclination (rad)
     * @param pa argument of perigee (rad)
     * @param raan right ascension of ascending node (rad)
     * @param meanAnomaly mean anomaly (rad)
     * @param revolutionNumberAtEpoch revolution number at epoch
     * @param bStar ballistic coefficient
     * @see #TLE(int, char, int, int, String, int, int, AbsoluteDate, double, double,
     * double, double, double, double, double, double, int, double, TimeScale)
     */
    @DefaultDataContext
    public TLE(final int satelliteNumber, final char classification,
               final int launchYear, final int launchNumber, final String launchPiece,
               final int ephemerisType, final int elementNumber, final AbsoluteDate epoch,
               final double meanMotion, final double meanMotionFirstDerivative,
               final double meanMotionSecondDerivative, final double e, final double i,
               final double pa, final double raan, final double meanAnomaly,
               final int revolutionNumberAtEpoch, final double bStar) {
        this(satelliteNumber, classification, launchYear, launchNumber, launchPiece,
                ephemerisType, elementNumber, epoch, meanMotion,
                meanMotionFirstDerivative, meanMotionSecondDerivative, e, i, pa, raan,
                meanAnomaly, revolutionNumberAtEpoch, bStar,
                DataContext.getDefault().getTimeScales().getUTC());
    }

    /**
     * <p>
     * Simple constructor from already parsed elements using the given time scale as
     * UTC.
     * </p>
     *
     * <p>
     * The mean anomaly, the right ascension of ascending node Ω and the argument of
     * perigee ω are normalized into the [0, 2π] interval as they can be negative.
     * After that, a range check is performed on some of the orbital elements:
     *
     * <pre>
     *     meanMotion &gt;= 0
     *     0 &lt;= i &lt;= π
     *     0 &lt;= Ω &lt;= 2π
     *     0 &lt;= e &lt;= 1
     *     0 &lt;= ω &lt;= 2π
     *     0 &lt;= meanAnomaly &lt;= 2π
     * </pre>
     *
     * @param satelliteNumber satellite number
     * @param classification classification (U for unclassified)
     * @param launchYear launch year (all digits)
     * @param launchNumber launch number
     * @param launchPiece launch piece (3 char String)
     * @param ephemerisType type of ephemeris
     * @param elementNumber element number
     * @param epoch elements epoch
     * @param meanMotion mean motion (rad/s)
     * @param meanMotionFirstDerivative mean motion first derivative (rad/s²)
     * @param meanMotionSecondDerivative mean motion second derivative (rad/s³)
     * @param e eccentricity
     * @param i inclination (rad)
     * @param pa argument of perigee (rad)
     * @param raan right ascension of ascending node (rad)
     * @param meanAnomaly mean anomaly (rad)
     * @param revolutionNumberAtEpoch revolution number at epoch
     * @param bStar ballistic coefficient
     * @param utc the UTC time scale.
     * @since 10.1
     */
    public TLE(final int satelliteNumber, final char classification,
               final int launchYear, final int launchNumber, final String launchPiece,
               final int ephemerisType, final int elementNumber, final AbsoluteDate epoch,
               final double meanMotion, final double meanMotionFirstDerivative,
               final double meanMotionSecondDerivative, final double e, final double i,
               final double pa, final double raan, final double meanAnomaly,
               final int revolutionNumberAtEpoch, final double bStar,
               final TimeScale utc) {

        // identification
        this.satelliteNumber = satelliteNumber;
        this.classification  = classification;
        this.launchYear      = launchYear;
        this.launchNumber    = launchNumber;
        this.launchPiece     = launchPiece;
        this.ephemerisType   = ephemerisType;
        this.elementNumber   = elementNumber;

        // orbital parameters
        this.epoch = epoch;
        // Checking mean motion range
        checkParameterRangeInclusive(MEAN_MOTION, meanMotion, 0.0, Double.POSITIVE_INFINITY);
        this.meanMotion = meanMotion;
        this.meanMotionFirstDerivative = meanMotionFirstDerivative;
        this.meanMotionSecondDerivative = meanMotionSecondDerivative;

        // Checking inclination range
        checkParameterRangeInclusive(INCLINATION, i, 0, FastMath.PI);
        this.inclination = i;

        // Normalizing RAAN in [0,2pi] interval
        this.raan = MathUtils.normalizeAngle(raan, FastMath.PI);

        // Checking eccentricity range
        checkParameterRangeInclusive(ECCENTRICITY, e, 0.0, 1.0);
        this.eccentricity = e;

        // Normalizing PA in [0,2pi] interval
        this.pa = MathUtils.normalizeAngle(pa, FastMath.PI);

        // Normalizing mean anomaly in [0,2pi] interval
        this.meanAnomaly = MathUtils.normalizeAngle(meanAnomaly, FastMath.PI);

        this.revolutionNumberAtEpoch = revolutionNumberAtEpoch;

        // don't build the line until really needed
        this.line1 = null;
        this.line2 = null;
        this.utc = utc;

        // create model parameter drivers
        this.bStarParameterDriver = new ParameterDriver(B_STAR, bStar, B_STAR_SCALE,
                                                        Double.NEGATIVE_INFINITY,
                                                        Double.POSITIVE_INFINITY);

    }

    /**
     * Get the UTC time scale used to create this TLE.
     *
     * @return UTC time scale.
     */
    TimeScale getUtc() {
        return utc;
    }

    /** Get the first line.
     * @return first line
     */
    public String getLine1() {
        if (line1 == null) {
            buildLine1();
        }
        return line1;
    }

    /** Get the second line.
     * @return second line
     */
    public String getLine2() {
        if (line2 == null) {
            buildLine2();
        }
        return line2;
    }

    /** Build the line 1 from the parsed elements.
     */
    private void buildLine1() {

        final StringBuffer buffer = new StringBuffer();

        buffer.append('1');

        buffer.append(' ');
        buffer.append(addPadding("satelliteNumber-1", satelliteNumber, '0', 5, true));
        buffer.append(classification);

        buffer.append(' ');
        buffer.append(addPadding("launchYear",   launchYear % 100, '0', 2, true));
        buffer.append(addPadding("launchNumber", launchNumber, '0', 3, true));
        buffer.append(addPadding("launchPiece",  launchPiece, ' ', 3, false));

        buffer.append(' ');
        final DateTimeComponents dtc = epoch.getComponents(utc);
        buffer.append(addPadding("year", dtc.getDate().getYear() % 100, '0', 2, true));
        buffer.append(addPadding("day",  dtc.getDate().getDayOfYear(),  '0', 3, true));
        buffer.append('.');
        // nota: 31250/27 == 100000000/86400
        final int fraction = (int) FastMath.rint(31250 * dtc.getTime().getSecondsInUTCDay() / 27.0);
        buffer.append(addPadding("fraction", fraction,  '0', 8, true));

        buffer.append(' ');
        final double n1 = meanMotionFirstDerivative * 1.86624e9 / FastMath.PI;
        final String sn1 = addPadding("meanMotionFirstDerivative",
                                      new DecimalFormat(".00000000", SYMBOLS).format(n1), ' ', 10, true);
        buffer.append(sn1);

        buffer.append(' ');
        final double n2 = meanMotionSecondDerivative * 5.3747712e13 / FastMath.PI;
        buffer.append(formatExponentMarkerFree("meanMotionSecondDerivative", n2, 5, ' ', 8, true));

        buffer.append(' ');
        buffer.append(formatExponentMarkerFree("B*", getBStar(), 5, ' ', 8, true));

        buffer.append(' ');
        buffer.append(ephemerisType);

        buffer.append(' ');
        buffer.append(addPadding("elementNumber", elementNumber, ' ', 4, true));

        buffer.append(Integer.toString(checksum(buffer)));

        line1 = buffer.toString();

    }

    /** Format a real number without 'e' exponent marker.
     * @param name parameter name
     * @param d number to format
     * @param mantissaSize size of the mantissa (not counting initial '-' or ' ' for sign)
     * @param c padding character
     * @param size desired size
     * @param rightJustified if true, the resulting string is
     * right justified (i.e. space are added to the left)
     * @return formatted and padded number
     */
    private String formatExponentMarkerFree(final String name, final double d, final int mantissaSize,
                                            final char c, final int size, final boolean rightJustified) {
        final double dAbs = FastMath.abs(d);
        int exponent = (dAbs < 1.0e-9) ? -9 : (int) FastMath.ceil(FastMath.log10(dAbs));
        long mantissa = FastMath.round(dAbs * FastMath.pow(10.0, mantissaSize - exponent));
        if (mantissa == 0) {
            exponent = 0;
        } else if (mantissa > (ArithmeticUtils.pow(10, mantissaSize) - 1)) {
            // rare case: if d has a single digit like d = 1.0e-4 with mantissaSize = 5
            // the above computation finds exponent = -4 and mantissa = 100000 which
            // doesn't fit in a 5 digits string
            exponent++;
            mantissa = FastMath.round(dAbs * FastMath.pow(10.0, mantissaSize - exponent));
        }
        final String sMantissa = addPadding(name, (int) mantissa, '0', mantissaSize, true);
        final String sExponent = Integer.toString(FastMath.abs(exponent));
        final String formatted = (d <  0 ? '-' : ' ') + sMantissa + (exponent <= 0 ? '-' : '+') + sExponent;

        return addPadding(name, formatted, c, size, rightJustified);

    }

    /** Build the line 2 from the parsed elements.
     */
    private void buildLine2() {

        final StringBuffer buffer = new StringBuffer();
        final DecimalFormat f34   = new DecimalFormat("##0.0000", SYMBOLS);
        final DecimalFormat f211  = new DecimalFormat("#0.00000000", SYMBOLS);

        buffer.append('2');

        buffer.append(' ');
        buffer.append(addPadding("satelliteNumber-2", satelliteNumber, '0', 5, true));

        buffer.append(' ');
        buffer.append(addPadding(INCLINATION, f34.format(FastMath.toDegrees(inclination)), ' ', 8, true));
        buffer.append(' ');
        buffer.append(addPadding("raan", f34.format(FastMath.toDegrees(raan)), ' ', 8, true));
        buffer.append(' ');
        buffer.append(addPadding(ECCENTRICITY, (int) FastMath.rint(eccentricity * 1.0e7), '0', 7, true));
        buffer.append(' ');
        buffer.append(addPadding("pa", f34.format(FastMath.toDegrees(pa)), ' ', 8, true));
        buffer.append(' ');
        buffer.append(addPadding("meanAnomaly", f34.format(FastMath.toDegrees(meanAnomaly)), ' ', 8, true));

        buffer.append(' ');
        buffer.append(addPadding(MEAN_MOTION, f211.format(meanMotion * 43200.0 / FastMath.PI), ' ', 11, true));
        buffer.append(addPadding("revolutionNumberAtEpoch", revolutionNumberAtEpoch, ' ', 5, true));

        buffer.append(Integer.toString(checksum(buffer)));

        line2 = buffer.toString();

    }

    /** Add padding characters before an integer.
     * @param name parameter name
     * @param k integer to pad
     * @param c padding character
     * @param size desired size
     * @param rightJustified if true, the resulting string is
     * right justified (i.e. space are added to the left)
     * @return padded string
     */
    private String addPadding(final String name, final int k, final char c,
                              final int size, final boolean rightJustified) {
        return addPadding(name, Integer.toString(k), c, size, rightJustified);
    }

    /** Add padding characters to a string.
     * @param name parameter name
     * @param string string to pad
     * @param c padding character
     * @param size desired size
     * @param rightJustified if true, the resulting string is
     * right justified (i.e. space are added to the left)
     * @return padded string
     */
    private String addPadding(final String name, final String string, final char c,
                              final int size, final boolean rightJustified) {

        if (string.length() > size) {
            throw new OrekitException(OrekitMessages.TLE_INVALID_PARAMETER,
                                      satelliteNumber, name, string);
        }

        final StringBuffer padding = new StringBuffer();
        for (int i = 0; i < size; ++i) {
            padding.append(c);
        }

        if (rightJustified) {
            final String concatenated = padding + string;
            final int l = concatenated.length();
            return concatenated.substring(l - size, l);
        }

        return (string + padding).substring(0, size);

    }

    /** Parse a double.
     * @param line line to parse
     * @param start start index of the first character
     * @param length length of the string
     * @return value of the double
     */
    private double parseDouble(final String line, final int start, final int length) {
        final String field = line.substring(start, start + length).trim();
        return field.length() > 0 ? Double.parseDouble(field.replace(' ', '0')) : 0;
    }

    /** Parse an integer.
     * @param line line to parse
     * @param start start index of the first character
     * @param length length of the string
     * @return value of the integer
     */
    private int parseInteger(final String line, final int start, final int length) {
        final String field = line.substring(start, start + length).trim();
        return field.length() > 0 ? Integer.parseInt(field.replace(' ', '0')) : 0;
    }

    /** Parse a year written on 2 digits.
     * @param line line to parse
     * @param start start index of the first character
     * @return value of the year
     */
    private int parseYear(final String line, final int start) {
        final int year = 2000 + parseInteger(line, start, 2);
        return (year > 2056) ? (year - 100) : year;
    }

    /** Get the satellite id.
     * @return the satellite number
     */
    public int getSatelliteNumber() {
        return satelliteNumber;
    }

    /** Get the classification.
     * @return classification
     */
    public char getClassification() {
        return classification;
    }

    /** Get the launch year.
     * @return the launch year
     */
    public int getLaunchYear() {
        return launchYear;
    }

    /** Get the launch number.
     * @return the launch number
     */
    public int getLaunchNumber() {
        return launchNumber;
    }

    /** Get the launch piece.
     * @return the launch piece
     */
    public String getLaunchPiece() {
        return launchPiece;
    }

    /** Get the type of ephemeris.
     * @return the ephemeris type (one of {@link #DEFAULT}, {@link #SGP},
     * {@link #SGP4}, {@link #SGP8}, {@link #SDP4}, {@link #SDP8})
     */
    public int getEphemerisType() {
        return ephemerisType;
    }

    /** Get the element number.
     * @return the element number
     */
    public int getElementNumber() {
        return elementNumber;
    }

    /** Get the TLE current date.
     * @return the epoch
     */
    public AbsoluteDate getDate() {
        return epoch;
    }

    /** Get the mean motion.
     * @return the mean motion (rad/s)
     */
    public double getMeanMotion() {
        return meanMotion;
    }

    /** Get the mean motion first derivative.
     * @return the mean motion first derivative (rad/s²)
     */
    public double getMeanMotionFirstDerivative() {
        return meanMotionFirstDerivative;
    }

    /** Get the mean motion second derivative.
     * @return the mean motion second derivative (rad/s³)
     */
    public double getMeanMotionSecondDerivative() {
        return meanMotionSecondDerivative;
    }

    /** Get the eccentricity.
     * @return the eccentricity
     */
    public double getE() {
        return eccentricity;
    }

    /** Get the inclination.
     * @return the inclination (rad)
     */
    public double getI() {
        return inclination;
    }

    /** Get the argument of perigee.
     * @return omega (rad)
     */
    public double getPerigeeArgument() {
        return pa;
    }

    /** Get Right Ascension of the Ascending node.
     * @return the raan (rad)
     */
    public double getRaan() {
        return raan;
    }

    /** Get the mean anomaly.
     * @return the mean anomaly (rad)
     */
    public double getMeanAnomaly() {
        return meanAnomaly;
    }

    /** Get the revolution number.
     * @return the revolutionNumberAtEpoch
     */
    public int getRevolutionNumberAtEpoch() {
        return revolutionNumberAtEpoch;
    }

    /** Get the ballistic coefficient.
     * @return bStar
     */
    public double getBStar() {
        return bStarParameterDriver.getValue();
    }

    /** Get a string representation of this TLE set.
     * <p>The representation is simply the two lines separated by the
     * platform line separator.</p>
     * @return string representation of this TLE set
     */
    public String toString() {
        try {
            return getLine1() + System.getProperty("line.separator") + getLine2();
        } catch (OrekitException oe) {
            throw new OrekitInternalError(oe);
        }
    }

    /**
     * Convert Spacecraft State into TLE.
     * This converter uses Newton method to reverse SGP4 and SDP4 propagation algorithm
     * and generates a usable TLE version of a state.
     * New TLE epoch is state epoch.
     *
     *<p>This method uses the {@link DataContext#getDefault() default data context}.
     *
     * @param state Spacecraft State to convert into TLE
     * @param templateTLE first guess used to get identification and estimate new TLE
     * @return TLE matching with Spacecraft State and template identification
     */
    @DefaultDataContext
    public static TLE stateToTLE(final SpacecraftState state, final TLE templateTLE) {

        // get keplerian parameters from state
        final Orbit orbit = state.getOrbit();
        final KeplerianOrbit keplerianOrbit = (KeplerianOrbit) OrbitType.KEPLERIAN.convertType(orbit);

        double meanMotion  = keplerianOrbit.getKeplerianMeanMotion();
        double e           = keplerianOrbit.getE();
        double i           = keplerianOrbit.getI();
        double raan        = keplerianOrbit.getRightAscensionOfAscendingNode();
        double pa          = keplerianOrbit.getPerigeeArgument();
        double meanAnomaly = keplerianOrbit.getMeanAnomaly();

        // rough initialization of the TLE
        FieldTLE<Gradient> current = gradTLE(meanMotion, e, i, raan, pa, meanAnomaly, state.getDate(), templateTLE);

        final Gradient zero = current.getE().getField().getZero();

        // threshold for each parameter
        final double epsilon             = 1.0e-10;
        final double thresholdMeanMotion = epsilon * (1 + keplerianOrbit.getKeplerianMeanMotion());
        final double thresholdE          = epsilon * (1 + state.getE());
        final double thresholdI          = epsilon * (1 + state.getI());
        final double thresholdAngles     = epsilon * FastMath.PI;
        int k = 0;
        while (k++ < 100) {

            // recompute the state from the current TLE
            final Gradient[] parameters = new Gradient[1];
            //parameters[0] = Gradient.constant(FREE_STATE_PARAMETERS, current.getBStar());
            parameters[0] = zero.add(current.getBStar());
            final FieldTLEPropagator<Gradient> propagator = FieldTLEPropagator.selectExtrapolator(current, parameters);
            final FieldSpacecraftState<Gradient> recoveredState = propagator.getInitialState();
            final FieldOrbit<Gradient> recoveredOrbit = recoveredState.getOrbit();
            final FieldKeplerianOrbit<Gradient> recoveredKeplerianOrbit = (FieldKeplerianOrbit<Gradient>) OrbitType.KEPLERIAN.convertType(recoveredOrbit);

            // adapted parameters residuals
            final Gradient deltaMeanMotion  = recoveredKeplerianOrbit.getKeplerianMeanMotion().negate().add(keplerianOrbit.getKeplerianMeanMotion());
            final Gradient deltaE           = recoveredKeplerianOrbit.getE().negate().add(keplerianOrbit.getE());
            final Gradient deltaI           = recoveredKeplerianOrbit.getI().negate().add(keplerianOrbit.getI());
            final Gradient deltaRAAN        = MathUtils.normalizeAngle(recoveredKeplerianOrbit.getRightAscensionOfAscendingNode().negate()
                                                                                     .add(keplerianOrbit.getRightAscensionOfAscendingNode()), zero);
            final Gradient deltaPA          = MathUtils.normalizeAngle(recoveredKeplerianOrbit.getPerigeeArgument().negate()
                                                                                     .add(keplerianOrbit.getPerigeeArgument()), zero);
            final Gradient deltaMeanAnomaly = MathUtils.normalizeAngle(recoveredKeplerianOrbit.getMeanAnomaly().negate()
                                                                                     .add(keplerianOrbit.getMeanAnomaly()), zero);

            // check convergence
            if ((FastMath.abs(deltaMeanMotion.getValue()) < thresholdMeanMotion) &&
                (FastMath.abs(deltaE.getValue())          < thresholdE) &&
                (FastMath.abs(deltaI.getValue())          < thresholdI) &&
                (FastMath.abs(deltaPA.getValue())         < thresholdAngles) &&
                (FastMath.abs(deltaRAAN.getValue())       < thresholdAngles) &&
                (FastMath.abs(deltaMeanMotion.getValue()) < thresholdAngles)) {

                return current.toTLE();
            }

            // compute differencial correction according to Newton method
            final double[] vector = new double[6];
            vector[0] = -deltaMeanMotion.getReal();
            vector[1] = -deltaE.getReal();
            vector[2] = -deltaI.getReal();
            vector[3] = -deltaRAAN.getReal();
            vector[4] = -deltaPA.getReal();
            vector[5] = -deltaMeanAnomaly.getReal();
            final RealVector F = MatrixUtils.createRealVector(vector);
            final RealMatrix J = MatrixUtils.createRealMatrix(6, 6);
            J.setRow(0, deltaMeanMotion.getGradient());
            J.setRow(1, deltaE.getGradient());
            J.setRow(2, deltaI.getGradient());
            J.setRow(3, deltaRAAN.getGradient());
            J.setRow(4, deltaPA.getGradient());
            J.setRow(5, deltaMeanAnomaly.getGradient());
            final QRDecomposition decomp = new QRDecomposition(J);

            final RealVector deltaTLE = decomp.getSolver().solve(F);

            // update TLE
            meanMotion  += deltaTLE.getEntry(0);
            e           += deltaTLE.getEntry(1);
            i           += deltaTLE.getEntry(2);
            raan        += deltaTLE.getEntry(3);
            pa          += deltaTLE.getEntry(4);
            meanAnomaly += deltaTLE.getEntry(5);

            current = gradTLE(meanMotion, e, i, raan, pa, meanAnomaly, state.getDate(), templateTLE);

        }

        throw new OrekitException(OrekitMessages.UNABLE_TO_COMPUTE_TLE, k);
    }

    /**
     * Builds a Gradient TLE from a TLE and specified parameters.
     * Warning! This Gradient TLE should not be used for any propagation!
     * @param meanMotion Mean Motion (rad/s)
     * @param e excentricity
     * @param i inclination (rad)
     * @param raan right ascension of ascending node (rad)
     * @param pa perigee argument (rad)
     * @param meanAnomaly mean anomaly (rad)
     * @param templateTLE TLE used to get object identification
     * @param epoch epoch of the new TLE
     * @return TLE with template identification and new orbital parameters
     */
    private static FieldTLE<Gradient> gradTLE(final double meanMotion,
                                             final double e,
                                             final double i,
                                             final double raan,
                                             final double pa,
                                             final double meanAnomaly,
                                             final AbsoluteDate epoch,
                                             final TLE templateTLE) {

        // Identification
        final int satelliteNumber = templateTLE.getSatelliteNumber();
        final char classification = templateTLE.getClassification();
        final int launchYear = templateTLE.getLaunchYear();
        final int launchNumber = templateTLE.getLaunchNumber();
        final String launchPiece = templateTLE.getLaunchPiece();
        final int ephemerisType = templateTLE.getEphemerisType();
        final int elementNumber = templateTLE.getElementNumber();
        final int revolutionNumberAtEpoch = templateTLE.getRevolutionNumberAtEpoch();
        final double dt = epoch.durationFrom(templateTLE.getDate());
        final int newRevolutionNumberAtEpoch = (int) ((int) revolutionNumberAtEpoch + FastMath.floor(dt * meanMotion / (2 * FastMath.PI)));

        final Gradient gMeanMotion  = Gradient.variable(6, 0, meanMotion);
        final Gradient ge           = Gradient.variable(6, 1, e);
        final Gradient gi           = Gradient.variable(6, 2, i);
        final Gradient graan        = Gradient.variable(6, 3, raan);
        final Gradient gpa          = Gradient.variable(6, 4, pa);
        final Gradient gMeanAnomaly = Gradient.variable(6, 5, meanAnomaly);
        // Epoch
        final FieldAbsoluteDate<Gradient> gEpoch = new FieldAbsoluteDate<>(gMeanMotion.getField(), epoch);

        //B*
        final double bStar = templateTLE.getBStar();

        // Mean Motion derivatives
        final Gradient gMeanMotionFirstDerivative = Gradient.constant(6, templateTLE.getMeanMotionFirstDerivative());
        final Gradient gMeanMotionSecondDerivative = Gradient.constant(6, templateTLE.getMeanMotionSecondDerivative());

        final FieldTLE<Gradient> newTLE = new FieldTLE<Gradient>(satelliteNumber, classification, launchYear, launchNumber, launchPiece, ephemerisType,
                       elementNumber, gEpoch, gMeanMotion, gMeanMotionFirstDerivative, gMeanMotionSecondDerivative,
                       ge, gi, gpa, graan, gMeanAnomaly, newRevolutionNumberAtEpoch, bStar, templateTLE.getUtc());

        for (int k = 0; k < newTLE.getParametersDrivers().length; ++k) {
            newTLE.getParametersDrivers()[k].setSelected(templateTLE.getParametersDrivers()[k].isSelected());
        }

        return newTLE;
    }

    /** Check the lines format validity.
     * @param line1 the first element
     * @param line2 the second element
     * @return true if format is recognized (non null lines, 69 characters length,
     * line content), false if not
     */
    public static boolean isFormatOK(final String line1, final String line2) {

        if (line1 == null || line1.length() != 69 ||
            line2 == null || line2.length() != 69) {
            return false;
        }

        if (!(LINE_1_PATTERN.matcher(line1).matches() &&
              LINE_2_PATTERN.matcher(line2).matches())) {
            return false;
        }

        // check sums
        final int checksum1 = checksum(line1);
        if (Integer.parseInt(line1.substring(68)) != (checksum1 % 10)) {
            throw new OrekitException(OrekitMessages.TLE_CHECKSUM_ERROR,
                                      1, Integer.toString(checksum1 % 10), line1.substring(68), line1);
        }

        final int checksum2 = checksum(line2);
        if (Integer.parseInt(line2.substring(68)) != (checksum2 % 10)) {
            throw new OrekitException(OrekitMessages.TLE_CHECKSUM_ERROR,
                                      2, Integer.toString(checksum2 % 10), line2.substring(68), line2);
        }

        return true;

    }

    /** Compute the checksum of the first 68 characters of a line.
     * @param line line to check
     * @return checksum
     */
    private static int checksum(final CharSequence line) {
        int sum = 0;
        for (int j = 0; j < 68; j++) {
            final char c = line.charAt(j);
            if (Character.isDigit(c)) {
                sum += Character.digit(c, 10);
            } else if (c == '-') {
                ++sum;
            }
        }
        return sum % 10;
    }

    /**
     * <p>
     * Check if the given parameter is within an acceptable range.
     * The bounds are inclusive: an exception is raised when either of those conditions are met:
     * <ul>
     *     <li>The parameter is strictly greater than upperBound</li>
     *     <li>The parameter is strictly lower than lowerBound</li>
     * </ul>
     * </p>
     * <p>
     * In either of these cases, an OrekitException is raised with a TLE_INVALID_PARAMETER_RANGE
     * message, for instance:
     * <pre>
     *   "invalid TLE parameter eccentricity: 42.0 not in range [0.0, 1.0]"
     * </pre>
     * </p>
     * @param parameterName name of the parameter
     * @param parameter value of the parameter
     * @param lowerBound lower bound of the acceptable range (inclusive)
     * @param upperBound upper bound of the acceptable range (inclusive)
     */
    private void checkParameterRangeInclusive(final String parameterName, final double parameter,
            final double lowerBound,
            final double upperBound) {
        if ((parameter < lowerBound) || (parameter > upperBound)) {
            throw new OrekitException(OrekitMessages.TLE_INVALID_PARAMETER_RANGE, parameterName,
                    parameter,
                    lowerBound, upperBound);
        }
    }

    /** Check if this tle equals the provided tle.
     * <p>Due to the difference in precision between object and string
     * representations of TLE, it is possible for this method to return false
     * even if string representations returned by {@link #toString()}
     * are equal.</p>
     * @param o other tle
     * @return true if this tle equals the provided tle
     */
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof TLE)) {
            return false;
        }
        final TLE tle = (TLE) o;
        return satelliteNumber == tle.satelliteNumber &&
                classification == tle.classification &&
                launchYear == tle.launchYear &&
                launchNumber == tle.launchNumber &&
                Objects.equals(launchPiece, tle.launchPiece) &&
                ephemerisType == tle.ephemerisType &&
                elementNumber == tle.elementNumber &&
                Objects.equals(epoch, tle.epoch) &&
                meanMotion == tle.meanMotion &&
                meanMotionFirstDerivative == tle.meanMotionFirstDerivative &&
                meanMotionSecondDerivative == tle.meanMotionSecondDerivative &&
                eccentricity == tle.eccentricity &&
                inclination == tle.inclination &&
                pa == tle.pa &&
                raan == tle.raan &&
                meanAnomaly == tle.meanAnomaly &&
                revolutionNumberAtEpoch == tle.revolutionNumberAtEpoch &&
                getBStar() == tle.getBStar();
    }

    /** Get a hashcode for this tle.
     * @return hashcode
     */
    @Override
    public int hashCode() {
        return Objects.hash(satelliteNumber,
                classification,
                launchYear,
                launchNumber,
                launchPiece,
                ephemerisType,
                elementNumber,
                epoch,
                meanMotion,
                meanMotionFirstDerivative,
                meanMotionSecondDerivative,
                eccentricity,
                inclination,
                pa,
                raan,
                meanAnomaly,
                revolutionNumberAtEpoch,
                getBStar());
    }

    /** Get the drivers for TLE propagation SGP4 and SDP4.
     * @return drivers for SGP4 and SDP4 model parameters
     */
    public ParameterDriver[] getParametersDrivers() {
        return new ParameterDriver[] {
            bStarParameterDriver
        };
    }

}
