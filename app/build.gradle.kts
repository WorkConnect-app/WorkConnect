plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.workconnect"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.workconnect"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    // 🔹 Enable java.time on API < 26 (Desugaring)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    implementation("com.google.android.gms:play-services-auth:21.0.0")

    // 🔹 Firebase (BoM)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")

    // 🔹 AndroidX & UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // 🔹 Image loading
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // 🔹 Testing
    testImplementation(libs.junit)
    androidTestImplementation ("androidx.test.uiautomator:uiautomator:2.3.0")

    // 🔹 Protobuf (force consistent version - important for Firestore + androidTest)
    implementation("com.google.protobuf:protobuf-javalite:3.25.3")
    androidTestImplementation("com.google.protobuf:protobuf-javalite:3.25.3")

    // 🔹 Espresso
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // IMPORTANT: exclude protobuf-lite pulled by espresso-contrib -> accessibility-test-framework
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.5.1") {
        exclude(group = "com.google.protobuf", module = "protobuf-lite")
    }

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:rules:1.5.0")
}

configurations.all {
    resolutionStrategy {
        force("com.google.protobuf:protobuf-javalite:3.25.3")
        force("com.google.protobuf:protobuf-java:3.25.3")
    }
}
