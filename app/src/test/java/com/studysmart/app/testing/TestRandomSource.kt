package com.studysmart.app.testing

import com.studysmart.app.core.common.RandomSource
import kotlin.random.Random

class SeededRandomSource(seed: Int) : RandomSource {
    private val random = Random(seed)

    override fun nextInt(until: Int): Int = random.nextInt(until)

    override fun nextLong(): Long = random.nextLong()
}
