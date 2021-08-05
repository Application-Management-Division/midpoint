/*
 * Copyright (C) 2010-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.model;

import org.apache.wicket.model.IModel;

import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerValueWrapper;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerWrapper;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.prism.path.ItemPath;

/**
 * @author katka
 */
public class PrismContainerWrapperModel<C extends Containerable, T extends Containerable>
        extends ItemWrapperModel<C, PrismContainerWrapper<T>> {

    private static final long serialVersionUID = 1L;

    private String identifier;

    PrismContainerWrapperModel(IModel<?> parent, ItemPath path, boolean fromContainerValue) {
        super(parent, path, fromContainerValue);
    }

    PrismContainerWrapperModel(IModel<?> parent, String identifier) {
        super(parent, null, false);
        this.identifier = identifier;
    }

    public static <C extends Containerable, T extends Containerable> PrismContainerWrapperModel<C, T> fromContainerWrapper(IModel<? extends PrismContainerWrapper<C>> parent, ItemPath path) {
        return new PrismContainerWrapperModel<>(parent, path, false);
    }

    public static <C extends Containerable, T extends Containerable> PrismContainerWrapperModel<C, T> fromContainerWrapper(IModel<? extends PrismContainerWrapper<C>> parent, String containerIdentfier) {
        return new PrismContainerWrapperModel<>(parent, containerIdentfier);
    }

    public static <C extends Containerable, T extends Containerable> PrismContainerWrapperModel<C, T> fromContainerWrapper(IModel<? extends PrismContainerWrapper<C>> parent, ItemName path) {
        return new PrismContainerWrapperModel<>(parent, ItemPath.create(path), false);
    }

    public static <C extends Containerable, T extends Containerable> PrismContainerWrapperModel<C, T> fromContainerValueWrapper(IModel<PrismContainerValueWrapper<C>> parent, ItemPath path) {
        return new PrismContainerWrapperModel<>(parent, path, true);
    }

    public static <C extends Containerable> PrismContainerWrapperModel<C, C> fromContainerValueWrapper(IModel<PrismContainerValueWrapper<C>> parent) {
        return new PrismContainerWrapperModel<>(parent, null, true);
    }

    public static <C extends Containerable, T extends Containerable> PrismContainerWrapperModel<C, T> fromContainerValueWrapper(IModel<PrismContainerValueWrapper<C>> parent, ItemName path) {
        return new PrismContainerWrapperModel<>(parent, ItemPath.create(path), true);
    }

    @Override
    public PrismContainerWrapper<T> getObject() {
        if (identifier != null) {
            PrismContainerWrapper<?> parentObject = (PrismContainerWrapper) getParent().getObject();
            return parentObject.findContainer(identifier);
        }
        return getItemWrapper(PrismContainerWrapper.class);
    }
}
