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

package org.apache.doris.nereids.types.coercion;

import org.apache.doris.catalog.Type;
import org.apache.doris.nereids.types.BigIntType;
import org.apache.doris.nereids.types.DataType;
import org.apache.doris.nereids.types.DecimalV3Type;
import org.apache.doris.nereids.types.IntegerType;
import org.apache.doris.nereids.types.LargeIntType;
import org.apache.doris.nereids.types.SmallIntType;

import org.apache.commons.lang3.NotImplementedException;

/**
 * Abstract class for all integral data type in Nereids.
 */
public class IntegralType extends NumericType {

    public static final IntegralType INSTANCE = new IntegralType();

    private final long typeDescriptor;

    protected IntegralType() {
        this(Type.TYPE_DESCRIPTOR_DEFAULT);
    }

    protected IntegralType(long typeDescriptor) {
        if ((typeDescriptor & ~Type.TYPE_DESCRIPTOR_SUPPORTED_MASK) != 0) {
            throw new IllegalArgumentException("Unsupported type descriptor: " + typeDescriptor);
        }
        this.typeDescriptor = typeDescriptor;
    }

    public long getTypeDescriptor() {
        return typeDescriptor;
    }

    public boolean isUnsigned() {
        return typeDescriptor == Type.TYPE_DESCRIPTOR_UNSIGNED_MASK;
    }

    public IntegralType withTypeDescriptor(long typeDescriptor) {
        throw new NotImplementedException("should be implemented by derived class");
    }

    /** Number of decimal digits required by the logical unsigned range. */
    public int unsignedDecimalDigits() {
        if (this instanceof SmallIntType) {
            return 3;
        } else if (this instanceof IntegerType) {
            return 5;
        } else if (this instanceof BigIntType) {
            return 10;
        } else if (this instanceof LargeIntType) {
            return 20;
        }
        throw new IllegalStateException("Unsupported unsigned integral type: " + this);
    }

    @Override
    public DataType defaultConcreteType() {
        return BigIntType.INSTANCE;
    }

    @Override
    public boolean acceptsType(DataType other) {
        return other instanceof IntegralType;
    }

    @Override
    public String simpleString() {
        return "integral";
    }

    @Override
    public boolean isInjectiveCastTo(DataType target) {
        if (target instanceof IntegralType) {
            return this.equals(target) || ((IntegralType) target).widerThan(this);
        }
        if (target instanceof DecimalV3Type && !(this instanceof LargeIntType)) {
            DecimalV3Type other = (DecimalV3Type) target;
            DecimalV3Type self = DecimalV3Type.forType(this);
            return other.getRange() >= self.getRange();
        }
        return target instanceof CharacterType;
    }

    public boolean widerThan(IntegralType other) {
        return this.width() > other.width();
    }

    // The maximum number of digits that Integer can represent.
    public int range() {
        throw new NotImplementedException("should be implemented by derived class");
    }
}
