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

package com.nxp.ltsm.ltsmclient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import android.R.integer;
import android.R.string;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import com.nxp.eseclient.LtsmService;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.InflateException;

import com.google.gson.Gson;
import com.nxp.ltsm.ltsmclient.tools.Data;
import com.nxp.ltsm.ltsmclient.tools.Utils;
import com.nxp.ltsm.ltsmclient.tools.VCDescriptionFile;

import java.util.Arrays;

public class LTSMCommunicator extends ILTSMClient.Stub {

    private static final String LOG_TAG = "LTSMCommunicator";
    private  static Context context;
    private static String CurrentPackage;
    private static Binder mBinder = new Binder();
    private static Bundle bundle;
    static int[] channelId = new int[10];
    static int[] isopened = new int[10];
    static int channelCnt = 0;
    static VCDescriptionFile vcDescriptionFile = new VCDescriptionFile();
    private static LtsmService mLtsmService = null;
    private static String currentProcess = "";
    static ltsmRegistry Registry;
    static int InitialVCEntry = 0;
    static int currentVcEntry = 0;
    static int DeleteVCEntry  = 99;
    static short statusCode   = 0;

    public LTSMCommunicator(Context context) {
        LTSMCommunicator.context = context;

    }

    /*******************************************************
     *
     *  Responsible for creating the Virtual Card
     *  The second argument-Personalise Data is optional and it is not mandatory to pass.
     *  If VC Creation configuration is set to 1, then Personalise Data shall be also provided.
     *
     *@param    vcData
     *@param    Personalise Data
     *
     *@return   If success then the VC entry number and VCUID are returned
     *          to the SP Application along with status word. Otherwise an error code.
     *
     *******************************************************/

    @Override
    public synchronized byte[] createVirtualCard(byte[] vcData,byte[] personalizeData) throws RemoteException {
        String TAG = "LTSMCommunicator:CreateVirtualCard";
        Log.i(TAG, "Enter");
        statusCode = 0;
        byte[] rData = new byte[0];
        byte cData[] = new byte[0];
        currentProcess = "CreateVirtualCard";
        int stat = Data.FAILED;
        boolean personalize = false;
        Log.i(TAG, "vcData : " + Utils.bytArrayToHex(vcData));

        boolean available = Utils.checkRegistryAvail(context);
        if(!available){
            available = Utils.checkRegBackupAvail();
            if(available){
                Utils.importReg();
            }else{
                Log.i(TAG, "no backup available");
                //return Utils.createStatusCode(Data.SW_REGISTRY_IS_EMPTY);
            }
        }
        if(available){

            //If Registry is already available in SharedPreference Check for Uninstalled Wallet Apps
            stat = checkUninstalledApps();
            if(stat == Data.SUCCESS){
                Log.i(TAG, "Uninstalled App Found");
            }
            stat = getStatus();
            if(stat == Data.SUCCESS){
                Log.i(TAG, "getStatus success");
            }
        }

        // Initiate the LTSM. Open Logical Channel and Select LTSM
        stat = ltsmStart();

        if(stat == Data.FAILED){
            Log.i(TAG, "LTSMStart Failed");
            if(statusCode != 0){
                return Utils.createStatusCode(statusCode);
            }
        }

        // Get the VC Entry for Current Create VC
        stat = managecurrentVcEntry(Registry);
        if(stat == Data.FAILED){
            Log.i(TAG, "Registry Exceeds MaxVC Count");
            closeLogicalChannel();
            return Utils.createStatusCode(Data.SW_REGISTRY_FULL);
        }

        //Create the Registry Entries for New VC Entry
        Registry.vcCount = Registry.vcCount + 1;
        Registry.walletName.add(Utils.getCallingAppPkg(context));
        Registry.shaWalletName.add(Utils.createSha(Utils.getCallingAppPkg(context)));
        Registry.vcCreateStatus.add(false);
        Registry.vcActivatedStatus.add(false);
        Registry.vcEntry.add((currentVcEntry));
        Registry.vcType.add("");
        Registry.vcUid.add("");
        saveRegistry(Registry);

        CurrentPackage = Utils.getCallingAppPkg(context);
        //Check Registry is Proper
        stat = check(Registry);
        if(stat == Data.FAILED){
            closeLogicalChannel();
            return Utils.createStatusCode(Data.SW_IMPROPER_REGISTRY);
        }

        if(personalizeData != null){
            stat = handlePersonalize(personalizeData,CurrentPackage,(short)currentVcEntry);
            if(stat == Data.SW_PROCESSING_ERROR){
                closeLogicalChannel();
                Log.i(TAG, "Error Personalization : " + Utils.createStatusCode(statusCode));
                return Utils.createStatusCode(statusCode);
            }
        }

        //currentProcess used during Process Response if CreateVC is Success
        currentProcess = "CreateVirtualCard";
        Registry = loadRegistry();
        Log.i(TAG, "(short)Registry.vcEntry.get(Registry.vcCount-1).intValue() " + (short)Registry.vcEntry.get(Registry.vcCount-1).intValue());
        cData = vcDescriptionFile.createVc(vcData,Registry.shaWalletName.get(Registry.vcCount-1),(short)currentVcEntry);

        Log.i(TAG, "cData : " + Utils.bytArrayToHex(cData));
        exchangeLtsmProcessCommand(cData);
        closeLogicalChannel();

        //Form the Response Data
        Registry = loadRegistry();
        for( int i = 0;i<Registry.vcCount;i++){
            if(Registry.vcEntry.get(i).equals(currentVcEntry))
            {
                if(Registry.vcCreateStatus.get(i) == false){
                    Log.i(TAG, "Create VC FAILED for VC Entry : "+Registry.vcEntry.get(i));
                    Log.i(TAG, "cData : " + Utils.bytArrayToHex(Utils.createStatusCode(statusCode)));
                    rData = Utils.createStatusCode(statusCode);
                }else{
                    rData = vcDescriptionFile.CreateVCResp(currentVcEntry,Registry.vcUid.get(i));
                    Log.i(TAG, "Create VC SUCCESS for VC Entry : "+Registry.vcEntry.get(i));
                }
                break;
            }
        }
        //Delete VC with Create Status False in the Registry.
        cleanUp();

        Log.i(TAG, "CreateVCResp : " + Utils.bytArrayToHex(rData));
        Utils.exportReg();
        Log.i(TAG, "Exit");
        return rData;
    }

    /*******************************************************
     *
     *  Responsible for deleting the Virtual Card
     *
     *@param    VC entry number to be deleted
     *
     *@return   Status word informing success or failure
     *
     ********************************************************/

    @Override
    public synchronized byte[] deleteVirtualCard(int vcEntry) throws RemoteException {
        String TAG = "LTSMCommunicator:deleteVirtualCard";
        Log.i(TAG, "Enter");
        statusCode = 0;
        byte[] rData = new byte[0];
        boolean available = Utils.checkRegistryAvail(context);
        if(!available){
            available = Utils.checkRegBackupAvail();
            if(available){
                Utils.importReg();
            }else{
                Log.i(TAG, "no backup available");
                return Utils.createStatusCode(Data.SW_REGISTRY_IS_EMPTY);
            }
        }
        String CurrentPackage = Utils.getCallingAppPkg(context);
        currentProcess = "deleteVirtualCard";
        DeleteVCEntry = vcEntry;
        int i;
        boolean VC_Present = false;
        int stat = Data.FAILED;
        Registry = loadRegistry();

        //Check Registry is Proper
        stat = check(Registry);
        if(stat == Data.FAILED){
            Log.i(TAG, "Registry data is not proper");
            return Utils.createStatusCode(Data.SW_IMPROPER_REGISTRY);
        }

        stat = ltsmStart();
        if(stat == Data.FAILED){
            Log.i(TAG, "LTSMStart Failed");
            if(statusCode != 0){
                return Utils.createStatusCode(statusCode);
            }
        }
        byte[] cData = vcDescriptionFile.deleteVc(Utils.createSha(Utils.getCallingAppPkg(context)),(short)vcEntry);

        stat = exchangeLtsmProcessCommand(cData);
        if(stat == Data.SW_NO_ERROR){
            rData =  Utils.createStatusCode(Data.SW_NO_ERROR);
        }
        else{
            rData =  Utils.createStatusCode(statusCode);
        }

        closeLogicalChannel();
        Utils.exportReg();
        Log.i(TAG, "deleteVirtualCard : " + Utils.bytArrayToHex(rData));
        Log.i(TAG, "Exit");
        return rData;
    }

    /*******************************************************
     *
     *  Activate/Deactivate Virtual Card.
     *
     *@param    vcEntry
     *@param    Activation mode - true/false for activate/deactivate the VC
     *
     *@return   Status word informing Success or Failure
     *
     ********************************************************/

    @Override
    public synchronized byte[] activateVirtualCard(int vcEntry, boolean mode) throws RemoteException {
        String TAG = "LTSMCommunicator:ActivateVirtualCard";
        Log.i(TAG, "Enter");
        byte[] rData = new byte[0];

        statusCode = 0;
        boolean available = Utils.checkRegistryAvail(context);
        if(!available){
            available = Utils.checkRegBackupAvail();
            if(available){
                Utils.importReg();
            }else{
                Log.i(TAG, "no backup available");
                return Utils.createStatusCode(Data.SW_REGISTRY_IS_EMPTY);
            }
        }

        byte[] retStat = new byte[4];
        int stat = Data.FAILED;
        byte[] deactivatedvcEntry = new byte[2],Ret_Data = new byte[4];
        byte[] rcvData = new byte[]{};

        int i;


        Registry = loadRegistry();
        if(Registry.vcCount == 0){
            Log.i(TAG, "No VC is Present");
            return Utils.createStatusCode(Data.SW_REGISTRY_IS_EMPTY);
        }
        try {
            Thread.sleep(1000);
        } catch (Exception e) {

        }

        String pkg = Utils.getCallingAppPkg(context);
        Log.i(TAG, "vcEntry : " + vcEntry);
        Log.i(TAG, "pkg : " + pkg);
        //Check for Requested VC Entry's Current Status
        for( i = 0;i<Registry.vcCount;i++){

            if((Registry.vcEntry.get(i).intValue() == vcEntry)&&(Registry.shaWalletName.get(i).toString().equals(Utils.createSha(pkg)))){
                break;
            }
        }
        if(i == Registry.vcCount){
            Log.i(TAG, "Not a Valid VC Entry");
            return Utils.createStatusCode(Data.SW_INVALID_VC_ENTRY);
        }
        byte[] aid = vcDescriptionFile.createTlv(0x4F,Utils.append(Data.ACTV_LTSM_AID,Utils.shortToByteArr((short)vcEntry)));
        Log.i(TAG, "aid : " + Utils.bytArrayToHex(aid));
        try {
            openConnection();
        } catch (RemoteException e) {
            // closeLogicalChannel();
            e.printStackTrace();
        }

        //Select CRS Prior to Activate
        stat = crsSelect();
        if(stat == Data.FAILED){
            Log.i(TAG, "crsSelect Failed");
            return Utils.createStatusCode(Data.SW_CRS_SELECT_FAILED);
        }

        rcvData = gating(aid,mode);

        if(Utils.getSW(rcvData) == Data.SUCCESS){
            rData =  Utils.createStatusCode(Data.SW_NO_ERROR);
        }
        else if (Utils.getSW(rcvData) == Data.SW_OTHER_ACTIVEVC_EXIST) {
            stat = parseSetStatusResp(rcvData);
            if (stat == Data.SUCCESS) {
                rcvData = gating(aid,mode);
                if(Utils.getSW(rcvData) == Data.SUCCESS){
                    // retData =  Utils.append(Utils.createStatusCode(Data.SW_OTHER_ACTIVEVC_EXIST), Utils.createStatusCode(Data.SW_NO_ERROR));
                    rData =  Utils.createStatusCode(Data.SW_NO_ERROR);
                }else{
                    rData = rcvData;
                }
            }
            else{
                rData =  Utils.createStatusCode(Data.SW_PROCESSING_ERROR);
            }
        }else{
            rData = rcvData;
        }
        Log.i(TAG, "retData : " + Utils.bytArrayToHex(rData));
        Utils.exportReg();
        try {
            closeConnection();
        } catch (RemoteException e) {
            // closeLogicalChannel();
            e.printStackTrace();
        }
        Log.i(TAG, "activateVirtualCard : " + Utils.bytArrayToHex(rData));
        Log.i(TAG, "Exit");
        return rData;
    }

    /*******************************************************
     *
     *  Add and Updates MDAC used to define the MIFARE Data that
     *  may be retrieved by the SP Application
     *
     *@param    vcEntry
     *@param    vcData
     *
     *@return   Status word informing Success or Failure
     *
     ********************************************************/

    @Override
    public synchronized byte[] addAndUpdateMdac(int vcEntry,byte[] vcData) throws RemoteException {
        String TAG = "LTSMCommunicator:addAndUpdateMdac";
        currentProcess = "addAndUpdateMdac";
        Log.i(TAG, "Enter");
        statusCode = 0;
        byte[] rData = new byte[0];
        int stat  = Data.FAILED;

        boolean available = Utils.checkRegistryAvail(context);
        if(!available){
            available = Utils.checkRegBackupAvail();
            if(available){
                Utils.importReg();
            }else{
                Log.i(TAG, "no backup available");
                return Utils.createStatusCode(Data.SW_REGISTRY_IS_EMPTY);
            }
        }

        String pkg = Utils.getCallingAppPkg(context);
        Registry = loadRegistry();
        int i;
        if(Registry.vcCount == 0){
            Log.i(TAG, "No VC is Present");
            return Utils.createStatusCode(Data.SW_REGISTRY_IS_EMPTY);
        }
        for( i = 0;i<Registry.vcCount;i++){
            if((Registry.vcEntry.get(i).intValue() == vcEntry)){//&&(Registry.shaWalletName.get(i).toString().equals(Utils.createSha(pkg)))){
                break;
            }
        }
        if(i == Registry.vcCount){
            Log.i(TAG, "Not a Valid VC Entry");
            return Utils.createStatusCode(Data.SW_INVALID_VC_ENTRY);
        }

        stat = ltsmStart();
        if(stat == Data.FAILED){
            Log.i(TAG, "LTSMStart Failed");
            if(statusCode != 0){
                return Utils.createStatusCode(statusCode);
            }
        }

        Registry = loadRegistry();
        byte[] cData = vcDescriptionFile.AddandUpdateMDAC(vcData,(short)vcEntry,Utils.createSha(pkg));
        Log.i(TAG, "cData : " + cData.length);
        stat = exchangeLtsmProcessCommand(cData);
        if(stat == Data.SW_NO_ERROR){
            rData = Utils.createStatusCode(Data.SW_NO_ERROR);
            // retStat = Utils.append(rData, Utils.createStatusCode(Data.SW_NO_ERROR));
            // rData = new byte[0];
        }
        else{
            rData = Utils.createStatusCode(statusCode);
        }
        closeLogicalChannel();
        Log.i(TAG, "retStat : " + Utils.arrayToHex(rData));
        Utils.exportReg();
        Log.i(TAG, "Exit");
        return rData;
    }

    /*******************************************************
     *
     *  Read Mifare Data used to retrieve MIFARE data from a VC
     *  under the condition that the MIFARE Data Access Control(s)
     *  have been provided with Add and update MDAC
     *
     *@param    vcData
     *@param    vcEntry
     *
     *@return   Read Mifare Data and Status Word
     *
     ********************************************************/

    @Override
    public synchronized byte[] readMifareData(int vcEntry,byte[] vcData)throws RemoteException {
        String TAG = "LTSMCommunicator:ReadMifareClassicData";
        Log.i(TAG, "Enter");
        statusCode = 0;
        byte[] rData = new byte[0];
        byte[] recvData = new byte[]{};

        boolean available = Utils.checkRegistryAvail(context);
        if(!available){
            available = Utils.checkRegBackupAvail();
            if(available){
                Utils.importReg();
            }else{
                Log.i(TAG, "no backup available");
                return Utils.createStatusCode(Data.SW_REGISTRY_IS_EMPTY);
            }
        }

        String pkg = Utils.getCallingAppPkg(context);
        Registry = loadRegistry();
        int i,stat;
        if(Registry.vcCount == 0){
            Log.i(TAG, "No VC is Present");
            return Utils.createStatusCode(Data.SW_REGISTRY_IS_EMPTY);
        }
        for( i = 0;i<Registry.vcCount;i++){
            if((Registry.vcEntry.get(i).intValue() == vcEntry)&&(Registry.shaWalletName.get(i).toString().equals(Utils.createSha(pkg)))){
                break;
            }
        }
        if(i == Registry.vcCount){
            Log.i(TAG, "Not a Valid VC Entry");
            return Utils.createStatusCode(Data.SW_INVALID_VC_ENTRY);
        }
        try {
            openConnection();
        } catch (RemoteException e) {
            closeConnection();
            e.printStackTrace();
        }

        byte[] cApdu = Utils.makeCAPDU(0x00, 0xA4, 0x04, 0x00, Utils.append(Data.serviceManagerAid, Utils.shortToByteArr((short)vcEntry)));

        recvData = exchangeWithSe(cApdu);
        // recvData = exchange(cApdu,channelId[channelCnt -1]);
        if(recvData == null)
        {
            Log.i(TAG, "SE transceive failed ");
            statusCode = Data.SW_SE_TRANSCEIVE_FAILED;
            stat = Data.FAILED;
        }

        Log.i(TAG, "Received Data : " + Utils.bytArrayToHex(recvData));

        Registry = loadRegistry();
        cApdu = Utils.makeCAPDU(0x80, 0xB0, 0x00, 0x00, vcData);
        recvData = exchangeWithSe(cApdu);
        rData = processReadMifareDataResponse(recvData);

        Log.i(TAG, "Exit");
        try {
            closeConnection();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "recvData : " + Utils.bytArrayToHex(rData));
        Utils.exportReg();
        return rData;
    }

    /*******************************************************
     *
     *  getVcList used to retrieve the list of couples VC entry
     *  in used and associated SP Application
     *
     *@param    none
     *
     *@return   byte array containing List of VC entry in use
     *and associated SP Applications and status word.
     *
     ********************************************************/

    @Override
    public byte[] getVcList() throws RemoteException {
        String TAG = "LTSMCommunicator:getVcList";
        Log.i(TAG, "Enter");
        byte[] tempData = new byte[]{};
        int stat = Data.FAILED;
        statusCode = 0;
        byte[] rData = new byte[0];

        boolean available = Utils.checkRegistryAvail(context);
        if(!available){
            available = Utils.checkRegBackupAvail();
            if(available){
                Utils.importReg();
            }else{
                Log.i(TAG, "no backup available");
                return Utils.createStatusCode(Data.SW_REGISTRY_IS_EMPTY);
            }
        }
        //If Registry is already available in SharedPreference Check for Uninstalled Wallet Apps
        stat = checkUninstalledApps();
        if(stat == Data.SUCCESS){
            Log.i(TAG, "Uninstalled App Found");
        }
        cleanUp();
        stat = ltsmStart();
        if(stat == Data.FAILED){
            Log.i(TAG, "LTSMStart Failed");
            if(statusCode != 0){
                return Utils.createStatusCode(Data.SW_PROCESSING_ERROR);
            }
        }
        rData = retrieveStatus();
        if (Utils.getSW(rData) == (short)(0x6A88)) {
            if (rData.length == 2) {
                rData = Utils.createStatusCode(Data.SW_PROCESSING_ERROR);
            }
            else{
                rData = Utils.append(Utils.getRDATA(rData), Utils.createStatusCode(Data.SW_NO_ERROR));
            }
        }else{
            rData = Utils.append(rData, Utils.createStatusCode(Data.SW_NO_ERROR));
        }
        Log.i(TAG, "retData : " + Utils.bytArrayToHex(rData));
        closeLogicalChannel();
        Utils.exportReg();
        return rData;
    }

    /*******************************************************
     *
     *Get the virtual card status from SE
     *
     ********************************************************/
    private byte[] retrieveStatus(){
        String TAG = "LTSMCommunicator:getStatusApdu";
        Log.i(TAG, "Enter");

        byte[] capdu = Utils.makeCAPDU(0x80, 0xF2, 0x40, 0x00, null);
        byte[] rapdu = exchange(capdu, channelId[channelCnt -1]);
        byte[] status = new byte[0];
        int initLen = status.length;
        loop: for(;;)
        {
            Log.i(TAG, "rapdu : " + Utils.bytArrayToHex(rapdu));
            switch(Utils.getSW(rapdu))
            {
            case (short)(0x6310):
                status = Utils.append(status, Utils.getRDATA(rapdu));
            break;

            case (short)(0x9000):
                status = Utils.append(status, Utils.getRDATA(rapdu));
            break loop;

            case (short)(0x6A88):
                status = Utils.append(status, rapdu);
            break loop;

            default: // Unexpected error.
                // FALL THROUGH
                break loop;
            }
            capdu = Utils.makeCAPDU(0x80, 0xF2, 0x40, 0x01, null); // Next occurrence.
            rapdu = exchange(capdu, channelId[channelCnt -1]);
        }
        Log.i(TAG, "status : " + Utils.bytArrayToHex(status));
        return status;
    }

    /*******************************************************
     *
     *Get the virtual card status from SE and update the registry
     *
     ********************************************************/

    private int getStatus(){

        String TAG = "LTSMCommunicator:getStatus";
        Log.i(TAG, "Enter");
        byte[] retData = new byte[]{};
        byte[] tempData = new byte[]{};
        byte[] statusReturned = new byte[]{};
        int stat = Data.FAILED;
        int i = 0;
        stat = ltsmStart();
        if(stat == Data.FAILED){
            Log.i(TAG, "LTSMStart Failed");
            if(statusCode != 0){
                return Data.FAILED;
            }
        }
        statusReturned = retrieveStatus();

        if (Utils.getSW(statusReturned) == (short)(0x6A88)) {
            Log.i(TAG, " statusReturned.length " + statusReturned.length);
            if (statusReturned.length == 2) {
                Log.i(TAG, " no vc present ");
                Registry = loadRegistry();
                while(Registry.vcCount!=0){
                    removeRegistryEntry(Registry.vcCount-1,Registry);
                    Registry = loadRegistry();
                }
                closeLogicalChannel();
                return Data.SUCCESS;
            }else {
                statusReturned = Utils.getRDATA(statusReturned);
            }

        }
        Log.i(TAG, "Final rapdu : " + Utils.bytArrayToHex(statusReturned));
        byte[] resp = processGetStatus(statusReturned);
        if(Utils.getSW(resp) != Data.SW_NO_ERROR){
            Log.i(TAG, "processGetStatus Failed ");

            stat =  Data.FAILED;
        }
        else {
            stat = Data.SUCCESS;
        }
        closeLogicalChannel();
        return stat;
    }
    private void removeRegistryEntry(int pos, ltsmRegistry Registry){
        int tmpVc = Registry.vcEntry.get(pos);
        String TAG = "LTSMCommunicator:removeRegistryEntry";
        Registry.shaWalletName.remove(pos);
        Registry.walletName.remove(pos);
        Registry.vcCreateStatus.remove(pos);
        Registry.vcActivatedStatus.remove(pos);
        Registry.vcEntry.remove(pos);
        Registry.vcType.remove(pos);
        Registry.vcUid.remove(pos);
        Registry.vcCount = Registry.vcCount - 1;
        Registry.deleteVcEntries.add(tmpVc);
        Log.i(TAG, " DeleteVCEntry " + tmpVc);
        saveRegistry(Registry);
    }

    /*******************************************************
     *
     * Initiate the LTSM. Opens the Logical Channel and Selects the LTSM
     *
     ********************************************************/

    public static int ltsmStart(){
        String TAG = "LTSMCommunicator:LTSMStart";
        Log.i(TAG, "Enter");
        int stat = Data.FAILED;
        stat = ltsmOpenChannel();
        if(stat == Data.FAILED){
            Log.i(TAG, "LTSM Open Channel Failed");
            return Data.FAILED;
        }
        stat = ltsmSelect();
        if(stat == Data.FAILED){
            Log.i(TAG, "LTSM select Failed");
            closeLogicalChannel();
            return Data.FAILED;
        }
        stat = Data.SUCCESS;
        Log.i(TAG, "Exit");
        return stat;
    }

    /*******************************************************
     *
     * Opens Logical Channel
     *
     ********************************************************/

    private static int ltsmOpenChannel() {
        String TAG = "LTSMCommunicator:openLogicalChannel";
        int stat = Data.FAILED;
        Log.i(TAG, "Enter");
        statusCode = 0;
        byte[] recvData = new byte[1024];

        try {
            openConnection();
        } catch (RemoteException e) {
            closeLogicalChannel();
            e.printStackTrace();
        }
        try {
            recvData = exchangeApdu(Data.openchannel, false);
        } catch (RemoteException e) {
            closeLogicalChannel();
            e.printStackTrace();
        }
        if(recvData == null)
        {
            Log.i(TAG, "SE transceive failed ");
            statusCode = Data.SW_SE_TRANSCEIVE_FAILED;
            return stat;
        }
        else if(Utils.getSW(recvData)!=Data.SW_NO_ERROR){
            statusCode = Utils.getSW(recvData);
            Log.i(TAG, "Invalid Response");
            return stat;
        }
        else{
            Log.i(TAG, "openLogicalChannel SUCCESS");
            channelId[channelCnt] = recvData[recvData.length - 3];
            isopened[channelCnt] = 1;

            Utils.saveArray(channelId, "channelId", context);
            Utils.saveArray(isopened, "isopened", context);
            channelCnt = channelCnt + 1;
            Utils.saveInt(channelCnt,"channelCnt",context);
            stat = Data.SUCCESS;
        }

        Log.i(TAG, "Exit");
        return stat;
    }

    /*******************************************************
     *
     *Selects LTSM with currently opened Logical channel
     *
     ********************************************************/

    private static int ltsmSelect() {
        String TAG = "LTSMCommunicator:ltsmSelect";
        int stat = Data.FAILED;
        boolean available = false;
        Log.i(TAG, "Enter");
        statusCode = 0;
        byte[] cApdu = Utils.makeCAPDU(0x00, 0xA4, 0x04, 0x00, Data.AID_M4M_LTSM);
        byte[] recvData = new byte[1024];

        recvData = exchange(cApdu,channelId[channelCnt -1]);
        if(recvData == null)
        {
            Log.i(TAG, "SE transceive failed ");
            statusCode = Data.SW_SE_TRANSCEIVE_FAILED;
            stat = Data.FAILED;
        }
        Log.i(TAG, "Received Data : " + Utils.bytArrayToHex(recvData));

        if (Utils.getSW(recvData) == Data.SW_NO_ERROR)
        {
            Log.i(TAG, "ltsmSelect SUCCESS ");
            byte[] vcCounts = Utils.getVcCount(recvData);

            InitialVCEntry    =  vcCounts[0] << 8 | vcCounts[1];
            int maxVcCount  =  vcCounts[2] << 8 | vcCounts[3];

            Log.i(TAG, "maxVcCount " + maxVcCount);
            Log.i(TAG, "InitialVCEntry " + InitialVCEntry);
            available = Utils.checkRegistryAvail(context);
            if(!available){
                Registry = new ltsmRegistry(maxVcCount);
                saveRegistry(Registry);
            }
            else{
                Registry = loadRegistry();
                Registry.maxVcCount = maxVcCount;
            }

            stat = Data.SUCCESS;
        }else{
            statusCode = Utils.getSW(recvData);
            stat = Data.FAILED;
        }
        Log.i(TAG, "Exit");
        return stat;
    }

    /*******************************************************
     *
     *Handles Exchange of Data during LTSM Process Command
     *
     ********************************************************/

    private static int exchangeLtsmProcessCommand(byte[] cdata)
    {
        String TAG = "LTSMCommunicator:exchangeLtsmProcessCommand";
        int stat = Data.SW_PROCESSING_ERROR;
        Log.i(TAG, "Enter");
        byte[] rData = new byte[0];
        Log.i(TAG, "(byte)cdata.length : "+(byte)cdata.length + "new byte[] {(byte)cdata.length} :  " + Utils.bytArrayToHex(new byte[] {(byte)cdata.length}));
        byte[] cApdu = Utils.makeCAPDU(Data.CLA_M4M_LTSM, Data.INS_M4M_LTSM_PROCESS_COMMAND, 0x00, 0x00, cdata);
        Log.i(TAG, "cApdu : " + Utils.bytArrayToHex(cApdu));
        byte[] rapdu = exchange(cApdu,channelId[channelCnt -1]);
        switch(Utils.getSW(rapdu))
        {
        default:
        {
            statusCode = Utils.getSW(rapdu);
            stat = Data.SW_PROCESSING_ERROR;
            break;
        }

        case Data.SW_NO_ERROR:
            cApdu = Utils.getRDATA(rapdu);
            Log.i(TAG, "SW_NO_ERROR ");
            if(currentProcess.equals("CreateVirtualCard")){
                currentProcess = "";
                processCreateVcResponse(rapdu);
            }
            else if(currentProcess.equals("deleteVirtualCard")){
                currentProcess = "";
                processDeleteVcResponse();
            }
            else if(currentProcess.equals("addAndUpdateMdac")){
                currentProcess = "";
                rData = cApdu;
            }
            stat = Data.SW_NO_ERROR;
            statusCode = 0;
            break;
        case Data.SW_6310_COMMAND_AVAILABLE:
            process_se_response: for(;;){
                if (rapdu.length == 2)
                    return Data.SW_NO_ERROR;
                cApdu = Utils.getRDATA(rapdu);
                Log.i(TAG, "SW_6310_COMMAND_AVAILABLE: Send Data back to SE ");
                //             toLTSM = false;
                rapdu = exchangeWithSe(cApdu);
                if (rapdu.length < 256)
                {
                    cApdu = Utils.makeCAPDU(Data.CLA_M4M_LTSM, Data.INS_M4M_LTSM_PROCESS_SE_RESPONSE, 0x00, 0x80, rapdu);
                    //                    }
                    rapdu = exchange(cApdu,channelId[channelCnt -1]);

                    switch(Utils.getSW(rapdu))
                    {
                    default:
                    {
                        statusCode = Utils.getSW(rapdu);
                        stat = Data.SW_PROCESSING_ERROR;
                        break process_se_response;
                    }

                    case Data.SW_NO_ERROR:
                        Log.i(TAG, "SW_NO_ERROR ");
                        cApdu = Utils.getRDATA(rapdu);
                        if(currentProcess.equals("CreateVirtualCard")){
                            currentProcess = "";
                            processCreateVcResponse(rapdu);
                        }
                        else if(currentProcess.equals("deleteVirtualCard")){
                            currentProcess = "";
                            processDeleteVcResponse();
                        }
                        else if(currentProcess.equals("addAndUpdateMdac")){
                            currentProcess = "";
                            rData = cApdu;
                        }
                        stat = Data.SW_NO_ERROR;
                        statusCode = 0;
                        break process_se_response;

                    case Data.SW_6310_COMMAND_AVAILABLE:
                        Log.i(TAG, "SW_6310_COMMAND_AVAILABLE: Send Data back to SE Again");
                    }
                }
                else
                {
                    byte[] rapdu1 = Utils.extract(rapdu, 0, 255);
                    byte[] rapdu2 = Utils.extract(rapdu, 255, rapdu.length - 255);

                    // First part
                    cApdu = Utils.makeCAPDU(Data.CLA_M4M_LTSM, Data.INS_M4M_LTSM_PROCESS_SE_RESPONSE, 0x00, 0x00, rapdu1);
                    rapdu = exchange(cApdu,channelId[channelCnt -1]);

                    if (Utils.getSW(rapdu) != Data.SW_NO_ERROR)
                    {
                        System.err.println("FAILED: APDU exchange (PROCESS SE RESPONSE) (1 of 2) failed!");
                        System.err.println(String.format("LTSM-SW: %04X", Utils.getSW(rapdu)));
                        System.exit(1);
                    }

                    // Second part
                    cApdu = Utils.makeCAPDU(Data.CLA_M4M_LTSM, Data.INS_M4M_LTSM_PROCESS_SE_RESPONSE, 0x00, 0x80, rapdu2);
                    rapdu = exchange(cApdu,channelId[channelCnt -1]);

                    switch(Utils.getSW(rapdu))
                    {
                    default:
                    {
                        statusCode = Utils.getSW(rapdu);
                        stat = Data.SW_PROCESSING_ERROR;
                        break process_se_response;
                    }

                    case Data.SW_NO_ERROR:
                        cApdu = Utils.getRDATA(rapdu);
                        if(currentProcess.equals("CreateVirtualCard")){
                            currentProcess = "";
                            processCreateVcResponse(rapdu);
                        }
                        else if(currentProcess.equals("deleteVirtualCard")){
                            currentProcess = "";
                            processDeleteVcResponse();
                        }
                        else if(currentProcess.equals("addAndUpdateMdac")){
                            currentProcess = "";
                            rData = cApdu;
                        }
                        stat = Data.SW_NO_ERROR;
                        statusCode = 0;
                        break process_se_response;

                    case Data.SW_6310_COMMAND_AVAILABLE:
                        // FALL THROUGH
                    }
                }
            }
        }

        Log.i(TAG, "Exit");
        return stat;
    }

    /*******************************************************
     *
     *Exchange APDU With Secure Element
     *
     ********************************************************/

    private static byte[] exchangeWithSe(byte[] cdata)
    {
        String TAG = "LTSMCommunicator:exchangeWithSe";
        Log.i(TAG, "Enter");
        byte[] recvData = new byte[1024];
        boolean channel_open_cmd = false;
        if (cdata[1] == 0x70) // MANAGE CHANNEL ?
        {
            if (cdata[2] == 0x00) // [open]
            {
                cdata[4] = (byte) 0x01;
                channel_open_cmd = true;
                //      SEChannel = 1;
            }
            else{

            }
        }
        recvData = exchange(cdata,(cdata[0] & 0x03));
        if(channel_open_cmd == true){
        }
        return recvData;
    }

    /*******************************************************
     *
     *Common Exchange method for Exchange with SE and Process LTSM Command
     *
     ********************************************************/

    private static byte[] exchange(byte[] cApdu,int chnl_id) {
        String TAG = "LTSMCommunicator:exchange";
        Log.i(TAG, "Enter");
        byte[] rData = new byte[0];

        //     byte[] xchangeData = new byte[cApdu.length];
        try {

            cApdu[0] = Utils.adjustCLA(cApdu[0], (byte)chnl_id);

            Log.i(TAG, "Sending Data : " + Utils.bytArrayToHex(cApdu));
            rData = exchangeApdu(cApdu, false);
            if(rData.length == 0)
            {
                Log.i(TAG, "SE transceive failed ");
                rData = Utils.createStatusCode(Data.SW_NO_DATA_RECEIVED);
            }
            Log.i(TAG, " rData : " + Utils.bytArrayToHex(rData));
        } catch (RemoteException e) {
            closeLogicalChannel();
            e.printStackTrace();
        }
        return rData;
    }

    /*******************************************************
     *
     *Selects CRS during Activate VC
     *
     ********************************************************/

    private static int crsSelect() {
        String TAG = "LTSMCommunicator:crsSelect";
        byte[] rData = new byte[0];
        Log.i(TAG, "Enter");
        rData = exchangeWithSe(Data.selectCRS);
        //recvData = exchange(Data.selectCRS,channelId[channelCnt -1]);
        if(rData == null)
        {
            Log.i(TAG, "SE transceive failed ");
            rData = Utils.createStatusCode(Data.SW_NO_DATA_RECEIVED);
        }
        Log.i(TAG, "rData : " + Utils.bytArrayToHex(rData));
        if (Utils.getSW(rData) == (short)0x6300)
        {
            System.out.println("WARNING: The M4M LTSM Application is in DEBUG mode.");
        }
        else if (Utils.getSW(rData) == Data.SW_NO_ERROR)
        {
            Log.i(TAG, "ltsmSelectCRS SUCCESS ");
            return Data.SUCCESS;
        }
        else{
            return Data.FAILED;
        }
        Log.i(TAG, "Exit");
        return Data.FAILED;
    }

    /*******************************************************
     *
     *Closes currently opened Logical Channels
     *
     ********************************************************/

    public static void closeLogicalChannel() {
        String TAG = "LTSMCommunicator:closeLogicalChannel";
        Log.i(TAG, "Enter");
        byte[] rData = new byte[0];
        channelCnt = Utils.loadInt("channelCnt",context);
        channelId = Utils.loadArray("channelId",context);
        isopened = Utils.loadArray("isopened",context);

        Log.i(TAG, "channelCnt : " + channelCnt);

        byte[] send = new byte[5];
        int xx = 0;

        for(int cnt =0; (cnt < channelCnt); cnt++){
            if(isopened[cnt] == 1){
                xx = 0;
                Log.i(TAG, "Enter : isopened[cnt] == true");
                send[xx++] = (byte) channelId[cnt];
                send[xx++] = (byte) 0x70;
                send[xx++] = (byte) 0x80;
                send[xx++] = (byte) channelId[cnt];
                send[xx++] = (byte) 0x00;

                try {
                    rData = exchangeApdu(send, true);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }

                if(rData == null){
                    Log.i(TAG, "exchangeApdu FAILED");
                    rData = Utils.createStatusCode(Data.SW_NO_DATA_RECEIVED);
                }
                else if(rData[rData.length - 2] == (byte) 0x90 &&
                        rData[rData.length - 1] == (byte) 0x00){
                    Log.i(TAG, "Close Channe id : " + channelId[cnt] + "is SUCCESS");
                    channelCnt = channelCnt -1;
                }
                else{
                    Log.i(TAG, "Close Channe id : " + channelId[cnt] + "is Failed");
                }
            }
        }

        Utils.saveInt(channelCnt,"channelCnt",context);

        try {
            closeConnection();
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        Log.i(TAG, "Exit");
    }

    /*******************************************************
     *
     *Updates the VC Entry for current CreateVC
     *
     ********************************************************/

    private int managecurrentVcEntry(ltsmRegistry registry) {
        String TAG = "LTSMCommunicator:managecurrentVcEntry";
        Log.i(TAG, "Enter");
        Log.i(TAG, "Registry.vcCount : " + Registry.vcCount + "Registry.maxVcCount : " + Registry.maxVcCount);
        if(Registry.vcCount == 0){

            Registry.deleteVcEntries.clear();
            currentVcEntry = InitialVCEntry;

        }else if(Registry.vcCount < Registry.maxVcCount){
            Log.i(TAG, "vcCount "+Registry.vcCount);
            if(Registry.deleteVcEntries.size() != 0){
                currentVcEntry = Registry.deleteVcEntries.get(0);
                Registry.deleteVcEntries.remove(0);
                saveRegistry(Registry);
            }else{
                currentVcEntry = findVcEntryMax(Registry);//Check for max VC Entry Value
                currentVcEntry++;
            }
        }
        else{
            return Data.FAILED;
        }
        return Data.SUCCESS;
    }

    /*******************************************************
     *
     *Finds the maximum value in VC Entry List
     *
     ********************************************************/

    private int findVcEntryMax(ltsmRegistry registry) {
        int Max = 0;
        for(int i = 0;i<Registry.vcCount;i++){
            if(Registry.vcEntry.get(i) > Max){
                Max = Registry.vcEntry.get(i);
            }
        }
        return Max;
    }

    /*******************************************************
     *
     *Verifies Registry, Deletes VC and Updates the Registry for VC's Which are not Created Properly
     *
     ********************************************************/

    private void cleanUp(){
        String TAG = "LTSMCommunicator:cleanUp";
        Log.i(TAG, "Enter");
        boolean available = Utils.checkRegistryAvail(context);
        if(available){
            Registry = loadRegistry();
            int VC_Conut_temp;
            for(int i = 0;i<Registry.vcCount;i++){
                VC_Conut_temp = Registry.vcCount;
                if((Registry.vcCreateStatus.get(i)==false)){
                    try{
                        deleteTempVc(Registry.vcEntry.get(i).intValue(),Registry.shaWalletName.get(i));
                    }catch(Exception e){
                        closeLogicalChannel();
                        e.printStackTrace();
                    }
                    Registry = loadRegistry();
                    int j;
                    if(VC_Conut_temp == Registry.vcCount){
                        for(j = 0;j<Registry.deleteVcEntries.size();j++){
                            if(Registry.vcEntry.get(i) == Registry.deleteVcEntries.get(j)){
                                break;
                            }
                        }
                        if(j == Registry.deleteVcEntries.size()){
                            Registry.deleteVcEntries.add(Registry.vcEntry.get(i));
                        }

                        Registry.shaWalletName.remove(i);
                        Registry.walletName.remove(i);
                        Registry.vcCreateStatus.remove(i);
                        Registry.vcActivatedStatus.remove(i);
                        Registry.vcEntry.remove(i);
                        Registry.vcType.remove(i);
                        Registry.vcUid.remove(i);
                        Registry.vcCount = Registry.vcCount - 1;
                        saveRegistry(Registry);
                    }
                }
            }
        }
        Utils.exportReg();
        Log.i(TAG, "Exit");
    }

    /*******************************************************
     *
     *Deletes VC's which are not Created Properly
     *
     ********************************************************/

    private int deleteTempVc(int vcEntry, String pkg) {
        String TAG = "LTSMCommunicator:deleteTempVc";
        Log.i(TAG, "Enter");
        int stat = Data.FAILED;
        DeleteVCEntry = vcEntry;
        Log.i(TAG, "DeleteVCEntry = vcEntry : " + DeleteVCEntry);
        currentProcess = "deleteVirtualCard";
        byte[] cData = vcDescriptionFile.deleteVc(pkg,(short)vcEntry);

        stat = ltsmStart();
        if(stat == Data.FAILED){
            Log.i(TAG, "DeleteVC Failed");
            return Data.FAILED;
        }
        stat = exchangeLtsmProcessCommand(cData);

        closeLogicalChannel();
        Log.i(TAG, "Exit");
        return stat;
    }

    /*******************************************************
     *
     *Handles Personalisation During CreateVC
     *
     *******************************************************/

    private int handlePersonalize(byte[] vcData, String pkg, short intValue) {
        String TAG = "LTSMCommunicator:handlePersonalize";
        int stat = Data.SW_PROCESSING_ERROR;
        Log.i(TAG, "Enter");
        currentProcess = "handlePersonalize";
        byte[] cData = vcDescriptionFile.CreatePersonalizeData(vcData,pkg,Utils.createSha(pkg),intValue,context);
        Log.i(TAG, "cData : " + Utils.bytArrayToHex(cData));
        stat = exchangeLtsmProcessCommand(cData);
        return stat;
    }

    /*******************************************************
     *
     *Opens Connection for Communication with SE
     *
     ********************************************************/

    public static boolean openConnection() throws RemoteException {
        String TAG = "LTSMCommunicator:openConnection";
        Log.i(TAG, "Enter");
        String errorCause = "";

        try {

            bundle = new Bundle();
            try {
                mLtsmService = LtsmService.createLtsmServiceInterface();
                errorCause = "Open Secure Element: Failed";
                if(mLtsmService != null){
                    bundle = mLtsmService.open("com.nxp.ltsm.ltsmclient",mBinder);
                }
                Log.i(LOG_TAG, "openConnection() to ESe: Success Bundle : " + bundle);
                if (bundle == null) {
                    Log.i(LOG_TAG,"openConnection() not successful");
                }

            } catch (Exception e) {
                Log.i(LOG_TAG, "openConnection() not successful");

            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /*******************************************************
     *
     *Opens Connection for Communication with SE
     *
     ********************************************************/

    public static boolean closeConnection() throws RemoteException {
        // TODO Auto-generated method stub
        String TAG = "LTSMCommunicator:closeConnection";
        Log.i(TAG, "Enter");
        String errorCause = "";

        try {

            bundle = new Bundle();

            //mBinder = new Binder();
            try {
                if(mLtsmService != null){
                    bundle = mLtsmService.close("com.nxp.ltsm.ltsmclient",mBinder);
                }
                Log.i(LOG_TAG, "closeConnection() to ESe: Success Bundle : " + bundle);
                if (bundle == null) {
                    Log.i(LOG_TAG,"closeConnection() not successful");
                }

            } catch (Exception e) {
                Log.i(LOG_TAG, "closeConnection() not successful");

            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /*******************************************************
     *
     *Transceives the APDU
     *
     ********************************************************/

    public static byte[] exchangeApdu(byte[] apdu, boolean addLeByte) throws RemoteException {

        byte[] apduToSend = null;
        Log.i(LOG_TAG,"exchange APDU ENTER");
        if (addLeByte) {
            // add a additional 0x00 to the end of the apdu
            apduToSend = new byte[apdu.length + 1];
            System.arraycopy(apdu, 0, apduToSend, 0, apdu.length);
            apduToSend[apduToSend.length - 1] = (byte) 0x00;
        } else {
            apduToSend = apdu;
        }
        try {
            if(mLtsmService != null){
                bundle = mLtsmService.transceive("com.nxp.ltsm.ltsmclient",apdu);
            }
            if (bundle == null) {
                Log.i(LOG_TAG,"exchange APDU failed");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return bundle.getByteArray("out");
    }

    /*******************************************************
     *
     * Handles transceives for activate/deactivate VC
     *
     ********************************************************/
    public byte[] gating(byte[] aid, boolean mode){
        String TAG = "LTSMCommunicator:gating";
        Log.i(TAG, "Enter");
        byte[] tmpAid = new byte[]{};
        byte[] cData = new byte[]{};
        int tmpVcEntry = 99,i=0,stat = Data.FAILED;
        tmpAid = Utils.extract(aid, 2, aid.length-4);

        Registry = loadRegistry();
        if(Arrays.equals(tmpAid, Data.ACTV_LTSM_AID)){
            for( i = 0;i<Registry.vcCount;i++){
                if(Registry.vcEntry.get(i).intValue() == Utils.getSW(aid)){
                    tmpVcEntry = Utils.getSW(aid);
                    break;
                }
            }
            if (i == Registry.vcCount) {
                tmpVcEntry = 99;
            }
        }
        if (mode == true) {
            cData = Utils.makeCAPDU((byte) 0x80, (byte) 0xF0, 0x01, (byte) 0x01, aid);
        }else{
            cData = Utils.makeCAPDU((byte) 0x80, (byte) 0xF0, 0x01, (byte) 0x00, aid);
        }

        //Activate VC
        Registry = loadRegistry();
        byte[] rcvData = exchangeWithSe(cData);
        stat = processGatingResponse(tmpVcEntry,mode,rcvData);
        if (stat == Data.SW_OTHER_ACTIVEVC_EXIST) {
            return rcvData;
        }
        else if (stat == Data.SUCCESS) {
            return Utils.createStatusCode((short)Data.SUCCESS);
        }
        else{
            return Utils.createStatusCode(statusCode);
        }

    }



    /*******************************************************
     *
     *Processes Create VC Response
     *
     ********************************************************/

    private static void processCreateVcResponse(byte[] rapdu) {
        String TAG = "processCreateVcResponse";
        Log.i(TAG, "Enter");
        //   String pkg = Utils.getCallingAppPkg(context);
        Log.i(TAG, "CurrentPackage : " + CurrentPackage);
        int i;
        for(i = 0;i<Registry.vcCount;i++){
            if((Registry.vcEntry.get(i) == currentVcEntry)&&(Utils.createSha(CurrentPackage).equals(Registry.shaWalletName.get(i))))
            {
                break;
            }
        }

        byte[] Value = Utils.getValue((byte)0x41,rapdu);
        Log.i(TAG, "After byte[] Value");
        Registry = loadRegistry();
        Log.i(TAG, "After Registry = loadRegistry()");
        Registry.vcUid.set(i, Utils.bytArrayToHex(Value));
        Registry.vcCreateStatus.set(i, true);
        saveRegistry(Registry);
    }

    /*******************************************************
     *
     *Processes Delete VC Response
     *
     ********************************************************/

    private static void processDeleteVcResponse() {
        String TAG = "processDeleteVcResponse";
        Log.i(TAG, "Enter");
        Registry = loadRegistry();
        for(int i = 0;i<Registry.vcCount;i++){
            Log.i(TAG, "Registry.vcEntry.get(i) "+Registry.vcEntry.get(i));
            if(Registry.vcEntry.get(i) == DeleteVCEntry){
                Registry.shaWalletName.remove(i);
                Registry.walletName.remove(i);
                Registry.vcCreateStatus.remove(i);
                Registry.vcActivatedStatus.remove(i);
                Registry.vcEntry.remove(i);
                Registry.vcType.remove(i);
                Registry.vcUid.remove(i);
                Registry.vcCount = Registry.vcCount - 1;
                Registry.deleteVcEntries.add(DeleteVCEntry);
                Log.i(TAG, "Inside if");
                saveRegistry(Registry);
                break;
            }
        }
        statusCode = Data.SW_NO_ERROR;
        Log.i(TAG, "Exit");
    }

    /*******************************************************
     *
     * Processes activate/deactivate VC response
     *
     ********************************************************/
    public int processGatingResponse(int vcEntry,  boolean mode, byte[] rcvData){
        String TAG = "LTSMCommunicator:processGatingResponse";
        Log.i(TAG, "Enter");
        statusCode = 0;
        int stat = Data.FAILED;
        Registry = loadRegistry();
        int pos = 0;
        Log.i(TAG, "vcEntry : " +vcEntry );
        for( pos = 0;pos<Registry.vcCount;pos++){
            if(Registry.vcEntry.get(pos).intValue() == vcEntry){
                break;
            }
        }
        //Successfull Activation or Deactivation
        if (Utils.getSW(rcvData) == Data.SW_NO_ERROR)
        {
            if(mode == true){
                Log.i(TAG, "ActivateVirtualCard SUCCESS");
                if(pos < Registry.vcCount){
                    Registry.vcActivatedStatus.set(pos,true);
                }
                stat =  Data.SUCCESS;
            }
            else{
                Log.i(TAG, "DeActivateVirtualCard SUCCESS");
                if(pos < Registry.vcCount){
                    Registry.vcActivatedStatus.set(pos,false);
                }
                stat =  Data.SUCCESS;
            }
        }
        //Already one VC is Activated from other Wallet App
        else if((Utils.getSW(rcvData) == Data.SW_OTHER_ACTIVEVC_EXIST))
        {
            stat =  Data.SW_OTHER_ACTIVEVC_EXIST;

        }
        else{
            statusCode = Utils.getSW(rcvData);
            stat = Data.FAILED;
        }
        saveRegistry(Registry);
        return stat;

    }

    /*******************************************************
     *
     *Processes Read Mifare Classic Data Response
     *
     ********************************************************/

    private byte[] processReadMifareDataResponse(byte[] rapdu) {
        statusCode = 0;
        statusCode = Utils.getSW(rapdu);
        byte[] retdata = new byte[1024];

        if(Utils.getSW(rapdu) == Data.SW_NO_ERROR){
            byte[] cApdu = Utils.getRDATA(rapdu);
            retdata = Utils.append(cApdu, Utils.createStatusCode(statusCode));
        }else{
            retdata = Utils.createStatusCode(statusCode);
        }
        return retdata;
    }

    /*******************************************************
     *
     *Check Registry whether all the entries matches with VC Count
     *
     ********************************************************/

    private int check(ltsmRegistry registry) {
        String TAG = "LTSMCommunicator:check";
        Log.i(TAG, "Enter" +registry.shaWalletName.size()+
                " "+registry.vcActivatedStatus.size()+
                " "+registry.vcCreateStatus.size()+
                " "+registry.vcEntry.size()+
                " "+registry.vcType.size()+
                " "+registry.vcUid.size());
        if((registry.vcCount == registry.shaWalletName.size())&&
                (registry.vcCount == registry.vcActivatedStatus.size())&&
                (registry.vcCount == registry.vcCreateStatus.size())&&
                (registry.vcCount == registry.vcEntry.size())&&
                (registry.vcCount == registry.vcType.size())&&
                (registry.vcCount == registry.vcUid.size())){
            return Data.SUCCESS;
        }
        else{
            return Data.FAILED;
        }
    }

    /*******************************************************
     *
     *Loads LTSM Registry from SharedPreference
     *
     ********************************************************/

    public static ltsmRegistry loadRegistry() {
        String TAG = "LTSMCommunicator:loadRegistry";
        Log.i(TAG, "Enter");
        SharedPreferences prefs = context.getSharedPreferences("Reg", Context.MODE_PRIVATE);
        Gson gson = new Gson();
        String json = prefs.getString("ltsmRegistry", "");
        ltsmRegistry RegObj = gson.fromJson(json, ltsmRegistry.class);
        Log.i(TAG, "Exit");
        return RegObj;
    }

    /*******************************************************
     *
     *Saves LTSM Registry to SharedPreference
     *
     ********************************************************/

    public static void saveRegistry(ltsmRegistry Registry) {
        SharedPreferences.Editor prefsEditor=context.getSharedPreferences("Reg", Context.MODE_PRIVATE).edit();
        Gson gson = new Gson();
        String json = gson.toJson(Registry);
        prefsEditor.putString("ltsmRegistry", json);
        if(Registry.vcCount==10){
            prefsEditor.clear();
        }
        prefsEditor.commit();
    }

    /*******************************************************
     *
     *Check and Process Uninstalled Wallet Apps
     *
     ********************************************************/

    private  int checkUninstalledApps(){
        String TAG = "LTSMCommunicator:checkUninstalledApps";
        Log.i(TAG, "Enter");
        List<PackageInfo> InstalledApps = Utils.getINstalledApps(context);
        List<String> UninstalledApps = new ArrayList<String>();
        Registry = loadRegistry();
        int i,j;
        /*
         * Checking Registry entry against Currently installed app entries
         * */
        for(i = 0; i < Registry.vcCount; i++){
            for(j = 0; j < InstalledApps.size(); j++){
                if(InstalledApps.get(j).packageName.equals(Registry.walletName.get(i).toString())){
                    Log.i(TAG, "CONTAINS : " + Registry.walletName.get(i).toString());
                    break;
                }
            }
            /*
             * If Current Registry entry is not found in Currently installed app entries
             * */
            if(j == InstalledApps.size()){
                UninstalledApps.add( Registry.walletName.get(i).toString());
                Log.i(TAG, "inside if(j == InstalledApps.size()){");
            }
        }
        /*
         * Delete VCs which are in Uninstalled List
         * */
        if(!UninstalledApps.isEmpty()){
            for(i = 0; i < UninstalledApps.size(); i++){
                for(j = 0; j < Registry.vcCount; j++){
                    if(UninstalledApps.get(i).equals(Registry.walletName.get(j).toString())){
                        deleteTempVc(Registry.vcEntry.get(j).intValue(),Registry.shaWalletName.get(j));
                    }
                }
            }
            Log.i(TAG, "Exit");
            return Data.SUCCESS;
        }
        else{
            Log.i(TAG, "Exit");
            return Data.FAILED;
        }
    }

    /*******************************************************
     *
     *Parse 61 Tag data and Update the registry accordingly
     *
     ********************************************************/
    private byte[] processGetStatus(byte[] apdu){
        String TAG = "LTSMCommunicator:processGetStatus";
        Log.i(TAG, "Enter");
        int arrLen = apdu.length;
        byte[] currentApdu = apdu;
        byte[] vcEntryList = new byte[]{};
        int progressLen = 0, offset = 0, lenTag61 = 0, lenTag40 = 0, lenTag4F = 0, lenTag9F70 = 0, lenTag42 = 0, lenTag43 = 0,i =0;

        if (apdu[0]!=(byte)0x61) {
            Log.i(TAG, "Tag 0x61 Not Found");
            return Utils.createStatusCode(Data.SW_PROCESSING_ERROR);
        }
        boolean tag61Exist = true;

        Log.i(TAG, "currentApdu : " + Utils.bytArrayToHex(currentApdu));
        while (tag61Exist) {

            lenTag61 = Utils.lenOfTLV(currentApdu, 1);
            offset = Utils.skipLenTLV(currentApdu, 1)+1;
            currentApdu = Utils.extract(currentApdu, offset, currentApdu.length-offset);
            progressLen = progressLen + offset;
            Log.i(TAG, "currentApdu tag61Exist : " + Utils.bytArrayToHex(currentApdu));
            Log.i(TAG, "progressLen : " + progressLen);

            /*Check and process tag 0x40*/
            if (currentApdu[0]!=(byte)0x40) {
                Log.i(TAG, "Tag 0x40 Not Found");
                return Utils.createStatusCode(Data.SW_PROCESSING_ERROR);
            }
            lenTag40    = 2;
            offset      = 2;
            vcEntryList = Utils.append(vcEntryList, Utils.extract(currentApdu, offset, lenTag40));
            currentApdu = Utils.extract(currentApdu, offset+lenTag40, currentApdu.length-(offset+lenTag40));
            progressLen = progressLen + offset+lenTag40;


            /*Check and process tag 0x4F*/
            if (currentApdu[0]!=(byte)0x4F) {
                Log.i(TAG, "Tag 0x4F Not Found");
                return Utils.createStatusCode(Data.SW_PROCESSING_ERROR);
            }
            lenTag4F    = Utils.lenOfTLV(currentApdu, 1);
            offset      = Utils.skipLenTLV(currentApdu, 1)+1;
            currentApdu = Utils.extract(currentApdu, offset+lenTag4F, currentApdu.length-(offset+lenTag4F));
            progressLen = progressLen+offset+lenTag4F;


            /*Check and process tag 0x9F70*/
            if ((currentApdu[0]!=(byte)0x9F)&&(currentApdu[1]!=(byte)0x70)) {
                Log.i(TAG, "Tag 0x9F70 Not Found");
                return Utils.createStatusCode(Data.SW_PROCESSING_ERROR);
            }
            lenTag9F70  = 1;
            offset      = 3;
            vcEntryList = Utils.append(vcEntryList, Utils.extract(currentApdu, offset, lenTag9F70));
            currentApdu = Utils.extract(currentApdu, offset+lenTag9F70, currentApdu.length-(offset+lenTag9F70));
            progressLen = progressLen+offset+lenTag9F70;

            if (currentApdu.length!=0) {
                Log.i(TAG, "currentApdu 0x42 : " + Utils.bytArrayToHex(currentApdu));
                /*Check and process tag 0x42*/
                if (currentApdu[0] == (byte)0x42) {
                    lenTag42    = Utils.lenOfTLV(currentApdu, 1);
                    offset      = Utils.skipLenTLV(currentApdu, 1)+1;
                    currentApdu = Utils.extract(currentApdu, offset+lenTag42, currentApdu.length-(offset+lenTag42));
                    progressLen = progressLen+offset+lenTag42;
                }

                /*Check and process tag 0x43*/
                if (currentApdu[0] == (byte)0x43) {
                    Log.i(TAG, "currentApdu 0x43 : " + Utils.bytArrayToHex(currentApdu));
                    lenTag43    = Utils.lenOfTLV(currentApdu, 1);
                    offset      = Utils.skipLenTLV(currentApdu, 1)+1;
                    currentApdu = Utils.extract(currentApdu, offset+lenTag43, currentApdu.length-(offset+lenTag43));
                    progressLen = progressLen+offset+lenTag43;

                }
            }
            Log.i(TAG, "progressLen : " + progressLen + "arrLen : " + arrLen );
            /*break if no more tag 61 available*/
            if (progressLen == arrLen) {
                Log.i(TAG, "progressLen == arrLen");
                tag61Exist = false;
            }
            else if (currentApdu[0] != (byte)0x61) {
                return Utils.createStatusCode(Data.SW_PROCESSING_ERROR);
            }
        }
        Log.i(TAG, "vcEntryList : " + Utils.bytArrayToHex(vcEntryList));
        Registry = loadRegistry();

        for( i = 0;i<Registry.vcCount;i++){
            Log.i(TAG, "i : " + i + "Registry.vcCount :" + Registry.vcCount);
            if (findVcEntry(Registry.vcEntry.get(i),vcEntryList)==false) {
                int tempVc = Registry.vcEntry.get(i);
                Log.i(TAG, "i : " + i + " tempVc : " + tempVc);
                Registry = loadRegistry();
                Registry.shaWalletName.remove(i);
                Registry.walletName.remove(i);
                Registry.vcCreateStatus.remove(i);
                Registry.vcActivatedStatus.remove(i);
                Registry.vcEntry.remove(i);
                Registry.vcType.remove(i);
                Registry.vcUid.remove(i);
                Registry.vcCount = Registry.vcCount - 1;
                if (tempVc <= Registry.maxVcCount) {
                    Registry.deleteVcEntries.add(tempVc);
                }
                //Log.i(TAG, "Removing registry : " + Registry.vcEntry.get(i));
                saveRegistry(Registry);
                Registry = loadRegistry();
                if (i==Registry.vcCount) {
                    break;
                }
            }
            Log.i(TAG, "After");
        }

        return Utils.createStatusCode(Data.SW_NO_ERROR);
    }

    /*******************************************************
     *
     *Check for VC Entry in Registry
     *
     ********************************************************/

    boolean findVcEntry(int vcEntry, byte[] vcEntryList ){
        String TAG = "LTSMCommunicator:findVcEntry";
        Log.i(TAG, "Enter");
        Registry = loadRegistry();
        byte[] vcEntryArr =new byte[2];
        int i,j;
        for(i = 0; i < vcEntryList.length; i = i+3){
            vcEntryArr = Utils.extract(vcEntryList, i, 2);
            Log.i(TAG, "byteArrayToShort : " + Utils.byteArrayToShort(vcEntryArr) + "vcEntry : " + (short)vcEntry);
            if ((short)vcEntry == Utils.byteArrayToShort(vcEntryArr)) {
                if ((vcEntryList[i+2]&(byte)0x03)!= (byte)0x03) {
                    loop:  for( j = 0;j<Registry.vcCount;j++){
                        if (Registry.vcEntry.get(j) == vcEntry) {
                            Registry.vcCreateStatus.set(j, false);
                            saveRegistry(Registry);
                            closeLogicalChannel();
                            cleanUp();
                            ltsmOpenChannel();
                            break loop;
                        }
                    }
                }
                Log.i(TAG, "Exit");
                return true;
            }
        }
        Log.i(TAG, "Exit");
        return false;
    }

    /*******************************************************
     *
     *Parse 61 Tag data and deactivate the AIDs present in
     *TAG A0 and TAG A2
     *
     ********************************************************/
    private int parseSetStatusResp(byte[] RcvData)
    {
        int tmpStat =0;
        int sReceivedLen = RcvData.length;

        if((sReceivedLen != 0) )
        {
            if(RcvData[0]== (byte)0x61)
            {
                /*Fetch size of length field of Tag 61 is 2 bytes*/
                int sLen61 = Utils.skipLenTLV(RcvData, 1);

                /*Get Length of Tag 61*/
                int sTag61Len = Utils.lenOfTLV(RcvData, 1);

                /*Check Tag61 length if Non-zero*/
                if(sTag61Len != 0)
                {
                    if(RcvData[sLen61+1] == (byte)0x4F)
                    {
                        int sLenConfAID = RcvData[sLen61+2];
                        int offA1 = 0;
                        int currAidLen = 0;
                        int tempLen = 0;
                        int tempAidLen = 0;
                        byte[] resp;
                        //    byte[] retStat=Data.SW_PROCESSING_ERROR;

                        if(RcvData[sLen61+ sLenConfAID +3] == (byte)0xA0)
                        {
                            int sLenA0 = Utils.lenOfTLV(RcvData, (sLenConfAID+sLen61+4));
                            int skipA0Len = Utils.skipLenTLV(RcvData, (sLenConfAID+sLen61+4));
                            tempLen = sLenA0;

                            int off4F = skipA0Len+sLenConfAID+sLen61+4;



                            do
                            {
                                if(RcvData[currAidLen+off4F] == (byte)0x4F)
                                {
                                    tempAidLen = RcvData[currAidLen+off4F+1];

                                    byte[] aid = Utils.findNextAID(RcvData, (currAidLen+off4F+1));
                                    /*Activate the requested VC entry*/
                                    resp = gating(aid,false);



                                    /*Do response validation
                                    tmpStat = processGatingResponse(resp,false,0);*/

                                    /*if(tmpStat == Data.SUCCESS){
                                retStat = Data.NO_ERROR;
                            }else{
                                retStat = Data.Processing_Error;
                            }*/
                                    /*To Reach 4F TAG*/
                                    currAidLen += tempAidLen+ 2;

                                }
                                else
                                {
                                    /*Error case for 4F tag*/
                                    break;
                                }
                            }while(tempLen > currAidLen);

                            /*Get offset of TAG A1 offA1*/
                            offA1 = sLenConfAID+sLen61+4+skipA0Len+sLenA0;
                        }
                        else
                        {
                            offA1 = sLen61+ sLenConfAID +3;
                        }
                        int offA2 = 0;

                        if(RcvData[offA1] == (byte)0xA1)
                        {
                            int lenTagA1 = Utils.lenOfTLV(RcvData, offA1+1);

                            int skipLenTagA1 = Utils.skipLenTLV(RcvData, offA1+1);

                            offA2 = offA1+ skipLenTagA1+lenTagA1+1;
                        }
                        else
                        {
                            /*since TAG presence is conditional*/
                            offA2 = offA1;
                        }

                        if(RcvData[offA2] == (byte)0xA2)
                        {
                            int lenTagA2 = Utils.lenOfTLV(RcvData, offA2+1);
                            int skipLenTagA2 = Utils.skipLenTLV(RcvData, offA2+1);

                            int offTag48 = offA2+skipLenTagA2+1;

                            tempLen = lenTagA2;

                            do{

                                if(RcvData[offTag48] == 0x48)
                                {
                                    int offTag484F = offTag48+4;

                                    currAidLen = 0;

                                    tempAidLen = 0;

                                    do
                                    {
                                        if(RcvData[currAidLen+offTag484F] == 0x4F)
                                        {
                                            tempAidLen = RcvData[currAidLen+offTag484F+1];

                                            byte[] aid = Utils.findNextAID(RcvData, (currAidLen+offTag484F+1));

                                            /*Activate the requested VC entry*/
                                            resp = gating(aid,false);

                                            /*Do response validation
                                            tmpStat = processGatingResponse(resp,false,0);*/

                                            /*if(tmpStat == Data.SUCCESS){
                                        retStat = Data.NO_ERROR;
                                    }else{
                                        retStat = Data.Processing_Error;
                                    }*/

                                            /*To Reach 4F TAG*/
                                            currAidLen += tempAidLen+2;
                                        }
                                        else
                                        {
                                            /*End of Tag 4F*/
                                            break;
                                        }
                                        tempLen -=(tempAidLen+2);

                                    }while(tempLen>currAidLen);

                                    /*Skip the current 4F tags of particular 48 Tag*/

                                }
                                tempLen-=4;
                                /*fetch next 48 tag*/
                                offTag48 +=  currAidLen +4;

                            }while(tempLen>0);

                        }
                        else
                        {
                            /*skip if Tag A2 is also not available*/
                        }
                    }
                    else
                    {
                        /*If Tag A0 is optional*/
                    }
                }
            }
        }
        else
        {

        }
        return tmpStat;
    }


}/*NEW IMPLEMENTATION END*/
