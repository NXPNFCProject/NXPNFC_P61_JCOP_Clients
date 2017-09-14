LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

ANDROID_VER := $(subst ., , $(PLATFORM_VERSION))
ANDROID_VER := $(word 1, $(ANDROID_VER))
ifeq ($(shell expr $(ANDROID_VER) \>= 8), 1)
ANDROID_O_OR_LATER := TRUE
else
ANDROID_O_OR_LATER := FALSE
endif 

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_AIDL_INCLUDES := packages/apps/SmartCardService/openmobileapi/src/org/simalliance/openmobileapi/service

LOCAL_PACKAGE_NAME := SmartcardService
LOCAL_CERTIFICATE := platform

ifeq ($(ANDROID_O_OR_LATER), TRUE)
LOCAL_JAVA_LIBRARIES := framework org.simalliance.openmobileapi com.nxp.nfc
else
LOCAL_JAVA_LIBRARIES := framework org.simalliance.openmobileapi
endif

LOCAL_PROGUARD_ENABLED := disabled

include $(BUILD_PACKAGE)

include $(call all-makefiles-under,$(LOCAL_PATH))
