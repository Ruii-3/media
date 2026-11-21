plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.automediaplayer"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.automediaplayer"
        minSdk = 31
        targetSdk = 37
        versionCode = 6
        versionName = "6.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    buildFeatures {
        viewBinding = true
    }
    testOptions {
        // Robolectric 需要读取合并后的资源与 Manifest。
        unitTests.isIncludeAndroidResources = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // 第 5 周：厂商方向盘/中控硬按键分发库（T5-3）。
    implementation(files("libs/hardkey.aar"))
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    // 第 6 周 CP6：单元测试框架（课程第七章指定版本）
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.1")
    testImplementation("org.robolectric:robolectric:4.10.3")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("org.mockito:mockito-core:4.11.0")
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
