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

public class Data {


    /*Status values*/
    public static final int SUCCESS             =   0;
    public static final int FAILED              =   1;

    public static final byte CLA_M4M_LTSM                       = (byte)0x80;
    public static final byte INS_M4M_LTSM_PROCESS_COMMAND       = (byte)0xA0;
    public static final byte INS_M4M_LTSM_PROCESS_SE_RESPONSE   = (byte)0xA2;
    public static final short SW_6310_AVAILABLE                 = (short)0x6310;
    public static final short SW_6330_AVAILABLE                 = (short)0x6330;

    /**
     * Generic Tags
     * */
    public static final short TAG_VC_ENTRY                      = (short)0x40;
    public static final short TAG_SM_AID                        = (short)0x42;
    public static final short TAG_SP_SD_AID                     = (short)0x43;
    public static final short TAG_SP_AID                        = (short)0x4F;
    public static final short  TAG_MF_DF_AID                    = (short)0xF8;
    public static final short  TAG_MF_AID                       = (short)0xE2;

    public static final short TAG_A0_AVAILABLE                  = (short)0xA0;
    public static final short TAG_A1_AVAILABLE                  = (short)0xA1;
    public static final short TAG_A2_AVAILABLE                  = (short)0xA2;
    public static final short TAG_A6_AVAILABLE                  = (short)0xA6;
    public static final short TAG_F1_AVAILABLE                  = (short)0xF1;


    public static final short TAG_48_AVAILABLE                  = (short)0x48;
    public static final short TAG_4F_AVAILABLE                  = (short)0x4F;

    /*check values*/
    public static final short TAG_PENDING_LOGICAL_CHANNEL       = (short)0x56;
    public static final short TAG_PENDING_SE_CONNECTION         = (short)0x57;
    public static final short TAG_NOT_PENDING                   = (short)0x58;



    /**
     *Activation : Reason Codes TAG48 of TAGA2
     * */
    public static final short REASON_CODE_8002                  = (short)0x8002;
    public static final short REASON_CODE_8003                  = (short)0x8003;
    public static final short REASON_CODE_8004                  = (short)0x8004;
    public static final short REASON_CODE_8005                  = (short)0x8005;
    public static final short REASON_CODE_8009                  = (short)0x8009;
    public static final short REASON_CODE_800B                  = (short)0x800B;
    public static final short REASON_CODE_800C                  = (short)0x800C;

    public static final int TAG_VC_STATE                      = 0x9F70;

    /**
     * Generic error codes (from ltsm client)
     * */
    public static final short CONDITION_OF_USE_NOT_SATISFIED        = (short)0x6980;
    public static final short SW_INVALID_VC_ENTRY                   = (short)0x69E0;
    public static final short SW_OPEN_LOGICAL_CHANNEL_FAILED        = (short)0x69E1;
    public static final short SW_SELECT_LTSM_FAILED                 = (short)0x69E2;
    public static final short SW_REGISTRY_FULL                      = (short)0x69E3;
    public static final short SW_IMPROPER_REGISTRY                  = (short)0x69E4;
    public static final short SW_NO_SET_SP_SD                       = (short)0x69E5;
    public static final short SW_ALREADY_ACTIVATED                  = (short)0x69E6;
    public static final short SW_ALREADY_DEACTIVATED                = (short)0x69E7;
    public static final short SW_CRS_SELECT_FAILED                  = (short)0x69E8;
    public static final short SW_SE_TRANSCEIVE_FAILED               = (short)0x69E9;
    public static final short SW_REGISTRY_IS_EMPTY                  = (short)0x69EA;
    public static final short SW_NO_DATA_RECEIVED                   = (short)0x69EB;
    public static final short SW_EXCEPTION                          = (short)0x69EC;
    public static final short SW_CONDITION_OF_USE_NOT_SATISFIED     = (short)0x6985;


    public static final short SW_OTHER_ACTIVEVC_EXIST               = (short)0x6330;

    public static final short SW_CONDITIONAL_OF_USED_NOT_SATISFIED  = (short)0x6A80;
    public static final short SW_INCORRECT_PARAM                    = (short)0x6A86;
    public static final short SW_REFERENCE_DATA_NOT_FOUND           = (short)0x6A88;

    public static final short SW_VC_IN_CONTACTLESS_SESSION          = (short)0x6230;


    public static final short SW_UNEXPECTED_BEHAVIOR                = (short)0x6F00;

    /**
     * createVc Response Error Codes (from ltsm application in SE)
     * */

    public static final short CREATEVC_PREPERSO_NOT_FOUND                = (short)0x6F01;
    public static final short CREATEVC_SET_SP_SD_NOT_SENT                = (short)0x6F02;
    public static final short CREATEVC_INVALID_SIGNATURE                 = (short)0x6F03;
    public static final short CREATEVC_SET_SP_SD_SENT                    = (short)0x6F04;
    public static final short CREATEVC_REGISTRY_ENTRY_CREATION_FAILED    = (short)0x6F05;

    /**
     * addAndUpdate/deleteVc Response Error Codes (from ltsm application in SE)
     * */
    public static final short DELETEVC_INVALID_VC_ENTRY                  = (short)0x6F01; //Same for deleteVc
    public static final short DELETEVC_NO_LTSM_SD_PRESENT                = (short)0x6F02;

    public static final byte[] openchannel                  =   new byte[] {0x00, 0x70, 0x00, 0x00, 0x01};
    public static final byte[] SelectLTSM                   =   new byte[] {0x00, (byte) 0xA4, 0x04, 0x00, 0x0D, (byte) 0xA0, 0x00, 0x00, 0x03, (byte) 0x96, 0x41, 0x4C, 0x41, 0x01, 0x43, 0x4F, 0x52, 0x01};
    public static final byte[] AID_M4M_LTSM                 =   {(byte)0xA0, (byte)0x00, (byte)0x00, (byte)0x03, (byte)0x96, 0x4D, 0x46, 0x00, (byte)0x4C, 0x54, 0x53, 0x4D, 0x01};
    public static final byte[] selectCRS                    =   new byte[] {(byte)0x00,(byte) 0xA4, 0x04, 0x00, 0x09,(byte) 0xA0, 0x00, 0x00, 0x01, 0x51, 0x43, 0x52, 0x53, 0x00, 0x00 };
    public static final byte[] SERVICE_MANAGER_AID          =   new byte[] {(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x03,(byte)0x96,(byte)0x4D,(byte)0x34,(byte)0x4D,(byte)0x24,(byte)0x00,(byte)0x81,(byte)0xDB,(byte)0x69,(byte)0x00};
    public static final byte[] VC_MANAGER_AID               =   new byte[] {(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x03,(byte)0x96,(byte)0x4D,(byte)0x34,(byte)0x4D,(byte)0x14,(byte)0x00,(byte)0x81,(byte)0xDB,(byte)0x69,(byte)0x00};
    public static final byte[] NONCONCURRENT_AID_PARTIAL    =   new byte[] {(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x03,(byte)0x96,(byte)0x4D,(byte)0x34,(byte)0x4D,(byte)0x14};

    public static final byte[] ACTV_LTSM_AID       =   {(byte)0xA0, (byte)0x00, (byte)0x00, (byte)0x03, (byte)0x96,0x4D,0x34,0x4D, 0x14,0x00,(byte) 0x81,(byte) 0xDB, 0x69,0x00};
    public static final byte[] Set_Status_Activate_Cmd      =   new byte[] {(byte) 0x80,(byte) 0xF0, 0x01,(byte) 0x01};
    public static final byte[] Set_Status_DeActivate_Cmd    =   new byte[] {(byte) 0x80,(byte) 0xF0, 0x01,(byte) 0x00};

    public static final short SW_NO_ERROR = (short)0x9000;
    public static final short ACTV_VC_EXISTS = (short)0x6330;
    public static final short ACTV_OTHERVC_EXISTS = (short)0x6320;

    //public static final byte[] Processing_Error              =   new byte[] {(byte) 0x4E,(byte) 0x02,(byte) 0x00,(byte) 0x99};
    public static final byte[] NO_ERROR                      =   new byte[] {(byte) 0x4E,(byte) 0x02,(byte) 0x90,(byte) 0x00};

    public static final byte[] ERROR_NO_VC_PRESENT           =   new byte[] {(byte) 0x4E,(byte) 0x02,(byte) 0x01,(byte) 0x99};
    public static final byte[] ERROR_ALREADY_ACTIVATED       =   new byte[] {(byte) 0x4E,(byte) 0x02,(byte) 0x02,(byte) 0x99};
    public static final byte[] ERROR_ALREADY_DEACTIVATED     =   new byte[] {(byte) 0x4E,(byte) 0x02,(byte) 0x03,(byte) 0x99};
    public static final byte[] ERROR_NO_VALID_INPUT          =   new byte[] {(byte) 0x4E,(byte) 0x02,(byte) 0x04,(byte) 0x99};

    //VC STATE

    public static final byte VC_CREATED                      = (byte)0x02;


}
