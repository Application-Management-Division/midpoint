/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.model.impl.lens;

import static com.evolveum.midpoint.model.api.ProgressInformation.ActivityType.CLOCKWORK;
import static com.evolveum.midpoint.model.api.ProgressInformation.StateType.ENTERING;
import static com.evolveum.midpoint.model.api.ProgressInformation.StateType.EXITING;
import static com.evolveum.midpoint.model.impl.lens.LensUtil.getExportType;
import static com.evolveum.midpoint.model.impl.lens.LensUtil.getExportTypeTraceOrReduced;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import javax.xml.datatype.XMLGregorianCalendar;

import com.evolveum.midpoint.model.api.*;
import com.evolveum.midpoint.model.impl.lens.projector.Components;

import com.evolveum.midpoint.model.impl.lens.projector.policy.PolicyRuleEnforcer;
import com.evolveum.midpoint.prism.delta.ReferenceDelta;

import com.evolveum.midpoint.schema.result.OperationResultStatus;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.audit.api.AuditEventStage;
import com.evolveum.midpoint.common.Clock;
import com.evolveum.midpoint.model.api.context.ModelState;
import com.evolveum.midpoint.model.api.hooks.HookOperationMode;
import com.evolveum.midpoint.model.common.SystemObjectCache;
import com.evolveum.midpoint.model.common.expression.evaluator.caching.AssociationSearchExpressionEvaluatorCache;
import com.evolveum.midpoint.model.impl.lens.projector.ContextLoader;
import com.evolveum.midpoint.model.impl.lens.projector.Projector;
import com.evolveum.midpoint.model.impl.lens.projector.focus.FocusConstraintsChecker;
import com.evolveum.midpoint.model.impl.lens.projector.policy.scriptExecutor.PolicyRuleScriptExecutor;
import com.evolveum.midpoint.model.impl.lens.projector.policy.PolicyRuleSuspendTaskExecutor;
import com.evolveum.midpoint.model.impl.migrator.Migrator;
import com.evolveum.midpoint.model.impl.util.ModelImplUtils;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.provisioning.api.EventDispatcher;
import com.evolveum.midpoint.provisioning.api.ProvisioningService;
import com.evolveum.midpoint.provisioning.api.ResourceObjectChangeListener;
import com.evolveum.midpoint.provisioning.api.ResourceOperationListener;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.ObjectDeltaOperation;
import com.evolveum.midpoint.schema.cache.CacheConfigurationManager;
import com.evolveum.midpoint.schema.cache.CacheType;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultBuilder;
import com.evolveum.midpoint.schema.util.SystemConfigurationTypeUtil;
import com.evolveum.midpoint.security.enforcer.api.AuthorizationParameters;
import com.evolveum.midpoint.security.enforcer.api.SecurityEnforcer;
import com.evolveum.midpoint.task.api.*;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.Holder;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.query_3.QueryType;
import com.evolveum.prism.xml.ns._public.query_3.SearchFilterType;

/**
 * @author semancik
 *
 */
@Component
public class Clockwork {

    private static final Trace LOGGER = TraceManager.getTrace(Clockwork.class);

    private static final String OP_RUN = Clockwork.class.getName() + ".run";
    private static final String OP_CLICK = Clockwork.class.getName() + ".click";

    @Autowired private Projector projector;
    @Autowired private ContextLoader contextLoader;
    @Autowired private ChangeExecutor changeExecutor;
    @Autowired private Clock clock;
    @Autowired private SystemObjectCache systemObjectCache;
    @Autowired private ProvisioningService provisioningService;
    @Autowired private EventDispatcher eventDispatcher;
    @Autowired private PersonaProcessor personaProcessor;
    @Autowired private PrismContext prismContext;
    @Autowired private TaskManager taskManager;
    @Autowired private Tracer tracer;
    @Autowired private OperationalDataManager metadataManager;
    @Autowired private Migrator migrator;
    @Autowired private ClockworkMedic medic;
    @Autowired private PolicyRuleScriptExecutor policyRuleScriptExecutor;
    @Autowired private PolicyRuleEnforcer policyRuleEnforcer;
    @Autowired private PolicyRuleSuspendTaskExecutor policyRuleSuspendTaskExecutor;
    @Autowired private ClockworkAuthorizationHelper clockworkAuthorizationHelper;
    @Autowired private CacheConfigurationManager cacheConfigurationManager;
    @Autowired private SecurityEnforcer securityEnforcer;
    @Autowired private OperationExecutionRecorderForClockwork operationExecutionRecorder;
    @Autowired private ClockworkAuditHelper clockworkAuditHelper;
    @Autowired private ClockworkHookHelper clockworkHookHelper;
    @Autowired private ClockworkConflictResolver clockworkConflictResolver;

    @Autowired
    @Qualifier("cacheRepositoryService")
    private RepositoryService repositoryService;

    private static final int DEFAULT_MAX_CLICKS = 200;

    public <F extends ObjectType> HookOperationMode run(LensContext<F> context, Task task, OperationResult parentResult)
            throws SchemaException, PolicyViolationException, ExpressionEvaluationException, ObjectNotFoundException,
            ObjectAlreadyExistsException, CommunicationException, ConfigurationException, SecurityViolationException {

        OperationResultBuilder builder = parentResult.subresult(OP_RUN);
        boolean tracingRequested = startTracingIfRequested(context, task, builder, parentResult);
        OperationResult result = builder.build();

        // There are some parts of processing (e.g. notifications deep in provisioning module) that have no access
        // to context.channel value, only to task.channel. So we have to get the two into sync. To return to the original
        // state, we restore task.channel afterwards.
        String originalTaskChannel = task.getChannel();
        task.setChannel(context.getChannel());

        ClockworkRunTraceType trace = null;
        try {
            trace = recordTraceAtStart(context, result);

            ClockworkConflictResolver.Context conflictResolutionContext = new ClockworkConflictResolver.Context();

            HookOperationMode mode = runWithConflictDetection(context, conflictResolutionContext, task, result);

            return clockworkConflictResolver.resolveFocusConflictIfPresent(context, conflictResolutionContext, mode, task, result);

        } catch (CommonException t) {
            result.recordFatalError(t.getMessage(), t);
            throw t;
        } finally {
            task.setChannel(originalTaskChannel);
            result.computeStatusIfUnknown();

            recordTraceAtEnd(context, trace, result);
            if (tracingRequested) {
                tracer.storeTrace(task, result, parentResult);
            }
        }
    }

    /**
     * Runs the clockwork with the aim of detecting modify-modify conflicts on the focus object.
     * It reports such states via the conflictResolutionContext parameter.
     */
    <F extends ObjectType> HookOperationMode runWithConflictDetection(LensContext<F> context,
            ClockworkConflictResolver.Context conflictResolutionContext, Task task, OperationResult result)
            throws SchemaException, PolicyViolationException, ExpressionEvaluationException, ObjectNotFoundException,
            ObjectAlreadyExistsException, CommunicationException, ConfigurationException, SecurityViolationException {

        contextLoader.updateSystemConfigurationInContext(context, result);

        LOGGER.trace("Running clockwork for context {}", context);
        context.checkConsistenceIfNeeded();

        int clicked = 0;

        try {
            context.reportProgress(new ProgressInformation(CLOCKWORK, ENTERING));
            clockworkConflictResolver.createConflictWatcherOnStart(context);
            FocusConstraintsChecker
                    .enterCache(cacheConfigurationManager.getConfiguration(CacheType.LOCAL_FOCUS_CONSTRAINT_CHECKER_CACHE));
            enterAssociationSearchExpressionEvaluatorCache();
            //enterDefaultSearchExpressionEvaluatorCache();
            provisioningService.enterConstraintsCheckerCache();

            executeInitialChecks(context);

            try {
                while (context.getState() != ModelState.FINAL) {

                    int maxClicks = getMaxClicks(result);
                    if (clicked >= maxClicks) {
                        throw new IllegalStateException(
                                "Model operation took too many clicks (limit is " + maxClicks + "). Is there a cycle?");
                    }
                    clicked++;

                    HookOperationMode mode = click(context, task, result);

                    if (mode == HookOperationMode.BACKGROUND) {
                        result.recordInProgress();
                        return mode;
                    } else if (mode == HookOperationMode.ERROR) {
                        return mode;
                    }
                }
                // One last click in FINAL state
                HookOperationMode mode = click(context, task, result);
                if (mode == HookOperationMode.FOREGROUND) {
                    // We must check inside here - before watchers are unregistered
                    clockworkConflictResolver.detectFocusConflicts(context, conflictResolutionContext, result);
                }
                return mode;
            } catch (ConflictDetectedException e) {
                LOGGER.debug("Clockwork conflict detected", e);
                conflictResolutionContext.recordConflictException();
                return HookOperationMode.FOREGROUND;
            }
        } finally {
            operationExecutionRecorder.recordOperationExecutions(context, task, result);
            clockworkConflictResolver.unregisterConflictWatcher(context);
            FocusConstraintsChecker.exitCache();
            //exitDefaultSearchExpressionEvaluatorCache();
            exitAssociationSearchExpressionEvaluatorCache();
            provisioningService.exitConstraintsCheckerCache();
            context.reportProgress(new ProgressInformation(CLOCKWORK, EXITING));
        }
    }

    private <F extends ObjectType> void executeInitialChecks(LensContext<F> context) throws PolicyViolationException {
        if (context.hasFocusOfType(AssignmentHolderType.class)) {
            checkArchetypeRefDelta(context);
        }
    }

    /**
     * This check was originally present in AssignmentHolderProcessor. But it refers to focus primary delta only;
     * so it's sufficient to execute it on clockwork entry (unless primary delta is manipulated e.g. in a scripting hook).
     */
    private <F extends ObjectType> void checkArchetypeRefDelta(LensContext<F> context) throws PolicyViolationException {
        ObjectDelta<F> focusPrimaryDelta = context.getFocusContext().getPrimaryDelta();
        if (focusPrimaryDelta != null) {
            ReferenceDelta archetypeRefDelta = focusPrimaryDelta.findReferenceModification(AssignmentHolderType.F_ARCHETYPE_REF);
            if (archetypeRefDelta != null) {
                // We want to allow this under special circumstances. E.g. we want be able to import user with archetypeRef.
                // Otherwise we won't be able to export a user and re-import it again.
                if (focusPrimaryDelta.isAdd()) {
                    String archetypeOidFromAssignments = LensUtil.determineExplicitArchetypeOidFromAssignments(focusPrimaryDelta.getObjectToAdd());
                    if (archetypeOidFromAssignments == null) {
                        throw new PolicyViolationException("Attempt add archetypeRef without a matching assignment");
                    } else {
                        boolean match = true;
                        for (PrismReferenceValue archetypeRefDeltaVal : archetypeRefDelta.getValuesToAdd()) {
                            if (!archetypeOidFromAssignments.equals(archetypeRefDeltaVal.getOid())) {
                                match = false;
                            }
                        }
                        if (match) {
                            return;
                        } else {
                            throw new PolicyViolationException("Attempt add archetypeRef that does not match assignment");
                        }
                    }
                }
                throw new PolicyViolationException("Attempt to modify archetypeRef directly");
            }
        }
    }

    // todo check authorization in this method
    private <F extends ObjectType> boolean startTracingIfRequested(LensContext<F> context, Task task,
            OperationResultBuilder builder, OperationResult parentResult) throws SchemaException, ObjectNotFoundException,
            SecurityViolationException, CommunicationException, ConfigurationException, ExpressionEvaluationException {
        // If the result is already traced, we could abstain from recording the final trace ourselves.
        // But I think it's more reasonable to do that, because e.g. if there is clockwork-inside-clockwork processing,
        // we would like to have two traces, even if the second one is contained also within the first one.
        TracingProfileType tracingProfile = ModelExecuteOptions.getTracingProfile(context.getOptions());
        if (tracingProfile != null) {
            securityEnforcer.authorize(ModelAuthorizationAction.RECORD_TRACE.getUrl(), null,
                    AuthorizationParameters.EMPTY, null, task, parentResult);
            builder.tracingProfile(tracer.compileProfile(tracingProfile, parentResult));
            return true;
        } else if (task.getTracingRequestedFor().contains(TracingRootType.CLOCKWORK_RUN)) {
            TracingProfileType profile = task.getTracingProfile() != null ? task.getTracingProfile() : tracer.getDefaultProfile();
            builder.tracingProfile(tracer.compileProfile(profile, parentResult));
            return true;
        } else {
            return false;
        }
    }

    private <F extends ObjectType> ClockworkRunTraceType recordTraceAtStart(LensContext<F> context,
            OperationResult result) throws SchemaException {
        if (result.isTracingAny(ClockworkRunTraceType.class)) {
            ClockworkRunTraceType trace = new ClockworkRunTraceType(prismContext);
            trace.setInputLensContextText(context.debugDump());
            trace.setInputLensContext(context.toLensContextType(getExportTypeTraceOrReduced(trace, result)));
            result.addTrace(trace);
            return trace;
        } else {
            return null;
        }
    }

    private <F extends ObjectType> void recordTraceAtEnd(LensContext<F> context, ClockworkRunTraceType trace,
            OperationResult result) throws SchemaException {
        if (trace != null) {
            trace.setOutputLensContextText(context.debugDump());
            trace.setOutputLensContext(context.toLensContextType(getExportTypeTraceOrReduced(trace, result)));
            if (context.getFocusContext() != null) {    // todo reconsider this
                PrismObject<F> objectAny = context.getFocusContext().getObjectAny();
                if (objectAny != null) {
                    trace.setFocusName(PolyString.getOrig(objectAny.getName()));
                }
            }
        }
    }

    public <F extends ObjectType> LensContext<F> previewChanges(LensContext<F> context, Collection<ProgressListener> listeners,
            Task task, OperationResult result)
            throws SchemaException, PolicyViolationException, ExpressionEvaluationException, ObjectNotFoundException,
            ObjectAlreadyExistsException, CommunicationException, ConfigurationException, SecurityViolationException {
        try {
            context.setPreview(true);

            LOGGER.trace("Preview changes context:\n{}", context.debugDumpLazily());
            context.setProgressListeners(listeners);

            try {
                projector.projectAllWaves(context, "preview", task, result);
            } catch (ConflictDetectedException e) {
                // Should not occur on preview changes!
                throw new SystemException("Unexpected execution conflict detected: " + e.getMessage(), e);
            }
            clockworkHookHelper.invokePreview(context, task, result);
            policyRuleEnforcer.execute(context, result);
            policyRuleSuspendTaskExecutor.execute(context, task, result);

        } catch (ConfigurationException | SecurityViolationException | ObjectNotFoundException | SchemaException |
                CommunicationException | PolicyViolationException | RuntimeException | ObjectAlreadyExistsException |
                ExpressionEvaluationException e) {
            ModelImplUtils.recordFatalError(result, e);
            throw e;
        }

        LOGGER.debug("Preview changes output:\n{}", context.debugDumpLazily());

        result.computeStatus();
        result.cleanupResult();

        return context;
    }

    private void enterAssociationSearchExpressionEvaluatorCache() {
        AssociationSearchExpressionEvaluatorCache cache = AssociationSearchExpressionEvaluatorCache.enterCache(
                cacheConfigurationManager.getConfiguration(CacheType.LOCAL_ASSOCIATION_TARGET_SEARCH_EVALUATOR_CACHE));
        if (cache.getClientContextInformation() == null) {
            AssociationSearchExpressionCacheInvalidator invalidator = new AssociationSearchExpressionCacheInvalidator(cache);
            cache.setClientContextInformation(invalidator);
            eventDispatcher.registerListener((ResourceObjectChangeListener) invalidator);
            eventDispatcher.registerListener((ResourceOperationListener) invalidator);
        }
    }

    private void exitAssociationSearchExpressionEvaluatorCache() {
        AssociationSearchExpressionEvaluatorCache cache = AssociationSearchExpressionEvaluatorCache.exitCache();
        if (cache == null) {
            LOGGER.error("exitAssociationSearchExpressionEvaluatorCache: cache instance was not found for the current thread");
            return;
        }
        if (cache.getEntryCount() <= 0) {
            Object invalidator = cache.getClientContextInformation();
            if (!(invalidator instanceof AssociationSearchExpressionCacheInvalidator)) {
                LOGGER.error("exitAssociationSearchExpressionEvaluatorCache: expected {}, got {} instead",
                        AssociationSearchExpressionCacheInvalidator.class, invalidator);
                return;
            }
            eventDispatcher.unregisterListener((ResourceObjectChangeListener) invalidator);
            eventDispatcher.unregisterListener((ResourceOperationListener) invalidator);
        }
    }

    @SuppressWarnings("unused")
    private void enterDefaultSearchExpressionEvaluatorCache() {
        //DefaultSearchExpressionEvaluatorCache.enterCache(cacheConfigurationManager.getConfiguration(CacheType.LOCAL_DEFAULT_SEARCH_EVALUATOR_CACHE));
    }

    @SuppressWarnings("unused")
    private void exitDefaultSearchExpressionEvaluatorCache() {
        //DefaultSearchExpressionEvaluatorCache.exitCache();
    }

    private int getMaxClicks(OperationResult result) throws SchemaException {
        PrismObject<SystemConfigurationType> systemConfiguration = systemObjectCache.getSystemConfiguration(result);
        Integer maxClicks = SystemConfigurationTypeUtil.getMaxModelClicks(systemConfiguration);
        return Objects.requireNonNullElse(maxClicks, DEFAULT_MAX_CLICKS);
    }

    public <F extends ObjectType> HookOperationMode click(LensContext<F> context, Task task, OperationResult parentResult)
            throws SchemaException, PolicyViolationException, ExpressionEvaluationException, ObjectNotFoundException,
            ObjectAlreadyExistsException, CommunicationException, ConfigurationException, SecurityViolationException,
            ConflictDetectedException {

        // DO NOT CHECK CONSISTENCY of the context here. The context may not be fresh and consistent yet. Project will fix
        // that. Check consistency afterwards (and it is also checked inside projector several times).

        if (context.getInspector() == null) {
            context.setInspector(medic.getClockworkInspector());
        }

        OperationResult result = parentResult.subresult(OP_CLICK)
                .addQualifier(context.getOperationQualifier())
                .addArbitraryObjectAsContext("context", context)
                .addArbitraryObjectAsContext("task", task)
                .build();

        ClockworkClickTraceType trace;
        if (result.isTracingAny(ClockworkClickTraceType.class)) {
            trace = new ClockworkClickTraceType(prismContext);
            if (result.isTracingNormal(ClockworkClickTraceType.class)) {
                trace.setInputLensContextText(context.debugDump());
            }
            trace.setInputLensContext(context.toLensContextType(getExportType(trace, result)));
            result.getTraces().add(trace);
        } else {
            trace = null;
        }
        try {

            XMLGregorianCalendar now = clock.currentTimeXMLGregorianCalendar();

            // We need to determine focus before auditing. Otherwise we will not know user
            // for the accounts (unless there is a specific delta for it).
            // This is ugly, but it is the easiest way now (TODO: cleanup).
            contextLoader.determineFocusContext(context, task, result);

            ModelState state = context.getState();
            if (state == ModelState.INITIAL) {
                medic.clockworkStart(context);
                metadataManager.setRequestMetadataInContext(context, now, task);
                context.getStats().setRequestTimestamp(now);
                context.generateRequestIdentifierIfNeeded();
                // We need to do this BEFORE projection. If we would do that after projection
                // there will be secondary changes that are not part of the request.
                clockworkAuditHelper.audit(context, AuditEventStage.REQUEST, task, result, parentResult); // we need to take the overall ("run" operation result) not the current one
            }

            if (state != ModelState.FINAL) {
                projectIfNeeded(context, task, result);
            }

            checkIndestructible(context, task, result);
            if (!context.isRequestAuthorized()) {
                clockworkAuthorizationHelper.authorizeContextRequest(context, task, result);
            }

            medic.traceContext(LOGGER, "CLOCKWORK (" + state + ")", "before processing", true, context, false);
            context.checkConsistenceIfNeeded();
            context.checkEncryptedIfNeeded();

            return moveStateForward(context, task, parentResult, result, state);

        } catch (CommunicationException | ConfigurationException | ExpressionEvaluationException | ObjectNotFoundException |
                PolicyViolationException | SchemaException | SecurityViolationException | RuntimeException | Error |
                ObjectAlreadyExistsException | ConflictDetectedException e) {
            processClockworkException(context, e, task, result, parentResult);
            throw e;
        } finally {
            if (trace != null) {
                if (result.isTracingNormal(ClockworkClickTraceType.class)) {
                    trace.setOutputLensContextText(context.debugDump());
                }
                trace.setOutputLensContext(context.toLensContextType(getExportType(trace, result)));
            }
            result.computeStatusIfUnknown(); // Maybe this should be "composite" instead.
            result.cleanupResultDeeply();
        }
    }

    private <F extends ObjectType> void projectIfNeeded(LensContext<F> context, Task task, OperationResult result)
            throws SchemaException, ConfigurationException, PolicyViolationException, ExpressionEvaluationException,
            ObjectNotFoundException, ObjectAlreadyExistsException, CommunicationException, SecurityViolationException,
            ConflictDetectedException {
        boolean recompute = false;
        if (!context.isFresh()) {
            LOGGER.trace("Context is not fresh -- forcing cleanup and recomputation");
            recompute = true;
        } else if (context.getExecutionWave() > context.getProjectionWave()) {        // should not occur
            LOGGER.warn("Execution wave is greater than projection wave -- forcing cleanup and recomputation");
            recompute = true;
        } else if (context.isInPrimary() && ModelExecuteOptions.getInitialPartialProcessing(context.getOptions()) != null) {
            LOGGER.trace("Initial phase was run with initialPartialProcessing option -- forcing cleanup and recomputation");
            recompute = true;
        }

        if (recompute) {
            context.cleanup();
            LOGGER.trace("Running projector with cleaned-up context for execution wave {}", context.getExecutionWave());
            projector.project(context, "PROJECTOR ("+ context.getState() +")", task, result);
        } else if (context.getExecutionWave() == context.getProjectionWave()) {
            LOGGER.trace("Resuming projector for execution wave {}", context.getExecutionWave());
            projector.resume(context, "PROJECTOR ("+ context.getState() +")", task, result);
        } else {
            LOGGER.trace("Skipping projection because the context is fresh and projection for current wave has already run");
        }
    }

    private <F extends ObjectType> HookOperationMode moveStateForward(LensContext<F> context, Task task, OperationResult parentResult,
            OperationResult result, ModelState state) throws PolicyViolationException, ObjectNotFoundException, SchemaException,
            ObjectAlreadyExistsException, CommunicationException, ConfigurationException, SecurityViolationException,
            ExpressionEvaluationException, ConflictDetectedException {
        switch (state) {
            case INITIAL:
                processInitialToPrimary(context, task, result);
                break;
            case PRIMARY:
                processPrimaryToSecondary(context, task, result);
                break;
            case SECONDARY:
                processSecondary(context, task, result, parentResult);
                if (context.getExecutionWave() > context.getMaxWave() + 1) {
                    processSecondaryToFinal(context, task, result);
                }
                break;
            case FINAL:
                HookOperationMode mode = processFinal(context, task, result, parentResult);
                medic.clockworkFinish(context);
                return mode;
        }
        return clockworkHookHelper.invokeHooks(context, task, result);
    }

    private <F extends ObjectType> void switchState(LensContext<F> context, ModelState newState) {
        medic.clockworkStateSwitch(context, newState);
        context.setState(newState);
    }

    private <F extends ObjectType> void processInitialToPrimary(LensContext<F> context, Task task, OperationResult result)
            throws PolicyViolationException, SchemaException, ObjectNotFoundException {
        // To mimic operation of the original enforcer hook, we execute the following only in the initial state.
        policyRuleEnforcer.execute(context, result);
        policyRuleSuspendTaskExecutor.execute(context, task, result);

        switchState(context, ModelState.PRIMARY);
    }

    private <F extends ObjectType> void processPrimaryToSecondary(LensContext<F> context, Task task, OperationResult result) throws PolicyViolationException, ObjectNotFoundException, SchemaException {
        switchState(context, ModelState.SECONDARY);
    }

    private <F extends ObjectType> void processSecondary(LensContext<F> context, Task task, OperationResult result, OperationResult overallResult)
            throws ObjectAlreadyExistsException, ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException,
            SecurityViolationException, ExpressionEvaluationException, PolicyViolationException, ConflictDetectedException {

        Holder<Boolean> restartRequestedHolder = new Holder<>(false);

        medic.partialExecute(Components.EXECUTION,
                (result1) -> {
                    boolean restartRequested = changeExecutor.executeChanges(context, task, result1);
                    restartRequestedHolder.setValue(restartRequested);
                },
                context.getPartialProcessingOptions()::getExecution,
                Clockwork.class, context, null, result);

        clockworkAuditHelper.audit(context, AuditEventStage.EXECUTION, task, result, overallResult);

        context.rotIfNeeded();
        context.resetDeltasAfterExecution();

        if (!restartRequestedHolder.getValue()) {
            context.incrementExecutionWave();
        } else {
            LOGGER.trace("Restart of the current execution wave ({}) was requested by the change executor",
                    context.getExecutionWave());
            // Shouldn't we explicitly rot context here?
            // BTW, what if restart is requested indefinitely?
        }

        medic.traceContext(LOGGER, "CLOCKWORK (" + context.getState() + ")", "change execution", false, context, false);
    }

    private <F extends ObjectType> void processSecondaryToFinal(LensContext<F> context, Task task, OperationResult result) throws SchemaException {
        switchState(context, ModelState.FINAL);
        policyRuleScriptExecutor.execute(context, task, result);
    }

    private <F extends ObjectType> HookOperationMode processFinal(LensContext<F> context, Task task, OperationResult result, OperationResult overallResult)
            throws ObjectAlreadyExistsException, ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException,
            SecurityViolationException, ExpressionEvaluationException, PolicyViolationException {
        clockworkAuditHelper.auditFinalExecution(context, task, result, overallResult);
        logFinalReadable(context);
        migrator.executeAfterOperationMigration(context, result);

        HookOperationMode opmode = personaProcessor.processPersonaChanges(context, task, result);
        if (opmode == HookOperationMode.BACKGROUND) {
            return opmode;
        }

        return triggerReconcileAffected(context, task, result);
    }

    private <F extends ObjectType> HookOperationMode triggerReconcileAffected(LensContext<F> context, Task task, OperationResult result) throws SchemaException {
        if (!ModelExecuteOptions.isReconcileAffected(context.getOptions())) {
            return HookOperationMode.FOREGROUND;
        }
        if (context.getFocusClass() == null || !RoleType.class.isAssignableFrom(context.getFocusClass())) {
            LOGGER.warn("ReconcileAffected requested but not available for {}. Doing nothing.", context.getFocusClass());
            return HookOperationMode.FOREGROUND;
        }

        // check preconditions
        PrismObject<F> role = context.getFocusContextRequired().getObjectAny();
        if (role == null) {
            throw new IllegalStateException("No focus object when expected it");
        }

        // preparing the recompute/reconciliation task
        Task reconTask;
        if (task.isPersistent()) {
            reconTask = task.createSubtask();
        } else {
            reconTask = task;
        }
        assert !reconTask.isPersistent();

        // creating object query
        PrismPropertyDefinition propertyDef = prismContext.getSchemaRegistry()
                .findPropertyDefinitionByElementName(SchemaConstants.MODEL_EXTENSION_OBJECT_QUERY);
        PrismReferenceValue referenceValue = prismContext.itemFactory().createReferenceValue(context.getFocusContext().getOid(), RoleType.COMPLEX_TYPE);
        ObjectFilter refFilter = prismContext.queryFor(FocusType.class)
                .item(FocusType.F_ASSIGNMENT, AssignmentType.F_TARGET_REF).ref(referenceValue)
                .buildFilter();
        SearchFilterType filterType = prismContext.getQueryConverter().createSearchFilterType(refFilter);
        QueryType queryType = new QueryType();
        queryType.setFilter(filterType);
        //noinspection unchecked
        PrismProperty<QueryType> property = propertyDef.instantiate();
        property.setRealValue(queryType);
        reconTask.addExtensionProperty(property);

        // other parameters
        reconTask.setName("Recomputing users after changing role " + role.asObjectable().getName());
        reconTask.setInitiallyRunnable();
        reconTask.setHandlerUri(ModelPublicConstants.RECOMPUTE_HANDLER_URI); // FIXME
        reconTask.setCategory(TaskCategory.RECOMPUTATION);
        reconTask.addArchetypeInformationIfMissing(SystemObjectsType.ARCHETYPE_RECOMPUTATION_TASK.value());
        taskManager.switchToBackground(reconTask, result);
        result.setBackgroundTaskOid(reconTask.getOid());
        result.recordStatus(OperationResultStatus.IN_PROGRESS, "Reconciliation task switched to background");
        return HookOperationMode.BACKGROUND;
    }

    /**
     * Check for indestructibility.
     * This check just makes sure that we fail fast, before we run full clockwork.
     */
    private void checkIndestructible(LensContext<? extends ObjectType> context, Task task, OperationResult result) throws IndestructibilityViolationException {
        checkIndestructible(context.getFocusContext(), task, result);
        for (LensProjectionContext projCtx : context.getProjectionContexts()) {
            checkIndestructible(projCtx, task, result);
        }
    }

    private <O extends ObjectType> void checkIndestructible(LensElementContext<O> elementContext, Task task, OperationResult result) throws IndestructibilityViolationException {
        if (elementContext != null && elementContext.isDelete()) {
            PrismObject<O> contextObject = elementContext.getObjectAny();
            if (contextObject != null && Boolean.TRUE.equals(contextObject.asObjectable().isIndestructible())) {
                IndestructibilityViolationException e = new IndestructibilityViolationException("Attempt to delete indestructible object "+contextObject);
                ModelImplUtils.recordFatalError(result, e);
                throw e;
            }
        }
    }

    private <F extends ObjectType> void processClockworkException(LensContext<F> context, Throwable e, Task task, OperationResult result, OperationResult overallResult)
            throws SchemaException {
        LOGGER.trace("Processing clockwork exception {}", e.toString());
        result.recordFatalErrorNotFinish(e);
        clockworkAuditHelper.auditEvent(context, AuditEventStage.EXECUTION, null, true, task, result, overallResult);

        reclaimSequencesIfPossible(context, task, result);
        result.recordEnd();
    }

    /**
     * An exception occurred, so it's quite possible that allocated sequence values have to be reclaimed.
     *
     * But this is safe to do only if we are sure they were not used. Currently the only way how
     * to be sure is to know that no focus/projection deltas were applied. It is an approximate
     * solution (because sequence values were maybe not used in these deltas), but we have nothing
     * better at hand now.
     */
    private <F extends ObjectType> void reclaimSequencesIfPossible(LensContext<F> context, Task task, OperationResult result) throws SchemaException {
        if (!context.wasAnythingExecuted()) {
            LensUtil.reclaimSequences(context, repositoryService, task, result);
        } else {
            LOGGER.trace("Something was executed, so we are not reclaiming sequence values");
        }
    }

    /**
     * Logs the entire operation in a human-readable fashion.
     */
    private <F extends ObjectType> void logFinalReadable(LensContext<F> context) {
        if (!LOGGER.isDebugEnabled()) {
            return;
        }

        // a priori: sync delta
        boolean hasSyncDelta = false;
        for (LensProjectionContext projectionContext: context.getProjectionContexts()) {
            ObjectDelta<ShadowType> syncDelta = projectionContext.getSyncDelta();
            if (syncDelta != null) {
                hasSyncDelta = true;
                break;
            }
        }

        Collection<ObjectDeltaOperation<? extends ObjectType>> executedDeltas = context.getExecutedDeltas();
        if (!hasSyncDelta && executedDeltas.isEmpty()) {
            // Not worth mentioning
            return;
        }

        StringBuilder sb = new StringBuilder();
        String channel = context.getChannel();
        if (channel != null) {
            sb.append("Channel: ").append(channel).append("\n");
        }

        if (hasSyncDelta) {
            sb.append("Triggered by synchronization delta\n");
            for (LensProjectionContext projectionContext: context.getProjectionContexts()) {
                ObjectDelta<ShadowType> syncDelta = projectionContext.getSyncDelta();
                if (syncDelta != null) {
                    sb.append(syncDelta.debugDump(1));
                    sb.append("\n");
                }
                DebugUtil.debugDumpLabel(sb, "Situation", 1);
                sb.append(" ");
                sb.append(projectionContext.getSynchronizationSituationDetected());
                sb.append(" -> ");
                sb.append(projectionContext.getSynchronizationSituationResolved());
                sb.append("\n");
            }
        }
        for (LensProjectionContext projectionContext: context.getProjectionContexts()) {
            if (projectionContext.isSyncAbsoluteTrigger()) {
                sb.append("Triggered by absolute state of ").append(projectionContext.getHumanReadableName());
                sb.append(": ");
                sb.append(projectionContext.getSynchronizationSituationDetected());
                sb.append(" -> ");
                sb.append(projectionContext.getSynchronizationSituationResolved());
                sb.append("\n");
            }
        }

        // focus primary
        LensFocusContext<F> focusContext = context.getFocusContext();
        if (focusContext != null) {
            ObjectDelta<F> focusPrimaryDelta = focusContext.getPrimaryDelta();
            if (focusPrimaryDelta != null) {
                sb.append("Triggered by focus primary delta\n");
                DebugUtil.indentDebugDump(sb, 1);
                sb.append(focusPrimaryDelta.toString());
                sb.append("\n");
            }
        }

        // projection primary
        Collection<ObjectDelta<ShadowType>> projPrimaryDeltas = new ArrayList<>();
        for (LensProjectionContext projectionContext: context.getProjectionContexts()) {
            ObjectDelta<ShadowType> projPrimaryDelta = projectionContext.getPrimaryDelta();
            if (projPrimaryDelta != null) {
                projPrimaryDeltas.add(projPrimaryDelta);
            }
        }
        if (!projPrimaryDeltas.isEmpty()) {
            sb.append("Triggered by projection primary delta\n");
            for (ObjectDelta<ShadowType> projDelta: projPrimaryDeltas) {
                DebugUtil.indentDebugDump(sb, 1);
                sb.append(projDelta.toString());
                sb.append("\n");
            }
        }

        if (focusContext != null) {
            sb.append("Focus: ").append(focusContext.getHumanReadableName()).append("\n");
        }
        if (!context.getProjectionContexts().isEmpty()) {
            sb.append("Projections (").append(context.getProjectionContexts().size()).append("):\n");
            for (LensProjectionContext projectionContext: context.getProjectionContexts()) {
                DebugUtil.indentDebugDump(sb, 1);
                sb.append(projectionContext.getHumanReadableName());
                if (projectionContext.isTombstone()) {
                    sb.append(" TOMBSTONE");
                }
                sb.append(": ");
                sb.append(projectionContext.getSynchronizationPolicyDecision());
                sb.append("\n");
            }
        }

        if (executedDeltas.isEmpty()) {
            sb.append("Executed: nothing\n");
        } else {
            sb.append("Executed:\n");
            for (ObjectDeltaOperation<? extends ObjectType> executedDelta: executedDeltas) {
                ObjectDelta<? extends ObjectType> delta = executedDelta.getObjectDelta();
                OperationResult deltaResult = executedDelta.getExecutionResult();
                DebugUtil.indentDebugDump(sb, 1);
                sb.append(delta.toString());
                sb.append(": ");
                sb.append(deltaResult.getStatus());
                sb.append("\n");
            }
        }

        LOGGER.debug("\n###[ CLOCKWORK SUMMARY ]######################################\n{}" +
                       "##############################################################",
                sb.toString());
    }
}
