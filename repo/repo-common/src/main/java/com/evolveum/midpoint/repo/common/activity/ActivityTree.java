/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.repo.common.activity;

import com.evolveum.midpoint.repo.common.activity.definition.ActivityDefinition;
import com.evolveum.midpoint.repo.common.activity.definition.WorkDefinition;
import com.evolveum.midpoint.repo.common.activity.handlers.ActivityHandler;
import com.evolveum.midpoint.repo.common.activity.state.ActivityTreePurger;
import com.evolveum.midpoint.repo.common.task.CommonTaskBeans;
import com.evolveum.midpoint.repo.common.task.task.GenericTaskExecution;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.task.ActivityPath;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.DebugDumpable;

import com.evolveum.midpoint.util.exception.SchemaException;

import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivityTreeRealizationStateType;

import org.jetbrains.annotations.NotNull;

/**
 * Represents the tree of activities that comprise a logical task.
 */
public class ActivityTree implements DebugDumpable {

    /**
     * The root activity. Children are referenced from it.
     */
    @NotNull private final StandaloneActivity<?, ?> rootActivity;

    @NotNull private final ActivityTreeStateOverview treeStateOverview;

    @NotNull private final CommonTaskBeans beans;

    private <WD extends WorkDefinition, AH extends ActivityHandler<WD, AH>> ActivityTree(
            @NotNull ActivityDefinition<WD> rootDefinition,
            @NotNull Task rootTask,
            @NotNull CommonTaskBeans beans) {
        AH handler = beans.activityHandlerRegistry.getHandler(rootDefinition);
        this.rootActivity = StandaloneActivity.createRoot(rootDefinition, handler, this);
        this.beans = beans;
        this.treeStateOverview = new ActivityTreeStateOverview(rootTask, beans);
    }

    public static ActivityTree create(Task rootTask, CommonTaskBeans beans) throws SchemaException {
        ActivityDefinition<?> rootDefinition = ActivityDefinition.createRoot(rootTask, beans);
        return new ActivityTree(rootDefinition, rootTask, beans);
    }

    @NotNull
    public Activity<?, ?> getRootActivity() {
        return rootActivity;
    }

    @NotNull
    public CommonTaskBeans getBeans() {
        return beans;
    }

    @Override
    public String toString() {
        return "ActivityTree{" +
                "rootActivity=" + rootActivity +
                '}';
    }

    @Override
    public String debugDump(int indent) {
        return rootActivity.debugDump(indent);
    }

    @NotNull
    public Activity<?, ?> getActivity(ActivityPath path) throws SchemaException {
        Activity<?, ?> current = rootActivity;
        for (String identifier : path.getIdentifiers()) {
            current = current.getChild(identifier);
        }
        return current;
    }

    public @NotNull ActivityTreeStateOverview getTreeStateOverview() {
        return treeStateOverview;
    }

    public ActivityTreeRealizationStateType getRealizationState() {
        return treeStateOverview.getRealizationState();
    }

    public void updateRealizationState(ActivityTreeRealizationStateType value, OperationResult result)
            throws ActivityExecutionException {
        treeStateOverview.updateRealizationState(value, result);
    }

    /** Purges the activity state (usually before new realization). */
    public void purgeState(GenericTaskExecution taskExecution, OperationResult result) throws ActivityExecutionException {
        purgeTreeStateOverview(result);
        purgeDetailedStateAndTaskStatistics(taskExecution, result);
    }

    private void purgeTreeStateOverview(OperationResult result) throws ActivityExecutionException {
        treeStateOverview.purge(result);
    }

    /**
     * Purges detailed state of the activities: including worker and delegator tasks!
     */
    private void purgeDetailedStateAndTaskStatistics(GenericTaskExecution taskExecution, OperationResult result)
            throws ActivityExecutionException {
        new ActivityTreePurger(taskExecution, beans)
                .purge(result);
    }
}
