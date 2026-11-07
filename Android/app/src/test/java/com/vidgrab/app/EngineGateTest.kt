package com.vidgrab.app

import com.vidgrab.app.core.EngineGate
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class EngineGateTest {
    @Test fun metadataCanBeFetchedWhileADownloadIsUsingTheEngine() = runBlocking {
        val gate = EngineGate()
        val downloading = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val download = launch { gate.use { downloading.complete(Unit); release.await() } }
        downloading.await()
        val metadata = withTimeout(1000) { gate.use { "metadata" } }
        assertEquals("metadata", metadata)
        release.complete(Unit)
        download.join()
    }

    @Test fun replacementWaitsForAllActiveReadersAndBlocksNewReaders() = runBlocking {
        val gate = EngineGate()
        val inDownload = CompletableDeferred<Unit>()
        val releaseDownload = CompletableDeferred<Unit>()
        val inReplacement = CompletableDeferred<Unit>()
        val releaseReplacement = CompletableDeferred<Unit>()
        val download = launch { gate.use { inDownload.complete(Unit); releaseDownload.await() } }
        inDownload.await()
        val replacement = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.replace { inReplacement.complete(Unit); releaseReplacement.await() }
        }
        assertFalse(inReplacement.isCompleted)
        releaseDownload.complete(Unit)
        inReplacement.await()
        val nextRead = async(start = CoroutineStart.UNDISPATCHED) { gate.use { "ready" } }
        assertFalse(nextRead.isCompleted)
        releaseReplacement.complete(Unit)
        assertEquals("ready", withTimeout(1000) { nextRead.await() })
        download.join(); replacement.join()
    }

    @Test fun cancellingReaderReleasesItsLeaseForAnUpdate() = runBlocking {
        val gate = EngineGate()
        val entered = CompletableDeferred<Unit>()
        val reader = launch { gate.use { entered.complete(Unit); awaitCancellation() } }
        entered.await()
        reader.cancelAndJoin()
        assertEquals("updated", withTimeout(1000) { gate.replace { "updated" } })
    }

    @Test fun cancellingWaitingWriterDoesNotBlockFutureReaders() = runBlocking {
        val gate = EngineGate()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val reader = launch { gate.use { entered.complete(Unit); release.await() } }
        entered.await()
        val writer = launch(start = CoroutineStart.UNDISPATCHED) { gate.replace { fail("Writer entered too early") } }
        writer.cancelAndJoin()
        assertEquals("metadata", withTimeout(1000) { gate.use { "metadata" } })
        release.complete(Unit); reader.join()
    }
}
