/*
 * Copyright (c) 2010-2018 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.task.quartzimpl;

/**
 * TODO
 */
public class MockWorkBucketsTaskHandler { //implements TaskHandler {

//    private static final Trace LOGGER = TraceManager.getTrace(MockWorkBucketsTaskHandler.class);
//
//    private static final String PART_URI = "part";
//
//    private TaskManagerQuartzImpl taskManager;
//    private final String id;
//
//    MockWorkBucketsTaskHandler(String id, TaskManagerQuartzImpl taskManager) {
//        this.id = id;
//        this.taskManager = taskManager;
//    }
//
//    @FunctionalInterface
//    public interface Processor {
//        void process(RunningTask task, WorkBucketType bucket, int index);
//    }
//
//    private Processor processor;
//
//    private boolean hasRun = false;
//
//    private int itemsProcessed;
//
//    private ObjectQuery defaultQuery;
//
//    private final List<ObjectQuery> queriesExecuted = new ArrayList<>();
//
//    private Task runningTask;
//
//    private boolean ensureSingleRunner;
//
//    private Throwable failure;
//
//    @NotNull
//    @Override
//    public StatisticsCollectionStrategy getStatisticsCollectionStrategy() {
//        return new StatisticsCollectionStrategy(true, false, false, true);
//    }
//
//    @Override
//    public TaskWorkBucketProcessingResult run(RunningTask task, WorkBucketType workBucket,
//            ActivityDefinitionType partition, TaskWorkBucketProcessingResult previousRunResult) {
//        LOGGER.info("Run starting (id = {}); task = {}", id, task);
//        task.setStructuredProgressPartInformation(PART_URI, 1, 1);
//
//        if (ensureSingleRunner) {
//            if (runningTask != null) {
//                String message = "Detected concurrent running tasks: existing = " + runningTask + ", new = " + task;
//                System.err.println("*** " + message);
//                failure = new AssertionError(message);
//                throw (AssertionError) failure;
//            }
//            runningTask = task;
//        }
//
//        OperationResult opResult = new OperationResult(MockWorkBucketsTaskHandler.class.getName()+".run");
//        opResult.recordSuccess();
//
//        if (defaultQuery != null) {
//            ObjectQuery narrowedQuery;
//            try {
//                narrowedQuery = taskManager.narrowQueryForWorkBucket(UserType.class, defaultQuery, task, null, workBucket, opResult);
//            } catch (SchemaException | ObjectNotFoundException e) {
//                throw new SystemException("Couldn't narrow query for work bucket", e);
//            }
//            queriesExecuted.add(narrowedQuery);
//            LOGGER.info("Using narrowed query in task {}:\n{}", task, narrowedQuery.debugDump());
//        }
//
//        NumericIntervalWorkBucketContentType content = (NumericIntervalWorkBucketContentType) workBucket.getContent();
//        if (content == null || content.getFrom() == null) {
//            LOGGER.info("Processing bucket {}; task = {}", content, task);
//            itemsProcessed++;
//            if (processor != null) {
//                processor.process(task, workBucket, 0);
//            }
//            task.incrementProgressAndStoreStatisticsIfTimePassed(opResult);
//        } else {
//            int from = content.getFrom().intValue();
//            int to = content.getTo().intValue(); // beware of nullability
//            LOGGER.info("Processing bucket {}; task = {}", content, task);
//            for (int i = from; i < to; i++) {
//                String objectName = "item " + i;
//                String objectOid = String.valueOf(i);
//                IterationItemInformation info = new IterationItemInformation(objectName, null, ObjectType.COMPLEX_TYPE, objectOid);
//                Operation op = task.recordIterativeOperationStart(info);
//                LOGGER.info("Processing item #{}; task = {}", i, task);
//                itemsProcessed++;
//                if (processor != null) {
//                    processor.process(task, workBucket, i);
//                }
//                op.succeeded();
//                QualifiedItemProcessingOutcomeType outcome = new QualifiedItemProcessingOutcomeType(getPrismContext())
//                        .outcome(ItemProcessingOutcomeType.SUCCESS)
//                        .qualifierUri("some-qualifier");
//                task.incrementStructuredProgress(PART_URI, outcome);
//                task.incrementProgressAndStoreStatisticsIfTimePassed(opResult);
//            }
//        }
//
//        TaskWorkBucketProcessingResult runResult = previousRunResult != null ? previousRunResult : new TaskWorkBucketProcessingResult();
//        runResult.setOperationResult(opResult);
//        runResult.setRunResultStatus(TaskRunResultStatus.FINISHED);
//        runResult.setBucketComplete(true);
//
//        hasRun = true;
//        runningTask = null;
//
//        LOGGER.info("Run stopping; task = {}", task);
//        task.updateAndStoreStatisticsIntoRepository(true, opResult);
//
//        LOGGER.info("Task structured progress:\n{}", StructuredTaskProgress.format(task.getStructuredProgressOrClone()));
//        LOGGER.info("Task iterative information:\n{}", IterativeTaskInformation.format(task.getStoredOperationStatsOrClone().getIterativeTaskInformation()));
//        return runResult;
//    }
//
//    private PrismContext getPrismContext() {
//        return taskManager.getPrismContext();
//    }
//
//    @Override
//    public Long heartbeat(Task task) {
//        return null;
//    }
//
//    @Override
//    public void refreshStatus(Task task) {
//    }
//
//    @SuppressWarnings("unused")
//    public boolean hasRun() {
//        return hasRun;
//    }
//
//    @SuppressWarnings("unused")
//    public void resetHasRun() {
//        hasRun = false;
//    }
//
//    @Override
//    public String getCategoryName(Task task) {
//        return TaskCategory.MOCK;
//    }
//
//    public TaskManagerQuartzImpl getTaskManager() {
//        return taskManager;
//    }
//
//    public void setTaskManager(TaskManagerQuartzImpl taskManager) {
//        this.taskManager = taskManager;
//    }
//
//    public void setProcessor(Processor processor) {
//        this.processor = processor;
//    }
//
//    void setDelayProcessor(long delay) {
//        setProcessor((task, bucket, i) -> {
//            if (delay > 0) {
//                LOGGER.info("Sleeping for {} ms; task = {}", delay, task);
//                try {
//                    Thread.sleep(delay);
//                } catch (InterruptedException e) {
//                    // TODO
//                }
//            }
//        });
//    }
//
//    @SuppressWarnings("unused")
//    public ObjectQuery getDefaultQuery() {
//        return defaultQuery;
//    }
//
//    void setDefaultQuery(ObjectQuery defaultQuery) {
//        this.defaultQuery = defaultQuery;
//    }
//
//    List<ObjectQuery> getQueriesExecuted() {
//        return queriesExecuted;
//    }
//
//    void resetBeforeTest() {
//        defaultQuery = null;
//        queriesExecuted.clear();
//        processor = null;
//        itemsProcessed = 0;
//        ensureSingleRunner = false;
//        runningTask = null;
//        failure = null;
//    }
//
//    int getItemsProcessed() {
//        return itemsProcessed;
//    }
//
//    @SuppressWarnings("unused")
//    public boolean isEnsureSingleRunner() {
//        return ensureSingleRunner;
//    }
//
//    @SuppressWarnings("SameParameterValue")
//    void setEnsureSingleRunner(boolean ensureSingleRunner) {
//        this.ensureSingleRunner = ensureSingleRunner;
//    }
//
//    public Throwable getFailure() {
//        return failure;
//    }
//
//    @Override
//    public String getArchetypeOid() {
//        return SystemObjectsType.ARCHETYPE_UTILITY_TASK.value();
//    }
}
