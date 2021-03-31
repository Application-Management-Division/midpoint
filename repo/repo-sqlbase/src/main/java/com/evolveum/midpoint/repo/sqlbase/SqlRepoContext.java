/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqlbase;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

import com.querydsl.sql.Configuration;
import com.querydsl.sql.RelationalPath;
import com.querydsl.sql.SQLQuery;
import com.querydsl.sql.SQLTemplates;
import com.querydsl.sql.dml.SQLDeleteClause;
import com.querydsl.sql.dml.SQLInsertClause;
import com.querydsl.sql.dml.SQLUpdateClause;

import com.evolveum.midpoint.repo.sqlbase.mapping.QueryTableMapping;
import com.evolveum.midpoint.repo.sqlbase.mapping.QueryModelMappingRegistry;
import com.evolveum.midpoint.repo.sqlbase.querydsl.FlexibleRelationalPathBase;
import com.evolveum.midpoint.repo.sqlbase.querydsl.QuerydslUtils;
import com.evolveum.midpoint.util.exception.SystemException;

/**
 * Encapsulates Querydsl {@link Configuration}, our {@link QueryModelMappingRegistry}
 * and other parts of SQL repository config and implements methods that need these.
 * Preferably, it should hide (hence "encapsulate") the fields and offer behavior instead.
 */
public class SqlRepoContext {

    private final JdbcRepositoryConfiguration jdbcRepositoryConfiguration;
    protected final Configuration querydslConfig;
    private final QueryModelMappingRegistry mappingRegistry;
    private final DataSource dataSource;

    public SqlRepoContext(
            JdbcRepositoryConfiguration jdbcRepositoryConfiguration,
            DataSource dataSource,
            QueryModelMappingRegistry mappingRegistry) {
        this.jdbcRepositoryConfiguration = jdbcRepositoryConfiguration;
        this.querydslConfig = QuerydslUtils.querydslConfiguration(
                jdbcRepositoryConfiguration.getDatabaseType());
        this.mappingRegistry = mappingRegistry;
        this.dataSource = dataSource;
    }

    public SQLQuery<?> newQuery() {
        return new SQLQuery<>(querydslConfig);
    }

    public SQLQuery<?> newQuery(Connection conn) {
        return new SQLQuery<>(conn, querydslConfig);
    }

    public <DR, DQ extends FlexibleRelationalPathBase<DR>> QueryTableMapping<?, DQ, DR>
    getMappingByQueryType(Class<DQ> queryType) {
        return mappingRegistry.getByQueryType(queryType);
    }

    // QM is potentially narrowed expected subtype of QueryModelMapping
    public <S, Q extends FlexibleRelationalPathBase<R>, R, QM extends QueryTableMapping<S, Q, R>>
    QM getMappingBySchemaType(Class<S> schemaType) {
        return mappingRegistry.getBySchemaType(schemaType);
    }

    public SQLTemplates getQuerydslTemplates() {
        return querydslConfig.getTemplates();
    }

    public SQLInsertClause newInsert(Connection connection, RelationalPath<?> entity) {
        return new SQLInsertClause(connection, querydslConfig, entity);
    }

    public SQLUpdateClause newUpdate(Connection connection, RelationalPath<?> entity) {
        return new SQLUpdateClause(connection, querydslConfig, entity);
    }

    public SQLDeleteClause newDelete(Connection connection, RelationalPath<?> entity) {
        return new SQLDeleteClause(connection, querydslConfig, entity);
    }

    public JdbcRepositoryConfiguration getJdbcRepositoryConfiguration() {
        return jdbcRepositoryConfiguration;
    }

    /**
     * Creates {@link JdbcSession} that typically represents transactional work on JDBC connection.
     * All other lifecycle methods are to be called on the returned object.
     * Object is {@link AutoCloseable} and can be used in try-with-resource blocks.
     * This call be followed by {@link JdbcSession#startTransaction()} (or one of its variants).
     * If the transaction is not started the connection will likely be in auto-commit mode.
     * *We want to start transaction for any work in production code* but for tests it's ok not to.
     */
    public JdbcSession newJdbcSession() {
        try {
            return new JdbcSession(dataSource.getConnection(), jdbcRepositoryConfiguration, this);
        } catch (SQLException e) {
            throw new SystemException("Cannot create JDBC connection", e);
        }
    }
}
