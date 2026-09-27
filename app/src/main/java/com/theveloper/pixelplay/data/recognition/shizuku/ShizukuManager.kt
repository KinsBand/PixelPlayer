package com.theveloper.pixelplay.data.recognition.shizuku

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

enum class ShizukuStatus {
    UNAVAILABLE,
    NEEDS_PERMISSION,
    READY
}

@Singleton
class ShizukuManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _status = MutableStateFlow(ShizukuStatus.UNAVAILABLE)
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        val isGranted = grantResult == PackageManager.PERMISSION_GRANTED
        Timber.d("ShizukuManager: Permission result -> granted = %s", isGranted)
        refreshStatus()
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Timber.d("ShizukuManager: Binder received")
        refreshStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Timber.d("ShizukuManager: Binder dead")
        _status.value = ShizukuStatus.UNAVAILABLE
    }

    init {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionListener)
            refreshStatus()
        } catch (e: Throwable) {
            Timber.w(e, "ShizukuManager: Failed to register listeners")
            _status.value = ShizukuStatus.UNAVAILABLE
        }
    }

    fun refreshStatus() {
        _status.value = try {
            if (!Shizuku.pingBinder()) {
                ShizukuStatus.UNAVAILABLE
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                ShizukuStatus.READY
            } else {
                ShizukuStatus.NEEDS_PERMISSION
            }
        } catch (e: Throwable) {
            Timber.d(e, "ShizukuManager: Exception while checking status")
            ShizukuStatus.UNAVAILABLE
        }
    }

    fun requestPermission(requestCode: Int = 1001) {
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (e: Throwable) {
            Timber.e(e, "ShizukuManager: Error requesting permission")
        }
    }

    suspend fun executeShell(command: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                return@withContext Result.failure(IllegalStateException("Shizuku is not ready"))
            }

            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
            val process = newProcessMethod.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            process.waitFor()
            val exitCode = process.exitValue()
            if (exitCode == 0) {
                Result.success(output.toString().trim())
            } else {
                val error = process.errorStream.bufferedReader().readText().trim()
                Result.failure(RuntimeException("Command failed (exit $exitCode): $error"))
            }
        } catch (e: Throwable) {
            Timber.e(e, "ShizukuManager: Failed executing shell: %s", command)
            Result.failure(e)
        }
    }
}
