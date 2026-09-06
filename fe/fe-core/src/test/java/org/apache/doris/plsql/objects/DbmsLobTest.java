// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.apache.doris.plsql.objects;

import org.apache.doris.plsql.Var;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DbmsLobTest {
    private final DbmsLob dbmsLob = DbmsLobClass.INSTANCE.newInstance();

    @Test
    public void testGetLengthCountsBlobBytes() {
        assertEquals(5L, call("getlength", Collections.singletonList(
                new Var(new byte[] {1, 2, 3, 4, 5}))).longValue());
    }

    @Test
    public void testSubstrUsesOneBasedOffsetAndStopsAtEnd() {
        Var source = new Var(new byte[] {1, 2, 3, 4, 5});
        assertArrayEquals(new byte[] {2, 3, 4}, (byte[]) call("substr",
                Arrays.asList(source, new Var(3L), new Var(2L))).value);
        assertArrayEquals(new byte[] {5}, (byte[]) call("substr",
                Arrays.asList(source, new Var(10L), new Var(5L))).value);
    }

    @Test
    public void testSubstrInvalidArgumentsAndNullReturnNull() {
        Var source = new Var(new byte[] {1, 2, 3});
        assertTrue(call("substr", Arrays.asList(source, new Var(0L), new Var(1L))).isNull());
        assertTrue(call("substr", Arrays.asList(source, new Var(1L), new Var(0L))).isNull());
        assertTrue(call("getlength", Collections.singletonList(Var.Null)).isNull());
    }

    private Var call(String method, List<Var> arguments) {
        return DbmsLobClass.INSTANCE.methodDictionary().get(null, method).call(dbmsLob, arguments);
    }
}
