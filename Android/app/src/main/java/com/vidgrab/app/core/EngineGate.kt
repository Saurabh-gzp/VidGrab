package com.vidgrab.app.core

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Readers can fetch metadata and download together; an engine replacement waits for both. */
class EngineGate {
    private val turnstile = Mutex()
    private val empty = Mutex()
    private val readerLock = Mutex()
    private var readers = 0

    suspend fun <T> use(block: suspend () -> T): T {
        turnstile.withLock { }
        readerLock.lock()
        try {
            if (readers == 0) empty.lock()
            readers++
        } finally { readerLock.unlock() }
        try { return block() }
        finally {
            withContext(NonCancellable) {
                readerLock.withLock {
                    readers--
                    if (readers == 0) empty.unlock()
                }
            }
        }
    }

    suspend fun <T> replace(block: suspend () -> T): T =
        turnstile.withLock { empty.withLock { block() } }
}
