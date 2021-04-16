/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.*;
import java.util.function.Consumer;
import javax.annotation.PreDestroy;

import com.google.common.base.Strings;
import com.querydsl.core.Tuple;
import com.querydsl.sql.SQLQuery;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.evolveum.midpoint.common.crypto.CryptoUtil;
import com.evolveum.midpoint.prism.ConsistencyCheckScope;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ItemDeltaCollectionsUtil;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.prism.equivalence.EquivalenceStrategy;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.repo.api.*;
import com.evolveum.midpoint.repo.api.perf.PerformanceMonitor;
import com.evolveum.midpoint.repo.api.query.ObjectFilterExpressionEvaluator;
import com.evolveum.midpoint.repo.sqale.operations.AddObjectOperation;
import com.evolveum.midpoint.repo.sqale.qmodel.SqaleTableMapping;
import com.evolveum.midpoint.repo.sqale.qmodel.object.MObject;
import com.evolveum.midpoint.repo.sqale.qmodel.object.QObject;
import com.evolveum.midpoint.repo.sqlbase.*;
import com.evolveum.midpoint.repo.sqlbase.mapping.QueryTableMapping;
import com.evolveum.midpoint.repo.sqlbase.perfmon.SqlPerformanceMonitorImpl;
import com.evolveum.midpoint.schema.*;
import com.evolveum.midpoint.schema.internals.InternalMonitor;
import com.evolveum.midpoint.schema.internals.InternalsConfig;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

/**
 * Repository implementation based on SQL, JDBC and Querydsl without any ORM.
 * WORK IN PROGRESS:
 * It will be PostgreSQL only or at least PG optimized with generic SQL support for other unsupported DB.
 * Possible Oracle support is in play.
 */
public class SqaleRepositoryService implements RepositoryService {

    private static final Trace LOGGER = TraceManager.getTrace(SqaleRepositoryService.class);

    /**
     * Class name prefix for operation names, including the dot separator.
     * Use with various `RepositoryService.OP_*` constants, not with constants without `OP_`
     * prefix because they already contain class name of the service interface.
     */
    private static final String OP_NAME_PREFIX = SqaleRepositoryService.class.getSimpleName() + '.';

    private static final int MAX_CONFLICT_WATCHERS = 10;

    private final SqaleRepoContext sqlRepoContext;
    private final SchemaService schemaService;
    private final SqlQueryExecutor sqlQueryExecutor;
    private final SqaleTransformerSupport transformerSupport;
    private final SqlPerformanceMonitorsCollection sqlPerformanceMonitorsCollection;

    private final ThreadLocal<List<ConflictWatcherImpl>> conflictWatchersThreadLocal =
            ThreadLocal.withInitial(ArrayList::new);

    private SqlPerformanceMonitorImpl performanceMonitor; // set to null in destroy

    public SqaleRepositoryService(
            SqaleRepoContext sqlRepoContext,
            SchemaService schemaService,
            SqlPerformanceMonitorsCollection sqlPerformanceMonitorsCollection) {
        this.sqlRepoContext = sqlRepoContext;
        this.schemaService = schemaService;
        this.sqlQueryExecutor = new SqlQueryExecutor(sqlRepoContext);
        this.transformerSupport = new SqaleTransformerSupport(schemaService, sqlRepoContext);
        this.sqlPerformanceMonitorsCollection = sqlPerformanceMonitorsCollection;

        // monitor initialization and registration
        JdbcRepositoryConfiguration config = sqlRepoContext.getJdbcRepositoryConfiguration();
        performanceMonitor = new SqlPerformanceMonitorImpl(
                config.getPerformanceStatisticsLevel(), config.getPerformanceStatisticsFile());
        sqlPerformanceMonitorsCollection.register(performanceMonitor);
    }

    @Override
    public @NotNull <T extends ObjectType> PrismObject<T> getObject(Class<T> type, String oid,
            Collection<SelectorOptions<GetOperationOptions>> options, OperationResult parentResult)
            throws ObjectNotFoundException, SchemaException {

        Objects.requireNonNull(type, "Object type must not be null.");
        UUID oidUuid = checkOid(oid);
        Objects.requireNonNull(parentResult, "Operation result must not be null.");

        LOGGER.debug("Getting object '{}' with OID '{}': {}",
                type.getSimpleName(), oid, parentResult.getOperation());
        InternalMonitor.recordRepositoryRead(type, oid);

        OperationResult operationResult = parentResult.subresult(OP_NAME_PREFIX + OP_GET_OBJECT)
                .addQualifier(type.getSimpleName())
                .setMinor()
                .addParam("type", type.getName())
                .addParam("oid", oid)
                .build();

        try {
            PrismObject<T> object;
            try (JdbcSession jdbcSession =
                    sqlRepoContext.newJdbcSession().startReadOnlyTransaction()) {
                //noinspection unchecked
                object = (PrismObject<T>) readByOid(jdbcSession, type, oidUuid, options)
                        .asPrismObject();
            }

            // "objectLocal" is here just to provide effectively final variable for the lambda below
//            PrismObject<T> objectLocal = executeAttempts(oid, OP_GET_OBJECT, type, "getting",
//                    subResult, () -> objectRetriever.getObjectAttempt(type, oid, options, operationResult));
//            object = objectLocal;
            invokeConflictWatchers((w) -> w.afterGetObject(object));

            // TODO both update and get need this?
            ObjectTypeUtil.normalizeAllRelations(object, schemaService.relationRegistry());
            return object;
        } catch (RuntimeException e) { // TODO what else to catch?
            throw handledGeneralException(e, operationResult);
        } catch (Throwable t) {
            operationResult.recordFatalError(t);
            throw t;
        } finally {
            operationResult.computeStatusIfUnknown();
//            OperationLogger.logGetObject(type, oid, options, object, operationResult);
            // TODO some logging
        }
    }

    private UUID checkOid(String oid) {
        Objects.requireNonNull(oid, "OID must not be null");
        try {
            return UUID.fromString(oid);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("OID " + oid + " is invalid", e);
        }
    }

    /** Read object using provided {@link JdbcSession} as a part of already running transaction. */
    private <S extends ObjectType, Q extends QObject<R>, R extends MObject> S readByOid(
            @NotNull JdbcSession jdbcSession,
            @NotNull Class<S> schemaType,
            @NotNull UUID oid,
            Collection<SelectorOptions<GetOperationOptions>> options)
            throws SchemaException, ObjectNotFoundException {
        return readByOidInternal(jdbcSession, schemaType, oid, options, false);
    }

    /** Read object using provided {@link JdbcSession} and locks it for update. */
    private <S extends ObjectType, Q extends QObject<R>, R extends MObject> S readByOidAndLock(
            @NotNull JdbcSession jdbcSession,
            @NotNull Class<S> schemaType,
            @NotNull UUID oid,
            Collection<SelectorOptions<GetOperationOptions>> options)
            throws SchemaException, ObjectNotFoundException {
        return readByOidInternal(jdbcSession, schemaType, oid, options, true);
    }

    // use one of above to avoid ugly last boolean parameter
    private <S extends ObjectType, Q extends QObject<R>, R extends MObject> S readByOidInternal(
            @NotNull JdbcSession jdbcSession,
            @NotNull Class<S> schemaType,
            @NotNull UUID oid,
            Collection<SelectorOptions<GetOperationOptions>> options,
            boolean forUpdate)
            throws SchemaException, ObjectNotFoundException {

//        context.processOptions(options); TODO how to process option, is setting of select expressions enough?

        SqaleTableMapping<S, Q, R> rootMapping =
                sqlRepoContext.getMappingBySchemaType(schemaType);
        Q root = rootMapping.defaultAlias();

        SQLQuery<?> sqlQuery = sqlRepoContext.newQuery(jdbcSession.connection());
        if (forUpdate) {
            sqlQuery.forUpdate();
        }

        Tuple result = sqlQuery
                .from(root)
                .select(rootMapping.selectExpressions(root, options))
                .where(root.oid.eq(oid))
                .fetchOne();

        if (result == null || result.get(root.fullObject) == null) {
            String oidString = oid.toString();
            throw new ObjectNotFoundException(schemaType, oidString);
        }

        return rootMapping.createTransformer(transformerSupport)
                .toSchemaObject(result, root, options);
    }

    @Override
    public <T extends ObjectType> String getVersion(
            Class<T> type, String oid, OperationResult parentResult)
            throws ObjectNotFoundException, SchemaException {
        return null;
        // TODO
    }

    // Add/modify/delete

    @Override
    @NotNull
    public <T extends ObjectType> String addObject(
            @NotNull PrismObject<T> object,
            @Nullable RepoAddOptions options,
            @NotNull OperationResult parentResult)
            throws ObjectAlreadyExistsException, SchemaException {

        Objects.requireNonNull(object, "Object must not be null.");
        Objects.requireNonNull(parentResult, "Operation result must not be null.");

        if (options == null) {
            options = new RepoAddOptions();
        }

        OperationResult operationResult = parentResult.subresult(OP_NAME_PREFIX + OP_ADD_OBJECT)
                .addQualifier(object.asObjectable().getClass().getSimpleName())
                .addParam("object", object)
                .addParam("options", options.toString())
                .build();

        try {
            PolyString name = object.getName();
            if (name == null || Strings.isNullOrEmpty(name.getOrig())) {
                throw new SchemaException("Attempt to add object without name.");
            }

            //noinspection ConstantConditions
            LOGGER.debug(
                    "Adding object type '{}', overwrite={}, allowUnencryptedValues={}, name={} - {}",
                    object.getCompileTimeClass().getSimpleName(), options.isOverwrite(),
                    options.isAllowUnencryptedValues(), name.getOrig(), name.getNorm());

            if (InternalsConfig.encryptionChecks && !RepoAddOptions.isAllowUnencryptedValues(options)) {
                CryptoUtil.checkEncrypted(object);
            }

            if (InternalsConfig.consistencyChecks) {
                object.checkConsistence(ConsistencyCheckScope.THOROUGH);
            } else {
                object.checkConsistence(ConsistencyCheckScope.MANDATORY_CHECKS_ONLY);
            }

            if (object.getVersion() == null) {
                object.setVersion("1");
            }

            /* old repo code missing in new repo:
            SqlPerformanceMonitorImpl pm = getPerformanceMonitor();
            long opHandle = pm.registerOperationStart(OP_ADD_OBJECT, object.getCompileTimeClass());
            int attempt = 1;
            int restarts = 0;
            boolean noFetchExtensionValueInsertionForbidden = false;
            String proposedOid = object.getOid();
            while (true) {
                try {
            */
            // TODO use executeAttempts

            String oid = new AddObjectOperation<>(object, options, operationResult)
                    .execute(transformerSupport);
            invokeConflictWatchers((w) -> w.afterAddObject(oid, object));
            return oid;
            /*
                } catch (RestartOperationRequestedException ex) {
                    // special case: we want to restart but we do not want to count these
                    LOGGER.trace("Restarting because of {}", ex.getMessage());
                    restarts++;
                    if (restarts > RESTART_LIMIT) {
                        throw new IllegalStateException("Too many operation restarts");
                    }
                } catch (RuntimeException ex) {
                    attempt = baseHelper.logOperationAttempt(proposedOid, "adding", attempt, ex, subResult);
//                    pm.registerOperationNewAttempt(opHandle, attempt);
                }
                noFetchExtensionValueInsertionForbidden = true; // todo This is a temporary measure; needs better handling.
            }
        } finally {
//            pm.registerOperationFinish(opHandle, attempt);
//            OperationLogger.logAdd(object, options, subResult); TODO logging
        }
        */
        } catch (Throwable t) {
            operationResult.recordFatalError(t);
            throw t;
        } finally {
            operationResult.computeStatusIfUnknown();
        }
    }

    @Override
    @NotNull
    public <T extends ObjectType> ModifyObjectResult<T> modifyObject(
            @NotNull Class<T> type,
            @NotNull String oid,
            @NotNull Collection<? extends ItemDelta<?, ?>> modifications,
            @NotNull OperationResult parentResult)
            throws ObjectNotFoundException, SchemaException, ObjectAlreadyExistsException {
        return modifyObject(type, oid, modifications, null, parentResult);
    }

    @Override
    @NotNull
    public <T extends ObjectType> ModifyObjectResult<T> modifyObject(
            @NotNull Class<T> type,
            @NotNull String oid,
            @NotNull Collection<? extends ItemDelta<?, ?>> modifications,
            @Nullable RepoModifyOptions options,
            @NotNull OperationResult parentResult)
            throws ObjectNotFoundException, SchemaException, ObjectAlreadyExistsException {
        try {
            return modifyObject(type, oid, modifications, null, options, parentResult);
        } catch (PreconditionViolationException e) {
            throw new AssertionError(e); // with null precondition we couldn't get this exception
        }
    }

    @Override
    @NotNull
    public <T extends ObjectType> ModifyObjectResult<T> modifyObject(
            @NotNull Class<T> type,
            @NotNull String oid,
            @NotNull Collection<? extends ItemDelta<?, ?>> modifications,
            @Nullable ModificationPrecondition<T> precondition,
            @Nullable RepoModifyOptions options,
            @NotNull OperationResult parentResult)
            throws ObjectNotFoundException, SchemaException, ObjectAlreadyExistsException, PreconditionViolationException {

        Objects.requireNonNull(modifications, "Modifications must not be null.");
        Objects.requireNonNull(type, "Object class in delta must not be null.");
        UUID oidUuid = checkOid(oid);
        Objects.requireNonNull(parentResult, "Operation result must not be null.");

        OperationResult operationResult = parentResult.subresult(OP_NAME_PREFIX + OP_MODIFY_OBJECT)
                .addQualifier(type.getSimpleName())
                .addParam("type", type.getName())
                .addParam("oid", oid)
                .addArbitraryObjectCollectionAsParam("modifications", modifications)
                .build();

        try {
            if (modifications.isEmpty() && !RepoModifyOptions.isForceReindex(options)) {
                LOGGER.debug("Modification list is empty, nothing was modified.");
                operationResult.recordStatus(OperationResultStatus.SUCCESS,
                        "Modification list is empty, nothing was modified.");
                return new ModifyObjectResult<>(modifications);
            }

            if (InternalsConfig.encryptionChecks) {
                CryptoUtil.checkEncrypted(modifications);
            }

            if (InternalsConfig.consistencyChecks) {
                ItemDeltaCollectionsUtil.checkConsistence(modifications, ConsistencyCheckScope.THOROUGH);
            } else {
                ItemDeltaCollectionsUtil.checkConsistence(modifications, ConsistencyCheckScope.MANDATORY_CHECKS_ONLY);
            }

            logTraceModifications(modifications);

            try (JdbcSession jdbcSession = sqlRepoContext.newJdbcSession().startTransaction()) {
                GetOperationOptionsBuilder getOptionsBuilder = schemaService.getOperationOptionsBuilder();
                T object = readByOidAndLock(jdbcSession, type, oidUuid, getOptionsBuilder.build());
                //noinspection unchecked
                PrismObject<T> prismObject = (PrismObject<T>) object.asPrismObject();
                if (precondition != null && !precondition.holds(prismObject)) {
                    throw new PreconditionViolationException("Modification precondition does not hold for " + prismObject);
                }
                invokeConflictWatchers(w -> w.beforeModifyObject(prismObject));

                PrismObject<T> originalObject = prismObject.clone();

                // TODO is modifications cloning unavoidable? see the clone at the start of ObjectUpdater.modifyObjectAttempt
                //  If cloning will be necessary, do it at the beginning of modifyObjectAttempt,
                //  especially if called potentially multiple times.
                // TODO replaces: RObject rObject = objectDeltaUpdater.modifyObject(type, oid, modifications, prismObject, modifyOptions, session, attemptContext);
                modifications = modifyObjectAttempt(jdbcSession, prismObject, modifications);
                LOGGER.trace("OBJECT after:\n{}", prismObject.debugDumpLazily());

                return new ModifyObjectResult<>(originalObject, prismObject, modifications);
            }
        } catch (RepositoryException | RuntimeException e) {
            throw handledGeneralException(e, operationResult);
        } catch (Throwable t) {
            operationResult.recordFatalError(t);
            throw t;
        } finally {
            operationResult.computeStatusIfUnknown();
        }
    }

    /**
     * Applies modifications, executes necessary updates and returns narrowed modifications.
     *
     * @param <S> schema type
     * @param <Q> type of entity path
     * @param <R> row type related to the {@link Q}
     */
    private <S extends ObjectType, Q extends QObject<R>, R extends MObject>
    Collection<? extends ItemDelta<?, ?>> modifyObjectAttempt(
            JdbcSession jdbcSession,
            PrismObject<S> prismObject,
            Collection<? extends ItemDelta<?, ?>> modifications)
            throws SchemaException, RepositoryException {

        // I reassign here, we DON'T want original modifications to be used further by accident
        modifications = prismObject.narrowModifications(
                modifications, EquivalenceStrategy.DATA,
                EquivalenceStrategy.REAL_VALUE_CONSIDER_DIFFERENT_IDS, true);
        LOGGER.trace("Narrowed modifications:\n{}", DebugUtil.debugDumpLazily(modifications));

        if (modifications.isEmpty()) {
            return modifications; // no need to execute any update
        }

        SqaleUpdateContext<S, Q, R> updateContext = new SqaleUpdateContext<>(
                transformerSupport, jdbcSession, prismObject);

        // TODO APPLY modifications HERE (generate update/set clauses)
        for (ItemDelta<?, ?> modification : modifications) {
            try {
                // TODO generate missing container IDs, see also old repo PrismIdentifierGenerator
                //  BWT: it's not enough to do it in prism object, we need it for deltas adding containers too
                modification.applyTo(prismObject);

                updateContext.processModification(modification);
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Modification failed/not implemented yet: {}", e.toString());
            }
        }

        ObjectTypeUtil.normalizeAllRelations(prismObject, schemaService.relationRegistry());
        updateContext.execute();
        return modifications;
    }

    private void logTraceModifications(@NotNull Collection<? extends ItemDelta<?, ?>> modifications) {
        if (LOGGER.isTraceEnabled()) {
            for (ItemDelta<?, ?> modification : modifications) {
                if (modification instanceof PropertyDelta<?>) {
                    PropertyDelta<?> propDelta = (PropertyDelta<?>) modification;
                    if (propDelta.getPath().equivalent(ObjectType.F_NAME)) {
                        Collection<PrismPropertyValue<PolyString>> values = propDelta.getValues(PolyString.class);
                        for (PrismPropertyValue<PolyString> pval : values) {
                            PolyString value = pval.getValue();
                            LOGGER.trace("NAME delta: {} - {}", value.getOrig(), value.getNorm());
                        }
                    }
                }
            }
        }
    }

    @Override
    public @NotNull <T extends ObjectType> DeleteObjectResult deleteObject(
            Class<T> type, String oid, OperationResult parentResult)
            throws ObjectNotFoundException {

        Validate.notNull(type, "Object type must not be null.");
        UUID oidUuid = checkOid(oid);
        Validate.notNull(parentResult, "Operation result must not be null.");

        LOGGER.debug("Deleting object type '{}' with oid '{}'", type.getSimpleName(), oid);

        OperationResult operationResult = parentResult.subresult(OP_NAME_PREFIX + OP_DELETE_OBJECT)
                .addQualifier(type.getSimpleName())
                .addParam("type", type.getName())
                .addParam("oid", oid)
                .build();
        try {
            try (JdbcSession jdbcSession = sqlRepoContext.newJdbcSession().startTransaction()) {
                DeleteObjectResult result = deleteObjectAttempt(type, oidUuid, jdbcSession);
                invokeConflictWatchers((w) -> w.afterDeleteObject(oid));
                return result;
            }
        } catch (RuntimeException e) {
            throw handledGeneralException(e, operationResult);
        } catch (Throwable t) {
            operationResult.recordFatalError(t);
            throw t;
        } finally {
            operationResult.computeStatusIfUnknown();
        }
    }

    private <T extends ObjectType, Q extends QObject<R>, R extends MObject>
    DeleteObjectResult deleteObjectAttempt(Class<T> type, UUID oid, JdbcSession jdbcSession)
            throws ObjectNotFoundException {

        QueryTableMapping<T, Q, R> mapping =
                transformerSupport.sqlRepoContext().getMappingBySchemaType(type);
        Q entityPath = mapping.defaultAlias();
        byte[] fullObject = jdbcSession.newQuery()
                .select(entityPath.fullObject)
                .forUpdate()
                .from(entityPath)
                .where(entityPath.oid.eq(oid))
                .fetchOne();
        if (fullObject == null) {
            throw new ObjectNotFoundException(type, oid.toString());
        }

        // object delete cascades to all owned related rows
        // TODO org closure
        jdbcSession.newDelete(entityPath)
                .where(entityPath.oid.eq(oid))
                .execute();

        return new DeleteObjectResult(new String(fullObject, StandardCharsets.UTF_8));
    }

    // Counting/searching

    @Override
    public <T extends ObjectType> int countObjects(Class<T> type, ObjectQuery query,
            Collection<SelectorOptions<GetOperationOptions>> options, OperationResult parentResult)
            throws SchemaException {
        Objects.requireNonNull(type, "Object type must not be null.");
        Objects.requireNonNull(parentResult, "Operation result must not be null.");

        OperationResult operationResult = parentResult.subresult(OP_NAME_PREFIX + OP_COUNT_OBJECTS)
                .addQualifier(type.getSimpleName())
                .addParam("type", type.getName())
                .addParam("query", query)
                .build();

        try {
            var queryContext = SqaleQueryContext.from(type, transformerSupport, sqlRepoContext);
            return sqlQueryExecutor.count(queryContext, query, options);
        } catch (RepositoryException | RuntimeException e) {
            throw handledGeneralException(e, operationResult);
        } catch (Throwable t) {
            operationResult.recordFatalError(t);
            throw t;
        } finally {
            operationResult.computeStatusIfUnknown();
        }
    }

    @Override
    public @NotNull <T extends ObjectType> SearchResultList<PrismObject<T>> searchObjects(
            @NotNull Class<T> type, ObjectQuery query,
            Collection<SelectorOptions<GetOperationOptions>> options,
            @NotNull OperationResult parentResult)
            throws SchemaException {
        Objects.requireNonNull(type, "Object type must not be null.");
        Objects.requireNonNull(parentResult, "Operation result must not be null.");

        OperationResult operationResult = parentResult.subresult(OP_NAME_PREFIX + OP_SEARCH_OBJECTS)
                .addQualifier(type.getSimpleName())
                .addParam("type", type.getName())
                .addParam("query", query)
                .build();

        try {
            var queryContext = SqaleQueryContext.from(type, transformerSupport, sqlRepoContext);
            SearchResultList<T> result =
                    sqlQueryExecutor.list(queryContext, query, options);
            // TODO see the commented code from old repo lower, problems for each object must be caught
            //noinspection unchecked
            return result.map(
                    o -> (PrismObject<T>) o.asPrismObject());
        } catch (RepositoryException | RuntimeException e) {
            throw handledGeneralException(e, operationResult);
        } catch (Throwable t) {
            operationResult.recordFatalError(t);
            throw t;
        } finally {
            operationResult.computeStatusIfUnknown();
        }
    }

    /*
    TODO from ObjectRetriever, how to do this per-object Throwable catch + record result?
     should we smuggle the OperationResult all the way to the transformer call?
    @NotNull
    private <T extends ObjectType> List<PrismObject<T>> queryResultToPrismObjects(
            List<T> objects, Class<T> type,
            Collection<SelectorOptions<GetOperationOptions>> options,
            OperationResult result) throws SchemaException {
        List<PrismObject<T>> rv = new ArrayList<>();
        if (objects == null) {
            return rv;
        }
        for (T object : objects) {
            String oid = object.getOid();
            Holder<PrismObject<T>> partialValueHolder = new Holder<>();
            PrismObject<T> prismObject;
            try {
                prismObject = createPrismObject(object, type, oid, options, partialValueHolder);
            } catch (Throwable t) {
                if (!partialValueHolder.isEmpty()) {
                    prismObject = partialValueHolder.getValue();
                } else {
                    prismObject = prismContext.createObject(type);
                    prismObject.setOid(oid);
                    prismObject.asObjectable().setName(PolyStringType.fromOrig("Unreadable object"));
                }
                result.recordFatalError("Couldn't retrieve " + type + " " + oid + ": " + t.getMessage(), t);
                prismObject.asObjectable().setFetchResult(result.createOperationResultType());
            }
            rv.add(prismObject);
        }
        return rv;
    }
    */

    @Override
    public <T extends ObjectType> SearchResultMetadata searchObjectsIterative(
            Class<T> type, ObjectQuery query, ResultHandler<T> handler,
            Collection<SelectorOptions<GetOperationOptions>> options, boolean strictlySequential,
            OperationResult parentResult) throws SchemaException {
        return null;
        // TODO
    }

    @Override
    public <T extends Containerable> int countContainers(Class<T> type, ObjectQuery query,
            Collection<SelectorOptions<GetOperationOptions>> options, OperationResult parentResult) {
        return 0;
    }

    @Override
    public <T extends Containerable> SearchResultList<T> searchContainers(
            Class<T> type, ObjectQuery query,
            Collection<SelectorOptions<GetOperationOptions>> options, OperationResult parentResult)
            throws SchemaException {

        Objects.requireNonNull(type, "Container type must not be null.");
        Objects.requireNonNull(parentResult, "Operation result must not be null.");

        OperationResult operationResult = parentResult.subresult(OP_NAME_PREFIX + OP_SEARCH_CONTAINERS)
                .addQualifier(type.getSimpleName())
                .addParam("type", type.getName())
                .addParam("query", query)
                .build();

        try {
            var queryContext = SqaleQueryContext.from(type, transformerSupport, sqlRepoContext);
            SearchResultList<T> result =
                    sqlQueryExecutor.list(queryContext, query, options);
            return result;
        } catch (RepositoryException | RuntimeException e) {
            throw handledGeneralException(e, operationResult);
        } catch (Throwable t) {
            operationResult.recordFatalError(t);
            throw t;
        } finally {
            operationResult.computeStatusIfUnknown();
        }
    }

    @Override
    public boolean isAnySubordinate(String upperOrgOid, Collection<String> lowerObjectOids)
            throws SchemaException {
        return false;
        // TODO
    }

    @Override
    public <O extends ObjectType> boolean isDescendant(PrismObject<O> object, String orgOid)
            throws SchemaException {
        return false;
        // TODO
    }

    @Override
    public <O extends ObjectType> boolean isAncestor(PrismObject<O> object, String oid)
            throws SchemaException {
        return false;
        // TODO
    }

    @Override
    public <F extends FocusType> PrismObject<F> searchShadowOwner(String shadowOid,
            Collection<SelectorOptions<GetOperationOptions>> options, OperationResult parentResult) {
        return null;
        // TODO
    }

    @Override
    public long advanceSequence(String oid, OperationResult parentResult)
            throws ObjectNotFoundException, SchemaException {
        return 0;
        // TODO
    }

    @Override
    public void returnUnusedValuesToSequence(
            String oid, Collection<Long> unusedValues, OperationResult parentResult)
            throws ObjectNotFoundException, SchemaException {

        // TODO
    }

    @Override
    public RepositoryDiag getRepositoryDiag() {
        return null;
        // TODO
    }

    @Override
    public void repositorySelfTest(OperationResult parentResult) {

        // TODO
    }

    @Override
    public void testOrgClosureConsistency(boolean repairIfNecessary, OperationResult testResult) {

        // TODO
    }

    @Override
    public RepositoryQueryDiagResponse executeQueryDiagnostics(
            RepositoryQueryDiagRequest request, OperationResult result) {

        // TODO search like containers + dry run?

        RepositoryQueryDiagResponse response = new RepositoryQueryDiagResponse(
                null, null, Map.of());
//                objects, implementationLevelQuery, implementationLevelQueryParameters);

        return response;
    }

    @Override
    public <O extends ObjectType> boolean selectorMatches(
            ObjectSelectorType objectSelector, PrismObject<O> object,
            ObjectFilterExpressionEvaluator filterEvaluator, Trace logger, String logMessagePrefix)
            throws SchemaException, ObjectNotFoundException, ExpressionEvaluationException,
            CommunicationException, ConfigurationException, SecurityViolationException {
        return false;
        // TODO
    }

    @Override
    public void applyFullTextSearchConfiguration(FullTextSearchConfigurationType fullTextSearch) {
        // TODO
    }

    @Override
    public FullTextSearchConfigurationType getFullTextSearchConfiguration() {
        return null;
        // TODO
    }

    @Override
    public void postInit(OperationResult result) throws SchemaException {
        // TODO
    }

    // TODO use internally in various operations (see old repo)
    private void invokeConflictWatchers(Consumer<ConflictWatcherImpl> consumer) {
        conflictWatchersThreadLocal.get().forEach(consumer);
    }

    @Override
    public ConflictWatcher createAndRegisterConflictWatcher(@NotNull String oid) {
        List<ConflictWatcherImpl> watchers = conflictWatchersThreadLocal.get();
        if (watchers.size() >= MAX_CONFLICT_WATCHERS) {
            throw new IllegalStateException("Conflicts watchers leaking: reached limit of "
                    + MAX_CONFLICT_WATCHERS + ": " + watchers);
        }
        ConflictWatcherImpl watcher = new ConflictWatcherImpl(oid);
        watchers.add(watcher);
        return watcher;
    }

    @Override
    public void unregisterConflictWatcher(ConflictWatcher watcher) {
        ConflictWatcherImpl watcherImpl = (ConflictWatcherImpl) watcher;
        List<ConflictWatcherImpl> watchers = conflictWatchersThreadLocal.get();
        // change these exceptions to logged errors, eventually
        if (watchers == null) {
            throw new IllegalStateException(
                    "No conflict watchers registered for current thread; tried to unregister " + watcher);
        } else if (!watchers.remove(watcherImpl)) { // expecting there's only one
            throw new IllegalStateException(
                    "Tried to unregister conflict watcher " + watcher + " that was not registered");
        }
    }

    @Override
    public boolean hasConflict(ConflictWatcher watcher, OperationResult parentResult) {
        OperationResult result = parentResult.subresult(OP_NAME_PREFIX + OP_HAS_CONFLICT)
                .setMinor()
                .addParam("oid", watcher.getOid())
                .addParam("watcherClass", watcher.getClass().getName())
                .build();

        try {
            boolean rv;
            if (watcher.hasConflict()) {
                rv = true;
            } else {
                try {
                    getVersion(ObjectType.class, watcher.getOid(), result);
                } catch (ObjectNotFoundException | SchemaException e) {
                    // just ignore this
                }
                rv = watcher.hasConflict();
            }
            result.addReturn("hasConflict", rv);
            return rv;
        } catch (Throwable t) {
            result.recordFatalError(t);
            throw t;
        } finally {
            result.computeStatusIfUnknown();
        }
    }

    @Override
    public <T extends ObjectType> void addDiagnosticInformation(Class<T> type, String oid,
            DiagnosticInformationType information, OperationResult parentResult)
            throws ObjectNotFoundException, SchemaException, ObjectAlreadyExistsException {

        // TODO
    }

    @Override
    public PerformanceMonitor getPerformanceMonitor() {
        return performanceMonitor;
    }

    @PreDestroy
    public void destroy() {
        if (performanceMonitor != null) {
            performanceMonitor.shutdown();
            sqlPerformanceMonitorsCollection.deregister(performanceMonitor);
            performanceMonitor = null;
        }
    }

    /**
     * Handles exception outside of transaction - this does not handle transactional problems.
     * Returns {@link SystemException}, call with `throw` keyword.
     */
    private SystemException handledGeneralException(@NotNull Throwable ex, OperationResult result) {
        // TODO reconsider this whole mechanism including isFatalException decision
        LOGGER.error("General checked exception occurred.", ex);
        recordException(ex, result,
                sqlRepoContext.getJdbcRepositoryConfiguration().isFatalException(ex));

        return ex instanceof SystemException
                ? (SystemException) ex
                : new SystemException(ex.getMessage(), ex);
    }

    private void recordException(@NotNull Throwable ex, OperationResult result, boolean fatal) {
        String message = Strings.isNullOrEmpty(ex.getMessage()) ? ex.getMessage() : "null";
        if (Strings.isNullOrEmpty(message)) {
            message = ex.getMessage();
        }

        // non-fatal errors will NOT be put into OperationResult, not to confuse the user
        if (result != null && fatal) {
            result.recordFatalError(message, ex);
        }
    }
}
