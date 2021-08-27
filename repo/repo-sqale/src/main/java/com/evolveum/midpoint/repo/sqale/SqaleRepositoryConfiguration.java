/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.PostConstruct;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.repo.api.RepositoryServiceFactoryException;
import com.evolveum.midpoint.repo.sqlbase.JdbcRepositoryConfiguration;
import com.evolveum.midpoint.repo.sqlbase.SupportedDatabase;
import com.evolveum.midpoint.repo.sqlbase.TransactionIsolation;
import com.evolveum.midpoint.repo.sqlbase.perfmon.SqlPerformanceMonitorImpl;
import com.evolveum.midpoint.util.exception.SystemException;

/**
 * Common part of the SQL-based repository configuration.
 * Contains JDBC/datasource setup, connection pool configuration, but no framework/ORM stuff.
 */
public class SqaleRepositoryConfiguration implements JdbcRepositoryConfiguration {

    private static final String DEFAULT_DRIVER = "org.postgresql.Driver";
    private static final SupportedDatabase DEFAULT_DATABASE = SupportedDatabase.POSTGRESQL;

    private static final int DEFAULT_MIN_POOL_SIZE = 8;
    private static final int DEFAULT_MAX_POOL_SIZE = 20;

    private static final int DEFAULT_ITERATIVE_SEARCH_PAGE_SIZE = 100;

    @NotNull private final Configuration configuration;

    /** Database kind - either explicitly configured or derived from other options. */
    private SupportedDatabase databaseType;

    // either dataSource or JDBC URL must be set
    private String dataSource;
    private String jdbcUrl;
    private String jdbcUsername;
    private String jdbcPassword;

    private String driverClassName;

    private int minPoolSize;
    private int maxPoolSize;

    private boolean useZip;
    private boolean useZipAudit;
    private String fullObjectFormat;

    private String performanceStatisticsFile;
    private int performanceStatisticsLevel;

    private int iterativeSearchByPagingBatchSize;

    // Provided with configuration node "midpoint.repository".
    public SqaleRepositoryConfiguration(@NotNull Configuration configuration) {
        this.configuration = configuration;
    }

    @PostConstruct
    public void init() throws RepositoryServiceFactoryException {
        dataSource = configuration.getString(PROPERTY_DATASOURCE);

        jdbcUrl = configuration.getString(PROPERTY_JDBC_URL);
        jdbcUsername = configuration.getString(PROPERTY_JDBC_USERNAME);

        databaseType = DEFAULT_DATABASE;
        driverClassName = DEFAULT_DRIVER;

        String jdbcPasswordFile = configuration.getString(PROPERTY_JDBC_PASSWORD_FILE);
        if (jdbcPasswordFile != null) {
            try {
                jdbcPassword = Files.readString(Path.of(jdbcPasswordFile));
            } catch (IOException e) {
                throw new SystemException("Couldn't read JDBC password from specified file '"
                        + jdbcPasswordFile + "': " + e.getMessage(), e);
            }
        } else {
            jdbcPassword = configuration.getString(PROPERTY_JDBC_PASSWORD);
        }

        minPoolSize = configuration.getInt(PROPERTY_MIN_POOL_SIZE, DEFAULT_MIN_POOL_SIZE);
        maxPoolSize = configuration.getInt(PROPERTY_MAX_POOL_SIZE, DEFAULT_MAX_POOL_SIZE);

        useZip = configuration.getBoolean(PROPERTY_USE_ZIP, false);
        useZipAudit = configuration.getBoolean(PROPERTY_USE_ZIP_AUDIT, true);
        fullObjectFormat = configuration.getString(PROPERTY_FULL_OBJECT_FORMAT, PrismContext.LANG_XML)
                .toLowerCase(); // all language string constants are lower-cases

        performanceStatisticsFile = configuration.getString(PROPERTY_PERFORMANCE_STATISTICS_FILE);
        performanceStatisticsLevel = configuration.getInt(PROPERTY_PERFORMANCE_STATISTICS_LEVEL,
                SqlPerformanceMonitorImpl.LEVEL_LOCAL_STATISTICS);

        iterativeSearchByPagingBatchSize = configuration.getInt(
                PROPERTY_ITERATIVE_SEARCH_BY_PAGING_BATCH_SIZE, DEFAULT_ITERATIVE_SEARCH_PAGE_SIZE);

        validateConfiguration();
    }

    private void validateConfiguration() throws RepositoryServiceFactoryException {
        if (dataSource == null) {
            notEmpty(jdbcUrl, "JDBC Url is empty or not defined.");
            // We don't check username and password, they can be null (MID-5342)
            // In case of configuration mismatch we let the JDBC driver to fail.
            notEmpty(driverClassName, "Driver class name is empty or not defined.");
        }
    }

    public @NotNull SupportedDatabase getDatabaseType() {
        return databaseType;
    }

    public String getDataSource() {
        return dataSource;
    }

    protected void notEmpty(String value, String message) throws RepositoryServiceFactoryException {
        if (StringUtils.isEmpty(value)) {
            throw new RepositoryServiceFactoryException(message);
        }
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public String getJdbcUsername() {
        return jdbcUsername;
    }

    public String getJdbcPassword() {
        return jdbcPassword;
    }

    public boolean isUseZip() {
        return useZip;
    }

    public boolean isUseZipAudit() {
        return useZipAudit;
    }

    /**
     * Returns serialization format (language) for writing fullObject.
     * Also see {@link #PROPERTY_FULL_OBJECT_FORMAT}.
     */
    public String getFullObjectFormat() {
        return fullObjectFormat;
    }

    @Override
    public boolean isEmbedded() {
        return false;
    }

    @Override
    public String getDefaultEmbeddedJdbcUrlPrefix() {
        throw new UnsupportedOperationException(
                "This configuration (repository factory) does not support embedded database.");
    }

    @Override
    public boolean isUsing(SupportedDatabase db) {
        return databaseType == db;
    }

    // TODO - IMPLEMENT EVERYTHING BELOW
    @Override
    public TransactionIsolation getTransactionIsolation() {
        return null;
    }

    @Override
    public boolean useSetReadOnlyOnConnection() {
        return true;
    }

    @Override
    public int getMinPoolSize() {
        return minPoolSize;
    }

    @Override
    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    @Override
    public Long getMaxLifetime() {
        // TODO implement
        return null;
    }

    @Override
    public Long getIdleTimeout() {
        // TODO implement
        return null;
    }

    @Override
    public long getInitializationFailTimeout() {
        // TODO implement
        return 0;
    }

    @Override
    public boolean isFatalException(Throwable ex) {
        // TODO implement
        // by default, any exception is fatal, unless specified otherwise (not yet implemented)
        return true;
    }

    @Override
    public String getPerformanceStatisticsFile() {
        return performanceStatisticsFile;
    }

    @Override
    public int getPerformanceStatisticsLevel() {
        return performanceStatisticsLevel;
    }

    @Override
    public int getIterativeSearchByPagingBatchSize() {
        return iterativeSearchByPagingBatchSize;
    }

    // exists because of testing
    public void setIterativeSearchByPagingBatchSize(int iterativeSearchByPagingBatchSize) {
        this.iterativeSearchByPagingBatchSize = iterativeSearchByPagingBatchSize;
    }
}
