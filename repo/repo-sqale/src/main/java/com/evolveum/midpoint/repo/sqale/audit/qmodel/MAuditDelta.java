/*
 * Copyright (C) 2010-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.audit.qmodel;

import static com.evolveum.midpoint.util.MiscUtil.binaryToHexPreview;

import com.evolveum.midpoint.prism.polystring.PolyString;

/**
 * Querydsl "row bean" type related to {@link QAuditDelta}.
 */
@SuppressWarnings("unused")
public class MAuditDelta {

    public Long recordId;
    public String checksum;
    public byte[] delta;
    public String deltaOid;
    public Integer deltaType;
    public byte[] fullResult;
    public String objectNameNorm;
    public String objectNameOrig;
    public String resourceNameNorm;
    public String resourceNameOrig;
    public String resourceOid;
    public Integer status;

    // "transient" fields not used by Querydsl
    public String serializedDelta;

    public PolyString getObjectName() {
        return new PolyString(objectNameOrig, objectNameNorm);
    }

    public PolyString getResourceName() {
        return new PolyString(resourceNameOrig, resourceNameNorm);
    }

    @Override
    public String toString() {
        return "MAuditDelta{" +
                "recordId=" + recordId +
                ", checksum='" + checksum + '\'' +
                ", delta=" + binaryToHexPreview(delta) +
                ", deltaOid='" + deltaOid + '\'' +
                ", deltaType=" + deltaType +
                ", fullResult=" + binaryToHexPreview(fullResult) +
                ", objectNameNorm='" + objectNameNorm + '\'' +
                ", objectNameOrig='" + objectNameOrig + '\'' +
                ", resourceNameNorm='" + resourceNameNorm + '\'' +
                ", resourceNameOrig='" + resourceNameOrig + '\'' +
                ", resourceOid='" + resourceOid + '\'' +
                ", status=" + status +
                '}';
    }
}
