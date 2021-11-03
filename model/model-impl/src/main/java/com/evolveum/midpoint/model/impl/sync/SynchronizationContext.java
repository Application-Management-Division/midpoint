/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.model.impl.sync;

import java.util.List;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.model.impl.ModelBeans;
import com.evolveum.midpoint.provisioning.api.ResourceObjectShadowChangeDescription;
import com.evolveum.midpoint.schema.util.ResourceTypeUtil;
import com.evolveum.midpoint.util.annotation.Experimental;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.evolveum.midpoint.common.refinery.RefinedObjectClassDefinition;
import com.evolveum.midpoint.common.refinery.RefinedResourceSchema;
import com.evolveum.midpoint.common.refinery.RefinedResourceSchemaImpl;
import com.evolveum.midpoint.model.common.expression.ExpressionEnvironment;
import com.evolveum.midpoint.model.common.expression.ModelExpressionThreadLocalHolder;
import com.evolveum.midpoint.model.impl.util.ModelImplUtils;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.repo.common.expression.ExpressionUtil;
import com.evolveum.midpoint.schema.expression.VariablesMap;
import com.evolveum.midpoint.schema.constants.ExpressionConstants;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.expression.ExpressionProfile;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.MiscSchemaUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.DebugDumpable;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

public class SynchronizationContext<F extends FocusType> implements DebugDumpable {

    private static final Trace LOGGER = TraceManager.getTrace(SynchronizationContext.class);

    @VisibleForTesting
    private static boolean skipMaintenanceCheck;

    /**
     * Normally, this is shadowed resource object, i.e. shadow + attributes (simply saying).
     * In the case of object deletion, the last known shadow can be used, i.e. without attributes.
     *
     * See {@link ResourceObjectShadowChangeDescription#getShadowedResourceObject()}.
     */
    @NotNull private final PrismObject<ShadowType> shadowedResourceObject;

    /**
     * Original delta that triggered this synchronization. (If known.)
     */
    private final ObjectDelta<ShadowType> resourceObjectDelta;

    private PrismObject<ResourceType> resource;
    private PrismObject<SystemConfigurationType> systemConfiguration;
    private String channel;
    private ExpressionProfile expressionProfile;

    private final Task task;

    private ObjectSynchronizationType objectSynchronization;
    private Class<F> focusClass;

    /** Owner that was found to be linked (in repo) to the shadow being synchronized. */
    private F linkedOwner;

    /** Owner that was found by synchronization sorter or correlation expression(s). */
    private F correlatedOwner;

    /** Situation determined by the sorter or the synchronization service. */
    private SynchronizationSituationType situation;

    private String intent;
    private String tag;

    private boolean reactionEvaluated = false;
    private SynchronizationReactionType reaction;

    private boolean shadowExistsInRepo = true;
    private boolean forceIntentChange;

    private final PrismContext prismContext;
    private final ModelBeans beans;

    /** TODO maybe will be removed */
    @Experimental
    private final String itemProcessingIdentifier;

    public SynchronizationContext(@NotNull PrismObject<ShadowType> shadowedResourceObject,
            ObjectDelta<ShadowType> resourceObjectDelta, PrismObject<ResourceType> resource, String channel,
            ModelBeans beans, Task task, String itemProcessingIdentifier) {
        this.shadowedResourceObject = shadowedResourceObject;
        this.resourceObjectDelta = resourceObjectDelta;
        this.resource = resource;
        this.channel = channel;
        this.task = task;
        this.prismContext = beans.prismContext;
        this.beans = beans;
        this.expressionProfile = MiscSchemaUtil.getExpressionProfile();
        this.itemProcessingIdentifier = itemProcessingIdentifier;
    }

    public boolean isSynchronizationEnabled() {
        return objectSynchronization != null && BooleanUtils.isNotFalse(objectSynchronization.isEnabled());
    }

    public boolean isProtected() {
        return BooleanUtils.isTrue(shadowedResourceObject.asObjectable().isProtectedObject());
    }

    public ShadowKindType getKind() {

        if (!hasApplicablePolicy()) {
            return ShadowKindType.UNKNOWN;
        }

        if (objectSynchronization.getKind() == null) {
            return ShadowKindType.ACCOUNT;
        }

        return objectSynchronization.getKind();
    }

    public String getIntent() throws SchemaException {
        if (!hasApplicablePolicy()) {
            return SchemaConstants.INTENT_UNKNOWN;
        }

        if (intent == null) {
            RefinedResourceSchema schema = RefinedResourceSchemaImpl.getRefinedSchema(resource);
            intent = schema.findDefaultObjectClassDefinition(getKind()).getIntent();
        }
        return intent;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public List<ConditionalSearchFilterType> getCorrelation() {
        return objectSynchronization.getCorrelation();
    }

    public ExpressionType getConfirmation() {
        return objectSynchronization.getConfirmation();
    }

    public ObjectReferenceType getObjectTemplateRef() {
        if (reaction.getObjectTemplateRef() != null) {
            return reaction.getObjectTemplateRef();
        }

        return objectSynchronization.getObjectTemplateRef();
    }

    public SynchronizationReactionType getReaction(OperationResult result)
            throws ConfigurationException, SchemaException, ObjectNotFoundException, CommunicationException,
            SecurityViolationException, ExpressionEvaluationException {
        if (reactionEvaluated) {
            return reaction;
        }

        SynchronizationReactionType defaultReaction = null;
        for (SynchronizationReactionType reactionToConsider : objectSynchronization.getReaction()) {
            SynchronizationSituationType reactionSituation = reactionToConsider.getSituation();
            if (reactionSituation == null) {
                throw new ConfigurationException("No situation defined for a reaction in " + resource);
            }
            if (reactionSituation == situation) {
                List<String> channels = reactionToConsider.getChannel();
                // The second and third conditions are suspicious but let's keep them here for historical reasons.
                if (channels.isEmpty() || channels.contains("") || channels.contains(null)) {
                    if (conditionMatches(reactionToConsider, result)) {
                        defaultReaction = reactionToConsider;
                    }
                } else if (channels.contains(this.channel)) {
                    if (conditionMatches(reactionToConsider, result)) {
                        reaction = reactionToConsider;
                        reactionEvaluated = true;
                        return reaction;
                    }
                } else {
                    LOGGER.trace("Skipping reaction {} because the channel does not match {}", reaction, this.channel);
                }
            }
        }
        LOGGER.trace("Using default reaction {}", defaultReaction);
        reaction = defaultReaction;
        reactionEvaluated = true;
        return reaction;
    }

    private boolean conditionMatches(SynchronizationReactionType reaction, OperationResult result) throws SchemaException,
            ExpressionEvaluationException, ObjectNotFoundException, CommunicationException, ConfigurationException,
            SecurityViolationException {
        if (reaction.getCondition() != null) {
            ExpressionType expression = reaction.getCondition();
            String desc = "condition in synchronization reaction on " + reaction.getSituation()
                    + (reaction.getName() != null ? " (" + reaction.getName() + ")" : "");
            VariablesMap variables = ModelImplUtils.getDefaultVariablesMap(getFocus(), shadowedResourceObject, null,
                    resource, systemConfiguration, null, prismContext);
            variables.put(ExpressionConstants.VAR_RESOURCE_OBJECT_DELTA, resourceObjectDelta, ObjectDelta.class);
            try {
                ModelExpressionThreadLocalHolder.pushExpressionEnvironment(new ExpressionEnvironment<>(task, result));
                boolean value = ExpressionUtil.evaluateConditionDefaultFalse(variables, expression,
                        expressionProfile, beans.expressionFactory, desc, task, result);
                if (!value) {
                    LOGGER.trace("Skipping reaction {} because the condition was evaluated to false", reaction);
                }
                return value;
            } finally {
                ModelExpressionThreadLocalHolder.popExpressionEnvironment();
            }
        } else {
            return true;
        }
    }

    @Nullable
    private PrismObject<? extends ObjectType> getFocus() {
        PrismObject<? extends ObjectType> focus;
        if (linkedOwner != null) {
            focus = linkedOwner.asPrismObject();
        } else if (correlatedOwner != null) {
            focus = correlatedOwner.asPrismObject();
        } else {
            focus = null;
        }
        return focus;
    }

    public boolean hasApplicablePolicy() {
        return objectSynchronization != null;
    }

    public String getPolicyName() {
        if (objectSynchronization == null) {
            return null;
        }
        if (objectSynchronization.getName() != null) {
            return objectSynchronization.getName();
        }
        return objectSynchronization.toString();
    }

    public Boolean isDoReconciliation() {
        if (reaction.isReconcile() != null) {
            return reaction.isReconcile();
        }
        if (objectSynchronization.isReconcile() != null) {
            return objectSynchronization.isReconcile();
        }
        return null;
    }

    public ModelExecuteOptionsType getExecuteOptions() {
        return reaction.getExecuteOptions();
    }

    public Boolean isLimitPropagation() {
        if (StringUtils.isNotBlank(channel)) {
            QName channelQName = QNameUtil.uriToQName(channel);
            // Discovery channel is used when compensating some inconsistent
            // state. Therefore we do not want to propagate changes to other
            // resources. We only want to resolve the problem and continue in
            // previous provisioning/synchronization during which this
            // compensation was triggered.
            if (SchemaConstants.CHANNEL_DISCOVERY.equals(channelQName)
                    && SynchronizationSituationType.DELETED != reaction.getSituation()) {
                return true;
            }
        }

        if (reaction.isLimitPropagation() != null) {
            return reaction.isLimitPropagation();
        }
        if (objectSynchronization.isLimitPropagation() != null) {
            return objectSynchronization.isLimitPropagation();
        }
        return null;
    }

    public @NotNull PrismObject<ShadowType> getShadowedResourceObject() {
        return shadowedResourceObject;
    }

    public PrismObject<ResourceType> getResource() {
        return resource;
    }

    public Class<F> getFocusClass() throws SchemaException {

        if (focusClass != null) {
            return focusClass;
        }

        if (!hasApplicablePolicy()) {
            throw new IllegalStateException("synchronizationPolicy is null");
        }

        QName focusTypeQName = objectSynchronization.getFocusType();
        if (focusTypeQName == null) {
            //noinspection unchecked
            this.focusClass = (Class<F>) UserType.class;
            return focusClass;
        }
        ObjectTypes objectType = ObjectTypes.getObjectTypeFromTypeQName(focusTypeQName);
        if (objectType == null) {
            throw new SchemaException("Unknown focus type " + focusTypeQName + " in synchronization policy in " + resource);
        }
        this.focusClass = objectType.getClassDefinition();
        return focusClass;
    }

    public F getLinkedOwner() {
        return linkedOwner;
    }

    public F getCorrelatedOwner() {
        return correlatedOwner;
    }

    public PrismContext getPrismContext() {
        return prismContext;
    }

    public SynchronizationSituationType getSituation() {
        return situation;
    }

    public void setObjectSynchronization(ObjectSynchronizationType objectSynchronization) {
        this.intent = objectSynchronization.getIntent();
        this.objectSynchronization = objectSynchronization;
    }

    public void setFocusClass(Class<F> focusClass) {
        this.focusClass = focusClass;
    }

    public void setLinkedOwner(F owner) {
        this.linkedOwner = owner;
    }

    public void setCorrelatedOwner(F correlatedFocus) {
        this.correlatedOwner = correlatedFocus;
    }

    public void setSituation(SynchronizationSituationType situation) {
        this.situation = situation;
    }

    public PrismObject<SystemConfigurationType> getSystemConfiguration() {
        return systemConfiguration;
    }

    public String getChannel() {
        return channel;
    }

    public void setResource(PrismObject<ResourceType> resource) {
        this.resource = resource;
    }

    public void setSystemConfiguration(PrismObject<SystemConfigurationType> systemConfiguration) {
        this.systemConfiguration = systemConfiguration;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public ExpressionProfile getExpressionProfile() {
        return expressionProfile;
    }

    public void setExpressionProfile(ExpressionProfile expressionProfile) {
        this.expressionProfile = expressionProfile;
    }

    public void setReaction(SynchronizationReactionType reaction) {
        this.reaction = reaction;
    }

    public Task getTask() {
        return task;
    }

    boolean isShadowExistsInRepo() {
        return shadowExistsInRepo;
    }

    void setShadowExistsInRepo(boolean shadowExistsInRepo) {
        this.shadowExistsInRepo = shadowExistsInRepo;
    }

    public boolean isForceIntentChange() {
        return forceIntentChange;
    }

    public void setForceIntentChange(boolean forceIntentChange) {
        this.forceIntentChange = forceIntentChange;
    }

    public String getItemProcessingIdentifier() {
        return itemProcessingIdentifier;
    }

    RefinedObjectClassDefinition findRefinedObjectClassDefinition() throws SchemaException {
        RefinedResourceSchema refinedResourceSchema = RefinedResourceSchema.getRefinedSchema(resource);
        return refinedResourceSchema.getRefinedDefinition(getKind(), getIntent());
    }

    @Override
    public String toString() {
        String policyDesc = null;
        if (objectSynchronization != null) {
            if (objectSynchronization.getName() == null) {
                policyDesc = "(kind=" + objectSynchronization.getKind() + ", intent="
                        + objectSynchronization.getIntent() + ", objectclass="
                        + objectSynchronization.getObjectClass() + ")";
            } else {
                policyDesc = objectSynchronization.getName();
            }
        }

        return policyDesc;
    }

    @Override
    public String debugDump(int indent) {
        StringBuilder sb = DebugUtil.createTitleStringBuilderLn(SynchronizationContext.class, indent);
        DebugUtil.debugDumpWithLabelLn(sb, "shadowedResourceObject", shadowedResourceObject, indent + 1);
        DebugUtil.debugDumpWithLabelToStringLn(sb, "resource", resource, indent + 1);
        DebugUtil.debugDumpWithLabelToStringLn(sb, "systemConfiguration", systemConfiguration, indent + 1);
        DebugUtil.debugDumpWithLabelToStringLn(sb, "channel", channel, indent + 1);
        DebugUtil.debugDumpWithLabelToStringLn(sb, "expressionProfile", expressionProfile, indent + 1);
        DebugUtil.debugDumpWithLabelToStringLn(sb, "objectSynchronization", objectSynchronization, indent + 1);
        DebugUtil.debugDumpWithLabelLn(sb, "focusClass", focusClass, indent + 1);
        DebugUtil.debugDumpWithLabelToStringLn(sb, "currentOwner", linkedOwner, indent + 1);
        DebugUtil.debugDumpWithLabelToStringLn(sb, "correlatedOwner", correlatedOwner, indent + 1);
        DebugUtil.debugDumpWithLabelToStringLn(sb, "situation", situation, indent + 1);
        DebugUtil.debugDumpWithLabelToStringLn(sb, "intent", intent, indent + 1);
        DebugUtil.debugDumpWithLabelToStringLn(sb, "tag", tag, indent + 1);
        DebugUtil.debugDumpWithLabelToStringLn(sb, "reaction", reaction, indent + 1);
        DebugUtil.debugDumpWithLabelLn(sb, "shadowExistsInRepo", shadowExistsInRepo, indent + 1);
        DebugUtil.debugDumpWithLabel(sb, "forceIntentChange", forceIntentChange, indent + 1);
        return sb.toString();
    }

    /**
     * Checks whether the source resource is not in maintenance mode.
     * (Throws an exception if it is.)
     *
     * Side-effect: updates the resource prism object (if it was changed).
     */
    void checkNotInMaintenance(OperationResult result)
            throws SchemaException, ExpressionEvaluationException, CommunicationException, SecurityViolationException,
            ConfigurationException, ObjectNotFoundException {
        if (!skipMaintenanceCheck) {
            resource = beans.provisioningService.getObject(ResourceType.class, resource.getOid(), null, task, result);
            ResourceTypeUtil.checkNotInMaintenance(resource.asObjectable());
        }
    }

    @VisibleForTesting
    public static void setSkipMaintenanceCheck(boolean skipMaintenanceCheck) {
        SynchronizationContext.skipMaintenanceCheck = skipMaintenanceCheck;
    }
}
