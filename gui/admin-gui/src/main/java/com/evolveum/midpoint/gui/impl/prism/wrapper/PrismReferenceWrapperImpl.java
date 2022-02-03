/*
 * Copyright (c) 2010-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.prism.wrapper;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.gui.impl.component.search.AbstractSearchItemWrapper;

import com.evolveum.midpoint.web.component.util.SerializableSupplier;

import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.gui.api.prism.ItemStatus;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerValueWrapper;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismReferenceWrapper;
import com.evolveum.midpoint.gui.api.util.ModelServiceLocator;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.query.ObjectFilter;

/**
 * @author katka
 */
public class PrismReferenceWrapperImpl<R extends Referencable>
        extends ItemWrapperImpl<PrismReference, PrismReferenceValueWrapperImpl<R>>
        implements PrismReferenceWrapper<R> {

    private ObjectFilter filter;
    private Set<SerializableSupplier<AbstractSearchItemWrapper>> specialItemFunctions = Collections.emptySet();
    private boolean onlyForDeltaComputation;

    public PrismReferenceWrapperImpl(PrismContainerValueWrapper<?> parent, PrismReference item, ItemStatus status) {
        super(parent, item, status);
    }

    private static final long serialVersionUID = 1L;

    @Override
    public QName getTargetTypeName() {
        return getItemDefinition().getTargetTypeName();
    }

    @Override
    public QName getCompositeObjectElementName() {
        return getItemDefinition().getCompositeObjectElementName();
    }

    @Override
    public boolean isComposite() {
        return getItemDefinition().isComposite();
    }

    @Override
    public @NotNull PrismReferenceDefinition clone() {
        return getItemDefinition().clone();
    }

    @NotNull
    @Override
    public PrismReference instantiate() {
        return getItemDefinition().instantiate();
    }

    @NotNull
    @Override
    public PrismReference instantiate(QName name) {
        return getItemDefinition().instantiate(name);
    }

    @Override
    public ObjectFilter getFilter() {
        return filter;
    }

    @Override
    public void setFilter(ObjectFilter filter) {
        this.filter = filter;
    }

    @Override
    public List<QName> getTargetTypes() {
        return WebComponentUtil.createSupportedTargetTypeList(getTargetTypeName());
    }

    @Override
    public Set<SerializableSupplier<AbstractSearchItemWrapper>> getSpecialSearchItemFunctions() {
        return specialItemFunctions;
    }

    @Override
    public void setSpecialSearchItemFunctions(Set<SerializableSupplier<AbstractSearchItemWrapper>> functions) {
        this.specialItemFunctions = functions;
    }

    @Override
    public boolean isEmpty() {
        if (super.isEmpty()) { return true; }

        List<PrismReferenceValue> pVals = getItem().getValues();
        boolean allEmpty = true;
        for (PrismReferenceValue pVal : pVals) {
            if (!pVal.isEmpty()) {
                allEmpty = false;
                break;
            }
        }

        return allEmpty;
    }

    @Override
    PrismReferenceDefinition getItemDefinition() {
        return super.getItemDefinition();
    }

    @Override
    public boolean isImmutable() {
        // TODO
        return false;
    }

    @Override
    public void freeze() {
        // TODO
    }

    @Override
    public boolean accept(Visitor<Definition> visitor, SmartVisitation<Definition> visitation) {
        // TODO
        return false;
    }

    @Override
    public void accept(Visitor<Definition> visitor) {
        // TODO
    }

    @Override
    protected PrismReferenceValue createNewEmptyValue(ModelServiceLocator locator) {
        return locator.getPrismContext().itemFactory().createReferenceValue();
    }

    @Override
    public Optional<ComplexTypeDefinition> structuredType() {
        return getItemDefinition().structuredType();
    }

    public void setOnlyForDeltaComputation(boolean onlyForDeltaComputation) {
        this.onlyForDeltaComputation = onlyForDeltaComputation;
    }

    public boolean isOnlyForDeltaComputation() {
        return onlyForDeltaComputation;
    }
}
