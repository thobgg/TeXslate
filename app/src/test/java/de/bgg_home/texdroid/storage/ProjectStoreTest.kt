package de.bgg_home.texdroid.storage

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests für die (Android-freie) reine Pfad-Logik [ProjectStore.baseDocIdWithin].
 *
 * Hintergrund-Bug: Tectonic schreibt seinen Quelltext immer als `document.tex`
 * in die Job-Wurzel. Lag das Projekt in einem Unterordner des gewählten Baums
 * (Tree `primary:Documents`, Hauptdatei `primary:Documents/texproj/x.tex`), wurde
 * die daneben liegende Klassendatei nach `job/texproj/` gespiegelt – eine Ebene
 * neben `document.tex` → „File 'x.cls' not found". [baseDocIdWithin] liefert den
 * Ordner, ab dem in die Wurzel gespiegelt werden muss.
 */
class ProjectStoreTest {

    @Test
    fun hauptdateiInUnterordner_liefertElternordner() {
        assertEquals(
            "primary:Documents/texproj",
            ProjectStore.baseDocIdWithin("primary:Documents", "primary:Documents/texproj/Beitrag_ZZV.tex"),
        )
    }

    @Test
    fun hauptdateiDirektInTreeWurzel_liefertTreeWurzel() {
        assertEquals(
            "primary:Documents",
            ProjectStore.baseDocIdWithin("primary:Documents", "primary:Documents/M2_Kapitel2.tex"),
        )
    }

    @Test
    fun tiefVerschachtelt_liefertDirektenElternordner() {
        assertEquals(
            "primary:Documents/a/b/c",
            ProjectStore.baseDocIdWithin("primary:Documents", "primary:Documents/a/b/c/main.tex"),
        )
    }

    @Test
    fun keinSlash_liefertTreeWurzel() {
        // Datei-Id ohne '/' (unerwartet oberste Ebene) → konservativ Tree-Wurzel.
        assertEquals(
            "primary:Documents",
            ProjectStore.baseDocIdWithin("primary:Documents", "primary:x.tex"),
        )
    }

    @Test
    fun elternAusserhalbTree_liefertTreeWurzel() {
        // Sollte praktisch nicht vorkommen (Datei muss im Baum liegen), aber die
        // Logik darf nie über die Tree-Wurzel hinaus spiegeln.
        assertEquals(
            "primary:Documents/proj",
            ProjectStore.baseDocIdWithin("primary:Documents/proj", "primary:Elsewhere/x.tex"),
        )
    }

    @Test
    fun praefixAberKeinKind_liefertTreeWurzel() {
        // "primary:Documents2" beginnt mit "primary:Documents", ist aber KEIN
        // Unterordner → darf nicht als Basis durchgehen.
        assertEquals(
            "primary:Documents",
            ProjectStore.baseDocIdWithin("primary:Documents", "primary:Documents2/x.tex"),
        )
    }
}
