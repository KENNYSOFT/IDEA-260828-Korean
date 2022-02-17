package kr.kennysoft.idea260828korean.handler

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationParameterList
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameValuePair
import de.plushnikov.intellij.plugin.LombokClassNames
import kr.kennysoft.idea260828korean.JavaErrorBundle
import java.util.regex.Pattern

/**
 * @see de.plushnikov.intellij.plugin.handler.OnXAnnotationHandler
 */
object OnXAnnotationHandler {
    private val UNDERSCORES = Pattern.compile("__*")
    private val CANNOT_RESOLVE_SYMBOL_UNDERSCORES_MESSAGE =
        Pattern.compile(JavaErrorBundle.message("cannot.resolve.symbol", "__*")
            .replace("(", "\\(").replace(")", "\\)"))
    private val CANNOT_RESOLVE_METHOD_UNDERSCORES_MESSAGE =
        Pattern.compile(JavaErrorBundle.message("annotation.unknown.method", "(onMethod|onConstructor|onParam)_+")
            .replace("(", "\\(").replace(")", "\\)")
            .replace("\\(onMethod|onConstructor|onParam\\)_+", "(onMethod|onConstructor|onParam)_+"))

    private val ANNOTATION_TYPE_EXPECTED = JavaErrorBundle.message("annotation.annotation.type.expected")
    private val CANNOT_FIND_METHOD_VALUE_MESSAGE = JavaErrorBundle.message("annotation.missing.method", "value")

    private val ONXABLE_ANNOTATIONS: Collection<String> = listOf(
        LombokClassNames.GETTER,
        LombokClassNames.SETTER,
        LombokClassNames.WITH,
        LombokClassNames.WITHER,
        LombokClassNames.NO_ARGS_CONSTRUCTOR,
        LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR,
        LombokClassNames.ALL_ARGS_CONSTRUCTOR,
        LombokClassNames.EQUALS_AND_HASHCODE
    )
    private val ONX_PARAMETERS: Collection<String> = listOf(
        "onConstructor",
        "onMethod",
        "onParam"
    )

    fun isOnXParameterAnnotation(highlightInfo: HighlightInfo, file: PsiFile): Boolean {
        val description = StringUtil.notNullize(highlightInfo.description)
        if (!(ANNOTATION_TYPE_EXPECTED == description
                    || CANNOT_RESOLVE_SYMBOL_UNDERSCORES_MESSAGE.matcher(description).matches()
                    || CANNOT_RESOLVE_METHOD_UNDERSCORES_MESSAGE.matcher(description).matches())) {
            return false
        }

        val highlightedElement = file.findElementAt(highlightInfo.getStartOffset())

        val nameValuePair: PsiNameValuePair? = findContainingNameValuePair(highlightedElement)
        if (nameValuePair == null || nameValuePair.context !is PsiAnnotationParameterList) {
            return false
        }

        var parameterName: String? = nameValuePair.name
        if (null != parameterName && parameterName.contains("_")) {
            parameterName = parameterName.substring(0, parameterName.indexOf('_'))
        }
        if (!ONX_PARAMETERS.contains(parameterName)) {
            return false
        }

        val containingAnnotation: PsiElement? = nameValuePair.context?.context;
        return containingAnnotation is PsiAnnotation && ONXABLE_ANNOTATIONS.contains(containingAnnotation.qualifiedName)
    }

    fun isOnXParameterValue(highlightInfo: HighlightInfo, file: PsiFile): Boolean {
        if (CANNOT_FIND_METHOD_VALUE_MESSAGE != highlightInfo.description) {
            return false
        }

        val highlightedElement = file.findElementAt(highlightInfo.getStartOffset())
        val nameValuePair: PsiNameValuePair? = findContainingNameValuePair(highlightedElement)
        if (nameValuePair == null || nameValuePair.context !is PsiAnnotationParameterList) {
            return false
        }

        val leftSibling: PsiElement? = nameValuePair.context?.prevSibling
        return leftSibling != null && UNDERSCORES.matcher(StringUtil.notNullize(leftSibling.text)).matches()
    }

    private fun findContainingNameValuePair(highlightedElement: PsiElement?): PsiNameValuePair? {
        var nameValuePair = highlightedElement
        while (!(nameValuePair == null || nameValuePair is PsiNameValuePair)) {
            nameValuePair = nameValuePair.context
        }

        return nameValuePair as PsiNameValuePair?
    }
}
