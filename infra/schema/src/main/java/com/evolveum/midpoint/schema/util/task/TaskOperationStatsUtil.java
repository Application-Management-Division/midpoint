/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.schema.util.task;

import com.evolveum.midpoint.prism.Objectable;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismObjectDefinition;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.prism.util.CloneUtil;
import com.evolveum.midpoint.prism.xml.XmlTypeConverter;
import com.evolveum.midpoint.schema.statistics.*;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.namespace.QName;
import java.util.*;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.evolveum.midpoint.util.MiscUtil.or0;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;

/**
 * Utility methods related to task operation statistics.
 */
public class TaskOperationStatsUtil {

    /**
     * Returns the number of item processing failures from this task and its subtasks.
     * Subtasks must be resolved into to full objects.
     *
     * TODO Avoid useless statistics aggregation (avoid "first aggregating, then selecting failures")
     */
    @Deprecated
    public static int getItemsProcessedWithFailureFromTree(TaskType task, PrismContext prismContext) {
        return 0;
    }

    @Deprecated
    public static int getItemsProcessedWithFailure(TaskType task) {
        return 0;
    }

    @Deprecated
    public static int getItemsProcessedWithFailure(OperationStatsType stats) {
        return 0;
    }

    @Deprecated
    public static int getItemsProcessedWithSuccess(OperationStatsType stats) {
        return 0;
    }

    /**
     * Provides aggregated operation statistics from this task and all its subtasks.
     * Works with stored operation stats, obviously. (We have no task instances here.)
     *
     * Assumes that the task has all subtasks filled-in.
     *
     * Currently does NOT support some low-level performance statistics, namely:
     *
     * 1. cachesPerformanceInformation,
     * 2. operationsPerformanceInformation,
     * 3. cachingConfiguration.
     */
    public static OperationStatsType getOperationStatsFromTree(TaskType task, PrismContext prismContext) {
        if (!ActivityStateUtil.isPartitionedMaster(task) && !ActivityStateUtil.isWorkStateHolder(task)) {
            return task.getOperationStats();
        }

        OperationStatsType aggregate = new OperationStatsType(prismContext)
                .environmentalPerformanceInformation(new EnvironmentalPerformanceInformationType())
                .repositoryPerformanceInformation(new RepositoryPerformanceInformationType());

        Stream<TaskType> subTasks = TaskTreeUtil.getAllTasksStream(task);
        subTasks.forEach(subTask -> {
            OperationStatsType operationStatsBean = subTask.getOperationStats();
            if (operationStatsBean != null) {
                EnvironmentalPerformanceInformation.addTo(aggregate.getEnvironmentalPerformanceInformation(), operationStatsBean.getEnvironmentalPerformanceInformation());
                RepositoryPerformanceInformationUtil.addTo(aggregate.getRepositoryPerformanceInformation(), operationStatsBean.getRepositoryPerformanceInformation());
            }
        });
        return aggregate;
    }

    /**
     * Returns the number of "iterations" i.e. how many times an item was processed by this task.
     * It is useful e.g. to provide average values for performance indicators.
     */
    @Deprecated
    public static Integer getItemsProcessed(OperationStatsType statistics) {
        return null;
    }

    /**
     * Returns object that was last successfully processed by given task.
     */
    @Deprecated
    public static String getLastSuccessObjectName(TaskType task) {
        return "N/A";
    }

    public static boolean isEmpty(EnvironmentalPerformanceInformationType info) {
        return info == null ||
                (isEmpty(info.getProvisioningStatistics())
                && isEmpty(info.getMappingsStatistics())
                && isEmpty(info.getNotificationsStatistics())
                && info.getLastMessage() == null
                && info.getLastMessageTimestamp() == null);
    }

    public static boolean isEmpty(NotificationsStatisticsType notificationsStatistics) {
        return notificationsStatistics == null || notificationsStatistics.getEntry().isEmpty();
    }

    public static boolean isEmpty(MappingsStatisticsType mappingsStatistics) {
        return mappingsStatistics == null || mappingsStatistics.getEntry().isEmpty();
    }

    public static boolean isEmpty(ProvisioningStatisticsType provisioningStatistics) {
        return provisioningStatistics == null || provisioningStatistics.getEntry().isEmpty();
    }

    /**
     * Computes a sum of two operation statistics.
     * Returns a modifiable object, independent from the source ones.
     */
    public static OperationStatsType sum(OperationStatsType a, OperationStatsType b) {
        if (a == null) {
            return CloneUtil.clone(b);
        } else {
            OperationStatsType sum = CloneUtil.clone(a);
            addTo(sum, b);
            return sum;
        }
    }

    /**
     * Adds an statistics increment into given aggregate statistic information.
     */
    private static void addTo(@NotNull OperationStatsType aggregate, @Nullable OperationStatsType increment) {
        if (increment == null) {
            return;
        }
        if (increment.getEnvironmentalPerformanceInformation() != null) {
            if (aggregate.getEnvironmentalPerformanceInformation() == null) {
                aggregate.setEnvironmentalPerformanceInformation(new EnvironmentalPerformanceInformationType());
            }
            EnvironmentalPerformanceInformation.addTo(aggregate.getEnvironmentalPerformanceInformation(), increment.getEnvironmentalPerformanceInformation());
        }
//        if (increment.getSynchronizationInformation() != null) {
//            if (aggregate.getSynchronizationInformation() == null) {
//                aggregate.setSynchronizationInformation(new ActivitySynchronizationStatisticsType());
//            }
//            SynchronizationInformation.addTo(aggregate.getSynchronizationInformation(), increment.getSynchronizationInformation());
//        }
//        if (increment.getActionsExecutedInformation() != null) {
//            if (aggregate.getActionsExecutedInformation() == null) {
//                aggregate.setActionsExecutedInformation(new ActivityActionsExecutedType());
//            }
//            ActionsExecutedInformation.addTo(aggregate.getActionsExecutedInformation(), increment.getActionsExecutedInformation());
//        }
        if (increment.getRepositoryPerformanceInformation() != null) {
            if (aggregate.getRepositoryPerformanceInformation() == null) {
                aggregate.setRepositoryPerformanceInformation(new RepositoryPerformanceInformationType());
            }
            RepositoryPerformanceInformationUtil.addTo(aggregate.getRepositoryPerformanceInformation(), increment.getRepositoryPerformanceInformation());
        }
        if (increment.getCachesPerformanceInformation() != null) {
            if (aggregate.getCachesPerformanceInformation() == null) {
                aggregate.setCachesPerformanceInformation(new CachesPerformanceInformationType());
            }
            CachePerformanceInformationUtil.addTo(aggregate.getCachesPerformanceInformation(), increment.getCachesPerformanceInformation());
        }
    }

    public static String format(OperationStatsType statistics) {
        if (statistics == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
//        if (statistics.getIterationInformation() != null) {
//            sb.append("Iteration information\n\n")
//                    .append(IterationInformation.format(statistics.getIterationInformation()))
//                    .append("\n");
//        }
//        if (statistics.getActionsExecutedInformation() != null) {
//            sb.append("Actions executed\n\n")
//                    .append(ActionsExecutedInformation.format(statistics.getActionsExecutedInformation()))
//                    .append("\n");
//        }
//        if (statistics.getSynchronizationInformation() != null) {
//            sb.append("Synchronization information:\n")
//                    .append(SynchronizationInformation.format(statistics.getSynchronizationInformation()))
//                    .append("\n");
//        }
        if (statistics.getEnvironmentalPerformanceInformation() != null) {
            sb.append("Environmental performance information\n\n")
                    .append(EnvironmentalPerformanceInformation.format(statistics.getEnvironmentalPerformanceInformation()))
                    .append("\n");
        }
        if (statistics.getRepositoryPerformanceInformation() != null) {
            sb.append("Repository performance information\n\n")
                    .append(RepositoryPerformanceInformationUtil.format(statistics.getRepositoryPerformanceInformation()))
                    .append("\n");
        }
        if (statistics.getCachesPerformanceInformation() != null) {
            sb.append("Cache performance information\n\n")
                    .append(CachePerformanceInformationUtil.format(statistics.getCachesPerformanceInformation()))
                    .append("\n");
        }
//        if (statistics.getWorkBucketManagementPerformanceInformation() != null) {
//            sb.append("Work bucket management performance information\n\n")
//                    .append(ActivityBucketManagementStatisticsUtil.format(statistics.getWorkBucketManagementPerformanceInformation()))
//                    .append("\n");
//        }
        if (statistics.getOperationsPerformanceInformation() != null) {
            sb.append("Methods performance information\n\n")
                    .append(OperationsPerformanceInformationUtil.format(statistics.getOperationsPerformanceInformation()))
                    .append("\n");
        }
        return sb.toString();
    }
}
