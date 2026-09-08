package com.photobooth.sticker;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Base64;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.OutputStream;
import java.util.UUID;

/** Bridge for printing the photo strip to an 80mm Bluetooth (Classic/SPP) receipt printer. */
@CapacitorPlugin(name = "BluetoothPrinter")
public class BluetoothPrinterPlugin extends Plugin {

    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int PRINT_WIDTH = 576; // 80mm @ 203 DPI

    private boolean hasBtPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return getContext().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    @PluginMethod
    public void listDevices(PluginCall call) {
        if (!hasBtPermission()) { call.reject("no_bluetooth_permission"); return; }
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) { call.reject("no_bluetooth"); return; }
        if (!adapter.isEnabled()) { call.reject("bluetooth_off"); return; }
        JSArray arr = new JSArray();
        try {
            for (BluetoothDevice d : adapter.getBondedDevices()) {
                JSObject o = new JSObject();
                o.put("name", d.getName() == null ? d.getAddress() : d.getName());
                o.put("address", d.getAddress());
                arr.put(o);
            }
        } catch (SecurityException e) { call.reject("security: " + e.getMessage()); return; }
        JSObject ret = new JSObject();
        ret.put("devices", arr);
        call.resolve(ret);
    }

    @PluginMethod
    public void print(final PluginCall call) {
        if (!hasBtPermission()) { call.reject("no_bluetooth_permission"); return; }
        final String image = call.getString("image");
        final int copies = call.getInt("copies", 1);
        final String address = call.getString("address", null);
        if (image == null) { call.reject("no_image"); return; }

        new Thread(new Runnable() {
            @Override public void run() {
                BluetoothSocket socket = null;
                try {
                    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                    if (adapter == null || !adapter.isEnabled()) { call.reject("bluetooth_off"); return; }

                    BluetoothDevice device = null;
                    if (address != null && !address.isEmpty()) {
                        device = adapter.getRemoteDevice(address);
                    } else {
                        for (BluetoothDevice d : adapter.getBondedDevices()) { device = d; break; }
                    }
                    if (device == null) { call.reject("no_device"); return; }

                    socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                    adapter.cancelDiscovery();
                    socket.connect();
                    OutputStream out = socket.getOutputStream();

                    String b64 = image.contains(",") ? image.substring(image.indexOf(",") + 1) : image;
                    byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                    Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bmp == null) { call.reject("bad_image"); return; }
                    byte[] raster = EscPos.bitmapToRaster(bmp, PRINT_WIDTH);

                    int n = Math.max(1, Math.min(copies, 10));
                    for (int i = 0; i < n; i++) {
                        out.write(EscPos.INIT);
                        out.write(EscPos.ALIGN_CENTER);
                        out.write(raster);
                        out.write(EscPos.feed(4));
                        out.write(EscPos.CUT);
                        out.flush();
                        Thread.sleep(500);
                    }
                    Thread.sleep(300);

                    JSObject ret = new JSObject();
                    ret.put("printed", n);
                    call.resolve(ret);
                } catch (Exception e) {
                    call.reject("print_failed: " + e.getMessage());
                } finally {
                    try { if (socket != null) socket.close(); } catch (Exception ignored) {}
                }
            }
        }).start();
    }
}
