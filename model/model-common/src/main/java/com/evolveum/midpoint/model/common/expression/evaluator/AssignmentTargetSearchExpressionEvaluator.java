/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.model.common.expression.evaluator;

import java.util.List;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.common.LocalizationService;
import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.crypto.Protector;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ItemDeltaCollectionsUtil;
import com.evolveum.midpoint.prism.delta.PlusMinusZero;
import com.evolveum.midpoint.repo.common.ObjectResolver;
import com.evolveum.midpoint.repo.common.expression.ExpressionEvaluationContext;
import com.evolveum.midpoint.schema.expression.VariablesMap;
import com.evolveum.midpoint.schema.internals.InternalsConfig;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

/**
 * Creates an assignment (or assignments) based on specified conditions for the assignment target.
 * Can create target objects on demand.
 *
 * @author Radovan Semancik
 */
public class AssignmentTargetSearchExpressionEvaluator
            extends AbstractSearchExpressionEvaluator<
                PrismContainerValue<AssignmentType>,
                AssignmentHolderType,
                PrismContainerDefinition<AssignmentType>,
                AssignmentTargetSearchExpressionEvaluatorType> {

    AssignmentTargetSearchExpressionEvaluator(
            QName elementName,
            AssignmentTargetSearchExpressionEvaluatorType expressionEvaluatorType,
            PrismContainerDefinition<AssignmentType> outputDefinition,
            Protector protector,
            ObjectResolver objectResolver,
            LocalizationService localizationService) {
        super(
                elementName,
                expressionEvaluatorType,
                outputDefinition,
                protector,
                objectResolver,
                localizationService);
    }

    @Override
    Evaluation createEvaluation(
            VariablesMap variables,
            PlusMinusZero valueDestination,
            boolean useNew,
            ExpressionEvaluationContext context,
            String contextDescription,
            Task task,
            OperationResult result) throws SchemaException {

        return new Evaluation(variables, valueDestination, useNew, context, contextDescription, task, result) {

            protected PrismContainerValue<AssignmentType> createResultValue(
                    String oid,
                    PrismObject<AssignmentHolderType> object,
                    List<ItemDelta<PrismContainerValue<AssignmentType>, PrismContainerDefinition<AssignmentType>>> newValueDeltas)
                    throws SchemaException {

                AssignmentPropertiesSpecificationType assignmentPropertiesSpec =
                        expressionEvaluatorBean.getAssignmentProperties();

                ObjectReferenceType ref =
                        new ObjectReferenceType()
                                .oid(oid)
                                .type(targetTypeQName)
                                .relation(assignmentPropertiesSpec != null ? assignmentPropertiesSpec.getRelation() : null);
                ref.asReferenceValue().setObject(object);

                AssignmentType assignment = new AssignmentType().targetRef(ref);
                if (assignmentPropertiesSpec != null) {
                    assignment.getSubtype().addAll(
                            assignmentPropertiesSpec.getSubtype());
                }

                //noinspection unchecked
                PrismContainerValue<AssignmentType> assignmentCVal = assignment.asPrismContainerValue();
                if (newValueDeltas != null) {
                    ItemDeltaCollectionsUtil.applyTo(newValueDeltas, assignmentCVal);
                }
                prismContext.adopt(assignmentCVal, FocusType.COMPLEX_TYPE, FocusType.F_ASSIGNMENT);
                if (InternalsConfig.consistencyChecks) {
                    assignmentCVal.assertDefinitions(
                            () -> "assignmentCVal in assignment expression in " + context.getContextDescription());
                }
                return assignmentCVal;
            }
        };
    }

    @Override
    public String shortDebugDump() {
        return "assignmentTargetSearchExpression";
    }
}
