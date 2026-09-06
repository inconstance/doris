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
import org.apache.doris.plsql.exception.PlValidationException;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DbmsObfuscationToolkitTest {
    private final DbmsObfuscationToolkit toolkit = DbmsObfuscationToolkitClass.INSTANCE.newInstance();

    @Test
    public void testRawProcedureRoundTripAndOutAssignment() {
        byte[] plain = "12345678ABCDEFGH".getBytes(StandardCharsets.US_ASCII);
        byte[] key = "abcdef0123456789".getBytes(StandardCharsets.US_ASCII);
        MethodArgument encrypted = new MethodArgument("encrypted_data", "encrypted", Var.Null);
        DbmsObfuscationToolkitClass.INSTANCE.methodDictionary().invoke(null, toolkit, "des3encrypt", Arrays.asList(
                new MethodArgument("input", null, new Var(plain)),
                new MethodArgument("key", null, new Var(key)), encrypted));
        assertNotNull(encrypted.output());

        MethodArgument decrypted = new MethodArgument("decrypted_data", "decrypted", Var.Null);
        DbmsObfuscationToolkitClass.INSTANCE.methodDictionary().invoke(null, toolkit, "des3decrypt", Arrays.asList(
                new MethodArgument("input", null, encrypted.output()),
                new MethodArgument("key", null, new Var(key)), decrypted));
        assertArrayEquals(plain, (byte[]) decrypted.output().value);
    }

    @Test
    public void testRejectsUnalignedInputAndShortKey() {
        assertThrows(PlValidationException.class,
                () -> toolkit.encrypt(new byte[] {1}, new byte[16], 0, null));
        assertThrows(PlValidationException.class,
                () -> toolkit.encrypt(new byte[8], new byte[8], 0, null));
    }
}
