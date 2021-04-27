/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.qmodel.lookuptable;

import static com.evolveum.midpoint.xml.ns._public.common.common_3.LookupTableRowType.*;

import com.evolveum.midpoint.repo.sqale.qmodel.common.QContainerMapping;
import com.evolveum.midpoint.repo.sqlbase.SqlTransformerSupport;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LookupTableRowType;

/**
 * Mapping between {@link QLookupTableRow} and {@link LookupTableRowType}.
 */
public class QLookupTableRowMapping
        extends QContainerMapping<LookupTableRowType, QLookupTableRow, MLookupTableRow, MLookupTable> {

    public static final String DEFAULT_ALIAS_NAME = "ltr";

    public static final QLookupTableRowMapping INSTANCE = new QLookupTableRowMapping();

    private QLookupTableRowMapping() {
        super(QLookupTableRow.TABLE_NAME, DEFAULT_ALIAS_NAME,
                LookupTableRowType.class, QLookupTableRow.class);

        addItemMapping(F_KEY, stringMapper(q -> q.key));
        addItemMapping(F_LABEL, polyStringMapper(
                q -> q.labelOrig, q -> q.labelNorm));
        addItemMapping(F_VALUE, stringMapper(q -> q.value));
        addItemMapping(F_LAST_CHANGE_TIMESTAMP,
                timestampMapper(q -> q.lastChangeTimestamp));
    }

    @Override
    protected QLookupTableRow newAliasInstance(String alias) {
        return new QLookupTableRow(alias);
    }

    @Override
    public LookupTableRowSqlTransformer createTransformer(SqlTransformerSupport transformerSupport) {
        return new LookupTableRowSqlTransformer(transformerSupport, this);
    }

    @Override
    public MLookupTableRow newRowObject() {
        return new MLookupTableRow();
    }

    @Override
    public MLookupTableRow newRowObject(MLookupTable ownerRow) {
        MLookupTableRow row = newRowObject();
        row.ownerOid = ownerRow.oid;
        return row;
    }
}
