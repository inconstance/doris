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

import java.math.BigDecimal;
import java.util.Random;

/** Oracle-compatible subset of the DBMS_RANDOM package. */
public class DbmsRandom implements PlObject {
    private static final char[] UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final char[] LOWERCASE = "abcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final char[] MIXED_CASE =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final char[] ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final char[] PRINTABLE = createPrintableCharacters();

    private final PlClass plClass;
    private final Random random;

    public DbmsRandom(PlClass plClass) {
        this(plClass, new Random());
    }

    DbmsRandom(PlClass plClass, Random random) {
        this.plClass = plClass;
        this.random = random;
    }

    @Override
    public PlClass plClass() {
        return plClass;
    }

    /** Return a random decimal in [0, 1). */
    public Var value() {
        return new Var(BigDecimal.valueOf(random.nextDouble()));
    }

    /** Return a random decimal in [low, high). */
    public Var value(BigDecimal low, BigDecimal high) {
        BigDecimal fraction = BigDecimal.valueOf(random.nextDouble());
        return new Var(low.add(high.subtract(low).multiply(fraction)));
    }

    /** Return an integer in [-2^31, 2^31). */
    public Var random() {
        return new Var((long) random.nextInt());
    }

    /** Return a random string using Oracle's DBMS_RANDOM.STRING option characters. */
    public Var string(String option, int length) {
        char[] characters = charactersFor(option);
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            result.append(characters[random.nextInt(characters.length)]);
        }
        return new Var(result.toString());
    }

    private char[] charactersFor(String option) {
        char selector = option.isEmpty() ? 'u' : Character.toLowerCase(option.charAt(0));
        switch (selector) {
            case 'l':
                return LOWERCASE;
            case 'a':
                return MIXED_CASE;
            case 'x':
                return ALPHANUMERIC;
            case 'p':
                return PRINTABLE;
            case 'u':
            default:
                return UPPERCASE;
        }
    }

    private static char[] createPrintableCharacters() {
        char[] characters = new char[95];
        for (int i = 0; i < characters.length; i++) {
            characters[i] = (char) (' ' + i);
        }
        return characters;
    }
}
