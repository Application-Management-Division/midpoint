/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale;

import javax.xml.namespace.QName;

import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.repo.sqale.qmodel.common.QUri;
import com.evolveum.midpoint.repo.sqlbase.JdbcSession;
import com.evolveum.midpoint.repo.sqlbase.SqlTransformerSupport;
import com.evolveum.midpoint.schema.SchemaService;
import com.evolveum.midpoint.util.QNameUtil;

/**
 * Extension of {@link SqlTransformerSupport} adding Sqale features like {@link UriCache} support.
 */
public class SqaleTransformerSupport extends SqlTransformerSupport {

    public SqaleTransformerSupport(SchemaService schemaService, SqaleRepoContext sqaleRepoContext) {
        super(schemaService, sqaleRepoContext);
    }

    private SqaleRepoContext sqaleRepoContext() {
        return (SqaleRepoContext) sqlRepoContext;
    }

    /**
     * Returns ID for relation QName or {@link UriCache#UNKNOWN_ID} without going to the database.
     * Relation is normalized before consulting {@link UriCache}.
     * Never returns null; returns default ID for configured default relation if provided with null.
     */
    public @NotNull Integer searchCachedRelationId(QName qName) {
        return sqaleRepoContext().searchCachedUriId(QNameUtil.qNameToUri(normalizeRelation(qName)));
    }

    /** Returns ID for cached URI without going to the database. */
    public Integer resolveUriToId(String uri) {
        return sqaleRepoContext().resolveUriToId(uri);
    }

    /** Returns ID for cached URI without going to the database. */
    public Integer resolveUriToId(QName uri) {
        return sqaleRepoContext().resolveUriToId(uri);
    }

    /**
     * Returns ID for relation QName creating new {@link QUri} row in DB as needed.
     * Relation is normalized before consulting the cache.
     * Never returns null, returns default ID for configured default relation.
     */
    public Integer processCacheableRelation(QName qName, JdbcSession jdbcSession) {
        return processCacheableUri(
                QNameUtil.qNameToUri(normalizeRelation(qName)),
                jdbcSession);
    }

    /** Returns ID for URI creating new cache row in DB as needed. */
    public Integer processCacheableUri(String uri, JdbcSession jdbcSession) {
        return sqaleRepoContext().processCacheableUri(uri, jdbcSession);
    }
}

