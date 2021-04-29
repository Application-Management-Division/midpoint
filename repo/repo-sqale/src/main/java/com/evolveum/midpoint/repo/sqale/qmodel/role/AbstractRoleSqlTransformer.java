/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.qmodel.role;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.repo.sqale.qmodel.assignment.AssignmentSqlTransformer;
import com.evolveum.midpoint.repo.sqale.qmodel.focus.FocusSqlTransformer;
import com.evolveum.midpoint.repo.sqlbase.JdbcSession;
import com.evolveum.midpoint.repo.sqlbase.SqlTransformerSupport;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AbstractRoleType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AutoassignSpecificationType;

public class AbstractRoleSqlTransformer<
        S extends AbstractRoleType, Q extends QAbstractRole<R>, R extends MAbstractRole>
        extends FocusSqlTransformer<S, Q, R> {

    public AbstractRoleSqlTransformer(
            SqlTransformerSupport transformerSupport, QAbstractRoleMapping<S, Q, R> mapping) {
        super(transformerSupport, mapping);
    }

    @Override
    public @NotNull R toRowObjectWithoutFullObject(S abstractRole, JdbcSession jdbcSession) {
        R row = super.toRowObjectWithoutFullObject(abstractRole, jdbcSession);

        AutoassignSpecificationType autoassign = abstractRole.getAutoassign();
        if (autoassign != null) {
            row.autoAssignEnabled = autoassign.isEnabled();
        }
        setPolyString(abstractRole.getDisplayName(),
                o -> row.displayNameOrig = o, n -> row.displayNameNorm = n);
        row.identifier = abstractRole.getIdentifier();
        row.requestable = abstractRole.isRequestable();
        row.riskLevel = abstractRole.getRiskLevel();
        return row;
    }

    @Override
    public void storeRelatedEntities(
            @NotNull R row, @NotNull S schemaObject, @NotNull JdbcSession jdbcSession) {
        super.storeRelatedEntities(row, schemaObject, jdbcSession);

        List<AssignmentType> inducement = schemaObject.getInducement();
        if (!inducement.isEmpty()) {
            AssignmentSqlTransformer<R> transformer =
                    mapping().inducementMapping().createTransformer(transformerSupport);
            inducement.forEach(assignment ->
                    transformer.insert(assignment, row, jdbcSession));
        }
    }

    @Override
    protected QAbstractRoleMapping<S, Q, R> mapping() {
        return (QAbstractRoleMapping<S, Q, R>) super.mapping();
    }
}
