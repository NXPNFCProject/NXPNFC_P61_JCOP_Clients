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

import org.apache.commons.codec.binary.Hex;

import android.content.Context;
import android.util.Log;


public class VCDescriptionFile {


    public VCDescriptionFile(){
    }

    public byte[] createVc(byte[] VCData,String appName, short VC_Entry)
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


    public byte[] deleteVc(String appName,short VC_Entry)
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

    static byte[] parseHexProperty(String name, String key)
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

    public byte[] CreateVCResp(int vCEntry, String VC_UID) {
        byte[] Rdata = new byte[2+VC_UID.length()];
        byte[] VC_EntryBytes = Utils.shortToByteArr((short)vCEntry);
        byte[] VCUID =  parseHexProperty("VC_UID",VC_UID);
        byte[] stat =  new byte[] {(byte)0x90,(byte)0x00};
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(createTlv(0x40, VC_EntryBytes));
            out.write(createTlv(0x41, VCUID));
            out.write(createTlv(0x4E, stat));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return out.toByteArray();
    }

    public byte[] CreatePersonalizeData(byte[] VCData, String pkgName,String appShaName, short VC_Entry, Context context) {
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

    public byte[] AddandUpdateMDAC(byte[] vCData, short vcEntry, String appName) {
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

    public byte[] CreateMFData(byte[] vCData, short vCEntry) {
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

    public byte[] formVCList(int VCEntry, String AppName){
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
}
