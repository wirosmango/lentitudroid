package dev.lentitudroid

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Terminal — «под терминал», Material — карточки, пузыри сообщений и отдельное поле ввода. */
enum class UiStyle { Terminal, Material }

data class Palette(
    val bg: Color,
    val fg: Color,
    val dim: Color,
    val err: Color,
    val nicks: List<Color>,
    val dark: Boolean,
    val style: UiStyle = UiStyle.Terminal,
    // ниже — только для Material-стиля
    val primary: Color = fg,
    val onPrimary: Color = bg,
    val primaryContainer: Color = bg,
    val onPrimaryContainer: Color = fg,
    val surfaceHigh: Color = bg,
    val errContainer: Color = bg,
)

private val TuiDarkPalette = Palette(
    bg = Color(0xFF2B2F3A),
    fg = Color.White,
    dim = Color(0xFF8A90A0),
    err = Color(0xFFFF6B6B),
    nicks = listOf(
        Color(0xFF7DCFFF), Color(0xFF9ECE6A), Color(0xFFE0AF68),
        Color(0xFFBB9AF7), Color(0xFFF7768E), Color(0xFF73DACA),
    ),
    dark = true,
)

private val TuiLightPalette = Palette(
    bg = Color(0xFFF5F2EA),
    fg = Color(0xFF2B2F3A),
    dim = Color(0xFF6B7280),
    err = Color(0xFFC62828),
    nicks = listOf(
        Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFF8D6E00),
        Color(0xFF6A1B9A), Color(0xFFC2185B), Color(0xFF00796B),
    ),
    dark = false,
)

private val Md3ELightPalette = Palette(
    bg = Color(0xFFFEF7FF),
    fg = Color(0xFF1D1B20),
    dim = Color(0xFF49454F),
    err = Color(0xFFB3261E),
    nicks = listOf(
        Color(0xFF6750A4), Color(0xFF006A6A), Color(0xFF8B5000),
        Color(0xFF3D6A00), Color(0xFF984061), Color(0xFF0061A4),
    ),
    dark = false,
    style = UiStyle.Material,
    primary = Color(0xFF6750A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    surfaceHigh = Color(0xFFECE6F0),
    errContainer = Color(0xFFF9DEDC),
)

private val Md3EDarkPalette = Palette(
    bg = Color(0xFF141218),
    fg = Color(0xFFE6E0E9),
    dim = Color(0xFFCAC4D0),
    err = Color(0xFFF2B8B5),
    nicks = listOf(
        Color(0xFFD0BCFF), Color(0xFF80D4D4), Color(0xFFFFB870),
        Color(0xFFA7D87B), Color(0xFFFFB0CB), Color(0xFF9ECAFF),
    ),
    dark = true,
    style = UiStyle.Material,
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    surfaceHigh = Color(0xFF2B2930),
    errContainer = Color(0xFF8C1D18),
)

/**
 * Список тем. Команда /theme переключает их по кругу, /theme <id> выбирает конкретную.
 * Чтобы добавить новую, достаточно дописать сюда элемент с палитрой (и style, если нужна другая вёрстка).
 */
enum class AppTheme(val id: String, val palette: Palette) {
    TUI_DARK("tui-dark", TuiDarkPalette),
    TUI_LIGHT("tui-light", TuiLightPalette),
    MD3E_LIGHT("md3e-light", Md3ELightPalette),
    MD3E_DARK("md3e-dark", Md3EDarkPalette);

    fun next(): AppTheme = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromId(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: TUI_DARK
        fun ids(): String = entries.joinToString(", ") { it.id }
    }
}

val LocalPalette = staticCompositionLocalOf { AppTheme.TUI_DARK.palette }

/** Динамические цвета (Monet) доступны с Android 12. */
val dynamicColorsSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Палитра с учётом переключателя «Динамические цвета»: для md3e-тем на Android 12+
 * берёт цвета из системных ресурсов (подстраиваются под обои), иначе — фиксированную палитру темы.
 */
fun resolvePalette(ctx: Context, theme: AppTheme, dynamic: Boolean): Palette {
    val base = theme.palette
    if (!dynamic || base.style != UiStyle.Material || !dynamicColorsSupported) return base
    return dynamicPalette(ctx, base)
}

@RequiresApi(Build.VERSION_CODES.S)
private fun dynamicPalette(ctx: Context, base: Palette): Palette {
    fun c(id: Int) = Color(ctx.getColor(id))
    return if (base.dark) base.copy(
        bg = c(android.R.color.system_neutral1_900),
        fg = c(android.R.color.system_neutral1_100),
        dim = c(android.R.color.system_neutral2_200),
        nicks = listOf(
            c(android.R.color.system_accent1_200), c(android.R.color.system_accent2_200),
            c(android.R.color.system_accent3_200), c(android.R.color.system_accent1_300),
            c(android.R.color.system_accent3_300), c(android.R.color.system_accent2_300),
        ),
        primary = c(android.R.color.system_accent1_200),
        onPrimary = c(android.R.color.system_accent1_800),
        primaryContainer = c(android.R.color.system_accent1_700),
        onPrimaryContainer = c(android.R.color.system_accent1_100),
        surfaceHigh = c(android.R.color.system_neutral1_800),
    ) else base.copy(
        bg = c(android.R.color.system_neutral1_10),
        fg = c(android.R.color.system_neutral1_900),
        dim = c(android.R.color.system_neutral2_700),
        nicks = listOf(
            c(android.R.color.system_accent1_700), c(android.R.color.system_accent2_700),
            c(android.R.color.system_accent3_700), c(android.R.color.system_accent1_500),
            c(android.R.color.system_accent3_500), c(android.R.color.system_accent2_500),
        ),
        primary = c(android.R.color.system_accent1_600),
        onPrimary = c(android.R.color.system_accent1_0),
        primaryContainer = c(android.R.color.system_accent1_100),
        onPrimaryContainer = c(android.R.color.system_accent1_900),
        surfaceHigh = c(android.R.color.system_neutral2_100),
    )
}
