/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.schema.statistics;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.util.task.ActivityPath;
import com.evolveum.midpoint.util.annotation.Experimental;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivityIterationInformationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface IterativeOperationCollector {

    /**
     * Records the start of iterative operation.
     * The operation end is recorded by calling appropriate method on the returned object.
     */
    @NotNull
    default IterationInformation.Operation recordIterativeOperationStart(PrismObject<? extends ObjectType> object) {
        return recordIterativeOperationStart(new IterationItemInformation(object));
    }

    /**
     * Records the start of iterative operation.
     * The operation end is recorded by calling appropriate method on the returned object.
     */
    @NotNull default IterationInformation.Operation recordIterativeOperationStart(IterationItemInformation info) {
        return recordIterativeOperationStart(new IterativeOperationStartInfo(info));
    }

    /**
     * Records the start of iterative operation.
     * The operation end is recorded by calling appropriate method on the returned object.
     */
    @NotNull IterationInformation.Operation recordIterativeOperationStart(IterativeOperationStartInfo operation);

    /**
     * Records end of part execution: updates execution times.
     */
    void recordPartExecutionEnd(ActivityPath activityPath, long partStartTimestamp, long partEndTimestamp);

    /**
     * Resets iterative task information collection, starting from a given value.
     */
    void resetIterativeTaskInformation(ActivityIterationInformationType value, boolean collectExecutions);

    /**
     * Returns last N failures. Deprecated.
     */
    @NotNull
    @Experimental
    @Deprecated
    List<String> getLastFailures();

}
