/* Copyright 2002-2012 Space Applications Services
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
package org.orekit.files.sp3;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.orekit.Utils;
import org.orekit.data.DataSource;
import org.orekit.data.UnixCompressFilter;
import org.orekit.errors.OrekitException;
import org.orekit.errors.OrekitMessages;
import org.orekit.files.sp3.SP3File.SP3Coordinate;
import org.orekit.files.sp3.SP3File.SP3Ephemeris;
import org.orekit.files.sp3.SP3File.SP3OrbitType;
import org.orekit.files.sp3.SP3File.TimeSystem;
import org.orekit.frames.FactoryManagedFrame;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.Predefined;
import org.orekit.propagation.BoundedPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;
import org.orekit.utils.PVCoordinates;

public class SP3ParserTest {

    @Test
    public void testParseSP3a1() throws IOException, URISyntaxException {
        // simple test for version sp3-a, only contains position entries
        final String    ex     = "/sp3/example-a-1.sp3";
        final DataSource source = new DataSource(ex, () -> getClass().getResourceAsStream(ex));
        final SP3File   file   = new SP3Parser().parse(source);

        Assert.assertEquals(SP3OrbitType.FIT, file.getOrbitType());
        Assert.assertEquals(TimeSystem.GPS, file.getTimeSystem());
        Assert.assertSame(Predefined.ITRF_CIO_CONV_2010_ACCURATE_EOP,
                          ((FactoryManagedFrame) file.getSatellites().get("1").getFrame()).getFactoryKey());

        Assert.assertEquals(25, file.getSatelliteCount());

        final List<SP3Coordinate> coords = file.getSatellites().get("1").getCoordinates();
        Assert.assertEquals(3, coords.size());

        final SP3Coordinate coord = coords.get(0);

        // 1994 12 17 0 0 0.00000000
        Assert.assertEquals(new AbsoluteDate(1994, 12, 17, 0, 0, 0,
                TimeScalesFactory.getGPS()), coord.getDate());

        // P 1 16258.524750 -3529.015750 -20611.427050 -62.540600
        checkPVEntry(new PVCoordinates(new Vector3D(16258524.75, -3529015.75, -20611427.049),
                                       Vector3D.ZERO),
                     coord);
        Assert.assertEquals(-0.0000625406, coord.getClockCorrection(), 1.0e-15);
        Assert.assertEquals("NGS", file.getAgency());
        Assert.assertEquals("ITR92", file.getCoordinateSystem());
        Assert.assertEquals("d", file.getDataUsed());
        Assert.assertEquals(0.0, file.getDayFraction(), 1.0e-15);
        Assert.assertEquals("1994-12-16T23:59:50.000", file.getEpoch().toString(TimeScalesFactory.getUTC()));
        Assert.assertEquals(49703, file.getJulianDay());
        Assert.assertEquals(3, file.getNumberOfEpochs());
        Assert.assertEquals(900.0, file.getEpochInterval(), 1.0e-15);
        Assert.assertEquals(779, file.getGpsWeek());
        Assert.assertEquals(518400.0, file.getSecondsOfWeek(), 1.0e-10);
        Assert.assertEquals(25, file.getSatellites().size());
        Assert.assertEquals(SP3File.SP3FileType.UNDEFINED, file.getType());
        Assert.assertNull(file.getSatellites().get(null));
    }

    @Test
    public void testParseSP3a2() throws IOException {
        // simple test for version sp3-a, contains p/v entries
        final String    ex     = "/sp3/example-a-2.sp3";
        final DataSource source = new DataSource(ex, () -> getClass().getResourceAsStream(ex));
        final SP3File   file   = new SP3Parser().parse(source);

        Assert.assertEquals(SP3OrbitType.FIT, file.getOrbitType());
        Assert.assertEquals(TimeSystem.GPS, file.getTimeSystem());

        Assert.assertEquals(25, file.getSatelliteCount());

        final List<SP3Coordinate> coords = file.getSatellites().get("1").getCoordinates();
        Assert.assertEquals(3, coords.size());

        final SP3Coordinate coord = coords.get(0);

        // 1994 12 17 0 0 0.00000000
        Assert.assertEquals(new AbsoluteDate(1994, 12, 17, 0, 0, 0,
                TimeScalesFactory.getGPS()), coord.getDate());

        // P 1 16258.524750 -3529.015750 -20611.427050 -62.540600
        // V 1  -6560.373522  25605.954994  -9460.427179     -0.024236
        checkPVEntry(new PVCoordinates(new Vector3D(16258524.75, -3529015.75, -20611427.049),
                                       new Vector3D(-656.0373, 2560.5954, -946.0427)),
                     coord);
        Assert.assertEquals(-0.0000625406, coord.getClockCorrection(), 1.0e-15);
        Assert.assertEquals(-0.0000024236, coord.getClockRateChange(), 1.0e-15);
    }

    @Test
    public void testParseSP3c1() throws IOException {
        // simple test for version sp3-c, contains p entries
        final String    ex     = "/sp3/example-c-1.sp3";
        final DataSource source = new DataSource(ex, () -> getClass().getResourceAsStream(ex));
        final SP3File   file   = new SP3Parser().parse(source);

        Assert.assertEquals(SP3OrbitType.HLM, file.getOrbitType());
        Assert.assertEquals(TimeSystem.GPS, file.getTimeSystem());

        Assert.assertEquals(26, file.getSatelliteCount());

        final List<SP3Coordinate> coords = file.getSatellites().get("G01").getCoordinates();
        Assert.assertEquals(2, coords.size());

        final SP3Coordinate coord = coords.get(0);

        // 2001  8  8  0  0  0.00000000
        Assert.assertEquals(new AbsoluteDate(2001, 8, 8, 0, 0, 0,
                TimeScalesFactory.getGPS()), coord.getDate());

        // PG01 -11044.805800 -10475.672350  21929.418200    189.163300 18 18 18 219
        checkPVEntry(new PVCoordinates(new Vector3D(-11044805.8, -10475672.35, 21929418.2),
                                       Vector3D.ZERO),
                     coord);
        Assert.assertEquals(0.0001891633, coord.getClockCorrection(), 1.0e-15);
    }

    @Test
    public void testParseSP3c2() throws IOException {
        // simple test for version sp3-c, contains p/v entries and correlations
        final String    ex     = "/sp3/example-c-2.sp3";
        final DataSource source = new DataSource(ex, () -> getClass().getResourceAsStream(ex));
        final SP3File   file   = new SP3Parser().parse(source);

        Assert.assertEquals(SP3OrbitType.HLM, file.getOrbitType());
        Assert.assertEquals(TimeSystem.GPS, file.getTimeSystem());

        Assert.assertEquals(26, file.getSatelliteCount());

        final List<SP3Coordinate> coords = file.getSatellites().get("G01").getCoordinates();
        Assert.assertEquals(2, coords.size());

        final SP3Coordinate coord = coords.get(0);

        // 2001  8  8  0  0  0.00000000
        Assert.assertEquals(new AbsoluteDate(2001, 8, 8, 0, 0, 0,
                TimeScalesFactory.getGPS()), coord.getDate());

        // PG01 -11044.805800 -10475.672350  21929.418200    189.163300 18 18 18 219
        // VG01  20298.880364 -18462.044804   1381.387685     -4.534317 14 14 14 191
        checkPVEntry(new PVCoordinates(new Vector3D(-11044805.8, -10475672.35, 21929418.2),
                                       new Vector3D(2029.8880364, -1846.2044804, 138.1387685)),
                     coord);
        Assert.assertEquals(0.0001891633,  coord.getClockCorrection(), 1.0e-15);
        Assert.assertEquals(-0.0004534317, coord.getClockRateChange(), 1.0e-15);
    }

    @Test
    public void testParseSP3d1() throws IOException {
        // simple test for version sp3-d, contains p entries
        final String    ex     = "/sp3/example-d-1.sp3";
        final DataSource source = new DataSource(ex, () -> getClass().getResourceAsStream(ex));
        final SP3File   file   = new SP3Parser().parse(source);

        Assert.assertEquals(SP3OrbitType.BCT, file.getOrbitType());
        Assert.assertEquals(TimeSystem.GPS, file.getTimeSystem());

        Assert.assertEquals(140, file.getSatelliteCount());

        final List<SP3Coordinate> coords = file.getSatellites().get("S37").getCoordinates();
        Assert.assertEquals(2, coords.size());

        final SP3Coordinate coord = coords.get(0);

        // 2013  4  3  0  0  0.00000000
        Assert.assertEquals(new AbsoluteDate(2013, 4, 3, 0, 0, 0,
                TimeScalesFactory.getGPS()), coord.getDate());

        // PS37 -34534.904566  24164.610955     29.812840      0.299420
        checkPVEntry(new PVCoordinates(new Vector3D(-34534904.566, 24164610.955, 29812.840),
                                       Vector3D.ZERO),
                     coord);
        Assert.assertEquals(0.00000029942, coord.getClockCorrection(), 1.0e-15);
    }

    @Test
    public void testParseSP3d2() throws IOException {
        // simple test for version sp3-c, contains p/v entries and correlations
        final String    ex     = "/sp3/example-d-2.sp3";
        final DataSource source = new DataSource(ex, () -> getClass().getResourceAsStream(ex));
        final SP3File   file   = new SP3Parser().parse(source);

        Assert.assertEquals(SP3OrbitType.HLM, file.getOrbitType());
        Assert.assertEquals(TimeSystem.GPS, file.getTimeSystem());

        Assert.assertEquals(26, file.getSatelliteCount());

        final List<SP3Coordinate> coords = file.getSatellites().get("G01").getCoordinates();
        Assert.assertEquals(2, coords.size());

        final SP3Coordinate coord = coords.get(0);

        // 2001  8  8  0  0  0.00000000
        Assert.assertEquals(new AbsoluteDate(2001, 8, 8, 0, 0, 0,
                TimeScalesFactory.getGPS()), coord.getDate());

        // PG01 -11044.805800 -10475.672350  21929.418200    189.163300 18 18 18 219
        // VG01  20298.880364 -18462.044804   1381.387685     -4.534317 14 14 14 191
        checkPVEntry(new PVCoordinates(new Vector3D(-11044805.8, -10475672.35, 21929418.2),
                                       new Vector3D(2029.8880364, -1846.2044804, 138.1387685)),
                     coord);
        Assert.assertEquals(0.0001891633,  coord.getClockCorrection(), 1.0e-15);
        Assert.assertEquals(-0.0004534317, coord.getClockRateChange(), 1.0e-15);
    }

    @Test
    public void testSP3GFZ() throws IOException {
        // simple test for version sp3-c, contains more than 85 satellites
        final String    ex     = "/sp3/gbm19500_truncated.sp3";
        final DataSource source = new DataSource(ex, () -> getClass().getResourceAsStream(ex));
        final SP3File   file   = new SP3Parser().parse(source);

        Assert.assertEquals(SP3OrbitType.FIT, file.getOrbitType());
        Assert.assertEquals(TimeSystem.GPS, file.getTimeSystem());

        Assert.assertEquals(87, file.getSatelliteCount());

        final List<SP3Coordinate> coords = file.getSatellites().get("R23").getCoordinates();
        Assert.assertEquals(2, coords.size());

        final SP3Coordinate coord = coords.get(0);

        Assert.assertEquals(new AbsoluteDate(2017, 5, 21, 0, 0, 0,
                TimeScalesFactory.getGPS()), coord.getDate());

        // PG01 -11044.805800 -10475.672350  21929.418200    189.163300 18 18 18 219
        // PR23  24552.470459   -242.899447   6925.437998     86.875825                    
        checkPVEntry(new PVCoordinates(new Vector3D(24552470.459, -242899.447, 6925437.998),
                                       Vector3D.ZERO),
                     coord);
        Assert.assertEquals(0.000086875825, coord.getClockCorrection(), 1.0e-15);
    }

    @Test
    public void testSP3Propagator() throws Exception {
        // setup
        final String    ex     = "/sp3/example-a-2.sp3";
        final DataSource source = new DataSource(ex, () -> getClass().getResourceAsStream(ex));
        final Frame     frame  = FramesFactory.getITRF(IERSConventions.IERS_2003, true);
        final SP3Parser parser = new SP3Parser(Constants.EIGEN5C_EARTH_MU, 3, s -> frame);

        // action
        final SP3File file = parser.parse(source);

        // verify
        TimeScale gps = TimeScalesFactory.getGPS();
        SP3Ephemeris ephemeris = file.getSatellites().get("1");
        BoundedPropagator propagator = ephemeris.getPropagator();
        Assert.assertEquals(propagator.getMinDate(), new AbsoluteDate(1994, 12, 17, gps));
        Assert.assertEquals(propagator.getMaxDate(), new AbsoluteDate(1994, 12, 17, 23, 45, 0, gps));
        SP3Coordinate expected = ephemeris.getCoordinates().get(0);
        checkPVEntry(
                propagator.getPVCoordinates(propagator.getMinDate(), frame),
                expected);
        expected = ephemeris.getCoordinates().get(1);
        checkPVEntry(propagator.getPVCoordinates(expected.getDate(), frame), expected);
        expected = ephemeris.getCoordinates().get(2);
        checkPVEntry(
                propagator.getPVCoordinates(propagator.getMaxDate(), frame),
                expected);

        ephemeris = file.getSatellites().get("31");
        propagator = ephemeris.getPropagator();
        Assert.assertEquals(propagator.getMinDate(), new AbsoluteDate(1994, 12, 17, gps));
        Assert.assertEquals(propagator.getMaxDate(), new AbsoluteDate(1994, 12, 17, 23, 45, 0, gps));
        expected = ephemeris.getCoordinates().get(0);
        checkPVEntry(
                propagator.propagate(propagator.getMinDate()).getPVCoordinates(frame),
                expected);
        expected = ephemeris.getCoordinates().get(1);
        checkPVEntry(propagator.propagate(expected.getDate()).getPVCoordinates(frame), expected);
        expected = ephemeris.getCoordinates().get(2);
        checkPVEntry(
                propagator.propagate(propagator.getMaxDate()).getPVCoordinates(frame),
                expected);
    }

    @Test
    public void testSP3Compressed() throws IOException {
        final String ex = "/sp3/gbm18432.sp3.Z";

        final SP3Parser parser = new SP3Parser();
        final DataSource compressed = new DataSource(ex, () -> getClass().getResourceAsStream(ex));
        final SP3File file = parser.parse(new UnixCompressFilter().filter(compressed));

        Assert.assertEquals(SP3OrbitType.FIT, file.getOrbitType());
        Assert.assertEquals("FIT",file.getOrbitTypeKey());
        Assert.assertEquals(TimeSystem.GPS, file.getTimeSystem());

        Assert.assertEquals(71, file.getSatelliteCount());

        final List<SP3Coordinate> coords = file.getSatellites().get("R13").getCoordinates();
        Assert.assertEquals(288, coords.size());

        final SP3Coordinate coord = coords.get(228);

        
        Assert.assertEquals(new AbsoluteDate(2015, 5, 5, 19, 0, 0,
                TimeScalesFactory.getGPS()), coord.getDate());

        // PR13  25330.290321   -411.728000   2953.331527   -482.447619
        checkPVEntry(new PVCoordinates(new Vector3D(25330290.321, -411728.000, 2953331.527),
                                       Vector3D.ZERO),
                     coord);
        Assert.assertEquals(-0.000482447619,  coord.getClockCorrection(), 1.0e-15);
    }

    private void checkPVEntry(final PVCoordinates expected, final PVCoordinates actual) {
        final Vector3D expectedPos = expected.getPosition();
        final Vector3D expectedVel = expected.getVelocity();

        final Vector3D actualPos = actual.getPosition();
        final Vector3D actualVel = actual.getVelocity();

        // sp3 files can have mm accuracy
        final double eps = 1e-3;

        Assert.assertEquals(expectedPos.getX(), actualPos.getX(), eps);
        Assert.assertEquals(expectedPos.getY(), actualPos.getY(), eps);
        Assert.assertEquals(expectedPos.getZ(), actualPos.getZ(), eps);

        Assert.assertEquals(expectedVel.getX(), actualVel.getX(), eps);
        Assert.assertEquals(expectedVel.getY(), actualVel.getY(), eps);
        Assert.assertEquals(expectedVel.getZ(), actualVel.getZ(), eps);

        Assert.assertEquals(Vector3D.ZERO, actual.getAcceleration());
    }

    @Test
    public void testTruncatedLine() throws IOException {
        try {
            final String    ex     = "/sp3/truncated-line.sp3";
            final DataSource source = new DataSource(ex, () -> getClass().getResourceAsStream(ex));
            final Frame     frame = FramesFactory.getITRF(IERSConventions.IERS_2003, true);
            final SP3Parser parser = new SP3Parser(Constants.EIGEN5C_EARTH_MU, 3, s -> frame);
            parser.parse(source);
            Assert.fail("an exception should have been thrown");
        } catch (OrekitException oe) {
            Assert.assertEquals(OrekitMessages.UNABLE_TO_PARSE_LINE_IN_FILE,
                                oe.getSpecifier());
            Assert.assertEquals(27, ((Integer) oe.getParts()[0]).intValue());
        }

    }

    @Test
    public void testMissingEOF() throws IOException {
        try {
            final String    ex     = "/sp3/missing-eof.sp3";
            final DataSource source = new DataSource(ex, () -> getClass().getResourceAsStream(ex));
            final Frame     frame  = FramesFactory.getITRF(IERSConventions.IERS_2003, true);
            final SP3Parser parser = new SP3Parser(Constants.EIGEN5C_EARTH_MU, 3, s -> frame);
            parser.parse(source);
            Assert.fail("an exception should have been thrown");
        } catch (OrekitException oe) {
            Assert.assertEquals(OrekitMessages.SP3_UNEXPECTED_END_OF_FILE,
                                oe.getSpecifier());
            Assert.assertEquals(24, ((Integer) oe.getParts()[0]).intValue());
        }

    }

    @Test
    public void testWrongLineIdentifier() throws IOException {
        try {
            final String    ex     = "/sp3/wrong-line-identifier.sp3";
            final DataSource source = new DataSource(ex, () -> getClass().getResourceAsStream(ex));
            final Frame     frame  = FramesFactory.getITRF(IERSConventions.IERS_2003, true);
            final SP3Parser parser = new SP3Parser(Constants.EIGEN5C_EARTH_MU, 3, s -> frame);
            parser.parse(source);
            Assert.fail("an exception should have been thrown");
        } catch (OrekitException oe) {
            Assert.assertEquals(OrekitMessages.UNABLE_TO_PARSE_LINE_IN_FILE,
                                oe.getSpecifier());
            Assert.assertEquals(13, ((Integer) oe.getParts()[0]).intValue());
        }

    }

    @Test
    public void testBHN() throws IOException {
        final Frame       frame        = FramesFactory.getITRF(IERSConventions.IERS_2003, true);
        final SP3Parser   parser       = new SP3Parser(Constants.EIGEN5C_EARTH_MU, 3, s -> frame);
        final String      ex           = "/sp3/esaBHN.sp3.Z";
        final DataSource   compressed   = new DataSource(ex, () -> getClass().getResourceAsStream(ex));
        final DataSource   uncompressed = new UnixCompressFilter().filter(compressed);
        final SP3File     file         = parser.parse(uncompressed);
        Assert.assertEquals(SP3OrbitType.FIT, file.getOrbitType());
        Assert.assertEquals("BHN",file.getOrbitTypeKey());
    }

    @Test
    public void testPRO() throws IOException {
        final Frame       frame        = FramesFactory.getITRF(IERSConventions.IERS_2003, true);
        final SP3Parser   parser       = new SP3Parser(Constants.EIGEN5C_EARTH_MU, 3, s -> frame);
        final String      ex           = "/sp3/esaPRO.sp3.Z";
        final DataSource   compressed   = new DataSource(ex, () -> getClass().getResourceAsStream(ex));
        final DataSource   uncompressed = new UnixCompressFilter().filter(compressed);
        final SP3File     file         = parser.parse(uncompressed);
        Assert.assertEquals(SP3OrbitType.EXT, file.getOrbitType());
        Assert.assertEquals("PRO",file.getOrbitTypeKey());
    }

    @Test
    public void testUnknownType() throws IOException {
        final Frame       frame        = FramesFactory.getITRF(IERSConventions.IERS_2003, true);
        final SP3Parser   parser       = new SP3Parser(Constants.EIGEN5C_EARTH_MU, 3, s -> frame);
        final String      ex           = "/sp3/unknownType.sp3.Z";
        final DataSource   compressed   = new DataSource(ex, () -> getClass().getResourceAsStream(ex));
        final DataSource   uncompressed = new UnixCompressFilter().filter(compressed);
        final SP3File     file         = parser.parse(uncompressed);
        Assert.assertEquals(SP3OrbitType.OTHER, file.getOrbitType());
        Assert.assertEquals("UKN",file.getOrbitTypeKey());
    }

    @Test
    public void testUnsupportedVersion() throws IOException {
        try {
            final String    ex     = "/sp3/unsupported-version.sp3";
            final DataSource source = new DataSource(ex, () -> getClass().getResourceAsStream(ex));
            final Frame     frame  = FramesFactory.getITRF(IERSConventions.IERS_2003, true);
            final SP3Parser parser = new SP3Parser(Constants.EIGEN5C_EARTH_MU, 3, s -> frame);
            parser.parse(source);
            Assert.fail("an exception should have been thrown");
        } catch (OrekitException oe) {
            Assert.assertEquals(OrekitMessages.SP3_UNSUPPORTED_VERSION,
                                oe.getSpecifier());
            Assert.assertEquals('z', ((Character) oe.getParts()[0]).charValue());
        }

    }

    @Test
    public void testWrongNumberOfEpochs() throws IOException {
        try {
            final String    ex     = "/sp3/wrong-number-of-epochs.sp3";
            final DataSource source = new DataSource(ex, () -> getClass().getResourceAsStream(ex));
            final Frame     frame  = FramesFactory.getITRF(IERSConventions.IERS_2003, true);
            final SP3Parser parser = new SP3Parser(Constants.EIGEN5C_EARTH_MU, 3, s -> frame);
            parser.parse(source);
            Assert.fail("an exception should have been thrown");
        } catch (OrekitException oe) {
            Assert.assertEquals(OrekitMessages.SP3_NUMBER_OF_EPOCH_MISMATCH,
                                oe.getSpecifier());
            Assert.assertEquals(  2, ((Integer) oe.getParts()[0]).intValue());
            Assert.assertEquals(192, ((Integer) oe.getParts()[2]).intValue());
        }

    }

    @Before
    public void setUp() {
        Utils.setDataRoot("regular-data");
    }
}
