package com.billlocket.app.media

import android.content.ClipData
import android.content.Intent
import android.net.Uri

object MediaShareIntentFactory {
    private const val AUTHORITY = "com.billlocket.app.fileprovider"

    fun create(readOnlyShareUri: String): Intent {
        val uri = Uri.parse(readOnlyShareUri)
        require(uri.scheme == "content" && uri.authority == AUTHORITY) { "Only BillLocket share URIs are allowed" }
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("BillLocket image", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
