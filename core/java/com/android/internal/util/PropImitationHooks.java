/*
 * Copyright (C) 2022 Paranoid Android
 *           (C) 2023 ArrowOS
 *           (C) 2023 The LibreMobileOS Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util;

import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.R;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @hide
 */
public class PropImitationHooks {

    private static final String TAG = "PropImitationHooks";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static native void setFieldNative(Class<?> targetClass,
            Field field, String type, Object value);

    private static volatile boolean sSpoofPropsForProcess = false;
    private static final Map<String, String> sSystemProps = new ConcurrentHashMap<>();

    public static void setSpoofPropsForProcess(boolean enabled) {
        sSpoofPropsForProcess = enabled;
    }

    public static boolean isSpoofPropsForProcess() {
        return sSpoofPropsForProcess;
    }

    public static String getSpoofedProperty(String key) {
        if (key == null || !sSpoofPropsForProcess) return null;
        String val = sSystemProps.get(key);
        if (val != null) return val;
        for (Map.Entry<String, String> entry : sSystemProps.entrySet()) {
            String pattern = entry.getKey();
            if (pattern.startsWith("*") && key.endsWith(pattern.substring(1))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static void mapSystemProp(String key, String value) {
        sSystemProps.put(key, value);
        switch (key) {
            case "BRAND":
                sSystemProps.put("ro.product.brand", value);
                sSystemProps.put("ro.product.odm.brand", value);
                sSystemProps.put("ro.product.system.brand", value);
                sSystemProps.put("ro.product.system_ext.brand", value);
                sSystemProps.put("ro.product.vendor.brand", value);
                sSystemProps.put("ro.product.product.brand", value);
                break;
            case "DEVICE":
                sSystemProps.put("ro.product.device", value);
                sSystemProps.put("ro.product.odm.device", value);
                sSystemProps.put("ro.product.system.device", value);
                sSystemProps.put("ro.product.system_ext.device", value);
                sSystemProps.put("ro.product.vendor.device", value);
                sSystemProps.put("ro.product.product.device", value);
                break;
            case "FINGERPRINT":
                sSystemProps.put("ro.build.fingerprint", value);
                sSystemProps.put("ro.bootimage.build.fingerprint", value);
                sSystemProps.put("ro.odm.build.fingerprint", value);
                sSystemProps.put("ro.system.build.fingerprint", value);
                sSystemProps.put("ro.system_ext.build.fingerprint", value);
                sSystemProps.put("ro.vendor.build.fingerprint", value);
                sSystemProps.put("ro.product.build.fingerprint", value);
                break;
            case "ID":
                sSystemProps.put("ro.build.id", value);
                break;
            case "MANUFACTURER":
                sSystemProps.put("ro.product.manufacturer", value);
                sSystemProps.put("ro.product.odm.manufacturer", value);
                sSystemProps.put("ro.product.system.manufacturer", value);
                sSystemProps.put("ro.product.system_ext.manufacturer", value);
                sSystemProps.put("ro.product.vendor.manufacturer", value);
                sSystemProps.put("ro.product.product.manufacturer", value);
                break;
            case "MODEL":
                sSystemProps.put("ro.product.model", value);
                sSystemProps.put("ro.product.odm.model", value);
                sSystemProps.put("ro.product.system.model", value);
                sSystemProps.put("ro.product.system_ext.model", value);
                sSystemProps.put("ro.product.vendor.model", value);
                sSystemProps.put("ro.product.product.model", value);
                break;
            case "PRODUCT":
                sSystemProps.put("ro.product.name", value);
                sSystemProps.put("ro.product.odm.name", value);
                sSystemProps.put("ro.product.system.name", value);
                sSystemProps.put("ro.product.system_ext.name", value);
                sSystemProps.put("ro.product.vendor.name", value);
                sSystemProps.put("ro.product.product.name", value);
                break;
            case "VERSION.RELEASE":
                sSystemProps.put("ro.build.version.release", value);
                break;
            case "VERSION.INCREMENTAL":
                sSystemProps.put("ro.build.version.incremental", value);
                break;
            case "VERSION.SECURITY_PATCH":
                sSystemProps.put("ro.build.version.security_patch", value);
                sSystemProps.put("ro.vendor.build.security_patch", value);
                break;
            case "VERSION.DEVICE_INITIAL_SDK_INT":
                sSystemProps.put("ro.product.first_api_level", value);
                break;
            case "TYPE":
                sSystemProps.put("ro.build.type", value);
                break;
            case "TAGS":
                sSystemProps.put("ro.build.tags", value);
                break;
            default:
                if (key.startsWith("ro.")) {
                    sSystemProps.put(key, value);
                }
                break;
        }
    }

    private static void setPropValue(String key, Object value) {
        if (key == null || value == null) return;
        try {
            dlog("Setting prop " + key + " to " + value.toString());
            Class clazz = Build.class;
            if (key.startsWith("VERSION.")) {
                clazz = Build.VERSION.class;
                key = key.substring(8);
            }
            Field field = clazz.getDeclaredField(key);
            field.setAccessible(true);
            Class<?> fieldType = field.getType();
            Object typedValue;
            if (fieldType.equals(Integer.TYPE)) {
                typedValue = (value instanceof Integer) ? value : Integer.parseInt(value.toString());
            } else if (fieldType.equals(Long.TYPE)) {
                typedValue = (value instanceof Long) ? value : Long.parseLong(value.toString());
            } else if (fieldType.equals(Boolean.TYPE)) {
                typedValue = (value instanceof Boolean) ? value : Boolean.parseBoolean(value.toString());
            } else {
                typedValue = value.toString();
            }
            try {
                setFieldNative(clazz, field, fieldType.getName(), typedValue);
            } catch (Throwable t) {
                // Fallback to Java reflection if native library is unavailable
                field.set(null, typedValue);
            }
            field.setAccessible(false);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set prop " + key, e);
        }
    }

    private static final String PACKAGE_ARCORE = "com.google.ar.core";
    private static final String PACKAGE_GMS = "com.google.android.gms";
    private static final String PROCESS_GMS_UNSTABLE = PACKAGE_GMS + ".unstable";
    private static final String PACKAGE_NETFLIX = "com.netflix.mediaclient";
    private static final String PACKAGE_GPHOTOS = "com.google.android.apps.photos";

    private static final String FEATURE_NEXUS_PRELOAD =
            "com.google.android.apps.photos.NEXUS_PRELOAD";

    private static final Map<String, String> sPixelXLProps = Map.of(
        "PRODUCT", "marlin",
        "DEVICE", "marlin",
        "MANUFACTURER", "Google",
        "BRAND", "google",
        "HARDWARE", "marlin",
        "MODEL", "Pixel XL",
        "ID", "QP1A.191005.007.A3",
        "FINGERPRINT", "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys"
    );

    private static final Set<String> sPixelFeatures = Set.of(
        "PIXEL_2017_EXPERIENCE",
        "PIXEL_2017_PRELOAD",
        "PIXEL_2018_EXPERIENCE",
        "PIXEL_2018_PRELOAD",
        "PIXEL_2019_EXPERIENCE",
        "PIXEL_2019_MIDYEAR_EXPERIENCE",
        "PIXEL_2019_MIDYEAR_PRELOAD",
        "PIXEL_2019_PRELOAD",
        "PIXEL_2020_EXPERIENCE",
        "PIXEL_2020_MIDYEAR_EXPERIENCE",
        "PIXEL_2021_MIDYEAR_EXPERIENCE"
    );

    private static final Set<String> sTensorFeatures = Set.of(
        "PIXEL_2021_EXPERIENCE",
        "PIXEL_2022_EXPERIENCE",
        "PIXEL_2022_MIDYEAR_EXPERIENCE",
        "PIXEL_2023_EXPERIENCE",
        "PIXEL_2023_MIDYEAR_EXPERIENCE",
        "PIXEL_2024_EXPERIENCE",
        "PIXEL_2024_MIDYEAR_EXPERIENCE",
        "PIXEL_2025_EXPERIENCE",
        "PIXEL_2025_MIDYEAR_EXPERIENCE",
        "PIXEL_2026_EXPERIENCE"
    );

    private static final Map<String, String> sRecentPixelProps = Map.of(
        "BRAND", "google",
        "MANUFACTURER", "Google",
        "DEVICE", "mustang",
        "PRODUCT", "mustang",
        "HARDWARE", "mustang",
        "MODEL", "Pixel 10 Pro XL",
        "ID", "CP1A.260405.005",
        "FINGERPRINT", "google/mustang/mustang:16/CP1A.260405.005/15001963:user/release-keys"
    );

    private static final Set<String> sRecentPixelPackages = Set.of(
        "com.google.android.aicore",
        "com.google.android.apps.aiwallpapers",
        "com.google.android.apps.bard",
        "com.google.android.apps.customization.pixel",
        "com.google.android.apps.emojiwallpaper",
        "com.google.android.apps.pixel.agent",
        "com.google.android.apps.pixel.creativeassistant",
        "com.google.android.apps.pixel.nowplaying",
        "com.google.android.apps.wallpaper",
        "com.google.android.apps.wallpaper.pixel",
        "com.google.android.apps.weather",
        "com.google.android.googlequicksearchbox",
        "com.google.android.wallpaper.effects",
        "com.google.pixel.livewallpaper"
    );

    private static volatile String sStockFp, sNetflixModel;

    private static volatile String sProcessName;
    private static volatile boolean sIsGms, sIsPhotos, sIsRecentPixel;
    private static volatile boolean sForceTensor, sGphotosSpoof;

    public static void setProps(Context context) {
        final String packageName = context.getPackageName();
        final String processName = Application.getProcessName();

        if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(processName)) {
            Log.e(TAG, "Null package or process name");
            return;
        }

        final Resources res = context.getResources();
        if (res == null) {
            Log.e(TAG, "Null resources");
            return;
        }

        sStockFp = res.getString(R.string.config_stockFingerprint);
        sNetflixModel = res.getString(R.string.config_netflixSpoofModel);

        sProcessName = processName;
        sIsGms = packageName.equals(PACKAGE_GMS) && processName.equals(PROCESS_GMS_UNSTABLE);
        sIsPhotos = packageName.equals(PACKAGE_GPHOTOS);
        sIsRecentPixel = sRecentPixelPackages.contains(packageName);

        try {
            sForceTensor = !Process.isIsolated() && Settings.Secure.getInt(context.getContentResolver(),
                    Settings.Secure.PI_TENSOR_SPOOF, 0) == 1;
        } catch (Exception e) {
            sForceTensor = false;
        }

        try {
            sGphotosSpoof = !Process.isIsolated() && Settings.Secure.getInt(context.getContentResolver(),
                    Settings.Secure.GPHOTOS_SPOOF, 1) == 1;
        } catch (Exception e) {
            sGphotosSpoof = true;
        }

        /* Set Stock Fingerprint for ARCore
         * Set custom model for Netflix
         * Set Pixel XL for Google Photos
         * Set Recent Pixel for Tensor spoofing
         */
        if (!sStockFp.isEmpty() && packageName.equals(PACKAGE_ARCORE)) {
            dlog("Setting stock fingerprint for: " + packageName);
            setPropValue("FINGERPRINT", sStockFp);
            mapSystemProp("FINGERPRINT", sStockFp);
            sSpoofPropsForProcess = true;
        } else if (sGphotosSpoof && sIsPhotos) {
            dlog("Spoofing Pixel XL for Google Photos");
            sPixelXLProps.forEach((k, v) -> {
                setPropValue(k, v);
                mapSystemProp(k, v);
            });
            sSpoofPropsForProcess = true;
        } else if (sForceTensor && sIsRecentPixel) {
            dlog("Spoofing Recent Pixel for: " + packageName);
            sRecentPixelProps.forEach((k, v) -> {
                setPropValue(k, v);
                mapSystemProp(k, v);
            });
            sSpoofPropsForProcess = true;
        } else if (!sNetflixModel.isEmpty() && packageName.equals(PACKAGE_NETFLIX)) {
            dlog("Setting model to " + sNetflixModel + " for Netflix");
            setPropValue("MODEL", sNetflixModel);
            mapSystemProp("MODEL", sNetflixModel);
            sSpoofPropsForProcess = true;
        }
    }

    public static boolean hasSystemFeature(String name, boolean has) {
        if (sIsPhotos) {
            if (has && (sPixelFeatures.stream().anyMatch(name::contains)
                    || sTensorFeatures.stream().anyMatch(name::contains))) {
                dlog("Blocked system feature " + name + " for Google Photos");
                has = false;
            } else if (!has && name.equalsIgnoreCase(FEATURE_NEXUS_PRELOAD)) {
                dlog("Enabled system feature " + name + " for Google Photos");
                has = true;
            }
        }
        if (!has && (sIsRecentPixel || sIsGms)) {
            if (sForceTensor && sTensorFeatures.stream().anyMatch(name::contains)) {
                dlog("Enabled Tensor system feature " + name + " for: " + sProcessName);
                has = true;
            }
        }
        return has;
    }

    public static void dlog(String msg) {
        if (DEBUG) Log.d(TAG, "[" + sProcessName + "] " + msg);
    }
}
