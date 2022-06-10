/*
 * Copyright (c) 2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.gui.impl.component.tile;

import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.gui.impl.component.search.Search;
import com.evolveum.midpoint.gui.impl.component.search.SearchPanel;
import com.evolveum.midpoint.gui.impl.page.self.requestAccess.PageableListView;
import com.evolveum.midpoint.gui.impl.page.self.requestAccess.ViewToggle;
import com.evolveum.midpoint.web.component.data.BoxedTablePanel;
import com.evolveum.midpoint.web.component.data.paging.NavigatorPanel;
import com.evolveum.midpoint.web.component.util.VisibleBehaviour;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.ISortableDataProvider;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import java.io.Serializable;
import java.util.List;

/**
 * Created by Viliam Repan (lazyman).
 */
public class TileTablePanel<T extends Tile, O extends Serializable> extends BasePanel<O> {

    private static final String ID_TILES_CONTAINER = "tilesContainer";
    private static final String ID_TILES = "tiles";

    private static final String ID_TILES_HEADER = "tilesHeader";
    private static final String ID_TILE = "tile";
    private static final String ID_TABLE = "table";

    private static final String ID_TILES_PAGING = "tilesPaging";

    private IModel<ViewToggle> viewToggleModel = Model.of(ViewToggle.TILE);

    private IModel<Search> searchModel;

    public TileTablePanel(String id, ISortableDataProvider provider, List<IColumn> columns) {
        super(id);

        initModels();
        initLayout(provider, columns);
    }

    private void initModels() {
        searchModel = createSearchModel();
    }

    public IModel<ViewToggle> getViewToggleModel() {
        return viewToggleModel;
    }

    private void initLayout(ISortableDataProvider provider, List<IColumn> columns) {
        setOutputMarkupId(true);

        add(createTilesHeader(ID_TILES_HEADER));

        WebMarkupContainer tilesContainer = new WebMarkupContainer(ID_TILES_CONTAINER);
        tilesContainer.add(new VisibleBehaviour(() -> viewToggleModel.getObject() == ViewToggle.TILE));
        add(tilesContainer);

        PageableListView<T, O> tiles = new PageableListView<T, O>(ID_TILES, provider) {

            @Override
            protected void populateItem(ListItem<T> item) {
                Component tile = createTile(ID_TILE, item.getModel());
                item.add(tile);
            }

            @Override
            protected T createItem(O object) {
                return createTileObject(object);
            }
        };
        tilesContainer.setOutputMarkupId(true);
        tilesContainer.add(tiles);

        NavigatorPanel tilesPaging = new NavigatorPanel(ID_TILES_PAGING, tiles, true) {

            @Override
            protected String getPaginationCssClass() {
                return null;
            }
        };
        add(tilesPaging);

        BoxedTablePanel table = new BoxedTablePanel(ID_TABLE, provider, columns) {

            @Override
            protected WebMarkupContainer createButtonToolbar(String id) {
                return TileTablePanel.this.createTableButtonToolbar(id);
            }

            @Override
            protected Component createHeader(String headerId) {
                return createTableHeader(headerId);
            }
        };
        table.add(new VisibleBehaviour(() -> viewToggleModel.getObject() == ViewToggle.TABLE));
        add(table);
    }

    protected Component createTile(String id, IModel<T> model) {
        return new CatalogTilePanel(id, model);
    }

    protected T createTileObject(O object) {
        return null;
    }

    public void refresh(AjaxRequestTarget target) {
        target.add(getPageBase().getFeedbackPanel());

        if (viewToggleModel.getObject() == ViewToggle.TABLE) {
            target.add(get(ID_TABLE));
        } else {
            target.add(get(ID_TILES_CONTAINER), get(ID_TILES_PAGING));
        }
    }

    protected IModel<Search> createSearchModel() {
        return null;
    }

    protected Component createTilesHeader(String id) {
        return createHeader(id);
    }

    protected Component createTableHeader(String id) {
        return createHeader(id);
    }

    protected Component createHeader(String id) {
        if (searchModel == null) {
            return new WebMarkupContainer(id);
        }

        return new SearchPanel(id, searchModel) {

            @Override
            protected void searchPerformed(AjaxRequestTarget target) {
                onSearchPerformed(target);
            }
        };
    }

    protected WebMarkupContainer createTableButtonToolbar(String id) {
        return new WebMarkupContainer(id);
    }

    private void onSearchPerformed(AjaxRequestTarget target) {
        refresh(target);
    }
}
