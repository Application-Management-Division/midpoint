/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqlbase.filtering;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.query.PropertyValueFilter;
import com.evolveum.midpoint.prism.query.ValueFilter;
import com.evolveum.midpoint.repo.sqlbase.QueryException;
import com.querydsl.core.types.Expression;

/**
 * Object wraps zero, one or multiple values and makes their processing easier.
 * Instead of just wrapping the values it uses the whole filter object
 * to utilize its convenience methods.
 *
 * Returned values are typed to Object, because they can be converted from original type.
 * Conversion is moved into this class, so the client code doesn't have to handle translation
 * from {@link PrismPropertyValue} to "real value" and then to convert it.
 * Both {@link #singleValue()} and {@link #allValues()} are handled the same way.
 *
 * If {@link #conversionFunction} is used any {@link IllegalArgumentException} will be re-wrapped
 * as {@link QueryException}, other runtime exceptions are not intercepted.
 *
 * @param <T> type of filter value
 * @param <V> type of value after conversion (can by the same like T)
 */
public abstract class ValueFilterValues<T, V> {

    @NotNull protected final PropertyValueFilter<T> filter;

    public static <T> ValueFilterValues<T, T> from(@NotNull PropertyValueFilter<T> filter) {
        return new Constant<T,T>(filter, null);
    }

    public static <T, V> ValueFilterValues<T, V>from(@NotNull PropertyValueFilter<T> filter, Expression<?> expression) {
        return new Expr<T,V>(filter, expression);
    }

    public static <T, V> ValueFilterValues<T, V> from(
            @NotNull PropertyValueFilter<T> filter,
            @Nullable Function<T, V> conversionFunction) {
        return new Constant<T,V>(filter, conversionFunction);
    }

    private ValueFilterValues(
            @NotNull PropertyValueFilter<T> filter) {
        this.filter = Objects.requireNonNull(filter);
    }

    /**
     * Returns single value or null or fails if there are multiple values, all converted.
     */
    public abstract @Nullable V singleValue() throws QueryException;

    /**
     * Returns single value or null or fails if there are multiple values without conversion.
     * Null-safe version of {@link ValueFilter#getSingleValue()} followed by
     * {@link PrismPropertyValue#getRealValue()}.
     */
    public abstract @Nullable T singleValueRaw();

    /**
     * Returns multiple values, all converted, or empty list - never null.
     */
    public @NotNull List<V> allValues() {
            return Collections.emptyList();
    }

    /**
     * Returns multiple real values without conversion or empty list - never null.
     */
    public @NotNull List<T> allValuesRaw() {
        return Collections.emptyList();
    }

    public boolean isEmpty() {
        return false;
    }

    public boolean isMultiValue() {
        return false;
    }

    private static class Constant<T,V> extends ValueFilterValues<T, V> {

        @Nullable private final Function<T, V> conversionFunction;

        public Constant(@NotNull PropertyValueFilter<T> filter, @Nullable Function<T, V> conversionFunction) {
            super(filter);
            this.conversionFunction = conversionFunction;
        }

        @Override
        public @Nullable V singleValue() throws QueryException {
            return convert(filter.getSingleValue());
        }

        @Override
        public @Nullable T singleValueRaw() {
            final PrismPropertyValue<T> singleValue = filter.getSingleValue();
            return singleValue != null ? singleValue.getRealValue() : null;
        }

        @Override
        public @NotNull List<V> allValues() {
            if (filter.getValues() == null) {
                return Collections.emptyList();
            }
            Stream<T> realValueStream = filter.getValues().stream()
                    .map(ppv -> ppv.getRealValue());
            if (conversionFunction == null) {
                //noinspection unchecked
                return (List<V>) realValueStream.collect(Collectors.toList());
            }
            return realValueStream
                    .map(conversionFunction)
                    .collect(Collectors.toList());
        }

        @Override
        public @NotNull List<T> allValuesRaw() {
            if (filter.getValues() == null) {
                return Collections.emptyList();
            }
            return filter.getValues().stream()
                    .map(ppv -> ppv.getRealValue())
                    .collect(Collectors.toList());
        }

        private V convert(PrismPropertyValue<T> value) throws QueryException {
            if (value == null) {
                return null;
            }
            if (conversionFunction == null) {
                //noinspection unchecked
                return (V) value.getRealValue();
            }
            try {
                return conversionFunction.apply(value.getRealValue());
            } catch (IllegalArgumentException | ClassCastException e) {
                throw new QueryException(e);
            }
        }

        @Override
        public boolean isEmpty() {
            return filter.getValues() == null || filter.getValues().isEmpty();
        }

        @Override
        public boolean isMultiValue() {
            return filter.getValues() != null && filter.getValues().size() > 1;
        }
    }

    private static class Expr<T,V> extends ValueFilterValues<T, V> {

        private @Nullable Expression<?> expression;

        public Expr(@NotNull PropertyValueFilter<T> filter, Expression<?> expression) {
            super(filter);
            this.expression = expression;
        }

        @Override
        public @Nullable V singleValue() throws QueryException {
            return (V) this.expression;
        }

        @Override
        public @Nullable T singleValueRaw() {
            return (T) this.expression;
        }
    }
}
