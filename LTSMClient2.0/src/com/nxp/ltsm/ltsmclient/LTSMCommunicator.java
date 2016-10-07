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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.binary.Hex;

import android.R.bool;
import android.R.integer;
import android.R.string;
import android.content.Context;
import android.content.Intent;
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
import com.nxp.ltsm.ltsmclient.tools.TLV;

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
    private static LtsmService mLtsmService = null;
    private static String currentProcess = "";
    static ltsmRegistry Registry;
    static int InitialVCEntry = 0;
    static int currentVcEntry = 0;
    static int DeleteVCEntry  = 99;
    static short statusCode   = 0;
    static byte[] vcUid = new byte[]{};
    private static boolean previousConcurrentActivationMode = true;

    public LTSMCommunicator(Context context) {
        LTSMCommunicator.context = context;
        try {
            mLtsmService = LtsmService.createLtsmServiceInterface();
        } catch (Exception e) {
                Log.i(LOG_TAG, "retrieving LtsmService failed");
                e.printStackTrace();
            }


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
        String currentWalletPkg = "";
        int status = Data.SW_REFERENCE_DATA_NOT_FOUND,rStatus;
        TLV tlvResult = null;
        TLV tlvResultF8 = null;
        TLV tlvResultE2 = null;
        boolean personalize = false;
        boolean saveRegistry = true;
        boolean addedintoRegistry = false;
        short vcEntry = 0;
        Log.i(TAG, "vcData : " + Utils.bytArrayToHex(vcData));

        try{
            createVcProcedure :
            {
            currentWalletPkg = getCallingAppPkg(context);
            boolean available = Utils.checkRegistryAvail(context);
            if(!available){
                available = Utils.checkRegBackupAvail();
                if(available){
                    Utils.importReg();
                }else{
                    Log.i(TAG, "no backup available");
                    Registry = new ltsmRegistry();
                }
            }

            // Initiate the LTSM. Open Logical Channel and Select LTSM
            rStatus = ltsmStart();
            if(rStatus != Data.SW_NO_ERROR){
                Log.i(TAG, "LTSMStart Failed");
                status = rStatus;
                break createVcProcedure;
            }

            if(available){
                //If Registry is already available in SharedPreference Check for Uninstalled Wallet Apps
                rStatus = checkUninstalledApps();
                if(rStatus == Data.SW_NO_ERROR){
                    Log.i(TAG, "Uninstalled App Found");
                }
           }

            /*get Virtual Card entry number*/
            cData = VCDescriptionFile.GetVC();
            byte[] rapdu = exchangeLtsmProcessCommand(cData);
            Log.i(TAG, "cData : " + Utils.bytArrayToHex(cData));

            List<TLV> listRapdu = TLV.parse(Utils.getRDATA(rapdu));
            tlvResult = TLV.find(listRapdu, 0x44); /*Tag contains VCEntry No. which is free */
            if(Utils.byteArrayToShort(tlvResult.getValue()) != Data.SW_NO_ERROR)
            {
                Log.i(TAG, "VCEntry Error : " + Utils.bytArrayToHex(tlvResult.getValue()));
                status = Utils.byteArrayToInt(tlvResult.getValue());
                break createVcProcedure;
            }

            tlvResult = TLV.find(listRapdu, 0x40);

            //check duplicate Registry Entry
            for(int i = 0; i < Registry.walletName.size(); i++){
                if(Registry.walletName.get(i).toString().equals(currentWalletPkg)){
                    //duplicate Registry Entry
                    saveRegistry = false;
                    break;
                }
            }
            if(saveRegistry == true){
                Registry.walletName.add(currentWalletPkg);
                Registry.walletName_hash.add(Utils.createSha(currentWalletPkg));
                saveRegistry(Registry);
                addedintoRegistry = true;
            }

            vcEntry =Utils.byteArrayToShort(tlvResult.getValue());
            if(personalizeData != null){
                rStatus = handlePersonalize(personalizeData,
                        currentWalletPkg,
                        vcEntry);

                if(rStatus  != Data.SW_NO_ERROR){
                    Log.i(TAG, "Error Personalization : " + Utils.createStatusCode(statusCode));
                    status = rStatus;
                    break createVcProcedure;
                }
            }
            byte[] spData=null;
            byte[] mfdfaid = null;
            byte[] cltecap = null;
            //check for that f8 tag
            List<TLV> tag = TLV.parse(vcData,new int[]{0xA8,0xE2,0xF8});
            tlvResultF8 = TLV.find(tag, Data.TAG_MF_DF_AID);
            tlvResultE2 = TLV.find(tag, Data.TAG_MF_AID);
            // Strip E2/F8 tag (when present)
            vcData = removeTags(vcData, 0xE2, 0xF8);
            if(tlvResultE2 != null || tlvResultF8 !=null)
            {
                if(tlvResultF8!=null)
                {
                    mfdfaid = tlvResultF8.getValue();
                }
                if(tlvResultE2!=null)
                {
                    cltecap = tlvResultE2.getValue();
                }
                Log.i(TAG, "E2/F8 Present : " );
                cData =VCDescriptionFile.createF8Vc( Utils.createSha(currentWalletPkg),
                        vcEntry,mfdfaid,cltecap);
                rData = exchangeLtsmProcessCommand(cData);
                status = Utils.getSW(rData);
                if(status != Data.SW_NO_ERROR)
                {
                    rData = Utils.createStatusCode((short)status);
                    return rData;
                }
            }
            //currentProcess used during Process Response if CreateVC is Success
            currentProcess = "CreateVirtualCard";

            cData = VCDescriptionFile.createVc(vcData,                                                      //vcData
                    Utils.createSha(currentWalletPkg),      //Sha of the SP Application
                    vcEntry);                     //VC Entry

            Log.i(TAG, "cData : " + Utils.bytArrayToHex(cData));
            rData = exchangeLtsmProcessCommand(cData);

            status = Utils.getSW(rData);
            }
        /*createVcProcedure Ends*/

        if(status == Data.SW_NO_ERROR)
        {
            processCreateVcResponse(rData);
            rData = VCDescriptionFile.CreateVCResp(vcEntry,
                    vcUid);
        }
        else
        {
            rData = Utils.createStatusCode((short)status);
        }

        Log.i(TAG, "CreateVCResp : " + Utils.bytArrayToHex(rData));
        Log.i(TAG, "Exit");
        return rData;
        }
        catch(Exception e)
        {
            e.printStackTrace();
            return Utils.createStatusCode(Data.SW_EXCEPTION);
        }
        finally
        {
            //Delete VC with Create Status False in the Registry.
           if(status != Data.SW_NO_ERROR && addedintoRegistry == true)
            {
                Registry.walletName.remove(Registry.walletName.size()-1);
                Registry.walletName_hash.remove(Registry.walletName_hash.size()-1);
                saveRegistry(Registry);
            }
            Utils.exportReg();
            closeLogicalChannel();
        }

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
        int status,rStatus;
        String currentWalletPkg;
        String currentWalletPkg_hash;
        statusCode = 0;
        byte[] rData = new byte[0];
        boolean match_found = false;
        try
        {
            deleteVcProcedure :
            {
            /* On runtime if we try to get calling app pkg name,
             * sometime it is possible that some other app may come to foreground which will give wrong data
             * So get calling app pkg name as soon as it enters the method
             * */
            currentWalletPkg = getCallingAppPkg(context);
            currentWalletPkg_hash = Utils.createSha(currentWalletPkg);

            boolean available = Utils.checkRegistryAvail(context);
            if(!available){
                available = Utils.checkRegBackupAvail();
                if(available)
                {
                    Utils.importReg();
                }
                else
                {
                    Log.i(TAG, "no backup available");
                    status = Data.SW_REGISTRY_IS_EMPTY;
                    break deleteVcProcedure;
                }
            }
            Registry = loadRegistry();

            currentProcess = "deleteVirtualCard";

            rStatus = ltsmStart();
            if(rStatus != Data.SW_NO_ERROR){
                Log.i(TAG, "LTSMStart Failed");
                status = rStatus;
                break deleteVcProcedure;
            }

            byte[] cData = VCDescriptionFile.deleteVc(Utils.createSha(currentWalletPkg),(short)vcEntry);

            rData = exchangeLtsmProcessCommand(cData);
            status = Utils.getSW(rData);

            /*If status is success then remove the entry from registry*/
            if(status == Data.SW_NO_ERROR)
            {
                byte[] getStatusRsp =getStatus(null);
                List<TLV> getStatusRspTlvs = TLV.parse(Utils.getRDATA(getStatusRsp));
                if(getStatusRspTlvs!=null){
                    for(TLV tlv: getStatusRspTlvs)
                    {
                        TLV tlvVCx = TLV.find(tlv.getNodes(), Data.TAG_VC_ENTRY);
                        TLV tlvApkId = TLV.find(tlv.getNodes(), Data.TAG_SP_AID);
                        byte ApkId[] = tlvApkId.getValue();
                        byte regApkId[] = Hex.decodeHex(currentWalletPkg_hash.toCharArray());
                        Log.i(TAG, "SEApkId : " + Utils.bytArrayToHex(ApkId));
                        Log.i(TAG, "regApkId : " + Utils.bytArrayToHex(regApkId));
                        if (Arrays.equals(ApkId,regApkId)){
                            match_found = true;
                            break;
                        }
                    }
                }
                if(!match_found){
                    for(int i=0;i<Registry.walletName.size();i++){
                        if(Registry.walletName.get(i).toString().equals(currentWalletPkg)){
                            Registry.walletName.remove(i);
                            Registry.walletName_hash.remove(i);
                            saveRegistry(Registry);
                            break;
                        }
                    }
                }
            }
            status = Utils.getSW(rData);
            }
        /*deleteVcProcedure Ends*/

        Log.i(TAG, "deleteVirtualCard : " + Utils.bytArrayToHex(Utils.createStatusCode((short)status)));
        Log.i(TAG, "Exit");

        return Utils.createStatusCode((short)status);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            return Utils.createStatusCode(Data.SW_EXCEPTION);
        }
        finally
        {
            closeLogicalChannel();
            Utils.exportReg();
        }
    }

    /*******************************************************
     *
     *  Responsible to retrieve the activation state of a VC
     *
     *@param    VC entry number
     *
     *@return   Status word informing success or failure
     *
     ********************************************************/

    @Override
    public synchronized byte[] getVirtualCardStatus(int vcEntry) throws RemoteException {
        String TAG = "LTSMCommunicator:getVirtualCardStatus";
        Log.i(TAG, "Enter");
        byte[] rData = new byte[0];
        byte[] rcvData = new byte[]{};
        byte[] activationState =  new byte[]{};
        int status,rStatus;
        String currentWalletPkg = "";
        statusCode = 0;
        try{
            getVcStatusProcedure :
            {
            currentWalletPkg = getCallingAppPkg(context);

            Log.i(TAG, "vcEntry : " + vcEntry);
            Log.i(TAG, "pkg : " + currentWalletPkg);

            /*Selecting LTSM Application
             * */
            rStatus = ltsmStart();
            if(rStatus != Data.SW_NO_ERROR){
                Log.i(TAG, "LTSMStart Failed");
                status = rStatus;
                break getVcStatusProcedure;
            }

            /*If VC detail doesn't available status will not be success
             * */
            byte[] getStatusRsp = getStatus(TLV.make(Data.TAG_VC_ENTRY, Utils.shortToByteArr((short)vcEntry)));
            if(Utils.getSW(getStatusRsp) != Data.SW_NO_ERROR)
            {
                status = Data.SW_REFERENCE_DATA_NOT_FOUND;
                break getVcStatusProcedure;
            }

            List<TLV> getStatusRspTlvs = TLV.parse(Utils.getRDATA(getStatusRsp)).get(0).getNodes();
            TLV tlvResult = TLV.find(getStatusRspTlvs, Data.TAG_SP_AID);

            if (tlvResult != null)
            {
                byte[] spAid = tlvResult.getValue();
                if(!Arrays.equals(Utils.getApkHash(currentWalletPkg), spAid))
                {
                    status = Data.SW_CONDITION_OF_USE_NOT_SATISFIED;
                    break getVcStatusProcedure;
                }
            }
            else
            {
                status = Data.SW_NO_DATA_RECEIVED;
                break getVcStatusProcedure;
            }

            //Step6:Select CRS.If it is not selected return the CRS Select status failed
            rStatus = crsSelect();
            if(rStatus != Data.SW_NO_ERROR){
                Log.i(TAG, "crsSelect Failed");
                status = Data.SW_CRS_SELECT_FAILED;
                break getVcStatusProcedure;
            }
            //Step7:LTSM Client sends a GET STATUS to retrieve the state of VC Manager
            byte[] vcmAid = Utils.append(Data.VC_MANAGER_AID, Utils.shortToByteArr((short)vcEntry));
            byte[] capdu = Utils.makeCAPDU(0x80, 0xF2, 0x40, 0x00,
                    Utils.append(
                      TLV.make(0x4F, vcmAid),
                      TLV.make(0x5C, Utils.parseHexString("9F70"))
                    )
                  );

            rcvData =exchangeWithSe(capdu);

            if(Utils.getSW(rcvData) != Data.SW_NO_ERROR)
            {
                status = Utils.getSW(rcvData);
                break getVcStatusProcedure;
            }

            getStatusRspTlvs = TLV.parse(Utils.getRDATA(rcvData)).get(0).getNodes();
            tlvResult = TLV.find(getStatusRspTlvs, Data.TAG_VC_STATE);

            if (tlvResult != null)
            {
                activationState = tlvResult.getValue();
                status = Data.SW_NO_ERROR;
            }
            else
            {
                status = Data.SW_REFERENCE_DATA_NOT_FOUND;
            }
            }
        /*
         * If success then return the activation status + status
         * else send the failed SW
         * */
        if(status == Data.SW_NO_ERROR)
        {
            rData = Utils.append(TLV.make(Data.TAG_VC_STATE, new byte[]{(byte)activationState[1]}), Utils.createStatusCode(Data.SW_NO_ERROR));
            Log.i(TAG, "getVirtualCardStatus RDATA: " + Utils.bytArrayToHex(rData));

        }
        else
        {
            rData = Utils.createStatusCode((short)status);
        }

        return rData;
        }
        catch(Exception e)
        {
            e.printStackTrace();
            return Utils.createStatusCode(Data.SW_EXCEPTION);
        }
        finally
        {
            closeLogicalChannel();
        }
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
    public synchronized byte[] activateVirtualCard(int vcEntry, boolean mode, boolean concurrentActivationMode) throws RemoteException {
        String TAG = "LTSMCommunicator:ActivateVirtualCard";
        Log.i(TAG, "Enter");
        byte[] rData = new byte[0];
        short status = Data.SW_REFERENCE_DATA_NOT_FOUND;

        try{

            activationProcedure :
            {

            int stat = Data.SW_UNEXPECTED_BEHAVIOR;
            TLV tlvResult;
            byte[] retStat              = new byte[4];
            byte[] deactivatedvcEntry   = new byte[2],Ret_Data = new byte[4];
            byte[] rcvData              = new byte[]{};
            byte[] getStatusRsp         = new byte[]{};
            byte[] setStatusRsp         = new byte[]{};

            byte[] vcInfo               = new byte[]{};
            byte[] vcmAid               = new byte[]{};
            byte[] smAid                = new byte[]{};
            byte[] vcState              = new byte[]{};
            List<TLV> getStatusRspTlvs;

            String pkg = getCallingAppPkg(context);
            Log.i(TAG, "vcEntry : " + vcEntry);
            Log.i(TAG, "pkg : " + pkg);

            /*Selecting LTSM Application
             * */
            stat = ltsmStart();
            if(stat != Data.SW_NO_ERROR){
                Log.i(TAG, "LTSMStart Failed");
                closeLogicalChannel();
                status = (short)stat;
                break activationProcedure;
            }

            /*If VC detail doesn't available status will not be success
             * */
            getStatusRsp = getStatus(TLV.make(Data.TAG_VC_ENTRY, Utils.shortToByteArr((short)vcEntry)));
            if(Utils.getSW(getStatusRsp) != Data.SW_NO_ERROR)
            {
                status = Data.CONDITION_OF_USE_NOT_SATISFIED;
                break activationProcedure;
            }

            getStatusRspTlvs= TLV.parse(Utils.getRDATA(getStatusRsp)).get(0).getNodes();
            tlvResult = TLV.find(getStatusRspTlvs, Data.TAG_SP_AID);

            if (tlvResult != null)
            {
                byte[] spAid = tlvResult.getValue();
                Log.i(TAG, "spAid : " + Utils.bytArrayToHex(spAid));
                Log.i(TAG, "appid : " + Utils.bytArrayToHex(Utils.getApkHash(pkg)));
                if(!Arrays.equals(Utils.getApkHash(pkg), spAid))
                {
                    status = Data.CONDITION_OF_USE_NOT_SATISFIED;
                    break activationProcedure;
                }
            }
            else
            {
                //TODO confirm with else
            }

            /*Check VC state*/
            tlvResult = TLV.find(getStatusRspTlvs, Data.TAG_VC_STATE);

            if (tlvResult != null)
            {
                vcState = tlvResult.getValue();

                /* possible values for vcState,
                 *  x x x x 0 0 1 0 - VC_CREATED
                 *  x x x x 0 0 0 1 - VC_IN_PROGRESS
                 *  x x x x 0 1 1 0 - VC_DEAD
                 * */

                if((vcState[0] & 0x02) != 0x02)
                {
                    status = Data.SW_INVALID_VC_ENTRY;
                    break activationProcedure;
                }
                else
                {
                    tlvResult = TLV.find(getStatusRspTlvs, Data.TAG_SM_AID);
                    if (tlvResult != null)
                    {
                        smAid = tlvResult.getValue();
                    }else
                    {
                        smAid = Utils.append(Data.SERVICE_MANAGER_AID, Utils.shortToByteArr((short)vcEntry));
                    }

                    vcmAid = Utils.append(Data.VC_MANAGER_AID, Utils.shortToByteArr((short)vcEntry));
                }
            }
            else
            {
                //TODO confirm with else
            }

            //Select CRS Prior to Activate
            stat = crsSelect();
            if(stat != Data.SW_NO_ERROR){
                Log.i(TAG, "crsSelect Failed");
                status = Data.SW_CRS_SELECT_FAILED;
                break activationProcedure;
            }

            //If current request is Activation
            if(mode == true)
            {
                //Checking VCM can be activate
                // 4.a Issue GET STATUS (for the VCM)
                byte[] capdu = Utils.makeCAPDU(0x80, 0xF2, 0x40, 0x00,
                  Utils.append(
                    TLV.make(0x4F, vcmAid),
                    TLV.make(0x5C, Utils.parseHexString("9F70"))
                  )
                );

                getStatusRsp =exchangeWithSe(capdu);
                if(Utils.getSW(getStatusRsp) != Data.SW_NO_ERROR)
                {
                    status = Data.SW_REFERENCE_DATA_NOT_FOUND;
                    break activationProcedure;
                }

                getStatusRspTlvs = TLV.parse(Utils.getRDATA(getStatusRsp)).get(0).getNodes();
                tlvResult = TLV.find(getStatusRspTlvs, Data.TAG_VC_STATE);

                if (tlvResult != null)
                {
                    vcState = tlvResult.getValue();
                    if(vcState.length == 0x02)
                    {

                        if(((vcState[0] & 0x80)==0x80)          //check if VCM_LOCKED
                                || ((vcState[1] & 0x80)==0x80))  // Check if VCM_NON_ACTIVABLE
                        {
                            status = Data.SW_CONDITION_OF_USE_NOT_SATISFIED;
                            break activationProcedure;
                        }

                        if(((vcState[0] & 0x80)!=0x80)
                                && (vcState[1] == 0x01)) //Check if Already Activated
                        {
                            Log.i(TAG, "Already Activated");
                            status = Data.SW_NO_ERROR;
                            break activationProcedure;
                        }
                    }
                    else
                    {
                        status = Data.CONDITION_OF_USE_NOT_SATISFIED;
                        break activationProcedure;
                    }
                }
                else
                {
                    status = Data.SW_UNEXPECTED_BEHAVIOR;
                    break activationProcedure;
                }

                //Checking SM can be activate
                capdu = Utils.makeCAPDU(0x80, 0xF2, 0x40, 0x00,
                        Utils.append(
                                TLV.make(0x4F, smAid),
                                TLV.make(0x5C, Utils.parseHexString("9F70"))
                                )
                        );

                getStatusRsp =exchangeWithSe(capdu);
                if(Utils.getSW(getStatusRsp) != Data.SW_NO_ERROR)
                {
                    status = Data.SW_REFERENCE_DATA_NOT_FOUND;
                    break activationProcedure;
                }

                getStatusRspTlvs = TLV.parse(Utils.getRDATA(getStatusRsp)).get(0).getNodes();
                tlvResult = TLV.find(getStatusRspTlvs, Data.TAG_VC_STATE);

                if (tlvResult != null)
                {
                    vcState = tlvResult.getValue();
                    if(vcState.length == 0x02)
                    {

                        if(((vcState[0] & 0x80)==0x80)          //check if VCM_LOCKED
                                || ((vcState[1] & 0x80)==0x80))  // Check if VCM_NON_ACTIVABLE
                        {
                            status = Data.SW_CONDITION_OF_USE_NOT_SATISFIED;
                            break activationProcedure;
                        }
                    }
                }
                else
                {
                    status = Data.SW_UNEXPECTED_BEHAVIOR;
                    break activationProcedure;
                }

                if(concurrentActivationMode == true)
                {
                    status = (short)manageConcurrencyActivation(vcEntry);
                }
                else
                {
                    status = (short)manageNonConcurrencyActivation(vcmAid);
                }
            }
            /*If current request is DeActivation
             * */
            else
            {
                setStatusRsp = setStatus(TLV.make(0x4F, vcmAid),false);
                status =  Utils.getSW(setStatusRsp);
            }

            } //activationProcedure block ends

        Log.i(TAG, "activateVirtualCard : " + Utils.bytArrayToHex(rData));
        Log.i(TAG, "Exit");
        return Utils.createStatusCode(status);

        } // try block ends
        catch(Exception e)
        {
            e.printStackTrace();
            return Utils.createStatusCode(Data.SW_EXCEPTION);
        }
        finally
        {
            closeLogicalChannel();
        }
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

        String pkg = getCallingAppPkg(context);

        stat = ltsmStart();
        if(stat != Data.SW_NO_ERROR){
            Log.i(TAG, "LTSMStart Failed");
            closeLogicalChannel();
            return Utils.createStatusCode((short)stat);
        }

        byte[] cData = VCDescriptionFile.AddandUpdateMDAC(vcData,(short)vcEntry,Utils.createSha(pkg));
        Log.i(TAG, "cData : " + cData.length);
        rData = exchangeLtsmProcessCommand(cData);

        if(Utils.getSW(rData) == Data.SW_NO_ERROR){
            rData = Utils.createStatusCode(Data.SW_NO_ERROR);
        }
        else{
            rData = Utils.createStatusCode(Utils.getSW(rData));
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
        byte[] smAid = new byte[]{};
        TLV tlvResult = null;

        String pkg = getCallingAppPkg(context);

        int i,stat;
        stat = ltsmStart();
        if(stat != Data.SW_NO_ERROR){
            Log.i(TAG, "LTSMStart Failed");
            closeLogicalChannel();
            return Utils.createStatusCode((short)stat);
        }

        byte [] getStatusRsp = getStatus(TLV.make(Data.TAG_VC_ENTRY, Utils.shortToByteArr((short)vcEntry)));
        if(Utils.getSW(getStatusRsp) != Data.SW_NO_ERROR)
        {
            statusCode = Utils.getSW(getStatusRsp);
            closeLogicalChannel();
            return Utils.createStatusCode(statusCode);
        }

        List<TLV> getStatusRspTlvs= TLV.parse(Utils.getRDATA(getStatusRsp)).get(0).getNodes();
        tlvResult = TLV.find(getStatusRspTlvs, Data.TAG_SP_AID);

        if (tlvResult != null)
        {
            byte[] spAid = tlvResult.getValue();
            Log.i(TAG, "spAid : " + Utils.bytArrayToHex(spAid));
            Log.i(TAG, "appid : " + Utils.bytArrayToHex(Utils.getApkHash(pkg)));
            if(!Arrays.equals(Utils.getApkHash(pkg), spAid))
            {
                closeLogicalChannel();
                return Utils.createStatusCode(Data.SW_CONDITION_OF_USE_NOT_SATISFIED);
            }
        }
        else
        {
            //TODO confirm with else
        }

        TLV tlvResult1 = TLV.find(getStatusRspTlvs, Data.TAG_SM_AID);
        if (tlvResult1 != null)
        {
            smAid = tlvResult1.getValue();
        }
        else
        {
            smAid = Utils.append(Data.SERVICE_MANAGER_AID, Utils.shortToByteArr((short)vcEntry));//Modified
        }
        byte[] cApdu = Utils.makeCAPDU(0x00, 0xA4, 0x04, 0x00, smAid);

        recvData = exchange(cApdu, channelId[channelCnt -1]);
        if(recvData == null)
        {
            Log.i(TAG, "SE transceive failed ");
            closeLogicalChannel();
            return Utils.createStatusCode(Data.SW_NO_DATA_RECEIVED);
        }

        Log.i(TAG, "Received Data : " + Utils.bytArrayToHex(recvData));

        cApdu = Utils.makeCAPDU(0x80, 0xB0, 0x00, 0x00, vcData);
        recvData = exchange(cApdu, channelId[channelCnt -1]);
        rData = processReadMifareDataResponse(recvData);

        Log.i(TAG, "Exit");
        closeLogicalChannel();
        Log.i(TAG, "recvData : " + Utils.bytArrayToHex(rData));
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
        byte[] VcList= new byte[0];

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

        stat = ltsmStart();
        if(stat != Data.SW_NO_ERROR){
            Log.i(TAG, "LTSMStart Failed");
            closeLogicalChannel();
            return Utils.createStatusCode((short)stat);
        }

        //If Registry is already available in SharedPreference Check for Uninstalled Wallet Apps
        stat = checkUninstalledApps();
        if(stat == Data.SW_NO_ERROR){
            Log.i(TAG, "Uninstalled App Found");
        }
        rData = getStatus(null);
        if(Utils.getSW(rData) == Data.SW_NO_ERROR)
        {
            List<TLV> getStatusRspTlvs = TLV.parse(Utils.getRDATA(rData));
            if(getStatusRspTlvs!=null)
            {
                VcList = VCDescriptionFile.getVcListResp(getStatusRspTlvs);
            }

            if (VcList.length !=0)
            {
                rData = Utils.append(VcList, Utils.createStatusCode(Utils.getSW(rData)));
            }
            else
            {
                rData = Utils.createStatusCode(Data.SW_REFERENCE_DATA_NOT_FOUND);
            }
        }
        else
        {
            rData = Utils.createStatusCode(Utils.getSW(rData));
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
    private byte[] getStatus(byte[] data){
        String TAG = "LTSMCommunicator:getStatus";
        Log.i(TAG, "Enter");

        byte[] capdu = Utils.makeCAPDU(0x80, 0xF2, 0x40, 0x00, data);
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
                status = Utils.append(status, rapdu);
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
     *Set the virtual card status
     *
     ********************************************************/
    private byte[] setStatus(byte[] data, boolean mode){
        String TAG = "LTSMCommunicator:setStatus";
        Log.i(TAG, "Enter");

        byte[] cData = new byte[]{};
        byte[] rData = new byte[]{};

        if (mode == true) {
            cData = Utils.makeCAPDU((byte) 0x80, (byte) 0xF0, 0x01, (byte) 0x01, data);
        }else{
            cData = Utils.makeCAPDU((byte) 0x80, (byte) 0xF0, 0x01, (byte) 0x00, data);
        }
        rData = exchangeWithSe(cData);
        Log.i(TAG, "Exit");
        return rData;
    }

    /*******************************************************
     *
     *Manage concurrency Activation Procedure
     * @param vcEntry
     * @return status
     *
     ********************************************************/
    private int manageConcurrencyActivation(int vcEntry) {
        String TAG = "LTSMCommunicator:manageCuncurrencyActivation";
        Log.i(TAG, "Enter");
        byte[] setStatusRsp = new byte[]{};
        byte[] getStatusRsp = new byte[]{};
        byte[] conflictingAidsTagA2 = new byte[]{};
        int skipFirstTag48 = 1;
        List<TLV> setStatusRspTlvs,parseTlvs;
        TLV tlvResult;
        int status;
        byte[] vcmAidToActivate = Utils.append(Data.VC_MANAGER_AID, Utils.shortToByteArr((short)vcEntry));

        if (!previousConcurrentActivationMode)
        {
          // Deactivate all
          deactivateAllContactlessApplications();
        }
        previousConcurrentActivationMode = true;

        /* Try to activate the intended VC
         * */
        tryActivate : for(;;)
        {
            setStatusRsp = setStatus(TLV.make(0x4F, vcmAidToActivate),true);

            switch(Utils.getSW(setStatusRsp))
            {
            case Data.SW_NO_ERROR :

                Log.i(TAG, "Activated Successfully");
                status =  Data.SW_NO_ERROR;
                break tryActivate;

            case Data.SW_6330_AVAILABLE :

            case Data.SW_6310_AVAILABLE :
                /*Activation not successful
                 * Conflicting AIDs present
                 * */
                tlvResult = getTlvA0A2(setStatusRsp);

                switch(tlvResult.getTag())
                {
                case Data.TAG_A0_AVAILABLE :
                    byte[] conflictingAidsTagA0 = tlvResult.getValue();
                    setStatusRsp = setStatus(conflictingAidsTagA0,false);
                    if(Utils.getSW(setStatusRsp) == Data.SW_NO_ERROR)
                    {
                        /*
                         * deActivation of conflictingAidsTagA0 successful
                         * try to activate initial intended aid
                         * */
                        break;
                    }
                    else
                    {
                        Log.i(TAG, "Dactivation of conflicting Aids of TagA0 failed. Status : " + Utils.getSW(setStatusRsp));
                        status = Data.SW_UNEXPECTED_BEHAVIOR;
                        break tryActivate;
                    }

                case Data.TAG_A2_AVAILABLE :
                    parseTlvs = TLV.parse(tlvResult.getValue());
                    tlvResult = TLV.find(parseTlvs, Data.TAG_48_AVAILABLE);

                    switch(Utils.byteArrayToShort(tlvResult.getValue()))
                    {
                    case Data.REASON_CODE_800B :
                    case Data.REASON_CODE_800C :

                        status = Data.SW_VC_IN_CONTACTLESS_SESSION;
                        break tryActivate;

                    case Data.REASON_CODE_8002 :
                    case Data.REASON_CODE_8003 :
                    case Data.REASON_CODE_8004 :
                    case Data.REASON_CODE_8005 :

                        /*Tag format
                         * A2 --
                         *       48 -- Mandatory
                         *       4F -- Mandatory
                         *       4F -- Optional
                         *       ..... .....
                         *       48 -- Optional
                         *       4F -- Optional
                         *       4F -- Optional
                         *       ..... .....
                         *       ..... .....
                         *       Get 4F Tag corresponding to only one Tag48
                         * */

                        conflictingAidsTagA2 = getSubsequentTlvs(skipFirstTag48, Data.TAG_4F_AVAILABLE, parseTlvs);

                        setStatusRsp = setStatus(conflictingAidsTagA2,false);

                        if(Utils.getSW(setStatusRsp) != Data.SW_NO_ERROR)
                        {
                            Log.i(TAG, "Dactivation of conflicting Aids of TagA0 failed. Status : " + Utils.getSW(setStatusRsp));
                            status = Data.SW_UNEXPECTED_BEHAVIOR;
                            break tryActivate;
                        }
                        /*
                         * deActivation of conflictingAidsTagA2 successful
                         * try to activate initial intended aid
                         * */
                        break;

                    case Data.REASON_CODE_8009 :
                        /*
                         * Deactivate the VC that contains a Mifare Desfire AID conflicting with
                         * Mifare Desfire AID of the VC to be activated
                         * */

                        TLV tlv4F,tlv;
                        tlv4F = TLV.find(parseTlvs, Data.TAG_4F_AVAILABLE);
                        byte[] aid = tlv4F.getValue();

                        /*appending 0x5C,0xA6 will make restrict the return Tag to only A6 (avoids all unnecessary tags)
                         * */
                        byte[] capdu = Utils.makeCAPDU(0x80, 0xF2, 0x40, 0x00,
                                Utils.append(
                                  TLV.make(0x4F, aid),
                                  TLV.make(0x5C, new byte[] { (byte)(0xA6) })
                                )
                              );
                        getStatusRsp = exchangeWithSe(capdu);
                        Log.i(TAG,"REASON_CODE_8009 getStatusRsp"+ Utils.bytArrayToHex(Utils.getRDATA(getStatusRsp)));
                        parseTlvs = TLV.parse(Utils.getRDATA(getStatusRsp), new int[] { 0xF1 });
                        tlv = TLV.find(parseTlvs, 0x61);
                        parseTlvs = tlv.getNodes();
                        tlv = TLV.find(parseTlvs, Data.TAG_A6_AVAILABLE);
                        if(tlv == null)
                        {
                            Log.i(TAG, "Tag A6 missing");
                            status = Data.SW_UNEXPECTED_BEHAVIOR;
                            break tryActivate;
                        }
                        parseTlvs = tlv.getNodes();
                        tlv = TLV.find(parseTlvs, Data.TAG_F1_AVAILABLE);
                        if(tlv == null)
                        {
                            status = Data.SW_UNEXPECTED_BEHAVIOR;
                            break tryActivate;
                        }

                        setStatusRsp = setStatus(TLV.make(Data.TAG_4F_AVAILABLE, tlv.getValue()),false);
                        if(Utils.getSW(setStatusRsp) != Data.SW_NO_ERROR)
                        {
                            status = Data.SW_UNEXPECTED_BEHAVIOR;
                            break tryActivate;
                        }
                        //TODO notify the application for deactivation of its VC
                        break;

                    default :
                        status = Data.SW_CONDITION_OF_USE_NOT_SATISFIED;
                        break tryActivate;

                    }
                    break;

                default :
                    status = Data.SW_UNEXPECTED_BEHAVIOR;
                    break tryActivate;
                }
                break;

            default :
                status = Data.SW_UNEXPECTED_BEHAVIOR;
                break tryActivate;
            }
        }

        Log.i(TAG, "Exit");
        return status;

    }
    /*******************************************************
     *
     *Manage Non-concurrency Activation Procedure
     * @param vcEntry
     * @return status
     *
     ********************************************************/
   private int manageNonConcurrencyActivation(byte[] vcmAid) {
        String TAG = "LTSMCommunicator:manageNonConcurrencyActivation";
        Log.i(TAG, "Enter");
        int  status = 0;
        status = deactivateAllContactlessApplications();
        if( status!= Data.SW_NO_ERROR)
        {
            return status;
        }
        previousConcurrentActivationMode = false;
     // Activate VCM (Do not check SW).
        byte[] capdu = Utils.makeCAPDU(0x80, 0xF0, 0x01, 0x01, TLV.make(0x4F, vcmAid));
        byte[] rapdu = exchangeWithSe(capdu);
        return Utils.getSW(rapdu);
    }

    private static boolean isVCManagerAID(byte[] aid) {
        if (aid.length < Data.NONCONCURRENT_AID_PARTIAL.length)
        {
           return false;
        }
        for(int i = 0; i < Data.NONCONCURRENT_AID_PARTIAL.length - 1; i++)
        {
           if (aid[i] != Data.NONCONCURRENT_AID_PARTIAL[i])
           {
               return false;
           }
        }
       return (byte)(aid[Data.NONCONCURRENT_AID_PARTIAL.length - 1] & 0xF0) == 0x10;
    }
    private int deactivateAllContactlessApplications() {
    // First, deactivate all VCMs; Then deactivate all other applications.
        int status = 0;
        byte p2 = 0x00; // First
        for(boolean more = true; more; p2 = 0x01)
        {
            // 5. Get the list of activated VCMs.
            byte[] capdu = Utils.makeCAPDU(0x80, 0xF2, 0x40, p2,
                    Utils.append(
                            TLV.make(0x4F, Utils.parseHexString("A000000396")), // RID
                            Utils.parseHexString("9F700207015C014F")
                            )
                    );
            byte[] rapdu = exchangeWithSe(capdu);

            switch(Utils.getSW(rapdu))
            {
            default:
                status = Data.SW_CONDITION_OF_USE_NOT_SATISFIED;
                return status;

            case Data.SW_6310_AVAILABLE:
                more = true;
                break;

            case Data.SW_NO_ERROR:
                more = false;
                status =Data.SW_NO_ERROR;
                break;

            case Data.SW_REFERENCE_DATA_NOT_FOUND:
                more = false;
                continue;
            }

            List<TLV> tlvs = TLV.parse(Utils.getRDATA(rapdu), new int[] { 0xF1 });
            for(TLV tlv61 : tlvs)
            {
                if (tlv61.getTag() != 0x61)
                {
                    continue;
                }

                List<TLV> tlv61s;
                if ((tlv61s = tlv61.getNodes()) == null)
                {
                    return 0;
                }

                for(TLV tlvAid: tlv61s)
                {
                    if (tlvAid.getTag() == 0x4F)
                    {
                        byte[] aid = tlvAid.getValue();
                        // VCM ?
                        if (!isVCManagerAID(aid))
                        {
                            continue;
                        }

                        // Deactivate VCM (Do not check SW).
                        capdu = Utils.makeCAPDU(0x80, 0xF0, 0x01, 0x00, TLV.make(0x4F, aid));
                        rapdu = exchangeWithSe(capdu);
                        Intent intent = new Intent();
                        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                        intent.setAction("com.nxp.ltsm.ltsmclient.VC_DEACTIVATED");
                        intent.putExtra("VC_AID", aid);
                        context.sendBroadcast(intent);

                    }
                }
            }
        }

        p2 = 0x00;
        for(boolean more = true; more; p2 = 0x01)
        {
            // 6. List all remaining activated contactless applications
            byte[] capdu = Utils.makeCAPDU(0x80, 0xF2, 0x40,p2,
                    Utils.parseHexString("4F009F700207015C014F")
                    );
            byte[] rapdu = exchangeWithSe(capdu);

            switch(Utils.getSW(rapdu))
            {
            default:
                status = Data.SW_CONDITION_OF_USE_NOT_SATISFIED;
                return status;

            case Data.SW_6310_AVAILABLE:
                more = true;
                break;

            case Data.SW_NO_ERROR:
                more = false;
                status =Data.SW_NO_ERROR;
                break;

            case Data.SW_REFERENCE_DATA_NOT_FOUND:
                more = false;
                continue;
            }

            List<TLV> tlvs = TLV.parse(Utils.getRDATA(rapdu), new int[] { 0xF1 });
            for(TLV tlv61 : tlvs)
            {
                if (tlv61.getTag() != 0x61)
                {
                    continue;
                }

                List<TLV> tlv61s;
                if ((tlv61s = tlv61.getNodes()) == null)
                {
                    return 0;
                }

                for(TLV tlvAid: tlv61s)
                {
                    if (tlvAid.getTag() == 0x4F)
                    {
                        byte[] aid = tlvAid.getValue();

                        // Deactivate VCM (Do not check SW).
                        capdu = Utils.makeCAPDU(0x80, 0xF0, 0x01, 0x00, TLV.make(0x4F, aid));
                        rapdu = exchangeWithSe(capdu);
                        Intent intent = new Intent();
                        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                        intent.setAction("com.nxp.ltsm.ltsmclient.VC_DEACTIVATED");
                        intent.putExtra("VC_AID", aid);
                        context.sendBroadcast(intent);
                    }
                }
            }
        }
        return Data.SW_NO_ERROR;
    }

    /*******************************************************
     *
     *Returns subsequent Tags in parseTlvs
     *
     ********************************************************/
    private byte[] getSubsequentTlvs(int startNode, short tagToFind, List<TLV> parseTlvs) {
        byte[] foundTlvs = new byte[]{};

        for(int i = startNode; i<parseTlvs.size(); i++)
        {
            if(parseTlvs.get(i).getTag() == tagToFind)
            {
                foundTlvs = Utils.append(foundTlvs, parseTlvs.get(i).getTLV());
            }
            else
            {
                /*break from for loop*/
                break;
            }
        }
        return foundTlvs;
    }

    /*******************************************************
     *
     *Returns either TLV Tag A0 or Tag A1
     *
     ********************************************************/
    private TLV getTlvA0A2(byte[] setStatusRsp) {
        String TAG = "LTSMCommunicator:getTagsA0A2";
        Log.i(TAG, "Enter");
        List<TLV> setStatusRspTlvs;
        TLV tlvResult,retTlv = null;
        Log.i(TAG,"getTlvA0A2 setStatusRsp"+ Utils.bytArrayToHex(Utils.getRDATA(setStatusRsp)));
        setStatusRspTlvs = TLV.parse(Utils.getRDATA(setStatusRsp)).get(0).getNodes();
        tlvResult = TLV.find(setStatusRspTlvs, Data.TAG_A0_AVAILABLE);
        /*Tag A0 Present*/
        if (tlvResult != null)
        {
            retTlv=  tlvResult;
        }
        else
        {
            tlvResult = TLV.find(setStatusRspTlvs, Data.TAG_A2_AVAILABLE);
            if (tlvResult != null)
            {
                retTlv = tlvResult;
            }
        }
        Log.i(TAG, "Exit");

        return retTlv;
    }

    /*******************************************************
     *
     * Remove Registry Entry
     *
     ********************************************************/
    private void removeRegistryEntry(int pos, ltsmRegistry Registry){
       // int tmpVc = Registry.walletName.get(pos);
        String TAG = "LTSMCommunicator:removeRegistryEntry";
        //Registry.vcEntry.remove(pos);
       // Log.i(TAG, " DeleteVCEntry " + tmpVc);
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
        int status;
        status = ltsmOpenChannel();
        if(status != Data.SW_NO_ERROR){
            Log.i(TAG, "LTSM Open Channel Failed");
            return status;
        }
        status = ltsmSelect();
        if(status != Data.SW_NO_ERROR){
            Log.i(TAG, "LTSM select Failed");
            closeLogicalChannel();
            return status;
        }
        status = Data.SW_NO_ERROR;
        Log.i(TAG, "Exit");
        return status;
    }

    /*******************************************************
     *
     * Opens Logical Channel
     *
     ********************************************************/

    private static int ltsmOpenChannel() {
        String TAG = "LTSMCommunicator:openLogicalChannel";
        int status;
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
            recvData = exchangeApdu(Data.openchannel);
        } catch (RemoteException e) {
            closeLogicalChannel();
            e.printStackTrace();
        }
        if(recvData == null)
        {
            Log.i(TAG, "SE transceive failed ");
            status = Data.SW_SE_TRANSCEIVE_FAILED;
        }
        else if(Utils.getSW(recvData)!=Data.SW_NO_ERROR){
            statusCode = Utils.getSW(recvData);
            Log.i(TAG, "Invalid Response");
            status = Utils.getSW(recvData);
        }
        else{
            Log.i(TAG, "openLogicalChannel SUCCESS");
            channelId[channelCnt] = recvData[recvData.length - 3];
            isopened[channelCnt] = 1;

            Utils.saveArray(channelId, "channelId", context);
            Utils.saveArray(isopened, "isopened", context);
            channelCnt = channelCnt + 1;
            Utils.saveInt(channelCnt,"channelCnt",context);
            status = Data.SW_NO_ERROR;
        }

        Log.i(TAG, "Exit");
        return status;
    }

    /*******************************************************
     *
     *Selects LTSM with currently opened Logical channel
     *
     ********************************************************/

    private static int ltsmSelect() {
        String TAG = "LTSMCommunicator:ltsmSelect";
        int status;
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
            status = Data.SW_SE_TRANSCEIVE_FAILED;
        }
        else
        {
            Log.i(TAG, "Received Data : " + Utils.bytArrayToHex(recvData));
            if (Utils.getSW(recvData) == Data.SW_NO_ERROR)
            {
                Log.i(TAG, "ltsmSelect SUCCESS ");
                status = Data.SW_NO_ERROR;
            }else{
                statusCode = Utils.getSW(recvData);
                status = Utils.getSW(recvData);
            }
        }
        Log.i(TAG, "Exit");
        return status;
    }

    /*******************************************************
     *
     *Handles Exchange of Data during LTSM Process Command
     *
     ********************************************************/

    private static byte[] exchangeLtsmProcessCommand(byte[] cdata)
    {
        String TAG = "LTSMCommunicator:exchangeLtsmProcessCommand";
        int stat = Data.SW_REFERENCE_DATA_NOT_FOUND;
        Log.i(TAG, "Enter");
        byte[] rData = new byte[0];
        Log.i(TAG, "(byte)cdata.length : "+(byte)cdata.length + "new byte[] {(byte)cdata.length} :  " + Utils.bytArrayToHex(new byte[] {(byte)cdata.length}));
        byte[] cApdu = Utils.makeCAPDU(Data.CLA_M4M_LTSM, Data.INS_M4M_LTSM_PROCESS_COMMAND, 0x00, 0x00, cdata);
        Log.i(TAG, "cApdu : " + Utils.bytArrayToHex(cApdu));
        byte[] rapdu = exchange(cApdu,channelId[channelCnt -1]);

        try{

            exchangeLtsmCmdbreak:
            {
            switch(Utils.getSW(rapdu))
            {
            default:
            {
                rData = rapdu;
                break;
            }

            case Data.SW_NO_ERROR:
                cApdu = Utils.getRDATA(rapdu);
                Log.i(TAG, "SW_NO_ERROR ");
                rData = rapdu;
                break;
            case Data.SW_6310_AVAILABLE:
                process_se_response: for(;;){
                    if (rapdu.length == 2)
                    {
                        rData = rapdu;
                        break;
                    }
                    cApdu = Utils.getRDATA(rapdu);
                    Log.i(TAG, "SW_6310_AVAILABLE: Send Data back to SE ");
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
                            rData = rapdu;
                            break process_se_response;
                        }

                        case Data.SW_NO_ERROR:
                            Log.i(TAG, "SW_NO_ERROR ");
                            rData = rapdu;
                            break process_se_response;

                        case Data.SW_6310_AVAILABLE:
                            Log.i(TAG, "SW_6310_AVAILABLE: Send Data back to SE Again");
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
                            rData = rapdu;
                            break process_se_response;
                        }

                        case Data.SW_NO_ERROR:
                            rData = rapdu;
                            break process_se_response;

                        case Data.SW_6310_AVAILABLE:
                            // FALL THROUGH
                        }
                    }
                }
            }
            }//break block exit
        }//try exit
        catch(Exception e)
        {
            e.printStackTrace();
        }
        Log.i(TAG, "Exit");
        return rData;
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
            }
        }
        recvData = exchange(cdata,(cdata[0] & 0x03));
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
            rData = exchangeApdu(cApdu);
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
        if(rData == null)
        {
            Log.i(TAG, "SE transceive failed ");
            return Data.SW_NO_DATA_RECEIVED;
        }
        Log.i(TAG, "rData : " + Utils.bytArrayToHex(rData));
        Log.i(TAG, "Exit");
        return Utils.getSW(rData);
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
        byte[] closeChannelCmd = new byte[]{};
        channelCnt = Utils.loadInt("channelCnt",context);
        channelId = Utils.loadArray("channelId",context);
        isopened = Utils.loadArray("isopened",context);

        Log.i(TAG, "channelCnt : " + channelCnt);


        for(int cnt =0; (cnt < channelCnt); cnt++){
            if(isopened[cnt] == 1){

                closeChannelCmd = new byte[]{
                        (byte) channelId[cnt],
                        (byte) 0x70,
                        (byte) 0x80,
                        (byte) channelId[cnt],
                        (byte) 0x00
                };
                try {
                    rData = exchangeApdu(closeChannelCmd);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }

                if(rData == null){
                    Log.i(TAG, "exchangeApdu FAILED");
                    rData = Utils.createStatusCode(Data.SW_NO_DATA_RECEIVED);
                }
                else if(Utils.getSW(rData) == Data.SW_NO_ERROR){
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
        byte[] cData = VCDescriptionFile.deleteVc(pkg,(short)vcEntry);
        byte[] rData = exchangeLtsmProcessCommand(cData);
        Log.i(TAG, "Exit");
        return Utils.getSW(rData);
    }

    /*******************************************************
     *
     *Handles Personalisation During CreateVC
     *
     *******************************************************/

    private int handlePersonalize(byte[] vcData, String pkg, short intValue) {
        String TAG = "LTSMCommunicator:handlePersonalize";
        int stat = Data.SW_REFERENCE_DATA_NOT_FOUND;
        Log.i(TAG, "Enter");
        currentProcess = "handlePersonalize";
        byte[] cData = VCDescriptionFile.CreatePersonalizeData(vcData,pkg,Utils.createSha(pkg),intValue,context);
        Log.i(TAG, "cData : " + Utils.bytArrayToHex(cData));
        byte[] rData = exchangeLtsmProcessCommand(cData);
        return Utils.getSW(rData);
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
                e.printStackTrace();
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

    public static byte[] exchangeApdu(byte[] apdu) throws RemoteException {

        Log.i(LOG_TAG,"exchange APDU ENTER");
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
     *Retreive callers package name
     *
     ********************************************************/
    public static String getCallingAppPkg(Context mContext) throws RemoteException {

        Log.i(LOG_TAG,"getCallingAppPkg ENTER");
        bundle = new Bundle();
        try {
            if(mLtsmService != null){
                bundle = mLtsmService.getCallingAppPkg("com.nxp.ltsm.ltsmclient", mBinder);
                Log.i(LOG_TAG, "Calling package name = " +bundle.getString("packageName"));
            }
            if (bundle == null) {
                Log.i(LOG_TAG,"exchange APDU failed");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return bundle.getString("packageName");
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

        vcUid = Utils.getValue((byte)0x41,rapdu);
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
        //        if(Registry.vcEntry.size()==10){
        //            prefsEditor.clear();
        //        }
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
        byte regApkId[];
        /*
         * Checking Registry entry against Currently installed app entries
         * */
        for(i = 0; i < Registry.walletName.size(); i++){
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
            }
        }
        /*
         * Delete VCs which are in Uninstalled List
         * */
        if(!UninstalledApps.isEmpty()){
            byte[] getStatusRsp =getStatus(null);
            List<TLV> getStatusRspTlvs = TLV.parse(Utils.getRDATA(getStatusRsp));
            for(i = 0; i < UninstalledApps.size(); i++){
                for(j = 0; j < Registry.walletName.size(); j++){
                    if(UninstalledApps.get(i).equals(Registry.walletName.get(j).toString())){
                        String walletName_hash = Registry.walletName_hash.get(j).toString();
                        try{
                            regApkId = Hex.decodeHex(Registry.walletName_hash.get(j).toCharArray());
                        }
                        catch(Exception e)
                        {
                            e.printStackTrace();
                            return Data.SW_EXCEPTION;
                        }
                        if(getStatusRspTlvs!=null){
                            for(TLV tlv: getStatusRspTlvs)
                            {
                                TLV tlvVCx = TLV.find(tlv.getNodes(), Data.TAG_VC_ENTRY);
                                TLV tlvApkId = TLV.find(tlv.getNodes(), Data.TAG_SP_AID);
                                byte ApkId[] = tlvApkId.getValue();
                                Log.i(TAG, "SEApkId : " + Utils.bytArrayToHex(ApkId));
                                Log.i(TAG, "regApkId : " + Utils.bytArrayToHex(regApkId));
                                if (Arrays.equals(ApkId,regApkId)){
                                    deleteTempVc(Utils.byteArrayToShort(tlvVCx.getValue()),walletName_hash);
                                }
                            }
                            Registry.walletName.remove(j);
                            Registry.walletName_hash.remove(j);
                            saveRegistry(Registry);
                        }
                        else{
                            Registry.walletName.remove(j);
                            Registry.walletName_hash.remove(j);
                            saveRegistry(Registry);
                        }
                    }
                }
            }

            Log.i(TAG, "Exit");
            return Data.SW_NO_ERROR;
        }
        else{
            Log.i(TAG, "Exit");
            return Data.SW_REFERENCE_DATA_NOT_FOUND;
        }
    }
    private static boolean inList(int value, int ... values) {
        for(int v: values)
        {
            if (v == value)
            {
                return true;
            }
        }
        return false;
    }
    private byte[] removeTags(byte[] vcData, int ... tags) {
        List<TLV> tlvs = new ArrayList<TLV>();

        for(TLV tlv: TLV.parse(vcData, new int[] { 0xA8, 0xE2, 0xF8 }))
        {
            if (!inList(tlv.getTag(), tags))
            {
                tlvs.add(tlv);
            }
        }
        return TLV.make(tlvs);
        }
    }
