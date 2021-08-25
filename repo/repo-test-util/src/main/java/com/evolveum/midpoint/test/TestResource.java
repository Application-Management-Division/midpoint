/*
 * Copyright (c) 2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.test;

import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.util.annotation.Experimental;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;

import java.io.File;
import java.io.IOException;

/**
 * Representation of any prism object in tests.
 */
@Experimental
public class TestResource<T extends ObjectType> {

    public final File file;
    public final String oid;
    public PrismObject<T> object;

    public TestResource(File dir, String fileName, String oid) {
        this.file = new File(dir, fileName);
        this.oid = oid;
    }

    public String getNameOrig() {
        return object.getName().getOrig();
    }

    public T getObjectable() {
        return object.asObjectable();
    }

    public Class<T> getObjectClass() {
        return object.getCompileTimeClass();
    }

    @Override
    public String toString() {
        return object != null ? object.toString() : file + " (" + oid + ")";
    }

    public ObjectReferenceType ref() {
        return ObjectTypeUtil.createObjectRef(object, SchemaConstants.ORG_DEFAULT);
    }

    public void read() throws SchemaException, IOException {
        object = PrismContext.get().parserFor(file).parse();
    }

    public Class<T> getType() throws SchemaException, IOException {
        if (object == null) {
            read();
        }
        //noinspection unchecked
        return (Class<T>) object.asObjectable().getClass();
    }
}
