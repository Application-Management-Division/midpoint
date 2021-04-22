/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.schema.util.task;

import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static java.util.Collections.emptyList;

/**
 * Utility methods related to task work state and work state management.
 */
public class TaskWorkStateUtil {

    public static WorkBucketType findBucketByNumber(List<WorkBucketType> buckets, int sequentialNumber) {
        return buckets.stream()
                .filter(b -> b.getSequentialNumber() == sequentialNumber)
                .findFirst().orElse(null);
    }

    // beware: do not call this on prism structure directly (it does not support setting values)
    public static void sortBucketsBySequentialNumber(List<WorkBucketType> buckets) {
        buckets.sort(Comparator.comparingInt(WorkBucketType::getSequentialNumber));
    }

    public static AbstractWorkSegmentationType getWorkSegmentationConfiguration(TaskWorkManagementType cfg) {
        if (cfg != null) {
            return getWorkSegmentationConfiguration(cfg.getBuckets());
        } else {
            return null;
        }
    }

    @Nullable
    public static AbstractWorkSegmentationType getWorkSegmentationConfiguration(WorkBucketsManagementType buckets) {
        if (buckets != null) {
            return MiscUtil.getFirstNonNull(
                    buckets.getNumericSegmentation(),
                    buckets.getStringSegmentation(),
                    buckets.getOidSegmentation(),
                    buckets.getExplicitSegmentation(),
                    buckets.getSegmentation());
        } else {
            return null;
        }
    }

    public static int getCompleteBucketsNumber(TaskType taskType) {
        return getCompleteBucketsNumber(taskType.getWorkState());
    }

    public static int getCompleteBucketsNumber(TaskWorkStateType workState) {
        if (workState == null) {
            return 0;
        }
        Integer max = null;
        int notComplete = 0;
        // TODO
//        for (WorkBucketType bucket : workState.getBucket()) {
//            if (max == null || bucket.getSequentialNumber() > max) {
//                max = bucket.getSequentialNumber();
//            }
//            if (bucket.getState() != WorkBucketStateType.COMPLETE) {
//                notComplete++;
//            }
//        }
        if (max == null) {
            return 0;
        } else {
            // what is not listed is assumed to be complete
            return max - notComplete;
        }
    }

    @Nullable
    public static Integer getExpectedBuckets(TaskType task) {
        return null; // TODO task.getWorkState() != null ? task.getWorkState().getNumberOfBuckets() : null;
    }

    private static Integer getFirstBucketNumber(@NotNull TaskWorkStateType workState) {
        return null; // TODO
//        return workState.getBucket().stream()
//                .map(WorkBucketType::getSequentialNumber)
//                .min(Integer::compareTo).orElse(null);
    }

    @Nullable
    public static WorkBucketType getLastBucket(List<WorkBucketType> buckets) {
        WorkBucketType lastBucket = null;
        for (WorkBucketType bucket : buckets) {
            if (lastBucket == null || lastBucket.getSequentialNumber() < bucket.getSequentialNumber()) {
                lastBucket = bucket;
            }
        }
        return lastBucket;
    }

    public static boolean hasLimitations(WorkBucketType bucket) {
        if (bucket == null || bucket.getContent() == null) {
            return false;
        }
        if (bucket.getContent() instanceof NumericIntervalWorkBucketContentType) {
            NumericIntervalWorkBucketContentType numInterval = (NumericIntervalWorkBucketContentType) bucket.getContent();
            return numInterval.getTo() != null || numInterval.getFrom() != null && !BigInteger.ZERO.equals(numInterval.getFrom());
        } else if (bucket.getContent() instanceof StringIntervalWorkBucketContentType) {
            StringIntervalWorkBucketContentType stringInterval = (StringIntervalWorkBucketContentType) bucket.getContent();
            return stringInterval.getTo() != null || stringInterval.getFrom() != null;
        } else if (bucket.getContent() instanceof StringPrefixWorkBucketContentType) {
            StringPrefixWorkBucketContentType stringPrefix = (StringPrefixWorkBucketContentType) bucket.getContent();
            return !stringPrefix.getPrefix().isEmpty();
        } else if (bucket.getContent() instanceof FilterWorkBucketContentType) {
            FilterWorkBucketContentType filtered = (FilterWorkBucketContentType) bucket.getContent();
            return !filtered.getFilter().isEmpty();
        } else if (AbstractWorkBucketContentType.class.equals(bucket.getContent().getClass())) {
            return false;
        } else {
            throw new AssertionError("Unsupported bucket content: " + bucket.getContent());
        }
    }

    @Nullable
    public static Integer getPartitionSequentialNumber(@NotNull TaskType taskType) {
        return null;// TODO
        //return taskType.getWorkManagement() != null ? taskType.getWorkManagement().getPartitionSequentialNumber() : null;
    }

    /**
     * @return True if the task is a coordinator (in the bucketing sense).
     */
    public static boolean isCoordinator(TaskType task) {
        return getKind(task) == TaskKindType.COORDINATOR;
    }

    /**
     * @return True if the task is a partitioned master.
     */
    public static boolean isPartitionedMaster(TaskType task) {
        return getKind(task) == TaskKindType.PARTITIONED_MASTER;
    }

    /**
     * @return Task kind: standalone, coordinator, worker, partitioned master.
     */
    @NotNull
    public static TaskKindType getKind(TaskType task) {
        return TaskKindType.STANDALONE; // TODO
//        if (task.getWorkManagement() != null && task.getWorkManagement().getTaskKind() != null) {
//            return task.getWorkManagement().getTaskKind();
//        } else {
//            return TaskKindType.STANDALONE;
//        }
    }

    public static boolean isManageableTreeRoot(TaskType taskType) {
        return isCoordinator(taskType) || isPartitionedMaster(taskType);
    }

    public static boolean isWorkStateHolder(TaskType taskType) {
        return (isCoordinator(taskType) || hasBuckets(taskType)) && !isCoordinatedWorker(taskType);
    }

    static boolean hasBuckets(TaskType taskType) {
        if (taskType.getWorkState() == null) {
            return false;
        }
        //TODO
        return false;
//        if (taskType.getWorkState().getNumberOfBuckets() != null && taskType.getWorkState().getNumberOfBuckets() > 1) {
//            return true;
//        }
//        List<WorkBucketType> buckets = taskType.getWorkState().getBucket();
//        if (buckets.size() > 1) {
//            return true;
//        } else {
//            return buckets.size() == 1 && buckets.get(0).getContent() != null;
//        }
    }

    private static boolean isCoordinatedWorker(TaskType taskType) {
        return false;//TODO
        //return taskType.getWorkManagement() != null && TaskKindType.WORKER == taskType.getWorkManagement().getTaskKind();
    }

    public static boolean isAllWorkComplete(TaskType task) {
        return task.getWorkState() != null && Boolean.TRUE.equals(task.getWorkState().isAllWorkComplete());
    }

    @NotNull
    public static List<WorkBucketType> getBuckets(TaskWorkStateType workState) {
        if (workState == null) {
            return emptyList();
        }
        TaskPartWorkStateType partWorkState = getCurrentPartWorkState(workState);
        if (partWorkState == null) {
            return emptyList();
        }
        return partWorkState.getBucket();
    }

    @Nullable
    public static TaskPartWorkStateType getCurrentPartWorkState(TaskWorkStateType workState) {
        if (workState != null) {
            return getPartWorkState(workState, getCurrentPartId(workState));
        } else {
            return null;
        }
    }

    @Nullable
    public static TaskPartWorkStateType getPartWorkState(TaskWorkStateType workState, String partId) {
        if (workState != null) {
            return getPartWorkState(workState.getPart(), partId);
        } else {
            return null;
        }
    }

    @Nullable
    private static TaskPartWorkStateType getPartWorkState(List<TaskPartWorkStateType> parts, String partId) {
        for (TaskPartWorkStateType partState : parts) {
            if (Objects.equals(partState.getPartId(), partId)) {
                return partState;
            }
            TaskPartWorkStateType inChildren = getPartWorkState(partState.getPart(), partId);
            if (inChildren != null) {
                return inChildren;
            }
        }
        return null;
    }

    public static TaskPartDefinitionType getPartDefinition(TaskPartsDefinitionType parts, String partId) {
        if (parts == null) {
            return null;
        }
        for (TaskPartDefinitionType partDef : parts.getPart()) {
            if (java.util.Objects.equals(partDef.getIdentifier(), partId)) {
                return partDef;
            }
            TaskPartDefinitionType inChildren = getPartDefinition(partDef.getParts(), partId);
            if (inChildren != null) {
                return inChildren;
            }
        }
        return null;
    }

    public static boolean isStandalone(TaskWorkStateType workState) {
        BucketsProcessingRoleType bucketsProcessingRole = getBucketsProcessingRole(workState);
        return bucketsProcessingRole == null || bucketsProcessingRole == BucketsProcessingRoleType.STANDALONE;
    }

    public static BucketsProcessingRoleType getBucketsProcessingRole(TaskWorkStateType workState) {
        TaskPartWorkStateType partWorkState = TaskWorkStateUtil.getCurrentPartWorkState(workState);
        return partWorkState != null ? partWorkState.getBucketsProcessingRole() : null;
    }

    public static WorkBucketsManagementType getBucketsManagement(TaskPartsDefinitionType parts, String partId) {
        TaskWorkManagementType workManagement = getWorkManagement(parts, partId);
        return workManagement != null ? workManagement.getBuckets() : null;
    }

    public static TaskWorkManagementType getWorkManagement(TaskPartsDefinitionType parts, String partId) {
        TaskPartDefinitionType partDef = getPartDefinition(parts, partId);
        return partDef != null ? partDef.getWorkManagement() : null;
    }

    public static String getCurrentPartId(TaskWorkStateType workState) {
        return workState != null ? workState.getCurrentPartId() : null;
    }

    public static boolean isScavenger(TaskWorkStateType workState) {
        TaskPartWorkStateType partWorkState = getCurrentPartWorkState(workState);
        return partWorkState != null && Boolean.TRUE.equals(partWorkState.isScavenger());
    }
}
