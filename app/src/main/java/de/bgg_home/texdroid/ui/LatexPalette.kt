package de.bgg_home.texdroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * LaTeX-Einfüge-UI (Kiles „LaTeX"-Menü + Wizards, tablet-tauglich):
 *  • [LatexFavoritesBar] – kurze, immer sichtbare Leiste mit den häufigsten
 *    Bausteinen (1 Tap) + „mehr"-Button.
 *  • [LatexInsertSheet]  – kategorisiertes Bottom-Sheet mit allen Bausteinen.
 *
 * Jeder Snippet enthält genau eine Cursor-Marke [CARET] (◇); nach dem Einfügen
 * landet der Cursor genau dort. Sie wird vor dem Einfügen entfernt und kommt in
 * echtem LaTeX nicht vor.
 */
private const val CARET = '◇'

data class LatexInsert(val label: String, val snippet: String)
data class InsertCategory(val name: String, val items: List<LatexInsert>)

/** Wendet einen Baustein an: bereinigt die Cursor-Marke und meldet den Offset. */
private fun LatexInsert.applyTo(onInsert: (String, Int) -> Unit) {
    val marker = snippet.indexOf(CARET)
    val clean = snippet.replace(CARET.toString(), "")
    onInsert(clean, if (marker < 0) clean.length else marker)
}

private val STRUKTUR = InsertCategory(
    "Struktur", listOf(
        LatexInsert("\\section", "\\section{$CARET}"),
        LatexInsert("\\subsection", "\\subsection{$CARET}"),
        LatexInsert("textbf", "\\textbf{$CARET}"),
        LatexInsert("emph", "\\emph{$CARET}"),
        LatexInsert("\\item", "\\item $CARET"),
        LatexInsert("label", "\\label{$CARET}"),
        LatexInsert("ref", "\\ref{$CARET}"),
        LatexInsert("cite", "\\cite{$CARET}"),
        LatexInsert("footnote", "\\footnote{$CARET}"),
    ),
)

private val UMGEBUNGEN = InsertCategory(
    "Umgebungen", listOf(
        LatexInsert("itemize", "\\begin{itemize}\n    \\item $CARET\n\\end{itemize}"),
        LatexInsert("enumerate", "\\begin{enumerate}\n    \\item $CARET\n\\end{enumerate}"),
        LatexInsert("equation", "\\begin{equation}\n    $CARET\n\\end{equation}"),
        LatexInsert("align", "\\begin{align}\n    $CARET\n\\end{align}"),
        LatexInsert("figure", "\\begin{figure}[h]\n    \\centering\n    \\includegraphics{$CARET}\n    \\caption{}\n\\end{figure}"),
        LatexInsert("table", "\\begin{table}[h]\n    \\centering\n    \\begin{tabular}{cc}\n        $CARET &  \\\\\n    \\end{tabular}\n    \\caption{}\n\\end{table}"),
        LatexInsert("tabular", "\\begin{tabular}{cc}\n    $CARET &  \\\\\n\\end{tabular}"),
        LatexInsert("verbatim", "\\begin{verbatim}\n$CARET\n\\end{verbatim}"),
        LatexInsert("quote", "\\begin{quote}\n    $CARET\n\\end{quote}"),
    ),
)

private val MATHE = InsertCategory(
    "Mathe", listOf(
        LatexInsert("\$…\$", "\$$CARET\$"),
        LatexInsert("frac", "\\frac{$CARET}{}"),
        LatexInsert("sqrt", "\\sqrt{$CARET}"),
        LatexInsert("x^{}", "^{$CARET}"),
        LatexInsert("x_{}", "_{$CARET}"),
        LatexInsert("sum", "\\sum_{$CARET}^{}"),
        LatexInsert("int", "\\int_{$CARET}^{}"),
        LatexInsert("lim", "\\lim_{$CARET}"),
        LatexInsert("·", "\\cdot"),
        LatexInsert("×", "\\times"),
        LatexInsert("≤", "\\leq"),
        LatexInsert("≥", "\\geq"),
        LatexInsert("≠", "\\neq"),
        LatexInsert("≈", "\\approx"),
        LatexInsert("→", "\\to"),
        LatexInsert("∞", "\\infty"),
        LatexInsert("∂", "\\partial"),
        LatexInsert("∇", "\\nabla"),
    ),
)

private val GRIECHISCH = InsertCategory(
    "Griechisch", listOf(
        LatexInsert("α", "\\alpha"), LatexInsert("β", "\\beta"), LatexInsert("γ", "\\gamma"),
        LatexInsert("δ", "\\delta"), LatexInsert("ε", "\\epsilon"), LatexInsert("θ", "\\theta"),
        LatexInsert("λ", "\\lambda"), LatexInsert("μ", "\\mu"), LatexInsert("π", "\\pi"),
        LatexInsert("ρ", "\\rho"), LatexInsert("σ", "\\sigma"), LatexInsert("φ", "\\phi"),
        LatexInsert("ω", "\\omega"), LatexInsert("Γ", "\\Gamma"), LatexInsert("Δ", "\\Delta"),
        LatexInsert("Θ", "\\Theta"), LatexInsert("Λ", "\\Lambda"), LatexInsert("Π", "\\Pi"),
        LatexInsert("Σ", "\\Sigma"), LatexInsert("Φ", "\\Phi"), LatexInsert("Ω", "\\Omega"),
    ),
)

private val CATEGORIES = listOf(STRUKTUR, UMGEBUNGEN, MATHE, GRIECHISCH)

/** Kurze Favoriten (1 Tap), Rest über den „mehr"-Button. */
private val FAVORITES = listOf(
    STRUKTUR.items[0],   // \section
    UMGEBUNGEN.items[0], // itemize
    UMGEBUNGEN.items[2], // equation
    MATHE.items[0],      // $…$
    MATHE.items[1],      // frac
    MATHE.items[3],      // x^{}
    GRIECHISCH.items[8], // π
)

@Composable
private fun InsertChip(entry: LatexInsert, onInsert: (String, Int) -> Unit) {
    AssistChip(
        onClick = { entry.applyTo(onInsert) },
        label = {
            Text(entry.label, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelLarge)
        },
        colors = AssistChipDefaults.assistChipColors(
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

/**
 * Immer sichtbare Favoriten-Leiste + „mehr"-Button (öffnet das Bottom-Sheet).
 */
@Composable
fun LatexFavoritesBar(
    onInsert: (String, Int) -> Unit,
    onOpenMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(FAVORITES) { entry -> InsertChip(entry, onInsert) }
            item {
                IconButton(onClick = onOpenMore) {
                    Icon(
                        Icons.Filled.GridView,
                        contentDescription = "Mehr LaTeX-Bausteine",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Kategorisiertes Bottom-Sheet mit ALLEN Bausteinen. Bleibt beim Einfügen offen,
 * damit mehrere Symbole nacheinander eingefügt werden können.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LatexInsertSheet(
    onInsert: (String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                "LaTeX einfügen",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            CATEGORIES.forEach { category ->
                Text(
                    category.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    category.items.forEach { entry -> InsertChip(entry, onInsert) }
                }
            }
        }
    }
}
