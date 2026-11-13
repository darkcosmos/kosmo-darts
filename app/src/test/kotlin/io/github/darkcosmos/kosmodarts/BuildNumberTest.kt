package io.github.darkcosmos.kosmodarts

import org.junit.Assert.assertTrue
import org.junit.Test

/** Rule AV2: the build number is the CI run number (1 to 10 digits) or `dev`, never anything else. */
class BuildNumberTest {

    @Test
    fun buildNumberIsCiRunNumberOrDev() {
        val buildNumber = BuildConfig.BUILD_NUMBER

        assertTrue(
            "BUILD_NUMBER must fully match [0-9]{1,10}|dev, was \"$buildNumber\"",
            Regex("[0-9]{1,10}|dev").matches(buildNumber),
        )
    }
}
