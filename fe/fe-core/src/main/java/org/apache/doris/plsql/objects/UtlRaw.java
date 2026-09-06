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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Oracle-compatible subset of UTL_RAW. */
public class UtlRaw implements PlObject {
    private final PlClass plClass;

    public UtlRaw(PlClass plClass) {
        this.plClass = plClass;
    }

    @Override
    public PlClass plClass() {
        return plClass;
    }

    public Var castToRaw(String value) {
        return new Var(value.getBytes(StandardCharsets.UTF_8));
    }

    public Var castToVarchar2(byte[] value) {
        return new Var(new String(value, StandardCharsets.UTF_8));
    }

    public Var convert(byte[] value, Charset toCharset, Charset fromCharset) throws CharacterCodingException {
        CharBuffer characters = fromCharset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(value));
        ByteBuffer converted = toCharset.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(characters);
        byte[] result = new byte[converted.remaining()];
        converted.get(result);
        return new Var(result);
    }
}
