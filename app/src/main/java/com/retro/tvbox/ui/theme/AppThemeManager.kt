package com.retro.tvbox.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import com.retro.tvbox.RetroApplication

enum class UiThemeStyle(
    val id: String,
    val title: String,
    val subtitle: String,
    val tag: String
) {
    PS5_HORIZON(
        id = "ps5_horizon",
        title = "PlayStation 5 Horizon Experience",
        subtitle = "Wallpaper dinámico 4K con halo blanco brillante, HUD cinematográfico y estética limpia de última generación.",
        tag = "PS5 Next-Gen"
    ),
    PS4_DYNAMIC_FLOW(
        id = "ps4_dynamic_flow",
        title = "PlayStation 4 Dynamic Flow",
        subtitle = "Ondas azul profundo (#0a1c3b), vitrina con reflejo y baldosas cuadradas clásicas de PlayStation.",
        tag = "PS4 Classic"
    ),
    STEAM_DECK_BIG_PICTURE(
        id = "steam_big_picture",
        title = "Steam Big Picture Deck",
        subtitle = "Tarjetas de hardware 10-foot, acentos azul cobalto SteamOS (#1a9fff) y atmósfera inmersiva de consola de salón.",
        tag = "SteamOS 10-foot"
    );

    companion object {
        fun fromId(id: String): UiThemeStyle {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: PS5_HORIZON
        }
    }
}

data class UiThemePalette(
    val style: UiThemeStyle,
    val backgroundBase: Color,
    val surfaceColor: Color,
    val surfaceHighlight: Color,
    val focusBorderColor: Color,
    val accentColor: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val topGradientStart: Color,
    val topGradientEnd: Color,
    val bottomGradientStart: Color,
    val bottomGradientEnd: Color
)

object AppThemeManager {
    private const val PREFS_NAME = "retro_tv_theme_prefs"
    private const val KEY_THEME_STYLE = "selected_theme_style"

    private val _currentTheme = mutableStateOf(UiThemeStyle.PS5_HORIZON)
    val currentTheme: State<UiThemeStyle> = _currentTheme

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_THEME_STYLE, UiThemeStyle.PS5_HORIZON.id) ?: UiThemeStyle.PS5_HORIZON.id
        _currentTheme.value = UiThemeStyle.fromId(saved)
    }

    fun setTheme(style: UiThemeStyle, context: Context = RetroApplication.instance) {
        _currentTheme.value = style
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_STYLE, style.id).apply()
    }

    fun getPalette(style: UiThemeStyle = _currentTheme.value): UiThemePalette {
        return when (style) {
            UiThemeStyle.PS5_HORIZON -> UiThemePalette(
                style = style,
                backgroundBase = Color(0xFF060913),
                surfaceColor = Color(0xFF131924),
                surfaceHighlight = Color(0xFF1E293B),
                focusBorderColor = Color(0xFFFFFFFF),
                accentColor = Color(0xFF00E5FF),
                textPrimary = Color(0xFFFFFFFF),
                textSecondary = Color(0xFF94A3B8),
                topGradientStart = Color(0xD9060913),
                topGradientEnd = Color(0x800A101E),
                bottomGradientStart = Color(0x600A101E),
                bottomGradientEnd = Color(0xF2060913)
            )
            UiThemeStyle.PS4_DYNAMIC_FLOW -> UiThemePalette(
                style = style,
                backgroundBase = Color(0xFF081426),
                surfaceColor = Color(0xFF0D223F),
                surfaceHighlight = Color(0xFF15335C),
                focusBorderColor = Color(0xFF0080FF),
                accentColor = Color(0xFF2997FF),
                textPrimary = Color(0xFFF0F6FF),
                textSecondary = Color(0xFF7E9EC7),
                topGradientStart = Color(0xE6081426),
                topGradientEnd = Color(0x990A2246),
                bottomGradientStart = Color(0x730A2246),
                bottomGradientEnd = Color(0xF5081426)
            )
            UiThemeStyle.STEAM_DECK_BIG_PICTURE -> UiThemePalette(
                style = style,
                backgroundBase = Color(0xFF0E141B),
                surfaceColor = Color(0xFF17202D),
                surfaceHighlight = Color(0xFF233144),
                focusBorderColor = Color(0xFF1A9FFF),
                accentColor = Color(0xFF1A9FFF),
                textPrimary = Color(0xFFEBEEF2),
                textSecondary = Color(0xFF8B9BB0),
                topGradientStart = Color(0xE60E141B),
                topGradientEnd = Color(0x9917202D),
                bottomGradientStart = Color(0x7317202D),
                bottomGradientEnd = Color(0xF50E141B)
            )
        }
    }
}
