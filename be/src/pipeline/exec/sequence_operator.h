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

#pragma once

#include "pipeline/pipeline_x/operator.h"
#include "vec/core/block.h"

namespace doris {

namespace pipeline {

class SequenceLocalState final : public PipelineXLocalState<FakeSharedState> {
public:
    ENABLE_FACTORY_CREATOR(SequenceLocalState);
    SequenceLocalState(RuntimeState* state, OperatorXBase* parent)
            : PipelineXLocalState<FakeSharedState>(state, parent) {}
};

class SequenceOperatorX final : public StreamingOperatorX<SequenceLocalState> {
public:
    SequenceOperatorX(ObjectPool* pool, const TPlanNode& tnode, int operator_id,
                      const DescriptorTbl& descs);

    Status pull(RuntimeState* state, vectorized::Block* block, bool* eos) override;

    [[nodiscard]] bool is_source() const override { return false; }

    DataDistribution required_data_distribution() const override {
        return {ExchangeType::PASSTHROUGH};
    }

private:
    Status _fill_nextvals(RuntimeState* state, const TSequenceSpec& spec, size_t rows,
                          vectorized::MutableColumnPtr& column);
    static Status _parse_canonical_int128(std::string_view text, int128_t* value);

    std::vector<TSequenceSpec> _sequences;
    const DescriptorTbl& _descs;
};

} // namespace pipeline
} // namespace doris
