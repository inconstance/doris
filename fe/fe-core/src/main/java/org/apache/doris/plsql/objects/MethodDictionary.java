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
// This file is copied from
// https://github.com/apache/hive/blob/master/hplsql/src/main/java/org/apache/hive/hplsql/objects/MethodDictionary.java
// and modified by Doris

package org.apache.doris.plsql.objects;

import org.apache.doris.plsql.Var;
import org.apache.doris.plsql.exception.NoSuchPlMethodException;

import org.antlr.v4.runtime.ParserRuleContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MethodDictionary<T extends PlObject> {
    public static final String __GETITEM__ = "__GETITEM__";
    public static final String __SETITEM__ = "__SETITEM__";
    private final Map<String, ParameterizedMethod<T>> dict = new HashMap<>();

    public void put(String methodName, Method<T> method) {
        dict.put(methodName.toUpperCase(), (self, arguments) -> {
            List<Var> values = arguments.stream().map(MethodArgument::value).collect(Collectors.toList());
            return method.call(self, values);
        });
    }

    public void putParameterized(String methodName, ParameterizedMethod<T> method) {
        dict.put(methodName.toUpperCase(), method);
    }

    public Method<T> get(ParserRuleContext ctx, String methodName) {
        ParameterizedMethod<T> method = find(ctx, methodName);
        return (self, values) -> method.call(self, values.stream()
                .map(value -> new MethodArgument(null, null, value)).collect(Collectors.toList()));
    }

    public Var invoke(ParserRuleContext ctx, T self, String methodName, List<MethodArgument> arguments) {
        return find(ctx, methodName).call(self, arguments);
    }

    private ParameterizedMethod<T> find(ParserRuleContext ctx, String methodName) {
        ParameterizedMethod<T> method = dict.get(methodName.toUpperCase());
        if (method == null) {
            throw new NoSuchPlMethodException(ctx, "No such method " + methodName);
        }
        return method;
    }
}
