/*
 * Copyright (C) 2010-2023 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.impl.mining.algorithm.cluster.mechanism;

import java.io.Serializable;
import java.util.Set;

/**
 * A distance measure interface for calculating the similarity or distance between two sets of values.
 * Implementations of this interface provide custom methods to compute the distance between two sets.
 * The distance measure is used in clustering and similarity calculations for various data points.
 */
public interface DistanceMeasure extends Serializable {

    /**
     * Computes the distance or similarity between two sets of values.
     *
     * @param valueA The first set of values.
     * @param valueB The second set of values.
     * @return The computed distance or similarity between the sets.
     */
    double compute(Set<String> valueA, Set<String> valueB);
}
