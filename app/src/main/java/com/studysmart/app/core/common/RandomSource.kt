package com.studysmart.app.core.common

import kotlin.random.Random

/** Randomness is a boundary so selection and sampling can use a stable test seed. */
interface RandomSource {
    fun nextInt(until: Int): Int
    fun nextLong(): Long
}

class DefaultRandomSource(
    private val random: Random = Random.Default,
) : RandomSource {
    override fun nextInt(until: Int): Int = random.nextInt(until)

    override fun nextLong(): Long = random.nextLong()
}
