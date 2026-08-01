package de.bgg_home.texdroid.compile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests für den Index-Aufbau ([MakeIndex]) — der Ersatz für das Programm `makeindex`. */
class MakeIndexTest {

    private fun build(vararg zeilen: String) = MakeIndex.build(zeilen.joinToString("\n"))!!

    @Test
    fun einfacheEintraege_alphabetischMitSeiten() {
        val ind = build(
            "\\indexentry{Zebra}{1}",
            "\\indexentry{Qualle}{1}",
            "\\indexentry{Zebra}{2}",
        )
        assertTrue(ind.startsWith("\\begin{theindex}"))
        assertTrue(ind.trimEnd().endsWith("\\end{theindex}"))
        assertTrue("Seiten zusammengefasst", ind.contains("\\item Zebra, 1, 2"))
        assertTrue(ind.contains("\\item Qualle, 1"))
        assertTrue("Qualle vor Zebra", ind.indexOf("Qualle") < ind.indexOf("Zebra"))
    }

    @Test
    fun dubletten_verschwinden() {
        val ind = build("\\indexentry{Zebra}{3}", "\\indexentry{Zebra}{3}")
        assertEquals(1, Regex(", 3").findAll(ind).count())
    }

    @Test
    fun seiten_numerischSortiert() {
        val ind = build(
            "\\indexentry{A}{10}", "\\indexentry{A}{2}", "\\indexentry{A}{1}",
        )
        assertTrue(ind.contains("\\item A, 1, 2, 10"))
    }

    @Test
    fun unterpunkte() {
        val ind = build(
            "\\indexentry{Tier!Zebra}{5}",
            "\\indexentry{Tier!Qualle}{6}",
        )
        assertTrue("Oberpunkt steht da", ind.contains("\\item Tier"))
        assertTrue(ind.contains("\\subitem Qualle, 6"))
        assertTrue(ind.contains("\\subitem Zebra, 5"))
    }

    @Test
    fun sortierschluessel_vorDemAt() {
        // alpha@$\alpha$ → sortiert unter „alpha", gesetzt wird $\alpha$
        val ind = build("\\indexentry{alpha@\$\\alpha\$}{2}", "\\indexentry{Beta}{3}")
        assertTrue("Anzeige ist die Formel", ind.contains("\\item \$\\alpha\$, 2"))
        assertTrue("alpha vor Beta einsortiert", ind.indexOf("alpha") < ind.indexOf("Beta"))
    }

    @Test
    fun seitenformatierung_mitBalken() {
        val ind = build("\\indexentry{Zebra|textbf}{7}")
        assertTrue(ind.contains("\\item Zebra, \\textbf{7}"))
    }

    @Test
    fun seitenbereich() {
        val ind = build("\\indexentry{Zebra|(}{7}", "\\indexentry{Zebra|)}{9}")
        assertTrue("Bereich zusammengefasst: $ind", ind.contains("\\item Zebra, 7--9"))
    }

    @Test
    fun gruppenabstand_zwischenBuchstaben() {
        val ind = build("\\indexentry{Affe}{1}", "\\indexentry{Zebra}{2}")
        assertTrue("Zwischen A und Z ein \\indexspace", ind.contains("\\indexspace"))
    }

    @Test
    fun leereEingabe_gibtNull() {
        assertNull(MakeIndex.build(""))
        assertNull(MakeIndex.build("% nur ein Kommentar\n"))
    }

    @Test
    fun geschweifteKlammernImEintrag_stoerenNicht() {
        val ind = build("\\indexentry{\\texttt{foo!bar}}{4}")
        assertTrue("Kein falscher Unterpunkt: $ind", ind.contains("\\item \\texttt{foo!bar}, 4"))
    }
}
