/*
  Copyright (C) 2011, The Android Open Source Project
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


 * Contributed by: Giesecke & Devrient GmbH.


package org.simalliance.openmobileapi.service.terminals;

import android.content.Context;
import org.simalliance.openmobileapi.service.CardException;
import org.simalliance.openmobileapi.service.SmartcardService;
import org.simalliance.openmobileapi.service.Terminal;
import org.simalliance.openmobileapi.service.Util;
import org.simalliance.openmobileapi.service.security.arf.SecureElementException;


import android.os.ServiceManager;
import android.os.SystemProperties;
import android.util.Log;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.TelephonyProperties;

import java.util.MissingResourceException;
import java.util.NoSuchElementException;

public class UiccTerminal extends Terminal {

    private ITelephony manager = null;

    private int[] channelId = new int[20];

    private String currentSelectedFilePath = "";

    private byte[] mAtr = null;

    public UiccTerminal(Context context) {
        super(SmartcardService._UICC_TERMINAL, context);

        try {
            manager = ITelephony.Stub.asInterface(ServiceManager
                    .getService(Context.TELEPHONY_SERVICE));
        } catch (Exception ex) {
        }

        for (int i = 0; i < channelId.length; i++) {
            channelId[i] = 0;
        }
    }

    @Override
    public boolean isCardPresent() throws CardException {
        String prop = SystemProperties
                .get(TelephonyProperties.PROPERTY_SIM_STATE);
        if ("READY".equals(prop)) {
            return true;
        }
        return false;
    }

    @Override
    protected void internalConnect() throws CardException {
        if (manager == null) {
            throw new CardException("Cannot connect to Telephony Service");
        }
        mIsConnected = true;
    }

    @Override
    protected void internalDisconnect() throws CardException {
    }

    private byte[] stringToByteArray(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return b;
    }

    private String byteArrayToString(byte[] b, int start) {
        StringBuffer s = new StringBuffer();
        for (int i = start; i < b.length; i++) {
            s.append(Integer.toHexString(0x100 + (b[i] & 0xff)).substring(1));
        }
        return s.toString();
    }

    *//**
     * Clear the channel number.
     *
     * @param cla
     *
     * @return the cla without channel number
     *//*
    private byte clearChannelNumber(byte cla) {
        // bit 7 determines which standard is used
        boolean isFirstInterindustryClassByteCoding = ((cla & 0x40) == 0x00);

        if (isFirstInterindustryClassByteCoding) {
            // First Interindustry Class Byte Coding
            // see 11.1.4.1: channel number is encoded in the 2 rightmost bits
            return (byte) (cla & 0xFC);
        } else {
            // Further Interindustry Class Byte Coding
            // see 11.1.4.2: channel number is encoded in the 4 rightmost bits
            return (byte) (cla & 0xF0);
        }
    }

    @Override
    protected byte[] internalTransmit(byte[] command) throws CardException {
        int cla = clearChannelNumber(command[0]) & 0xff;
        int ins = command[1] & 0xff;
        int p1 = command[2] & 0xff;
        int p2 = command[3] & 0xff;
        int p3 = -1;
        if (command.length > 4) {
            p3 = command[4] & 0xff;
        }
        String data = null;
        if (command.length > 5) {
            data = byteArrayToString(command, 5);
        }

        int channelNumber = parseChannelNumber(command[0]);

        if (channelNumber == 0) {

            try {
                String response = manager.transmitIccBasicChannel(cla, ins, p1,
                        p2, p3, data);
                return stringToByteArray(response);
            } catch (Exception ex) {
                throw new CardException("transmit command failed");
            }

        } else {
            if ((channelNumber > 0) && (channelId[channelNumber] == 0)) {
                throw new CardException("channel not open");
            }

            try {
                String response = manager.transmitIccLogicalChannel(
                        cla, ins, channelId[channelNumber], p1, p2, p3, data);
                return stringToByteArray(response);
            } catch (Exception ex) {
                throw new CardException("transmit command failed");
            }
        }
    }

    @Override
    public byte[] getAtr() {
        if (mAtr == null) {
            try {
                String atr = manager.getIccAtr();
                if (atr.equals("")) {
                    mAtr = null;
                } else {
                    mAtr = stringToByteArray(atr);
                }
            } catch (Exception ex) {
                mAtr = null;
            }
        }
        return mAtr;
    }

    *//**
     * Exchanges APDU (SELECT, READ/WRITE) to the  given EF by File ID and file
     * path via iccIO.
     *
     * The given command is checked and might be rejected.
     *
     * @param fileID
     * @param filePath
     * @param cmd
     * @return
     *//*
    @Override
    public byte[] simIOExchange(int fileID, String filePath, byte[] cmd)
            throws Exception {
        try {
            int ins = 0;
            int p1 = cmd[2] & 0xff;
            int p2 = cmd[3] & 0xff;
            int p3 = cmd[4] & 0xff;
            switch(cmd[1]) {
                case (byte) 0xB0:
                    ins = 176;
                    break;
                case (byte) 0xB2:
                    ins = 178;
                    break;
                case (byte) 0xA4:
                    ins = 192;
                    p1 = 0;
                    p2 = 0;
                    p3 = 15;
                    break;
                default:
                    throw new SecureElementException("Unknown SIM_IO command");
            }

            if (filePath != null && filePath.length() > 0) {
                currentSelectedFilePath = filePath;
            }

            byte[] ret = manager.transmitIccSimIO(
                    fileID, ins, p1, p2, p3, currentSelectedFilePath);

            return ret;
        } catch (Exception e) {
            throw new Exception("SIM IO access error");
        }
    }

    *//**
     * Extracts the channel number from a CLA byte. Specified in GlobalPlatform
     * Card Specification 2.2.0.7: 11.1.4 Class Byte Coding.
     *
     * @param cla
     *            the command's CLA byte
     * @return the channel number within [0x00..0x0F]
     *//*
    private int parseChannelNumber(byte cla) {
        // bit 7 determines which standard is used
        boolean isFirstInterindustryClassByteCoding = ((cla & 0x40) == 0x00);

        if (isFirstInterindustryClassByteCoding) {
            // First Interindustry Class Byte Coding
            // see 11.1.4.1: channel number is encoded in the 2 rightmost bits
            return cla & 0x03;
        } else {
            // Further Interindustry Class Byte Coding
            // see 11.1.4.2: channel number is encoded in the 4 rightmost bits
            return (cla & 0x0F) + 4;
        }
    }

    @Override
    protected int internalOpenLogicalChannel() throws Exception {
        // Select response will always be null because no select command will be
        // issued.
        mSelectResponse =  null;
        for (int i = 1; i < channelId.length; i++) {
            if (channelId[i] == 0) {
                channelId[i] = manager.openIccLogicalChannel("");

                if (!(channelId[i] > 0)) {
                    // channelId[i] == 0, means an error occured.
                    channelId[i] = 0;
                    int lastError = manager.getLastError();

                    if (lastError == 2) {
                        throw new MissingResourceException(
                                "all channels are used", "", "");
                    }
                    if (lastError == 3) {
                        throw new NoSuchElementException("applet not found");
                    }
                    throw new CardException("open channel failed");
                }

                return i;
            }
        }
        throw new MissingResourceException("out of channels", "", "");
    }

    @Override
    protected int internalOpenLogicalChannel(byte[] aid) throws Exception {

        if (aid == null) {
            throw new NullPointerException("aid must not be null");
        }
        mSelectResponse = null;
        for (int i = 1; i < channelId.length; i++) {
            if (channelId[i] == 0) {
                channelId[i] = manager.openIccLogicalChannel(
                        byteArrayToString(aid, 0));

                if (!(channelId[i] > 0)) {
                    // channelId[i] == 0, means an error occured.
                    channelId[i] = 0;
                    int lastError = manager.getLastError();

                    if (lastError == 2) {
                        throw new MissingResourceException(
                                "all channels are used", "", "");
                    }
                    if (lastError == 3) {
                        throw new NoSuchElementException("applet not found");
                    }
                    throw new CardException("open channel failed");
                }

                // If everything succeeded, set the select response.
                mSelectResponse = stringToByteArray(
                                    manager.getIccSelectResponse());
                return i;
            }
        }
        throw new MissingResourceException("out of channels", "", "");
    }

    @Override
    protected void internalCloseLogicalChannel(int channelNumber)
            throws CardException {
        if (channelNumber == 0) {
            return;
        }
        if (channelId[channelNumber] == 0) {
            throw new CardException("channel not open");
        }
        try {
            if (!manager.closeIccLogicalChannel(channelId[channelNumber])) {
                throw new CardException("close channel failed");
            }
        } catch (Exception ex) {
            throw new CardException("close channel failed");
        }
        channelId[channelNumber] = 0;
    }
}

*/