package app.plainpaper.core.scanengine

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.scanengine.model.CapabilityDetails

/** `ActivityManager.MemoryInfo.totalMem`, `isLowRamDevice` and the 64-bit ABI list (research R10). */
public class AndroidCapabilityProvider(
    private val context: Context,
) : CapabilityProvider {
    override fun current(): DeviceCapability =
        details().let { DeviceCapability.classifyCapability(it.totalMemoryBytes, it.is64Bit, it.isLowRamDevice) }

    override fun details(): CapabilityDetails {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        return CapabilityDetails(
            totalMemoryBytes = memory.totalMem,
            is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty(),
            isLowRamDevice = activityManager.isLowRamDevice,
            platformName = "android-${Build.VERSION.SDK_INT}-${Build.MODEL}",
        )
    }
}
