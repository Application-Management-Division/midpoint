/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.report;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import com.evolveum.midpoint.model.api.ModelExecuteOptions;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.equivalence.EquivalenceStrategy;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.util.MidPointTestConstants;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.apache.commons.lang3.StringUtils;
import org.testng.annotations.Test;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import static org.testng.AssertJUnit.*;

/**
 * @author skublik
 */

public class TestCsvReport extends AbstractReportIntegrationTest {

    public static final File REPORT_IMPORT_OBJECT_COLLECTION_WITH_VIEW_FILE = new File(TEST_REPORTS_DIR, "report-import-object-collection-with-view.xml");

    public static final String IMPORT_USERS_FILE_PATH = MidPointTestConstants.TEST_RESOURCES_PATH + "/import/import-users.csv";
    public static final String IMPORT_MODIFY_FILE_PATH = MidPointTestConstants.TEST_RESOURCES_PATH + "/import/import-modify-user.csv";

    public static final String REPORT_IMPORT_OBJECT_COLLECTION_WITH_CONDITION_OID = "2b77aa2e-dd86-4842-bcf5-762c8a9a85de";

    int expectedColumns;
    int expectedRow;
    String lastLine;

    @Override
    public void initSystem(Task initTask, OperationResult initResult) throws Exception {
        super.initSystem(initTask, initResult);
        importObjectFromFile(REPORT_IMPORT_OBJECT_COLLECTION_WITH_VIEW_FILE, initResult);
    }

    @Override
    public void test001CreateDashboardReportWithDefaultColumn() throws Exception {
        setExpectedValueForDashboardReport();
        super.test001CreateDashboardReportWithDefaultColumn();
    }

    @Override
    public void test002CreateDashboardReportWithView() throws Exception {
        setExpectedValueForDashboardReport();
        super.test002CreateDashboardReportWithView();
    }

    @Override
    public void test003CreateDashboardReportWithTripleView() throws Exception {
        setExpectedValueForDashboardReport();
        super.test003CreateDashboardReportWithTripleView();
    }

    @Override
    public void test004CreateDashboardReportEmpty() throws Exception {
        expectedColumns = 3;
        expectedRow = 3;
        super.test004CreateDashboardReportEmpty();
    }

    @Override
    public void test101CreateAuditCollectionReportWithDefaultColumn() throws Exception {
        expectedColumns = 8;
        expectedRow = -1;
        super.test101CreateAuditCollectionReportWithDefaultColumn();
    }

    @Override
    public void test102CreateAuditCollectionReportWithView() throws Exception {
        expectedColumns = 2;
        expectedRow = -1;
        super.test102CreateAuditCollectionReportWithView();
    }

    @Override
    public void test103CreateAuditCollectionReportWithDoubleView() throws Exception {
        expectedColumns = 3;
        expectedRow = -1;
        super.test103CreateAuditCollectionReportWithDoubleView();
    }

    @Test
    public void test104CreateAuditCollectionReportWithCondition() throws Exception {
        expectedColumns = 8;
        expectedRow = 2;
        super.test104CreateAuditCollectionReportWithCondition();
    }

    @Override
    public void test105CreateAuditCollectionReportEmpty() throws Exception {
        expectedColumns = 8;
        expectedRow = 1;
        super.test105CreateAuditCollectionReportEmpty();
    }

    @Override
    public void test110CreateObjectCollectionReportWithDefaultColumn() throws Exception {
        expectedColumns = 6;
        expectedRow = 4;
        super.test110CreateObjectCollectionReportWithDefaultColumn();
    }

    @Override
    public void test111CreateObjectCollectionReportWithView() throws Exception {
        expectedColumns = 2;
        expectedRow = 2;
        super.test111CreateObjectCollectionReportWithView();
    }

    @Override
    public void test112CreateObjectCollectionReportWithDoubleView() throws Exception {
        expectedColumns = 3;
        expectedRow = 4;
        super.test112CreateObjectCollectionReportWithDoubleView();
    }

    @Override
    public void test113CreateObjectCollectionReportWithFilter() throws Exception {
        expectedColumns = 2;
        expectedRow = 3;
        super.test113CreateObjectCollectionReportWithFilter();
    }

    @Override
    public void test114CreateObjectCollectionReportWithFilterAndBasicCollection() throws Exception {
        expectedColumns = 2;
        expectedRow = 2;
        super.test114CreateObjectCollectionReportWithFilterAndBasicCollection();
    }

    @Test
    public void test115CreateObjectCollectionReportWithCondition() throws Exception {
        expectedColumns = 6;
        expectedRow = 2;
        super.test115CreateObjectCollectionReportWithCondition();
    }

    @Test
    public void test116CreateObjectCollectionEmptyReport() throws Exception {
        expectedColumns = 6;
        expectedRow = 1;
        super.test116CreateObjectCollectionEmptyReport();
    }

    @Test
    public void test117CreateObjectCollectionReportWithFilterAndBasicCollectionWithoutView() throws Exception {
        expectedColumns = 1;
        expectedRow = 2;
        super.test117CreateObjectCollectionReportWithFilterAndBasicCollectionWithoutView();
    }

    @Test
    public void test118CreateObjectCollectionWithParamReport() throws Exception {
        expectedColumns = 2;
        expectedRow = 2;
        super.test118CreateObjectCollectionWithParamReport();
    }

    @Test
    public void test119CreateObjectCollectionWithSubreportParamReport() throws Exception {
        expectedColumns = 2;
        expectedRow = 2;
        lastLine = "\"will\";\"TestRole1230,TestRole123010\"";
        try {
            super.test119CreateObjectCollectionWithSubreportParamReport();
        } finally {
            lastLine = null;
        }
    }

    @Test
    public void test120RunMidpointUsers() throws Exception {
        expectedColumns = 6;
        expectedRow = 4;
        super.test120RunMidpointUsers();
    }

    @Test
    public void test121RunMidpointUsersScript() throws Exception {
        expectedColumns = 6;
        expectedRow = 4;
        super.test121RunMidpointUsersScript();
    }

    @Test
    public void test200ImportReportForUser() throws Exception {
        PrismObject<ReportType> report = getObject(ReportType.class, REPORT_IMPORT_OBJECT_COLLECTION_WITH_CONDITION_OID);
        importReport(report, IMPORT_USERS_FILE_PATH, false);
        PrismObject<UserType> user = searchObjectByName(UserType.class, "testUser01");
        assertNotNull("User testUser01 was not created", user);
        assertEquals(ActivationStatusType.ENABLED, user.asObjectable().getActivation().getAdministrativeStatus());
        assertEquals("2020-07-07T00:00:00.000+02:00", user.asObjectable().getActivation().getValidFrom().toString());
        assertEquals("sub1", user.asObjectable().getSubtype().get(0));
        assertEquals("sub22", user.asObjectable().getSubtype().get(1));
        assertEquals("Test import: test_NICK", user.asObjectable().getNickName().getOrig());
        assertEquals("00000000-0000-0000-0000-000000000008", user.asObjectable().getAssignment().get(0).getTargetRef().getOid());
        assertEquals("00000000-0000-0000-0000-000000000004", user.asObjectable().getAssignment().get(1).getTargetRef().getOid());

        user = searchObjectByName(UserType.class, "testUser02");
        assertNotNull("User testUser02 was not created", user);
        assertEquals(ActivationStatusType.ENABLED, user.asObjectable().getActivation().getAdministrativeStatus());
        assertEquals("2020-07-07T00:00:00.000+02:00", user.asObjectable().getActivation().getValidFrom().toString());
        assertTrue(user.asObjectable().getSubtype().isEmpty());
        assertEquals("Test import: test_NICK2", user.asObjectable().getNickName().getOrig());
        assertTrue(user.asObjectable().getAssignment().isEmpty());

        user = searchObjectByName(UserType.class, "testUser03");
        assertNotNull("User testUser03 was not created", user);
        assertEquals(ActivationStatusType.ENABLED, user.asObjectable().getActivation().getAdministrativeStatus());
        assertEquals("2020-07-07T00:00:00.000+02:00", user.asObjectable().getActivation().getValidFrom().toString());
        assertEquals("sub31", user.asObjectable().getSubtype().get(0));
        assertEquals("Test import: test_NICK3", user.asObjectable().getNickName().getOrig());
        assertTrue(user.asObjectable().getAssignment().isEmpty());
    }

    @Test(dependsOnMethods = {"test115CreateObjectCollectionReportWithCondition"})
    public void test201ImportReportFromExportedReport() throws Exception {
        PrismObject<ReportType> report = getObject(ReportType.class, REPORT_OBJECT_COLLECTION_WITH_CONDITION_OID);
        runReport(report, false);
        File outputFile = findOutputFile(report);

        Task task = getTestTask();
        OperationResult result = task.getResult();
        PrismObject<ReportType> reportBefore = report.clone();
        ReportBehaviorType behavior = new ReportBehaviorType();
        behavior.setDirection(DirectionTypeType.IMPORT);
        ImportOptionsType importOptions = new ImportOptionsType();
        importOptions.setOverwrite(true);
        behavior.setImportOptions(importOptions);
        report.asObjectable().setBehavior(behavior);
        ObjectDelta<ReportType> diffDelta = reportBefore.diff(report, EquivalenceStrategy.REAL_VALUE_CONSIDER_DIFFERENT_IDS);
        executeChanges(diffDelta, ModelExecuteOptions.createRaw(), task, result);

        PrismObject<UserType> oldWill = searchObjectByName(UserType.class, "will");

        importReport(report, outputFile.getAbsolutePath(), false);
        PrismObject<UserType> newWill = searchObjectByName(UserType.class, "will");

        assertNotNull("User will was not created", newWill);
        assertEquals(null, newWill.asObjectable().getTelephoneNumber());
        assertEquals(oldWill.asObjectable().getGivenName(), newWill.asObjectable().getGivenName());
        assertEquals(oldWill.asObjectable().getFamilyName(), newWill.asObjectable().getFamilyName());
        assertEquals(oldWill.asObjectable().getFullName(), newWill.asObjectable().getFullName());
        assertEquals(oldWill.asObjectable().getEmailAddress(), newWill.asObjectable().getEmailAddress());
        outputFile.renameTo(new File(outputFile.getParentFile(), "processed-" + outputFile.getName()));
    }

    @Test(dependsOnMethods = {"test200ImportReportForUser"})
    public void test202ImportReportWithImportScript() throws Exception {
        PrismObject<UserType> testUser02 = searchObjectByName(UserType.class, "testUser02");
        assertNotNull("User testUser02 was not created", testUser02);
        assertTrue(testUser02.asObjectable().getAssignment().isEmpty());

        PrismObject<UserType> testUser01 = searchObjectByName(UserType.class, "testUser01");
        assertNotNull("User testUser01 was not created", testUser01);
        assertTrue(testUser01.asObjectable().getAssignment().size() == 2);

        PrismObject<UserType> jack = searchObjectByName(UserType.class, "jack");
        assertNotNull("User jack was not created", jack);
        assertNull(jack.asObjectable().getAssignment().get(0).getActivation().getValidFrom());
        assertNull(jack.asObjectable().getAssignment().get(0).getActivation().getValidTo());

        PrismObject<ReportType> report = getObject(ReportType.class, REPORT_WITH_IMPORT_SCRIPT_OID);
        importReport(report, IMPORT_MODIFY_FILE_PATH, true);

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        Date date = format.parse("2018-01-01");
        GregorianCalendar cal = new GregorianCalendar();
        cal.setTime(date);
        XMLGregorianCalendar validFrom = DatatypeFactory.newInstance().newXMLGregorianCalendar(cal);
        date = format.parse("2018-05-01");
        cal.setTime(date);
        XMLGregorianCalendar validTo = DatatypeFactory.newInstance().newXMLGregorianCalendar(cal);

        testUser02 = searchObjectByName(UserType.class, "testUser02");
        assertNotNull("User testUser02 was not created", testUser02);
        assertEquals("00000000-0000-0000-0000-000000000004", testUser02.asObjectable().getAssignment().get(0).getTargetRef().getOid());
        assertEquals(validFrom, testUser02.asObjectable().getAssignment().get(0).getActivation().getValidFrom());
        assertEquals(validTo, testUser02.asObjectable().getAssignment().get(0).getActivation().getValidTo());

        testUser01 = searchObjectByName(UserType.class, "testUser01");
        assertNotNull("User testUser01 was not created", testUser01);
        assertTrue(testUser01.asObjectable().getAssignment().size() == 1);
        assertEquals("00000000-0000-0000-0000-000000000008", testUser01.asObjectable().getAssignment().get(0).getTargetRef().getOid());

        jack = searchObjectByName(UserType.class, "jack");
        assertNotNull("User jack was not created", jack);
        assertEquals("00000000-0000-0000-0000-000000000004", jack.asObjectable().getAssignment().get(0).getTargetRef().getOid());
        assertEquals(validFrom, jack.asObjectable().getAssignment().get(0).getActivation().getValidFrom());
        assertEquals(validTo, jack.asObjectable().getAssignment().get(0).getActivation().getValidTo());
    }

    private void setExpectedValueForDashboardReport() {
        expectedColumns = 3;
        expectedRow = 8;
    }

    @Override
    protected FileFormatConfigurationType getFileFormatConfiguration() {
        FileFormatConfigurationType config = new FileFormatConfigurationType();
        config.setType(FileFormatTypeType.CSV);
        return config;
    }

    protected List<String> basicCheckOutputFile(PrismObject<ReportType> report) throws IOException, SchemaException, ObjectNotFoundException, SecurityViolationException, CommunicationException, ConfigurationException, ExpressionEvaluationException {
        List<String> lines = super.basicCheckOutputFile(report);

        if (expectedRow != -1 && lines.size() != expectedRow) {
            fail("Unexpected count of rows of csv report. Expected: " + expectedRow + ", Actual: " + lines.size());
        }

        if (expectedRow == -1 && lines.size() < 2) {
            fail("Unexpected count of rows of csv report. Expected: more as one, Actual: " + lines.size());
        }

        int actualColumns = getNumberOfColumns(lines);

        if (actualColumns != expectedColumns) {
            fail("Unexpected count of columns of csv report. Expected: " + expectedColumns + ", Actual: " + actualColumns);
        }

        String lastLine = lines.get(lines.size() - 1);
        if (StringUtils.isNoneEmpty(this.lastLine) && !lastLine.equals(this.lastLine)) {
            fail("Unexpected last line of csv report. Expected: '" + this.lastLine + "', Actual: '" + lastLine + "'");
        }

        return lines;
    }

    private int getNumberOfColumns(List<String> lines) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Couldn't find line of report");
        }
        return lines.get(0).split(";").length;
    }
}
