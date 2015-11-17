/*
 * Copyright (C) 2014 NXP Semiconductors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nxp.ltsm.ltsmclient.tools;
import java.io.ByteArrayOutputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class TLV
{
    private final int tag;
    private List<TLV> nodes;
    private final byte[] value;

    private TLV(int tag, byte[] value, List<TLV> nodes)
    {
        this.tag = tag;
        this.value = value;
        this.nodes = nodes;
    }

    private TLV(int tag, byte[] value)
    {
        this.tag = tag;
        this.value = value;
    }

    public static byte[] make(int tag, byte[] value)
    {
        return new TLV(tag, value).getTLV();
    }

    public boolean isConstructed()
    {
        return this.nodes != null;
    }

    public boolean isPrimitive()
    {
        return this.nodes != null;
    }

    public int getTag()
    {
        return this.tag;
    }

    public List<TLV> getNodes()
    {
        if (nodes == null)
        {
            throw new RuntimeException("Bad call");
        }

        return this.nodes;
    }

    public byte[] getValue()
    {
        return this.value;
    }

    public byte[] getTLV()
    {
        byte[] t, l;

        if ((this.tag & 0xFF00) == 0x0000)
        {
            t = new byte[] { (byte)this.tag };
        }
        else
        {
            t = new byte[] { (byte)(this.tag >> 8), (byte)this.tag };
        }

        if (value.length < 128)
        {
            l = new byte[] { (byte)value.length };
        }
    else if (value.length < 256)
      {
      l = new byte[] { (byte)(0x81), (byte)value.length };
      }
        else
        {
      l = new byte[] { (byte)(0x82), (byte)(value.length >> 8), (byte)value.length };
        }

    return Utils.append(Utils.append(t, l), value);
    }

    public static List<TLV> parse(byte[] buffer)
    {
        return parse(ByteBuffer.wrap(buffer));
    }

    public static List<TLV> parse(byte[] buffer, int[] asPrimitiveTags)
    {
        return parse(ByteBuffer.wrap(buffer), asPrimitiveTags);
    }

    private static List<TLV> parse(ByteBuffer bb)
    {
        return parse(bb, null);
    }

    private static List<TLV> parse(ByteBuffer bb, int[] asPrimitiveTags)
    {
        List<TLV> list = new ArrayList<TLV>();

        for(;;)
        {
            int tag;

            try
            {
                tag = bb.get() & 0xFF;
            }
            catch(BufferUnderflowException e)
            {
                break;
            }

            boolean isPrimitive = (tag & 0x20) == 0x00;

            if ((tag & 0x1F) == 0x1F)
            {
                // 2-byte tag
                tag = (tag << 8) + (bb.get() & 0xFF);
            }

            if (!isPrimitive && (asPrimitiveTags != null))
            {
                for(int asPrimitiveTag: asPrimitiveTags)
                {
                    if (tag == asPrimitiveTag)
                    {
                        isPrimitive = true;
                        break;
                    }
                }
            }

            int length = bb.get() & 0xFF;
            if (length <= 0x7F)
            {
                // 1-byte length
            }
            else if (length == 0x81)
            {
                length = bb.get() & 0xFF;
            }
            else if (length == 0x82)
            {
                int length1 = bb.get() & 0xFF;
                int length2 = bb.get() & 0xFF;
                length = (length1 << 8) + length2;
            }
            else
            {
                throw new RuntimeException("Bad length field");
            }

            byte[] value = new byte[length];
            bb.get(value, 0, value.length);

            if (isPrimitive)
            {
                list.add(new TLV(tag, value));
            }
            else
            {
        list.add(new TLV(tag, value, TLV.parse(value, asPrimitiveTags)));
            }
        }

        return list;
    }

    public static TLV find(List<TLV> nodes, int tag)
    {
        for(TLV tlv: nodes)
        {
            if (tlv.getTag() == tag)
            {
                return tlv;
            }
        }

        return null;
    }

    public static byte[] createTLV(int tag, byte[] value)
    {
        if (value == null)
        {
            return new byte[0];
        }
        else
        {
            byte[] t, l;

            if ((tag & 0xFF00) == 0x0000)
            {
                t = new byte[] { (byte)tag };
            }
            else
            {
                t = new byte[] { (byte)(tag >> 8), (byte)tag };
            }

            if (value.length < 128)
            {
                l = new byte[] { (byte)value.length };
            }
            else if (value.length < 256)
            {
                l = new byte[] { (byte)(0x81), (byte)value.length };
            }
            else
            {
                l = new byte[] { (byte)(0x82), (byte)(value.length >> 8), (byte)value.length };
            }

            return Utils.append(Utils.append(t, l), value);
        }
    }

    public static byte[] make(List<TLV> tlvs)
    {
        try
        {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            for(TLV tlv: tlvs)
            {
                out.write(tlv.getTLV());
            }

            return out.toByteArray();
        }
        catch(Exception e)
        {
            return null;
        }
    }

}
