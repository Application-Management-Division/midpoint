/*
 * Copyright (C) 2010-2023 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.common.mining.objects.detection;

import com.evolveum.midpoint.prism.impl.binding.AbstractReferencable;
import com.evolveum.midpoint.xml.ns._public.common.common_3.RoleAnalysisDetectionPatternType;

import java.io.Serializable;
import java.util.Set;

public abstract class BasePattern implements Serializable {

    protected final Set<String> roles;
    protected final Set<String> users;
    protected Double metric;
    protected Long id;
    protected String identifier;
    protected String associatedColor;

    public BasePattern(Set<String> roles,
            Set<String> users,
            Double metric,
            Long id,
            String identifier,
            String associatedColor) {
        this.roles = roles;
        this.users = users;
        this.metric = metric;
        this.id = id;
        this.identifier = identifier;
        this.associatedColor = associatedColor;
    }

    public BasePattern(RoleAnalysisDetectionPatternType detectionPattern) {
        this.roles = detectionPattern.getRolesOccupancy()
                .stream().map(AbstractReferencable::getOid).collect(java.util.stream.Collectors.toSet());
        this.users = detectionPattern.getUserOccupancy()
                .stream().map(AbstractReferencable::getOid).collect(java.util.stream.Collectors.toSet());
        this.metric = detectionPattern.getClusterMetric();
        this.id = detectionPattern.getId();
        if (id != null) {
            this.identifier = id.toString();
        } else {
            this.identifier = null;
        }
        this.associatedColor = null;
    }

    public BasePattern(Set<String> roles, Set<String> users,
            double clusterMetric, Long patternId) {
        this.roles = roles;
        this.users = users;
        this.metric = clusterMetric;
        this.id = patternId;
        if (id != null) {
            this.identifier = id.toString();
        } else {
            this.identifier = null;
        }
        this.associatedColor = null;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public Set<String> getUsers() {
        return users;
    }

    public Double getMetric() {
        return metric;
    }

    public Long getId() {
        return id;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getAssociatedColor() {
        return associatedColor;
    }

    public String getCandidateRoleIdToString() {
        if (id == null) {
            return "";
        }
        return id.toString();
    }

    public void setMetric(Double metric) {
        this.metric = metric;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public void setAssociatedColor(String associatedColor) {
        this.associatedColor = associatedColor;
    }

}
