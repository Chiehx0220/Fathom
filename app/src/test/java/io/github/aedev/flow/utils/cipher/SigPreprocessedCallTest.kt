package io.github.aedev.flow.utils.cipher

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The signature step as player `5203c085` (2026-10-06) writes it: the deciphering function is one of many
 * behind a pair of leading constants, and the signature goes through `decodeURIComponent` first.
 * The call site below is that player's own.
 */
class SigPreprocessedCallTest {
    private val callSite =
        """for(;!x.done;x=a.next()){var H=x.value;if(H.url){var h=new g.bo(H.url,!0);if(H.s){var B=h,k=H.sp,""" +
            """T=rb(6,3439,mi(24,1406,H.s));B[aX[18]](k,rb(51,6452,T))}var M=g.f(Object.keys(m))"""

    @Test
    fun `the function and both pairs of constants are read from the call`() {
        val info = FunctionNameExtractor.extractSigFunctionInfo(callSite)

        assertThat(info).isNotNull()
        assertThat(info!!.name).isEqualTo("rb")
        assertThat(info.constantArgs).containsExactly(6, 3439).inOrder()
        assertThat(info.preprocessFunc).isEqualTo("mi")
        assertThat(info.preprocessArgs).containsExactly(24, 1406).inOrder()
        assertThat(info.isHardcoded).isFalse()
    }

    @Test
    fun `a similar call that is not applied to the signature parameter is left alone`() {
        val other = """var q=rb(6,3439,mi(24,1406,H.t));B.set(rb(51,6452,q))"""

        assertThat(FunctionNameExtractor.extractSigFunctionInfo(other)).isNull()
    }
}
