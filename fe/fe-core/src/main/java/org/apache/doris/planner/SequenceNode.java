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

package org.apache.doris.planner;

import org.apache.doris.analysis.TupleId;
import org.apache.doris.statistics.StatisticalType;
import org.apache.doris.thrift.TExplainLevel;
import org.apache.doris.thrift.TPlanNode;
import org.apache.doris.thrift.TPlanNodeType;
import org.apache.doris.thrift.TSequenceNode;
import org.apache.doris.thrift.TSequenceSpec;

import com.google.common.collect.ImmutableList;

import java.util.List;

/** Legacy planner bridge for the vectorized Sequence pipeline operator. */
public class SequenceNode extends PlanNode {
    private final List<TSequenceSpec> sequences;

    public SequenceNode(PlanNodeId id, PlanNode input, TupleId sequenceTupleId, List<TSequenceSpec> sequences) {
        super(id, "SEQUENCE", StatisticalType.DEFAULT);
        children.add(input);
        tupleIds.addAll(input.getOutputTupleIds());
        tupleIds.add(sequenceTupleId);
        tblRefIds.addAll(input.getTblRefIds());
        nullableTupleIds.addAll(input.getNullableTupleIds());
        this.sequences = ImmutableList.copyOf(sequences);
    }

    @Override
    protected void toThrift(TPlanNode msg) {
        msg.setNodeType(TPlanNodeType.SEQUENCE_NODE);
        msg.setSequenceNode(new TSequenceNode(sequences));
    }

    @Override
    public String getNodeExplainString(String prefix, TExplainLevel detailLevel) {
        return prefix + "sequences: " + sequences.stream()
                .map(spec -> String.valueOf(spec.getSequenceId()))
                .collect(java.util.stream.Collectors.joining(", ")) + "\n";
    }
}
