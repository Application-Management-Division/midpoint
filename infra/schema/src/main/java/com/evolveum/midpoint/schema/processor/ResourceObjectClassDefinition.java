/*
 * Copyright (c) 2010-2017 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.schema.processor;

import com.evolveum.midpoint.prism.DeepCloneOperation;

import com.evolveum.midpoint.prism.path.ItemName;

import com.evolveum.midpoint.util.exception.ConfigurationException;

import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.util.ObjectQueryUtil;
import com.evolveum.midpoint.util.exception.SchemaException;

/**
 * Describes a resource object class (e.g. ri:inetOrgPerson).
 *
 * It is basically a collection of attribute definitions, with a couple of other properties.
 */
public interface ResourceObjectClassDefinition
        extends
            ResourceObjectDefinition {

    /**
     * Returns the native object class name.
     *
     * Native object class is the name of the object class as it is seen by the resource itself.
     * The name of the object class used in midPoint may be constrained by XSD or other syntax and therefore
     * may be "mangled" to conform to such syntax. The _native object class_ value will contain original,
     * un-mangled name (if available).
     *
     * Returns null if there is no native object class.
     *
     * The exception should be never thrown unless there is some bug in the code. The validation of model
     * consistency should be done at the time of schema parsing.
     *
     * @return native object class
     */
    String getNativeObjectClass();

    /**
     * TODO
     */
    boolean isAuxiliary();

    /**
     * Indicates whether definition is the default account definition.
     * (This feature is present for "dumb" resource definition that are completely without `schemaHandling` part.)
     *
     * This is a way how a resource connector may suggest applicable object classes.
     *
     * Currently the only use of this flag is that ConnId `pass:[__ACCOUNT__]` is declared
     * as a default for the kind of `ACCOUNT`.
     *
     * Originally, this property was called `defaultInAKind` and marked the object class as being default
     * for given kind. At that time, the kind was part of object class definition. This is no longer the case,
     * therefore also this property is renamed - and is available only for account-like object classes.
     * In the future we may put those things (kind + default-in-a-kind) back, if needed.
     */
    boolean isDefaultAccountDefinition();

    /**
     * Returns `true` if this definition is a part of the raw schema.
     *
     * Note that if it's part of refined schema (but not refined in any way) the return value is `true`.
     * See also {@link #hasRefinements()}.
     */
    boolean isRaw();

    /**
     * Returns `true` if there are really any refinements (i.e. a refinement bean exists, even if it can be effectively empty,
     * besides obligatory object class name reference).
     */
    boolean hasRefinements();

    /**
     * Creates a query for obtaining shadows related to this object class.
     */
    default @NotNull ObjectQuery createShadowSearchQuery(String resourceOid) throws SchemaException {
        return ObjectQueryUtil.createResourceAndObjectClassQuery(resourceOid, getTypeName());
    }

    ResourceAttributeContainer instantiate(ItemName elementName);

    @NotNull
    ResourceObjectClassDefinition clone();

    @NotNull
    @Override
    ResourceObjectClassDefinition deepClone(@NotNull DeepCloneOperation operation);

    MutableResourceObjectClassDefinition toMutable();

    /** Returns a definition with specified {@link BasicResourceInformation}; throws an exception in case of conflicts. */
    @NotNull ResourceObjectClassDefinition attachTo(@NotNull BasicResourceInformation resourceInformation)
            throws SchemaException, ConfigurationException;
}
