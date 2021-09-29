/*
 * Copyright (c) 2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.gui.impl.page.admin.report.component;

import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.impl.page.admin.component.AssignmentHolderOperationalButtonsPanel;

import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.web.component.AjaxCompositedIconSubmitButton;
import com.evolveum.midpoint.web.page.admin.reports.PageReports;
import com.evolveum.midpoint.web.page.admin.reports.component.ImportReportPopupPanel;
import com.evolveum.midpoint.web.page.admin.reports.component.ReportObjectsListPanel;
import com.evolveum.midpoint.web.page.admin.reports.component.RunReportPopupPanel;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import com.evolveum.midpoint.gui.api.GuiStyleConstants;
import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismObjectWrapper;
import com.evolveum.midpoint.gui.impl.component.AjaxCompositedIconButton;
import com.evolveum.midpoint.gui.impl.component.icon.CompositedIconBuilder;
import com.evolveum.midpoint.gui.impl.component.icon.IconCssStyle;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.web.component.util.VisibleBehaviour;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.apache.wicket.model.StringResourceModel;

public abstract class ReportOperationalButtonsPanel extends AssignmentHolderOperationalButtonsPanel<ReportType> {

    private static final String DOT_CLASS = PageReports.class.getName() + ".";
    private static final String OPERATION_RUN_REPORT = DOT_CLASS + "runReport";
    private static final String OPERATION_IMPORT_REPORT = DOT_CLASS + "importReport";
    private static final String ID_REPORT_BUTTONS = "reportButtons";

    private final IModel<Boolean> isShowingPreview = Model.of(Boolean.FALSE);

    public ReportOperationalButtonsPanel(String id, LoadableModel<PrismObjectWrapper<ReportType>> model) {
        super(id, model);
    }

    @Override
    protected void addButtons(RepeatingView repeatingView) {
        initSaveAndRunButton(repeatingView);
        super.addButtons(repeatingView);
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        initReportButtons();
    }

    private void initReportButtons() {
        RepeatingView reportButtons = new RepeatingView(ID_REPORT_BUTTONS);
        initOperationalButtons(reportButtons);
        add(reportButtons);
    }

    private void initSaveAndRunButton(RepeatingView repeatingView) {
        IconType iconType = new IconType();
        iconType.setCssClass(GuiStyleConstants.CLASS_PLAY);
        CompositedIconBuilder iconBuilder = new CompositedIconBuilder()
                .setBasicIcon(GuiStyleConstants.CLASS_ICON_SAVE, IconCssStyle.IN_ROW_STYLE);
//                .appendLayerIcon(iconType, IconCssStyle.BOTTOM_RIGHT_STYLE);
        AjaxCompositedIconSubmitButton saveAndRunButton = new AjaxCompositedIconSubmitButton(repeatingView.newChildId(), iconBuilder.build(),
                createStringResource("pageReport.button.saveAndRun")) {

            private static final long serialVersionUID = 1L;

            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                saveAndRunPerformed(target);
            }

            @Override
            protected void onError(AjaxRequestTarget target) {
                target.add(getPageBase().getFeedbackPanel());
            }
        };
        saveAndRunButton.titleAsLabel(true);
        saveAndRunButton.add(new VisibleBehaviour(() -> !getModelObject().isReadOnly()));
        saveAndRunButton.setOutputMarkupId(true);
        saveAndRunButton.setOutputMarkupPlaceholderTag(true);
        saveAndRunButton.add(AttributeAppender.append("class", "btn-primary btn-sm"));
        repeatingView.add(saveAndRunButton);
    }

    private void initOperationalButtons(RepeatingView repeatingView) {
        String refreshId = repeatingView.newChildId();
        String showPreviewId = repeatingView.newChildId();
        String showPreviewInPopupId = repeatingView.newChildId();

        IconType iconType = new IconType();
        iconType.setCssClass("fa fa-address-card-o");
        CompositedIconBuilder iconBuilder = new CompositedIconBuilder()
                .setBasicIcon("fa fa-refresh", IconCssStyle.IN_ROW_STYLE)
                .appendLayerIcon(iconType, IconCssStyle.BOTTOM_RIGHT_STYLE);
        AjaxCompositedIconButton refresh = new AjaxCompositedIconButton(refreshId, iconBuilder.build(),
                createStringResource("pageCreateCollectionReport.button.refresh")) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                ReportObjectsListPanel<?> table = getReportTable();
                table.getPageStorage().setSearch(null);
                table.resetSearchModel();
                table.resetTable(target);
                table.refreshTable(target);
                table.checkView();
                target.add(table);
                target.add(getTableBox());
                target.add(getTableContainer());
                target.add(getPageBase().getFeedbackPanel());
            }
        };
        refresh.add(new VisibleBehaviour(isShowingPreview::getObject));
        refresh.add(AttributeAppender.append("class", "btn-default btn-sm"));
        refresh.setOutputMarkupId(true);
        repeatingView.add(refresh);

        iconBuilder = new CompositedIconBuilder()
                .setBasicIcon("fa fa-address-card-o", IconCssStyle.IN_ROW_STYLE);
        AjaxCompositedIconButton showPreview = new AjaxCompositedIconButton(showPreviewId, iconBuilder.build(),
                createStringResource("pageCreateCollectionReport.button.showPreview.${}", isShowingPreview)) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                isShowingPreview.setObject(!isShowingPreview.getObject());
                ReportObjectsListPanel<?> table = getReportTable();
                if (isShowingPreview.getObject()) {
                    table.getPageStorage().setSearch(null);
                    table.resetSearchModel();
                    table.resetTable(target);
                    table.refreshTable(target);
                    table.checkView();
                }
                target.add(getTableBox());
                if (isShowingPreview.getObject() && table.hasView()) {
                    info(createStringResource("PageReport.message.shownedReportPreview").getString());
                }
                target.add(getTableContainer());
                target.add(this);
                target.add(repeatingView.get(showPreviewInPopupId));
                target.add(repeatingView.get(refreshId));
                target.add(ReportOperationalButtonsPanel.this);
                target.add(getPageBase().getFeedbackPanel());
            }
        };
        showPreview.titleAsLabel(true);
        showPreview.add(new VisibleBehaviour(this::isCollectionReport));
        showPreview.add(AttributeAppender.append("class", "btn-default btn-sm"));
        showPreview.setOutputMarkupId(true);
        repeatingView.add(showPreview);

        iconType = new IconType();
        iconType.setCssClass("fa fa-window-maximize");
        iconBuilder = new CompositedIconBuilder()
                .setBasicIcon("fa fa-address-card-o", IconCssStyle.IN_ROW_STYLE);
//                .appendLayerIcon(iconType, IconCssStyle.BOTTOM_RIGHT_STYLE);
        AjaxCompositedIconButton showPreviewInPopup = new AjaxCompositedIconButton(showPreviewInPopupId, iconBuilder.build(),
                createStringResource("pageCreateCollectionReport.button.showPreviewInPopup")) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                RunReportPopupPanel reportPopup = new RunReportPopupPanel(getPageBase().getMainPopupBodyId(), getReport(), false);
                getPageBase().showMainPopup(reportPopup, target);
                target.add(ReportOperationalButtonsPanel.this);
            }
        };
        showPreviewInPopup.titleAsLabel(true);
        showPreviewInPopup.add(new VisibleBehaviour(() -> isCollectionReport() && !isShowingPreview.getObject()));
        showPreviewInPopup.add(AttributeAppender.append("class", "btn-default btn-sm"));
        showPreviewInPopup.setOutputMarkupId(true);
        repeatingView.add(showPreviewInPopup);

        iconBuilder = new CompositedIconBuilder()
                .setBasicIcon(GuiStyleConstants.CLASS_START_MENU_ITEM, IconCssStyle.IN_ROW_STYLE);
        AjaxCompositedIconButton runReport = new AjaxCompositedIconButton(repeatingView.newChildId(), iconBuilder.build(),
                createStringResource("pageCreateCollectionReport.button.runOriginalReport")) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                runReportPerformed(target, getOriginalReport(), getPageBase());
            }
        };
        runReport.titleAsLabel(true);
        runReport.add(new VisibleBehaviour(() -> isEditObject() && !WebComponentUtil.isImportReport(getOriginalReport())));
        runReport.add(AttributeAppender.append("class", "btn-default btn-sm"));
        runReport.setOutputMarkupId(true);
        repeatingView.add(runReport);


        iconBuilder = new CompositedIconBuilder()
                .setBasicIcon(GuiStyleConstants.CLASS_UPLOAD, IconCssStyle.IN_ROW_STYLE);
        AjaxCompositedIconButton importReport = new AjaxCompositedIconButton(repeatingView.newChildId(), iconBuilder.build(),
                createStringResource("pageCreateCollectionReport.button.importOriginalReport")) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                importReportPerformed(target, getOriginalReport(), getPageBase());
            }
        };
        importReport.add(new VisibleBehaviour(() -> isEditObject() && WebComponentUtil.isImportReport(getOriginalReport())));
        importReport.add(AttributeAppender.append("class", "btn-default btn-sm"));
        importReport.setOutputMarkupId(true);
        repeatingView.add(importReport);
    }

    protected abstract Boolean isEditObject();

    protected abstract Component getTableContainer();

    protected abstract Component getTableBox();

    protected abstract ReportObjectsListPanel<?> getReportTable();

    private ReportType getOriginalReport() {
        return getModelObject().getObjectOld().asObjectable();
    }

    public static void importReportPerformed(AjaxRequestTarget target, ReportType report, PageBase pageBase) {
        ImportReportPopupPanel importReportPopupPanel = new ImportReportPopupPanel(pageBase.getMainPopupBodyId(), report) {

            private static final long serialVersionUID = 1L;

            protected void importConfirmPerformed(AjaxRequestTarget target, ReportDataType reportImportData) {
                ReportOperationalButtonsPanel.importConfirmPerformed(target, report, reportImportData, pageBase);
                pageBase.hideMainPopup(target);

            }
        };
        pageBase.showMainPopup(importReportPopupPanel, target);
    }

    private static void importConfirmPerformed(AjaxRequestTarget target, ReportType reportType, ReportDataType reportImportData, PageBase pageBase) {
        OperationResult result = new OperationResult(OPERATION_IMPORT_REPORT);
        Task task = pageBase.createSimpleTask(OPERATION_IMPORT_REPORT);

        try {
            pageBase.getReportManager().importReport(reportType.asPrismObject(), reportImportData.asPrismObject(), task, result);
        } catch (Exception ex) {
            result.recordFatalError(ex);
        } finally {
            result.computeStatusIfUnknown();
        }

        pageBase.showResult(result);
        target.add(pageBase.getFeedbackPanel());
    }

    public static void runReportPerformed(AjaxRequestTarget target, ReportType report, PageBase pageBase) {

        if (!hasParameters(report)) {
            runConfirmPerformed(target, report, null, pageBase);
            return;
        }

        RunReportPopupPanel runReportPopupPanel = new RunReportPopupPanel(pageBase.getMainPopupBodyId(), report) {

            private static final long serialVersionUID = 1L;

            protected void runConfirmPerformed(AjaxRequestTarget target, ReportType reportType, PrismContainer<ReportParameterType> reportParam) {
                ReportOperationalButtonsPanel.runConfirmPerformed(target, reportType, reportParam, pageBase);
                pageBase.hideMainPopup(target);
            }
        };
        pageBase.showMainPopup(runReportPopupPanel, target);

    }

    private static void runConfirmPerformed(AjaxRequestTarget target, ReportType reportType, PrismContainer<ReportParameterType> reportParam, PageBase pageBase) {
        OperationResult result = new OperationResult(OPERATION_RUN_REPORT);
        Task task = pageBase.createSimpleTask(OPERATION_RUN_REPORT);

        try {
            pageBase.getReportManager().runReport(reportType.asPrismObject(), reportParam, task, result);
        } catch (Exception ex) {
            result.recordFatalError(ex);
        } finally {
            result.computeStatusIfUnknown();
        }

        pageBase.showResult(result);
        target.add(pageBase.getFeedbackPanel());
    }

    private ReportType getReport() {
        return getModelObject().getObject().asObjectable();
    }

    private boolean isCollectionReport() {
        return getModelObject().findItemDefinition(ReportType.F_OBJECT_COLLECTION) != null;
    }

    public abstract void saveAndRunPerformed(AjaxRequestTarget target);

    public Boolean isShowingPreview() {
        return isShowingPreview.getObject();
    }

    public static boolean hasParameters(ReportType report) {
        return report.getObjectCollection() != null && !report.getObjectCollection().getParameter().isEmpty();
    }
}
