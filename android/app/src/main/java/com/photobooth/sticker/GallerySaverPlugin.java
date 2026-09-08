package com.photobooth.sticker;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/** Saves a base64 PNG straight into the device photo gallery (Pictures/PhotoBooth). */
@CapacitorPlugin(name = "GallerySaver")
public class GallerySaverPlugin extends Plugin {

    @PluginMethod
    public void save(PluginCall call) {
        String data = call.getString("data", "");
        if (data == null || data.isEmpty()) { call.reject("no_data"); return; }

        // Accept a full data-URL ("data:image/png;base64,....") or raw base64.
        int comma = data.indexOf(',');
        if (data.startsWith("data:") && comma >= 0) data = data.substring(comma + 1);

        String name = call.getString("name", "photobooth_" + System.currentTimeMillis());
        if (!name.toLowerCase().endsWith(".png")) name = name + ".png";

        try {
            byte[] bytes = Base64.decode(data, Base64.DEFAULT);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Scoped storage: no runtime permission needed.
                ContentResolver resolver = getContext().getContentResolver();
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/PhotoBooth");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);

                Uri collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                Uri item = resolver.insert(collection, values);
                if (item == null) { call.reject("insert_failed"); return; }

                try (OutputStream out = resolver.openOutputStream(item)) {
                    if (out == null) { call.reject("open_stream_failed"); return; }
                    out.write(bytes);
                    out.flush();
                }
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(item, values, null, null);

                JSObject ret = new JSObject();
                ret.put("uri", item.toString());
                call.resolve(ret);
            } else {
                // Android 9 and below: write to public Pictures then index it.
                File dir = new File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                        "PhotoBooth");
                if (!dir.exists() && !dir.mkdirs()) { call.reject("mkdir_failed"); return; }
                File f = new File(dir, name);
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(bytes);
                    out.flush();
                }
                MediaScannerConnection.scanFile(getContext(),
                        new String[]{ f.getAbsolutePath() }, new String[]{ "image/png" }, null);

                JSObject ret = new JSObject();
                ret.put("uri", Uri.fromFile(f).toString());
                call.resolve(ret);
            }
        } catch (Exception e) {
            call.reject("save_failed: " + e.getMessage());
        }
    }
}
