package com.theveloper.pixelplay.data.youtube

import com.theveloper.pixelplay.di.YouTubeOkHttpClient
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NewPipeRequest
import org.schabi.newpipe.extractor.downloader.Response as NewPipeResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NewPipe transport. Uses the dedicated YouTube client so the headers NewPipe sets per
 * InnerTube client (User-Agent, X-Youtube-Client-*, cookies) reach YouTube unchanged.
 */
@Singleton
class NewPipeDownloader @Inject constructor(
    @YouTubeOkHttpClient private val client: OkHttpClient
) : Downloader() {

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: NewPipeRequest): NewPipeResponse {
        val url = request.url()
        val builder = Request.Builder().url(url)

        var hasUserAgent = false
        for ((headerName, headerValues) in request.headers()) {
            if (headerValues.isEmpty()) continue
            builder.removeHeader(headerName)
            headerValues.forEach { builder.addHeader(headerName, it) }
            if (headerName.equals("User-Agent", ignoreCase = true)) hasUserAgent = true
        }
        if (!hasUserAgent) builder.header("User-Agent", YouTubeHttp.DESKTOP_USER_AGENT)

        val method = request.httpMethod()
        val body = request.dataToSend()?.toRequestBody(null)
            ?: if (method in BODY_METHODS) ByteArray(0).toRequestBody(null) else null
        builder.method(method, body)

        val call = client.newCall(builder.build())
        val group = NewPipeExecution.current.get()
        group?.register(call)
        try {
            call.execute().use { response ->
                if (response.code == 429) {
                    throw ReCaptchaException("reCaptcha challenge requested", url)
                }
                return NewPipeResponse(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),
                    response.body.string(),
                    response.request.url.toString()
                )
            }
        } finally { group?.remove(call) }
    }

    private companion object {
        val BODY_METHODS = setOf("POST", "PUT", "PATCH")
    }
}
