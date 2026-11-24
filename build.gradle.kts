plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.spotless)
}

spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    val composeRules = "io.nlopez.compose.rules:ktlint:${libs.versions.composeRules.get()}"
    val rules =
        mapOf(
            "ktlint_code_style" to "ktlint_official",
            "max_line_length" to "140",
            "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
            "ktlint_class_signature_rule_force_multiline_when_parameter_count_greater_or_equal_than" to "4",
            "ktlint_function_signature_rule_force_multiline_when_parameter_count_greater_or_equal_than" to "5",
            // The floating bar's height and reselection for tab pages, and the theme's extra colours.
            "compose_allowed_composition_locals" to
                "LocalBottomBarInset,LocalScrollToTop,LocalAromaColors,LocalAromaTypography,LocalAromaShapes,LocalAromaShadows,LocalAromaSpace,LocalTonalFill",
        )
    kotlin {
        target("app/src/**/*.kt")
        ktlint(ktlintVersion).editorConfigOverride(rules).customRuleSets(listOf(composeRules))
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktlint(ktlintVersion).editorConfigOverride(rules)
    }
}
