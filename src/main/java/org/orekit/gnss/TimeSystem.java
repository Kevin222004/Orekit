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
package org.orekit.gnss;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.orekit.errors.OrekitIllegalArgumentException;
import org.orekit.errors.OrekitMessages;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScales;

/** Enumerate for the time systems used in navigation files.
 *
 * @author Thomas Neidhart
 * @author Evan Ward
 * @author Thomas Paulet
 * @since 11.0
 */
public enum TimeSystem {

    /** Global Positioning System. */
    GPS("GPS", "GP", ts -> ts.getGPS()),

    /** GLONASS. */
    GLONASS("GLO", "GL", ts -> ts.getGLONASS()),

    /** GALILEO. */
    GALILEO("GAL", "GA", ts -> ts.getGST()),

    /** International Atomic Time. */
    TAI("TAI", null, ts -> ts.getTAI()),

    /** Coordinated Universal Time. */
    UTC("UTC", "UT", ts -> ts.getUTC()),

    /** Quasi-Zenith System. */
    QZSS("QZS", "QZ", ts -> ts.getQZSS()),

    /** Beidou. */
    BEIDOU("BDS", "BD", ts -> ts.getBDT()),

    /** IRNSS. */
    IRNSS("IRN", "IR", ts -> ts.getIRNSS()),

    /** SBAS.
     * @since 12.0
     */
    SBAS("SBAS", "SB", ts -> ts.getUTC()),

    /** GMT (should only by used in RUN BY / DATE entries).
     * @since 12.0
     */
    GMT("GMT", null, ts -> ts.getUTC()),

    /** Unknown (should only by used in RUN BY / DATE entries). */
    UNKNOWN("LCL", null, ts -> ts.getGPS());

    /** Parsing key map. */
    private static final Map<String, TimeSystem> KEYS_MAP = new HashMap<>();

    /** Parsing two letters code map.
     * @since 12.0
     */
    private static final Map<String, TimeSystem> TLC_MAP = new HashMap<>();

    static {
        for (final TimeSystem timeSystem : values()) {
            KEYS_MAP.put(timeSystem.key, timeSystem);
            if (timeSystem.twoLettersCode != null) {
                TLC_MAP.put(timeSystem.twoLettersCode, timeSystem);
            }
        }
    }

    /** Key for the system. */
    private final String key;

    /** Two-letters code.
     * @since 12.0
     */
    private final String twoLettersCode;

    /** Time scale provider.
     * @since 12.0
     */
    private final Function<TimeScales, TimeScale> timeScaleProvider;

    /** Simple constructor.
     * @param key key letter
     * @param twoLettersCode two letters code (may be null)
     * @param timeScaleProvider time scale provider
     */
    TimeSystem(final String key, final String twoLettersCode,
               final Function<TimeScales, TimeScale> timeScaleProvider) {
        this.key               = key;
        this.twoLettersCode    = twoLettersCode;
        this.timeScaleProvider = timeScaleProvider;
    }

    /** Parse a string to get the time system.
     * <p>
     * The string must be the time system.
     * </p>
     * @param s string to parse
     * @return the time system
     * @exception OrekitIllegalArgumentException if the string does not correspond to a time system key
     */
    public static TimeSystem parseTimeSystem(final String s)
        throws OrekitIllegalArgumentException {
        final TimeSystem timeSystem = KEYS_MAP.get(s);
        if (timeSystem == null) {
            throw new OrekitIllegalArgumentException(OrekitMessages.UNKNOWN_TIME_SYSTEM, s);
        }
        return timeSystem;
    }

    /** Parse a string to get the time system.
     * <p>
     * The string must be the two letters code of the time system.
     * </p>
     * @param code string to parse
     * @return the time system
     * @exception OrekitIllegalArgumentException if the string does not correspond to a time system key
     */
    public static TimeSystem parseTwoLettersCode(final String code)
        throws OrekitIllegalArgumentException {
        final TimeSystem timeSystem = TLC_MAP.get(code);
        if (timeSystem == null) {
            throw new OrekitIllegalArgumentException(OrekitMessages.UNKNOWN_TIME_SYSTEM, code);
        }
        return timeSystem;
    }

    /** Get the time scale corresponding to time system.
     * @param timeScales the set of time scales to use
     * @return the time scale corresponding to time system in the set of time scales
     */
    public TimeScale getTimeScale(final TimeScales timeScales) {
        return timeScaleProvider.apply(timeScales);
    }

}
