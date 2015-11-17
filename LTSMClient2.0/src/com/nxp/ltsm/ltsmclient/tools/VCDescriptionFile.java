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
import java.io.IOException;
import java.util.List;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import android.content.Context;
import android.util.Log;


public class VCDescriptionFile {


    public VCDescriptionFile(){
    }

    public static byte[] createVc(byte[] VCData,String appName, short VC_Entry)
    {
        String TAG = "VCDescriptionFile:createVC";
        Log.i(TAG, "Enter");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] VC_EntryBytes = Utils.shortToByteArr(VC_Entry);
        try{
            out.write(createTlv(0x4F, Hex.decodeHex(appName.toCharArray())));
            out.write(createTlv(0x40, VC_EntryBytes));
            return(createTlv(0x70,Utils.append(out.toByteArray(),VCData)));
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }


    public static byte[] createF8Vc(String appName, short vcEntry,byte[] mfdfaid, byte[] cltecap)
    {
        String TAG = "VCDescriptionFile:createVC";
        Log.i(TAG, "Enter");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] apkId = null;
        try {
            apkId = Hex.decodeHex(appName.toCharArray());
        } catch (DecoderException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
            return null;
        }
        try
        {
            out.write(TLV.createTLV(0x4F, apkId));
            out.write(TLV.createTLV(0x40, new byte[] { (byte)(vcEntry >> 8), (byte)(vcEntry) }));
            out.write(TLV.createTLV(0xF8, mfdfaid));
            out.write(TLV.createTLV(0xE2, cltecap));
            return TLV.createTLV(0x74, out.toByteArray());
        }
        catch(IOException e)
        {
            e.printStackTrace();
            System.exit(1);
            return null;
        }
    }

    public static byte[] deleteVc(String appName,short VC_Entry)
    {
        String TAG = "VCDescriptionFile:deleteVc";
        Log.i(TAG, "Enter");
        byte[] VC_EntryBytes = Utils.shortToByteArr(VC_Entry);
        try
        {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            out.write(createTlv(0x4F, Hex.decodeHex(appName.toCharArray())));
            out.write(createTlv(0x40, VC_EntryBytes));
            Log.i(TAG, "Exit");
            return createTlv(0x71, out.toByteArray());
        }
        catch(Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }


    public static byte[] createTlv(int tag, byte[] value)
    {
        if ((value == null) || (value.length == 0))
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

    public static byte[] parseHexProperty(String name, String key)
    {
        String TAG = "VCDescriptionFile:parseHexProperty";
        String value = key;

        try
        {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            while(value.length() > 0)
            {
                out.write(Integer.parseInt(value.substring(0, 2), 16));
                value = value.substring(2);
            }

            return out.toByteArray();
        }
        catch(Exception e)
        {
            e.printStackTrace();
            Log.i(TAG, "Property %s is malformed" + name);
            System.exit(1);
            return null;
        }
    }

    public static byte[] CreateVCResp(short vCEntry, byte[] VC_UID) {
        byte[] VC_EntryBytes = Utils.shortToByteArr(vCEntry);
        byte[] stat =  new byte[] {(byte)0x90,(byte)0x00};
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(createTlv(0x40, VC_EntryBytes));
            out.write(createTlv(0x41, VC_UID));
            out.write(createTlv(0x4E, stat));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return out.toByteArray();
    }

    public static byte[] CreatePersonalizeData(byte[] VCData, String pkgName,String appShaName, short VC_Entry, Context context) {
        String TAG = "VCDescriptionFile:cCreatePersonalizeData";
        Log.i(TAG, "Enter");
        int i = VCData.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] VC_EntryBytes = Utils.shortToByteArr(VC_Entry);
        try{
            out.write(createTlv(0x4F, Hex.decodeHex(appShaName.toCharArray())));
            out.write(createTlv(0x40, VC_EntryBytes));
            out.write(Utils.append(VCData,createTlv(0xC1, Hex.decodeHex(Utils.shaSignature(pkgName, context).toCharArray()))));
            return(createTlv(0x73,out.toByteArray()));
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] AddandUpdateMDAC(byte[] vCData, short vcEntry, String appName) {
        String TAG = "VCDescriptionFile:AddandUpdateMDAC";
        Log.i(TAG, "Enter");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] VC_EntryBytes = Utils.shortToByteArr(vcEntry);
        try{
            out.write(createTlv(0x4F, Hex.decodeHex(appName.toCharArray())));
            out.write(createTlv(0x40, VC_EntryBytes));
            return(createTlv(0x72,Utils.append(out.toByteArray(),vCData)));
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] CreateMFData(byte[] vCData, short vCEntry) {
        String TAG = "VCDescriptionFile:CreateMFData";
        Log.i(TAG, "Enter");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] VC_EntryBytes = Utils.shortToByteArr(vCEntry);
        try{
            out.write(createTlv(0x40, VC_EntryBytes));
            out.write(vCData);
            return(createTlv(0x78, out.toByteArray()));
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] formVCList(int VCEntry, String AppName){
        String TAG = "VCDescriptionFile:FormVCList";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] VC_EntryBytes = Utils.shortToByteArr((short)VCEntry);
        try{
            out.write(createTlv(0x40, VC_EntryBytes));
            out.write(createTlv(0x4F, AppName.getBytes("UTF-8")));
            return createTlv(0x61, out.toByteArray());

        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] GetVC()
    {
        return TLV.createTLV(0x75, new byte[0]);
    }
    public static byte[] getVcListResp(List<TLV> getStatusRspTlvs) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            for(TLV tlv: getStatusRspTlvs)
            {
                TLV tlv_VCstate = TLV.find(tlv.getNodes(), Data.TAG_VC_STATE);
                if(tlv_VCstate.getValue()[0] == Data.VC_CREATED){
                    TLV tlvVCx = TLV.find(tlv.getNodes(), Data.TAG_VC_ENTRY);
                    TLV tlvApkId = TLV.find(tlv.getNodes(), Data.TAG_SP_AID);
                    out.write(
                        TLV.createTLV(0x61, Utils.append(
                            tlvVCx.getTLV(),
                            tlvApkId.getTLV()
                        ))
                      );
                }
            }
        }catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return out.toByteArray();
    }
}
