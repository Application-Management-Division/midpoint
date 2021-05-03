/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqlbase;

import javax.xml.namespace.QName;

import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.schema.SchemaService;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.util.exception.SchemaException;

/**
 * Holds various component dependencies that are for query and update contexts and in mappers.
 * Components can be obtained to execute calls on them, but preferably the needed logic
 * can be implemented here (better abstraction).
 *
 * TODO: Unify/merge with SqlRepoContext and let it provide all the "services".
 */
public class SqlSupportService {

    private static SqlSupportService instance;

    protected final SchemaService schemaService;
    protected final SqlRepoContext sqlRepoContext;

    public SqlSupportService(SchemaService schemaService, SqlRepoContext sqlRepoContext) {
        this.schemaService = schemaService;
        this.sqlRepoContext = sqlRepoContext;
        instance = this;
    }

    public static SqlSupportService getInstance() {
        return instance;
    }

    public <T> Class<? extends T> qNameToSchemaClass(QName qName) {
        return schemaService.typeQNameToSchemaClass(qName);
    }

    public QName schemaClassToQName(Class<?> schemaClass) {
        return schemaService.schemaClassToTypeQName(schemaClass);
    }

    public @NotNull QName normalizeRelation(QName qName) {
        return schemaService.normalizeRelation(qName);
    }

    @NotNull
    public PrismSerializer<String> createStringSerializer() {
        return schemaService.createStringSerializer(
                sqlRepoContext.getJdbcRepositoryConfiguration().getFullObjectFormat());
    }

    public <T extends Objectable> ParseResult<T> parsePrismObject(String serializedForm)
            throws SchemaException {
        PrismContext prismContext = schemaService.prismContext();
        // "Postel mode": be tolerant what you read. We need this to tolerate (custom) schema changes
        ParsingContext parsingContext = prismContext.createParsingContextForCompatibilityMode();
        PrismObject<T> prismObject = prismContext.parserFor(serializedForm)
                .context(parsingContext).parse();
        return new ParseResult<>(parsingContext, prismObject);
    }

    @NotNull
    public PrismParserNoIO createStringParser(String serializedResult) {
        return schemaService.parserFor(serializedResult);
    }

    /**
     * Sometimes delegation is not enough - we need Prism context for schema type construction
     * with definitions (parameter to constructor).
     */
    public PrismContext prismContext() {
        return schemaService.prismContext();
    }

    public SqlRepoContext sqlRepoContext() {
        return sqlRepoContext;
    }

    public void normalizeAllRelations(PrismObject<?> prismObject) {
        ObjectTypeUtil.normalizeAllRelations(prismObject, schemaService.relationRegistry());
    }

    public static class ParseResult<T extends Objectable> {
        public final ParsingContext parsingContext;
        public final PrismObject<T> prismObject;

        public ParseResult(ParsingContext parsingContext, PrismObject<T> prismObject) {
            this.parsingContext = parsingContext;
            this.prismObject = prismObject;
        }
    }
}
