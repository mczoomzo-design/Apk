package com.zoomzo.noisemeter

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * แอปวัดเสียงห้องเรียน
 *
 * หน้าตา (มิเตอร์/ตั้งค่า/แจ้งเตือน) เป็นหน้าเว็บใน assets/index.html แสดงผ่าน WebView
 * ส่วน "การอัดเสียงจากไมโครโฟน" ทำด้วยโค้ด Android เอง (AudioRecord) แล้วส่งค่าระดับเสียง
 * เข้าไปให้หน้าเว็บผ่าน window.onNativeLevel(...) — วิธีนี้ใช้สิทธิ์ RECORD_AUDIO ของแอปตรง ๆ
 * ไม่ต้องพึ่ง getUserMedia ของ WebView (ซึ่งบางเครื่องเปิดไมค์ไม่ได้ "Could not start audio source")
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var assetLoader: WebViewAssetLoader

    @Volatile private var recording = false
    private var recordThread: Thread? = null
    private var pendingStart = false

    private val requestMic =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && pendingStart) {
                pendingStart = false
                startRecording()
            } else if (!granted) {
                pendingStart = false
                notifyError("ไม่ได้รับอนุญาตให้ใช้ไมโครโฟน")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // เปิดจอค้างไว้ระหว่างใช้งาน (เหมาะกับการเปิดค้างหน้าห้องเรียน)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false // ให้เสียงบี๊บเล่นได้
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
        }

        webView.addJavascriptInterface(Bridge(), "NativeAudio")
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    /** สะพานให้ JavaScript เรียกฝั่งเนทีฟ */
    inner class Bridge {
        @JavascriptInterface
        fun start() { runOnUiThread { ensureMicThenStart() } }

        @JavascriptInterface
        fun stop() { stopRecording() }
    }

    private fun ensureMicThenStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startRecording()
        } else {
            pendingStart = true
            requestMic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        if (recording) return

        val sampleRate = 44100
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) { notifyError("อุปกรณ์ไม่รองรับการอัดเสียง"); return }

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, 4096)
            )
        } catch (e: Exception) {
            notifyError("เปิดไมโครโฟนไม่สำเร็จ"); return
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release(); notifyError("ไมโครโฟนไม่พร้อมใช้งาน (อาจถูกแอปอื่นใช้อยู่)"); return
        }

        try {
            recorder.startRecording()
        } catch (e: Exception) {
            recorder.release(); notifyError("เริ่มบันทึกเสียงไม่สำเร็จ"); return
        }

        recording = true
        recordThread = thread(start = true, name = "noise-meter-audio") {
            val buf = ShortArray(2048)
            var lastPost = 0L
            try {
                while (recording) {
                    val n = recorder.read(buf, 0, buf.size)
                    if (n > 0) {
                        var sum = 0.0
                        for (i in 0 until n) {
                            val v = buf[i] / 32768.0
                            sum += v * v
                        }
                        val rms = sqrt(sum / n)
                        var raw = (20.0 * log10(rms + 1e-7) + 60.0) * 1.7
                        if (raw < 0.0) raw = 0.0
                        if (raw > 100.0) raw = 100.0

                        val now = System.currentTimeMillis()
                        if (now - lastPost >= 50) { // ~20 ครั้ง/วินาที
                            lastPost = now
                            postLevel(raw)
                        }
                    }
                }
            } finally {
                try { recorder.stop() } catch (e: Exception) {}
                recorder.release()
            }
        }
    }

    private fun stopRecording() {
        recording = false
        recordThread = null
    }

    private fun postLevel(level: Double) {
        val js = "window.onNativeLevel && window.onNativeLevel(${String.format(Locale.US, "%.1f", level)});"
        webView.post { webView.evaluateJavascript(js, null) }
    }

    private fun notifyError(msg: String) {
        recording = false
        val js = "window.onNativeError && window.onNativeError(${jsString(msg)});"
        webView.post { webView.evaluateJavascript(js, null) }
    }

    private fun jsString(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    override fun onDestroy() {
        stopRecording()
        webView.destroy()
        super.onDestroy()
    }
}
