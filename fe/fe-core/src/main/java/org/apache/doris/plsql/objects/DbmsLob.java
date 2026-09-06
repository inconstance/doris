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

import java.util.Arrays;

/** Oracle-compatible read-only subset of DBMS_LOB for BLOB values. */
public class DbmsLob implements PlObject {
    private final PlClass plClass;

    public DbmsLob(PlClass plClass) {
        this.plClass = plClass;
    }

    @Override
    public PlClass plClass() {
        return plClass;
    }

    public Var getLength(byte[] value) {
        return new Var((long) value.length);
    }

    public Var substr(byte[] value, int amount, int offset) {
        int start = offset - 1;
        if (start >= value.length) {
            return new Var(new byte[0]);
        }
        int length = Math.min(amount, value.length - start);
        return new Var(Arrays.copyOfRange(value, start, start + length));
    }
}
