package org.privote.backend.web3.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

public class Web3Types
{
    public static UUID bytes16ToUuid(byte[] b)
    {
        if (b == null || b.length != 16) throw new IllegalArgumentException("uuid must be 16 bytes");
        ByteBuffer bb = ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN);
        long most = bb.getLong();
        long least = bb.getLong();
        return new UUID(most, least);
    }

    public static byte[] uuidToBytes16(UUID u)
    {
        ByteBuffer bb = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN);
        bb.putLong(u.getMostSignificantBits());
        bb.putLong(u.getLeastSignificantBits());
        return bb.array();
    }
}
