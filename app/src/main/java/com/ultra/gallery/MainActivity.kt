package com.ultra.gallery

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = WebViewClient()
        web.webChromeClient = WebChromeClient()

        web.addJavascriptInterface(object {
            @JavascriptInterface
            fun refresh() {
                runOnUiThread { loadMedia() }
            }
        }, "AndroidGallery")

        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
    }

    private fun loadMedia() {
        val permissions = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 77)
            return
        }

        val result = JSONArray()
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DISPLAY_NAME
        )

        val selection =
            "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"

        val args = arrayOf("1", "3")

        contentResolver.query(uri, projection, selection, args, null)?.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val type = it.getInt(1)
                val mediaUri = if (type == 1) {
                    android.content.ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    )
                } else {
                    android.content.ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                    )
                }

                result.put(
                    JSONObject()
                        .put("uri", mediaUri.toString())
                        .put("type", if (type == 1) "image" else "video")
                )
            }
        }

        web.evaluateJavascript(
            "window.loadPhoneMedia(${JSONObject.quote(result.toString())})",
            null
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 77) loadMedia()
    }
}
