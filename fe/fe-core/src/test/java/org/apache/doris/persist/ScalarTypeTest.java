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

package org.apache.doris.persist;

import org.apache.doris.catalog.MysqlColType;
import org.apache.doris.catalog.PrimitiveType;
import org.apache.doris.catalog.ScalarType;
import org.apache.doris.catalog.Type;
import org.apache.doris.catalog.VariantType;
import org.apache.doris.persist.gson.GsonUtils;

import org.junit.Assert;
import org.junit.Test;

public class ScalarTypeTest {
    @Test
    public void testScalarType() {
        ScalarType scalarType = new ScalarType(PrimitiveType.VARIANT);
        String json = GsonUtils.GSON.toJson(scalarType);
        System.out.println(json);
        ScalarType scalarType2 = GsonUtils.GSON.fromJson(json, ScalarType.class);
        Assert.assertFalse(scalarType2 instanceof VariantType);
        Assert.assertEquals(scalarType.getPrimitiveType(), scalarType2.getPrimitiveType());
        Assert.assertEquals(scalarType.getVariantMaxSubcolumnsCount(), 0);
        Assert.assertEquals(scalarType.getVariantEnableTypedPathsToSparse(), false);
        Assert.assertEquals(scalarType.getVariantMaxSparseColumnStatisticsSize(), 0);
    }

    @Test
    public void testUnsignedIntegerRoundTrip() {
        ScalarType unsignedInt = Type.UNSIGNED_INT;
        Assert.assertEquals(PrimitiveType.BIGINT, unsignedInt.getPrimitiveType());
        Assert.assertEquals(PrimitiveType.INT, unsignedInt.getUnsignedOriginType());
        Assert.assertEquals("int unsigned", unsignedInt.toSql());
        Assert.assertEquals(Type.TYPE_DESCRIPTOR_UNSIGNED_MASK, unsignedInt.getTypeDescriptor());

        String json = GsonUtils.GSON.toJson(unsignedInt);
        ScalarType restored = GsonUtils.GSON.fromJson(json, ScalarType.class);
        Assert.assertEquals(unsignedInt, restored);
        Assert.assertEquals("int unsigned", restored.toSql());
        Assert.assertEquals(unsignedInt, Type.fromThrift(unsignedInt.toThrift()));
    }

    @Test
    public void testTypeDescriptorIsOrthogonalToTypeIdentity() {
        ScalarType writeBigint = Type.INT.withTypeDescriptor(
                MysqlColType.MYSQL_TYPE_LONGLONG.getCode());
        ScalarType unsignedWriteBigint = Type.INT.withTypeDescriptor(
                Type.TYPE_DESCRIPTOR_UNSIGNED_MASK | MysqlColType.MYSQL_TYPE_LONGLONG.getCode());

        Assert.assertEquals(Type.INT, writeBigint);
        Assert.assertEquals(Type.INT.hashCode(), writeBigint.hashCode());
        Assert.assertTrue(Type.INT.matchesType(writeBigint));
        Assert.assertEquals(MysqlColType.MYSQL_TYPE_LONGLONG.getCode(), writeBigint.getWriteTypeCode());
        Assert.assertTrue(unsignedWriteBigint.isUnsignedInteger());
        Assert.assertEquals(MysqlColType.MYSQL_TYPE_LONGLONG.getCode(),
                unsignedWriteBigint.getWriteTypeCode());
    }
}
