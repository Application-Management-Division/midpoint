/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.qmodel.report;

import com.evolveum.midpoint.repo.sqale.qmodel.object.ObjectSqlTransformer;
import com.evolveum.midpoint.repo.sqale.qmodel.object.QObjectMapping;
import com.evolveum.midpoint.repo.sqlbase.SqlTransformerSupport;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ReportType;

/**
 * Mapping between {@link QReport} and {@link ReportType}.
 */
public class QReportMapping
        extends QObjectMapping<ReportType, QReport, MReport> {

    public static final String DEFAULT_ALIAS_NAME = "rep";

    public static final QReportMapping INSTANCE = new QReportMapping();

    private QReportMapping() {
        super(QReport.TABLE_NAME, DEFAULT_ALIAS_NAME,
                ReportType.class, QReport.class);

        // TODO mapping, items are from ReportType or from JasperReportEngineConfigurationType?
    }

    @Override
    protected QReport newAliasInstance(String alias) {
        return new QReport(alias);
    }

    @Override
    public ObjectSqlTransformer<ReportType, QReport, MReport>
    createTransformer(SqlTransformerSupport transformerSupport) {
        // no special class needed, no additional columns
        return new ObjectSqlTransformer<>(transformerSupport, this);
    }

    @Override
    public MReport newRowObject() {
        return new MReport();
    }
}
