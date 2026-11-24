import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "app.aroma"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "app.aroma"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "0.7.0"
    }

    signingConfigs {
        val keystore = providers.environmentVariable("AROMA_KEYSTORE").orNull
        if (keystore != null) {
            val password = providers.environmentVariable("AROMA_KEYSTORE_PASSWORD").get()
            create("release") {
                storeFile = file(keystore)
                storePassword = password
                keyAlias = providers.environmentVariable("AROMA_KEY_ALIAS").getOrElse("aroma")
                keyPassword = providers.environmentVariable("AROMA_KEY_PASSWORD").getOrElse(password)
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        generateLocaleConfig = true
    }

    // The language setting switches among all of Aroma's languages, so a bundle must keep them all.
    bundle {
        language {
            enableSplit = false
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                test.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                // Screenshots are for design reviews, not for every push: they render only with
                // -Pscreenshots (see the Screenshots workflow), the other tests run on every build.
                if (!providers.gradleProperty("screenshots").isPresent) test.exclude("app/aroma/screenshots/**")
                // The live tests read these; another server must mean another test run, not a cached one.
                listOf("MEALIE_TEST_URL", "MEALIE_TEST_USER").forEach { name ->
                    test.inputs.property(name, providers.environmentVariable(name).orElse(""))
                }
            }
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        checkDependencies = false
        // Tests are not shipped: linting them doubled the time lint takes and found nothing users meet.
        ignoreTestSources = true
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
        lintConfig = file("lint.xml")
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)

    lintChecks(libs.compose.lint.checks)

    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.androidx.glance.appwidget.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.coil.test)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.turbine)
    debugImplementation(libs.compose.ui.test.manifest)
}
