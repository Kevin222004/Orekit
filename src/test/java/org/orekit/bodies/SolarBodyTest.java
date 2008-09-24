/* Copyright 2002-2008 CS Communication & Systèmes
 * Licensed to CS Communication & Systèmes (CS) under one or more
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
package org.orekit.bodies;

import java.text.ParseException;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.commons.math.geometry.Vector3D;
import org.orekit.data.DataDirectoryCrawler;
import org.orekit.errors.OrekitException;
import org.orekit.frames.Frame;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.propagation.analytical.KeplerianPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TTScale;
import org.orekit.utils.PVCoordinates;

public class SolarBodyTest extends TestCase {

    public void testGeocentricPV() throws OrekitException, ParseException {
        AbsoluteDate date = new AbsoluteDate(1969, 06, 28, TTScale.getInstance());
        Frame geocentricFrame = Frame.getJ2000();
        checkPV(SolarSystemBody.getMoon(), date, geocentricFrame,
                new Vector3D(-0.0008081773279115, -0.0019946300016204, -0.0010872626608381),
                new Vector3D( 0.0006010848166591, -0.0001674454606152, -0.0000855621449740));
        checkPV(SolarSystemBody.getEarth(), date, geocentricFrame, Vector3D.ZERO, Vector3D.ZERO);
    }

    public void testHeliocentricPV() throws OrekitException, ParseException {
        AbsoluteDate date = new AbsoluteDate(1969, 06, 28, TTScale.getInstance());
        Frame heliocentricFrame = SolarSystemBody.getSun().getFrame();
        checkPV(SolarSystemBody.getSun(), date, heliocentricFrame, Vector3D.ZERO, Vector3D.ZERO);
        checkPV(SolarSystemBody.getMercury(), date, heliocentricFrame,
                new Vector3D( 0.3572602064472754,   -0.0915490424305184, -0.0859810399869404),
                new Vector3D( 0.0033678456621938,    0.0248893428422493,  0.0129440715867960));
        checkPV(SolarSystemBody.getVenus(), date, heliocentricFrame,
                new Vector3D( 0.6082494331856039,   -0.3491324431959005, -0.1955443457854069),
                new Vector3D( 0.0109524201099088,    0.0156125067398625,  0.0063288764517467));
        checkPV(SolarSystemBody.getMars(), date, heliocentricFrame,
                new Vector3D(-0.1146885824390927,   -1.3283665308334880, -0.6061551894193808),
                new Vector3D( 0.0144820048079447,    0.0002372854923607, -0.0002837498361024));
        checkPV(SolarSystemBody.getJupiter(), date, heliocentricFrame,
                new Vector3D(-5.3842094069921451,   -0.8312476561610838, -0.2250947570335498),
                new Vector3D( 0.0010923632912185,   -0.0065232941911923, -0.0028230122672194));
        checkPV(SolarSystemBody.getSaturn(), date, heliocentricFrame,
                new Vector3D( 7.8898899338228166,    4.5957107269260122,  1.558431516725089),
                new Vector3D(-0.0032172034910937,    0.0043306322335557,  0.0019264174637995));
        checkPV(SolarSystemBody.getUranus(), date, heliocentricFrame,
                new Vector3D(-18.2699008149782607,  -1.1627115802190469, -0.2503695407425549),
                new Vector3D(  0.0002215401656274,  -0.0037676535582462, -0.0016532438049224));
        checkPV(SolarSystemBody.getNeptune(), date, heliocentricFrame,
                new Vector3D(-16.0595450919244627, -23.9429482908794995, -9.4004227803540061),
                new Vector3D(  0.0026431227915766,  -0.0015034920807588, -0.0006812710048723));
        checkPV(SolarSystemBody.getPluto(), date, heliocentricFrame,
                new Vector3D(-30.4878221121555448,  -0.8732454301967293,  8.9112969841847551),
                new Vector3D(  0.0003225621959332,  -0.0031487479275516, -0.0010801779315937));
    }

    private void checkPV(CelestialBody body, AbsoluteDate date, Frame frame,
                         Vector3D position, Vector3D velocity)
    throws OrekitException {

        PVCoordinates pv = body.getPVCoordinates(date, frame);

        final double posScale = 149597870691.0;
        final double velScale = posScale / 86400.0;
        PVCoordinates reference =
            new PVCoordinates(new Vector3D(posScale, position), new Vector3D(velScale, velocity));

        PVCoordinates error = new PVCoordinates(1.0, pv, -1.0, reference);
        assertEquals(0, error.getPosition().getNorm(), 2.0e-3);
        assertEquals(0, error.getVelocity().getNorm(), 2.0e-10);

    }

    public void testKepler() throws OrekitException {
        AbsoluteDate date = new AbsoluteDate(1969, 06, 28, TTScale.getInstance());
        final double au = 149597870691.0;
        //checkKepler(SolarSystemBody.getMoon(),    SolarSystemBody.getEarth(), date, 3.844e8);
        checkKepler(SolarSystemBody.getMercury(), SolarSystemBody.getSun(),   date,  0.387 * au);
        checkKepler(SolarSystemBody.getVenus(),   SolarSystemBody.getSun(),   date,  0.723 * au);
        checkKepler(SolarSystemBody.getEarth(),   SolarSystemBody.getSun(),   date,  1.000 * au);
        checkKepler(SolarSystemBody.getMars(),    SolarSystemBody.getSun(),   date,  1.52  * au);
        checkKepler(SolarSystemBody.getJupiter(), SolarSystemBody.getSun(),   date,  5.20  * au);
        checkKepler(SolarSystemBody.getSaturn(),  SolarSystemBody.getSun(),   date,  9.58  * au);
        checkKepler(SolarSystemBody.getUranus(),  SolarSystemBody.getSun(),   date, 19.20  * au);
        checkKepler(SolarSystemBody.getNeptune(), SolarSystemBody.getSun(),   date, 30.05  * au);
        checkKepler(SolarSystemBody.getPluto(),   SolarSystemBody.getSun(),   date, 39.24  * au);
    }

    private void checkKepler(final CelestialBody orbiting, final CelestialBody central,
                             final AbsoluteDate start, final double a)
        throws OrekitException {

        // set up Keplerian orbit of orbiting body around central body
        Orbit orbit = new KeplerianOrbit(orbiting.getPVCoordinates(start, central.getFrame()),
                                         central.getFrame(),start, central.getGM());
        KeplerianPropagator propagator = new KeplerianPropagator(orbit);
        assertEquals(a, orbit.getA(), 0.02 * a);
        double duration = 0.01 * orbit.getKeplerianPeriod();

        double max = 0;
        for (AbsoluteDate date = start;
             date.durationFrom(start) < duration;
             date = new AbsoluteDate(date, duration / 100)) {
            PVCoordinates ephemPV = orbiting.getPVCoordinates(date, central.getFrame());
            PVCoordinates keplerPV = propagator.propagate(date).getPVCoordinates();
            Vector3D error = keplerPV.getPosition().subtract(ephemPV.getPosition());
            max = Math.max(max, error.getNorm());
        }
        assertTrue(max < 1.0e-4 * a);
    }

    public void setUp() {
        System.setProperty(DataDirectoryCrawler.DATA_ROOT_DIRECTORY_FS, "");
        System.setProperty(DataDirectoryCrawler.DATA_ROOT_DIRECTORY_CP, "regular-data");
    }

    public static Test suite() {
        return new TestSuite(SolarBodyTest.class);
    }

}
