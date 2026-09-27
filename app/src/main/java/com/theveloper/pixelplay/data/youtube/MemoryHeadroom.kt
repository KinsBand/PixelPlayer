package com.theveloper.pixelplay.data.youtube

/** How much Java heap is left before the app's limit (not counting what GC could free). */
internal object MemoryHeadroom {
    fun available(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
    }

    fun hasAtLeast(bytes: Long): Boolean = available() >= bytes
}
