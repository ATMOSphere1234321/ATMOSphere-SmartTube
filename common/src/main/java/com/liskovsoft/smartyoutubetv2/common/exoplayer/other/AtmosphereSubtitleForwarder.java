/*
 * Copyright (C) 2026 ATMOSphere
 * SPDX-License-Identifier: Apache-2.0
 *
 * AtmosphereSubtitleForwarder — QA 26.04.02 SmartTube integration.
 *
 * When ATMOSphere's VideoOutputManagerService has routed the current
 * SmartTube video decoder to the secondary display, we must forward
 * every ExoPlayer Cue to VOM.routeSubtitleCue so Presenter can render
 * subtitles on the TV instead of (or in addition to) this SubtitleView
 * on the primary tablet.
 *
 * The framework IVideoOutputManager AIDL interface is @hide, so this
 * forwarder uses reflection to hit it — the fork APK therefore has no
 * compile-time dependency on the framework-level AIDL and can still be
 * built outside of an AOSP tree (important for local `./gradlew
 * assembleStstableRelease` runs and for our ATMOSphere-SmartTube CI).
 *
 * When the framework class isn't reachable at runtime (e.g. the fork
 * is running on a stock Android device for development), every method
 * is a no-op and the local SubtitleView continues to render as normal.
 *
 * Entry point: AtmosphereSubtitleForwarder.forwardCues(packageName, cues).
 * Intended caller: SubtitleManager.onCues().
 */
package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.os.IBinder;
import android.util.Log;

import com.google.android.exoplayer2.text.Cue;

import java.lang.reflect.Method;
import java.util.List;

public final class AtmosphereSubtitleForwarder {
    private static final String TAG = "ATMOSphere::STFwd";
    private static final String SERVICE_NAME = "video_output";
    private static final String STUB_CLASS =
            "android.media.IVideoOutputManager$Stub";
    private static final String ROUTE_METHOD = "routeSubtitleCue";

    /** Cache the resolved IBinder / stub asInterface / routeSubtitleCue
     *  across calls. ExoPlayer may call onCues() 25+ times per second. */
    private static volatile IBinder sBinder;
    private static volatile Method sAsInterfaceMethod;
    private static volatile Method sRouteCueMethod;
    private static volatile boolean sUnavailable;

    private AtmosphereSubtitleForwarder() {}

    /**
     * Forward a list of ExoPlayer cues to VideoOutputManagerService.
     * Returns true if at least one cue was forwarded, false if routing
     * is not active or the framework service is unreachable. The caller
     * should continue to render locally either way — SubtitleView and
     * the TV Presenter can coexist; framework's onHideSubtitlesOnPrimary
     * callback blanks the primary when appropriate.
     */
    public static boolean forwardCues(String packageName, List<Cue> cues) {
        if (sUnavailable) {
            return false;
        }
        if (cues == null || cues.isEmpty() || packageName == null) {
            // Empty cue list typically means "clear subtitles" — pass
            // through as an empty string so Presenter dismisses its
            // current display cue.
            return dispatch(packageName, "");
        }
        StringBuilder sb = new StringBuilder();
        for (Cue c : cues) {
            if (c == null || c.text == null) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(c.text);
        }
        return dispatch(packageName, sb.toString());
    }

    private static boolean dispatch(String packageName, String text) {
        try {
            Method routeFn = resolveRouteFn();
            if (routeFn == null) {
                return false;
            }
            Object vom = resolveVom();
            if (vom == null) {
                return false;
            }
            // duration=0 → Presenter auto-hides on next cue or after the
            //   configured timeout; startTimeUs=0 because we don't track
            //   media clock here (VOM de-dups anyway).
            // styleJson="" → Presenter falls back to its default style.
            Object result = routeFn.invoke(vom, packageName, text,
                    (long) 0L, (long) 0L, "");
            return Boolean.TRUE.equals(result);
        } catch (Throwable t) {
            // First failure: log once + mark unavailable so we don't burn
            // CPU on every cue dispatch.
            if (!sUnavailable) {
                Log.w(TAG, "forwardCues failed, will no-op until process restart: "
                        + t.getMessage());
                sUnavailable = true;
            }
            return false;
        }
    }

    private static Method resolveRouteFn() throws Exception {
        Method cached = sRouteCueMethod;
        if (cached != null) return cached;
        Class<?> stub = Class.forName(STUB_CLASS);
        Method asInterface = stub.getMethod("asInterface", IBinder.class);
        sAsInterfaceMethod = asInterface;
        // Probe the interface class once to resolve the routeSubtitleCue method.
        IBinder binder = getBinder();
        if (binder == null) return null;
        Object vom = asInterface.invoke(null, binder);
        if (vom == null) return null;
        Method route = vom.getClass().getMethod(ROUTE_METHOD,
                String.class, String.class,
                Long.TYPE, Long.TYPE,
                String.class);
        sRouteCueMethod = route;
        return route;
    }

    private static Object resolveVom() throws Exception {
        IBinder binder = getBinder();
        if (binder == null) return null;
        Method asInterface = sAsInterfaceMethod;
        if (asInterface == null) {
            asInterface = Class.forName(STUB_CLASS)
                    .getMethod("asInterface", IBinder.class);
            sAsInterfaceMethod = asInterface;
        }
        return asInterface.invoke(null, binder);
    }

    private static IBinder getBinder() {
        IBinder cached = sBinder;
        if (cached != null && cached.isBinderAlive()) {
            return cached;
        }
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            IBinder fresh = (IBinder) sm.getMethod("getService", String.class)
                    .invoke(null, SERVICE_NAME);
            sBinder = fresh;
            return fresh;
        } catch (Throwable t) {
            return null;
        }
    }
}
