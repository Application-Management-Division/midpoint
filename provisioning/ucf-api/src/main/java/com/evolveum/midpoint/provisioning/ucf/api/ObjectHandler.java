/*
 * Copyright (c) 2010-2017 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.provisioning.ucf.api;

import com.evolveum.midpoint.schema.result.OperationResult;

/**
 * Classes implementing this interface are used to handle iterative results.
 *
 * It is only used to handle iterative search results now. It may be reused for
 * other purposes as well.
 *
 * @author Radovan Semancik
 */
@FunctionalInterface
public interface ObjectHandler {

    /**
     * Handle a single result.
     * @param object Resource object to process.
     * @param result Operation result in context of which the object should be processed.
     * @return true if the operation should proceed, false if it should stop
     */
    boolean handle(UcfObjectFound object, OperationResult result);

}
