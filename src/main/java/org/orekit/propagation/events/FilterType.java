/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.orekit.propagation.events;

import org.orekit.errors.OrekitInternalError;
import org.orekit.propagation.events.handlers.EventHandler;

/** Enumerate for {@link EventSlopeFilter filtering events}.
 * <p>This class is heavily based on the class with the same name from the
 * Hipparchus library. The changes performed consist in package
 * name and error handling.</p>
 * @since 6.0
 */

public enum FilterType {

    /** Constant for triggering only decreasing events.
     * <p>When this filter is used, the wrapped {@link EventHandler
     * event handler} {@link
     * EventHandler#eventOccurred(org.orekit.propagation.SpacecraftState,
     * EventDetector, boolean) eventOccurred} method will be called
     * <em>only</em> with its {@code increasing} argument set to false.</p>
     */
    TRIGGER_ONLY_DECREASING_EVENTS {

        /**  {@inheritDoc} */
        protected boolean getTriggeredIncreasing() {
            return false;
        }

        /** {@inheritDoc}
         * <p>
         * states scheduling for computing h(t, y) as an altered version of g(t, y)
         * <ul>
         * <li>0 are triggered events for which a zero is produced (here decreasing events)</li>
         * <li>X are ignored events for which zero is masked (here increasing events)</li>
         * </ul>
         * </p>
         * <p>
         * Several expressions are used to compute h, depending on the current state:
         * <ul>
         *   <li>h = max(+s, -g, +g)</li>
         *   <li>h = +g</li>
         *   <li>h = min(-s, -g, +g)</li>
         *   <li>h = -g</li>
         * </ul>
         * where s is a tiny positive value: {@link org.hipparchus.util.Precision#SAFE_MIN}.
         * </p>
         */
        protected  Transformer selectTransformer(final Transformer previous,
                                                 final double g, final boolean forward) {
            if (forward) {
                switch (previous) {
                    case UNINITIALIZED :
                        // we are initializing the first point
                        if (g > 0) {
                            // initialize as if previous root (i.e. backward one) was an ignored increasing event
                            return Transformer.MAX;
                        } else if (g < 0) {
                            // initialize as if previous root (i.e. backward one) was a triggered decreasing event
                            return Transformer.PLUS;
                        } else {
                            // we are exactly at a root, we don't know if it is an increasing
                            // or a decreasing event, we remain in uninitialized state
                            return Transformer.UNINITIALIZED;
                        }
                    case PLUS  :
                        if (g >= 0) {
                            // we have crossed the zero line on an ignored increasing event,
                            // we must change the transformer
                            return Transformer.MIN;
                        } else {
                            // we are still in the same status
                            return previous;
                        }
                    case MINUS :
                        if (g >= 0) {
                            // we have crossed the zero line on an ignored increasing event,
                            // we must change the transformer
                            return Transformer.MAX;
                        } else {
                            // we are still in the same status
                            return previous;
                        }
                    case MIN   :
                        if (g <= 0) {
                            // we have crossed the zero line on a triggered decreasing event,
                            // we must change the transformer
                            return Transformer.MINUS;
                        } else {
                            // we are still in the same status
                            return previous;
                        }
                    case MAX   :
                        if (g <= 0) {
                            // we have crossed the zero line on a triggered decreasing event,
                            // we must change the transformer
                            return Transformer.PLUS;
                        } else {
                            // we are still in the same status
                            return previous;
                        }
                    default    :
                        // this should never happen
                        throw new OrekitInternalError(null);
                }
            } else {
                switch (previous) {
                    case UNINITIALIZED :
                        // we are initializing the first point
                        if (g > 0) {
                            // initialize as if previous root (i.e. forward one) was a triggered decreasing event
                            return Transformer.MINUS;
                        } else if (g < 0) {
                            // initialize as if previous root (i.e. forward one) was an ignored increasing event
                            return Transformer.MIN;
                        } else {
                            // we are exactly at a root, we don't know if it is an increasing
                            // or a decreasing event, we remain in uninitialized state
                            return Transformer.UNINITIALIZED;
                        }
                    case PLUS  :
                        if (g <= 0) {
                            // we have crossed the zero line on an ignored increasing event,
                            // we must change the transformer
                            return Transformer.MAX;
                        } else {
                            // we are still in the same status
                            return previous;
                        }
                    case MINUS :
                        if (g <= 0) {
                            // we have crossed the zero line on an ignored increasing event,
                            // we must change the transformer
                            return Transformer.MIN;
                        } else {
                            // we are still in the same status
                            return previous;
                        }
                    case MIN   :
                        if (g >= 0) {
                            // we have crossed the zero line on a triggered decreasing event,
                            // we must change the transformer
                            return Transformer.PLUS;
                        } else {
                            // we are still in the same status
                            return previous;
                        }
                    case MAX   :
                        if (g >= 0) {
                            // we have crossed the zero line on a triggered decreasing event,
                            // we must change the transformer
                            return Transformer.MINUS;
                        } else {
                            // we are still in the same status
                            return previous;
                        }
                    default    :
                        // this should never happen
                        throw new OrekitInternalError(null);
                }
            }
        }

    },

    /** Constant for triggering only increasing events.
     * <p>When this filter is used, the wrapped {@link EventHandler
     * event handler} {@link EventHandler#eventOccurred(org.orekit.propagation.SpacecraftState,
     * EventDetector, boolean) eventOccurred} method will be called <em>only</em> with
     * its {@code increasing} argument set to true.</p>
     */
    TRIGGER_ONLY_INCREASING_EVENTS {

        /**  {@inheritDoc} */
        protected boolean getTriggeredIncreasing() {
            return true;
        }

        /** {@inheritDoc}
         * <p>
         * states scheduling for computing h(t, y) as an altered version of g(t, y)
         * <ul>
         * <li>0 are triggered events for which a zero is produced (here increasing events)</li>
         * <li>X are ignored events for which zero is masked (here decreasing events)</li>
         * </ul>
         * </p>
         * <p>
         * Several expressions are used to compute h, depending on the current state:
         * <ul>
         *   <li>h = max(+s, -g, +g)</li>
         *   <li>h = +g</li>
         *   <li>h = min(-s, -g, +g)</li>
         *   <li>h = -g</li>
         * </ul>
         * where s is a tiny positive value: {@link org.hipparchus.util.Precision#SAFE_MIN}.
         * </p>
         */
        protected  Transformer selectTransformer(final Transformer previous,
                                                 final double g, final boolean forward) {
            if (forward) {
                switch (previous) {
                    case UNINITIALIZED :
                        // we are initializing the first point
                        if (g > 0) {
                            // initialize as if previous root (i.e. backward one) was a triggered increasing event
                            return Transformer.PLUS;
                        } else if (g < 0) {
                            // initialize as if previous root (i.e. backward one) was an ignored decreasing event
                            return Transformer.MIN;
                        } else {
                            // we are exactly at a root, we don't know if it is an increasing
                            // or a decreasing event, we remain in uninitialized state
                            return Transformer.UNINITIALIZED;
                        }
                    case PLUS  :
                        if (g <= 0) {
                            // we have crossed the zero line on an ignored decreasing event,
                            // we must change the transformer
                            return Transformer.MAX;
                        } else {
                            // we are still in the same status
                            return previous;
                        }
                    case MINUS :
                        if (g <= 0) {
                            // we have crossed the zero line on an ignored decreasing event,
                            // we must change the transformer
                            return Transformer.MIN;
                        } else {
                            // we are still in the same status
                            return previous;
                        }
                    case MIN   :
                        if (g >= 0) {
                            // we have crossed the zero line on a triggered increasing event,
                            // we must change the transformer
                            return Transformer.PLUS;
                        } else {
                            // we are still in the same status
                            return previous;
                        }
                    case MAX   :
                        if (g >= 0) {
                            // we have crossed the zero line on a triggered increasing event,
                            // we must change the transformer
                            return Transformer.MINUS;
                        } else {
                            // we are still in the same status
                            return previous;
                        }
                    default    :
                        // this should never happen
                        throw new OrekitInternalError(null);
                }
            } else {
                switch (previous) {
                    case UNINITIALIZED :
                        // we are initializing the first point
                        if (g > 0) {
                            // initialize as if previous root (i.e. forward one) was an ignored decreasing event
                            return Transformer.MAX;
                        } else if (g < 0) {
                            // initialize as if previous root (i.e. forward one) was a triggered increasing event
                            return Transformer.MINUS;
                        } else {
                            // we are exactly at a root, we don't know if it is an increasing
                            // or a decreasing event, we remain in uninitialized state
                            return Transformer.UNINITIALIZED;
                        }
                    case PLUS  :
                        if (g >= 0) {
                            // we have crossed the zero line on an ignored decreasing event,
                            // we must change the transformer
                            return Transformer.MIN;
                        } else {
                            // we are still in the same status
                            return previous;
                        }
                    case MINUS :
                        if (g >= 0) {
                            // we have crossed the zero line on an ignored decreasing event,
                            // we must change the transformer
                            return Transformer.MAX;
                        } else {
                            // we are still in the same status
                            return previous;
                        }
                    case MIN   :
                        if (g <= 0) {
                            // we have crossed the zero line on a triggered increasing event,
                            // we must change the transformer
                            return Transformer.MINUS;
                        } else {
                            // we are still in the same status
                            return previous;
                        }
                    case MAX   :
                        if (g <= 0) {
                            // we have crossed the zero line on a triggered increasing event,
                            // we must change the transformer
                            return Transformer.PLUS;
                        } else {
                            // we are still in the same status
                            return previous;
                        }
                    default    :
                        // this should never happen
                        throw new OrekitInternalError(null);
                }
            }
        }

    };

    /** Get the increasing status of triggered events.
     * @return true if triggered events are increasing events
     */
    protected abstract boolean getTriggeredIncreasing();

    /** Get next function transformer in the specified direction.
     * @param previous transformer active on the previous point with respect
     * to integration direction (may be null if no previous point is known)
     * @param g current value of the g function
     * @param forward true if integration goes forward
     * @return next transformer transformer
     */
    protected abstract Transformer selectTransformer(Transformer previous,
                                                     double g, boolean forward);

}
