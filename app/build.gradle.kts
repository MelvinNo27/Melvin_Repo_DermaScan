plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
    id("kotlin-android")
}

android {
    namespace = "com.example.dermascanai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.dermascanai"
        minSdk = 27
        targetSdk = 35
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
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        mlModelBinding = true
    }
    packaging {
        resources {
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/LICENSE.md"
        }
    }

}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.firebase.auth)

    implementation("pl.droidsonroids.gif:android-gif-drawable:1.2.27")

    implementation("com.prolificinteractive:material-calendarview:1.4.3")

    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta3")




    implementation("org.mindrot:jbcrypt:0.4")
    implementation("androidx.palette:palette:1.0.0")

    implementation("com.github.bumptech.glide:glide:4.15.0")
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.firebase.firestore)
//    implementation(libs.androidx.swiperefreshlayout)
    ksp("com.github.bumptech.glide:compiler:4.15.0")

    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.libraries.places:places:3.3.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("org.tensorflow:tensorflow-lite-metadata:0.4.4")


    implementation("androidx.room:room-runtime:2.5.0")
    ksp("androidx.room:room-compiler:2.5.0")

    ksp("com.google.dagger:hilt-compiler:2.40.5")

    implementation("com.google.android.material:material:1.11.0")

//    implementation("org.tensorflow:tensorflow-lite:2.9.0")
//    implementation("org.tensorflow:tensorflow-lite-task-vision:0.3.1")
//    implementation("org.tensorflow:tensorflow-lite-gpu:2.9.0")

// ✅ TensorFlow Lite stable 2.14.0 set
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.0")



    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")
    implementation(libs.firebase.database)
    implementation(libs.firebase.storage)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}