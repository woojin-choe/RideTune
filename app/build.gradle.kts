plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ridetune.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ridetune.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    // release 서명: 키와 비밀번호는 저장소 밖 개인 설정(~/.gradle/gradle.properties)에 둔다.
    // 없으면 debug 키로 서명한다 — 서명 없는 APK는 설치가 안 돼 심사에서 실행할 수 없기 때문이다.
    val signing = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        .associateWith { providers.gradleProperty("ridetune.$it").orNull }
    signingConfigs {
        if (signing.values.all { it != null }) {
            create("release") {
                storeFile = file(signing.getValue("storeFile")!!)
                storePassword = signing.getValue("storePassword")
                keyAlias = signing.getValue("keyAlias")
                keyPassword = signing.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    // 안드로이드 표준 차량 API(CarPropertyManager)
    useLibrary("android.car")

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navihelper)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}

// 제출용 APK: apk/<팀명>_RideTune_release.apk (공모전 규정 파일 이름). 팀명은 gradle.properties의 ridetune.teamName.
val teamName = providers.gradleProperty("ridetune.teamName").orElse("TeamName")
tasks.register<Copy>("submissionApk") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(rootProject.layout.projectDirectory.dir("apk"))
    rename { "${teamName.get()}_RideTune_release.apk" }
}
