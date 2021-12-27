/*
 * Copyright (C) 2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.component.search.refactored;

import com.evolveum.midpoint.web.component.search.DateIntervalSearchPanel;
import org.apache.wicket.Component;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;

public class DateSearchItemPanel extends PropertySearchItemPanel<DateSearchItemWrapper> {

    private static final long serialVersionUID = 1L;

    public DateSearchItemPanel(String id, IModel<DateSearchItemWrapper> searchItemModel) {
        super(id, searchItemModel);
    }

    @Override
    protected Component initSearchItemField() {
        return new DateIntervalSearchPanel(ID_SEARCH_ITEM_FIELD,
                new PropertyModel(getModel(), DateSearchItemWrapper.F_FROM_DATE),
                new PropertyModel(getModel(), DateSearchItemWrapper.F_TO_DATE));
    }

}
