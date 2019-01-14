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
package org.orekit.propagation.semianalytical.dsst.forces;

import org.orekit.propagation.semianalytical.dsst.utilities.AuxiliaryElements;

/** This class is a container for the field attributes of
 * {@link org.orekit.propagation.semianalytical.dsst.forces.DSSTNewtonianAttractionContext DSSTNewtonianAttractionContext}. */
public class DSSTNewtonianAttractionContext extends ForceModelContext {

    /** Standard gravitational parameter μ for the body in m³/s². */
    private final double           gm;

    /** Simple constructor.
     * Performs initialization at each integration step for the current force model.
     * This method aims at being called before mean elements rates computation
     * @param auxiliaryElements auxiliary elements related to the current orbit
     * @param parameters values of the force model parameters
     */
    DSSTNewtonianAttractionContext(final AuxiliaryElements auxiliaryElements, final double[] parameters) {

        super(auxiliaryElements);
        this.gm = parameters[0];
    }

    /** Get standard gravitational parameter μ for the body in m³/s².
     *  @return gm
     */
    public double getGM() {
        return gm;
    }
}
