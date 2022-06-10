/*
 * Copyright (c) 2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.gui.impl.page.self.requestAccess;

import org.apache.wicket.ajax.AjaxEventBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.model.IModel;

import com.evolveum.midpoint.gui.api.component.BasePanel;

import java.io.Serializable;

/**
 * Created by Viliam Repan (lazyman).
 */
public class TilePanel<T extends Serializable> extends BasePanel<Tile<T>> {

    private static final long serialVersionUID = 1L;

    private static final String ID_ICON = "icon";
    private static final String ID_TITLE = "title";

    public TilePanel(String id, IModel<Tile<T>> model) {
        super(id, model);

        initLayout();
    }

    private void initLayout() {
        add(AttributeAppender.append("class", "tile-panel d-flex flex-column align-items-center bg-white rounded p-3"));
        add(AttributeAppender.append("class", () -> getModelObject().isSelected() ? "active" : null));
        setOutputMarkupId(true);

        WebMarkupContainer icon = new WebMarkupContainer(ID_ICON);
        icon.add(AttributeAppender.append("class", () -> getModelObject().getIcon()));
        add(icon);

        add(new Label(ID_TITLE, () -> {
            String title = getModelObject().getTitle();
            return title != null ? getString(title, null, title) : null;
        }));

        add(new AjaxEventBehavior("click") {

            @Override
            protected void onEvent(AjaxRequestTarget target) {
                TilePanel.this.onClick(target);
            }
        });
    }

    protected void onClick(AjaxRequestTarget target) {
        getModelObject().toggle();
        target.add(this);
    }
}
