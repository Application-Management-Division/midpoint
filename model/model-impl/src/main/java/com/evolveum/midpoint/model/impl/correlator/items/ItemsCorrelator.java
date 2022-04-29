/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.impl.correlator.items;

import static com.evolveum.midpoint.util.DebugUtil.lazy;
import static com.evolveum.midpoint.util.MiscUtil.configCheck;

import java.util.ArrayList;
import java.util.List;

import com.evolveum.midpoint.prism.query.ObjectFilter;

import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.model.api.correlator.CorrelationContext;
import com.evolveum.midpoint.model.api.correlator.CorrelationResult;
import com.evolveum.midpoint.model.api.correlator.CorrelatorContext;
import com.evolveum.midpoint.model.impl.ModelBeans;
import com.evolveum.midpoint.model.impl.correlator.BaseCorrelator;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.PrettyPrinter;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ItemsCorrelatorType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;

import org.jetbrains.annotations.Nullable;

/**
 * A "user-friendly" correlator based on a list of items that need to be matched between the source
 * (usually the pre-focus, but a shadow is acceptable here as well), and the target (set of focal objects).
 */
class ItemsCorrelator extends BaseCorrelator<ItemsCorrelatorType> {

    private static final Trace LOGGER = TraceManager.getTrace(ItemsCorrelator.class);

    ItemsCorrelator(@NotNull CorrelatorContext<ItemsCorrelatorType> correlatorContext, @NotNull ModelBeans beans) {
        super(LOGGER, "items", correlatorContext, beans);
    }

    @Override
    public @NotNull CorrelationResult correlateInternal(
            @NotNull CorrelationContext correlationContext,
            @NotNull OperationResult result)
            throws SchemaException, ExpressionEvaluationException, CommunicationException, SecurityViolationException,
            ConfigurationException, ObjectNotFoundException {
        return new Correlation<>(correlationContext)
                .correlate(result);
    }

    @Override
    protected boolean checkCandidateOwnerInternal(
            @NotNull CorrelationContext correlationContext,
            @NotNull FocusType candidateOwner,
            @NotNull OperationResult result)
            throws ConfigurationException, SchemaException {
        return new Correlation<>(correlationContext)
                .checkCandidateOwner(candidateOwner);
    }

    private class Correlation<F extends FocusType> {

        @NotNull private final ShadowType resourceObject;
        @NotNull private final CorrelationContext correlationContext;
        @NotNull private final String contextDescription;

        Correlation(@NotNull CorrelationContext correlationContext) {
            this.resourceObject = correlationContext.getResourceObject();
            this.correlationContext = correlationContext;
            this.contextDescription = getDefaultContextDescription(correlationContext);
        }

        public CorrelationResult correlate(OperationResult result)
                throws SchemaException, ExpressionEvaluationException, CommunicationException, SecurityViolationException,
                ConfigurationException, ObjectNotFoundException {

            List<F> candidates = findCandidates(result);
            return beans.builtInResultCreator.createCorrelationResult(candidates, correlationContext);
        }

        private @NotNull List<F> findCandidates(OperationResult result)
                throws SchemaException, ConfigurationException {

            CorrelationItems correlationItems = createCorrelationItems();

            List<F> allCandidates = findCandidates(correlationItems, result);

            LOGGER.debug("Found {} owner candidates for {} using {} correlation item(s) in {}: {}",
                    allCandidates.size(), resourceObject, correlationItems.size(), contextDescription,
                    lazy(() -> PrettyPrinter.prettyPrint(allCandidates, 3)));

            return allCandidates;
        }

        boolean checkCandidateOwner(F candidateOwner) throws SchemaException, ConfigurationException {
            CorrelationItems correlationItems = createCorrelationItems();

            boolean matches = checkCandidateOwner(correlationItems, candidateOwner);

            LOGGER.debug("Does candidate owner {} for {} using {} correlation item(s) in {} match: {}",
                    candidateOwner, resourceObject, correlationItems.size(), contextDescription, matches);

            return matches;
        }

        private @NotNull CorrelationItems createCorrelationItems() throws ConfigurationException {
            CorrelationItems items = CorrelationItems.create(correlatorContext, correlationContext);
            configCheck(!items.isEmpty(), "No items specified in %s", contextDescription);
            LOGGER.trace("Going to find candidates (or check candidate) using {} conditional items(s) in {}",
                    items.size(), contextDescription);
            return items;
        }

        @NotNull private List<F> findCandidates(
                CorrelationItems correlationItems, OperationResult result)
                throws SchemaException, ConfigurationException {

            List<ObjectQuery> queries = createQueries(correlationItems);
            if (queries == null) {
                return List.of();
            }

            List<F> candidates = new ArrayList<>();
            for (ObjectQuery query : queries) {
                executeQuery(query, candidates, result);
            }
            return candidates;
        }

        /** Returns `null` if we cannot use the definitions here. */
        private @Nullable List<ObjectQuery> createQueries(CorrelationItems correlationItems)
                throws SchemaException, ConfigurationException {
            if (areItemsApplicable(correlationItems)) {
                List<ObjectQuery> queries = correlationItems.createQueries(correlationContext.getFocusType());
                LOGGER.debug("Correlation items specification resulted in {} queries", queries.size());
                return queries;
            } else {
                return null;
            }
        }

        private boolean areItemsApplicable(CorrelationItems correlationItems) throws SchemaException {
            assert !correlationItems.isEmpty();

            for (CorrelationItem item : correlationItems.getItems()) {
                if (!item.isApplicable()) {
                    LOGGER.trace("Correlation item {} forbids us to use this correlator", item);
                    return false;
                }
            }
            return true;
        }

        private boolean checkCandidateOwner(CorrelationItems correlationItems, F candidateOwner)
                throws SchemaException, ConfigurationException {

            List<ObjectQuery> queries = createQueries(correlationItems);
            if (queries == null) {
                return false;
            }

            for (ObjectQuery query : queries) {
                if (candidateOwnerMatches(query, candidateOwner)) {
                    return true;
                }
            }
            return false;
        }

        private void executeQuery(ObjectQuery query, List<F> candidates, OperationResult gResult) throws SchemaException {
            LOGGER.trace("Using the following query to find owner candidates:\n{}", query.debugDumpLazily(1));
            // TODO use read-only option in the future (but is it OK to start a clockwork with immutable object?)
            //noinspection unchecked
            beans.cacheRepositoryService.searchObjectsIterative(
                    (Class<F>) correlationContext.getFocusType(),
                    query,
                    (object, lResult) -> addToCandidates(object.asObjectable(), candidates),
                    null,
                    true,
                    gResult);
        }

        private boolean addToCandidates(F object, List<F> candidates) {
            if (candidates.stream()
                    .noneMatch(candidate -> candidate.getOid().equals(object.getOid()))) {
                candidates.add(object);
                if (candidates.size() > MAX_CANDIDATES) {
                    // TEMPORARY
                    throw new SystemException("Maximum number of candidate focus objects was exceeded: " + MAX_CANDIDATES);
                }
            }
            return true;
        }

        private boolean candidateOwnerMatches(ObjectQuery query, F candidateOwner) throws SchemaException {
            LOGGER.trace("Checking the following query:\n{}\nregarding the candidate:\n{}",
                    query.debugDumpLazily(1),
                    candidateOwner.debugDumpLazily(1));
            ObjectFilter filter = query.getFilter();
            return filter == null || filter.match(candidateOwner.asPrismContainerValue(), beans.matchingRuleRegistry);
        }
    }
}
