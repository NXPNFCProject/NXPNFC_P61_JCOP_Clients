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

import java.util.ArrayList;

public class ltsmRegistry {
    public  int maxVcCount;
    public int vcCount;
    ArrayList<Integer>vcEntry = new ArrayList<Integer>();
    ArrayList<String> shaWalletName = new ArrayList<String>();
    ArrayList<String> vcUid = new ArrayList<String>();
    ArrayList<String> walletName = new ArrayList<String>();
    ArrayList<Boolean> vcCreateStatus = new ArrayList<Boolean>();
    ArrayList<Boolean> vcActivatedStatus = new ArrayList<Boolean>();
    ArrayList<String> vcType = new ArrayList<String>();
    ArrayList<Integer> deleteVcEntries = new ArrayList<Integer>();


    public ltsmRegistry(int MaxVC) {
        maxVcCount = MaxVC;
        vcCount = 0;
        vcEntry.clear();
        shaWalletName.clear();
        walletName.clear();
        vcUid.clear();
        vcCreateStatus.clear();
        vcActivatedStatus.clear();
        vcType.clear();
        deleteVcEntries.clear();
    }


}
