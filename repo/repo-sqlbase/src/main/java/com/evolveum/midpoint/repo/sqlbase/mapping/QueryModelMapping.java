/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqlbase.mapping;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.namespace.QName;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.repo.sqlbase.QueryException;
import com.evolveum.midpoint.repo.sqlbase.filtering.FilterProcessor;
import com.evolveum.midpoint.repo.sqlbase.filtering.item.ItemValueFilterProcessor;
import com.evolveum.midpoint.repo.sqlbase.querydsl.FlexibleRelationalPathBase;
import com.evolveum.midpoint.util.QNameUtil;

/**
 * Common mapping functionality that covers the need for mapping from item paths
 * to table columns, but also to nested embedded mappings (e.g. metadata).
 *
 * This also works as implementation for nested mappings like `metadata` that contain attributes
 * resolved to the same query type - e.g. `metadata/createTimestamp`.
 * While `metadata` is resolved on the master mapping (for the query type representing table)
 * the nested `createTimestamp` is resolved by nested mapper implemented by this type.
 * Nested mapping can still contain relations, so {@link #addRelationResolver} is available.
 *
 * @param <S> schema type
 * @param <Q> type of entity path
 * @param <R> row type related to the {@link Q}
 */
public class QueryModelMapping<S, Q extends FlexibleRelationalPathBase<R>, R> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final Class<S> schemaType;
    private final Class<Q> queryType;

    private final Map<QName, ItemSqlMapper<Q, R>> itemMappings = new LinkedHashMap<>();
    private final Map<QName, ItemRelationResolver<Q, R, ?, ?>> itemRelationResolvers = new HashMap<>();

    public QueryModelMapping(
            @NotNull Class<S> schemaType,
            @NotNull Class<Q> queryType) {
        this.schemaType = schemaType;
        this.queryType = queryType;
    }

    /** Returns schema type as class - refers to midPoint schema, not DB schema. */
    public Class<S> schemaType() {
        return schemaType;
    }

    public Class<Q> queryType() {
        return queryType;
    }

    /**
     * Adds information how item (attribute) from schema type is mapped to query,
     * especially for condition creating purposes.
     * This is not usable for complex item path resolution,
     * see {@link #addRelationResolver(ItemName, ItemRelationResolver)} for that purpose.
     *
     * The {@link ItemSqlMapper} works as a factory for {@link FilterProcessor} that can process
     * {@link ObjectFilter} related to the {@link ItemName} specified as the first parameter.
     * It is not possible to use filter processor directly because at the time of mapping
     * specification we don't have the actual query path representing the entity or the column.
     * These paths are non-static properties of query class instances.
     *
     * The {@link ItemSqlMapper} also provides so called "primary mapping" to a column for ORDER BY
     * part of the filter.
     * But there can be additional column mappings specified as for some types (e.g. poly-strings)
     * there may be other than 1-to-1 mapping.
     *
     * Construction of the {@link ItemSqlMapper} is typically simplified by static methods
     * {@code #mapper()} provided on various {@code *ItemFilterProcessor} classes.
     * This works as a "processor factory factory" and makes table mapping specification simpler.
     *
     * @param itemName item name from schema type (see {@code F_*} constants on schema types)
     * @param itemMapper mapper wrapping the information about column mappings working also
     * as a factory for {@link FilterProcessor}
     */
    public QueryModelMapping<S, Q, R> addItemMapping(
            @NotNull QName itemName,
            @NotNull ItemSqlMapper<Q, R> itemMapper) {
        itemMappings.put(itemName, itemMapper);
        return this;
    }

    /**
     * Adds information how {@link ItemName} (attribute) from schema type is to be resolved
     * when it appears as a component of a complex (non-single) {@link ItemPath}.
     * This is in contrast with "item mapping" that is used for single (or last) component
     * of the item path and helps with query interpretation.
     */
    // TODO add "to-many" option so the interpreter can use WHERE EXISTS instead of JOIN
    public QueryModelMapping<S, Q, R> addRelationResolver(
            @NotNull ItemName itemName,
            @NotNull ItemRelationResolver<Q, R, ?, ?> itemRelationResolver) {
        itemRelationResolvers.put(itemName, itemRelationResolver);
        return this;
    }

    /**
     * Returns {@link ItemSqlMapper} for provided {@link ItemName} or throws.
     * This is later used to create {@link ItemValueFilterProcessor}.
     *
     * @throws QueryException if the mapper for the item is not found
     */
    public final @NotNull ItemSqlMapper<Q, R> itemMapper(QName itemName) throws QueryException {
        ItemSqlMapper<Q, R> itemMapping = getItemMapper(itemName);
        if (itemMapping == null) {
            throw new QueryException("Missing item mapping for '" + itemName
                    + "' in mapping " + getClass().getSimpleName());
        }
        return itemMapping;
    }

    /**
     * Returns {@link ItemSqlMapper} for provided {@link ItemName} or `null`.
     */
    public @Nullable ItemSqlMapper<Q, R> getItemMapper(QName itemName) {
        return QNameUtil.getByQName(this.itemMappings, itemName);
    }

    /**
     * Returns {@link ItemRelationResolver} for provided {@link ItemName} or throws.
     * Relation resolver helps with traversal over all-but-last components of item paths.
     *
     * @param <TQ> type of target entity path
     * @param <TR> row type related to the target entity path {@link TQ}
     * @throws QueryException if the resolver for the item is not found
     */
    public final @NotNull <TQ extends FlexibleRelationalPathBase<TR>, TR>
    ItemRelationResolver<Q, R, TQ, TR> relationResolver(ItemName itemName)
            throws QueryException {
        ItemRelationResolver<Q, R, TQ, TR> resolver = getRelationResolver(itemName);
        if (resolver == null) {
            throw new QueryException("Missing relation resolver for '" + itemName
                    + "' in mapping " + getClass().getSimpleName());
        }
        return resolver;
    }

    /**
     * Returns {@link ItemRelationResolver} for provided {@link ItemName} or `null`.
     *
     * @param <TQ> type of target entity path
     * @param <TR> row type related to the target entity path {@link TQ}
     */
    public final @Nullable <TQ extends FlexibleRelationalPathBase<TR>, TR>
    ItemRelationResolver<Q, R, TQ, TR> getRelationResolver(ItemName itemName) {
        //noinspection unchecked
        return (ItemRelationResolver<Q, R, TQ, TR>)
                QNameUtil.getByQName(itemRelationResolvers, itemName);
    }

    /** Returns copy of the map of the item mappings. */
    public final @NotNull Map<QName, ItemSqlMapper<Q, R>> getItemMappings() {
        return new LinkedHashMap<>(itemMappings);
    }
}
