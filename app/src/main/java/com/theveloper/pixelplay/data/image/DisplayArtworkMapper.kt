package com.theveloper.pixelplay.data.image

import android.net.Uri
import coil.map.Mapper
import coil.request.Options
import coil.size.Dimension
import com.theveloper.pixelplay.data.metadata.ArtworkUrls

/** Change the fetched URL before Coil computes its automatic cache keys. */
class DisplayArtworkMapper : Mapper<Uri, Uri> {
    // Custom mappers run before Coil's built-in String -> Uri mapper.
    class Strings : Mapper<String, Uri> {
        override fun map(data: String, options: Options): Uri =
            Uri.parse(data)
    }

    override fun map(data: Uri, options: Options): Uri {
        val width = (options.size.width as? Dimension.Pixels)?.px ?: return data
        val height = (options.size.height as? Dimension.Pixels)?.px ?: return data
        val original = data.toString()
        val resized = ArtworkUrls.forDisplay(original, width, height)
        return if (resized == original) data else Uri.parse(resized)
    }
}
