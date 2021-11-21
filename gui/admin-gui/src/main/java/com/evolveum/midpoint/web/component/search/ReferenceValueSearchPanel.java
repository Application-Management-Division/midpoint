/*
 * Copyright (C) 2010-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.component.search;

import java.util.Collections;
import java.util.List;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.model.IModel;

import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.prism.PrismReferenceDefinition;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AreaCategoryType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;

/**
 * @author honchar
 */
public class ReferenceValueSearchPanel extends PopoverSearchPanel<ObjectReferenceType> {

    private static final long serialVersionUID = 1L;

    private final PrismReferenceDefinition referenceDef;

    public ReferenceValueSearchPanel(String id, IModel<ObjectReferenceType> model, PrismReferenceDefinition referenceDef) {
        super(id, model);
        this.referenceDef = referenceDef;
    }

    @Override
    protected PopoverSearchPopupPanel createPopupPopoverPanel(String id) {
        return new ReferenceValueSearchPopupPanel(id, ReferenceValueSearchPanel.this.getModel()) {

            private static final long serialVersionUID1 = 1L;

            @Override
            protected List<QName> getAllowedRelations() {
                return ReferenceValueSearchPanel.this.getAllowedRelations();
            }

            @Override
            protected List<QName> getSupportedTargetList() {
                if (referenceDef != null) {
                    return WebComponentUtil.createSupportedTargetTypeList(referenceDef.getTargetTypeName());
                }
                return Collections.singletonList(ObjectType.COMPLEX_TYPE);
            }

            @Override
            protected void confirmPerformed(AjaxRequestTarget target) {
                target.add(ReferenceValueSearchPanel.this);
                referenceValueUpdated(ReferenceValueSearchPanel.this.getModelObject(), target);
            }

            @Override
            protected Boolean isItemPanelEnabled() {
                return ReferenceValueSearchPanel.this.isItemPanelEnabled();
            }

            @Override
            protected boolean isAllowedNotFoundObjectRef() {
                return ReferenceValueSearchPanel.this.isAllowedNotFoundObjectRef();
            }
        };
    }

    @Override
    protected LoadableModel<String> getTextValue() {
        return new LoadableModel<String>() {
            @Override
            protected String load() {
                return WebComponentUtil.getReferenceObjectTextValue(getModelObject(), referenceDef, getPageBase());
            }
        };
    }

    protected void referenceValueUpdated(ObjectReferenceType ort, AjaxRequestTarget target) {
    }

    protected List<QName> getAllowedRelations() {
        return WebComponentUtil.getCategoryRelationChoices(AreaCategoryType.ADMINISTRATION, getPageBase());
    }

    protected boolean isAllowedNotFoundObjectRef(){
        return false;
    }
}
