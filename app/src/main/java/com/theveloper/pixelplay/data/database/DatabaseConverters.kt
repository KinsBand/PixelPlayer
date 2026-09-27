package com.theveloper.pixelplay.data.database

import androidx.room.TypeConverter
import java.nio.ByteBuffer

class DatabaseConverters {

    @TypeConverter
    fun fromFloatArray(array: FloatArray?): ByteArray? {
        if (array == null) return null
        val byteBuffer = ByteBuffer.allocate(array.size * 4)
        byteBuffer.asFloatBuffer().put(array)
        return byteBuffer.array()
    }

    @TypeConverter
    fun toFloatArray(bytes: ByteArray?): FloatArray? {
        if (bytes == null) return null
        val floatBuffer = ByteBuffer.wrap(bytes).asFloatBuffer()
        val array = FloatArray(floatBuffer.remaining())
        floatBuffer.get(array)
        return array
    }
}
