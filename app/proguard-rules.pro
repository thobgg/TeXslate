# SPDX-License-Identifier: GPL-3.0-or-later
# Keep-Regeln fuer R8 (Release-Build). Die Standardregeln von AGP sind aktiv
# (includeDefault); hier nur, was R8 nicht selbst erkennen kann.

# JNI: Die Rust-Bibliothek exportiert Symbole wie
#   Java_de_bgg_1home_texslate_RustBridge_tectonicCompile
# Klassenname und Methodennamen muessen deshalb exakt erhalten bleiben.
-keep class de.bgg_home.texslate.RustBridge {
    native <methods>;
}

# Stacktraces aus Nutzermeldungen lesbar halten (Zeilennummern bleiben,
# Quelldateiname wird vereinheitlicht).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# TextMate (sora-editor-language-textmate / Eclipse tm4e): Grammatiken,
# Themes und Sprachkonfiguration werden per Gson-Reflexion aus JSON gelesen.
# Ohne diese Regeln macht R8 die Modellklassen abstrakt/leer -> Absturz beim
# Start ("Abstract classes can't be instantiated … LanguageConfiguration").
-keep class org.eclipse.tm4e.** { *; }
-keep class io.github.rosemoe.sora.langs.textmate.** { *; }
# Regex-Engine von tm4e laedt Zeichensaetze per Klassenname.
-keep class org.jcodings.** { *; }
-keep class org.joni.** { *; }
# Gson braucht generische Signaturen und TypeToken-Unterklassen.
-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
