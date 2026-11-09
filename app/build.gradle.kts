plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.haohao.inkjournal"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.haohao.inkjournal"
        // 29 = Android 10：前緩衝低延遲筆跡與 RenderNode 都從這版開始有。S7 FE 是 Android 14。
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
    }

    // 跟 Grad Daily 用同一把 debug 金鑰，之後覆蓋安裝新版才不會跳「套件衝突」
    signingConfigs {
        getByName("debug") {
            storeFile = file("E:/Android/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        // 平常安裝用 release：有 R8 最佳化，Compose 介面與手寫都比 debug 版順很多
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    // Jetpack Ink：低延遲手寫（前緩衝繪製）、筆刷、筆劃幾何與繪製
    val ink = "1.0.0"
    implementation("androidx.ink:ink-authoring:$ink")
    implementation("androidx.ink:ink-brush:$ink")
    implementation("androidx.ink:ink-strokes:$ink")
    implementation("androidx.ink:ink-rendering:$ink")
    implementation("androidx.ink:ink-geometry:$ink")
    // 筆尖位置預測：把畫面上的筆跡往前補一點，降低「筆跡跟不上筆尖」的感覺
    implementation("androidx.input:input-motionprediction:1.0.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
