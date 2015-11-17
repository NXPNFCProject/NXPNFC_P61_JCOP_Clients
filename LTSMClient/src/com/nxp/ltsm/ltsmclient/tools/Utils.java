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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.codec.Charsets;
import org.apache.commons.codec.binary.Hex;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.view.InflateException;

public class Utils {
    /**
     * Convert a byte array to an hexadecimal string
     *
     * @param data
     *            the byte array to be converted
     * @return the output string
     */
    public static String arrayToHex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            String bs = Integer.toHexString(data[i] & 0xFF).toUpperCase();
            if (bs.length() == 1) {
                sb.append(0);
            }
            sb.append(bs);
            sb.append(" ");
        }
        return sb.toString();
    }

    /**
     * Convert an hexadecimal string into a byte array. Each byte in ethe
     * hexadecimal string being separated by spaces or not (eg. s = "65 A0 12"
     * <=> "65A012").
     *
     * @param s
     *            the string to be converted
     * @return the corresponding byte array
     * @throws IllegalArgumentException
     *             if not a valid string representation of a byte array
     */
    public static byte[] hexToArray(String s) {

        // check the entry
        Pattern p = Pattern.compile("([a-fA-F0-9]{2}[ ]*)*");
        boolean valid = p.matcher(s).matches();

        if (!valid) {
            throw new IllegalArgumentException("not a valid string representation of a byte array :" + s);
        }

        String hex = s.replaceAll(" ", "");
        byte[] tab = new byte[hex.length() / 2];
        for (int i = 0; i < tab.length; i++) {
            tab[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return tab;
    }

    /**
     * Convert a byte array into ASCII string representation.
     *
     * @param buf
     *            The bytes to format.
     * @return ASCII string representation of the specified bytes.
     * @throws UnsupportedEncodingException
     */
    public static String toAsciiString(byte[] buf) throws UnsupportedEncodingException {
        String ascii = null;
        if (buf != null) {
            ascii = new String(buf, "US-ASCII");
            // Check the characters
            char[] charArray = ascii.toCharArray();
            for (int i = 0; i < charArray.length; i++) {
                // Show null character as blank space
                if (charArray[i] == (char) 0x00) {
                    charArray[i] = ' ';
                }
            }
            ascii = new String(charArray);
        }
        return ascii;
    }

    /**
     * Convert the byte array to an int starting from the given offset.
     *
     * @param b
     *            The byte array
     * @param offset
     *            The array offset
     * @return The integer
     */
    public static int byteArrayToInt(byte[] b) {
        if (b.length == 1) {
            return b[0] & 0xFF;
        } else if (b.length == 2) {
            return ((b[0] & 0xFF) << 8) + (b[1] & 0xFF);
        } else if (b.length == 3) {
            return ((b[0] & 0xFF) << 16) + ((b[1] & 0xFF) << 8) + (b[2] & 0xFF);
        } else if (b.length == 4)
            return (b[0] << 24) + ((b[1] & 0xFF) << 16) + ((b[2] & 0xFF) << 8) + (b[3] & 0xFF);
        else
            throw new IndexOutOfBoundsException();
    }

    public static String getTimestampString(byte[] timestamp) {
        if (timestamp == null || timestamp.length < 6) {
            return "?";
        }
        Log.i("TimeStamp Parser", "Timestampbytes: " + Utils.arrayToHex(timestamp));
        String timestampSting = "";
        int day = timestamp[0] & 0xFF;
        int month = timestamp[1] & 0xFF;
        byte[] yearBytes = new byte[2];
        System.arraycopy(timestamp, 2, yearBytes, 0, 2);
        int year = Utils.byteArrayToInt(yearBytes);
        int hours = timestamp[4] & 0xFF;
        int mins = timestamp[5] & 0xFF;

        // Check for correct Values
        if (day < 1 || day > 31 || month < 1 || month > 12 || year < 1990 || hours < 0 || hours > 24 || mins < 0 || mins > 59) {
            return "?";
        } else {
            timestampSting = String.valueOf(day) + "." + String.valueOf(month) + "." + String.valueOf(year) + " " + String.valueOf(hours) + ":"
                    + String.valueOf(mins);
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat ("d.M.y H:m");
        try {
            Date timestampDate = dateFormat.parse(timestampSting);
            dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");
            return dateFormat.format(timestampDate);
        } catch (ParseException e) {
            return "?";
        }
    }
    public static int CompareArrays(short[] array1, int array1pos, short[] array2, int array2pos, int len) {
        int i, j;
        for (i = array1pos,j = array2pos; j < len; i++,j++) {
            if (array1[i] != array2[j]) {
                return Data.FAILED;
            }
        }
        if(((i - array1pos ) == len) && ((j - array2pos ) == len)){
            return Data.SUCCESS;
        }
        else{
            return Data.FAILED;
        }
    }
    public static int ShorttoByteArraycopy(short[] src, int srcpos, byte[] dest, int destpos, int len) {
        String TAG = "LTSMlib:ShorttoIntArraycopy";

        if( dest.length < len ){
            Log.i(TAG, "dest array length less than length to be copied ");
            return Data.FAILED;
        }
        int i, j;
        for (i = srcpos,j = destpos; j < len; i++,j++) {
            dest[j] = (byte) src[i];
        }
        if(j == len){
            return Data.SUCCESS;
        }
        else{
            return Data.FAILED;
        }
    }
    public static int BytetoShortArraycopy(byte[] src, int srcpos, short[] dest, int destpos, int len) {
        String TAG = "LTSMlib:ShorttoIntArraycopy";

        if( dest.length < len ){
            Log.i(TAG, "dest array length less than length to be copied ");
            return Data.FAILED;
        }
        int i, j;
        for (i = srcpos,j = destpos; j < len; i++,j++) {
            dest[j] = src[i];
        }
        if(j == len){
            return Data.SUCCESS;
        }
        else{
            return Data.FAILED;
        }
    }

    public static byte[] makeCAPDU(int cla, int ins, int p1, int p2, byte[] cdata)
    {
        byte[] l,t;
        if (cdata == null)
        {
            return new byte[] { (byte)cla, (byte)ins, (byte)p1, (byte)p2, 0 };
        }
        //        else if (cdata.length > 128)
        //        {
        //        l = new byte[] { (byte)(0x81), (byte)cdata.length };
        //        t = append(new byte[] { (byte)cla, (byte)ins, (byte)p1, (byte)p2}, l);
        //        return append(t, cdata);
        //        }
        else
        {
            return append(new byte[] { (byte)cla, (byte)ins, (byte)p1, (byte)p2, (byte)cdata.length}, cdata);
        }
    }

    public static byte[] append(byte[] a, byte[] b)
    {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    public static byte[] extract(byte[] buffer, int offset, int length)
    {
        byte[] result = new byte[length];
        System.arraycopy(buffer, offset, result, 0, length);
        return result;
    }

    public static short getSW(byte[] rapdu)
    {
        byte sw1 = rapdu[rapdu.length - 2];
        byte sw2 = rapdu[rapdu.length - 1];
        return (short)((sw1 << 8) + (sw2 & 0xFF));
    }

    public static byte[] getRDATA(byte[] rapdu)
    {
        return extract(rapdu, 0, rapdu.length - 2);
    }

    public static byte adjustCLA(byte cla, byte lcnum)
    {
        return (byte)((cla & ~0x03) | (lcnum & 0x03));
    }

    public static void saveArray(int[] channel_id, String arrayName, Context mContext) {
        SharedPreferences.Editor edit= mContext.getSharedPreferences(arrayName, Context.MODE_PRIVATE).edit();
        edit.putInt("Count", channel_id.length);
        int count = 0;
        for (int i: channel_id){
            edit.putInt("IntValue_" + count++, i);
        }
        edit.commit();
    }

    public static int[] loadArray(String arrayName, Context mContext) {
        int[] ret;
        SharedPreferences prefs = mContext.getSharedPreferences(arrayName, Context.MODE_PRIVATE);
        int count = prefs.getInt("Count", 0);
        ret = new int[count];
        for (int i = 0; i < count; i++){
            ret[i] = prefs.getInt("IntValue_"+ i, i);
        }
        return ret;
    }

    public static void saveInt(int cnt, String name, Context mContext) {
        SharedPreferences.Editor edit= mContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit();
        edit.putInt("chnl_cnt", cnt);
        edit.commit();
    }


    public static int loadInt(String name, Context mContext) {
        SharedPreferences prefs = mContext.getSharedPreferences(name, Context.MODE_PRIVATE);
        int savedCnt = prefs.getInt("chnl_cnt", 0);
        return savedCnt;
    }

    public static String bytArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder();
        for(byte b: a)
            sb.append(String.format("%02X", b&0xff));
        return sb.toString();
    }


    public static boolean checkRegistryAvail(Context context) {
        String TAG = "Utils:checkRegistryAvail";
        Log.i(TAG, "Enter");
        File f = new File(
                "/data/data/com.nxp.ltsm.ltsmclient/shared_prefs/Reg.xml");
        if (f.exists()){
            Log.d(TAG, "LTSM Registry exist");
            return true;
        }
        else{
            Log.d(TAG, "not exists");
            return false;
        }
    }

    public static boolean checkRegBackupAvail(){
        String TAG = "Utils:checkRegistryAvail";
        Log.i(TAG, "Enter");
        File f = new File(
                Environment.getExternalStorageDirectory().getPath()+"/ltsmRegBackup/Reg.xml");
        if (f.exists()){
            Log.d(TAG, "LTSM Registry backup exist");
            return true;
        }
        else{
            Log.d(TAG, "LTSM Registry backup doesnt exist");
            return false;
        }
    }


    public static String shaSignature(String pkg, Context context){
        String TAG = "Utils:createSha";
        String hashString = "";
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
            Signature[] sigs = info.signatures;
            for (int i = 0; i < sigs.length; i++) {
                hashString = hashString+sigs[i].toCharsString();
            }
            Log.i(TAG,"pkg " + pkg);
            Log.i(TAG,"hashString " + hashString);
            if (hashString == "") {
                return hashString;
            }
            return makeShaSignature(hashString);
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public static String makeShaSignature(String hashString){
        String TAG = "Utils:retSha";
        StringBuffer sb = new StringBuffer();

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(hashString.getBytes());

            byte byteData[] = md.digest();

            for (int i = 0; i < byteData.length; i++) {
                sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
            }
            Log.i(TAG,"sb.toString() " + sb.toString());
            return  new String(Hex.encodeHex(byteData));
            //  return sb.toString();
            //return byteData.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;

    }

    public static String createSha(String pkg){
        String TAG = "Utils:createSha";
        StringBuffer sb = new StringBuffer();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(pkg.getBytes());

            byte byteData[] = md.digest();

            for (int i = 0; i < byteData.length; i++) {
                sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
            }
            return  new String(Hex.encodeHex(byteData));
            //  return sb.toString();
            //return byteData.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;

    }

    public static String getCallingAppPkg(Context context) {
        String TAG = "getCallingAppPkg";
        String packageName = null;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT_WATCH) {

            packageName = getForeGroundL(context);
        }
        else
        {
            packageName = getForeGroundKK(context);
        }
        Log.i(TAG,"packageName : " + packageName);
        return packageName;
    }

    static String getForeGroundKK(Context context)
    {
        ActivityManager am = (ActivityManager) context.getSystemService(context.ACTIVITY_SERVICE);
        // get the info from the currently running task
        List< ActivityManager.RunningTaskInfo > taskInfo = am.getRunningTasks(1);

        Log.d("topActivity", "CURRENT Activity ::"
                + taskInfo.get(0).topActivity.getClassName());
        String s = taskInfo.get(0).topActivity.getClassName();

        ComponentName componentInfo = taskInfo.get(0).topActivity;
        componentInfo.getPackageName();
        return componentInfo.getPackageName();
    }

    static String getForeGroundL(Context context)
    {
        final int PROCESS_STATE_TOP = 2;
        RunningAppProcessInfo currentInfo = null;
        Field field = null;
        try {
            field = RunningAppProcessInfo.class.getDeclaredField("processState");
        } catch (Exception e) {e.printStackTrace(); }
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<RunningAppProcessInfo> appList = am.getRunningAppProcesses();
        for (RunningAppProcessInfo app : appList) {
            if (app.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                    app.importanceReasonCode == 0 ) {
                Integer state = null;
                try {
                    state = field.getInt( app );
                } catch  (Exception e) {e.printStackTrace(); }
                if (state != null && state == PROCESS_STATE_TOP) {
                    currentInfo = app;
                    break;
                }
            }
        }
        return currentInfo.processName;
    }

    public static List getINstalledApps(Context context){
        List<PackageInfo> apps = context.getPackageManager().getInstalledPackages(0);
        return apps;
    }

    public static byte[] shortToByteArr(short vC_Entry) {
        String TAG = "shortToByteArr";
        Log.i(TAG,"vC_Entry)" + vC_Entry);
        byte[] vcEntryBytes = new byte[2];
        vcEntryBytes[1] = (byte)(vC_Entry & 0xff);
        vcEntryBytes[0] = (byte)((vC_Entry >> 8) & 0xff);
        return vcEntryBytes;

    }

    public static byte[] getValue(byte Tag, byte[] rapdu) {
        String TAG = "Utils:getValue";
        byte[] retData = null;
        for(int i = 0 ; i< rapdu.length-1 ; i++ ){
            if(Tag == rapdu[i]){
                retData = new byte[rapdu[i+1]];
                System.arraycopy(rapdu, i+2, retData, 0, rapdu[i+1]);
                Log.i(TAG,"retData" + bytArrayToHex(retData));
                break;
            }
        }
        return retData;
    }

    public static byte[] getVcCount(byte[] recvData) {
        byte[] temp = getValue((byte)0x6F,recvData);
        byte[] temp1 = new byte[temp.length - (temp[1]+2)];
        System.arraycopy(temp, (temp[1]+2), temp1, 0, temp1.length);
        temp = getValue((byte)0xA5,temp1);
        temp = getValue((byte)0x10,temp);
        return temp;
    }

    public static boolean chekPersonalize(byte[] vCData) {
        String TAG = "Utils:getValue";
        Log.i(TAG,"Enter");
        byte[] temp = getValue((byte)0x46,vCData);
        if((temp[0]&(byte)0x02) == (byte)0x02){
            return true;
        }
        else{
            return false;
        }
    }
    public static byte[] createStatusCode(short statusCode){
        byte [] resp = Utils.shortToByteArr(statusCode);
        return Utils.append(new byte[]{(byte)0x4E,(byte)resp.length}, resp);
    }


    public static short byteArrayToShort(byte[] data)
    {
        return (short)((data[0]<<8) | (data[1]));
    }

    public static int lenOfTLV(byte[] tlv, int offset)
    {
        int tlvLen = 0;

        if(tlv != null)
        {
            if(tlv[offset] == 0x82)
            {
                tlvLen = tlv[offset+1]<<8|tlv[offset+2];
            }
            else if(tlv[offset] == 0x81)
            {
                tlvLen = tlv[offset+1];
            }
            else if(tlv[offset] <= 0x7F)
            {
                tlvLen = tlv[offset];
            }
            else
            {
                /*Error case*/
            }
        }
        return tlvLen;
    }

    public static int skipLenTLV(byte[] tlv, int offset)
    {
        int skipLen = 1;

        if(tlv != null)
        {
            if(tlv[offset] == 0x82)
            {
                skipLen = 3;
            }
            else if(tlv[offset] == 0x81)
            {
                skipLen = 2;
            }
            else if(tlv[offset] <= 0x7F)
            {
                skipLen = 1;
            }
            else
            {
                /*Error case*/
            }
        }
        return skipLen;
    }

    public static byte[] findNextAID(byte[] byteArr, int offset)
    {
        byte[] aidBytes = new byte[byteArr[offset]+2];

        for(int i=0;i< (byteArr[offset]+(byte)2);i++)
        {
            /*Modified for Including 0x4F tag and length*/
            aidBytes[i] = byteArr[offset-(byte)1+i];
        }
        return aidBytes;
    }
    public static void exportReg(){
        String  pathsrc = "/data/data/com.nxp.ltsm.ltsmclient/shared_prefs/Reg.xml";
        String pathdest = Environment.getExternalStorageDirectory().getPath()+"/ltsmRegBackup/";

        File ip = new File(pathsrc);
        File op = new File(pathdest);

        copyFile(pathsrc,pathdest);
    }

    public static void importReg(){
        String pathdest  =  "/data/data/com.nxp.ltsm.ltsmclient/shared_prefs/";
        String pathsrc   =  Environment.getExternalStorageDirectory().getPath()+"/ltsmRegBackup/Reg.xml";

        File ip = new File(pathsrc);
        File op = new File(pathdest);

        copyFile(pathsrc,pathdest);
    }

    public static void copyFile(String pathsrc,  String pathdest)
            throws InflateException {
        String TAG = "copyFile";
        String filepath = "";
        String filename = "Reg.xml";

        File sourceLocation = new File(pathsrc);
        File targetLocation = new File(pathdest);
        try {
            Log.i(TAG, "Enter");
            if(!sourceLocation.exists()){
                Log.i(TAG, "sourceLocation "+pathsrc+" Does not exist..!!");
                return;
            }
            if (!targetLocation.exists()) {
                Log.i(TAG, "Creating Target Directory");
                targetLocation.mkdirs();
            }
            filepath = pathdest+filename;
            targetLocation = new File(filepath);
            InputStream in = new FileInputStream(sourceLocation);

            OutputStream out = new FileOutputStream(targetLocation);

            // Copy the bits from instream to outstream
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
