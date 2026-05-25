package world.saloris.donoff

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 기기(또는 에뮬레이터)에서 실행되는 테스트.
 * PC에서만 도는 [test] 소스와 달리 Android 런타임·앱 패키지를 사용합니다.
 */
@RunWith(AndroidJUnit4::class)
class PackageNameInstrumentedTest {
    @Test
    fun appContext_hasExpectedPackageName() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("world.saloris.donoff", appContext.packageName)
    }
}
