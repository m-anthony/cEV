package com.snaky.poker.cev.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object DefaultTheme {
    @Composable
    fun initialize() {
        Colors.initialize()
        Typography.initialize()
    }

    object Colors {
        // On initialise avec des valeurs par défaut "Safe" (non-composables)
        var AccentContainer = Color.Unspecified; private set
        var Divider = Color.Unspecified; private set
        var HeaderBackground = Color.Unspecified; private set
        var Interactive = Color.Unspecified; private set
        var OnAccentContainer = Color.Unspecified; private set
        var TableBackground = Color.White; private set
        var TextContrast = Color.Black; private set
        var TextDisabled = Color.Gray.copy(alpha = 0.7f); private set
        var TextPrimary = Color.Black; private set
        var TextSecondary = Color.Gray; private set
        var TooltipBackground = Color.DarkGray; private set
        var TooltipOnBackground = Color.White; private set

        private val SoftRed = Color(0xFFFFEBEE)
        private val DangerRed = Color(0xFFD32F2F)
        // --- Percentiles (Simulation) ---
        // On utilise des couleurs douces basées sur ton code actuel
        val RowMedian = Color(0xFFEEEEEE)
        val RowPositive = Color(0xFFE8F5E9) // Vert très clair (Top)
        val RowNegative = SoftRed // Rouge très clair (Worst)

        // Texte d'alerte (Valeurs négatives)
        val TextDanger = DangerRed

        // Pour le bouton désactivé (un gris neutre et pro)
        val DisabledContainer = Color(0xFFE0E0E0)
        val DisabledContent = Color(0xFF9E9E9E)

        val ActionTechnicalContainer = Color(0xFF607D8B)
        val TextOnActionTechnical = Color(0xFFFFFFFF)

        // Pour les champs en erreur (Rouge très clair/pastel)
        // On utilise un fond quasi blanc avec une pointe de rouge pour la lisibilité
        val ErrorOutline = DangerRed   // Bordure du champ invalide
        val ErrorBackground = SoftRed  // Fond du champ invalide

        // Dans ton DefaultTheme.Colors
        val OptionalOutline = Color(0xFFE0E0E0).copy(alpha = 0.5f)
        val OptionalLabel = Color(0xFF9E9E9E).copy(alpha = 0.7f)

        val PrimaryAction = Color(0xFF6200EE) // Ton violet

        val TextOnAction = Color(0xFFFFFFFF) // Ton White sémantique

        // Utilitaires pour éviter les imports graphiques dans les vues
        val Transparent = Color.Transparent
        val WindowBackground = Color(0xFFF5F5F2)

        @Composable
        internal fun initialize() {
            val scheme = MaterialTheme.colorScheme

            AccentContainer = scheme.primaryContainer.copy(alpha = 0.4f)
            Divider = scheme.outlineVariant.copy(alpha = 0.4f)
            HeaderBackground = scheme.surfaceContainerHighest.copy(alpha = 0.7f)
            Interactive = scheme.primary
            OnAccentContainer = scheme.onPrimaryContainer
            TextPrimary = scheme.onSurface
            TextSecondary = scheme.onSurfaceVariant
            TooltipBackground = scheme.inverseSurface
            TooltipOnBackground = scheme.inverseOnSurface
        }
    }

    object Dimensions {
        val BORDER_THICKNESS = 1.dp
        val BUTTON_HEIGHT = 56.dp
        val CELL_PADDING = 2.dp
        val CHIP_HORIZONTAL_PADDING = 12.dp
        val CHIP_SPACING = 8.dp
        val CHIP_VERTICAL_PADDING = 6.dp
        val CONTAINER_PADDING = 16.dp
        val DIVIDER_THICKNESS = 0.5.dp
        val DROPDOWN_MAX_HEIGHT = 235.dp
        val FORM_SPACING_LARGE = 20.dp
        val FORM_SPACING_SMALL = 8.dp
        val SECTION_SPACING = 16.dp
        val TABLE_ELEVATION = 0.dp
        val TABLE_HEADER_PADDING = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        val TABLE_ROW_MIN_HEIGHT = 24.dp
        val TABLE_ROW_PADDING = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
        const val TOOLTIP_DELAY = 500
        val TOOLTIP_ELEVATION = 4.dp
        val TOOLTIP_PADDING = 8.dp
    }

    object Shapes {
        val Medium = RoundedCornerShape(8.dp)
        val Large = RoundedCornerShape(12.dp)
        val Small = RoundedCornerShape(4.dp)
    }

    object Typography {
        var Annotation = TextStyle.Default ; private set
        var Body = TextStyle.Default ; private set
        var BodyBold = TextStyle.Default ; private set
        var Header = TextStyle.Default ; private set
        var Headline = TextStyle.Default ; private set
        var LabelBold = TextStyle.Default ; private set
        var Small = TextStyle.Default ; private set
        var TitleBold: TextStyle = TextStyle.Default ; private set
        var FieldLabel = TextStyle.Default ; private set
        var ButtonLabel = TextStyle.Default ; private set
        var SectionLabel = TextStyle.Default ; private set

        @Composable
        fun initialize() {
            val typography = MaterialTheme.typography
            Annotation = typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Body = typography.bodyMedium
            BodyBold = typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            Header = typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold)
            Headline = typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold)
            LabelBold = typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            Small = typography.labelMedium
            TitleBold = typography.titleMedium.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
            FieldLabel = typography.bodyMedium
            SectionLabel = typography.labelMedium.copy(
                fontWeight = FontWeight.Bold
            )
            ButtonLabel = typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.25.sp
            )
        }
    }
}