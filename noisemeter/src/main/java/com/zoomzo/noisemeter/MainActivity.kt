package com.zoomzo.noisemeter

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader

/**
 * แอปวัดเสียงห้องเรียน — ห่อหน้าเว็บ (assets/index.html) ไว้ใน WebView
 *
 * ใช้ WebViewAssetLoader เสิร์ฟไฟล์ผ่าน https://appassets.androidplatform.net/
 * เพื่อให้เป็น "secure context" — getUserMedia (ไมโครโฟน) จึงทำงานได้ใน WebView
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var assetLoader: WebViewAssetLoader

    // ขอสิทธิ์ไมโครโฟนของระบบ แล้วโหลดหน้าเว็บไม่ว่าจะอนุญาตหรือไม่
    private val requestMic =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            loadApp()
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
            // ให้เสียงบี๊บ/AudioContext เล่นได้โดยไม่ต้องรอ user gesture เพิ่ม
            mediaPlaybackRequiresUserGesture = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    if (request.resources.any { it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }) {
                        request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                    } else {
                        request.deny()
                    }
                }
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            loadApp()
        } else {
            requestMic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun loadApp() {
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
