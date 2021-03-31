/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqlbase.filtering.item;

import java.util.function.Function;

import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.evolveum.midpoint.prism.query.PropertyValueFilter;
import com.evolveum.midpoint.repo.sqlbase.QueryException;
import com.evolveum.midpoint.repo.sqlbase.SqlQueryContext;
import com.evolveum.midpoint.repo.sqlbase.filtering.ValueFilterValues;
import com.evolveum.midpoint.repo.sqlbase.mapping.item.ItemSqlMapper;

/**
 * Filter processor for a an attribute path (Prism item) of enum type that is mapped
 * to SQL as ordinal value.
 *
 * @param <E> type of enum on the enum contained in object filter, this is optionally mapped
 * to final type used for ordinal. Can be {@code null} if no mapping is needed.
 */
public class EnumOrdinalItemFilterProcessor<E extends Enum<E>>
        extends SinglePathItemFilterProcessor<PropertyValueFilter<E>, Path<Integer>> {

    @Nullable
    private final Function<E, Integer> conversionFunction;

    /**
     * Returns the mapper creating the enum filter processor from context.
     * With no value conversion function the filter value must contain enum whose ordinal
     * numbers are used in the repository.
     */
    public static ItemSqlMapper mapper(
            @NotNull Function<EntityPath<?>, Path<Integer>> rootToQueryItem) {
        return mapper(rootToQueryItem, null);
    }

    /**
     * Returns the mapper creating the enum filter processor from context
     * with enum value conversion function.
     */
    public static <E extends Enum<E>> ItemSqlMapper mapper(
            @NotNull Function<EntityPath<?>, Path<Integer>> rootToQueryItem,
            @Nullable Function<E, Enum<?>> conversionFunction) {
        return new ItemSqlMapper(ctx ->
                new EnumOrdinalItemFilterProcessor<>(ctx, rootToQueryItem, conversionFunction),
                rootToQueryItem);
    }

    private EnumOrdinalItemFilterProcessor(
            SqlQueryContext<?, ?, ?> context,
            Function<EntityPath<?>, Path<Integer>> rootToQueryItem,
            @Nullable Function<E, Enum<?>> conversionFunction) {
        super(context, rootToQueryItem);
        this.conversionFunction = conversionFunction != null
                ? conversionFunction.andThen(Enum::ordinal)
                : Enum::ordinal;
    }

    @Override
    public Predicate process(PropertyValueFilter<E> filter) throws QueryException {
        return createBinaryCondition(filter, path,
                ValueFilterValues.from(filter, conversionFunction));
    }
}
