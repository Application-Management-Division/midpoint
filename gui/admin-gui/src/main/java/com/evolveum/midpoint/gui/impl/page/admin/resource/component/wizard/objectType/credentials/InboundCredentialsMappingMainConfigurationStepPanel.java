/*
 * Copyright (C) 2023 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.page.admin.resource.component.wizard.objectType.credentials;

import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerValueWrapper;
import com.evolveum.midpoint.gui.impl.page.admin.resource.ResourceDetailsModel;
import com.evolveum.midpoint.gui.impl.page.admin.resource.component.wizard.objectType.attributeMapping.InboundMappingMainConfigurationStepPanel;
import com.evolveum.midpoint.gui.impl.util.GuiDisplayNameUtil;
import com.evolveum.midpoint.web.application.PanelDisplay;
import com.evolveum.midpoint.web.application.PanelInstance;
import com.evolveum.midpoint.xml.ns._public.common.common_3.MappingType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OperationTypeType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;

import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;

/**
 * @author lskublik
 */
@PanelInstance(identifier = "rw-credentials-inbound-main",
        applicableForType = ResourceType.class,
        applicableForOperation = OperationTypeType.WIZARD,
        display = @PanelDisplay(label = "PageResource.wizard.step.credentials.inbound.main", icon = "fa fa-screwdriver-wrench"),
        expanded = true)
public class InboundCredentialsMappingMainConfigurationStepPanel
        extends InboundMappingMainConfigurationStepPanel {

    public static final String PANEL_TYPE = "rw-credentials-inbound-main";

    public InboundCredentialsMappingMainConfigurationStepPanel(ResourceDetailsModel model,
                                                    IModel<PrismContainerValueWrapper<MappingType>> newValueModel) {
        super(model, newValueModel);
    }

    protected String getPanelType() {
        return PANEL_TYPE;
    }

    @Override
    public IModel<String> getTitle() {
        return
                createStringResource("PageResource.wizard.step.credentials.inbound.main");
    }

    @Override
    protected IModel<?> getTextModel() {
        return createStringResource("PageResource.wizard.step.credentials.inbound.main.text");
    }

    @Override
    protected IModel<?> getSubTextModel() {
        return createStringResource(
                "PageResource.wizard.step.credentials.inbound.main.subText",
                GuiDisplayNameUtil.getDisplayName(getValueModel().getObject().getNewValue()));
    }

    @Override
    protected LoadableDetachableModel<String> createLabelModel() {
        return (LoadableDetachableModel<String>) getTextModel();
    }
}
