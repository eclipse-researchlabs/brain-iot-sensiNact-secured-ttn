/*
 * Copyright (c) 2020 - 2021 Kentyou.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
*    Kentyou - initial API and implementation
*/
package org.eclipse.sensinact.gateway.brainiot.ttn.decoder;

public abstract class ByteConverter {

    public static double unsignedByteArrayToDouble(byte[] b) {
        final int length = b.length;
        double value = 0;
        for (int i = 0; i < length; i++) {
            int shift = (length - 1 - i) * 8;
            value += (b[i] & 0x000000FF) << shift;
        }
        return value;
    }

    public static double lsbUnsignedByteArrayToDouble(byte[] b) {
        final int length = b.length;
        double value = 0;
        for (int i = length-1; i >=0; i--) {
            int shift = i * 8;
            value += (b[i] & 0x000000FF) << shift;
        }
        return value;
    }

    public static double byteArrayToDouble(byte[] b) {
        final int length = b.length;
        double value = 0;
        for (int i = 0; i < length; i++) {
            int shift = (length - 1 - i) * 8;
            if (b.length == 1) {
                value += (b[i] & 0x000000FF) << shift;
            } else if (b[i] < 0 && i == 0) {
                value += (b[i] | 0xFFFFFF00) << shift;
            } else {
                value += (b[i] & 0x000000FF) << shift;
            }
        }
        return value;
    }
    
    public static double lsbByteArrayToDouble(byte[] b) {
        final int length = b.length;
        double value = 0;
        for (int i = length-1; i >= 0; i--) {
            int shift = i * 8;
            if (b.length == 1) 
                value += (b[i] & 0x000000FF) << shift;
            else if (b[i] < 0 && i == length-1) 
                value += (b[i] | 0xFFFFFF00) << shift;
            else 
                value += (b[i] & 0x000000FF) << shift;
        }
        return value;
    }

    public static String byteToHex(byte b) {
        int i = b & 0xFF;
        return Integer.toHexString(i);
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}
