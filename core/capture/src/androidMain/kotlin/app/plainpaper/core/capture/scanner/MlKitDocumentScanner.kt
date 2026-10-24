package app.plainpaper.core.capture.scanner

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google's on-device document scanner, delivered through Play services (research R7). The scanner
 * UI runs in the Play services process; Plainpaper only receives the cropped JPEGs.
 */
internal class MlKitDocumentScannerFactory : DocumentScannerFactory {
    private class Pending {
        var deferred: CompletableDeferred<ActivityResult>? = null
    }

    @Composable
    override fun remember(): DocumentScanner {
        val context = LocalContext.current
        val pending = remember { Pending() }
        val launcher =
            rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
                pending.deferred?.complete(result)
                pending.deferred = null
            }
        return remember(context, launcher) { MlKitDocumentScanner(context, launcher, pending) }
    }

    private class MlKitDocumentScanner(
        private val context: Context,
        private val launcher: ActivityResultLauncher<IntentSenderRequest>,
        private val pending: Pending,
    ) : DocumentScanner {
        private val client: GmsDocumentScanner by lazy {
            GmsDocumentScanning.getClient(
                GmsDocumentScannerOptions
                    .Builder()
                    .setGalleryImportAllowed(false)
                    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .build(),
            )
        }

        @Suppress("TooGenericExceptionCaught") // Play services failures of any kind mean "not available"
        override suspend fun availability(): ScannerAvailability =
            try {
                if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) !=
                    ConnectionResult.SUCCESS
                ) {
                    ScannerAvailability.Unavailable
                } else {
                    val installer = ModuleInstall.getClient(context)
                    if (installer.areModulesAvailable(client).await().areModulesAvailable()) {
                        ScannerAvailability.Ready
                    } else {
                        // Play services downloads the module in the background.
                        installer.installModules(ModuleInstallRequest.newBuilder().addApi(client).build())
                        ScannerAvailability.NeedsSetup
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ScannerAvailability.Unavailable
            }

        @Suppress("TooGenericExceptionCaught")
        override suspend fun scan(destinationDir: String): ScannerResult {
            val activity = context.findActivity() ?: return ScannerResult.Failed("no activity")
            return try {
                val sender = client.getStartScanIntent(activity).await()
                val deferred = CompletableDeferred<ActivityResult>()
                pending.deferred = deferred
                launcher.launch(IntentSenderRequest.Builder(sender).build())
                val result = deferred.await()
                if (result.resultCode != Activity.RESULT_OK) {
                    ScannerResult.Cancelled
                } else {
                    val scanned = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                    val uris = scanned?.pages?.map { it.imageUri }.orEmpty()
                    if (uris.isEmpty()) ScannerResult.Cancelled else copyPages(uris, File(destinationDir))
                }
            } catch (cancelled: CancellationException) {
                pending.deferred = null
                throw cancelled
            } catch (e: Exception) {
                ScannerResult.Failed(e.message ?: e.javaClass.simpleName)
            }
        }

        private suspend fun copyPages(
            uris: List<Uri>,
            directory: File,
        ): ScannerResult =
            withContext(Dispatchers.IO) {
                directory.mkdirs()
                val first = nextOriginalIndex(directory.list()?.toList().orEmpty())
                val written = mutableListOf<File>()
                try {
                    uris.forEachIndexed { offset, uri ->
                        val target = File(directory, originalName(first + offset))
                        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("unreadable page")
                        input.use { source -> target.outputStream().use { source.copyTo(it) } }
                        written += target
                    }
                    ScannerResult.Pages(written.map { it.absolutePath })
                } catch (e: IOException) {
                    written.forEach { it.delete() }
                    ScannerResult.Failed(e.message ?: "copy failed")
                }
            }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.cancel() }
    }
