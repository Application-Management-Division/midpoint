/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.model.impl.tasks.simple;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.repo.common.task.SearchBasedActivityExecution;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AbstractActivityWorkStateType;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.common.Clock;
import com.evolveum.midpoint.model.api.ScriptingService;
import com.evolveum.midpoint.model.impl.controller.ModelController;
import com.evolveum.midpoint.model.impl.lens.Clockwork;
import com.evolveum.midpoint.model.impl.lens.ContextFactory;
import com.evolveum.midpoint.model.impl.sync.SynchronizationService;
import com.evolveum.midpoint.model.impl.sync.tasks.SyncTaskHelper;
import com.evolveum.midpoint.model.impl.tasks.ModelActivityHandler;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.provisioning.api.ProvisioningService;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.repo.common.activity.definition.WorkDefinition;
import com.evolveum.midpoint.repo.common.activity.definition.WorkDefinitionFactory;
import com.evolveum.midpoint.repo.common.activity.definition.WorkDefinitionFactory.WorkDefinitionSupplier;
import com.evolveum.midpoint.repo.common.activity.execution.AbstractActivityExecution;
import com.evolveum.midpoint.repo.common.activity.execution.ExecutionInstantiationContext;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.security.enforcer.api.SecurityEnforcer;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;

/**
 * Implementing class for simple model-level search-based activity handlers.
 *
 * It makes writing non-composite activities a little bit easier. Generally the implementation should contain
 * an implementation of {@link WorkDefinition}, a subclass of {@link SearchBasedActivityExecution}, and
 * a configuration code like {@link #getWorkDefinitionSupplier()}, {@link #getLegacyHandlerUri()}, and so on.
 */
@Component
public abstract class SimpleActivityHandler<
        O extends ObjectType,
        WD extends WorkDefinition,
        SAH extends SimpleActivityHandler<O, WD, SAH>>
        extends ModelActivityHandler<WD, SAH> {

    @Autowired protected WorkDefinitionFactory workDefinitionFactory;
    @Autowired protected ProvisioningService provisioningService;
    @Autowired protected PrismContext prismContext;
    @Autowired protected ModelController modelController;
    @Autowired protected SynchronizationService synchronizationService;
    @Autowired protected SyncTaskHelper syncTaskHelper;
    @Autowired protected SecurityEnforcer securityEnforcer;
    @Autowired @Qualifier("cacheRepositoryService") protected RepositoryService repositoryService;
    @Autowired protected Clock clock;
    @Autowired protected Clockwork clockwork;
    @Autowired protected ContextFactory contextFactory;
    @Autowired protected ScriptingService scriptingService;

    @PostConstruct
    public void register() {
        handlerRegistry.register(getWorkDefinitionTypeName(), getLegacyHandlerUri(), getWorkDefinitionClass(),
                getWorkDefinitionSupplier(), this);
    }

    @PreDestroy
    public void unregister() {
        handlerRegistry.unregister(getWorkDefinitionTypeName(), getLegacyHandlerUri(), getWorkDefinitionClass());
    }

    @Override
    public AbstractActivityExecution<WD, SAH, ?> createExecution(
            @NotNull ExecutionInstantiationContext<WD, SAH> context,
            @NotNull OperationResult result) {
        return getExecutionSupplier().supply(context, getShortName());
    }

    protected abstract @NotNull QName getWorkDefinitionTypeName();

    protected abstract @NotNull Class<WD> getWorkDefinitionClass();

    protected abstract @NotNull WorkDefinitionSupplier getWorkDefinitionSupplier();

    protected abstract @NotNull ExecutionSupplier<O, WD, SAH> getExecutionSupplier();

    protected String getLegacyHandlerUri() {
        return null;
    }

    protected abstract @NotNull String getShortName();

    public interface ExecutionSupplier<
            O extends ObjectType,
            WD extends WorkDefinition,
            SAH extends SimpleActivityHandler<O, WD, SAH>> {
        @NotNull SearchBasedActivityExecution<O, WD, SAH, AbstractActivityWorkStateType> supply(
                ExecutionInstantiationContext<WD, SAH> context, String shortNameCapitalized);
    }
}
