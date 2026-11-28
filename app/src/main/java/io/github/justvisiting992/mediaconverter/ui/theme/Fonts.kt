package io.github.justvisiting992.mediaconverter.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.justvisiting992.mediaconverter.R

/**
 * Vazirmatn, the screen-optimized Vazir typeface (SIL Open Font
 * License), subsetted to the Persian and Latin glyphs the app uses:
 * about 72 KB per weight instead of the full font. The whole app
 * switches to it whenever the effective language is Persian, the one
 * bundled script the system fonts render poorly on many phones.
 * SemiBold borrows the Medium file: three weights are enough range
 * for this UI, and a fourth file is size without benefit.
 */
val VazirFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_medium, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)
