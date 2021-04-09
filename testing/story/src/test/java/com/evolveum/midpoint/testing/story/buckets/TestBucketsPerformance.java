/*
 * Copyright (c) 2019-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.testing.story.buckets;

import java.io.File;
import java.io.IOException;

import com.evolveum.midpoint.prism.crypto.EncryptionException;
import com.evolveum.midpoint.schema.util.task.TaskOperationStatsUtil;
import com.evolveum.midpoint.schema.util.task.TaskPerformanceInformation;
import com.evolveum.midpoint.util.exception.CommonException;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Test;

import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.TestResource;
import com.evolveum.midpoint.test.util.MidPointTestConstants;
import com.evolveum.midpoint.testing.story.AbstractStoryTest;

/**
 * Tests performance of bucketed tasks.
 */
@ContextConfiguration(locations = { "classpath:ctx-story-test-main.xml" })
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class TestBucketsPerformance extends AbstractStoryTest {

    protected static final File TEST_DIR = new File(MidPointTestConstants.TEST_RESOURCES_DIR, "buckets");

    protected static final File SYSTEM_CONFIGURATION_FILE = new File(TEST_DIR, "system-configuration.xml");

    private static final TestResource<RoleType> ROLE_SLOW = new TestResource<>(TEST_DIR, "role-slow.xml", "4c9f44cc-9cbd-454f-b346-a3a66242ec3d");
    private static final TestResource<TaskType> TASK_RECOMPUTE_16_04 = new TestResource<>(TEST_DIR, "task-recompute-16-04.xml", "0f19ae56-050f-4a6d-b407-25a6cf49297d");
    private static final TestResource<TaskType> TASK_RECOMPUTE_256_30 = new TestResource<>(TEST_DIR, "task-recompute-256-30.xml", "a2060e8f-1675-41a0-a1ae-1781f14c034f");

    private static final int NUMBER_OF_USERS = 1000;

    @Override
    public void initSystem(Task initTask, OperationResult initResult) throws Exception {
        super.initSystem(initTask, initResult);

        addObject(ROLE_SLOW, initTask, initResult);
        createUsers(initResult);
    }

    private void createUsers(OperationResult result) throws SchemaException, EncryptionException, ObjectAlreadyExistsException {
        for (int i = 0; i < NUMBER_OF_USERS; i++) {
            UserType user = new UserType(prismContext)
                    .name(String.format("user-%06d", i))
                    .beginAssignment()
                        .targetRef(ROLE_SLOW.oid, RoleType.COMPLEX_TYPE)
                    .end();
            repoAddObject(user.asPrismObject(), result);
        }
        display("Users created: " + NUMBER_OF_USERS);
    }

    /** We want minimalistic logging here. */
    @Override
    protected boolean isAvoidLoggingChange() {
        return false;
    }

    @Override
    protected File getSystemConfigurationFile() {
        return SYSTEM_CONFIGURATION_FILE;
    }

    @Test
    public void test100RecomputeUsers_16_buckets_4_threads() throws Exception {
        executeRecomputation(TASK_RECOMPUTE_16_04);
    }

    @Test
    public void test110RecomputeUsers_256_buckets_30_threads() throws Exception {
        executeRecomputation(TASK_RECOMPUTE_256_30);
    }

    private void executeRecomputation(TestResource<TaskType> recomputationTask) throws IOException, CommonException {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        when();
        addTask(recomputationTask, result);
        waitForTaskFinish(recomputationTask.oid, false);

        then();
        var tree = assertTaskTree(recomputationTask.oid, "after")
                .display()
                .subtask(0)
                .display()
                .end()
                .getObject().asObjectable();

        var stats = TaskOperationStatsUtil.getOperationStatsFromTree(tree, prismContext);
        displayValue("Statistics", TaskOperationStatsUtil.format(stats));

        var performanceInformation = TaskPerformanceInformation.fromTaskTree(tree);
        displayDumpable("Performance information", performanceInformation);
    }
}
