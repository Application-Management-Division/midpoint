/*
 * Copyright (c) 2010-2018 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.common.task;

import java.lang.reflect.Constructor;
import java.util.*;

import com.evolveum.midpoint.prism.match.MatchingRuleRegistry;
import com.evolveum.midpoint.repo.common.util.OperationExecutionRecorderForTasks;
import com.evolveum.midpoint.schema.SchemaService;

import com.evolveum.midpoint.schema.cache.CacheConfigurationManager;
import com.evolveum.midpoint.task.api.*;
import com.evolveum.midpoint.util.exception.SystemException;

import com.evolveum.midpoint.util.logging.Trace;

import com.evolveum.midpoint.xml.ns._public.common.common_3.TaskPartDefinitionType;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.repo.api.RepositoryService;

import static com.evolveum.midpoint.repo.common.task.TaskExceptionHandlingUtil.processException;
import static com.evolveum.midpoint.repo.common.task.TaskExceptionHandlingUtil.processFinish;

/**
 * Abstract base task handler for any non-trivial (mostly iterative) tasks.
 *
 * The whole structure looks like this:
 *
 * 1. {@link AbstractTaskHandler} is the main entry point: it provides {@link TaskHandler} interface to the task manager.
 *    After its {@link TaskHandler#run(RunningTask)} method is invoked, it instantiates {@link AbstractTaskExecution} and
 *    passes the control to it. Its other responsibility is to hold autowired beans (as it is a Spring component, unlike
 *    related classes).
 * 2. The {@link AbstractTaskExecution} represents the specific execution of the task. It should contain all the
 *    fields that are specific to given task instance, like fetched resource definition object (for synchronization tasks),
 *    timestamps (for scanner tasks), and so on.
 * 3. The task execution object then instantiates - via {@link AbstractTaskExecution#createPartExecutions()}
 *    method - objects that are responsible for execution of individual _task parts_. For example, a reconciliation
 *    task consists of three such parts: processing unfinished shadows, resource objects reconciliation, and (remaining)
 *    shadows reconciliation. Currently these parts are subclasses of {@link AbstractIterativeTaskPartExecution}.
 * 4. Each part issues an iteration process, typically by starting a search by providing an object type, a query,
 *    and search options. (An alternative way is to start iteration in other way, e.g. by starting a live sync
 *    or async update.) Then the part instantiates _item processor_.
 * 5. Item processor is a subclass of {@link AbstractIterativeItemProcessor} and is responsible for processing
 *    items found (objects or events) under the control of {@link ItemProcessingGatekeeper}.
 *
 * *TODO Specify responsibilities of individual components w.r.t. multithreading, error handling,
 *   progress and error reporting, and so on.*
 *
 * This approach may look like an overkill for simple tasks (like e.g. recompute or propagation tasks), but it enables
 * code deduplication and simplification for really complex tasks, like the reconciliation. It is experimental and probably will
 * evolve in the future. There is also a possibility of introducing the _task execution_ concept at the level of
 * the task manager.
 *
 * For the simplest tasks please use `SimpleIterativeTaskHandler` (in `model-impl`) that hides all the complexity
 * in exchange for some task limitations, like having only a single part, and so on.
 *
 * *TODO: Generalize this class a bit. In fact, there is nothing specific to the iterative nature
 *        of the processing here.*
 *
 * *WARNING!* The task handler is effectively singleton! It is a Spring bean and it is
 * supposed to handle all search task instances. Therefore it must not have task-specific fields.
 * It can only contain fields specific to all tasks of a specified type.
 *
 * @author semancik
 */
public abstract class AbstractTaskHandler<
        TH extends AbstractTaskHandler<TH, TE>,
        TE extends AbstractTaskExecution<TH, TE>>
        implements TaskHandler {

    /**
     * Logger that is specific to the concrete task handler class. This is to avoid logging everything under
     * common {@link AbstractTaskHandler} or {@link AbstractTaskExecution} or similar classes.
     * Also, it allows to group all processing related to the given task under a single logger.
     * Provided by the constructor.
     */
    @NotNull private final Trace logger;

    /**
     * Human-understandable name of the task type, like "Recompute", "Import from resource", and so on.
     * Used for logging and similar purposes. Provided by the constructor.
     */
    @NotNull protected final String taskTypeName;

    /**
     * Prefix for the task's operation result operation name.
     * E.g. `com.evolveum.midpoint.common.operation.reconciliation`.
     */
    @NotNull final String taskOperationPrefix;

    /**
     * Options that govern how various aspects of task execution (progress, errors, statistics, and so on)
     * are reported - into the log or by other means. These are "global", i.e. applicable to all tasks
     * handled by this task handler. They may be fine-tuned for specific tasks by configuration means.
     */
    @NotNull protected final TaskReportingOptions globalReportingOptions;

    /**
     * Executions (instances) of the current task handler. Used to delegate {@link #heartbeat(Task)} method calls.
     * Note: the future of this method is unclear.
     */
    @NotNull private final Map<String, TE> currentTaskExecutions = Collections.synchronizedMap(new HashMap<>());

    // Various useful beans.

    @Autowired protected TaskManager taskManager;
    @Autowired private Tracer tracer;
    @Autowired private CacheConfigurationManager cacheConfigurationManager;
    @Autowired @Qualifier("cacheRepositoryService") protected RepositoryService repositoryService;
    @Autowired protected PrismContext prismContext;
    @Autowired protected SchemaService schemaService;
    @Autowired protected MatchingRuleRegistry matchingRuleRegistry;
    @Autowired protected OperationExecutionRecorderForTasks operationExecutionRecorder;
    @Autowired protected LightweightIdentifierGenerator lightweightIdentifierGenerator;

    protected AbstractTaskHandler(@NotNull Trace logger, @NotNull String taskTypeName, @NotNull String taskOperationPrefix) {
        this.logger = logger;
        this.taskTypeName = taskTypeName;
        this.taskOperationPrefix = taskOperationPrefix;
        this.globalReportingOptions = new TaskReportingOptions();
    }

    public @NotNull String getTaskTypeName() {
        return taskTypeName;
    }

    @NotNull String getTaskOperationPrefix() {
        return taskOperationPrefix;
    }

    public @NotNull TaskManager getTaskManager() {
        return taskManager;
    }

    public @NotNull RepositoryService getRepositoryService() {
        return repositoryService;
    }

    public @NotNull PrismContext getPrismContext() {
        return prismContext;
    }

    @Override
    public @NotNull StatisticsCollectionStrategy getStatisticsCollectionStrategy() {
        return globalReportingOptions.getStatisticsCollectionStrategy();
    }

    /**
     * Main entry point.
     *
     * We basically delegate all the processing to a TaskExecution object.
     * Error handling is delegated to {@link TaskExceptionHandlingUtil#processException(Throwable, Trace, TaskPartDefinitionType, String, TaskRunResult)}
     * method.
     */
    @Override
    public TaskWorkBucketProcessingResult run(RunningTask localCoordinatorTask) {
        TE taskExecution = createTaskExecution(localCoordinatorTask);
        try {
            taskExecution.run();
            return processFinish(logger, null, taskTypeName, taskExecution.getCurrentRunResult(), taskExecution.getErrorState());
        } catch (Throwable t) {
            return processException(t, logger, null, taskTypeName, taskExecution.getCurrentRunResult());
        }
    }

    /**
     * Method to create the task execution. Can be overridden.
     */
    @NotNull
    protected TE createTaskExecution(RunningTask localCoordinatorTask) {
        return createTaskExecutionFromAnnotation(localCoordinatorTask);
    }

    @NotNull
    private TE createTaskExecutionFromAnnotation(RunningTask localCoordinatorTask) {
        try {
            TaskExecutionClass annotation = AnnotationSupportUtil.getRequiredAnnotation(this, TaskExecutionClass.class);
            Constructor<?> constructor = annotation.value().getDeclaredConstructor(this.getClass(), RunningTask.class);
            //noinspection unchecked
            return (TE) constructor.newInstance(this, localCoordinatorTask);
        } catch (Throwable t) {
            throw new SystemException("Cannot create task execution instance for " + this.getClass() + ": " + t.getMessage(), t);
        }
    }

    /** TODO decide what to do with this method. */
    private TE getCurrentTaskExecution(Task task) {
        return currentTaskExecutions.get(task.getOid());
    }

    /** TODO decide what to do with this method. */
    @Override
    public Long heartbeat(Task task) {
        // Delegate heartbeat to the result handler
        TE execution = getCurrentTaskExecution(task);
        if (execution != null) {
            return execution.heartbeat();
        } else {
            // most likely a race condition.
            return null;
        }
    }

    /** TODO decide what to do with this method. */
    @Override
    public void refreshStatus(Task task) {
        // Local task. No refresh needed. The Task instance has always fresh data.
    }

    public @NotNull TaskReportingOptions getGlobalReportingOptions() {
        return globalReportingOptions;
    }

    /** TODO decide what to do with this method. */
    void registerExecution(RunningTask localCoordinatorTask, TE execution) {
        currentTaskExecutions.put(localCoordinatorTask.getOid(), execution);
    }

    /** TODO decide what to do with this method. */
    void unregisterExecution(RunningTask localCoordinatorTask) {
        currentTaskExecutions.remove(localCoordinatorTask.getOid());
    }

    public @NotNull MatchingRuleRegistry getMatchingRuleRegistry() {
        return matchingRuleRegistry;
    }

    public @NotNull OperationExecutionRecorderForTasks getOperationExecutionRecorder() {
        return operationExecutionRecorder;
    }

    public @NotNull Trace getLogger() {
        return logger;
    }

    public Tracer getTracer() {
        return tracer;
    }

    public CacheConfigurationManager getCacheConfigurationManager() {
        return cacheConfigurationManager;
    }
}
