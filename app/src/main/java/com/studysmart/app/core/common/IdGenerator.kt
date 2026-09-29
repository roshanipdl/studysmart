package com.studysmart.app.core.common

import java.util.UUID

interface IdGenerator {
    fun nextId(prefix: String): String
}

class DefaultIdGenerator : IdGenerator {
    override fun nextId(prefix: String): String {
        require(prefix.isNotBlank()) { "prefix must not be blank" }
        return "$prefix-${UUID.randomUUID()}"
    }
}
