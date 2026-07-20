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
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Kept in its own class: the reproduction only works reliably as the first thing in the JVM to
 * construct a `DigestAuthProvider`, since ktor's nonce buffer is a process-wide singleton that
 * other tests can warm up. Run in isolation, e.g. `./gradlew test --tests "*.DigestAuthProviderCongestionTest"`.
 */
class DigestAuthProviderCongestionTest {

    @Test
    @OptIn(DelicateCoroutinesApi::class)
    fun `construction does not block when Dispatchers-Default is congested`() {
        // DigestAuthProvider's constructor calls io.ktor.util.generateNonceBlocking(), which
        // runBlocking()s on the calling thread waiting for a coroutine on Dispatchers.Default.
        // If that dispatcher's pool is fully busy, construction stalls until a thread frees up.
        // digestAuthProvider is lazy precisely to avoid this: constructing
        // PreemptiveBasicDigestAuthProvider must never touch it.
        val processors = Runtime.getRuntime().availableProcessors()
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

        assertFalse("Construction blocked because Dispatchers.Default was fully congested", thread.isAlive)
    }

}
