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

package org.apache.doris.plsql.objects;

import org.apache.doris.plsql.Var;
import org.apache.doris.plsql.exception.ArityException;
import org.apache.doris.plsql.exception.PlValidationException;
import org.apache.doris.plsql.exception.TypeException;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DbmsRandomTest {
    @Test
    public void testValueWithoutBounds() {
        DbmsRandom dbmsRandom = new DbmsRandom(DbmsRandomClass.INSTANCE, new Random(7));
        BigDecimal value = call(dbmsRandom, "value", Collections.emptyList()).decimalValue();
        assertTrue(value.compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(value.compareTo(BigDecimal.ONE) < 0);
    }

    @Test
    public void testValueWithBounds() {
        DbmsRandom dbmsRandom = new DbmsRandom(DbmsRandomClass.INSTANCE, new Random(7));
        BigDecimal value = call(dbmsRandom, "value", Arrays.asList(new Var(new BigDecimal("10.25")),
                new Var(new BigDecimal("20.75")))).decimalValue();
        assertTrue(value.compareTo(new BigDecimal("10.25")) >= 0);
        assertTrue(value.compareTo(new BigDecimal("20.75")) < 0);
    }

    @Test
    public void testValuePropagatesNull() {
        DbmsRandom dbmsRandom = new DbmsRandom(DbmsRandomClass.INSTANCE, new Random(7));
        Var value = call(dbmsRandom, "value", Arrays.asList(Var.Null, new Var(2L)));
        assertTrue(value.isNull());
        assertEquals(Var.Type.DECIMAL, value.type);
    }

    @Test
    public void testValueRejectsUnsupportedArity() {
        DbmsRandom dbmsRandom = new DbmsRandom(DbmsRandomClass.INSTANCE, new Random(7));
        assertThrows(ArityException.class,
                () -> call(dbmsRandom, "value", Collections.singletonList(new Var(1L))));
    }

    @Test
    public void testValueRejectsNonNumericBounds() {
        DbmsRandom dbmsRandom = new DbmsRandom(DbmsRandomClass.INSTANCE, new Random(7));
        assertThrows(TypeException.class,
                () -> call(dbmsRandom, "value", Arrays.asList(new Var(true), new Var(2L))));
    }

    @Test
    public void testValueRejectsInvalidRange() {
        DbmsRandom dbmsRandom = new DbmsRandom(DbmsRandomClass.INSTANCE, new Random(7));
        assertThrows(PlValidationException.class,
                () -> call(dbmsRandom, "value", Arrays.asList(new Var(2L), new Var(2L))));
    }

    @Test
    public void testRandomReturnsOracleIntegerRange() {
        DbmsRandom dbmsRandom = new DbmsRandom(DbmsRandomClass.INSTANCE, new Random(7));
        long value = call(dbmsRandom, "random", Collections.emptyList()).longValue();
        assertTrue(value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE);
    }

    @Test
    public void testStringOptions() {
        DbmsRandom dbmsRandom = new DbmsRandom(DbmsRandomClass.INSTANCE, new Random(7));
        assertTrue(call(dbmsRandom, "string", Arrays.asList(new Var("u"), new Var(32L)))
                .toString().matches("[A-Z]{32}"));
        assertTrue(call(dbmsRandom, "string", Arrays.asList(new Var("l"), new Var(32L)))
                .toString().matches("[a-z]{32}"));
        assertTrue(call(dbmsRandom, "string", Arrays.asList(new Var("x"), new Var(32L)))
                .toString().matches("[A-Z0-9]{32}"));
    }

    @Test
    public void testStringValidatesArguments() {
        DbmsRandom dbmsRandom = new DbmsRandom(DbmsRandomClass.INSTANCE, new Random(7));
        assertThrows(ArityException.class,
                () -> call(dbmsRandom, "string", Collections.singletonList(new Var("u"))));
        assertThrows(PlValidationException.class,
                () -> call(dbmsRandom, "string", Arrays.asList(new Var("u"), new Var(-1L))));
    }

    private Var call(DbmsRandom dbmsRandom, String method, List<Var> arguments) {
        return DbmsRandomClass.INSTANCE.methodDictionary().get(null, method).call(dbmsRandom, arguments);
    }
}
