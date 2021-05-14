/*
 * Copyright (c) 2010-2015 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.schema.statistics;

import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.util.CloneUtil;
import com.evolveum.midpoint.prism.xml.XmlTypeConverter;
import com.evolveum.midpoint.schema.util.task.WallClockTimeComputer;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.apache.commons.collections.buffer.CircularFifoBuffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.datatype.XMLGregorianCalendar;
import java.util.*;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static com.evolveum.midpoint.util.MiscUtil.or0;

import static java.util.Objects.requireNonNull;

/**
 * This is "live" iterative task information.
 *
 * BEWARE: When explicitly enabled, automatically updates also the structured progress when recording operation end.
 * This is somewhat experimental and should be reconsidered.
 *
 * Thread safety: Must be thread safe.
 *
 * 1. Updates are invoked in the context of the thread executing the task.
 * 2. But queries are invoked either from this thread, or from some observer (task manager or GUI thread).
 *
 * Implementation: Because the iterative task information grew to quite complex structure,
 * we no longer keep "native" form and "bean" form separately. Now we simply store the native
 * form, and provide the necessary synchronization.
 *
 * Also, we no longer distinguish start value and delta. Everything is kept in the {@link #value}.
 */
public class IterativeTaskInformation {

    private static final Trace LOGGER = TraceManager.getTrace(IterativeTaskInformation.class);

    private static final AtomicLong ID_COUNTER = new AtomicLong(0);

    @Deprecated
    public static final int LAST_FAILURES_KEPT = 30;

    /** Current value */
    @NotNull private final IterativeTaskInformationType value = new IterativeTaskInformationType();

    @Deprecated
    protected CircularFifoBuffer lastFailures = new CircularFifoBuffer(LAST_FAILURES_KEPT);

    @NotNull
    private final PrismContext prismContext;

    /**
     * For tasks that aggregate operation statistics over significant periods of time (i.e. those that
     * do not start from zero) we would like to avoid execution records to grow indefinitely.
     * As the easiest solution (for 4.3) is to simply stop collecting this information for such tasks.
     */
    private final boolean collectExecutions;

    public IterativeTaskInformation(@NotNull PrismContext prismContext) {
        this.prismContext = prismContext;
        this.collectExecutions = false;
    }

    public IterativeTaskInformation(IterativeTaskInformationType value, boolean collectExecutions,
            @NotNull PrismContext prismContext) {
        this.prismContext = prismContext;
        this.collectExecutions = collectExecutions;
        if (value != null) {
            addTo(this.value, value);
        }
    }

    /** Returns a current value of this statistics. It is copied because of thread safety issues. */
    public synchronized IterativeTaskInformationType getValueCopy() {
        return value.cloneWithoutId();
    }

    /**
     * Records an operation that has been just started. Stores it into the list of current operations.
     * Returns an object that should receive the status of the operation, in order to record
     * the operation end.
     */
    public synchronized Operation recordOperationStart(IterativeOperationStartInfo startInfo) {
        IterationItemInformation item = startInfo.getItem();
        ProcessedItemType processedItem = new ProcessedItemType(prismContext)
                .name(item.getObjectName())
                .displayName(item.getObjectDisplayName())
                .type(item.getObjectType())
                .oid(item.getObjectOid())
                .startTimestamp(XmlTypeConverter.createXMLGregorianCalendar(startInfo.getStartTimeMillis()))
                .operationId(getNextOperationId());

        IterativeTaskPartItemsProcessingInformationType matchingPart =
                findOrCreateMatchingPart(value.getPart(), startInfo.getPartIdentifier());
        updatePartExecutions(matchingPart, startInfo.getPartStartTimestamp(), System.currentTimeMillis());

        List<ProcessedItemType> currentList = matchingPart.getCurrent();
        currentList.add(processedItem);
        LOGGER.trace("Recorded current operation. Current list size: {}. Operation: {}", currentList.size(), startInfo);
        return new OperationImpl(startInfo, processedItem);
    }

    public synchronized void recordPartExecutionEnd(String partUri, long partStartTimestamp, long partEndTimestamp) {
        IterativeTaskPartItemsProcessingInformationType matchingPart =
                findOrCreateMatchingPart(value.getPart(), partUri);
        updatePartExecutions(matchingPart, partStartTimestamp, partEndTimestamp);
    }

    private void updatePartExecutions(IterativeTaskPartItemsProcessingInformationType part, Long partStartTimestamp,
            long currentTimeMillis) {
        if (!collectExecutions) {
            return;
        }
        if (partStartTimestamp == null) {
            return;
        }
        findOrCreateMatchingExecutionRecord(part.getExecution(), partStartTimestamp)
                .setEndTimestamp(XmlTypeConverter.createXMLGregorianCalendar(currentTimeMillis));
    }

    private TaskPartExecutionRecordType findOrCreateMatchingExecutionRecord(List<TaskPartExecutionRecordType> records,
            long partStartTimestamp) {
        XMLGregorianCalendar startAsGregorian = XmlTypeConverter.createXMLGregorianCalendar(partStartTimestamp);
        for (TaskPartExecutionRecordType record : records) {
            if (startAsGregorian.equals(record.getStartTimestamp())) {
                return record;
            }
        }
        TaskPartExecutionRecordType newRecord = new TaskPartExecutionRecordType(prismContext)
                .startTimestamp(startAsGregorian);
        records.add(newRecord);
        return newRecord;
    }

    /**
     * Records the operation end. Must be synchronized because it is called externally (through Operation interface).
     */
    private synchronized void recordOperationEnd(OperationImpl operation, QualifiedItemProcessingOutcomeType outcome,
            Throwable exception) {
        String partUri = operation.startInfo.getPartIdentifier();

        Optional<IterativeTaskPartItemsProcessingInformationType> matchingPartOptional =
                findMatchingPart(value.getPart(), partUri);
        if (matchingPartOptional.isPresent()) {
            recordOperationEndToPart(matchingPartOptional.get(), operation, outcome, exception);
        } else {
            LOGGER.warn("Couldn't record operation end. Task part {} was not found for {}",
                    partUri, operation);
        }
    }

    /** The actual recording of operation end. */
    private void recordOperationEndToPart(IterativeTaskPartItemsProcessingInformationType part, OperationImpl operation,
            QualifiedItemProcessingOutcomeType outcome, Throwable exception) {

        removeFromCurrentOperations(part, operation.operationId);
        addToProcessedItemSet(part, operation, outcome, exception);

        // We use operation end time to ensure consistency with other statistics that rely on operation end time.
        updatePartExecutions(part, operation.startInfo.getPartStartTimestamp(), operation.endTimeMillis);
    }

    /** Updates the corresponding `processed` statistics. Creates and stores appropriate `lastItem` record. */
    private void addToProcessedItemSet(IterativeTaskPartItemsProcessingInformationType part, OperationImpl operation,
            QualifiedItemProcessingOutcomeType outcome, Throwable exception) {

        ProcessedItemSetType itemSet = findOrCreateProcessedItemSet(part, outcome);
        itemSet.setCount(or0(itemSet.getCount()) + 1);

        itemSet.setDuration(or0(itemSet.getDuration()) + operation.getDurationRounded());
        ProcessedItemType processedItemClone = operation.processedItem.cloneWithoutId(); // mainly to remove the parent
        processedItemClone.setEndTimestamp(XmlTypeConverter.createXMLGregorianCalendar(operation.endTimeMillis));
        if (exception != null) {
            processedItemClone.setMessage(exception.getMessage());
        }
        itemSet.setLastItem(processedItemClone);
    }

    /** Finds item set, creating _and adding to the list_ if necessary. */
    private ProcessedItemSetType findOrCreateProcessedItemSet(IterativeTaskPartItemsProcessingInformationType part,
            QualifiedItemProcessingOutcomeType outcome) {
        return part.getProcessed().stream()
                .filter(itemSet -> Objects.equals(itemSet.getOutcome(), outcome))
                .findFirst()
                .orElseGet(
                        () -> add(part.getProcessed(), new ProcessedItemSetType(prismContext).outcome(outcome.cloneWithoutId())));
    }

    /** Like {@link List#add(Object)} but returns the value. */
    private static <T> T add(List<T> list, T value) {
        list.add(value);
        return value;
    }

    /** Removes operation (given by id) from current operations in given task part. */
    private void removeFromCurrentOperations(IterativeTaskPartItemsProcessingInformationType part, long operationId) {
        boolean removed = part.getCurrent().removeIf(item -> Objects.equals(operationId, item.getOperationId()));
        if (!removed) {
            LOGGER.warn("Couldn't remove operation {} from the list of current operations: {}", operationId, part.getCurrent());
        }
    }

    private static long getNextOperationId() {
        return ID_COUNTER.getAndIncrement();
    }

    /** Updates specified summary with given delta. */
    public static void addTo(@NotNull IterativeTaskInformationType sum, @Nullable IterativeTaskInformationType delta) {
        if (delta != null) {
            addMatchingParts(sum.getPart(), delta.getPart());
        }
    }

    /** Looks for matching parts (created if necessary) and adds them. */
    private static void addMatchingParts(List<IterativeTaskPartItemsProcessingInformationType> sumParts,
            List<IterativeTaskPartItemsProcessingInformationType> deltaParts) {
        for (IterativeTaskPartItemsProcessingInformationType deltaPart : deltaParts) {
            IterativeTaskPartItemsProcessingInformationType matchingPart =
                    findOrCreateMatchingPart(sumParts, deltaPart.getPartIdentifier());
            addPartInformation(matchingPart, deltaPart);
        }
    }

    private static IterativeTaskPartItemsProcessingInformationType findOrCreateMatchingPart(
            @NotNull List<IterativeTaskPartItemsProcessingInformationType> list, String partUri) {
        return findMatchingPart(list, partUri)
                .orElseGet(
                        () -> add(list, new IterativeTaskPartItemsProcessingInformationType().partIdentifier(partUri)));
    }

    private static Optional<IterativeTaskPartItemsProcessingInformationType> findMatchingPart(
            @NotNull List<IterativeTaskPartItemsProcessingInformationType> list, String partUri) {
        return list.stream()
                .filter(item -> Objects.equals(item.getPartIdentifier(), partUri))
                .findFirst();
    }

    /** Adds two "part information" */
    private static void addPartInformation(@NotNull IterativeTaskPartItemsProcessingInformationType sum,
            @NotNull IterativeTaskPartItemsProcessingInformationType delta) {
        addProcessed(sum.getProcessed(), delta.getProcessed());
        addCurrent(sum.getCurrent(), delta.getCurrent());
        addExecutionRecords(sum, delta);
    }

    private static void addExecutionRecords(@NotNull IterativeTaskPartItemsProcessingInformationType sum, @NotNull IterativeTaskPartItemsProcessingInformationType delta) {
        List<TaskPartExecutionRecordType> nonOverlappingRecords =
                new WallClockTimeComputer(sum.getExecution(), delta.getExecution())
                        .getNonOverlappingRecords();
        sum.getExecution().clear();
        nonOverlappingRecords.sort(Comparator.comparing(r -> XmlTypeConverter.toMillis(r.getStartTimestamp())));
        sum.getExecution().addAll(CloneUtil.cloneCollectionMembersWithoutIds(nonOverlappingRecords));
    }

    /** Adds `processed` items information */
    private static void addProcessed(@NotNull List<ProcessedItemSetType> sumSets, @NotNull List<ProcessedItemSetType> deltaSets) {
        for (ProcessedItemSetType deltaSet : deltaSets) {
            ProcessedItemSetType matchingSet =
                    findOrCreateMatchingSet(sumSets, deltaSet.getOutcome());
            addMatchingProcessedItemSets(matchingSet, deltaSet);
        }
    }

    private static ProcessedItemSetType findOrCreateMatchingSet(
            @NotNull List<ProcessedItemSetType> list, QualifiedItemProcessingOutcomeType outcome) {
        return list.stream()
                .filter(item -> Objects.equals(item.getOutcome(), outcome))
                .findFirst()
                .orElseGet(
                        () -> add(list, new ProcessedItemSetType().outcome(outcome)));
    }

    /** Adds matching processed item sets: adds counters and determines the latest `lastItem`. */
    private static void addMatchingProcessedItemSets(@NotNull ProcessedItemSetType sum, @NotNull ProcessedItemSetType delta) {
        sum.setCount(or0(sum.getCount()) + or0(delta.getCount()));
        sum.setDuration(or0(sum.getDuration()) + or0(delta.getDuration()));
        if (delta.getLastItem() != null) {
            if (sum.getLastItem() == null ||
                    XmlTypeConverter.isAfterNullLast(delta.getLastItem().getEndTimestamp(), sum.getLastItem().getEndTimestamp())) {
                sum.setLastItem(delta.getLastItem());
            }
        }
    }

    /** Adds `current` items information (simply concatenates the lists). */
    private static void addCurrent(List<ProcessedItemType> sum, List<ProcessedItemType> delta) {
        sum.addAll(CloneUtil.cloneCollectionMembersWithoutIds(delta)); // to avoid problems with parent and IDs
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Deprecated
    public List<String> getLastFailures() {
        return new ArrayList<>(lastFailures);
    }

    public static String format(IterativeTaskInformationType source) {
        return format(source, null);
    }

    // TODO reconsider
    public static String format(List<IterativeTaskInformationType> sources) {
        StringBuilder sb = new StringBuilder();
        for (IterativeTaskInformationType source : sources) {
            sb.append(format(source));
        }
        return sb.toString();
    }

    /** Formats the information. */
    public static String format(IterativeTaskInformationType source, AbstractStatisticsPrinter.Options options) {
        IterativeTaskInformationType information = source != null ? source : new IterativeTaskInformationType();
        return new IterativeTaskInformationPrinter(information, options).print();
    }

    public boolean isCollectExecutions() {
        return collectExecutions;
    }

    /**
     * Operation being recorded: represents an object to which the client reports the end of the operation.
     * It is called simply {@link Operation} to avoid confusing the clients.
     */
    public interface Operation {

        default void succeeded() {
            done(ItemProcessingOutcomeType.SUCCESS, null);
        }

        default void skipped() {
            done(ItemProcessingOutcomeType.SKIP, null);
        }

        default void failed(Throwable t) {
            done(ItemProcessingOutcomeType.FAILURE, t);
        }

        default void done(ItemProcessingOutcomeType outcome, Throwable exception) {
            QualifiedItemProcessingOutcomeType qualifiedOutcome =
                    new QualifiedItemProcessingOutcomeType(PrismContext.get())
                            .outcome(outcome);
            done(qualifiedOutcome, exception);
        }

        void done(QualifiedItemProcessingOutcomeType outcome, Throwable exception);

        double getDurationRounded();

        long getEndTimeMillis();
    }

    /**
     * Real implementation of the {@link Operation} interface.
     */
    public class OperationImpl implements Operation {

        /** Generated ID used for finding the operation among current operations. */
        private final long operationId;

        /** Client-supplied operation information. */
        @NotNull private final IterativeOperationStartInfo startInfo;

        /**
         * The processed item structure generated when recording operation start.
         * It is stored in the list of current operations.
         */
        @NotNull private final ProcessedItemType processedItem;

        private long endTimeMillis;
        private long endTimeNanos;

        OperationImpl(@NotNull IterativeOperationStartInfo startInfo, @NotNull ProcessedItemType processedItem) {
            // The processedItem is stored only in memory; so there is no way of having null here.
            this.operationId = requireNonNull(processedItem.getOperationId());
            this.startInfo = startInfo;
            this.processedItem = processedItem;
        }

        @Override
        public void done(QualifiedItemProcessingOutcomeType outcome, Throwable exception) {
//            System.out.println("DONE: " + startInfo);
            setEndTimes();
            recordOperationEnd(this, outcome, exception);
            StructuredProgressCollector progressCollector = startInfo.getStructuredProgressCollector();
            if (progressCollector != null) {
                progressCollector.incrementStructuredProgress(startInfo.getPartIdentifier(), outcome);
            }
        }

        private void setEndTimes() {
            endTimeMillis = System.currentTimeMillis();
            endTimeNanos = System.nanoTime();
        }

        @Override
        public double getDurationRounded() {
            if (endTimeNanos == 0) {
                throw new IllegalStateException("Operation has not finished yet");
            } else {
                double tensOfMicroseconds = Math.round((endTimeNanos - startInfo.getStartTimeNanos()) / 10000.0);
                return tensOfMicroseconds / 100.0;
            }
        }

        @Override
        public long getEndTimeMillis() {
            return endTimeMillis;
        }
    }
}
