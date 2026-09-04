package com.zangrcar.cngitaly

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectivityStateTest {
    @Test
    fun `validated internet requires both internet and validated capabilities`() {
        assertTrue(
            hasValidatedInternetCapabilities(
                hasInternet = true,
                hasValidated = true
            )
        )

        assertFalse(
            hasValidatedInternetCapabilities(
                hasInternet = true,
                hasValidated = false
            )
        )

        assertFalse(
            hasValidatedInternetCapabilities(
                hasInternet = false,
                hasValidated = true
            )
        )

        assertFalse(
            hasValidatedInternetCapabilities(
                hasInternet = false,
                hasValidated = false
            )
        )
    }
}
