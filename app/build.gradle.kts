import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release-Signierung: liest Keystore-Angaben aus keystore.properties (NICHT im
// Repo – siehe .gitignore). Fehlt die Datei (z.B. beim F-Droid-Build oder auf
// einem fremden Rechner), bleibt der Release unsigniert statt den Build zu brechen.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}

android {
    namespace = "de.bgg_home.texdroid"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "de.bgg_home.texslate"
        minSdk = 26
        targetSdk = 36
        versionCode = 13
        versionName = "1.0-alpha13"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Editionen (biber-Milestone-Entscheidung): Basis strikt schlank, biber-Runtime
    // (Perl+XS, ~14 MB) NUR in der thesis-Edition. Gated über BuildConfig.HAS_BIBER.
    flavorDimensions += "edition"
    productFlavors {
        create("core") {
            dimension = "edition"
            // Kein biber: biblatex braucht backend=bibtex (Preflight erklärt das).
            buildConfigField("boolean", "HAS_BIBER", "false")
        }
        create("thesis") {
            dimension = "edition"
            // Volles biber: Runtime aus src/thesis (assets-zip + jniLibs), Wiring in
            // BiberRuntime. Für Thesis-/Abschlussarbeiten (biblatex mit biber-Backend).
            buildConfigField("boolean", "HAS_BIBER", "true")
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Debug-Builds mit dem Release-Key signieren (nur wenn der Keystore
            // lokal liegt): erlaubt In-place-Updates über die per Obtainium
            // installierte Release-APK auf den Testgeräten, ohne App-Daten
            // (TeX-Bundle-Cache, Vorlagen, Projekt-Freigaben) zu verlieren.
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            optimization {
                enable = false
            }
            // Nur signieren, wenn ein Keystore hinterlegt ist (sonst unsigniert).
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // Pro ABI eine eigene APK statt einer fetten Universal-APK. Jede native
    // Tectonic-Lib ist ~60 MB – so bleibt die Tablet-APK (arm64-v8a) halb so groß.
    // installDebug installiert automatisch die zum Gerät passende Variante.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    // thesis-Edition (biber): jniLibs ins nativeLibraryDir extrahieren, damit die .so
    // als echte Dateien auf Platte liegen. Nur von dort sind exec (perl/launcher)
    // erlaubt (App-Datendirs sind noexec / W^X). AGP-Default mappt sie sonst nur aus
    // dem APK → kein exec'bares File. Siehe BiberRuntime / app/src/thesis/README.md.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.sora.editor)
    implementation(libs.sora.editor.textmate)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}