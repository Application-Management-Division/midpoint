/*
 * Copyright (c) 2010-2017 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.notifications.impl.notifiers;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.PrismReference;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.repo.common.ObjectResolver;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.model.api.ModelService;
import com.evolveum.midpoint.notifications.api.events.TaskEvent;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.report.api.ReportConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;

/**
 * @author skublik
 */
@Component
public class SimpleReportNotifier extends AbstractGeneralNotifier<TaskEvent, SimpleReportNotifierType> {

    private static final Trace LOGGER = TraceManager.getTrace(SimpleReportNotifier.class);

    private static final String REPORT_TASK_URI = "http://midpoint.evolveum.com/xml/ns/public/report/handler-3";

    @Autowired private ModelService modelService;
    @Autowired private ObjectResolver resolver;

    @Override
    public Class<TaskEvent> getEventType() {
        return TaskEvent.class;
    }

    @Override
    public Class<SimpleReportNotifierType> getEventHandlerConfigurationType() {
        return SimpleReportNotifierType.class;
    }

    @Override
    protected boolean checkApplicability(TaskEvent event, SimpleReportNotifierType generalNotifierType, OperationResult result) {
        if (!event.isSuccess()) {
            LOGGER.trace("Operation was not successful, exiting.");
            return false;
        } else if (!event.isFinished()) {
            LOGGER.trace("No report output oid present in task. Skip sending notifications.");
            return false;
        }

        return true;
    }

    @Override
    protected boolean quickCheckApplicability(TaskEvent event, SimpleReportNotifierType configuration, OperationResult result) {
        @NotNull Task task = event.getTask();
        if (task.getHandlerUri() != null && !task.getHandlerUri().equals(REPORT_TASK_URI) && !isReportTask(task)) {
            LOGGER.trace("{} is not applicable for this kind of event, continuing in the handler chain; event class = {}",
                    getClass().getSimpleName(), event.getClass());
            return false;
        } else {
            return true;
        }
    }

    private boolean isReportTask(Task task) {
        ActivityDefinitionType activity = task.getRootActivityDefinitionOrClone();
        if (activity == null) {
            return false;
        }
        WorkDefinitionsType workDef = activity.getWork();
        if (workDef == null) {
            return false;
        }
        return workDef.getReportExport() != null || workDef.getDistributedReportExport() != null;
    }

    @Override
    protected String getSubject(TaskEvent event, SimpleReportNotifierType configuration, String transport, Task task, OperationResult result) {
        final String taskName = PolyString.getOrig(event.getTask().getName());

        if (event.isAdd()) {
            return "Task '" + taskName + "' start notification";
        } else if (event.isDelete()) {
            return "Task '" + taskName + "' finish notification: " + event.getOperationResultStatus();
        } else {
            return "(unknown " + taskName + " operation)";
        }
    }

    @Override
    protected List<NotificationMessageAttachmentType> getAttachment(TaskEvent event, SimpleReportNotifierType generalNotifierType,
            String transportName, Task task, OperationResult result) {

        String outputOid = getReportDataOid(event.getTask());

        if (outputOid == null || outputOid.isEmpty()) {
            throw new IllegalStateException("Unexpected oid of report output, oid is null or empty");
        }

        PrismObject<ReportDataType> reportOutput;
        try {
            reportOutput = modelService.getObject(ReportDataType.class, outputOid, null, task, result);
        } catch (ObjectNotFoundException | SecurityViolationException | CommunicationException | ConfigurationException
                | ExpressionEvaluationException | SchemaException e) {
            getLogger().error("Couldn't get Report output with oid " + outputOid, e);
            throw new SystemException("Couldn't get report output " + outputOid, e);
        }

        NotificationMessageAttachmentType attachment = new NotificationMessageAttachmentType();
        String type;
        String filePath = reportOutput.asObjectable().getFilePath();
        if (reportOutput.asObjectable().getFileFormat() != null) {
            type = reportOutput.asObjectable().getFileFormat().value().toLowerCase();
        } else {
            type = FilenameUtils.getExtension(filePath);
        }
        if (StringUtils.isBlank(type)) {
            type = "plain";
        }
        attachment.setContentType("text/" + type);
        attachment.setContentFromFile(filePath);
        List attachments = new ArrayList();
        attachments.add(attachment);
        return attachments;
    }

    @Override
    protected String getBody(TaskEvent event, SimpleReportNotifierType configuration, String transport, Task opTask, OperationResult opResult) throws SchemaException {

        Task task = event.getTask();
        PrismObject<ReportType> report = getReportFromTask(task, opTask, opResult);

        StringBuilder body = new StringBuilder();

        body.append("Notification about creating of report.\n\n");
        body.append("Report: ").append(report.getName()).append("\n\n");
        body.append("You can see report output in attachment.").append("\n");
        return body.toString();
    }

    private PrismObject<ReportType> getReportFromTask(Task task, Task opTask, OperationResult opResult) {
        try {
            if (isReportTask(task)) {
                ObjectReferenceType ref;
                if (task.getRootActivityDefinitionOrClone().getWork().getReportExport() != null) {
                    ref = task.getRootActivityDefinitionOrClone().getWork().getReportExport().getReportRef();
                } else {
                    ref = task.getRootActivityDefinitionOrClone().getWork().getDistributedReportExport().getReportRef();
                }
                return resolver.resolve(
                        ref,
                        ReportType.class,
                        null,
                        "resolving report",
                        opTask,
                        opResult
                        ).asPrismContainer();
            }
            return task.getObject(ReportType.class, opResult);
        } catch (CommonException e) {
            getLogger().error("Couldn't get Report from task " + task.debugDump(), e);
            throw new SystemException("Could't get Report from task " + task.debugDump(), e);
        }
    }

    private String getReportDataOid(Task task) {
        PrismReference reportData = task.getExtensionReferenceOrClone(ReportConstants.REPORT_DATA_PROPERTY_NAME);
        if (reportData == null || reportData.getRealValue() == null) {
            String reportOutputOid = task.getExtensionPropertyRealValue(ReportConstants.REPORT_OUTPUT_OID_PROPERTY_NAME);
            if (reportOutputOid == null) {
                return null;
            }
            return reportOutputOid;
        }

        return reportData.getRealValue().getOid();
    }

    @Override
    protected Trace getLogger() {
        return LOGGER;
    }
}
