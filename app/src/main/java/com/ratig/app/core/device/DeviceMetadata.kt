package com.ratig.app.core.device

import android.os.Build
import com.ratig.app.BuildConfig
import kotlinx.serialization.Serializable

/**
 * Technical metadata recorded with every test session so that reaction-time
 * results can be audited in the context of the device they were captured on
 * (touch sampling and display pipeline differ between devices).
 */
@Serializable
data class DeviceMetadata(
    val manufacturer: String,
    val model: String,
    val androidSdkInt: Int,
    val androidRelease: String,
    val appVersionName: String,
    val appVersionCode: Int,
) {
    companion object {
        fun current(): DeviceMetadata = DeviceMetadata(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidSdkInt = Build.VERSION.SDK_INT,
            androidRelease = Build.VERSION.RELEASE,
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE,
        )
    }
}
