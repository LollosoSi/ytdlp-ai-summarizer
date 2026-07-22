plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "ciabbale.summariser"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "ciabbale.summariser"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)


    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    annotationProcessor("androidx.room:room-compiler:$roomVersion")

    // (Opzionale ma raccomandato) Per usare Gson e convertire facilmente oggetti se serve
    implementation("com.google.code.gson:gson:2.10.1")

    // OkHttp per le chiamate API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Markwon per il rendering Markdown
    implementation("io.noties.markwon:core:4.6.2")
}