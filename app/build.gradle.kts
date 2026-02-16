plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "xuanniao.map.gnss"
    compileSdk = 36

    defaultConfig {
        applicationId = "xuanniao.map.gnss"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // Kotlin 协程（异步处理）
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // AndroidX 核心库
    implementation("androidx.core:core-ktx:1.12.0")
    // ViewPager2 核心依赖
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    // Fragment 依赖
    implementation("androidx.fragment:fragment-ktx:1.6.1")
    // ViewModel + LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    // Lifecycle观察者
    implementation("androidx.lifecycle:lifecycle-common-java8:2.7.0")
    // 阿里的json工具
    implementation("com.alibaba.fastjson2:fastjson2:2.0.61")

    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.activity:activity:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.3.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}