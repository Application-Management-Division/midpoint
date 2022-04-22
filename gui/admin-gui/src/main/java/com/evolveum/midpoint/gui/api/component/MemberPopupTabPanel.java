/*
 * Copyright (C) 2010-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.api.component;

import java.util.ArrayList;
import java.util.List;
import javax.xml.namespace.QName;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.panel.Fragment;
import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.prism.PrismConstants;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.input.RelationDropDownChoicePanel;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

/**
 * Created by honchar
 */
public abstract class MemberPopupTabPanel<O extends ObjectType> extends AbstractPopupTabPanel<O> {
    private static final long serialVersionUID = 1L;

    private static final Trace LOGGER = TraceManager.getTrace(MemberPopupTabPanel.class);

    private static final String ID_RELATION_CONTAINER = "relationContainer";
    private static final String ID_RELATION = "relation";

    private PageBase pageBase;

    private final RelationSearchItemConfigurationType relationConfig;
    private final List<ObjectReferenceType> archetypeReferenceList;

    public MemberPopupTabPanel(String id, RelationSearchItemConfigurationType supportedRelationList) {
        this(id, supportedRelationList, new ArrayList<>());
    }

    public MemberPopupTabPanel(String id, RelationSearchItemConfigurationType relationConfig,
            List<ObjectReferenceType> archetypeReferenceList) {
        super(id);
        this.relationConfig = relationConfig;
        this.archetypeReferenceList = archetypeReferenceList;
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        pageBase = getPageBase();
    }

    @Override
    protected void initParametersPanel(Fragment parametersPanel) {
        WebMarkupContainer relationContainer = new WebMarkupContainer(ID_RELATION_CONTAINER);
        relationContainer.setOutputMarkupId(true);
        relationContainer.add(new VisibleEnableBehaviour() {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isVisible() {
                return CollectionUtils.isNotEmpty(relationConfig.getSupportedRelations());
            }

            @Override
            public boolean isEnabled() {
                return CollectionUtils.isNotEmpty(relationConfig.getSupportedRelations())
                        && relationConfig.getSupportedRelations().size() > 1;
            }
        });
        parametersPanel.add(relationContainer);

        relationContainer.add(new RelationDropDownChoicePanel(
                ID_RELATION, getDefaultRelation(), relationConfig.getSupportedRelations(), false));
    }

    private QName getDefaultRelation() {
        QName relation = relationConfig.getDefaultValue();
        if (QNameUtil.match(relation, PrismConstants.Q_ANY)) {
            QName defRelation = WebComponentUtil.getDefaultRelation();
            if (relationConfig.getSupportedRelations().contains(defRelation)) {
                relation = defRelation;
            } else {
                relation = relationConfig.getSupportedRelations().iterator().next();
            }
        }
        return relation;
    }

    protected ObjectDelta prepareDelta() {
        ObjectDelta delta = null;
        try {
            Class classType = WebComponentUtil.qnameToClass(pageBase.getPrismContext(), getObjectType().getTypeQName());
            delta = pageBase.getPrismContext().deltaFactory()
                    .object().createEmptyModifyDelta(classType, "fakeOid");
            AssignmentType newAssignment = new AssignmentType();
            ObjectReferenceType ref = ObjectTypeUtil.createObjectRef(getAbstractRoleTypeObject(), getRelationValue());
            newAssignment.setTargetRef(ref);

            pageBase.getPrismContext().adopt(newAssignment);
            delta.addModificationAddContainer(FocusType.F_ASSIGNMENT, newAssignment);

        } catch (SchemaException e) {
            LoggingUtils.logUnexpectedException(LOGGER, "Failed to prepare delta for adding a member operation ", e);
        }

        return delta;
    }

    protected abstract AbstractRoleType getAbstractRoleTypeObject();

    @Override
    protected List<ObjectReferenceType> getArchetypeRefList() {
        return archetypeReferenceList;
    }

    public @NotNull QName getRelationValue() {
        return getRelationDropDown()
                .getRelationValue();
    }

    private RelationDropDownChoicePanel getRelationDropDown() {
        return (RelationDropDownChoicePanel) get(ID_PARAMETERS_PANEL).get(ID_RELATION_CONTAINER).get(ID_RELATION);
    }
}
