/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale;

import static java.util.Comparator.comparing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static com.evolveum.midpoint.repo.api.RepoAddOptions.createOverwrite;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.xml.namespace.QName;

import org.testng.annotations.Test;

import com.evolveum.midpoint.repo.api.DeleteObjectResult;
import com.evolveum.midpoint.repo.sqale.qmodel.accesscert.MAccessCertificationDefinition;
import com.evolveum.midpoint.repo.sqale.qmodel.accesscert.QAccessCertificationDefinition;
import com.evolveum.midpoint.repo.sqale.qmodel.common.MContainer;
import com.evolveum.midpoint.repo.sqale.qmodel.common.MContainerType;
import com.evolveum.midpoint.repo.sqale.qmodel.common.QContainer;
import com.evolveum.midpoint.repo.sqale.qmodel.connector.MConnector;
import com.evolveum.midpoint.repo.sqale.qmodel.connector.MConnectorHost;
import com.evolveum.midpoint.repo.sqale.qmodel.connector.QConnector;
import com.evolveum.midpoint.repo.sqale.qmodel.connector.QConnectorHost;
import com.evolveum.midpoint.repo.sqale.qmodel.focus.MGenericObject;
import com.evolveum.midpoint.repo.sqale.qmodel.focus.MUser;
import com.evolveum.midpoint.repo.sqale.qmodel.focus.QGenericObject;
import com.evolveum.midpoint.repo.sqale.qmodel.focus.QUser;
import com.evolveum.midpoint.repo.sqale.qmodel.lookuptable.MLookupTableRow;
import com.evolveum.midpoint.repo.sqale.qmodel.lookuptable.QLookupTableRow;
import com.evolveum.midpoint.repo.sqale.qmodel.object.*;
import com.evolveum.midpoint.repo.sqale.qmodel.ref.*;
import com.evolveum.midpoint.repo.sqale.qmodel.report.MReport;
import com.evolveum.midpoint.repo.sqale.qmodel.report.MReportData;
import com.evolveum.midpoint.repo.sqale.qmodel.report.QReport;
import com.evolveum.midpoint.repo.sqale.qmodel.report.QReportData;
import com.evolveum.midpoint.repo.sqale.qmodel.resource.MResource;
import com.evolveum.midpoint.repo.sqale.qmodel.resource.QResource;
import com.evolveum.midpoint.repo.sqale.qmodel.shadow.MShadow;
import com.evolveum.midpoint.repo.sqale.qmodel.shadow.QShadow;
import com.evolveum.midpoint.repo.sqale.qmodel.system.QSystemConfiguration;
import com.evolveum.midpoint.repo.sqale.qmodel.task.MTask;
import com.evolveum.midpoint.repo.sqale.qmodel.task.QTask;
import com.evolveum.midpoint.repo.sqlbase.JdbcSession;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

public class SqaleRepoAddDeleteObjectTest extends SqaleRepoBaseTest {

    @Test
    public void test100AddNamedUserWithoutOidWorksOk()
            throws ObjectAlreadyExistsException, SchemaException {
        OperationResult result = createOperationResult();

        given("user with a name");
        String userName = "user" + getTestNumber();
        UserType userType = new UserType(prismContext)
                .name(userName);

        when("adding it to the repository");
        repositoryService.addObject(userType.asPrismObject(), null, result);

        then("operation is successful and user row for it is created");
        assertThatOperationResult(result).isSuccess();

        QUser u = aliasFor(QUser.class);
        List<MUser> users = select(u, u.nameOrig.eq(userName));
        assertThat(users).hasSize(1);

        MUser row = users.get(0);
        assertThat(row.oid).isNotNull();
        assertThat(row.nameNorm).isNotNull(); // normalized name is stored
        assertThat(row.version).isEqualTo(1); // initial version is set
        // read-only column with value generated/stored in the database
        assertThat(row.objectType).isEqualTo(MObjectType.USER);
        assertThat(row.subtypes).isNull(); // we don't store empty lists as empty arrays
    }

    @Test
    public void test101AddUserWithoutNameFails() {
        OperationResult result = createOperationResult();

        given("user without specified name");
        long baseCount = count(QUser.class);
        UserType userType = new UserType(prismContext);

        expect("adding it to the repository throws exception and no row is created");
        assertThatThrownBy(() -> repositoryService.addObject(userType.asPrismObject(), null, result))
                .isInstanceOf(SchemaException.class)
                .hasMessage("Attempt to add object without name.");

        and("operation result is fatal error");
        assertThatOperationResult(result).isFatalError()
                .hasMessageContaining("Attempt to add object without name.");

        and("object count in the repository is not changed");
        assertCount(QUser.class, baseCount);
    }

    @Test
    public void test102AddWithoutOidIgnoresOverwriteOption()
            throws ObjectAlreadyExistsException, SchemaException {
        OperationResult result = createOperationResult();

        given("user with a name but without OID");
        String userName = "user" + getTestNumber();
        UserType userType = new UserType(prismContext)
                .name(userName);

        when("adding it to the repository with overwrite option");
        repositoryService.addObject(userType.asPrismObject(), createOverwrite(), result);

        then("operation is successful and user row for it is created, overwrite is meaningless");
        assertThatOperationResult(result).isSuccess();

        QUser u = aliasFor(QUser.class);
        List<MUser> users = select(u, u.nameOrig.eq(userName));
        assertThat(users).hasSize(1);
        assertThat(users.get(0).oid).isNotNull();
    }

    @Test
    public void test110AddUserWithProvidedOidWorksOk()
            throws ObjectAlreadyExistsException, SchemaException {
        OperationResult result = createOperationResult();

        given("user with provided OID");
        UUID providedOid = UUID.randomUUID();
        String userName = "user" + getTestNumber();
        UserType userType = new UserType(prismContext)
                .oid(providedOid.toString())
                .name(userName);

        when("adding it to the repository");
        repositoryService.addObject(userType.asPrismObject(), null, result);

        then("operation is successful and user row with provided OID is created");
        assertThatOperationResult(result).isSuccess();

        QUser u = aliasFor(QUser.class);
        List<MUser> users = select(u, u.nameOrig.eq(userName));
        assertThat(users).hasSize(1);

        MUser mUser = users.get(0);
        assertThat(mUser.oid).isEqualTo(providedOid);
        assertThat(mUser.version).isEqualTo(1); // initial version is set
    }

    @Test
    public void test111AddSecondObjectWithTheSameOidThrowsObjectAlreadyExists()
            throws ObjectAlreadyExistsException, SchemaException {
        OperationResult result = createOperationResult();

        given("user with provided OID already exists");
        UUID providedOid = UUID.randomUUID();
        UserType user1 = new UserType(prismContext)
                .oid(providedOid.toString())
                .name("user" + getTestNumber());
        repositoryService.addObject(user1.asPrismObject(), null, result);

        when("adding it another user with the same OID to the repository");
        long baseCount = count(QUser.class);
        UserType user2 = new UserType(prismContext)
                .oid(providedOid.toString())
                .name("user" + getTestNumber() + "-different-name");

        then("operation fails and no new user row is created");
        assertThatThrownBy(() -> repositoryService.addObject(user2.asPrismObject(), null, result))
                .isInstanceOf(ObjectAlreadyExistsException.class);
        assertThatOperationResult(result).isFatalError()
                .hasMessageMatching("Provided OID .* already exists");
        assertCount(QUser.class, baseCount);
    }

    @Test
    public void test200AddObjectWithMultivalueContainers()
            throws ObjectAlreadyExistsException, SchemaException {
        OperationResult result = createOperationResult();

        given("user with assignment and ref");
        String userName = "user" + getTestNumber();
        String targetRef1 = UUID.randomUUID().toString();
        String targetRef2 = UUID.randomUUID().toString();
        UserType user = new UserType(prismContext)
                .name(userName)
                .assignment(new AssignmentType(prismContext)
                        .targetRef(targetRef1, RoleType.COMPLEX_TYPE))
                .assignment(new AssignmentType(prismContext)
                        .targetRef(targetRef2, RoleType.COMPLEX_TYPE));

        when("adding it to the repository");
        repositoryService.addObject(user.asPrismObject(), null, result);

        then("object and its container rows are created and container IDs are assigned");
        assertThatOperationResult(result).isSuccess();

        QUser u = aliasFor(QUser.class);
        List<MUser> users = select(u, u.nameOrig.eq(userName));
        assertThat(users).hasSize(1);
        MUser userRow = users.get(0);
        assertThat(userRow.oid).isNotNull();
        assertThat(userRow.containerIdSeq).isEqualTo(3); // next free container number

        QContainer<MContainer> c = aliasFor(QContainer.CLASS);
        List<MContainer> containers = select(c, c.ownerOid.eq(userRow.oid));
        assertThat(containers).hasSize(2)
                .allMatch(cRow -> cRow.ownerOid.equals(userRow.oid)
                        && cRow.containerType == MContainerType.ASSIGNMENT)
                .extracting(cRow -> cRow.cid)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    public void test201AddObjectWithOidAndMultivalueContainers()
            throws ObjectAlreadyExistsException, SchemaException {
        OperationResult result = createOperationResult();

        given("user with assignment and ref");
        UUID providedOid = UUID.randomUUID();
        String userName = "user" + getTestNumber();
        String targetRef1 = UUID.randomUUID().toString();
        String targetRef2 = UUID.randomUUID().toString();
        UserType user = new UserType(prismContext)
                .oid(providedOid.toString())
                .name(userName)
                .assignment(new AssignmentType(prismContext)
                        .targetRef(targetRef1, RoleType.COMPLEX_TYPE))
                .assignment(new AssignmentType(prismContext)
                        .targetRef(targetRef2, RoleType.COMPLEX_TYPE));

        when("adding it to the repository");
        repositoryService.addObject(user.asPrismObject(), null, result);

        then("object and its container rows are created and container IDs are assigned");
        assertThatOperationResult(result).isSuccess();

        QUser u = aliasFor(QUser.class);
        List<MUser> users = select(u, u.nameOrig.eq(userName));
        assertThat(users).hasSize(1);
        MUser userRow = users.get(0);
        assertThat(userRow.oid).isNotNull();
        assertThat(userRow.containerIdSeq).isEqualTo(3); // next free container number

        QContainer<MContainer> c = aliasFor(QContainer.CLASS);
        List<MContainer> containers = select(c, c.ownerOid.eq(userRow.oid));
        assertThat(containers).hasSize(2)
                .allMatch(cRow -> cRow.ownerOid.equals(userRow.oid)
                        && cRow.containerType == MContainerType.ASSIGNMENT)
                .extracting(cRow -> cRow.cid)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    public void test205AddObjectWithMultivalueRefs()
            throws ObjectAlreadyExistsException, SchemaException {
        OperationResult result = createOperationResult();

        given("user with ref");
        String userName = "user" + getTestNumber();
        String targetRef1 = UUID.randomUUID().toString();
        String targetRef2 = UUID.randomUUID().toString();
        UserType user = new UserType(prismContext)
                .name(userName)
                .linkRef(targetRef1, RoleType.COMPLEX_TYPE)
                .linkRef(targetRef2, RoleType.COMPLEX_TYPE);

        when("adding it to the repository");
        repositoryService.addObject(user.asPrismObject(), null, result);

        then("object and its container rows are created and container IDs are assigned");
        assertThatOperationResult(result).isSuccess();

        QUser u = aliasFor(QUser.class);
        List<MUser> users = select(u, u.nameOrig.eq(userName));
        assertThat(users).hasSize(1);
        MUser userRow = users.get(0);
        assertThat(userRow.oid).isNotNull();
        assertThat(userRow.containerIdSeq).isEqualTo(1); // cid sequence is in initial state

        UUID userOid = UUID.fromString(user.getOid());
        QObjectReference or = QObjectReferenceMapping.INSTANCE_PROJECTION.defaultAlias();
        List<MReference> projectionRefs = select(or, or.ownerOid.eq(userOid));
        assertThat(projectionRefs).hasSize(2)
                .allMatch(rRow -> rRow.referenceType == MReferenceType.PROJECTION)
                .allMatch(rRow -> rRow.ownerOid.equals(userOid))
                .extracting(rRow -> rRow.targetOid.toString())
                .containsExactlyInAnyOrder(targetRef1, targetRef2);
        // this is the same set of refs queried from the super-table
        QReference<MReference> r = aliasFor(QReference.CLASS);
        List<MReference> refs = select(r, r.ownerOid.eq(userOid));
        assertThat(refs).hasSize(2)
                .allMatch(rRow -> rRow.referenceType == MReferenceType.PROJECTION);
    }

    @Test
    public void test290DuplicateCidInsideOneContainerIsCaughtByPrism() {
        expect("object construction with duplicate CID inside container fails immediately");
        assertThatThrownBy(() -> new UserType(prismContext)
                .assignment(new AssignmentType()
                        .targetRef("ref1", RoleType.COMPLEX_TYPE).id(1L))
                .assignment(new AssignmentType()
                        .targetRef("ref2", RoleType.COMPLEX_TYPE).id(1L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Attempt to add a container value with an id that already exists: 1");
    }

    @Test
    public void test291DuplicateCidInDifferentContainersIsCaughtByRepo() {
        OperationResult result = createOperationResult();

        given("object with duplicate CID in different containers");
        UserType user = new UserType(prismContext)
                .name("any name")
                .assignment(new AssignmentType().id(1L))
                .operationExecution(new OperationExecutionType().id(1L));

        expect("adding object to repository throws exception");
        assertThatThrownBy(() -> repositoryService.addObject(user.asPrismObject(), null, result))
                .isInstanceOf(SchemaException.class)
                .hasMessage("CID 1 is used repeatedly in the object!");
    }

    // region insertion of various types

    // this test covers function of ObjectSqlTransformer and all the basic object fields
    @Test
    public void test800SystemConfigurationBasicObjectAttributes() throws Exception {
        OperationResult result = createOperationResult();

        given("system configuration");
        String objectName = "sc" + getTestNumber();
        UUID tenantRefOid = UUID.randomUUID();
        UUID creatorRefOid = UUID.randomUUID();
        UUID modifierRefOid = UUID.randomUUID();
        QName relation1 = QName.valueOf("{https://random.org/ns}random-rel-1");
        QName relation2 = QName.valueOf("{https://random.org/ns}random-rel-2");
        SystemConfigurationType systemConfiguration = new SystemConfigurationType(prismContext)
                .name(objectName)
                .tenantRef(tenantRefOid.toString(), OrgType.COMPLEX_TYPE, relation1)
                .lifecycleState("lifecycle-state")
                .policySituation("policy-situation-1")
                .policySituation("policy-situation-2")
                .subtype("subtype-1")
                .subtype("subtype-2")
                // TODO ext some time later
                .metadata(new MetadataType()
                        .creatorRef(creatorRefOid.toString(), UserType.COMPLEX_TYPE, relation1)
                        .createChannel("create-channel")
                        .createTimestamp(MiscUtil.asXMLGregorianCalendar(1L))
                        .modifierRef(modifierRefOid.toString(), ServiceType.COMPLEX_TYPE, relation2)
                        .modifyChannel("modify-channel")
                        .modifyTimestamp(MiscUtil.asXMLGregorianCalendar(2L)));

        when("adding it to the repository");
        repositoryService.addObject(systemConfiguration.asPrismObject(), null, result);

        then("it is stored and relevant attributes are in columns");
        assertThatOperationResult(result).isSuccess();

        MObject row = selectObjectByOid(QSystemConfiguration.class, systemConfiguration.getOid());
        display("FULL OBJECT: " + new String(row.fullObject, StandardCharsets.UTF_8));
        assertThat(row.nameOrig).isEqualTo(objectName);
        assertThat(row.nameNorm).isEqualTo(objectName); // nothing to normalize here
        assertThat(row.tenantRefTargetOid).isEqualTo(tenantRefOid);
        assertThat(row.tenantRefTargetType).isEqualTo(MObjectType.ORG);
        assertCachedUri(row.tenantRefRelationId, relation1);
        assertThat(row.lifecycleState).isEqualTo("lifecycle-state");
        // complex DB columns
        assertThat(resolveCachedUriIds(row.policySituations))
                .containsExactlyInAnyOrder("policy-situation-1", "policy-situation-2");
        assertThat(row.subtypes).containsExactlyInAnyOrder("subtype-1", "subtype-2");
        // TODO EXT
        // metadata
        assertThat(row.creatorRefTargetOid).isEqualTo(creatorRefOid);
        assertThat(row.creatorRefTargetType).isEqualTo(MObjectType.USER);
        assertCachedUri(row.creatorRefRelationId, relation1);
        assertCachedUri(row.createChannelId, "create-channel");
        assertThat(row.createTimestamp).isEqualTo(Instant.ofEpochMilli(1));
        assertThat(row.modifierRefTargetOid).isEqualTo(modifierRefOid);
        assertThat(row.modifierRefTargetType).isEqualTo(MObjectType.SERVICE);
        assertCachedUri(row.modifierRefRelationId, relation2);
        assertCachedUri(row.modifyChannelId, "modify-channel");
        assertThat(row.modifyTimestamp).isEqualTo(Instant.ofEpochMilli(2));
    }

    @Test
    public void test801ContainerTrigger() throws Exception {
        OperationResult result = createOperationResult();

        given("object with few triggers");
        String objectName = "object" + getTestNumber();
        SystemConfigurationType systemConfiguration = new SystemConfigurationType(prismContext)
                .name(objectName)
                .trigger(new TriggerType()
                        .id(3L) // one pre-filled CID, with non-first ID
                        .handlerUri("trigger-1-handler-uri")
                        .timestamp(MiscUtil.asXMLGregorianCalendar(1L)))
                .trigger(new TriggerType() // second one without CID
                        .handlerUri("trigger-2-handler-uri")
                        .timestamp(MiscUtil.asXMLGregorianCalendar(2L)));

        when("adding it to the repository");
        repositoryService.addObject(systemConfiguration.asPrismObject(), null, result);

        then("it is stored with its persisted trigger containers");
        assertThatOperationResult(result).isSuccess();

        QTrigger t = aliasFor(QTrigger.class);
        List<MTrigger> containers = select(t, t.ownerOid.eq(UUID.fromString(systemConfiguration.getOid())));
        assertThat(containers).hasSize(2);

        containers.sort(comparing(tr -> tr.cid));
        MTrigger containerRow = containers.get(0);
        assertThat(containerRow.cid).isEqualTo(3); // assigned in advance
        assertCachedUri(containerRow.handlerUriId, "trigger-1-handler-uri");
        assertThat(containerRow.timestampValue).isEqualTo(Instant.ofEpochMilli(1));

        containerRow = containers.get(1);
        assertThat(containerRow.cid).isEqualTo(4); // next CID assigned by repo
        assertCachedUri(containerRow.handlerUriId, "trigger-2-handler-uri");
        assertThat(containerRow.timestampValue).isEqualTo(Instant.ofEpochMilli(2));

        MObject objectRow = selectObjectByOid(QSystemConfiguration.class, systemConfiguration.getOid());
        assertThat(objectRow.containerIdSeq).isEqualTo(5); // next free CID
    }

    @Test
    public void test802ContainerOperationExecution() throws Exception {
        OperationResult result = createOperationResult();

        given("object with few operation executions");
        String objectName = "object" + getTestNumber();
        UUID initiatorRefOid = UUID.randomUUID();
        UUID taskRefOid = UUID.randomUUID();
        QName initiatorRelation = QName.valueOf("{https://random.org/ns}rel-initiator");
        QName taskRelation = QName.valueOf("{https://random.org/ns}rel-task");
        SystemConfigurationType systemConfiguration = new SystemConfigurationType(prismContext)
                .name(objectName)
                .operationExecution(new OperationExecutionType()
                        .status(OperationResultStatusType.FATAL_ERROR)
                        .recordType(OperationExecutionRecordTypeType.SIMPLE)
                        .initiatorRef(initiatorRefOid.toString(),
                                UserType.COMPLEX_TYPE, initiatorRelation)
                        .taskRef(taskRefOid.toString(), TaskType.COMPLEX_TYPE, taskRelation)
                        .timestamp(MiscUtil.asXMLGregorianCalendar(1L)))
                .operationExecution(new OperationExecutionType()
                        .status(OperationResultStatusType.UNKNOWN)
                        .timestamp(MiscUtil.asXMLGregorianCalendar(2L)));

        when("adding it to the repository");
        repositoryService.addObject(systemConfiguration.asPrismObject(), null, result);

        then("it is stored with its persisted trigger containers");
        assertThatOperationResult(result).isSuccess();

        QOperationExecution oe = aliasFor(QOperationExecution.class);
        List<MOperationExecution> containers =
                select(oe, oe.ownerOid.eq(UUID.fromString(systemConfiguration.getOid())));
        assertThat(containers).hasSize(2);

        containers.sort(comparing(tr -> tr.cid));
        MOperationExecution containerRow = containers.get(0);
        assertThat(containerRow.cid).isEqualTo(1);
        assertThat(containerRow.status).isEqualTo(OperationResultStatusType.FATAL_ERROR);
        assertThat(containerRow.recordType).isEqualTo(OperationExecutionRecordTypeType.SIMPLE);
        assertThat(containerRow.initiatorRefTargetOid).isEqualTo(initiatorRefOid);
        assertThat(containerRow.initiatorRefTargetType).isEqualTo(MObjectType.USER);
        assertCachedUri(containerRow.initiatorRefRelationId, initiatorRelation);
        assertThat(containerRow.taskRefTargetOid).isEqualTo(taskRefOid);
        assertThat(containerRow.taskRefTargetType).isEqualTo(MObjectType.TASK);
        assertCachedUri(containerRow.taskRefRelationId, taskRelation);
        assertThat(containerRow.timestampValue).isEqualTo(Instant.ofEpochMilli(1));

        containerRow = containers.get(1);
        assertThat(containerRow.cid).isEqualTo(2);
        assertThat(containerRow.status).isEqualTo(OperationResultStatusType.UNKNOWN);
        assertThat(containerRow.timestampValue).isEqualTo(Instant.ofEpochMilli(2));

        // this time we didn't test assigned CID or CID SEQ value on owner (see test801)
    }

    @Test
    public void test808LookupTable() throws Exception {
        OperationResult result = createOperationResult();

        given("lookup table with a couple of rows");
        String objectName = "ltable" + getTestNumber();
        LookupTableType lookupTable = new LookupTableType(prismContext)
                .name(objectName)
                .row(new LookupTableRowType()
                        .key("row1")
                        .value("value1")
                        .label("label-1")
                        .lastChangeTimestamp(MiscUtil.asXMLGregorianCalendar(1L)))
                .row(new LookupTableRowType()
                        .key("row2")
                        .value("value2")
                        .lastChangeTimestamp(MiscUtil.asXMLGregorianCalendar(2L)))
                .row(new LookupTableRowType()
                        .key("row3"));

        when("adding it to the repository");
        repositoryService.addObject(lookupTable.asPrismObject(), null, result);

        then("it is stored with its persisted trigger containers");
        assertThatOperationResult(result).isSuccess();

        QLookupTableRow ltRow = aliasFor(QLookupTableRow.class);
        List<MLookupTableRow> rows =
                select(ltRow, ltRow.ownerOid.eq(UUID.fromString(lookupTable.getOid())));
        assertThat(rows).hasSize(3);

        rows.sort(comparing(tr -> tr.cid));
        MLookupTableRow containerRow = rows.get(0);
        assertThat(containerRow.cid).isEqualTo(1);
        assertThat(containerRow.key).isEqualTo("row1");
        assertThat(containerRow.value).isEqualTo("value1");
        assertThat(containerRow.labelOrig).isEqualTo("label-1");
        assertThat(containerRow.labelNorm).isEqualTo("label1");
        assertThat(containerRow.lastChangeTimestamp).isEqualTo(Instant.ofEpochMilli(1));

        containerRow = rows.get(1);
        assertThat(containerRow.cid).isEqualTo(2);
        assertThat(containerRow.key).isEqualTo("row2");
        assertThat(containerRow.value).isEqualTo("value2");
        assertThat(containerRow.lastChangeTimestamp).isEqualTo(Instant.ofEpochMilli(2));

        containerRow = rows.get(2);
        assertThat(containerRow.cid).isEqualTo(3);
        assertThat(containerRow.key).isEqualTo("row3");
    }

    // TODO test for object's related entities?
    // - trigger
    // - operation execution

    @Test
    public void test810ResourceAndItsBusinessApproverReferences() throws Exception {
        OperationResult result = createOperationResult();

        given("resource");
        String objectName = "res" + getTestNumber();
        UUID connectorOid = UUID.randomUUID();
        QName approver1Relation = QName.valueOf("{https://random.org/ns}random-rel-1");
        QName approver2Relation = QName.valueOf("{https://random.org/ns}random-rel-2");
        QName connectorRelation = QName.valueOf("{https://random.org/ns}conn-rel");
        ResourceType resource = new ResourceType(prismContext)
                .name(objectName)
                .business(new ResourceBusinessConfigurationType(prismContext)
                        .administrativeState(ResourceAdministrativeStateType.DISABLED)
                        .approverRef(UUID.randomUUID().toString(),
                                UserType.COMPLEX_TYPE, approver1Relation)
                        .approverRef(UUID.randomUUID().toString(),
                                ServiceType.COMPLEX_TYPE, approver2Relation))
                .operationalState(new OperationalStateType()
                        .lastAvailabilityStatus(AvailabilityStatusType.BROKEN))
                .connectorRef(connectorOid.toString(),
                        ConnectorType.COMPLEX_TYPE, connectorRelation);

        when("adding it to the repository");
        repositoryService.addObject(resource.asPrismObject(), null, result);

        then("it is stored and relevant attributes are in columns");
        assertThatOperationResult(result).isSuccess();

        MResource row = selectObjectByOid(QResource.class, resource.getOid());
        assertThat(row.businessAdministrativeState)
                .isEqualTo(ResourceAdministrativeStateType.DISABLED);
        assertThat(row.operationalStateLastAvailabilityStatus)
                .isEqualTo(AvailabilityStatusType.BROKEN);
        assertThat(row.connectorRefTargetOid).isEqualTo(connectorOid);
        assertThat(row.connectorRefTargetType).isEqualTo(MObjectType.CONNECTOR);
        assertCachedUri(row.connectorRefRelationId, connectorRelation);

        QObjectReference ref = QObjectReferenceMapping
                .INSTANCE_RESOURCE_BUSINESS_CONFIGURATION_APPROVER.defaultAlias();
        List<MReference> refs = select(ref, ref.ownerOid.eq(row.oid));
        assertThat(refs).hasSize(2);

        refs.sort(comparing(rr -> rr.targetType));
        MReference refRow = refs.get(0);
        assertThat(refRow.referenceType)
                .isEqualTo(MReferenceType.RESOURCE_BUSINESS_CONFIGURATION_APPROVER);
        assertThat(refRow.targetType).isEqualTo(MObjectType.SERVICE);
        assertCachedUri(refRow.relationId, approver2Relation);
    }

    @Test
    public void test811Connector() throws Exception {
        OperationResult result = createOperationResult();

        given("connector");
        String objectName = "conn" + getTestNumber();
        UUID connectorHostOid = UUID.randomUUID();
        QName connectorHostRelation = QName.valueOf("{https://random.org/ns}conn-host-rel");
        ConnectorType connector = new ConnectorType(prismContext)
                .name(objectName)
                .connectorBundle("com.connector.package")
                .connectorType("ConnectorTypeClass")
                .connectorVersion("1.2.3")
                .framework(SchemaConstants.UCF_FRAMEWORK_URI_BUILTIN)
                .connectorHostRef(connectorHostOid.toString(),
                        ConnectorHostType.COMPLEX_TYPE, connectorHostRelation)
                .targetSystemType("type1")
                .targetSystemType("type2");

        when("adding it to the repository");
        repositoryService.addObject(connector.asPrismObject(), null, result);

        then("it is stored and relevant attributes are in columns");
        assertThatOperationResult(result).isSuccess();

        MConnector row = selectObjectByOid(QConnector.class, connector.getOid());
        assertThat(row.connectorBundle).isEqualTo("com.connector.package");
        assertThat(row.connectorType).isEqualTo("ConnectorTypeClass");
        assertThat(row.connectorVersion).isEqualTo("1.2.3");
        assertCachedUri(row.frameworkId, SchemaConstants.UCF_FRAMEWORK_URI_BUILTIN);
        assertThat(row.connectorHostRefTargetOid).isEqualTo(connectorHostOid);
        assertThat(row.connectorHostRefTargetType).isEqualTo(MObjectType.CONNECTOR_HOST);
        assertCachedUri(row.connectorHostRefRelationId, connectorHostRelation);
        assertThat(row.targetSystemTypes).containsExactlyInAnyOrder("type1", "type2");
    }

    @Test
    public void test812ConnectorHost() throws Exception {
        OperationResult result = createOperationResult();

        given("connector host");
        String objectName = "conn-host" + getTestNumber();
        ConnectorHostType connectorHost = new ConnectorHostType(prismContext)
                .name(objectName)
                .hostname("hostname")
                .port("port");

        when("adding it to the repository");
        repositoryService.addObject(connectorHost.asPrismObject(), null, result);

        then("it is stored and relevant attributes are in columns");
        assertThatOperationResult(result).isSuccess();

        MConnectorHost row = selectObjectByOid(QConnectorHost.class, connectorHost.getOid());
        assertThat(row.hostname).isEqualTo("hostname");
        assertThat(row.port).isEqualTo("port");
    }

    @Test
    public void test813Report() throws Exception {
        OperationResult result = createOperationResult();

        given("report");
        String objectName = "report" + getTestNumber();
        ReportType report = new ReportType(prismContext)
                .name(objectName)
                .jasper(new JasperReportEngineConfigurationType()
                        .orientation(OrientationType.LANDSCAPE)
                        .parent(true));

        when("adding it to the repository");
        repositoryService.addObject(report.asPrismObject(), null, result);

        then("it is stored and relevant attributes are in columns");
        assertThatOperationResult(result).isSuccess();

        MReport row = selectObjectByOid(QReport.class, report.getOid());
        assertThat(row.orientation).isEqualTo(OrientationType.LANDSCAPE);
        assertThat(row.parent).isTrue();
    }

    @Test
    public void test814ReportData() throws Exception {
        OperationResult result = createOperationResult();

        given("report data");
        String objectName = "report-data" + getTestNumber();
        UUID reportOid = UUID.randomUUID();
        QName reportRelation = QName.valueOf("{https://random.org/ns}report-rel");
        ReportDataType report = new ReportDataType(prismContext)
                .name(objectName)
                .reportRef(reportOid.toString(), ReportType.COMPLEX_TYPE, reportRelation);

        when("adding it to the repository");
        repositoryService.addObject(report.asPrismObject(), null, result);

        then("it is stored and relevant attributes are in columns");
        assertThatOperationResult(result).isSuccess();

        MReportData row = selectObjectByOid(QReportData.class, report.getOid());
        assertThat(row.reportRefTargetOid).isEqualTo(reportOid);
        assertThat(row.reportRefTargetType).isEqualTo(MObjectType.REPORT);
        assertCachedUri(row.reportRefRelationId, reportRelation);
    }

    @Test
    public void test818Shadow() throws Exception {
        OperationResult result = createOperationResult();

        given("shadow");
        String objectName = "shadow" + getTestNumber();
        QName objectClass = QName.valueOf("{https://random.org/ns}shadow-object-class");
        UUID resourceRefOid = UUID.randomUUID();
        QName resourceRefRelation = QName.valueOf("{https://random.org/ns}resource-ref-rel");
        ShadowType shadow = new ShadowType(prismContext)
                .name(objectName)
                .objectClass(objectClass)
                .resourceRef(resourceRefOid.toString(),
                        ResourceType.COMPLEX_TYPE, resourceRefRelation)
                .intent("intent")
                .kind(ShadowKindType.ACCOUNT)
                // TODO attemptNumber used at all?
                .dead(false)
                .exists(true)
                .fullSynchronizationTimestamp(MiscUtil.asXMLGregorianCalendar(1L))
                .pendingOperation(new PendingOperationType().attemptNumber(1))
                .pendingOperation(new PendingOperationType().attemptNumber(2))
                .primaryIdentifierValue("PID")
                .synchronizationSituation(SynchronizationSituationType.DISPUTED)
                .synchronizationTimestamp(MiscUtil.asXMLGregorianCalendar(2L));

        when("adding it to the repository");
        repositoryService.addObject(shadow.asPrismObject(), null, result);

        then("it is stored and relevant attributes are in columns");
        assertThatOperationResult(result).isSuccess();

        MShadow row = selectObjectByOid(QShadow.class, shadow.getOid());
        assertCachedUri(row.objectClassId, objectClass);
        assertThat(row.resourceRefTargetOid).isEqualTo(resourceRefOid);
        assertThat(row.resourceRefTargetType).isEqualTo(MObjectType.RESOURCE);
        assertCachedUri(row.resourceRefRelationId, resourceRefRelation);
        assertThat(row.intent).isEqualTo("intent");
        assertThat(row.kind).isEqualTo(ShadowKindType.ACCOUNT);
        assertThat(row.dead).isEqualTo(false);
        assertThat(row.exist).isEqualTo(true);
        assertThat(row.fullSynchronizationTimestamp).isEqualTo(Instant.ofEpochMilli(1));
        assertThat(row.pendingOperationCount).isEqualTo(2);
        assertThat(row.primaryIdentifierValue).isEqualTo("PID");
        assertThat(row.synchronizationSituation).isEqualTo(SynchronizationSituationType.DISPUTED);
        assertThat(row.synchronizationTimestamp).isEqualTo(Instant.ofEpochMilli(2));
    }

    // this covers mapping of attributes in FocusSqlTransformer + GenericObject
    @Test
    public void test820GenericObject() throws Exception {
        OperationResult result = createOperationResult();

        given("generic object");
        String objectName = "go" + getTestNumber();
        GenericObjectType genericObject = new GenericObjectType(prismContext)
                .name(objectName)
                .costCenter("cost-center")
                .emailAddress("email-address")
                .jpegPhoto(new byte[] { 1, 2, 3, 4, 5 })
                .locale("locale")
                .locality("locality")
                .preferredLanguage("preferred-language")
                .telephoneNumber("telephone-number")
                .timezone("timezone")
                .credentials(new CredentialsType()
                        .password(new PasswordType()
                                .metadata(new MetadataType()
                                        .createTimestamp(MiscUtil.asXMLGregorianCalendar(1L))
                                        .modifyTimestamp(MiscUtil.asXMLGregorianCalendar(2L)))))
                .activation(new ActivationType()
                        .administrativeStatus(ActivationStatusType.ENABLED)
                        .effectiveStatus(ActivationStatusType.DISABLED)
                        .enableTimestamp(MiscUtil.asXMLGregorianCalendar(3L))
                        .disableTimestamp(MiscUtil.asXMLGregorianCalendar(4L))
                        .disableReason("disable-reason")
                        .validityStatus(TimeIntervalStatusType.IN)
                        .validFrom(MiscUtil.asXMLGregorianCalendar(5L))
                        .validTo(MiscUtil.asXMLGregorianCalendar(6L))
                        .validityChangeTimestamp(MiscUtil.asXMLGregorianCalendar(7L))
                        .archiveTimestamp(MiscUtil.asXMLGregorianCalendar(8L))
                        .lockoutStatus(LockoutStatusType.NORMAL))
                // this is the only additional persisted field for GenericObject
                .objectType("some-custom-object-type-uri");

        when("adding it to the repository");
        repositoryService.addObject(genericObject.asPrismObject(), null, result);

        then("it is stored and relevant attributes are in columns");
        assertThatOperationResult(result).isSuccess();

        MGenericObject row = selectObjectByOid(
                QGenericObject.class, UUID.fromString(genericObject.getOid()));
        assertThat(row.costCenter).isEqualTo("cost-center");
        assertThat(row.emailAddress).isEqualTo("email-address");
        assertThat(row.photo).isEqualTo(new byte[] { 1, 2, 3, 4, 5 });
        assertThat(row.locale).isEqualTo("locale");
        assertThat(row.localityOrig).isEqualTo("locality");
        assertThat(row.localityNorm).isEqualTo("locality");
        assertThat(row.preferredLanguage).isEqualTo("preferred-language");
        assertThat(row.telephoneNumber).isEqualTo("telephone-number");
        assertThat(row.timezone).isEqualTo("timezone");

        assertThat(row.passwordCreateTimestamp).isEqualTo(Instant.ofEpochMilli(1));
        assertThat(row.passwordModifyTimestamp).isEqualTo(Instant.ofEpochMilli(2));

        assertThat(row.administrativeStatus).isEqualTo(ActivationStatusType.ENABLED);
        assertThat(row.effectiveStatus).isEqualTo(ActivationStatusType.DISABLED);
        assertThat(row.enableTimestamp).isEqualTo(Instant.ofEpochMilli(3));
        assertThat(row.disableTimestamp).isEqualTo(Instant.ofEpochMilli(4));
        assertThat(row.disableReason).isEqualTo("disable-reason");
        assertThat(row.validityStatus).isEqualTo(TimeIntervalStatusType.IN);
        assertThat(row.validFrom).isEqualTo(Instant.ofEpochMilli(5));
        assertThat(row.validTo).isEqualTo(Instant.ofEpochMilli(6));
        assertThat(row.validityChangeTimestamp).isEqualTo(Instant.ofEpochMilli(7));
        assertThat(row.archiveTimestamp).isEqualTo(Instant.ofEpochMilli(8));
        assertThat(row.lockoutStatus).isEqualTo(LockoutStatusType.NORMAL);

        // field specific to GenericObjectType
        assertCachedUri(row.genericObjectTypeId, "some-custom-object-type-uri");
    }

    // TODO test for focus' related entities?

    @Test
    public void test830Task() throws Exception {
        OperationResult result = createOperationResult();

        given("task");
        String objectName = "task" + getTestNumber();
        UUID objectRefOid = UUID.randomUUID();
        UUID ownerRefOid = UUID.randomUUID();
        QName relationUri = QName.valueOf("{https://some.uri}someRelation");
        var task = new TaskType(prismContext)
                .name(objectName)
                .taskIdentifier("task-id")
                .binding(TaskBindingType.LOOSE)
                .category("category")
                .completionTimestamp(MiscUtil.asXMLGregorianCalendar(1L))
                .executionStatus(TaskExecutionStateType.RUNNABLE)
                // TODO full result?
                .handlerUri("handler-uri")
                .lastRunStartTimestamp(MiscUtil.asXMLGregorianCalendar(1L))
                .lastRunFinishTimestamp(MiscUtil.asXMLGregorianCalendar(2L))
                .node("node")
                .objectRef(objectRefOid.toString(), OrgType.COMPLEX_TYPE, relationUri)
                .ownerRef(ownerRefOid.toString(), UserType.COMPLEX_TYPE, relationUri)
                .parent("parent")
                .recurrence(TaskRecurrenceType.RECURRING)
                .resultStatus(OperationResultStatusType.UNKNOWN)
                .threadStopAction(ThreadStopActionType.RESCHEDULE)
                .waitingReason(TaskWaitingReasonType.OTHER_TASKS)
                .dependent("dep-task-1")
                .dependent("dep-task-2");

        when("adding it to the repository");
        repositoryService.addObject(task.asPrismObject(), null, result);

        then("it is stored and relevant attributes are in columns");
        assertThatOperationResult(result).isSuccess();

        MTask row = selectObjectByOid(QTask.class, task.getOid());
        assertThat(row.taskIdentifier).isEqualTo("task-id");
        assertThat(row.binding).isEqualTo(TaskBindingType.LOOSE);
        assertThat(row.category).isEqualTo("category");
        assertThat(row.completionTimestamp).isEqualTo(Instant.ofEpochMilli(1));
        assertThat(row.executionStatus).isEqualTo(TaskExecutionStateType.RUNNABLE);
        assertCachedUri(row.handlerUriId, "handler-uri");
        assertThat(row.lastRunStartTimestamp).isEqualTo(Instant.ofEpochMilli(1));
        assertThat(row.lastRunFinishTimestamp).isEqualTo(Instant.ofEpochMilli(2));
        assertThat(row.node).isEqualTo("node");
        assertThat(row.objectRefTargetOid).isEqualTo(objectRefOid);
        assertThat(row.objectRefTargetType).isEqualTo(MObjectType.ORG);
        assertCachedUri(row.objectRefRelationId, relationUri);
        assertThat(row.ownerRefTargetOid).isEqualTo(ownerRefOid);
        assertThat(row.ownerRefTargetType).isEqualTo(MObjectType.USER);
        assertCachedUri(row.ownerRefRelationId, relationUri);
        assertThat(row.parent).isEqualTo("parent");
        assertThat(row.recurrence).isEqualTo(TaskRecurrenceType.RECURRING);
        assertThat(row.resultStatus).isEqualTo(OperationResultStatusType.UNKNOWN);
        assertThat(row.threadStopAction).isEqualTo(ThreadStopActionType.RESCHEDULE);
        assertThat(row.waitingReason).isEqualTo(TaskWaitingReasonType.OTHER_TASKS);
        assertThat(row.dependentTaskIdentifiers)
                .containsExactlyInAnyOrder("dep-task-1", "dep-task-2");
    }

    @Test
    public void test840AccessCertificationDefinition() throws Exception {
        OperationResult result = createOperationResult();

        given("access certification definition");
        String objectName = "acd" + getTestNumber();
        UUID ownerRefOid = UUID.randomUUID();
        Instant lastCampaignStarted = Instant.ofEpochMilli(1); // 0 means null in MiscUtil
        Instant lastCampaignClosed = Instant.ofEpochMilli(System.currentTimeMillis());
        QName relationUri = QName.valueOf("{https://some.uri}specialRelation");
        var accessCertificationDefinition = new AccessCertificationDefinitionType(prismContext)
                .name(objectName)
                .handlerUri("handler-uri")
                .lastCampaignStartedTimestamp(MiscUtil.asXMLGregorianCalendar(lastCampaignStarted))
                .lastCampaignClosedTimestamp(MiscUtil.asXMLGregorianCalendar(lastCampaignClosed))
                .ownerRef(ownerRefOid.toString(), UserType.COMPLEX_TYPE, relationUri);

        when("adding it to the repository");
        repositoryService.addObject(accessCertificationDefinition.asPrismObject(), null, result);

        then("it is stored and relevant attributes are in columns");
        assertThatOperationResult(result).isSuccess();

        MAccessCertificationDefinition row = selectObjectByOid(
                QAccessCertificationDefinition.class, accessCertificationDefinition.getOid());
        assertCachedUri(row.handlerUriId, "handler-uri");
        assertThat(row.lastCampaignStartedTimestamp).isEqualTo(lastCampaignStarted);
        assertThat(row.lastCampaignClosedTimestamp).isEqualTo(lastCampaignClosed);
        assertThat(row.ownerRefTargetOid).isEqualTo(ownerRefOid);
        assertThat(row.ownerRefTargetType).isEqualTo(MObjectType.USER);
        assertCachedUri(row.ownerRefRelationId, relationUri);
    }
    // endregion

    // region delete tests
    // when we get here we have couple of users and some other types stored in the repository
    @Test
    public void test900DeleteOfNonexistentObjectFails() {
        OperationResult result = createOperationResult();

        given("nonexistent OID");
        String oid = UUID.randomUUID().toString();

        expect("deleting object fails");
        assertThatThrownBy(() -> repositoryService.deleteObject(ObjectType.class, oid, result))
                .isInstanceOf(ObjectNotFoundException.class);

        and("operation result is fatal error");
        assertThatOperationResult(result).isFatalError()
                .hasMessageMatching("Object of type 'ObjectType' with OID .* was not found\\.");
    }

    @Test
    public void test910DeleteUsingSupertypeWorksOk() throws Exception {
        OperationResult result = createOperationResult();

        given("any object existing in the repository");
        // we'll sacrifice one of the users for this
        UUID userOid = randomExistingOid(QUser.class);

        when("deleting the object using its supertype");
        DeleteObjectResult deleteResult =
                repositoryService.deleteObject(ObjectType.class, userOid.toString(), result);

        then("object is deleted");
        assertThatOperationResult(result).isSuccess();

        assertThat(deleteResult).isNotNull();
        assertThat(deleteResult.getObjectTextRepresentation()).isNotNull();

        assertThat(selectObjectByOid(QUser.class, userOid)).isNull();
    }

    // slight variation of the above, but using lower-level abstract table
    @Test
    public void test911DeleteUserUsingFocusWorksOk() throws Exception {
        OperationResult result = createOperationResult();

        given("random user existing in the repository");
        // we'll sacrifice one of the users for this
        UUID userOid = randomExistingOid(QUser.class);

        when("deleting it using the focus type");
        DeleteObjectResult deleteResult =
                repositoryService.deleteObject(FocusType.class, userOid.toString(), result);

        then("user is deleted");
        assertThatOperationResult(result).isSuccess();

        assertThat(deleteResult).isNotNull();
        assertThat(deleteResult.getObjectTextRepresentation()).isNotNull();

        assertThat(selectObjectByOid(QUser.class, userOid)).isNull();
    }

    @Test
    public void test920DeleteAllOtherObjects() throws Exception {
        // this doesn't follow given-when-then, sorry
        OperationResult result = createOperationResult();

        // we didn't create that many in this class, it should be OK to read them all at once
        for (MObject row : select(QObject.CLASS)) {
            DeleteObjectResult deleteResult = repositoryService.deleteObject(
                    row.objectType.getSchemaType(), row.oid.toString(), result);

            assertThat(deleteResult).isNotNull();
        }

        assertThatOperationResult(result).isSuccess();

        assertThat(select(QObject.CLASS)).isEmpty();
        assertThat(select(QContainer.CLASS)).isEmpty();
        assertThat(select(QReference.CLASS)).isEmpty();
    }

    protected <R extends MObject, Q extends QObject<R>> UUID randomExistingOid(Class<Q> queryType) {
        try (JdbcSession jdbcSession = sqlRepoContext.newJdbcSession()) {
            Q path = aliasFor(queryType);
            return jdbcSession.newQuery()
                    .from(path)
                    .orderBy(path.oid.asc())
                    .select(path.oid)
                    .fetchFirst();
        }
    }
    // endregion
}
