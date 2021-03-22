/*
 * Copyright (c) 2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.test.asserter;

import javax.xml.namespace.QName;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.schema.util.task.TaskTreeUtil;
import com.evolveum.midpoint.schema.util.task.TaskWorkStateUtil;
import com.evolveum.midpoint.test.IntegrationTestTools;
import com.evolveum.midpoint.test.util.TestUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.AssertJUnit.assertEquals;

public class TaskAsserter<RA> extends AssignmentHolderAsserter<TaskType, RA> {

    private TaskAsserter(PrismObject<TaskType> object) {
        super(object);
    }

    private TaskAsserter(PrismObject<TaskType> object, String details) {
        super(object, details);
    }

    private TaskAsserter(PrismObject<TaskType> object, RA returnAsserter, String details) {
        super(object, returnAsserter, details);
    }

    @SuppressWarnings("unused")
    public static TaskAsserter<Void> forTask(PrismObject<TaskType> object) {
        return new TaskAsserter<>(object);
    }

    public static TaskAsserter<Void> forTask(PrismObject<TaskType> object, String details) {
        return new TaskAsserter<>(object, details);
    }

    // It is insane to override all those methods from superclass.
    // But there is no better way to specify something like <SELF> type in Java.
    // This is lesser evil.
    @Override
    public TaskAsserter<RA> assertOid() {
        super.assertOid();
        return this;
    }

    @Override
    public TaskAsserter<RA> assertOid(String expected) {
        super.assertOid(expected);
        return this;
    }

    @Override
    public TaskAsserter<RA> assertOidDifferentThan(String oid) {
        super.assertOidDifferentThan(oid);
        return this;
    }

    @Override
    public TaskAsserter<RA> assertName() {
        super.assertName();
        return this;
    }

    @Override
    public TaskAsserter<RA> assertName(String expectedOrig) {
        super.assertName(expectedOrig);
        return this;
    }

    @Override
    public TaskAsserter<RA> assertDescription(String expected) {
        super.assertDescription(expected);
        return this;
    }

    @Override
    public TaskAsserter<RA> assertNoDescription() {
        super.assertNoDescription();
        return this;
    }

    @Override
    public TaskAsserter<RA> assertSubtype(String... expected) {
        super.assertSubtype(expected);
        return this;
    }

    @Override
    public TaskAsserter<RA> assertTenantRef(String expectedOid) {
        super.assertTenantRef(expectedOid);
        return this;
    }

    @Override
    public TaskAsserter<RA> assertLifecycleState(String expected) {
        super.assertLifecycleState(expected);
        return this;
    }

    @Override
    public TaskAsserter<RA> assertActiveLifecycleState() {
        super.assertActiveLifecycleState();
        return this;
    }

    public TaskAsserter<RA> display() {
        super.display();
        return this;
    }

    public TaskAsserter<RA> displayOperationResult() {
        OperationResultType resultBean = getTaskBean().getResult();
        if (resultBean != null) {
            IntegrationTestTools.display(desc() + " operation result:\n" + OperationResult.createOperationResult(resultBean).debugDump(1));
        } else {
            IntegrationTestTools.display(desc() + " has no operation result");
        }
        return this;
    }

    public TaskAsserter<RA> display(String message) {
        super.display(message);
        return this;
    }

    @Override
    public TaskAsserter<RA> assertArchetypeRef(String expectedArchetypeOid) {
        return (TaskAsserter<RA>) super.assertArchetypeRef(expectedArchetypeOid);
    }

    @Override
    public TaskAsserter<RA> assertNoItem(ItemPath itemPath) {
        super.assertNoItem(itemPath);
        return this;
    }

    public TaskAsserter<RA> assertProgress(long expected) {
        long actual = defaultIfNull(getObject().asObjectable().getProgress(), 0L);
        assertEquals("Wrong progress", expected, actual);
        return this;
    }

    public TaskAsserter<RA> assertToken(Object expected) {
        assertPropertyEquals(ItemPath.create(TaskType.F_EXTENSION, SchemaConstants.SYNC_TOKEN), expected);
        return this;
    }

    @Override
    public TaskAsserter<RA> assertPolyStringProperty(QName propName, String expectedOrig) {
        return (TaskAsserter<RA>) super.assertPolyStringProperty(propName, expectedOrig);
    }

    public SynchronizationInfoAsserter<TaskAsserter<RA>> synchronizationInformation() {
        OperationStatsType operationStats = getObject().asObjectable().getOperationStats();
        SynchronizationInformationType information = operationStats != null ?
                operationStats.getSynchronizationInformation() : new SynchronizationInformationType();
        SynchronizationInfoAsserter<TaskAsserter<RA>> asserter = new SynchronizationInfoAsserter<>(information, this, getDetails());
        copySetupTo(asserter);
        return asserter;
    }

    public IterativeTaskInfoAsserter<TaskAsserter<RA>> iterativeTaskInformation() {
        OperationStatsType operationStats = getObject().asObjectable().getOperationStats();
        IterativeTaskInformationType information = operationStats != null ?
                operationStats.getIterativeTaskInformation() : new IterativeTaskInformationType();
        IterativeTaskInfoAsserter<TaskAsserter<RA>> asserter = new IterativeTaskInfoAsserter<>(information, this, getDetails());
        copySetupTo(asserter);
        return asserter;
    }

    public StructuredTaskProgressAsserter<TaskAsserter<RA>> structuredProgress() {
        StructuredTaskProgressType progress = getObject().asObjectable().getStructuredProgress();
        if (progress == null) {
            progress = new StructuredTaskProgressType(getPrismContext());
        }
        StructuredTaskProgressAsserter<TaskAsserter<RA>> asserter = new StructuredTaskProgressAsserter<>(progress, this, getDetails());
        copySetupTo(asserter);
        return asserter;
    }

    public ActionsExecutedInfoAsserter<TaskAsserter<RA>> actionsExecutedInformation() {
        OperationStatsType operationStats = getObject().asObjectable().getOperationStats();
        ActionsExecutedInformationType information = operationStats != null ?
                operationStats.getActionsExecutedInformation() : new ActionsExecutedInformationType();
        ActionsExecutedInfoAsserter<TaskAsserter<RA>> asserter = new ActionsExecutedInfoAsserter<>(information, this, getDetails());
        copySetupTo(asserter);
        return asserter;
    }

    public TaskAsserter<RA> assertClosed() {
        return assertExecutionStatus(TaskExecutionStateType.CLOSED);
    }

    public TaskAsserter<RA> assertExecutionStatus(TaskExecutionStateType status) {
        assertEquals("Wrong execution status", status, getTaskBean().getExecutionStatus());
        return this;
    }

    public TaskAsserter<RA> assertSchedulingState(TaskSchedulingStateType state) {
        assertEquals("Wrong scheduling state", state, getTaskBean().getSchedulingState());
        return this;
    }

    private TaskType getTaskBean() {
        return getObject().asObjectable();
    }

    public TaskAsserter<RA> assertSuccess() {
        TestUtil.assertSuccess(getTaskBean().getResult());
        return this;
    }

    public TaskAsserter<RA> assertPartialError() {
        TestUtil.assertPartialError(getTaskBean().getResult());
        return this;
    }

    public TaskAsserter<RA> assertFatalError() {
        TestUtil.assertFatalError(getTaskBean().getResult());
        return this;
    }

    public TaskAsserter<RA> assertCategory(String category) {
        assertEquals(category, getTaskBean().getCategory());
        return this;
    }

    public TaskAsserter<RA> assertBinding(TaskBindingType binding) {
        assertEquals(binding, getTaskBean().getBinding());
        return this;
    }

    @Override
    public AssignmentsAsserter<TaskType, TaskAsserter<RA>, RA> assignments() {
        AssignmentsAsserter<TaskType, TaskAsserter<RA>, RA> asserter = new AssignmentsAsserter<>(this, getDetails());
        copySetupTo(asserter);
        return asserter;
    }

    public ObjectReferenceAsserter<UserType, RA> owner() {
        ObjectReferenceAsserter<UserType, RA> ownerAsserter = new ObjectReferenceAsserter<>(getTaskBean().getOwnerRef().asReferenceValue(), UserType.class);
        copySetupTo(ownerAsserter);
        return ownerAsserter;
    }

    public TaskAsserter<TaskAsserter<RA>> subtaskForPart(int number) {
        TaskType subtask = TaskTreeUtil.getAllTasksStream(getObjectable())
                .filter(t -> Integer.valueOf(number).equals(TaskWorkStateUtil.getPartitionSequentialNumber(t)))
                .findAny().orElse(null);
        assertThat(subtask).withFailMessage(() -> "No subtask for part " + number + " found").isNotNull();

        TaskAsserter<TaskAsserter<RA>> asserter = new TaskAsserter<>(subtask.asPrismObject(), this, "subtask for part " +
                number + " in " + getDetails());
        copySetupTo(asserter);
        return asserter;
    }

    public TaskAsserter<TaskAsserter<RA>> subtask(int index) {
        TaskType subtask = (TaskType) ObjectTypeUtil.getObjectFromReference(getObjectable().getSubtaskRef().get(index));
        assertThat(subtask).withFailMessage(() -> "No subtask #" + index + " found").isNotNull();

        TaskAsserter<TaskAsserter<RA>> asserter = new TaskAsserter<>(subtask.asPrismObject(), this,
                "subtask #" + index + " in " + getDetails());
        copySetupTo(asserter);
        return asserter;
    }
}
