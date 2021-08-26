/*
 * Copyright (c) 2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.page.admin;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.evolveum.midpoint.gui.api.factory.wrapper.PrismObjectWrapperFactory;
import com.evolveum.midpoint.gui.api.factory.wrapper.WrapperContext;
import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.prism.ItemStatus;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismObjectWrapper;
import com.evolveum.midpoint.gui.api.util.ModelServiceLocator;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.model.api.AdminGuiConfigurationMergeManager;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.util.validation.MidpointFormValidator;
import com.evolveum.midpoint.web.util.validation.SimpleValidationError;
import com.evolveum.midpoint.xml.ns._public.common.common_3.GuiObjectDetailsPageType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;

public class ObjectDetailsModels<O extends ObjectType> implements Serializable {

    private static final Trace LOGGER = TraceManager.getTrace(ObjectDetailsModels.class);

    private ModelServiceLocator modelServiceLocator;
    private LoadableModel<PrismObject<O>> prismObjectModel;

    private LoadableModel<PrismObjectWrapper<O>> objectWrapperModel;
    private LoadableModel<GuiObjectDetailsPageType> detailsPageConfigurationModel;

    private LoadableModel<O> summaryModel;

    public ObjectDetailsModels(LoadableModel<PrismObject<O>> prismObjectModel, ModelServiceLocator serviceLocator) {
        this.prismObjectModel = prismObjectModel;
        this.modelServiceLocator = serviceLocator;

        objectWrapperModel = new LoadableModel<>(false) {

            @Override
            protected PrismObjectWrapper<O> load() {
                PrismObject<O> prismObject = prismObjectModel.getObject();
                PrismObjectWrapperFactory<O> factory = modelServiceLocator.findObjectWrapperFactory(prismObject.getDefinition());
                Task task = modelServiceLocator.createSimpleTask("createWrapper");
                OperationResult result = task.getResult();
                WrapperContext ctx = new WrapperContext(task, result);
                ctx.setCreateIfEmpty(true);
                ctx.setContainerPanelConfigurationType(detailsPageConfigurationModel.getObject().getPanel());
                try {
                    return factory.createObjectWrapper(prismObject, isEditUser(prismObject) ? ItemStatus.NOT_CHANGED : ItemStatus.ADDED, ctx);
                } catch (SchemaException e) {
                    //TODO:
                    return null;
                }

            }
        };

        detailsPageConfigurationModel = new LoadableModel<>(false) {
            @Override
            protected GuiObjectDetailsPageType load() {
                return loadDetailsPageConfiguration();
            }
        };

        summaryModel = new LoadableModel<O>(false) {

            @Override
            protected O load() {
                PrismObjectWrapper<O> wrapper = objectWrapperModel.getObject();
                if (wrapper == null) {
                    return null;
                }

                PrismObject<O> object = wrapper.getObject();
//                loadParentOrgs(object);
                return object.asObjectable();
            }
        };
    }

    protected GuiObjectDetailsPageType loadDetailsPageConfiguration() {
        return modelServiceLocator.getCompiledGuiProfile().findObjectDetailsConfiguration(prismObjectModel.getObject().getDefinition().getTypeName());
    }

    public LoadableModel<PrismObjectWrapper<O>> getObjectWrapperModel() {
        return objectWrapperModel;
    }

    private PrismObjectWrapper<O> getObjectWrapper() {
        return getObjectWrapperModel().getObject();
    }

    protected PrismObject<O> getPrismObject() {
        return getObjectWrapper().getObject();
    }

    public LoadableModel<GuiObjectDetailsPageType> getObjectDetailsPageConfiguration() {
        return detailsPageConfigurationModel;
    }

    //TODO change summary panels to wrappers?
    public LoadableModel<O> getSummaryModel() {
        return summaryModel;
    }

    private boolean isEditUser(PrismObject<O> prismObject) {
        return prismObject.getOid() != null;
    }

    protected PrismContext getPrismContext() {
        return modelServiceLocator.getPrismContext();
    }

    private Collection<SimpleValidationError> validationErrors;
    private ObjectDelta<O> delta;

    public ObjectDelta<O> getDelta() {
        return delta;
    }

    public Collection<ObjectDelta<? extends ObjectType>> collectDeltas(OperationResult result) throws SchemaException {
        validationErrors = null;
//        delta = null;
        PrismObjectWrapper<O> objectWrapper = getObjectWrapperModel().getObject();
        delta = objectWrapper.getObjectDelta();
        WebComponentUtil.encryptCredentials(delta, true, modelServiceLocator);
        switch (objectWrapper.getStatus()) {
            case ADDED:
                    PrismObject<O> objectToAdd = delta.getObjectToAdd();
//                    WebComponentUtil.encryptCredentials(objectToAdd, true, modelServiceLocator);
                    prepareObjectForAdd(objectToAdd);
                    getPrismContext().adopt(objectToAdd, objectWrapper.getCompileTimeClass());
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Delta before add user:\n{}", delta.debugDump(3));
                    }

                    if (!delta.isEmpty()) {
                        delta.revive(getPrismContext());

                        final Collection<ObjectDelta<? extends ObjectType>> deltas = MiscUtil.createCollection(delta);
                        validationErrors = performCustomValidation(objectToAdd, deltas);
                        return deltas;

//                        if (checkValidationErrors(target, validationErrors)) {
//                            return null;
//                        }
                    }
                break;

            case NOT_CHANGED:
//                    WebComponentUtil.encryptCredentials(delta, true, modelServiceLocator);
                    prepareObjectDeltaForModify(delta); //preparing of deltas for projections (ADD, DELETE, UNLINK)

                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Delta before modify user:\n{}", delta.debugDump(3));
                    }

                    Collection<ObjectDelta<? extends ObjectType>> deltas = new ArrayList<>();
                    if (!delta.isEmpty()) {
                        delta.revive(getPrismContext());
                        deltas.add(delta);
                    }

                    List<ObjectDelta<? extends ObjectType>> additionalDeltas = getAdditionalModifyDeltas(result);
                    if (additionalDeltas != null) {
                        for (ObjectDelta additionalDelta : additionalDeltas) {
                            if (!additionalDelta.isEmpty()) {
                                additionalDelta.revive(getPrismContext());
                                deltas.add(additionalDelta);
                            }
                        }
                    }
                    return deltas;
            // support for add/delete containers (e.g. delete credentials)
            default:
                throw new UnsupportedOperationException("Unsupported state");
        }
        LOGGER.trace("returning from saveOrPreviewPerformed");
        return new ArrayList<>();
    }

    public Collection<SimpleValidationError> getValidationErrors() {
        return validationErrors;
    }

    protected Collection<SimpleValidationError> performCustomValidation(PrismObject<O> object,
            Collection<ObjectDelta<? extends ObjectType>> deltas) throws SchemaException {
        Collection<SimpleValidationError> errors = null;

        if (object == null) {
            if (getObjectWrapper() != null && getObjectWrapper().getObjectOld() != null) {
                object = getObjectWrapper().getObjectOld().clone();        // otherwise original object could get corrupted e.g. by applying the delta below

                for (ObjectDelta delta : deltas) {
                    // because among deltas there can be also ShadowType deltas
                    if (UserType.class.isAssignableFrom(delta.getObjectTypeClass())) {
                        delta.applyTo(object);
                    }
                }
            }
        } else {
            object = object.clone();
        }

//        performAdditionalValidation(object, deltas, errors);

        for (MidpointFormValidator validator : getValidators()) {
            if (errors == null) {
                errors = validator.validateObject(object, deltas);
            } else {
                errors.addAll(validator.validateObject(object, deltas));
            }
        }

        return errors;
    }

    private Collection<MidpointFormValidator> getValidators() {
        return modelServiceLocator.getFormValidatorRegistry().getValidators();
    }

    protected void prepareObjectForAdd(PrismObject<O> objectToAdd) throws SchemaException {

    }

    protected void prepareObjectDeltaForModify(ObjectDelta<O> modifyDelta) throws SchemaException {

    }

    protected List<ObjectDelta<? extends ObjectType>> getAdditionalModifyDeltas(OperationResult result) {
        return new ArrayList<>();
    }


    public void reset() {
        prismObjectModel.reset();
        objectWrapperModel.reset();
        detailsPageConfigurationModel.reset();
        summaryModel.reset();
    }

    protected ModelServiceLocator getModelServiceLocator() {
        return modelServiceLocator;
    }

    protected AdminGuiConfigurationMergeManager getAdminGuiConfigurationMergeManager() {
        return modelServiceLocator.getAdminGuiConfigurationMergeManager();
    }
}
