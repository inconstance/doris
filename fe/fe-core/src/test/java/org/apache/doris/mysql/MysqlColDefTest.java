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

package org.apache.doris.mysql;

import org.apache.doris.catalog.PrimitiveType;
import org.apache.doris.catalog.ScalarType;
import org.apache.doris.catalog.Type;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;

public class MysqlColDefTest {
    // TODO(dhc): comment to pass ut coverage
    @Test
    public void testCreate() {
        //        Expr expr = EasyMock.createMock(Expr.class);
        //        EasyMock.expect(expr.getType()).andReturn(ScalarType.createType(PrimitiveType.BIGINT)).anyTimes();
        //        EasyMock.replay(expr);
        //
        //        MysqlColDef def = MysqlColDef.fromExpr(expr, "col1");
        //        MysqlSerializer serializer = MysqlSerializer.newInstance();
        //        def.writeTo(serializer);
        //        ByteBuffer buf = serializer.toByteBuffer();
        //        Assert.assertEquals("def", new String(MysqlProto.readLenEncodedString(buf)));
        //        Assert.assertEquals("", new String(MysqlProto.readLenEncodedString(buf)));
        //        Assert.assertEquals("", new String(MysqlProto.readLenEncodedString(buf)));
        //        Assert.assertEquals("", new String(MysqlProto.readLenEncodedString(buf)));
        //        Assert.assertEquals("col1", new String(MysqlProto.readLenEncodedString(buf)));
        //        Assert.assertEquals("col1", new String(MysqlProto.readLenEncodedString(buf)));
        //        Assert.assertEquals(0x0c, MysqlProto.readVInt(buf));
        //        Assert.assertEquals(33, MysqlProto.readInt2(buf));
        //        Assert.assertEquals(255, MysqlProto.readInt4(buf));
        //        Assert.assertEquals(8, MysqlProto.readInt1(buf));
        //        Assert.assertEquals(0, MysqlProto.readInt2(buf));
        //        Assert.assertEquals(0, MysqlProto.readInt1(buf));
        //        Assert.assertEquals(0, MysqlProto.readInt2(buf));
        //        Assert.assertTrue(buf.remaining() == 0);
    }

    @Test
    public void testCreateFromName() {
        //        Expr expr = EasyMock.createMock(Expr.class);
        //        EasyMock.expect(expr.getType()).andReturn(ScalarType.createType(PrimitiveType.BIGINT)).anyTimes();
        //        EasyMock.replay(expr);
        //
        //        MysqlColDef def = MysqlColDef.fromName("col1");
        //        MysqlSerializer serializer = MysqlSerializer.newInstance();
        //        def.writeTo(serializer);
        //        ByteBuffer buf = serializer.toByteBuffer();
        //        Assert.assertEquals("def", new String(MysqlProto.readLenEncodedString(buf)));
        //        Assert.assertEquals("", new String(MysqlProto.readLenEncodedString(buf)));
        //        Assert.assertEquals("", new String(MysqlProto.readLenEncodedString(buf)));
        //        Assert.assertEquals("", new String(MysqlProto.readLenEncodedString(buf)));
        //        Assert.assertEquals("col1", new String(MysqlProto.readLenEncodedString(buf)));
        //        Assert.assertEquals("col1", new String(MysqlProto.readLenEncodedString(buf)));
        //        Assert.assertEquals(0x0c, MysqlProto.readVInt(buf));
        //        Assert.assertEquals(33, MysqlProto.readInt2(buf));
        //        Assert.assertEquals(255, MysqlProto.readInt4(buf));
        //        Assert.assertEquals(254, MysqlProto.readInt1(buf));
        //        Assert.assertEquals(0, MysqlProto.readInt2(buf));
        //        Assert.assertEquals(0, MysqlProto.readInt1(buf));
        //        Assert.assertEquals(0, MysqlProto.readInt2(buf));
        //        Assert.assertTrue(buf.remaining() == 0);
    }

    @Test
    public void testUnsignedResultMetadata() {
        MysqlSerializer serializer = MysqlSerializer.newInstance();
        serializer.writeField("u8", Type.UNSIGNED_TINYINT);
        ByteBuffer buf = serializer.toByteBuffer();
        for (int i = 0; i < 6; i++) {
            MysqlProto.readLenEncodedString(buf);
        }
        Assert.assertEquals(0x0c, MysqlProto.readVInt(buf));
        MysqlProto.readInt2(buf);
        Assert.assertEquals(3, MysqlProto.readInt4(buf));
        Assert.assertEquals(PrimitiveType.TINYINT.toMysqlType().getCode(), MysqlProto.readInt1(buf));
        Assert.assertEquals(32, MysqlProto.readInt2(buf));
    }

    @Test
    public void testUnsignedSumDecimalResultMetadata() {
        MysqlSerializer serializer = MysqlSerializer.newInstance();
        serializer.writeField("sum_u64", ScalarType.createDecimalV3Type(38, 0));
        ByteBuffer buf = serializer.toByteBuffer();
        for (int i = 0; i < 6; i++) {
            MysqlProto.readLenEncodedString(buf);
        }
        Assert.assertEquals(0x0c, MysqlProto.readVInt(buf));
        MysqlProto.readInt2(buf);
        MysqlProto.readInt4(buf);
        Assert.assertEquals(PrimitiveType.DECIMAL128.toMysqlType().getCode(), MysqlProto.readInt1(buf));
    }
}
