/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.model.impl.scripting;

import static com.evolveum.midpoint.util.MiscUtil.argCheck;

import javax.xml.namespace.QName;

import com.evolveum.midpoint.repo.common.task.BaseSearchBasedExecutionSpecificsImpl;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.model.api.ModelPublicConstants;
import com.evolveum.midpoint.model.api.ScriptExecutionResult;
import com.evolveum.midpoint.model.impl.tasks.simple.SimpleActivityHandler;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.repo.common.activity.ActivityExecutionException;
import com.evolveum.midpoint.repo.common.activity.definition.AbstractWorkDefinition;
import com.evolveum.midpoint.repo.common.activity.definition.ObjectSetSpecificationProvider;
import com.evolveum.midpoint.repo.common.activity.definition.WorkDefinitionFactory.WorkDefinitionSupplier;
import com.evolveum.midpoint.repo.common.task.ActivityReportingOptions;
import com.evolveum.midpoint.repo.common.task.ItemProcessingRequest;
import com.evolveum.midpoint.repo.common.task.SearchBasedActivityExecution;
import com.evolveum.midpoint.repo.common.task.SearchBasedActivityExecution.SearchBasedSpecificsSupplier;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.expression.VariablesMap;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.task.work.LegacyWorkDefinitionSource;
import com.evolveum.midpoint.schema.util.task.work.ObjectSetUtil;
import com.evolveum.midpoint.schema.util.task.work.WorkDefinitionSource;
import com.evolveum.midpoint.schema.util.task.work.WorkDefinitionWrapper.TypedWorkDefinitionWrapper;
import com.evolveum.midpoint.task.api.RunningTask;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.exception.CommonException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.IterativeScriptingWorkDefinitionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectSetType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SystemObjectsType;
import com.evolveum.midpoint.xml.ns._public.model.scripting_3.ExecuteScriptType;
import com.evolveum.midpoint.xml.ns._public.model.scripting_3.ValueListType;

@Component
public class IterativeScriptingActivityHandler
        extends SimpleActivityHandler<
            ObjectType,
            IterativeScriptingActivityHandler.MyWorkDefinition,
            IterativeScriptingActivityHandler> {

    private static final String LEGACY_HANDLER_URI = ModelPublicConstants.ITERATIVE_SCRIPT_EXECUTION_TASK_HANDLER_URI;
    private static final Trace LOGGER = TraceManager.getTrace(IterativeScriptingActivityHandler.class);

    @Override
    protected @NotNull QName getWorkDefinitionTypeName() {
        return IterativeScriptingWorkDefinitionType.COMPLEX_TYPE;
    }

    @Override
    protected @NotNull Class<MyWorkDefinition> getWorkDefinitionClass() {
        return MyWorkDefinition.class;
    }

    @Override
    protected @NotNull WorkDefinitionSupplier getWorkDefinitionSupplier() {
        return MyWorkDefinition::new;
    }

    @Override
    protected @NotNull SearchBasedSpecificsSupplier<ObjectType, MyWorkDefinition, IterativeScriptingActivityHandler> getSpecificSupplier() {
        return MyExecutionSpecifics::new;
    }

    @Override
    protected @NotNull String getLegacyHandlerUri() {
        return LEGACY_HANDLER_URI;
    }

    @Override
    public String getDefaultArchetypeOid() {
        return SystemObjectsType.ARCHETYPE_ITERATIVE_BULK_ACTION_TASK.value();
    }

    @Override
    protected @NotNull String getShortName() {
        return "Iterative scripting";
    }

    @Override
    public String getIdentifierPrefix() {
        return "iterative-scripting";
    }

    static class MyExecutionSpecifics extends
            BaseSearchBasedExecutionSpecificsImpl<ObjectType, MyWorkDefinition, IterativeScriptingActivityHandler> {

        MyExecutionSpecifics(
                @NotNull SearchBasedActivityExecution<ObjectType, MyWorkDefinition, IterativeScriptingActivityHandler, ?> activityExecution) {
            super(activityExecution);
        }

        @Override
        public @NotNull ActivityReportingOptions getDefaultReportingOptions() {
            return super.getDefaultReportingOptions()
                    .enableActionsExecutedStatistics(true);
        }

        @Override
        public boolean processObject(@NotNull PrismObject<ObjectType> object,
                @NotNull ItemProcessingRequest<PrismObject<ObjectType>> request, RunningTask workerTask, OperationResult result)
                throws CommonException, ActivityExecutionException {
            executeScriptOnObject(object, workerTask, result);
            return true;
        }

        private void executeScriptOnObject(PrismObject<ObjectType> object, RunningTask workerTask, OperationResult result)
                throws CommonException {
            ExecuteScriptType executeScriptRequest = getWorkDefinition().getScriptExecutionRequest().clone();
            executeScriptRequest.setInput(new ValueListType().value(object.asObjectable()));
            ScriptExecutionResult executionResult = getActivityHandler().scriptingService.evaluateExpression(executeScriptRequest,
                    VariablesMap.emptyMap(), false, workerTask, result);
            LOGGER.debug("Execution output: {} item(s)", executionResult.getDataOutput().size());
            LOGGER.debug("Execution result:\n{}", executionResult.getConsoleOutput());
            result.computeStatus();
        }
    }

    public static class MyWorkDefinition extends AbstractWorkDefinition implements ObjectSetSpecificationProvider {

        private final ObjectSetType objects;
        private final ExecuteScriptType scriptExecutionRequest;

        MyWorkDefinition(WorkDefinitionSource source) {
            if (source instanceof LegacyWorkDefinitionSource) {
                objects = ObjectSetUtil.fromLegacySource((LegacyWorkDefinitionSource) source);
                scriptExecutionRequest = ((LegacyWorkDefinitionSource) source)
                        .getExtensionItemRealValue(SchemaConstants.SE_EXECUTE_SCRIPT, ExecuteScriptType.class);
            } else {
                IterativeScriptingWorkDefinitionType typedDefinition = (IterativeScriptingWorkDefinitionType)
                        ((TypedWorkDefinitionWrapper) source).getTypedDefinition();
                objects = typedDefinition.getObjects();
                scriptExecutionRequest = typedDefinition.getScriptExecutionRequest();
            }
            argCheck(scriptExecutionRequest != null, "No script execution request provided");
            argCheck(scriptExecutionRequest.getScriptingExpression() != null, "No scripting expression provided");
            if (scriptExecutionRequest.getInput() != null && !scriptExecutionRequest.getInput().getValue().isEmpty()) {
                LOGGER.warn("Ignoring input values in executeScript data: {}", scriptExecutionRequest);
            }
        }

        @Override
        public ObjectSetType getObjectSetSpecification() {
            return objects;
        }

        public ExecuteScriptType getScriptExecutionRequest() {
            return scriptExecutionRequest;
        }

        @Override
        protected void debugDumpContent(StringBuilder sb, int indent) {
            DebugUtil.debugDumpWithLabelLn(sb, "objects", objects, indent+1);
            DebugUtil.debugDumpWithLabelLn(sb, "scriptExecutionRequest", String.valueOf(scriptExecutionRequest), indent+1);
        }
    }
}
