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

package org.apache.doris.nereids.trees.expressions;

import org.apache.doris.nereids.trees.expressions.functions.NoneMovableFunction;
import org.apache.doris.nereids.trees.expressions.shape.LeafExpression;
import org.apache.doris.nereids.trees.expressions.visitor.ExpressionVisitor;
import org.apache.doris.nereids.types.DataType;
import org.apache.doris.nereids.types.LargeIntType;

import java.util.Objects;

/** Catalog-bound stateful sequence pseudocolumn. */
public class SequenceValue extends Expression implements LeafExpression, NoneMovableFunction {
    private final long dbId;
    private final long sequenceId;
    private final long sequenceVersion;
    private final long cacheSize;
    private final String qualifiedName;
    private final boolean nextVal;

    /** Create a catalog-bound Sequence pseudocolumn. */
    public SequenceValue(long dbId, long sequenceId, long sequenceVersion, long cacheSize,
            String qualifiedName, boolean nextVal) {
        this.dbId = dbId;
        this.sequenceId = sequenceId;
        this.sequenceVersion = sequenceVersion;
        this.cacheSize = cacheSize;
        this.qualifiedName = Objects.requireNonNull(qualifiedName, "qualifiedName");
        this.nextVal = nextVal;
    }

    public long getDbId() {
        return dbId;
    }

    public long getSequenceId() {
        return sequenceId;
    }

    public long getSequenceVersion() {
        return sequenceVersion;
    }

    public long getCacheSize() {
        return cacheSize;
    }

    public boolean isNextVal() {
        return nextVal;
    }

    @Override
    public DataType getDataType() {
        return LargeIntType.INSTANCE;
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
        return qualifiedName + "." + (nextVal ? "NEXTVAL" : "CURRVAL");
    }

    @Override
    public int computeHashCode() {
        return Objects.hash(sequenceId, sequenceVersion, nextVal);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof SequenceValue)) {
            return false;
        }
        SequenceValue other = (SequenceValue) obj;
        return sequenceId == other.sequenceId && sequenceVersion == other.sequenceVersion
                && nextVal == other.nextVal;
    }

    @Override
    public <R, C> R accept(ExpressionVisitor<R, C> visitor, C context) {
        return visitor.visitSequenceValue(this, context);
    }
}
