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
    namespace = "de.bgg_home.texslate"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    // NDK fest verdrahtet — zwei Gruende. Erstens muss AGP ein NDK finden, sonst
    // ueberspringt es stripCoreReleaseDebugSymbols ("Unable to strip ..., packaging
    // them as they are") und packt ungestrippte .so ein; F-Droid strippt (deren
    // Rezept setzt ndk:), und dann weicht das Referenz-APK ab -> Reproducible Build
    // scheitert. Zweitens darf nicht die neueste installierte Version gewinnen: der
    // Rust-Link muss gegen dasselbe NDK laufen wie auf dem Buildserver.
    // MUSS mit dem `ndk:`-Feld in docs/fdroid/de.bgg_home.texslate.yml uebereinstimmen.
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "de.bgg_home.texslate"
        minSdk = 26
        targetSdk = 36
        versionCode = 24
        versionName = "1.0-alpha24"

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

// ── Nativen Build optional in Gradle ziehen ─────────────────────────────────
// F-Droid baut die Rust/Tectonic-Lib NICHT im prebuild: deren Quell-Scanner
// laeuft zwischen prebuild und Gradle (build.py: prebuild -> scan_source ->
// gradle) und meldet jede .so im Baum als "shared library". `scanignore` ist
// laut Review nicht erlaubt (MR !42882, linsui 15.09.2026) — also muss die .so
// erst nach dem Scan entstehen, sprich hier.
//
// Der Task greift nur, wenn -PvcpkgRoot gesetzt ist; F-Droid schreibt die Zeile
// im prebuild nach gradle.properties. Lokal aendert sich nichts: dort laeuft
// weiterhin ./build-native.sh von Hand, und ohne die Property haengt hier kein
// Task im Graph.
val vcpkgRootProp: String? = providers.gradleProperty("vcpkgRoot").orNull

if (vcpkgRootProp != null) {
    val nativeAbis = providers.gradleProperty("nativeAbis").getOrElse("arm64-v8a")

    val buildNativeLibs = tasks.register<Exec>("buildNativeLibs") {
        description = "Baut libtexslate_native.so via build-native.sh (nur im F-Droid-Build)"
        workingDir = rootProject.projectDir
        commandLine(listOf("./build-native.sh") + nativeAbis.split(","))
        environment("VCPKG_ROOT", vcpkgRootProp)
        // jniLibs entsteht erst hier — deshalb kein Input/Output-Tracking, der
        // Task soll im F-Droid-Lauf genau einmal durchlaufen.
        outputs.upToDateWhen { false }
    }

    // Vor dem Einsammeln der jniLibs einhaengen, sonst landet ein leeres
    // Verzeichnis im APK.
    tasks.matching { it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }
        .configureEach { dependsOn(buildNativeLibs) }
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