LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_STATIC_JAVA_LIBRARIES += guava com.android.vcard \
                               LTSMLibs \
                               LTSMLibs1

# Only compile source java files in this apk.
LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_PACKAGE_NAME := ltsmclient
LOCAL_SRC_FILES +=src/com/nxp/ltsm/ltsmclient/ILTSMClient.aidl
LOCAL_PRIVILEGED_MODULE := true

#LOCAL_SDK_VERSION := current

include $(BUILD_PACKAGE)

include $(CLEAR_VARS)

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := LTSMLibs:libs/gson-2.2.4.jar LTSMLibs1:libs/commons-codec-1.9.jar

include $(BUILD_MULTI_PREBUILT)
