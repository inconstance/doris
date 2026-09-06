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

package org.apache.doris.plsql.objects;

import org.apache.doris.plsql.Var;
import org.apache.doris.plsql.exception.PlValidationException;

import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Triple-DES operations used by DBMS_OBFUSCATION_TOOLKIT. */
public class DbmsObfuscationToolkit implements PlObject {
    private static final byte[] DEFAULT_IV = new byte[] {
            0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xab, (byte) 0xcd, (byte) 0xef
    };

    private final PlClass plClass;

    public DbmsObfuscationToolkit(PlClass plClass) {
        this.plClass = plClass;
    }

    @Override
    public PlClass plClass() {
        return plClass;
    }

    public byte[] encrypt(byte[] input, byte[] key, int which, byte[] iv) {
        return crypt(Cipher.ENCRYPT_MODE, input, key, which, iv);
    }

    public byte[] decrypt(byte[] input, byte[] key, int which, byte[] iv) {
        return crypt(Cipher.DECRYPT_MODE, input, key, which, iv);
    }

    private byte[] crypt(int mode, byte[] input, byte[] key, int which, byte[] iv) {
        validate(input, key, which, iv);
        try {
            Cipher cipher = Cipher.getInstance("DESede/CBC/NoPadding");
            cipher.init(mode, new SecretKeySpec(expandKey(key, which), "DESede"),
                    new IvParameterSpec(iv == null ? DEFAULT_IV : Arrays.copyOf(iv, 8)));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException e) {
            throw new PlValidationException(null, "DBMS_OBFUSCATION_TOOLKIT 3DES operation failed: "
                    + e.getMessage());
        }
    }

    private static void validate(byte[] input, byte[] key, int which, byte[] iv) {
        if (input.length == 0 || key.length == 0) {
            throw new PlValidationException(null, "ORA-28231: Invalid input to Obfuscation toolkit");
        }
        if (input.length % 8 != 0) {
            throw new PlValidationException(null, "ORA-28232: Invalid input size for Obfuscation toolkit");
        }
        int keyLength = which == 0 ? 16 : which == 1 ? 24 : -1;
        if (keyLength < 0) {
            throw new PlValidationException(null, "ORA-28236: Invalid Triple DES mode");
        }
        if (key.length < keyLength) {
            throw new PlValidationException(null, "ORA-28234: Key length too short");
        }
        if (iv != null && iv.length < 8) {
            throw new PlValidationException(null, "DBMS_OBFUSCATION_TOOLKIT IV must contain at least 8 bytes");
        }
    }

    private static byte[] expandKey(byte[] key, int which) {
        byte[] expanded = new byte[24];
        System.arraycopy(key, 0, expanded, 0, which == 0 ? 16 : 24);
        if (which == 0) {
            System.arraycopy(key, 0, expanded, 16, 8);
        }
        return expanded;
    }
}
