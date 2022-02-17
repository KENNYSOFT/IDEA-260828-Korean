package kr.kennysoft.idea260828korean.extension

import com.intellij.codeInsight.daemon.JavaErrorBundle
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter
import com.intellij.codeInsight.intention.AddAnnotationFix
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiClassInitializer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiMethodReferenceExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.util.PsiTreeUtil
import de.plushnikov.intellij.plugin.LombokClassNames
import de.plushnikov.intellij.plugin.handler.BuilderHandler
import de.plushnikov.intellij.plugin.handler.EqualsAndHashCodeCallSuperHandler
import de.plushnikov.intellij.plugin.handler.FieldNameConstantsHandler
import de.plushnikov.intellij.plugin.handler.LazyGetterHandler
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder
import de.plushnikov.intellij.plugin.util.LombokLibraryUtil
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil
import kr.kennysoft.idea260828korean.handler.OnXAnnotationHandler
import java.util.regex.Pattern

/**
 * @see de.plushnikov.intellij.plugin.extension.LombokHighlightErrorFilter
 */
class LombokHighlightErrorFilter : HighlightInfoFilter {
    private val LOMBOK_ANY_ANNOTATION_REQUIRED = Pattern.compile("Incompatible types\\. Found: '__*', required: 'lombok.*AnyAnnotation\\[\\]'")

    private val registeredFilters: MutableMap<HighlightSeverity, MutableMap<TextAttributesKey?, MutableList<LombokHighlightFilter>>>
    private val registeredHooks: MutableMap<HighlightSeverity, MutableMap<TextAttributesKey?, MutableList<LombokHighlightFixHook>>>

    init {
        registeredFilters = HashMap()
        registeredHooks = HashMap()

        for (highlightFilter in LombokHighlightFilter.values()) {
            registeredFilters.computeIfAbsent(highlightFilter.severity) { HashMap() }
                .computeIfAbsent(highlightFilter.key) { ArrayList() }
                .add(highlightFilter)
        }

        for (highlightFixHook in LombokHighlightFixHook.values()) {
            registeredHooks.computeIfAbsent(highlightFixHook.severity) { HashMap() }
                .computeIfAbsent(highlightFixHook.key) { ArrayList() }
                .add(highlightFixHook)
        }
    }

    override fun accept(highlightInfo: HighlightInfo, file: PsiFile?): Boolean {
        if (null == file) {
            return true
        }

        val project = file.project
        if (!LombokLibraryUtil.hasLombokLibrary(project)) {
            return true
        }

        val highlightedElement = file.findElementAt(highlightInfo.getStartOffset()) ?: return true

        // check exceptions for highlights
        val acceptHighlight = registeredFilters
            .getOrDefault(highlightInfo.severity, emptyMap())
            .getOrDefault(highlightInfo.type.attributesKey, emptyList())
            .stream()
            .filter { it.descriptionCheck(highlightInfo.description) }
            .allMatch { it.accept(highlightedElement) }

        // check if highlight was filtered
        if (!acceptHighlight) {
            return false
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

        // register different quick fix for highlight
        registeredHooks
            .getOrDefault(highlightInfo.severity, emptyMap())
            .getOrDefault(highlightInfo.type.attributesKey, emptyList())
            .stream()
            .filter { it.descriptionCheck(highlightInfo.description) }
            .forEach { it.processHook(highlightedElement, highlightInfo) }

        return true
    }

    private enum class LombokHighlightFixHook(val severity: HighlightSeverity, val key: TextAttributesKey?) {

        UNHANDLED_EXCEPTION(HighlightSeverity.ERROR, CodeInsightColors.ERRORS_ATTRIBUTES) {
            private val pattern = Pattern.compile("Unhandled exceptions?: .+")

            override fun descriptionCheck(description: String?): Boolean {
                return description != null && pattern.matcher(description).matches()
            }

            override fun processHook(highlightedElement: PsiElement, highlightInfo: HighlightInfo) {
                val importantParent: PsiElement? = PsiTreeUtil.getParentOfType(
                    highlightedElement,
                    PsiMethod::class.java,
                    PsiLambdaExpression::class.java,
                    PsiMethodReferenceExpression::class.java,
                    PsiClassInitializer::class.java
                )

                // applicable only for methods
                if (importantParent is PsiMethod) {
                    val fix = AddAnnotationFix(LombokClassNames.SNEAKY_THROWS, (importantParent as PsiModifierListOwner?)!!)
                    highlightInfo.registerFix(fix, null, null, null, null)
                }
            }
        };

        abstract fun descriptionCheck(description: String?): Boolean

        abstract fun processHook(highlightedElement: PsiElement, highlightInfo: HighlightInfo)
    }

    private enum class LombokHighlightFilter(val severity: HighlightSeverity, val key: TextAttributesKey?) {
        // ERROR HANDLERS

        VARIABLE_MIGHT_NOT_BEEN_INITIALIZED(HighlightSeverity.ERROR, CodeInsightColors.ERRORS_ATTRIBUTES) {
            private val pattern = Pattern.compile("Variable '.+' might not have been initialized")

            override fun descriptionCheck(description: String?): Boolean {
                return description != null && pattern.matcher(description).matches()
            }

            override fun accept(highlightedElement: PsiElement): Boolean {
                return !LazyGetterHandler.isLazyGetterHandled(highlightedElement)
            }
        },

        CONSTANT_EXPRESSION_REQUIRED(HighlightSeverity.ERROR, CodeInsightColors.ERRORS_ATTRIBUTES) {
            override fun descriptionCheck(description: String?): Boolean {
                return JavaErrorBundle.message("constant.expression.required") == description
            }

            override fun accept(highlightedElement: PsiElement): Boolean {
                return !FieldNameConstantsHandler.isFiledNameConstants(highlightedElement)
            }
        },

        // WARNINGS HANDLERS

        VARIABLE_INITIALIZER_IS_REDUNDANT(HighlightSeverity.WARNING, CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES) {
            private val pattern = Pattern.compile("Variable '.+' initializer '.+' is redundant")

            override fun descriptionCheck(description: String?): Boolean {
                return description != null && pattern.matcher(description).matches()
            }

            override fun accept(highlightedElement: PsiElement): Boolean {
                return !BuilderHandler.isDefaultBuilderValue(highlightedElement)
            }
        },

        /**
         * field should have lazy getter and should be initialized in constructors
         */
        METHOD_INVOCATION_WILL_PRODUCE_NPE(HighlightSeverity.WARNING, CodeInsightColors.WARNINGS_ATTRIBUTES) {
            private val pattern = Pattern.compile("Method invocation '.*' will produce 'NullPointerException'")

            override fun descriptionCheck(description: String?): Boolean {
                return description != null && pattern.matcher(description).matches()
            }

            override fun accept(highlightedElement: PsiElement): Boolean {
                return !LazyGetterHandler.isLazyGetterHandled(highlightedElement)
                        || !LazyGetterHandler.isInitializedInConstructors(highlightedElement)
            }
        },

        REDUNDANT_DEFAULT_PARAMETER_VALUE_ASSIGNMENT(HighlightSeverity.WARNING, CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES) {
            override fun descriptionCheck(description: String?): Boolean {
                return "Redundant default parameter value assignment" == description
            }

            override fun accept(highlightedElement: PsiElement): Boolean {
                return !EqualsAndHashCodeCallSuperHandler.isEqualsAndHashCodeCallSuperDefault(highlightedElement)
            }
        },

        /**
         * Handles warnings that are related to Builder.Default cause.
         * The final fields that are marked with Builder.Default contains only possible value because user can set another value during the creation of the object.
         */
        CONSTANT_CONDITIONS_DEFAULT_BUILDER_CAN_BE_SIMPLIFIED(HighlightSeverity.WARNING, CodeInsightColors.WARNINGS_ATTRIBUTES) {
            private val patternCanBeSimplified = Pattern.compile("'.+' can be simplified to '.+'")
            private val patternIsAlways = Pattern.compile("Condition '.+' is always '(true|false)'")

            override fun descriptionCheck(description: String?): Boolean {
                return description != null
                        && (patternCanBeSimplified.matcher(description).matches() || patternIsAlways.matcher(description).matches())
            }

            override fun accept(highlightedElement: PsiElement): Boolean {
                val parent = PsiTreeUtil.getParentOfType(highlightedElement, PsiReferenceExpression::class.java) ?: return true

                val resolve = parent.resolve()
                if (resolve !is PsiField) {
                    return true
                }

                return !PsiAnnotationSearchUtil.isAnnotatedWith((resolve as PsiField?)!!, LombokClassNames.BUILDER_DEFAULT)
            }
        },

        /**
         * Caller of extension methods do not need to perform null pointer checking.
         * This is a compromise because IntelliJ will only warn of the first possible null pointer call.
         * See the example code below for details.
         *
         * static boolean isNullOrEmpty(String str) { return str == null || str.isEmpty(); }
         *
         * {
         * String a = null;
         * a.isNullOrEmpty(); // Warning is eliminated by filter.
         * a.isBlank();       // This call will not be warned.
         * }
         */
        EXTENSION_METHOD_CALLER_NULL_CHECKING(HighlightSeverity.WARNING, CodeInsightColors.WARNINGS_ATTRIBUTES) {
            private val patternMayNPE = Pattern.compile("Method invocation '.+' (may|will) produce 'NullPointerException'")

            override fun descriptionCheck(description: String?): Boolean {
                return description != null && patternMayNPE.matcher(description).matches()
            }

            override fun accept(highlightedElement: PsiElement): Boolean {
                val parent = highlightedElement.parent
                if (parent != null) {
                    val parentParent = parent.parent
                    if (parentParent is PsiMethodCallExpression) {
                        val method = parentParent.resolveMethod()
                        if (method is LombokLightMethodBuilder) {
                            val navigationElement = method.getNavigationElement()
                            return !(navigationElement is PsiMethod && navigationElement.hasModifierProperty(PsiModifier.STATIC))
                        }
                    }
                }
                return true
            }
        };

        /**
         * @param description of the current highlighted element
         * @return true if the filter can handle current type of the highlight info with that kind of the description
         */
        abstract fun descriptionCheck(description: String?): Boolean

        /**
         * @param highlightedElement the deepest element (it's the leaf element in PSI tree where the highlight was occurred)
         * @return false if the highlight should be suppressed
         */
        abstract fun accept(highlightedElement: PsiElement): Boolean
    }
}
