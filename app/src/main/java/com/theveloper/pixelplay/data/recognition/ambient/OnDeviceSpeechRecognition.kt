package com.theveloper.pixelplay.data.recognition.ambient

import android.content.Context
import android.os.Build
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class OnDeviceSpeechRecognition @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun isAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun create(): SpeechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
}
