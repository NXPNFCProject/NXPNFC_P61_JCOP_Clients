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
    public static final short SW_6310_COMMAND_AVAILABLE         = (short)0x6310;

    /**
     * Generic error codes (from ltsm client)
     * */
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

    public static final short SW_OTHER_ACTIVEVC_EXIST               = (short)0x6330;
    public static final short SW_PROCESSING_ERROR                   = (short)0x6A88;

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

    public static final byte[] openchannel      =   new byte[] {0x00, 0x70, 0x00, 0x00, 0x01};
    public static final byte[] SelectLTSM       =   new byte[] {0x00, (byte) 0xA4, 0x04, 0x00, 0x0D, (byte) 0xA0, 0x00, 0x00, 0x03, (byte) 0x96, 0x41, 0x4C, 0x41, 0x01, 0x43, 0x4F, 0x52, 0x01};
    public static final byte[] AID_M4M_LTSM     =   {(byte)0xA0, (byte)0x00, (byte)0x00, (byte)0x03, (byte)0x96, 0x4D, 0x46, 0x00, (byte)0x4C, 0x54, 0x53, 0x4D, 0x01};
    public static final byte[] selectCRS        =   new byte[] {(byte)0x00,(byte) 0xA4, 0x04, 0x00, 0x09,(byte) 0xA0, 0x00, 0x00, 0x01, 0x51, 0x43, 0x52, 0x53, 0x00, 0x00 };
    public static final byte[] serviceManagerAid=   new byte[] {(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x03,(byte)0x96,(byte)0x4D,(byte)0x34,(byte)0x4D,(byte)0x24,(byte)0x00,(byte)0x81,(byte)0xDB,(byte)0x69,(byte)0x00};

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



}
