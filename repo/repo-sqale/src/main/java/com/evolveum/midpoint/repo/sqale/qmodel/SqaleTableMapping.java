/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.qmodel;

import java.util.function.Function;
import javax.xml.namespace.QName;

import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.dsl.*;
import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.repo.sqale.delta.item.*;
import com.evolveum.midpoint.repo.sqale.filtering.ObjectRefTableItemFilterProcessor;
import com.evolveum.midpoint.repo.sqale.filtering.RefItemFilterProcessor;
import com.evolveum.midpoint.repo.sqale.filtering.UriItemFilterProcessor;
import com.evolveum.midpoint.repo.sqale.mapping.NestedMappingResolver;
import com.evolveum.midpoint.repo.sqale.mapping.SqaleItemSqlMapper;
import com.evolveum.midpoint.repo.sqale.qmodel.object.MObjectType;
import com.evolveum.midpoint.repo.sqale.qmodel.object.QObject;
import com.evolveum.midpoint.repo.sqale.qmodel.ref.QObjectReferenceMapping;
import com.evolveum.midpoint.repo.sqlbase.filtering.item.EnumItemFilterProcessor;
import com.evolveum.midpoint.repo.sqlbase.filtering.item.PolyStringItemFilterProcessor;
import com.evolveum.midpoint.repo.sqlbase.filtering.item.SimpleItemFilterProcessor;
import com.evolveum.midpoint.repo.sqlbase.filtering.item.TimestampItemFilterProcessor;
import com.evolveum.midpoint.repo.sqlbase.mapping.QueryModelMapping;
import com.evolveum.midpoint.repo.sqlbase.mapping.QueryTableMapping;
import com.evolveum.midpoint.repo.sqlbase.querydsl.FlexibleRelationalPathBase;
import com.evolveum.midpoint.repo.sqlbase.querydsl.UuidPath;

/**
 * Mapping superclass with common functions for {@link QObject} and non-objects (e.g. containers).
 *
 * @see QueryTableMapping
 */
public abstract class SqaleTableMapping<S, Q extends FlexibleRelationalPathBase<R>, R>
        extends QueryTableMapping<S, Q, R> {

    protected SqaleTableMapping(
            @NotNull String tableName,
            @NotNull String defaultAliasName,
            @NotNull Class<S> schemaType,
            @NotNull Class<Q> queryType) {
        super(tableName, defaultAliasName, schemaType, queryType);
    }

    /** Nested mapping adaptation for repo-sqale. */
    public <N extends Containerable> SqaleNestedMapping<N, Q, R> addNestedMapping(
            @NotNull ItemName itemName, @NotNull Class<N> nestedSchemaType) {
        SqaleNestedMapping<N, Q, R> nestedMapping =
                new SqaleNestedMapping<>(nestedSchemaType, queryType());
        addRelationResolver(itemName, new NestedMappingResolver<>(nestedMapping));
        // first function for query doesn't matter, it just can't be null
        addItemMapping(itemName, new SqaleItemSqlMapper(ctx -> null,
                ctx -> new EmbeddedContainerDeltaProcessor<N>(ctx, nestedMapping)));
        return nestedMapping;
    }

    // TODO will the version for RefItemFilterProcessor be useful too?
    //  Yes, if it needs relation mapping too!
    public final void addRefMapping(
            @NotNull QName itemName, @NotNull QObjectReferenceMapping qReferenceMapping) {
        ((QueryModelMapping<?, ?, ?>) this).addItemMapping(itemName,
                ObjectRefTableItemFilterProcessor.mapper(qReferenceMapping));
        // TODO add relation mapping too
    }

    /** Returns the mapper creating the string filter/delta processors from context. */
    @Override
    protected SqaleItemSqlMapper stringMapper(
            Function<EntityPath<?>, StringPath> rootToQueryItem) {
        return new SqaleItemSqlMapper(
                ctx -> new SimpleItemFilterProcessor<>(ctx, rootToQueryItem),
                ctx -> new SimpleItemDeltaProcessor<>(ctx, rootToQueryItem),
                rootToQueryItem);
    }

    /** Returns the mapper creating the integer filter/delta processors from context. */
    @Override
    public SqaleItemSqlMapper integerMapper(
            Function<EntityPath<?>, NumberPath<Integer>> rootToQueryItem) {
        return new SqaleItemSqlMapper(
                ctx -> new SimpleItemFilterProcessor<>(ctx, rootToQueryItem),
                ctx -> new SimpleItemDeltaProcessor<>(ctx, rootToQueryItem),
                rootToQueryItem);
    }

    /** Returns the mapper creating the boolean filter/delta processors from context. */
    @Override
    protected SqaleItemSqlMapper booleanMapper(Function<EntityPath<?>, BooleanPath> rootToQueryItem) {
        return new SqaleItemSqlMapper(
                ctx -> new SimpleItemFilterProcessor<>(ctx, rootToQueryItem),
                ctx -> new SimpleItemDeltaProcessor<>(ctx, rootToQueryItem),
                rootToQueryItem);
    }

    /** Returns the mapper creating the UUID filter/delta processors from context. */
    @Override
    protected SqaleItemSqlMapper uuidMapper(Function<EntityPath<?>, UuidPath> rootToQueryItem) {
        return new SqaleItemSqlMapper(
                ctx -> new SimpleItemFilterProcessor<>(ctx, rootToQueryItem),
                ctx -> new SimpleItemDeltaProcessor<>(ctx, rootToQueryItem),
                rootToQueryItem);
    }

    /** Returns the mapper creating the timestamp filter/delta processors from context. */
    @Override
    protected <T extends Comparable<T>> SqaleItemSqlMapper timestampMapper(
            Function<EntityPath<?>, DateTimePath<T>> rootToQueryItem) {
        return new SqaleItemSqlMapper(
                ctx -> new TimestampItemFilterProcessor<>(ctx, rootToQueryItem),
                ctx -> new TimestampItemDeltaProcessor<>(ctx, rootToQueryItem),
                rootToQueryItem);
    }

    /** Returns the mapper creating the polystring filter/delta processors from context. */
    @Override
    protected SqaleItemSqlMapper polyStringMapper(
            @NotNull Function<EntityPath<?>, StringPath> origMapping,
            @NotNull Function<EntityPath<?>, StringPath> normMapping) {
        return new SqaleItemSqlMapper(
                ctx -> new PolyStringItemFilterProcessor(ctx, origMapping, normMapping),
                ctx -> new PolyStringItemDeltaProcessor(ctx, origMapping, normMapping),
                origMapping);
    }

    /** Returns the mapper creating the reference filter/delta processors from context. */
    protected SqaleItemSqlMapper refMapper(
            Function<EntityPath<?>, UuidPath> rootToOidPath,
            Function<EntityPath<?>, EnumPath<MObjectType>> rootToTypePath,
            Function<EntityPath<?>, NumberPath<Integer>> rootToRelationIdPath) {
        return new SqaleItemSqlMapper(
                ctx -> new RefItemFilterProcessor(ctx,
                        rootToOidPath, rootToTypePath, rootToRelationIdPath),
                ctx -> new RefItemDeltaProcessor(ctx,
                        rootToOidPath, rootToTypePath, rootToRelationIdPath));
    }

    /** Returns the mapper creating the cached URI filter/delta processors from context. */
    protected SqaleItemSqlMapper uriMapper(
            Function<EntityPath<?>, NumberPath<Integer>> rootToPath) {
        return new SqaleItemSqlMapper(
                ctx -> new UriItemFilterProcessor(ctx, rootToPath),
                ctx -> new UriItemDeltaProcessor(ctx, rootToPath));
    }

    /** Returns the mapper creating the enum filter/delta processors from context. */
    public <E extends Enum<E>> SqaleItemSqlMapper enumMapper(
            @NotNull Function<EntityPath<?>, EnumPath<E>> rootToQueryItem) {
        return new SqaleItemSqlMapper(
                ctx -> new EnumItemFilterProcessor<>(ctx, rootToQueryItem),
                ctx -> new EnumItemDeltaProcessor<>(ctx, rootToQueryItem),
                rootToQueryItem);
    }
}
