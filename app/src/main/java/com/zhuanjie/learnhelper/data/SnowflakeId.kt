package com.zhuanjie.learnhelper.data

import java.util.concurrent.atomic.AtomicLong

object SnowflakeId {
    private const val EPOCH = 1700000000000L // custom epoch: 2023-11-14
    private const val SEQUENCE_BITS = 22
    private const val SEQUENCE_MASK = (1L shl SEQUENCE_BITS) - 1

    private val state = AtomicLong(0L) // packed: upper 42 bits = timestamp, lower 22 bits = sequence

    fun next(): Long {
        while (true) {
            val now = System.currentTimeMillis() - EPOCH
            val prev = state.get()
            val prevTs = prev ushr SEQUENCE_BITS
            val seq = if (now == prevTs) {
                val s = (prev and SEQUENCE_MASK) + 1
                if (s > SEQUENCE_MASK) {
                    // sequence exhausted for this ms, spin
                    Thread.sleep(1)
                    continue
                }
                s
            } else {
                0L
            }
            val next = (now shl SEQUENCE_BITS) or seq
            if (state.compareAndSet(prev, next)) {
                return (now shl SEQUENCE_BITS) or seq
            }
        }
    }
}
