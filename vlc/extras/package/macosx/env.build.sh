#!/bin/bash

vlcGetTriplet() {
    local OSX_KERNELVERSION=$(uname -r | cut -d. -f1)
    if [ ! -z "$VLC_FORCE_KERNELVERSION" ]; then
        OSX_KERNELVERSION="$VLC_FORCE_KERNELVERSION"
    fi

    local LOCAL_ARCH="x86_64"
    if [ -n "$ARCH" ]; then
        LOCAL_ARCH="$ARCH"
    fi

    echo "$LOCAL_ARCH-apple-darwin$OSX_KERNELVERSION"
}

# Gets VLCs root dir based on location of this file, also follow symlinks
vlcGetRootDir() {
    local SOURCE="${BASH_SOURCE[0]}"
    while [ -h "$SOURCE" ]; do
        local DIR="$(cd -P "$( dirname "$SOURCE" )" && pwd)"
        SOURCE="$(readlink "$SOURCE")"
        [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE" # Relative symlink needs dir prepended
    done
    echo "$(cd -P "$(dirname "$SOURCE")/../../../" && pwd)"
}

vlcSetBaseEnvironment() {
    local LOCAL_TRIPLET="$TRIPLET"
    if [ -z "$LOCAL_TRIPLET" ]; then
        LOCAL_TRIPLET="$(vlcGetTriplet)"
    fi

    local VLC_ROOT_DIR="$(vlcGetRootDir)"

    echo "Setting base environment"
    echo "Using VLC root dir $VLC_ROOT_DIR and triplet $LOCAL_TRIPLET"

    export CC="$(xcrun --find clang)"
    export CXX="$(xcrun --find clang++)"
    export OBJC="$(xcrun --find clang)"
    export PATH="${VLC_ROOT_DIR}/extras/tools/build/bin:${VLC_ROOT_DIR}/contrib/${LOCAL_TRIPLET}/bin:${VLC_PATH}:/bin:/sbin:/usr/bin:/usr/sbin"
}

vlcSetSymbolEnvironment() {
    echo "Setting symbol environment"

    # The following symbols do not exist on the minimal macOS version (10.7), so they are disabled
    # here. This allows compilation also with newer macOS SDKs.
    # Added symbols in 10.13
    export ac_cv_func_open_wmemstream=no
    export ac_cv_func_fmemopen=no
    export ac_cv_func_open_memstream=no
    export ac_cv_func_futimens=no
    export ac_cv_func_utimensat=no

    # Added symbols between 10.11 and 10.12
    export ac_cv_func_basename_r=no
    export ac_cv_func_clock_getres=no
    export ac_cv_func_clock_gettime=no
    export ac_cv_func_clock_settime=no
    export ac_cv_func_dirname_r=no
    export ac_cv_func_getentropy=no
    export ac_cv_func_mkostemp=no
    export ac_cv_func_mkostemps=no

    # Added symbols between 10.7 and 10.11
    export ac_cv_func_ffsll=no
    export ac_cv_func_flsll=no
    export ac_cv_func_fdopendir=no
    export ac_cv_func_openat=no
    export ac_cv_func_fstatat=no
    export ac_cv_func_readlinkat=no

    # libnetwork does not exist yet on 10.7 (used by libcddb)
    export ac_cv_lib_network_connect=no
}

vlcSetContribEnvironment() {
    if [ -z "$1" ]; then
        return 1
    fi
    local MINIMAL_OSX_VERSION="$1"

    if [ -z "$SDKROOT" ]; then
        export SDKROOT="$(xcrun --show-sdk-path)"
    fi

    echo "Setting contrib environment with minimum macOS version $MINIMAL_OSX_VERSION and SDK $SDKROOT"

    # Select avcodec flavor to compile contribs with
    export USE_FFMPEG=1

    # Usually, VLCs contrib libraries do not support partial availability at runtime.
    # Forcing those errors has two reasons:
    # - Some custom configure scripts include the right header for testing availability.
    #   Those configure checks fail (correctly) with those errors, and replacements are
    #   enabled. (e.g. ffmpeg)
    # - This will fail the build if a partially available symbol is added later on
    #   in contribs and not mentioned in the list of symbols above.
    export CFLAGS="-Werror=partial-availability"
    export CXXFLAGS="-Werror=partial-availability"
    export OBJCFLAGS="-Werror=partial-availability"

    export EXTRA_CFLAGS="-isysroot $SDKROOT -mmacosx-version-min=$MINIMAL_OSX_VERSION -DMACOSX_DEPLOYMENT_TARGET=$MINIMAL_OSX_VERSION"
    export EXTRA_LDFLAGS="-Wl,-syslibroot,$SDKROOT -mmacosx-version-min=$MINIMAL_OSX_VERSION -isysroot $SDKROOT -DMACOSX_DEPLOYMENT_TARGET=$MINIMAL_OSX_VERSION"
    export XCODE_FLAGS="MACOSX_DEPLOYMENT_TARGET=$MINIMAL_OSX_VERSION -sdk $SDKROOT WARNING_CFLAGS=-Werror=partial-availability"
}

vlcUnsetContribEnvironment() {
    echo "Unsetting contrib flags, prepare VLC flags"

    unset CFLAGS
    unset CXXFLAGS
    unset OBJCFLAGS

    unset EXTRA_CFLAGS
    unset EXTRA_LDFLAGS
    unset XCODE_FLAGS

    # Enable debug symbols by default
    export CFLAGS="-g"
    export CXXFLAGS="-g"
    export OBJCFLAGS="-g"
}


# Parameter handling

# First parameter: mode to use this script:
# vlc (default): auto-setup environment suitable for building vlc itself
# contrib: auto-setup environment suitable for building vlc contribs
# none: do not perform any auto-setup (used for scripts)
VLC_ENV_MODE="vlc"
if [ "$1" = "contrib" ]; then
    VLC_ENV_MODE="contrib"
fi
if [ "$1" = "none" ]; then
    VLC_ENV_MODE="none"
fi

if [ "$VLC_ENV_MODE" = "contrib" ]; then
    vlcSetBaseEnvironment
    vlcSetSymbolEnvironment
    vlcSetContribEnvironment "10.10"
elif [ "$VLC_ENV_MODE" = "vlc" ]; then
    vlcSetBaseEnvironment
    vlcSetSymbolEnvironment
    vlcUnsetContribEnvironment
fi
