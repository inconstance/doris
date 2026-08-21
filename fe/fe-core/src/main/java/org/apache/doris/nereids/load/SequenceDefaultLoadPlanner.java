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

package org.apache.doris.nereids.load;

import org.apache.doris.analysis.CastExpr;
import org.apache.doris.analysis.DescriptorTable;
import org.apache.doris.analysis.Expr;
import org.apache.doris.analysis.SlotDescriptor;
import org.apache.doris.analysis.SlotRef;
import org.apache.doris.analysis.TupleDescriptor;
import org.apache.doris.catalog.Column;
import org.apache.doris.catalog.Database;
import org.apache.doris.catalog.Env;
import org.apache.doris.catalog.OlapTable;
import org.apache.doris.catalog.Sequence;
import org.apache.doris.catalog.Type;
import org.apache.doris.common.AnalysisException;
import org.apache.doris.common.UserException;
import org.apache.doris.planner.FileLoadScanNode;
import org.apache.doris.planner.PlanNode;
import org.apache.doris.planner.PlanNodeId;
import org.apache.doris.planner.SequenceNode;
import org.apache.doris.thrift.TSequenceSpec;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Adds Sequence evaluation above a file scan for columns whose input uses a NEXTVAL default. */
final class SequenceDefaultLoadPlanner {
    private SequenceDefaultLoadPlanner() {
    }

    static PlanNode wrap(FileLoadScanNode fileScanNode, TupleDescriptor scanTuple,
            Collection<String> sequenceDefaultColumns, DescriptorTable descriptorTable,
            Database db, OlapTable table) throws UserException {
        if (sequenceDefaultColumns.isEmpty()) {
            return fileScanNode;
        }

        TupleDescriptor sequenceTuple = descriptorTable.createTupleDescriptor("SequenceDefaultTuple");
        Map<String, SlotDescriptor> sequenceSlots = Maps.newHashMap();
        List<TSequenceSpec> specs = Lists.newArrayList();
        for (String columnName : sequenceDefaultColumns) {
            Column column = table.getColumn(columnName);
            List<String> nameParts = column.getDefaultSequenceNameParts();
            String sequenceDbName = nameParts.size() == 2 ? nameParts.get(0) : db.getFullName();
            String sequenceName = nameParts.get(nameParts.size() - 1);
            Database sequenceDb = Env.getCurrentInternalCatalog().getDbNullable(sequenceDbName);
            Sequence sequence = sequenceDb == null ? null : sequenceDb.getSequenceNullable(sequenceName);
            if (sequence == null) {
                throw new AnalysisException("Sequence does not exist: " + String.join(".", nameParts));
            }

            SlotDescriptor sequenceSlot = descriptorTable.addSlotDescriptor(sequenceTuple);
            sequenceSlot.setType(Type.LARGEINT);
            sequenceSlot.setLabel("__sequence_default_" + columnName);
            sequenceSlot.setIsMaterialized(true);
            sequenceSlot.setIsNullable(false);
            sequenceSlots.put(columnName, sequenceSlot);
            specs.add(new TSequenceSpec(sequenceDb.getId(), sequence.getId(), sequence.getVersion(),
                    sequenceSlot.getId().asInt(), true, sequence.getCacheSize()));
        }

        SequenceNode sequenceNode = new SequenceNode(
                new PlanNodeId(1), fileScanNode, sequenceTuple.getId(), specs);
        List<Expr> projections = Lists.newArrayListWithCapacity(scanTuple.getSlots().size());
        for (SlotDescriptor outputSlot : scanTuple.getSlots()) {
            SlotDescriptor sequenceSlot = sequenceSlots.get(outputSlot.getColumn().getName());
            projections.add(sequenceSlot == null
                    ? new SlotRef(outputSlot)
                    : new CastExpr(outputSlot.getType(), new SlotRef(sequenceSlot)));
        }
        sequenceNode.setProjectList(projections);
        sequenceNode.setOutputTupleDesc(scanTuple);
        sequenceNode.init();
        return sequenceNode;
    }
}
