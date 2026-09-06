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
import org.apache.doris.plsql.exception.PlValidationException;

/** An evaluated object method argument and the PL variable used as its optional writable target. */
public class MethodArgument {
    private final String name;
    private final String targetName;
    private final Var value;
    private Var output;

    public MethodArgument(String name, String targetName, Var value) {
        this.name = name;
        this.targetName = targetName;
        this.value = value;
    }

    public String name() {
        return name;
    }

    public String targetName() {
        return targetName;
    }

    public Var value() {
        return value;
    }

    public boolean isWritable() {
        return targetName != null;
    }

    public void setOutput(Var output) {
        if (!isWritable()) {
            throw new PlValidationException(null, "OUT argument must be a writable variable");
        }
        this.output = output;
    }

    public Var output() {
        return output;
    }
}
