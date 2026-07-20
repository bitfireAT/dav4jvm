/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.ktor

import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kept in its own test class (rather than in [PreemptiveBasicDigestAuthProviderTest]) because the
 * reproduction below only works reliably as the *first* thing in the JVM to construct a
 * `DigestAuthProvider`: `io.ktor.util.generateNonceBlocking()`'s nonce buffer is a process-wide
 * singleton, so once any other test has warmed it up, construction no longer blocks. Run this
 * class in isolation, e.g. `./gradlew test --tests "*.DigestAuthProviderCongestionTest"`.
 *
 * This is a canary for a known starvation bug in ktor-client-auth, not a regression test for our
 * own code: it's written to pass while the bug is present, so that it starts failing (and gets our
 * attention) once a ktor upgrade fixes the underlying issue.
 */
class DigestAuthProviderCongestionTest {

    @Test
    @OptIn(DelicateCoroutinesApi::class)
    fun `construction stalls when Dispatchers-Default is congested`() {
        // DigestAuthProvider's constructor synchronously generates a client nonce via
        // io.ktor.util.generateNonceBlocking(), which starts a nonce-generator coroutine on
        // Dispatchers.Default and then runBlocking()s until it receives a value from that
        // coroutine. If every thread in the (CPU-core-sized) Dispatchers.Default pool is
        // already busy, the nonce generator has nowhere to run, and construction stalls until a
        // thread frees up.
        val processors = Runtime.getRuntime().availableProcessors()
        // launch() only guarantees the coroutine is scheduled, not that it's actually running on
        // a worker thread yet; wait for all of them to confirm they're occupying a thread before
        // starting the construction below, otherwise it could race ahead of the congestion.
        val allCongested = CountDownLatch(processors)
        repeat(processors) {
            GlobalScope.launch(Dispatchers.Default) {
                allCongested.countDown()
                Thread.sleep(10_000)
            }
        }
        allCongested.await()

        val thread = Thread {
            PreemptiveBasicDigestAuthProvider(
                username = "user",
                password = "password"
            )
        }
        thread.start()
        thread.join(2_000)

        assertTrue(
            "DigestAuthProvider construction no longer stalls under Dispatchers.Default " +
                "congestion. The ktor-client-auth starvation bug this test tracks (see " +
                "io.ktor.util.generateNonceBlocking) appears to be fixed — this test can be removed.",
            thread.isAlive
        )
    }

}
