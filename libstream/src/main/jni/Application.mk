APP_BUILD_SCRIPT := $(call my-dir)Android.mk
APP_MODULES := libuv

APP_OPTIM        := release 
APP_CFLAGS       += -O3
APP_STL := gnustl_static
NDK_TOOLCHAIN_VERSION = 4.9

APP_PLATFORM := android-14