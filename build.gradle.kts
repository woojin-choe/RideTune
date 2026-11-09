plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// iCloud로 동기화되는 폴더(예: ~/Documents)에서는 빌드 중 "이름 2.class" 복사본이 생겨 dex 단계가 깨질 수 있다.
// 개인 설정(~/.gradle/gradle.properties)에 ridetune.buildRoot를 두면 빌드 결과물을 그 밖으로 옮긴다.
providers.gradleProperty("ridetune.buildRoot").orNull?.let { root ->
    allprojects {
        layout.buildDirectory.set(file("$root/${rootProject.name}/${project.path.replace(':', '/')}"))
    }
}
