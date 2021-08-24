-- 2020-05-29 09:20

CREATE INDEX iOpExecTimestampValue
  ON m_operation_execution (timestampValue);

-- 2020-06-25 11:35

ALTER TABLE m_acc_cert_campaign ALTER COLUMN definitionRef_type RENAME TO definitionRef_targetType;
ALTER TABLE m_acc_cert_campaign ALTER COLUMN ownerRef_type RENAME TO ownerRef_targetType;
ALTER TABLE m_acc_cert_definition ALTER COLUMN ownerRef_type RENAME TO ownerRef_targetType;
ALTER TABLE m_connector ALTER COLUMN connectorHostRef_type RENAME TO connectorHostRef_targetType;
ALTER TABLE m_object ALTER COLUMN creatorRef_type RENAME TO creatorRef_targetType;
ALTER TABLE m_object ALTER COLUMN modifierRef_type RENAME TO modifierRef_targetType;
ALTER TABLE m_object ALTER COLUMN tenantRef_type RENAME TO tenantRef_targetType;
ALTER TABLE m_report_output ALTER COLUMN reportRef_type RENAME TO reportRef_targetType;
ALTER TABLE m_resource ALTER COLUMN connectorRef_type RENAME TO connectorRef_targetType;
ALTER TABLE m_shadow ALTER COLUMN resourceRef_type RENAME TO resourceRef_targetType;
ALTER TABLE m_acc_cert_case ALTER COLUMN objectRef_type RENAME TO objectRef_targetType;
ALTER TABLE m_acc_cert_case ALTER COLUMN orgRef_type RENAME TO orgRef_targetType;
ALTER TABLE m_acc_cert_case ALTER COLUMN targetRef_type RENAME TO targetRef_targetType;
ALTER TABLE m_acc_cert_case ALTER COLUMN tenantRef_type RENAME TO tenantRef_targetType;
ALTER TABLE m_acc_cert_wi ALTER COLUMN performerRef_type RENAME TO performerRef_targetType;
ALTER TABLE m_assignment ALTER COLUMN creatorRef_type RENAME TO creatorRef_targetType;
ALTER TABLE m_assignment ALTER COLUMN modifierRef_type RENAME TO modifierRef_targetType;
ALTER TABLE m_assignment ALTER COLUMN orgRef_type RENAME TO orgRef_targetType;
ALTER TABLE m_assignment ALTER COLUMN resourceRef_type RENAME TO resourceRef_targetType;
ALTER TABLE m_assignment ALTER COLUMN targetRef_type RENAME TO targetRef_targetType;
ALTER TABLE m_assignment ALTER COLUMN tenantRef_type RENAME TO tenantRef_targetType;
ALTER TABLE m_case_wi ALTER COLUMN originalAssigneeRef_type RENAME TO originalAssigneeRef_targetType;
ALTER TABLE m_case_wi ALTER COLUMN performerRef_type RENAME TO performerRef_targetType;
ALTER TABLE m_operation_execution ALTER COLUMN initiatorRef_type RENAME TO initiatorRef_targetType;
ALTER TABLE m_operation_execution ALTER COLUMN taskRef_type RENAME TO taskRef_targetType;
ALTER TABLE m_task ALTER COLUMN objectRef_type RENAME TO objectRef_targetType;
ALTER TABLE m_task ALTER COLUMN ownerRef_type RENAME TO ownerRef_targetType;
ALTER TABLE m_abstract_role ALTER COLUMN ownerRef_type RENAME TO ownerRef_targetType;
ALTER TABLE m_case ALTER COLUMN objectRef_type RENAME TO objectRef_targetType;
ALTER TABLE m_case ALTER COLUMN parentRef_type RENAME TO parentRef_targetType;
ALTER TABLE m_case ALTER COLUMN requestorRef_type RENAME TO requestorRef_targetType;
ALTER TABLE m_case ALTER COLUMN targetRef_type RENAME TO targetRef_targetType;

-- 2020-08-19 10:55

ALTER TABLE m_focus ADD COLUMN passwordCreateTimestamp TIMESTAMP;
ALTER TABLE m_focus ADD COLUMN passwordModifyTimestamp TIMESTAMP;

-- MID-6037
ALTER TABLE m_service ADD CONSTRAINT uc_service_name UNIQUE (name_norm);

-- MID-6232
CREATE INDEX iAuditEventRecordEStageTOid
  ON m_audit_event (eventStage, targetOid);

-- policySituation belong to M_OBJECT
ALTER TABLE m_focus_policy_situation DROP CONSTRAINT fk_focus_policy_situation;
ALTER TABLE m_focus_policy_situation RENAME TO m_object_policy_situation;
ALTER TABLE m_object_policy_situation ALTER COLUMN focus_oid RENAME TO object_oid;
ALTER TABLE m_object_policy_situation
  ADD CONSTRAINT fk_object_policy_situation FOREIGN KEY (object_oid) REFERENCES m_object;

COMMIT;

-- 4.3+ Changes

-- MID-6417
ALTER TABLE m_operation_execution ADD COLUMN recordType INTEGER;

-- MID-3669
ALTER TABLE m_focus ADD COLUMN lockoutStatus INTEGER;

-- 4.4+ Changes
ALTER TABLE m_task ADD COLUMN schedulingState INTEGER;
ALTER TABLE m_task ADD COLUMN autoScalingMode INTEGER;
ALTER TABLE m_node ADD COLUMN operationalState INTEGER;

-- WRITE CHANGES ABOVE ^^
UPDATE m_global_metadata SET value = '4.4' WHERE name = 'databaseSchemaVersion';
