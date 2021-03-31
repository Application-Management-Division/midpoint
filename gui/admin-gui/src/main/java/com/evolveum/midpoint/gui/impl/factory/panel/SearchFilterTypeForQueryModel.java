/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.factory.panel;

import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.PrismQuerySerialization;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.prism.xml.ns._public.query_3.SearchFilterType;
import com.evolveum.prism.xml.ns._public.types_3.ObjectType;

import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.model.IModel;

public class SearchFilterTypeForQueryModel<O extends ObjectType> extends SearchFilterTypeModel {

    private static final Trace LOGGER = TraceManager.getTrace(SearchFilterTypeForQueryModel.class);

    private static final long serialVersionUID = 1L;

    private IModel<Class<O>> filterTypeModel;
    private final boolean useParsing;

    public SearchFilterTypeForQueryModel(IModel<SearchFilterType> valueWrapper, PageBase pageBase,
            IModel<Class<O>> filterTypeModel) {
        this(valueWrapper, pageBase, filterTypeModel, true);
    }

    public SearchFilterTypeForQueryModel(IModel<SearchFilterType> valueWrapper, PageBase pageBase,
            IModel<Class<O>> filterTypeModel, boolean useParsing) {
        super(valueWrapper, pageBase);
        this.filterTypeModel = filterTypeModel;
        this.useParsing = useParsing;
    }

    @Override
    public String getObject() {
        try {
            SearchFilterType value = getBaseModel().getObject();
            if (value == null) {
                return null;
            }
            if (useParsing) {
                ObjectFilter objectFilter = getPageBase().getQueryConverter().createObjectFilter(filterTypeModel.getObject(), value);
                PrismQuerySerialization serialization = getPageBase().getPrismContext().querySerializer().serialize(objectFilter);
                if (serialization != null) {
                    return serialization.filterText();
                }
            }
            return value.getText();
        } catch (Exception e) {
            LoggingUtils.logUnexpectedException(LOGGER, "Cannot serialize filter", e);
            getPageBase().getFeedbackMessages().error(getPageBase(),
                    "Cannot parse filter: " + e.getMessage() + ". For more details, please, see midpoint log");
        }
        return null;
    }

    @Override
    public void setObject(String object) {
        if (StringUtils.isBlank(object)) {
            return;
        }
        try {
            ObjectFilter objectFilter = getPageBase().getPrismContext().createQueryParser().parseQuery(filterTypeModel.getObject(), object);
            SearchFilterType filter = getPageBase().getQueryConverter().createSearchFilterType(objectFilter);
            filter.setText(object);
            getBaseModel().setObject(filter);
        } catch (Exception e) {
            LoggingUtils.logUnexpectedException(LOGGER, "Cannot parse filter", e);
            getPageBase().getFeedbackMessages().error(getPageBase(), "Cannot parse filter: " + e.getMessage() + ". For more details, please, see midpoint log");
        }
    }
}
