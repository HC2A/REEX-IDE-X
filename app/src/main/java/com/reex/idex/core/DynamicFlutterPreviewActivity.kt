package com.reex.idex.core

import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity

class DynamicFlutterPreviewActivity : FlutterActivity() {
    companion object {
        const val EXTRA_BUNDLE_PATH = "reex.flutter.bundle.path"
        const val EXTRA_INITIAL_ROUTE = "reex.flutter.initial.route"
    }

    override fun getAppBundlePath(): String =
        intent.getStringExtra(EXTRA_BUNDLE_PATH) ?: super.getAppBundlePath()

    override fun getInitialRoute(): String =
        intent.getStringExtra(EXTRA_INITIAL_ROUTE) ?: "/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setTitle("REEX IDE X • Flutter Preview")
    }
}
