/*
 * Copyright (c) 2010-2018 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.common.task;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.repo.api.PreconditionViolationException;
import com.evolveum.midpoint.repo.common.task.definition.WorkDefinition;
import com.evolveum.midpoint.repo.common.task.handlers.ActivityHandler;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.RunningTask;
import com.evolveum.midpoint.util.exception.CommonException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OperationResultType;

import org.jetbrains.annotations.NotNull;

import static com.evolveum.midpoint.schema.result.OperationResultStatus.NOT_APPLICABLE;

/**
 * Processes individual objects in iterative activity execution.
 *
 * It provides backwards-compatible {@link #processObject(PrismObject, ItemProcessingRequest, RunningTask, OperationResult)}
 * to be used instead of more generic {@link #process(ItemProcessingRequest, RunningTask, OperationResult)} method.
 *
 * But also allows separate processing of errored objects by {@link #processError(PrismObject, OperationResultType, RunningTask, OperationResult)}.
 */
public abstract class AbstractSearchIterativeItemProcessor<
        O extends ObjectType,
        WD extends WorkDefinition,
        AH extends ActivityHandler<WD>,
        AE extends AbstractSearchIterativeActivityExecution<O, WD, AH, AE>>
        implements ItemProcessor<PrismObject<O>> {

    private static final Trace LOGGER = TraceManager.getTrace(AbstractSearchIterativeItemProcessor.class);

    private static final String OP_PREPROCESS_OBJECT = AbstractSearchIterativeItemProcessor.class.getName() + ".preprocessObject";

    /**
     * Execution of the containing task part.
     */
    @NotNull protected final AE activityExecution;

    public AbstractSearchIterativeItemProcessor(@NotNull AE activityExecution) {
        this.activityExecution = activityExecution;
    }

    @Override
    public boolean process(ItemProcessingRequest<PrismObject<O>> request, RunningTask workerTask,
            OperationResult result) throws CommonException, PreconditionViolationException {

        PrismObject<O> object = request.getItem();
        String oid = object.getOid();
        if (oid != null) {
            if (!activityExecution.checkAndRegisterOid(oid)) {
                LOGGER.trace("Skipping OID that has been already seen: {}", oid);
                result.recordStatus(NOT_APPLICABLE, "Object has been already seen");
                return true; // continue working
            }
        } else {
            LOGGER.trace("OID is null; can be in case of malformed objects");
        }

        if (filteredOutByAdditionalFilter(request)) {
            LOGGER.trace("Request {} filtered out by additional filter", request);
            result.recordStatus(NOT_APPLICABLE, "Filtered out by additional filter");
            return true; // continue working
        }

        OperationResultType originalFetchResult = object.asObjectable().getFetchResult();
        if (originalFetchResult == null) {
            return processWithPreprocessing(request, workerTask, result);
        } else {
            return processError(object, originalFetchResult, workerTask, result);
        }
    }

    private boolean filteredOutByAdditionalFilter(ItemProcessingRequest<PrismObject<O>> request)
            throws SchemaException {
        return activityExecution.additionalFilter != null &&
                !activityExecution.additionalFilter.match(request.getItem().getValue(), getBeans().matchingRuleRegistry);
    }

    private CommonTaskBeans getBeans() {
        return activityExecution.getBeans();
    }

    private boolean processWithPreprocessing(ItemProcessingRequest<PrismObject<O>> request, RunningTask workerTask,
            OperationResult result) throws CommonException, PreconditionViolationException {
        PrismObject<O> objectToProcess = preprocessObject(request, workerTask, result);
        return processObject(objectToProcess, request, workerTask, result);
    }

    private PrismObject<O> preprocessObject(ItemProcessingRequest<PrismObject<O>> request, RunningTask workerTask,
            OperationResult parentResult) throws CommonException {
        if (activityExecution.preprocessor == null) {
            return request.getItem();
        }
        OperationResult result = parentResult.createMinorSubresult(OP_PREPROCESS_OBJECT);
        try {
            return activityExecution.preprocessor.preprocess(request.getItem(), workerTask, result);
        } catch (Throwable t) {
            result.recordFatalError(t);
            throw t; // any exceptions thrown are treated in the gatekeeper
        } finally {
            result.computeStatusIfUnknown();
        }
    }

    /**
     * Processes given object that came as part of a request.
     *
     * BEWARE: Object may have been preprocessed, and may be different from the object present in the request.
     */
    protected abstract boolean processObject(PrismObject<O> object, ItemProcessingRequest<PrismObject<O>> request,
            RunningTask workerTask, OperationResult result)
            throws CommonException, PreconditionViolationException;

    @SuppressWarnings({ "WeakerAccess", "unused" })
    protected boolean processError(PrismObject<O> object, @NotNull OperationResultType errorFetchResult, RunningTask workerTask,
            OperationResult result)
            throws CommonException, PreconditionViolationException {
        result.recordFatalError("Error in preprocessing: " + errorFetchResult.getMessage());
        return true; // "Can continue" flag is updated by item processing gatekeeper (unfortunately, the exception is lost)
    }
}
