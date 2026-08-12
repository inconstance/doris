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

package org.apache.doris.nereids.analyzer;

import org.apache.doris.nereids.trees.expressions.Expression;
import org.apache.doris.nereids.trees.expressions.shape.LeafExpression;
import org.apache.doris.nereids.trees.expressions.visitor.ExpressionVisitor;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** An Oracle-style sequence pseudocolumn before catalog binding. */
public class UnboundSequenceValue extends Expression implements LeafExpression, Unbound {
    private final List<String> nameParts;
    private final boolean nextVal;

    public UnboundSequenceValue(List<String> nameParts, boolean nextVal) {
        this.nameParts = ImmutableList.copyOf(Objects.requireNonNull(nameParts, "nameParts"));
        this.nextVal = nextVal;
    }

    public List<String> getNameParts() {
        return nameParts;
    }

    public boolean isNextVal() {
        return nextVal;
    }

    @Override
    public boolean nullable() {
        return false;
    }

    @Override
    public boolean isConstant() {
        return false;
    }

    @Override
    public boolean foldable() {
        return false;
    }

    @Override
    public String computeToSql() {
        return String.join(".", nameParts) + "." + (nextVal ? "NEXTVAL" : "CURRVAL");
    }

    @Override
    public String toString() {
        return computeToSql().toLowerCase(Locale.ROOT);
    }

    @Override
    public int computeHashCode() {
        return Objects.hash(nameParts, nextVal);
    }

    @Override
    public <R, C> R accept(ExpressionVisitor<R, C> visitor, C context) {
        return visitor.visitUnboundSequenceValue(this, context);
    }
}
