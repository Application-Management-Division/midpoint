/*
 * Copyright (c) 2010-2017 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.impl.integrity;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.model.api.ModelPublicConstants;
import com.evolveum.midpoint.model.common.SystemObjectCache;
import com.evolveum.midpoint.model.impl.tasks.AbstractModelTaskHandler;
import com.evolveum.midpoint.repo.common.task.AbstractTaskExecution;
import com.evolveum.midpoint.repo.common.task.PartExecutionClass;
import com.evolveum.midpoint.repo.common.task.TaskExecutionClass;
import com.evolveum.midpoint.schema.result.OperationConstants;
import com.evolveum.midpoint.task.api.RunningTask;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.task.api.TaskCategory;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SystemObjectsType;

/**
 * Task handler for "Object integrity check" task.
 *
 * The purpose of this task is to detect and optionally fix anomalies in repository objects.
 *
 * However, currently its only function is to display information about objects size.
 */
@Component
@TaskExecutionClass(ObjectIntegrityCheckTaskHandler.TaskExecution.class)
@PartExecutionClass(ObjectIntegrityCheckTaskPartExecution.class)
public class ObjectIntegrityCheckTaskHandler
        extends AbstractModelTaskHandler
        <ObjectIntegrityCheckTaskHandler, ObjectIntegrityCheckTaskHandler.TaskExecution> {

    public static final String HANDLER_URI = ModelPublicConstants.OBJECT_INTEGRITY_CHECK_TASK_HANDLER_URI;

    private static final Trace LOGGER = TraceManager.getTrace(ObjectIntegrityCheckTaskHandler.class);

    // WARNING! This task handler is efficiently singleton!
     // It is a spring bean and it is supposed to handle all search task instances
     // Therefore it must not have task-specific fields. It can only contain fields specific to
     // all tasks of a specified type

    @Autowired SystemObjectCache systemObjectCache;

    public ObjectIntegrityCheckTaskHandler() {
        super(LOGGER, "Object integrity check", OperationConstants.CHECK_OBJECT_INTEGRITY);
        globalReportingOptions.setPreserveStatistics(false);
        globalReportingOptions.setLogErrors(false); // we do log errors ourselves
        globalReportingOptions.setSkipWritingOperationExecutionRecords(true); // because of performance
    }

    @PostConstruct
    private void initialize() {
        taskManager.registerHandler(HANDLER_URI, this);
    }

    @Override
    public String getCategoryName(Task task) {
        return TaskCategory.UTIL;
    }

    @Override
    public String getArchetypeOid() {
        return SystemObjectsType.ARCHETYPE_UTILITY_TASK.value();
    }

    /** Just to make Java compiler happy. */
    protected static class TaskExecution
            extends AbstractTaskExecution<ObjectIntegrityCheckTaskHandler, TaskExecution> {

        public TaskExecution(ObjectIntegrityCheckTaskHandler taskHandler, RunningTask localCoordinatorTask) {
            super(taskHandler, localCoordinatorTask);
        }
    }
}
