package kr.kennysoft.idea260828korean.extension

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiFile
import de.plushnikov.intellij.plugin.util.LombokLibraryUtil
import kr.kennysoft.idea260828korean.JavaErrorBundle
import kr.kennysoft.idea260828korean.handler.OnXAnnotationHandler
import java.util.regex.Pattern

/**
 * @see de.plushnikov.intellij.plugin.extension.LombokHighlightErrorFilter
 */
class LombokHighlightErrorFilter : HighlightInfoFilter {
    private val LOMBOK_ANY_ANNOTATION_REQUIRED =
        Pattern.compile(JavaErrorBundle.message("incompatible.types", "lombok.*AnyAnnotation\\[\\]", "__*"))

    override fun accept(highlightInfo: HighlightInfo, file: PsiFile?): Boolean {
        if (null == file) {
            return true
        }

        val project = file.project
        if (!LombokLibraryUtil.hasLombokLibrary(project)) {
            return true
        }

        // handle rest cases
        val description = highlightInfo.description
        if (HighlightSeverity.ERROR == highlightInfo.severity) {
            //Handling onX parameters
            if (OnXAnnotationHandler.isOnXParameterAnnotation(highlightInfo, file)
                || OnXAnnotationHandler.isOnXParameterValue(highlightInfo, file)
                || description != null && LOMBOK_ANY_ANNOTATION_REQUIRED.matcher(description).matches()
            ) {
                return false
            }
        }

        return true
    }
}
