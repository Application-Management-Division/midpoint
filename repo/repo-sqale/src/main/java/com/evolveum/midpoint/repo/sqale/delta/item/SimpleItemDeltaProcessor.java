/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.delta.item;

import java.util.function.Function;

import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.Path;

import com.evolveum.midpoint.repo.sqale.RootUpdateContext;

// TODO necessary if this bare?
public class SimpleItemDeltaProcessor<T, P extends Path<T>>
        extends SinglePathItemDeltaProcessor<T, P> {

    public SimpleItemDeltaProcessor(
            RootUpdateContext<?, ?, ?> context, Function<EntityPath<?>, P> rootToQueryItem) {
        super(context, rootToQueryItem);
    }
}
