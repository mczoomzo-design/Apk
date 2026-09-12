package com.photobooth.sticker;

import android.app.Activity;
import android.view.WindowManager;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Kiosk helpers: report the build flavor and pin/unpin the screen (lock task). */
@CapacitorPlugin(name = "Kiosk")
public class KioskPlugin extends Plugin {

    @PluginMethod
    public void info(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("kiosk", BuildConfig.KIOSK);   // true only in the kiosk flavor
        call.resolve(ret);
    }

    @PluginMethod
    public void lock(PluginCall call) {
        final Activity a = getActivity();
        if (a == null) { call.reject("no_activity"); return; }
        a.runOnUiThread(() -> {
            try {
                a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                a.startLockTask();   // screen pinning (hard-locks when app is Device Owner)
            } catch (Exception e) { /* pinning may be unavailable; ignore */ }
        });
        call.resolve();
    }

    @PluginMethod
    public void unlock(PluginCall call) {
        final Activity a = getActivity();
        if (a == null) { call.reject("no_activity"); return; }
        a.runOnUiThread(() -> {
            try { a.stopLockTask(); } catch (Exception e) {}
        });
        call.resolve();
    }
}
