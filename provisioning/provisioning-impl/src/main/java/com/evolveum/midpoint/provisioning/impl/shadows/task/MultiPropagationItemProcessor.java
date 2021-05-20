/*
 * Copyright (c) 2018 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.provisioning.impl.shadows.task;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.crypto.EncryptionException;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.provisioning.ucf.api.GenericFrameworkException;
import com.evolveum.midpoint.repo.common.task.ItemProcessingRequest;
import com.evolveum.midpoint.schema.ResultHandler;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.RunningTask;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.CommonException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;

/**
 * Item processor for the multi-propagation task.
 *
 * @author semancik
 */
public class MultiPropagationItemProcessor
        extends AbstractSearchIterativeItemProcessorOld
        <ResourceType,
                        MultiPropagationTaskHandler,
                        MultiPropagationTaskHandler.TaskExecution,
                        MultiPropagationActivityExecution,
                        MultiPropagationItemProcessor> {

    public MultiPropagationItemProcessor(MultiPropagationActivityExecution taskExecution) {
        super(taskExecution);
    }

    @Override
    protected boolean processObject(PrismObject<ResourceType> resource,
            ItemProcessingRequest<PrismObject<ResourceType>> request,
            RunningTask workerTask, OperationResult taskResult)
            throws CommonException {

        logger.trace("Propagating provisioning operations on {}", resource);

        ObjectQuery query = resource.getPrismContext().queryFor(ShadowType.class)
                .item(ShadowType.F_RESOURCE_REF).ref(resource.getOid())
                .and()
                .exists(ShadowType.F_PENDING_OPERATION)
            .build();

        ResultHandler<ShadowType> handler =
                (shadow, result) -> {
                    propagateShadowOperations(resource, shadow, workerTask, result);
                    return true;
                };

        taskHandler.getRepositoryService()
                .searchObjectsIterative(ShadowType.class, query, handler, null, true, taskResult);

        logger.trace("Propagation of {} done", resource);

        return true;
    }

    private void propagateShadowOperations(PrismObject<ResourceType> resource, PrismObject<ShadowType> shadow,
            Task workerTask, OperationResult result) {
        try {
            taskHandler.getShadowCache().propagateOperations(resource, shadow, workerTask, result);
        } catch (CommonException | GenericFrameworkException | EncryptionException e) {
            throw new SystemException("Generic provisioning framework error: " + e.getMessage(), e);
        }
    }
}
