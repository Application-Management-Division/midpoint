/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.report.impl.controller.fileformat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.schema.expression.VariablesMap;
import com.evolveum.midpoint.task.api.RunningTask;

import j2html.TagCreator;
import j2html.tags.ContainerTag;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.model.api.authentication.CompiledObjectCollectionView;
import com.evolveum.midpoint.model.api.interaction.DashboardWidget;
import com.evolveum.midpoint.model.api.util.DashboardUtils;
import com.evolveum.midpoint.model.common.util.DefaultColumnUtils;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.report.impl.ReportServiceImpl;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.MiscSchemaUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

/**
 * Legacy code that deals with both exporting (dashboard- and collection-style) and importing reports.
 * Specialized to HTML files.
 *
 * TODO split into smaller, more specific classes
 *
 * @author skublik
 */
public class HtmlController extends FileFormatController {

    private static final Trace LOGGER = TraceManager.getTrace(HtmlController.class);

    private static final String REPORT_CSS_STYLE_FILE_NAME = "html-report-style.css";

    private static final String REPORT_GENERATED_ON = "Widget.generatedOn";
    private static final String NUMBER_OF_RECORDS = "Widget.numberOfRecords";

    public HtmlController(FileFormatConfigurationType fileFormatConfiguration, ReportType report, ReportServiceImpl reportService) {
        super(fileFormatConfiguration, report, reportService);
    }

    @Override
    public byte[] processDashboard(DashboardReportEngineConfigurationType dashboardConfig, RunningTask task, OperationResult result) throws Exception {
        ObjectReferenceType ref = dashboardConfig.getDashboardRef();
        Class<ObjectType> type = getReportService().getPrismContext().getSchemaRegistry().determineClassForType(ref.getType());
        DashboardType dashboard = (DashboardType) getReportService().getModelService()
                .getObject(type, ref.getOid(), null, task, result)
                .asObjectable();

        ClassLoader classLoader = getClass().getClassLoader();
        InputStream in = classLoader.getResourceAsStream(REPORT_CSS_STYLE_FILE_NAME);
        if (in == null) {
            throw new IllegalStateException("Resource " + REPORT_CSS_STYLE_FILE_NAME + " couldn't be found");
        }
        byte[] data = IOUtils.toByteArray(in);
        String cssStyle = new String(data, Charset.defaultCharset());

        StringBuilder body = new StringBuilder();
        body.append("<div> <style> ").append(cssStyle).append(" </style>");

        ContainerTag widgetTable = createTable();
        widgetTable.with(createTHead("Widget.", getHeadsOfWidget()));

        ContainerTag widgetTBody = TagCreator.tbody();
        List<ContainerTag> tableboxesFromWidgets = new ArrayList<>();
        long startMillis = getReportService().getClock().currentTimeMillis();
        long i = 1;
        task.setExpectedTotal((long) dashboard.getWidget().size());
        for (DashboardWidgetType widget : dashboard.getWidget()) {
            recordProgress(task, i, result, LOGGER);
            i++;
            if (widget == null) {
                throw new IllegalArgumentException("Widget in DashboardWidget is null");
            }
            DashboardWidget widgetData = getReportService().getDashboardService().createWidgetData(widget, task, result);
            widgetTBody.with(createTBodyRow(widgetData));
            if (!Boolean.TRUE.equals(dashboardConfig.isShowOnlyWidgetsTable())) {
                DashboardWidgetPresentationType presentation = widget.getPresentation();
                if (!DashboardUtils.isDataFieldsOfPresentationNullOrEmpty(presentation)) {
                    ContainerTag tableBox = null;
                    DashboardWidgetSourceTypeType sourceType = DashboardUtils.getSourceType(widget);
                    if (sourceType == null) {
                        throw new IllegalStateException("No source type specified in " + widget);
                    }
                    CollectionRefSpecificationType collectionRefSpecification = getReportService()
                            .getDashboardService().getCollectionRefSpecificationType(widget, task, result);
                    ObjectCollectionType collection = getReportService().getDashboardService().getObjectCollectionType(widget, task, result);

                    CompiledObjectCollectionView compiledCollection = new CompiledObjectCollectionView();
                    if (collection != null) {
                        getReportService().getModelInteractionService().applyView(compiledCollection, collection.getDefaultView());
                    } else if (collectionRefSpecification.getBaseCollectionRef() != null
                            && collectionRefSpecification.getBaseCollectionRef().getCollectionRef() != null) {
                        ObjectCollectionType baseCollection = (ObjectCollectionType) getReportService()
                                .getObjectFromReference(collectionRefSpecification.getBaseCollectionRef().getCollectionRef()).asObjectable();
                        getReportService().getModelInteractionService().applyView(compiledCollection, baseCollection.getDefaultView());
                    }

                    if (widget.getPresentation() != null && widget.getPresentation().getView() != null) {
                        getReportService().getModelInteractionService().applyView(compiledCollection, widget.getPresentation().getView());
                    }

                    QName collectionType = getReportService().resolveTypeQNameForReport(collectionRefSpecification, compiledCollection);
                    GuiObjectListViewType reportView = getReportViewByType(dashboardConfig, collectionType);
                    if (reportView != null) {
                        getReportService().getModelInteractionService().applyView(compiledCollection, reportView);
                    }

                    switch (sourceType) {
                        case AUDIT_SEARCH:
                            if (collection == null) {
                                LOGGER.error("CollectionRef is null for report of audit records");
                                throw new IllegalArgumentException("CollectionRef is null for report of audit records");
                            }
                        case OBJECT_COLLECTION:
                            tableBox = createTableBox(widgetData.getLabel(getReportService().getLocalizationService()), collectionRefSpecification, compiledCollection,
                                    null, Collections.emptyList(), result, false, task);
                            break;
                    }
                    if (tableBox != null) {
                        tableboxesFromWidgets.add(tableBox);
                    }
                }
            }
        }
        widgetTable.with(widgetTBody);

        body.append(createTableBox(widgetTable, "Widgets", dashboard.getWidget().size(),
                convertMillisToString(startMillis), null).render());
        appendNewLine(body);
        tableboxesFromWidgets.forEach(table -> {
            body.append(table.render());
            appendNewLine(body);
        });
        body.append("</div>");

        return body.toString().getBytes();
    }

    private GuiObjectListViewType getReportViewByType(DashboardReportEngineConfigurationType dashboardConfig, QName type) {
        for (GuiObjectListViewType view : dashboardConfig.getView()) {
            if (QNameUtil.match(view.getType(), type)) {
                return view;
            }
        }
        return null;
    }

    @Override
    public byte[] processCollection(String nameOfReport, ObjectCollectionReportEngineConfigurationType collectionConfig,
            RunningTask task, OperationResult result) throws CommonException, IOException {
        CollectionRefSpecificationType collectionRefSpecification = collectionConfig.getCollection();
        ObjectReferenceType ref = collectionRefSpecification.getCollectionRef();
        ObjectCollectionType collection = null;
        if (ref != null) {
            Class<ObjectType> type = getReportService().getPrismContext().getSchemaRegistry().determineClassForType(ref.getType());
            collection = (ObjectCollectionType) getReportService().getModelService()
                    .getObject(type, ref.getOid(), null, task, result)
                    .asObjectable();
        }

        initializeParameters(collectionConfig.getParameter(), task);

        ClassLoader classLoader = getClass().getClassLoader();
        InputStream in = classLoader.getResourceAsStream(REPORT_CSS_STYLE_FILE_NAME);
        if (in == null) {
            throw new IllegalStateException("Resource " + REPORT_CSS_STYLE_FILE_NAME + " couldn't be found");
        }
        byte[] data = IOUtils.toByteArray(in);
        String cssStyle = new String(data, Charset.defaultCharset());

        StringBuilder body = new StringBuilder();
        body.append("<div> <style> ").append(cssStyle).append(" </style>");

        CompiledObjectCollectionView compiledCollection = new CompiledObjectCollectionView();
        String defaultName = nameOfReport;
        if (collection != null) {
            if (!Boolean.TRUE.equals(collectionConfig.isUseOnlyReportView())) {
                getReportService().getModelInteractionService().applyView(compiledCollection, collection.getDefaultView());
            }
            defaultName = collection.getName().getOrig();
        } else if (collectionRefSpecification.getBaseCollectionRef() != null
                && collectionRefSpecification.getBaseCollectionRef().getCollectionRef() != null) {
            ObjectCollectionType baseCollection = (ObjectCollectionType) getReportService()
                    .getObjectFromReference(collectionRefSpecification.getBaseCollectionRef().getCollectionRef()).asObjectable();
            if (!Boolean.TRUE.equals(collectionConfig.isUseOnlyReportView())) {
                getReportService().getModelInteractionService().applyView(compiledCollection, baseCollection.getDefaultView());
            }
            defaultName = baseCollection.getName().getOrig();
        }

        GuiObjectListViewType reportView = collectionConfig.getView();
        if (reportView != null) {
            getReportService().getModelInteractionService().applyView(compiledCollection, reportView);
        }

        String label;
        if (compiledCollection.getDisplay() != null && StringUtils.isEmpty(compiledCollection.getDisplay().getLabel().getOrig())) {
            label = compiledCollection.getDisplay().getLabel().getOrig();
        } else {
            label = defaultName;
        }

        ContainerTag tableBox = createTableBox(label, collectionRefSpecification, compiledCollection,
                collectionConfig.getCondition(), collectionConfig.getSubreport(),result, true, task);

        body.append(tableBox.render());

        body.append("</div>");

        return body.toString().getBytes();
    }

    private ContainerTag createTable() {
        return TagCreator.table().withClasses("table", "table-striped", "table-hover", "table-bordered");
    }

    private ContainerTag createTableBox(ContainerTag table, String nameOfTable, int countOfTableRecords,
            String createdTime, DisplayType display) {
        ContainerTag div = TagCreator.div().withClasses("box-body", "no-padding").with(TagCreator.h1(nameOfTable))
                .with(TagCreator.p(GenericSupport.getMessage(localizationService, REPORT_GENERATED_ON, createdTime)))
                .with(TagCreator.p(GenericSupport.getMessage(localizationService, NUMBER_OF_RECORDS, countOfTableRecords))).with(table);
        String style = "";
        String classes = "";
        if (display != null) {
            if (display.getCssStyle() != null) {
                style = display.getCssStyle();
            }
            if (display.getCssClass() != null) {
                classes = display.getCssClass();
            }
        }
        return TagCreator.div().withClasses("box", "boxed-table", classes).withStyle(style).with(div);
    }

    private ContainerTag createTableBox(String tableLabel, CollectionRefSpecificationType collection, @NotNull CompiledObjectCollectionView compiledCollection,
            ExpressionType condition, List<SubreportParameterType> subreports, OperationResult result, boolean recordProgress, RunningTask task)
            throws ObjectNotFoundException, SchemaException, CommunicationException,
            ConfigurationException, SecurityViolationException, ExpressionEvaluationException {
        long startMillis = getReportService().getClock().currentTimeMillis();
        Class<Containerable> type = getReportService().resolveTypeForReport(compiledCollection);
        Collection<SelectorOptions<GetOperationOptions>> options = DefaultColumnUtils.createOption(type, getReportService().getSchemaService());
        PrismContainerDefinition<Containerable> def = getReportService().getPrismContext().getSchemaRegistry()
                .findItemDefinitionByCompileTimeClass(type, PrismContainerDefinition.class);

        ContainerTag table = createTable();
        ContainerTag tHead = TagCreator.thead();
        ContainerTag tBody = TagCreator.tbody();
        List<GuiObjectColumnType> columns;
        if (compiledCollection.getColumns().isEmpty()) {
            columns = MiscSchemaUtil.orderCustomColumns(DefaultColumnUtils.getDefaultView(type).getColumn());
        } else {
            columns = MiscSchemaUtil.orderCustomColumns(compiledCollection.getColumns());
        }
        ContainerTag trForHead = TagCreator.tr().withStyle("width: 100%;");

        columns.forEach(column -> {
            Validate.notNull(column.getName(), "Name of column is null");

            String label = getColumnLabel(column, def);
            DisplayType columnDisplay = column.getDisplay();
            ContainerTag th = TagCreator.th(TagCreator.div(TagCreator.span(label).withClass("sortableLabel")));
            if (columnDisplay != null) {
                if (StringUtils.isNotBlank(columnDisplay.getCssClass())) {
                    th.withClass(columnDisplay.getCssClass());
                }
                if (StringUtils.isNotBlank(columnDisplay.getCssStyle())) {
                    th.withStyle(columnDisplay.getCssStyle());
                }
            }
            trForHead.with(th);

        });
        tHead.with(trForHead);
        table.with(tHead);

        AtomicInteger index = new AtomicInteger(1);
        Predicate<PrismContainer> handler = (value) -> {
            if (!task.canRun()) {
                return false;
            }
            index.getAndIncrement();
            if (recordProgress) {
                recordProgress(task, index.get(), result, LOGGER);
            }
            boolean writeRecord = true;
            if (condition != null) {
                try {
                    writeRecord = evaluateCondition(condition, value, task, result);
                } catch (Exception e) {
                    LOGGER.error("Couldn't evaluate condition for report record " + value);
                    return false;
                }
            }
            if (writeRecord) {
                ContainerTag tr = TagCreator.tr();
                evaluateSubreportParameters(subreports, value, task);
                columns.forEach(column -> {
                    ItemPath path = column.getPath() == null ? null : column.getPath().getItemPath();
                    ExpressionType expression = column.getExport() != null ? column.getExport().getExpression() : null;
                    tr.with(TagCreator
                            .th(TagCreator.div(getRealValueAsString(column, value, path, expression, task, result))
                                    .withStyle("white-space: pre-wrap")));
                });
                tBody.with(tr);
            }
            cleanUpVariables();
            return true;
        };
        searchObjectFromCollection(collection, compiledCollection.getContainerType(), handler, options,
                task, result, recordProgress);
        if (tBody.getNumChildren() == 0 && !recordProgress) {
            return null;
        }
        table.with(tBody);
        DisplayType display = compiledCollection.getDisplay();
        return createTableBox(table, tableLabel, (index.get() - 1),
                convertMillisToString(startMillis), display);
    }

    private ContainerTag createTHead(String prefix, Set<String> set) {
        return TagCreator.thead(TagCreator.tr(TagCreator.each(set,
                header -> TagCreator.th(TagCreator.div(TagCreator.span(
                        GenericSupport.getMessage(localizationService, prefix + header)).withClass("sortableLabel")))))
                .withStyle("width: 100%;"));
    }

    private ContainerTag createTBodyRow(DashboardWidget data) {
        return TagCreator.tr(
                TagCreator.each(getHeadsOfWidget(),
                        header -> getContainerTagForWidgetHeader(header, data)));

    }

    private ContainerTag getContainerTagForWidgetHeader(String header, DashboardWidget data) {
        if (header.equals(LABEL_COLUMN)) {
            ContainerTag div = TagCreator.div(data.getLabel(getReportService().getLocalizationService()));
            return TagCreator.th().with(div);
        }
        if (header.equals(NUMBER_COLUMN)) {
            ContainerTag div = TagCreator.div(data.getNumberMessage());
            return TagCreator.th().with(div);
        }
        if (header.equals(STATUS_COLUMN)) {
            ContainerTag div = TagCreator.div().withStyle("width: 100%; height: 20px; ");
            ContainerTag th = TagCreator.th();
            if (data.getDisplay() != null && StringUtils.isNoneBlank(data.getDisplay().getColor())) {
                th.withStyle("background-color: " + data.getDisplay().getColor() + " !important;");
            }
            th.with(div);
            return th;
        }
        return TagCreator.th();
    }

    private String convertMillisToString(long millis) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, d. MMM yyyy HH:mm:ss", Locale.getDefault());
        return dateFormat.format(millis);
    }

    private void appendNewLine(StringBuilder body) {
        body.append(getMultivalueDelimiter());
    }

    protected String getMultivalueDelimiter(){
        return "<br>";
    }

    protected void appendMultivalueDelimiter(StringBuilder body) {
        appendNewLine(body);
    }

    @Override
    public void importCollectionReport(ReportType report, VariablesMap listOfVariables, RunningTask task, OperationResult result) {
        throw new UnsupportedOperationException("Unsupported operation import for HTML file format");
    }

    @Override
    public List<VariablesMap> createVariablesFromFile(ReportType report, ReportDataType reportData,
            boolean useImportScript, Task task, OperationResult result) {
        throw new UnsupportedOperationException("Unsupported operation import for HTML file format");
    }

    @Override
    public String getTypeSuffix() {
        return ".html";
    }

    @Override
    public String getType() {
        return "HTML";
    }
}
