package kr.kennysoft.idea260828korean

import com.intellij.DynamicBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "messages.JavaErrorBundle"

/**
 * * Original: ideaIC-XXXX.Y.zip/plugins/java/lib/java_resources_en.jar/messages/JavaErrorBundle.properties
 * * i18n: ko.XXX.YYY.zip/lib/ko.XXX.YYY.jar/messages/JavaErrorBundle.properties
 */
object JavaErrorBundle : DynamicBundle(BUNDLE) {

    @Suppress("SpreadOperator")
    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any) =
        getMessage(key, *params)

    @Suppress("SpreadOperator", "unused")
    @JvmStatic
    fun messagePointer(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any) =
        getLazyMessage(key, *params)
}
