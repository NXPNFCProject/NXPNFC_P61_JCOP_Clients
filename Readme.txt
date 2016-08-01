eseclient integration steps with the MW (Nfc/Spi)release

1. Copy the intf folder from eseclient folder and place it under directory frameworks/base/core/java/com/nxp/
2. Copy the eseclient folder and place it under directory frameworks/base/core/java/com/nxp/
3. delete the intf folder from eseclient
4. compile the framwork code base.

smart-card-service for ncihal2_l_dev

1.Copy the files from smart-card-service/src/org/simalliance/openmobileapi/service/terminals_l_dev/  and replace in smart-card-service/src/org/simalliance/openmobileapi/service/terminals/
2.Remove the folder smart-card-service/src/org/simalliance/openmobileapi/service/terminals_l_dev
3.In Android.mk change LOCAL_JAVA_LIBRARIES := core framework org.simalliance.openmobileapi to LOCAL_JAVA_LIBRARIES := framework org.simalliance.openmobileapi

ltsm-client for ncihalx-gen

1.Place LTSMClient directory under <Android build env>/packages/apps/
2.Compile the application
3.Install generated ltsmclient.apk



