/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sql.audit.mapping;

import static com.evolveum.midpoint.repo.sql.audit.querymodel.QAuditItem.TABLE_NAME;

import com.evolveum.midpoint.repo.sql.audit.beans.MAuditDelta;
import com.evolveum.midpoint.repo.sql.audit.querymodel.QAuditDelta;
import com.evolveum.midpoint.repo.sqlbase.SqlTransformerSupport;
import com.evolveum.midpoint.repo.sqlbase.mapping.QueryTableMapping;
import com.evolveum.midpoint.repo.sqlbase.mapping.SqlTransformer;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectDeltaOperationType;

/**
 * Mapping between {@link QAuditDelta} and {@link ObjectDeltaOperationType}.
 */
public class QAuditDeltaMapping
        extends QueryTableMapping<ObjectDeltaOperationType, QAuditDelta, MAuditDelta> {

    public static final String DEFAULT_ALIAS_NAME = "ad";

    public static final QAuditDeltaMapping INSTANCE = new QAuditDeltaMapping();

    private QAuditDeltaMapping() {
        super(TABLE_NAME, DEFAULT_ALIAS_NAME,
                ObjectDeltaOperationType.class, QAuditDelta.class);
    }

    @Override
    protected QAuditDelta newAliasInstance(String alias) {
        return new QAuditDelta(alias);
    }

    @Override
    public SqlTransformer<ObjectDeltaOperationType, QAuditDelta, MAuditDelta> createTransformer(
            SqlTransformerSupport transformerContext) {
        return new AuditDeltaSqlTransformer(transformerContext, this);
    }
}
