package com.studysmart.app.core.data.importer

fun interface CurriculumBundleSource {
    fun read(relativePath: String): ByteArray
}

internal fun checkedBundlePath(path: String): String {
    require(path.isNotBlank() && !path.startsWith('/') && '\\' !in path) { "Unsafe bundle path: $path" }
    require(path.split('/').none { it.isEmpty() || it == "." || it == ".." }) { "Unsafe bundle path: $path" }
    require(path == "curriculum.json" || path == "bundle-manifest.json" ||
        Regex("subjects/[A-Za-z0-9_-]+\\.json").matches(path)) { "Unexpected bundle file: $path" }
    return path
}
