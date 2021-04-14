/*
 * Copyright (C) 2016-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.api.component;

import java.util.*;

import com.evolveum.midpoint.web.component.data.ISelectableDataProvider;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.extensions.markup.html.repeater.data.table.DataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import com.evolveum.midpoint.gui.api.GuiStyleConstants;
import com.evolveum.midpoint.gui.api.component.button.CsvDownloadButtonPanel;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.impl.component.AjaxCompositedIconButton;
import com.evolveum.midpoint.gui.impl.component.icon.CompositedIcon;
import com.evolveum.midpoint.gui.impl.component.icon.CompositedIconBuilder;
import com.evolveum.midpoint.gui.impl.component.icon.IconCssStyle;
import com.evolveum.midpoint.gui.impl.component.icon.LayeredIconCssStyle;
import com.evolveum.midpoint.model.api.AssignmentObjectRelation;
import com.evolveum.midpoint.model.api.ModelAuthorizationAction;
import com.evolveum.midpoint.model.api.authentication.CompiledObjectCollectionView;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.security.api.AuthorizationConstants;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.AjaxIconButton;
import com.evolveum.midpoint.web.component.MultiFunctinalButtonDto;
import com.evolveum.midpoint.web.component.MultifunctionalButton;
import com.evolveum.midpoint.web.component.data.column.CheckBoxHeaderColumn;
import com.evolveum.midpoint.web.component.util.SelectableBean;
import com.evolveum.midpoint.web.component.util.VisibleBehaviour;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;
import com.evolveum.midpoint.web.page.admin.configuration.PageImportObject;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

/**
 * @author katkav
 */
public abstract class MainObjectListPanel<O extends ObjectType> extends ObjectListPanel<O> {
    private static final long serialVersionUID = 1L;

    private static final Trace LOGGER = TraceManager.getTrace(MainObjectListPanel.class);

    public MainObjectListPanel(String id, Class<O> type) {
        this(id, type, null);
    }

    public MainObjectListPanel(String id, Class<O> type, Collection<SelectorOptions<GetOperationOptions>> options) {
        super(id, type, options);
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        setAdditionalBoxCssClasses(WebComponentUtil.getBoxCssClasses(WebComponentUtil.classToQName(getPrismContext(), getType())));
    }

    @Override
    protected IColumn<SelectableBean<O>, String> createCheckboxColumn() {
        return new CheckBoxHeaderColumn<>();
    }

    protected void newObjectPerformed(AjaxRequestTarget target, AssignmentObjectRelation relation, CompiledObjectCollectionView collectionView) {
        if (collectionView == null) {
            collectionView = getObjectCollectionView();
        }

        List<ObjectReferenceType> archetypeRef = getReferencesList(collectionView);
        try {
            WebComponentUtil.initNewObjectWithReference(getPageBase(),
                    WebComponentUtil.classToQName(getPrismContext(), getType()),
                    archetypeRef);
        } catch (SchemaException ex) {
            getPageBase().getFeedbackMessages().error(MainObjectListPanel.this, ex.getUserFriendlyMessage());
            target.add(getPageBase().getFeedbackPanel());
        }
    }

    protected List<ObjectReferenceType> getReferencesList(CompiledObjectCollectionView collectionView) {
        if (!isArchetypedCollectionView(collectionView)) {
            return null;
        }

        ObjectReferenceType ref = collectionView.getCollection().getCollectionRef();
        return Collections.singletonList(ref);
    }

    private CompositedIcon createCompositedIcon(CompiledObjectCollectionView collectionView) {
        DisplayType additionalButtonDisplayType = WebComponentUtil.getNewObjectDisplayTypeFromCollectionView(collectionView, getPageBase());
        CompositedIconBuilder builder = getNewObjectButtonAdditionalIconBuilder(collectionView, additionalButtonDisplayType);
        if (builder == null) {
            return null;
        }
        return builder.build();
    }

    @Override
    protected ISelectableDataProvider<O, SelectableBean<O>> createProvider() {
        return createSelectableBeanObjectDataProvider(null, null); // default
    }

    @Override
    protected List<Component> createToolbarButtonsList(String buttonId) {
        List<Component> buttonsList = new ArrayList<>();

        buttonsList.add(createCreateNewObjectButton(buttonId));
        buttonsList.add(createImportObjectButton(buttonId));
        buttonsList.add(createDownloadButton(buttonId));
        buttonsList.add(createCreateReportButton(buttonId));
        buttonsList.add(createRefreshButton(buttonId));
        buttonsList.add(createPlayPauseButton(buttonId));

        return buttonsList;
    }

    protected MultifunctionalButton createCreateNewObjectButton(String buttonId) {
        MultifunctionalButton createNewObjectButton = new MultifunctionalButton(buttonId, loadButtonDescriptions()) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void buttonClickPerformed(AjaxRequestTarget target, AssignmentObjectRelation relationSpec, CompiledObjectCollectionView collectionView) {
                newObjectPerformed(target, relationSpec, collectionView);
            }

            @Override
            protected DisplayType getMainButtonDisplayType() {
                return getNewObjectButtonStandardDisplayType();
            }

            @Override
            protected Map<IconCssStyle, IconType> getMainButtonLayerIcons() {
                return createMainButtonLayerIcons(getMainButtonDisplayType());
            }

            @Override
            protected DisplayType getDefaultObjectButtonDisplayType() {
                return getNewObjectButtonSpecialDisplayType();
            }

            @Override
            protected boolean isDefaultButtonVisible() {
                return getNewObjectGenericButtonVisibility();
            }
        };
        createNewObjectButton.add(new VisibleBehaviour(this::isCreateNewObjectEnabled));
        createNewObjectButton.add(AttributeAppender.append("class", "btn-margin-right"));
        return createNewObjectButton;
    }

    private List<MultiFunctinalButtonDto> loadButtonDescriptions() {
        List<MultiFunctinalButtonDto> multiFunctinalButtonDtos = new ArrayList<>();

        Collection<CompiledObjectCollectionView> compiledObjectCollectionViews = getNewObjectInfluencesList();

        if (CollectionUtils.isNotEmpty(compiledObjectCollectionViews)) {
            compiledObjectCollectionViews.forEach(collection -> {
                MultiFunctinalButtonDto buttonDesc = new MultiFunctinalButtonDto();
                buttonDesc.setCompositedIcon(createCompositedIcon(collection));
                buttonDesc.setOrCreateDefaultAdditionalButtonDisplayType(collection.getDisplay());
                buttonDesc.setCollectionView(collection);
                multiFunctinalButtonDtos.add(buttonDesc);
            });
        }

        return multiFunctinalButtonDtos;
    }

    private DisplayType getNewObjectButtonStandardDisplayType() {
        if (isCollectionViewPanelForCompiledView()) {

            CompiledObjectCollectionView view = getObjectCollectionView();
            if (isArchetypedCollectionView(view)) {
                return WebComponentUtil.getNewObjectDisplayTypeFromCollectionView(view, getPageBase());
            }
        }

        String sb = createStringResource("MainObjectListPanel.newObject").getString()
                + " "
                + createStringResource("ObjectTypeLowercase." + getType().getSimpleName()).getString();
        return WebComponentUtil.createDisplayType(GuiStyleConstants.CLASS_ADD_NEW_OBJECT, "green",
                sb);
    }

    protected Map<IconCssStyle, IconType> createMainButtonLayerIcons(DisplayType mainButtonDisplayType) {
        if (!isCollectionViewPanelForCompiledView()) {
            return null;
        }
//        DisplayType mainButtonDisplayType = getMainButtonDisplayType();
        return WebComponentUtil.createMainButtonLayerIcon(mainButtonDisplayType);
    }

    private DisplayType getNewObjectButtonSpecialDisplayType() {
        String iconCssStyle = WebComponentUtil.createDefaultBlackIcon(WebComponentUtil.classToQName(getPageBase().getPrismContext(), getType()));

        String sb = createStringResource("MainObjectListPanel.newObject").getString()
                + " "
                + createStringResource("ObjectTypeLowercase." + getType().getSimpleName()).getString();
        return WebComponentUtil.createDisplayType(iconCssStyle, "", sb);
    }

    protected boolean getNewObjectGenericButtonVisibility() {
        return true;
    }

    private AjaxIconButton createImportObjectButton(String buttonId) {
        AjaxIconButton importObject = new AjaxIconButton(buttonId, new Model<>(GuiStyleConstants.CLASS_UPLOAD),
                createStringResource("MainObjectListPanel.import")) {

            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                ((PageBase) getPage()).navigateToNext(PageImportObject.class);
            }
        };
        importObject.add(AttributeAppender.append("class", "btn btn-default btn-sm"));
        importObject.add(new VisibleBehaviour(this::isImportObjectButtonVisible));
        return importObject;
    }

    private boolean isImportObjectButtonVisible() {
        try {
            return ((PageBase) getPage()).isAuthorized(ModelAuthorizationAction.IMPORT_OBJECTS.getUrl())
                    && WebComponentUtil.isAuthorized(AuthorizationConstants.AUTZ_UI_CONFIGURATION_ALL_URL,
                    AuthorizationConstants.AUTZ_UI_CONFIGURATION_IMPORT_URL);
        } catch (Exception ex) {
            LOGGER.error("Failed to check authorization for IMPORT action for " + getType().getSimpleName()
                    + " object, ", ex);
        }
        return false;
    }

    private CsvDownloadButtonPanel createDownloadButton(String buttonId) {
        boolean canCountBeforeExporting = getType() == null || !ShadowType.class.isAssignableFrom(getType()) ||
                isRawOrNoFetchOption(getOptions());
        CsvDownloadButtonPanel exportDataLink = new CsvDownloadButtonPanel(buttonId, canCountBeforeExporting) {

            private static final long serialVersionUID = 1L;

            @Override
            protected DataTable<?, ?> getDataTable() {
                return getTable().getDataTable();
            }

            @Override
            protected String getFilename() {
                return getType().getSimpleName() +
                        "_" + createStringResource("MainObjectListPanel.exportFileName").getString();
            }

        };
        exportDataLink.add(new VisibleEnableBehaviour() {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isVisible() {
                return WebComponentUtil.isAuthorized(AuthorizationConstants.AUTZ_UI_ADMIN_CSV_EXPORT_ACTION_URI);
            }
        });
//        exportDataLink.add(AttributeAppender.append("class", "btn-margin-right"));
        return exportDataLink;
    }

    private AjaxCompositedIconButton createCreateReportButton(String buttonId) {
        final CompositedIconBuilder builder = new CompositedIconBuilder();
        builder.setBasicIcon(WebComponentUtil.createReportIcon(), IconCssStyle.IN_ROW_STYLE);
        IconType plusIcon = new IconType();
        plusIcon.setCssClass(GuiStyleConstants.CLASS_ADD_NEW_OBJECT);
        plusIcon.setColor("green");
        builder.appendLayerIcon(plusIcon, LayeredIconCssStyle.BOTTOM_RIGHT_STYLE);
        AjaxCompositedIconButton createReport = new AjaxCompositedIconButton(buttonId, builder.build(),
                getPageBase().createStringResource("MainObjectListPanel.createReport")) {

            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                createReportPerformed(target);
            }
        };
        createReport.add(AttributeAppender.append("class", "btn btn-default btn-sm btn-margin-right"));
        createReport.add(new VisibleBehaviour(() -> WebComponentUtil.isAuthorized(AuthorizationConstants.AUTZ_UI_ADMIN_CREATE_REPORT_BUTTON_URI)));
        return createReport;
    }

    private AjaxIconButton createRefreshButton(String buttonId) {
        AjaxIconButton refreshIcon = new AjaxIconButton(buttonId, new Model<>(GuiStyleConstants.CLASS_RECONCILE),
                createStringResource("MainObjectListPanel.refresh")) {

            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                clearCache();
                refreshTable(target);

                target.add(getTable());
            }
        };
        refreshIcon.add(AttributeAppender.append("class", "btn btn-default btn-margin-left btn-sm"));
        return refreshIcon;
    }

    private AjaxIconButton createPlayPauseButton(String buttonId) {
        AjaxIconButton playPauseIcon = new AjaxIconButton(buttonId, getRefreshPausePlayButtonModel(),
                getRefreshPausePlayButtonTitleModel()) {

            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                clearCache();
                setManualRefreshEnabled(!isRefreshEnabled());
                target.add(getTable());
            }
        };
        playPauseIcon.add(AttributeAppender.append("class", "btn btn-default btn-sm"));
        return playPauseIcon;
    }

    private IModel<String> getRefreshPausePlayButtonModel() {
        return () -> {
            if (isRefreshEnabled()) {
                return GuiStyleConstants.CLASS_PAUSE;
            }

            return GuiStyleConstants.CLASS_PLAY;
        };
    }

    private IModel<String> getRefreshPausePlayButtonTitleModel() {
        return () -> {
            if (isRefreshEnabled()) {
                return createStringResource("MainObjectListPanel.refresh.pause").getString();
            }
            return createStringResource("MainObjectListPanel.refresh.start").getString();
        };
    }

    private boolean isRawOrNoFetchOption(Collection<SelectorOptions<GetOperationOptions>> options) {
        if (options == null) {
            return false;
        }
        for (SelectorOptions<GetOperationOptions> option : options) {
            if (Boolean.TRUE.equals(option.getOptions().getRaw()) ||
                    Boolean.TRUE.equals(option.getOptions().getNoFetch())) {
                return true;
            }
        }
        return false;
    }

    protected boolean isCreateNewObjectEnabled() {
        return true;
    }

    protected List<CompiledObjectCollectionView> getNewObjectInfluencesList() {
        if (isCollectionViewPanelForCompiledView()) {
            return new ArrayList<>();
        }
        return getAllApplicableArchetypeViews();
    }

    private boolean isArchetypedCollectionView(CompiledObjectCollectionView view) {
        if (view == null) {
            return false;
        }

        CollectionRefSpecificationType collectionRefSpecificationType = view.getCollection();
        if (collectionRefSpecificationType == null) {
            return false;
        }

        ObjectReferenceType collectionRef = collectionRefSpecificationType.getCollectionRef();
        if (collectionRef == null) {
            return false;
        }

        return QNameUtil.match(ArchetypeType.COMPLEX_TYPE, collectionRef.getType());
    }

    protected CompositedIconBuilder getNewObjectButtonAdditionalIconBuilder(CompiledObjectCollectionView influencingObject, DisplayType additionalButtonDisplayType) {
        return null;
    }
}
