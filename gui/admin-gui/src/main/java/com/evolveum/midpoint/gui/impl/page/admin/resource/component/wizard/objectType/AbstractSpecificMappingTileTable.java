/*
 * Copyright (C) 2010-2023 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.gui.impl.page.admin.resource.component.wizard.objectType;

import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerValueWrapper;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerWrapper;
import com.evolveum.midpoint.gui.api.util.MappingDirection;
import com.evolveum.midpoint.gui.api.util.WebPrismUtil;
import com.evolveum.midpoint.gui.impl.component.tile.TileTablePanel;
import com.evolveum.midpoint.gui.impl.page.admin.resource.ResourceDetailsModel;
import com.evolveum.midpoint.gui.impl.page.admin.resource.component.wizard.objectType.activation.MappingTile;
import com.evolveum.midpoint.gui.impl.page.admin.resource.component.wizard.objectType.activation.MappingTilePanel;
import com.evolveum.midpoint.gui.impl.page.admin.resource.component.wizard.objectType.activation.SpecificMappingProvider;
import com.evolveum.midpoint.gui.impl.page.self.requestAccess.PageableListView;
import com.evolveum.midpoint.gui.impl.util.GuiDisplayNameUtil;
import com.evolveum.midpoint.gui.impl.util.IconAndStylesUtil;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.AjaxIconButton;
import com.evolveum.midpoint.web.component.prism.ValueStatus;
import com.evolveum.midpoint.web.component.util.VisibleBehaviour;
import com.evolveum.midpoint.web.model.ContainerValueWrapperFromObjectWrapperModel;
import com.evolveum.midpoint.web.session.UserProfileStorage;
import com.evolveum.midpoint.web.util.ExpressionUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.extensions.markup.html.repeater.data.table.ISortableDataProvider;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractSpecificMappingTileTable<C extends Containerable> extends TileTablePanel
        <MappingTile<PrismContainerValueWrapper<? extends Containerable>>, PrismContainerValueWrapper> {

    private static final Trace LOGGER = TraceManager.getTrace(AbstractSpecificMappingTileTable.class);
    private static final String ID_ADD_RULE_CONTAINER = "addRuleContainer";
    private static final String ID_NO_RULE_MESSAGE = "noRuleMessage";
    private static final String ID_ADD_BUTTON = "addButton";

    private final IModel<PrismContainerWrapper<C>> containerModel;
    private final MappingDirection mappingDirection;
    private final ResourceDetailsModel detailsModel;

    public AbstractSpecificMappingTileTable(
            String id,
            IModel<PrismContainerWrapper<C>> containerModel,
            @NotNull MappingDirection mappingDirection,
            ResourceDetailsModel detailsModel) {
        super(id);
        this.containerModel = containerModel;
        this.mappingDirection = mappingDirection;
        this.detailsModel = detailsModel;
    }

    public ResourceDetailsModel getDetailsModel() {
        return detailsModel;
    }

    public MappingDirection getMappingDirection() {
        return mappingDirection;
    }

    public IModel<PrismContainerWrapper<C>> getContainerModel() {
        return containerModel;
    }

    @Override
    protected ISortableDataProvider createProvider() {
        return new SpecificMappingProvider<C>(
                AbstractSpecificMappingTileTable.this,
                new ContainerValueWrapperFromObjectWrapperModel<>(containerModel, ItemPath.EMPTY_PATH),
                mappingDirection
        );
    }

    @Override
    protected MappingTile createTileObject(PrismContainerValueWrapper object) {
        MappingTile tile = new MappingTile(object);
        tile.setIcon(IconAndStylesUtil.createMappingIcon(object));
        switch (tile.getMappingDefinitionType()) {
            case CONFIGURED:
                tile.setTitle(GuiDisplayNameUtil.getDisplayName(
                        (MappingType) object.getRealValue()));
                tile.setDescription(createDescription((MappingType) object.getRealValue()));
                tile.setHelp(createStrengthHelp((MappingType) object.getRealValue()));
                break;
            case PREDEFINED:
                tile.setTitle(GuiDisplayNameUtil.getDisplayName(
                        (AbstractPredefinedActivationMappingType) object.getRealValue()));
                tile.setDescription(WebPrismUtil.getHelpText(
                        object.getDefinition(),
                        object.getParent().getParent().getNewValue().getRealClass()));
                break;
        }

        return tile;
    }

    private String createStrengthHelp(MappingType mapping) {
        String strength = translateStrength(mapping);
        return getPageBase().createStringResource("AbstractSpecificMappingTileTable.tile.help", strength).getString();
    }

    @Override
    protected Component createTile(String id, IModel<MappingTile<PrismContainerValueWrapper<? extends Containerable>>> model) {
        MappingTilePanel tilePanel = new MappingTilePanel(id, model) {
            @Override
            protected <T extends PrismContainerValueWrapper<? extends Containerable>> void onConfigureClick(AjaxRequestTarget target, MappingTile<T> tile) {
                switch (tile.getMappingDefinitionType()) {
                    case PREDEFINED:
                        editPredefinedMapping(
                                (IModel<PrismContainerValueWrapper<AbstractPredefinedActivationMappingType>>) Model.of(tile.getValue()),
                                target);
                        break;
                    case CONFIGURED:
                        editConfiguredMapping(
                                (IModel<PrismContainerValueWrapper<MappingType>>) Model.of(tile.getValue()),
                                target);
                        break;
                }
            }

            @Override
            protected void onRemovePerformed(PrismContainerValueWrapper<? extends Containerable> value, AjaxRequestTarget target) {
                if (value.getStatus() == ValueStatus.ADDED) {
                    PrismContainerWrapper wrapper = value.getParent();
                    if (wrapper != null) {
                        wrapper.getValues().remove(value);
                    }
                } else {
                    value.setStatus(ValueStatus.DELETED);
                }
                getTilesModel().detach();
                refresh(target);
            }
        };
        tilePanel.add(AttributeAppender.append("style", "cursor:default;"));
        return tilePanel;
    }

    private String translateStrength(MappingType mapping) {
        MappingStrengthType strengthBean = mapping.getStrength();
        if (strengthBean == null) {
            strengthBean = MappingStrengthType.NORMAL;
        }
        return PageBase.createStringResourceStatic(null, strengthBean).getString().toLowerCase();
    }

    private String createDescription(MappingType mapping) {
        if (StringUtils.isNotEmpty(mapping.getDescription())) {
            return mapping.getDescription();
        }
        String strength = translateStrength(mapping);

        ExpressionType expressionBean = mapping.getExpression();
        String description = PageBase.createStringResourceStatic(
                "AbstractSpecificMappingTileTable.tile.description.prefix",
                "AbstractSpecificMappingTileTable.tile.description.prefix",
                strength).getString();

        ExpressionUtil.ExpressionEvaluatorType evaluatorType = null;
        if (expressionBean != null) {
            String expression = ExpressionUtil.loadExpression(expressionBean, PrismContext.get(), LOGGER);
            evaluatorType = ExpressionUtil.getExpressionType(expression);

        }

        if (evaluatorType == null) {
            evaluatorType = ExpressionUtil.ExpressionEvaluatorType.AS_IS;
        }

        String evaluator = PageBase.createStringResourceStatic(null, evaluatorType).getString();

        description += " " + PageBase.createStringResourceStatic(
                "AbstractSpecificMappingTileTable.tile.description.suffix",
                "AbstractSpecificMappingTileTable.tile.description.suffix",
                evaluator).getString();
        return description;
    }

    @Override
    protected WebMarkupContainer createTilesContainer(
            String idTilesContainer,
            ISortableDataProvider<PrismContainerValueWrapper, String> provider,
            UserProfileStorage.TableId tableId) {
        Fragment tilesFragment = new Fragment(idTilesContainer, ID_TILES_FRAGMENT, AbstractSpecificMappingTileTable.this);

        PageableListView tiles = createTilesPanel(ID_TILES, provider);
        tilesFragment.add(tiles);

        WebMarkupContainer addContainer = createAddRuleContainer();
        tilesFragment.add(addContainer);

        return tilesFragment;
    }

    private WebMarkupContainer createAddRuleContainer() {
        WebMarkupContainer addRuleContainer = new WebMarkupContainer(ID_ADD_RULE_CONTAINER);
        addRuleContainer.add(new VisibleBehaviour(() -> getTilesModel().getObject().isEmpty()));

        addRuleContainer.add(new Label(ID_NO_RULE_MESSAGE, getPageBase().createStringResource(getNoRuleMessageKey())));

        addRuleContainer.add(createAddButton(ID_ADD_BUTTON));

        return addRuleContainer;
    }

    protected String getNoRuleMessageKey() {
        return "AbstractSpecificMappingTileTable.noRules";
    }

    private AjaxIconButton createAddButton(String buttonId) {
        AjaxIconButton addButton = new AjaxIconButton(
                buttonId,
                Model.of("fa fa-circle-plus text-light"),
                (IModel<String>) () -> createStringResource(
                        "AbstractSpecificMappingTileTable.button.add." + mappingDirection.name().toLowerCase()).getString()) {

            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                AbstractSpecificMappingTileTable.this.onClickCreateMapping(target);
            }
        };
        addButton.showTitleAsLabel(true);
        addButton.add(AttributeAppender.append("class", "text-light"));
        return addButton;
    }

    protected abstract void onClickCreateMapping(AjaxRequestTarget target);

    protected abstract void editPredefinedMapping(
            IModel<PrismContainerValueWrapper<AbstractPredefinedActivationMappingType>> valueModel, AjaxRequestTarget target);

    protected abstract void editConfiguredMapping(
            IModel<PrismContainerValueWrapper<MappingType>> valueModel, AjaxRequestTarget target);

    @Override
    protected String getTileCssClasses() {
        return "col-xs-6 col-sm-6 col-md-4 col-lg-3 col-xl-5i col-xxl-2 p-2";
    }

    @Override
    protected boolean showFooter() {
        return !getTilesModel().getObject().isEmpty();
    }

    @Override
    protected WebMarkupContainer createTilesButtonToolbar(String id) {
        RepeatingView repView = new RepeatingView(id);

        AjaxIconButton addButton = createAddButton(repView.newChildId());
        addButton.add(AttributeAppender.replace("class", "btn btn-primary"));
        addButton.showTitleAsLabel(true);
        repView.add(addButton);

        return repView;
    }
}
