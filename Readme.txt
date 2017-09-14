eseclient integration steps with the MW (Nfc/Spi)release

1. Copy the intf folder from eseclient folder and place it under directory frameworks/base/core/java/com/nxp/
2. Copy the eseclient folder and place it under directory frameworks/base/core/java/com/nxp/
3. delete the intf folder from eseclient
4. compile the framwork code base.

smart-card-service for Android-KK or earlier

1.Remove the folder smart-card-service/src/org/simalliance/openmobileapi/service/terminals
2.rename smart-card-service/src/org/simalliance/openmobileapi/service/terminals_legacy/  to smart-card-service/src/org/simalliance/openmobileapi/service/terminals/
3.In Android.mk change LOCAL_JAVA_LIBRARIES := framework org.simalliance.openmobileapi to LOCAL_JAVA_LIBRARIES := core framework org.simalliance.openmobileapi

smart-card-service for Android-L or later
1.Remove the folder smart-card-service/src/org/simalliance/openmobileapi/service/terminals_legacy

ltsm-client for ncihalx-gen

1.Place LTSMClient directory under <Android build env>/packages/apps/
2.Compile the application
3.Install generated ltsmclient.apk



