/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.qmodel.task;

import com.evolveum.midpoint.repo.sqale.qmodel.object.QObjectMapping;
import com.evolveum.midpoint.repo.sqlbase.SqlTransformerSupport;
import com.evolveum.midpoint.xml.ns._public.common.common_3.TaskType;

/**
 * Mapping between {@link QTask} and {@link TaskType}.
 */
public class QTaskMapping
        extends QObjectMapping<TaskType, QTask, MTask> {

    public static final String DEFAULT_ALIAS_NAME = "t";

    public static final QTaskMapping INSTANCE = new QTaskMapping();

    private QTaskMapping() {
        super(QTask.TABLE_NAME, DEFAULT_ALIAS_NAME,
                TaskType.class, QTask.class);

        addItemMapping(TaskType.F_TASK_IDENTIFIER, stringMapper(q -> q.taskIdentifier));
        addItemMapping(TaskType.F_BINDING, enumMapper(q -> q.binding));
        addItemMapping(TaskType.F_CATEGORY, stringMapper(q -> q.category));
        addItemMapping(TaskType.F_COMPLETION_TIMESTAMP,
                timestampMapper(q -> q.completionTimestamp));
        addItemMapping(TaskType.F_EXECUTION_STATUS, enumMapper(q -> q.executionStatus));
        // TODO byte[] fullResult mapping - probably does not make sense?
        addItemMapping(TaskType.F_HANDLER_URI, uriMapper(q -> q.handlerUriId));
        addItemMapping(TaskType.F_LAST_RUN_FINISH_TIMESTAMP,
                timestampMapper(q -> q.lastRunFinishTimestamp));
        addItemMapping(TaskType.F_LAST_RUN_START_TIMESTAMP,
                timestampMapper(q -> q.lastRunStartTimestamp));
        addItemMapping(TaskType.F_NODE, stringMapper(q -> q.node));
        addItemMapping(TaskType.F_OBJECT_REF, refMapper(
                q -> q.objectRefTargetOid,
                q -> q.objectRefTargetType,
                q -> q.objectRefRelationId));
        addItemMapping(TaskType.F_OWNER_REF, refMapper(
                q -> q.ownerRefTargetOid,
                q -> q.ownerRefTargetType,
                q -> q.ownerRefRelationId));
        addItemMapping(TaskType.F_PARENT, stringMapper(q -> q.parent));
        addItemMapping(TaskType.F_RECURRENCE, enumMapper(q -> q.recurrence));
        addItemMapping(TaskType.F_RESULT_STATUS, enumMapper(q -> q.resultStatus));
        addItemMapping(TaskType.F_THREAD_STOP_ACTION, enumMapper(q -> q.threadStopAction));
        addItemMapping(TaskType.F_WAITING_REASON, enumMapper(q -> q.waitingReason));
        // TODO dependentTaskIdentifiers String[] mapping not supported yet
    }

    @Override
    protected QTask newAliasInstance(String alias) {
        return new QTask(alias);
    }

    @Override
    public TaskSqlTransformer createTransformer(
            SqlTransformerSupport transformerSupport) {
        return new TaskSqlTransformer(transformerSupport, this);
    }

    @Override
    public MTask newRowObject() {
        return new MTask();
    }
}
