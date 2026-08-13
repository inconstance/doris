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

#include "pipeline/exec/sequence_operator.h"

#include <gen_cpp/FrontendService.h>

#include "runtime/client_cache.h"
#include "runtime/exec_env.h"
#include "runtime/large_int_value.h"
#include "util/string_parser.hpp"
#include "util/thrift_rpc_helper.h"
#include "vec/columns/columns_number.h"
#include "vec/data_types/data_type_number.h"

namespace doris::pipeline {

using namespace vectorized;

SequenceOperatorX::SequenceOperatorX(ObjectPool* pool, const TPlanNode& tnode, int operator_id,
                                     const DescriptorTbl& descs)
        : StreamingOperatorX<SequenceLocalState>(pool, tnode, operator_id, descs),
          _sequences(tnode.sequence_node.sequences),
          _descs(descs) {}

Status SequenceOperatorX::_parse_canonical_int128(std::string_view text, int128_t* value) {
    if (text.empty() || text.front() == '+' || text == "-0" ||
        (text.size() > 1 && text.front() == '0') ||
        (text.size() > 2 && text.front() == '-' && text[1] == '0')) {
        return Status::InvalidArgument("Non-canonical Sequence integer: {}", text);
    }
    size_t begin = text.front() == '-' ? 1 : 0;
    if (begin == text.size()) {
        return Status::InvalidArgument("Invalid Sequence integer: {}", text);
    }
    for (size_t i = begin; i < text.size(); ++i) {
        if (text[i] < '0' || text[i] > '9') {
            return Status::InvalidArgument("Invalid Sequence integer: {}", text);
        }
    }
    StringParser::ParseResult parse_result;
    *value = StringParser::string_to_int<int128_t>(text.data(), text.size(), &parse_result);
    if (parse_result != StringParser::PARSE_SUCCESS) {
        return Status::InvalidArgument("Sequence integer is outside LARGEINT: {}", text);
    }
    return Status::OK();
}

Status SequenceOperatorX::_fill_nextvals(RuntimeState* state, const TSequenceSpec& spec,
                                         size_t rows, MutableColumnPtr& column) {
    auto* values = assert_cast<ColumnInt128*>(column.get());
    size_t remaining = rows;
    while (remaining > 0) {
        int64_t request_count =
                spec.cache_size == 0 ? 1 : std::min<int64_t>(remaining, spec.cache_size);
        TSequenceRangeRequest request;
        request.__set_db_id(spec.db_id);
        request.__set_sequence_id(spec.sequence_id);
        request.__set_count(request_count);
        request.__set_sequence_version(spec.sequence_version);
        request.__set_query_id(state->query_id());
        request.__set_fragment_instance_id(state->fragment_instance_id());

        TSequenceRangeResult result;
        TNetworkAddress target = ExecEnv::GetInstance()->master_info()->network_address;
        for (int attempt = 0; attempt < 2; ++attempt) {
            RETURN_IF_ERROR(ThriftRpcHelper::rpc<FrontendServiceClient>(
                    target.hostname, target.port,
                    [&request, &result](FrontendServiceConnection& client) {
                        client->getSequenceRange(result, request);
                    }));
            if (result.status.status_code != TStatusCode::NOT_MASTER ||
                !result.__isset.master_address || attempt != 0) {
                break;
            }
            target = result.master_address;
        }
        RETURN_IF_ERROR(Status::create(result.status));
        size_t received = 0;
        int128_t last_value = 0;
        for (const auto& segment : result.segments) {
            int128_t start;
            int128_t increment;
            RETURN_IF_ERROR(_parse_canonical_int128(segment.start_value, &start));
            RETURN_IF_ERROR(_parse_canonical_int128(segment.increment, &increment));
            for (int64_t i = 0; i < segment.count; ++i) {
                last_value = start + static_cast<int128_t>(i) * increment;
                values->insert_value(last_value);
            }
            received += segment.count;
        }
        if (received != static_cast<size_t>(request_count)) {
            return Status::InternalError("Sequence range returned {} values, expected {}", received,
                                         request_count);
        }
        remaining -= received;
        TSequenceUsage usage;
        usage.__set_sequence_id(spec.sequence_id);
        usage.__set_last_consumed_value(LargeIntValue::to_string(last_value));
        usage.__set_sequence_version(result.sequence_version);
        usage.__set_allocation_ticket(result.allocation_ticket);
        usage.__set_consumed_index(request_count - 1);
        state->add_sequence_usage(usage);
    }
    return Status::OK();
}

Status SequenceOperatorX::pull(RuntimeState* state, Block* block, bool* eos) {
    auto& local_state = get_local_state(state);
    SCOPED_TIMER(local_state.exec_time_counter());
    const size_t rows = block->rows();
    for (const auto& spec : _sequences) {
        const auto* slot = _descs.get_slot_descriptor(SlotId(spec.output_slot_id));
        if (slot == nullptr) {
            return Status::InternalError("Unknown Sequence output slot {}", spec.output_slot_id);
        }
        MutableColumnPtr column = ColumnInt128::create();
        if (spec.next_val) {
            RETURN_IF_ERROR(_fill_nextvals(state, spec, rows, column));
        } else {
            int128_t currval;
            RETURN_IF_ERROR(_parse_canonical_int128(spec.session_currval, &currval));
            assert_cast<ColumnInt128*>(column.get())->get_data().resize_fill(rows, currval);
        }
        block->insert({std::move(column), std::make_shared<DataTypeInt128>(), slot->col_name()});
    }
    local_state.add_num_rows_returned(rows);
    return Status::OK();
}

} // namespace doris::pipeline
