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

package org.orekit.files.ccsds.ndm.odm.opm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.files.ccsds.ndm.odm.OStateData;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.utils.PVCoordinates;

/** This class gathers the informations present in the Orbital Parameter Message (OPM), and contains
 * methods to generate {@link CartesianOrbit}, {@link KeplerianOrbit} or {@link SpacecraftState}.
 * @author sports
 * @since 6.1
 */
public class OPMData extends OStateData {

    /** Position vector (m). */
    private Vector3D position;

    /** Velocity vector (m/s). */
    private Vector3D velocity;

    /** Maneuvers. */
    private List<OPMManeuver> maneuvers;

    /** Create an empty data set.
     */
    OPMData() {
        maneuvers = new ArrayList<>();
    }

    /** Get position vector.
     * @return the position vector
     */
    public Vector3D getPosition() {
        return position;
    }

    /** Set position vector.
     * @param position the position vector to be set
     */
    void setPosition(final Vector3D position) {
        this.position = position;
    }

    /** Get velocity vector.
     * @return the velocity vector
     */
    public Vector3D getVelocity() {
        return velocity;
    }

    /** Set velocity vector.
     * @param velocity the velocity vector to be set
     */
    void setVelocity(final Vector3D velocity) {
        this.velocity = velocity;
    }

    /** Get the number of maneuvers present in the OPM.
     * @return the number of maneuvers
     */
    public int getNbManeuvers() {
        return maneuvers.size();
    }

    /** Get a list of all maneuvers.
     * @return unmodifiable list of all maneuvers.
     */
    public List<OPMManeuver> getManeuvers() {
        return Collections.unmodifiableList(maneuvers);
    }

    /** Get a maneuver.
     * @param index maneuver index, counting from 0
     * @return maneuver
     */
    public OPMManeuver getManeuver(final int index) {
        return maneuvers.get(index);
    }

    /** Add a maneuver.
     * @param maneuver maneuver to be set
     */
    void addManeuver(final OPMManeuver maneuver) {
        maneuvers.add(maneuver);
    }

    /** Get boolean testing whether the OPM contains at least one maneuver.
     * @return true if OPM contains at least one maneuver
     *         false otherwise */
    public boolean hasManeuver() {
        return !maneuvers.isEmpty();
    }

    /** Get the position/velocity coordinates contained in the OPM.
     * @return the position/velocity coordinates contained in the OPM
     */
    public PVCoordinates getPVCoordinates() {
        return new PVCoordinates(getPosition(), getVelocity());
    }

}

