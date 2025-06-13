plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.fyp.tomato_guard"
    compileSdk = 35




    defaultConfig {
        applicationId = "com.fyp.tomato_guard"
        minSdk = 24
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



   buildFeatures{
       viewBinding=true
   }



    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }


}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.generativeai)
    implementation(libs.androidx.ui.geometry.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation ("de.hdodenhof:circleimageview:3.1.0")
    implementation ("com.airbnb.android:lottie:4.2.2")
    implementation ("com.google.code.gson:gson:2.8.8")

    implementation ("com.github.bumptech.glide:glide:4.15.0")
    annotationProcessor ("com.github.bumptech.glide:compiler:4.15.0")

    //corutinse

    implementation ("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // ViewModel KTX (for viewModels() delegate)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // LiveData KTX (for LiveData observables)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    implementation ("org.pytorch:pytorch_android:1.13.1")
    implementation ("org.pytorch:pytorch_android_torchvision:1.13.1")
    implementation ("com.github.yalantis:ucrop:2.2.10")










}