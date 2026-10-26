plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.ourtoday"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.ourtoday"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        // 시작 URL. 로컬 테스트 시 -PstartUrl=http://10.0.2.2:3000/ 로 덮어쓰기 가능 (debug 만)
        buildConfigField("String", "START_URL", "\"https://ourtoday.vercel.app/\"")
    }

    buildTypes {
        debug {
            val override = project.findProperty("startUrl") as String?
            if (override != null) {
                buildConfigField("String", "START_URL", "\"$override\"")
            }
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.webkit:webkit:1.12.1")
}
