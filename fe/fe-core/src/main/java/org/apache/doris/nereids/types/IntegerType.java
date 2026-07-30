// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.doris.nereids.types;

import org.apache.doris.catalog.Type;
import org.apache.doris.nereids.types.coercion.Int32OrLessType;
import org.apache.doris.nereids.types.coercion.IntegralType;

/**
 * Integer data type in Nereids.
 */
public class IntegerType extends IntegralType implements Int32OrLessType {
    public static final IntegerType INSTANCE = new IntegerType(Type.TYPE_DESCRIPTOR_DEFAULT);
    public static final int RANGE = 10; // The maximum number of digits that Integer can represent.

    private static final IntegerType UNSIGNED_INSTANCE = new IntegerType(Type.TYPE_DESCRIPTOR_UNSIGNED_MASK);
    private static final int WIDTH = 4;

    private IntegerType(long typeDescriptor) {
        super(typeDescriptor);
    }

    @Override
    public Type toCatalogDataType() {
        return isUnsigned() ? Type.UNSIGNED_SMALLINT : Type.INT;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof IntegerType;
    }

    @Override
    public String simpleString() {
        return isUnsigned() ? "smallint unsigned" : "int";
    }

    @Override
    public boolean acceptsType(DataType other) {
        return equals(other);
    }

    @Override
    public DataType defaultConcreteType() {
        return this;
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int range() {
        return RANGE;
    }

    @Override
    public IntegralType withTypeDescriptor(long typeDescriptor) {
        if (typeDescriptor == Type.TYPE_DESCRIPTOR_DEFAULT) {
            return INSTANCE;
        } else if (typeDescriptor == Type.TYPE_DESCRIPTOR_UNSIGNED_MASK) {
            return UNSIGNED_INSTANCE;
        }
        throw new IllegalArgumentException("Unsupported INT descriptor: " + typeDescriptor);
    }
}
