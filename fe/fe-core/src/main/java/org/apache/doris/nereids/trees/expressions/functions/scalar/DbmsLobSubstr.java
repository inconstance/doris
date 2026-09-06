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

package org.apache.doris.nereids.trees.expressions.functions.scalar;

import org.apache.doris.catalog.FunctionSignature;
import org.apache.doris.nereids.trees.expressions.Expression;
import org.apache.doris.nereids.trees.expressions.functions.AlwaysNullable;
import org.apache.doris.nereids.trees.expressions.functions.ExplicitlyCastableSignature;
import org.apache.doris.nereids.trees.expressions.literal.IntegerLiteral;
import org.apache.doris.nereids.trees.expressions.visitor.ExpressionVisitor;
import org.apache.doris.nereids.types.IntegerType;
import org.apache.doris.nereids.types.VarBinaryType;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.util.List;

/** DBMS_LOB.SUBSTR for BLOB values. */
public class DbmsLobSubstr extends ScalarFunction implements ExplicitlyCastableSignature, AlwaysNullable {
    public static final List<FunctionSignature> SIGNATURES = ImmutableList.of(
            FunctionSignature.ret(VarBinaryType.INSTANCE).args(VarBinaryType.INSTANCE),
            FunctionSignature.ret(VarBinaryType.INSTANCE)
                    .args(VarBinaryType.INSTANCE, IntegerType.INSTANCE),
            FunctionSignature.ret(VarBinaryType.INSTANCE)
                    .args(VarBinaryType.INSTANCE, IntegerType.INSTANCE, IntegerType.INSTANCE));

    public DbmsLobSubstr(Expression value) {
        this(value, new IntegerLiteral(32767), new IntegerLiteral(1));
    }

    public DbmsLobSubstr(Expression value, Expression amount) {
        this(value, amount, new IntegerLiteral(1));
    }

    public DbmsLobSubstr(Expression value, Expression amount, Expression offset) {
        super("dbms_lob_substr", value, amount, offset);
    }

    private DbmsLobSubstr(ScalarFunctionParams functionParams) {
        super(functionParams);
    }

    @Override
    public DbmsLobSubstr withChildren(List<Expression> children) {
        Preconditions.checkArgument(children.size() == 3);
        return new DbmsLobSubstr(getFunctionParams(children));
    }

    @Override
    public List<FunctionSignature> getSignatures() {
        return SIGNATURES;
    }

    @Override
    public <R, C> R accept(ExpressionVisitor<R, C> visitor, C context) {
        return visitor.visitScalarFunction(this, context);
    }
}
