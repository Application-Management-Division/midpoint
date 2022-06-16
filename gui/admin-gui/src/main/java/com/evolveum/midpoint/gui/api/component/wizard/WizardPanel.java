/*
 * Copyright (c) 2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.gui.api.component.wizard;

import java.util.stream.Collectors;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.IModel;

import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.web.component.util.VisibleBehaviour;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;

import org.jetbrains.annotations.NotNull;

/**
 * Created by Viliam Repan (lazyman).
 */
public class WizardPanel extends BasePanel {

    private static final long serialVersionUID = 1L;

    private static final String ID_HEADER = "header";
    private static final String ID_STEPS = "steps";
    private static final String ID_STEP = "step";
    private static final String ID_LINE = "line";
    private static final String ID_CONTENT_HEADER = "contentHeader";
    public static final String ID_CONTENT_BODY = "contentBody";

    private WizardModel wizardModel;

    public WizardPanel(String id, WizardModel wizardModel) {
        super(id);

        this.wizardModel = wizardModel;
        this.wizardModel.setPanel(this);

        initLayout();

        this.wizardModel.init();
    }

    public WizardModel getWizardModel() {
        return wizardModel;
    }

    @Override
    protected void onComponentTag(ComponentTag tag) {
        checkComponentTag(tag, "div");

        super.onComponentTag(tag);
    }

    @Override
    protected void onBeforeRender() {
        super.onBeforeRender();

        addOrReplace((Component) getCurrentPanel());
    }

    private void initLayout() {
        add(AttributeAppender.prepend("class", "bs-stepper"));
        add(AttributeAppender.append("class", () -> getCurrentPanel().appendCssToWizard()));

        WebMarkupContainer header = new WebMarkupContainer(ID_HEADER);
        header.setOutputMarkupId(true);
        add(header);

        ListView<IModel<String>> steps = new ListView<>(ID_STEPS, () -> wizardModel.getSteps().stream().map(s -> s.getTitle()).collect(Collectors.toList())) {

            @Override
            protected void populateItem(ListItem<IModel<String>> listItem) {
                WizardHeaderStepPanel step = new WizardHeaderStepPanel(ID_STEP, listItem.getIndex(), listItem.getModelObject());
                step.add(AttributeAppender.append("class", () -> wizardModel.getActiveStepIndex() == listItem.getIndex() ? "active" : null));
                listItem.add(step);

                WebMarkupContainer line = new WebMarkupContainer(ID_LINE);
                // hide last "line"
                line.add(new VisibleBehaviour(() -> listItem.getIndex() < wizardModel.getSteps().size() - 1));
                listItem.add(line);
            }
        };
        header.add(steps);

        IModel<String> currentPanelTitle = () -> getCurrentPanel().getTitle().getObject();
        IModel<String> nextPanelTitle = () -> {
            WizardStep next = wizardModel.getNextPanel();
            return next != null ? next.getTitle().getObject() : null;
        };
        WizardHeader contentHeader = new WizardHeader(ID_CONTENT_HEADER, currentPanelTitle, nextPanelTitle) {

            @Override
            protected Component createHeaderContent(String id) {
                return getCurrentPanel().createHeaderContent(id);
            }

            @Override
            protected void onBackPerformed(AjaxRequestTarget target) {
                if (wizardModel.getActiveStepIndex() == 0) {
                    getPageBase().redirectBack();
                } else {
                    wizardModel.previous();
                }

                target.add(WizardPanel.this);
            }

            @Override
            protected void onNextPerformed(AjaxRequestTarget target) {
                wizardModel.next();

                target.add(WizardPanel.this);
            }

            @Override
            protected @NotNull VisibleEnableBehaviour getNextVisibilityBehaviour() {
                return wizardModel.getActiveStep().getNextBehaviour();
            }
        };
        contentHeader.add(new VisibleEnableBehaviour() {

            @Override
            public boolean isVisible() {
                VisibleEnableBehaviour b = getCurrentPanel().getHeaderBehaviour();
                return b != null ? b.isVisible() : true;
            }

            @Override
            public boolean isEnabled() {
                VisibleEnableBehaviour b = getCurrentPanel().getHeaderBehaviour();
                return b != null ? b.isEnabled() : true;
            }
        });
        contentHeader.setOutputMarkupId(true);
        add(contentHeader);

        addOrReplace(new WebMarkupContainer(ID_CONTENT_BODY));
    }

    public WizardStep getCurrentPanel() {
        return wizardModel.getActiveStep();
    }

    public Component getHeader() {
        return get(ID_CONTENT_HEADER);
    }
}
