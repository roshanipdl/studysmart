package com.studysmart.app.core.data.importer

import android.content.res.AssetManager
import java.io.ByteArrayOutputStream

class AssetCurriculumBundleSource(private val assets: AssetManager) : CurriculumBundleSource {
    override fun read(relativePath: String): ByteArray =
        assets.open("curriculum/${checkedBundlePath(relativePath)}").use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_FILE_BYTES) { "Curriculum file exceeds size limit: $relativePath" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }

    private companion object { const val MAX_FILE_BYTES = 16 * 1024 * 1024 }
}
