package org.schabi.newpipe.localserver

import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import androidx.annotation.AttrRes
import com.google.android.material.color.DynamicColors
import com.google.android.material.R as MaterialR
import io.github.aedev.flow.R

object DynamicColorHelper {

    // Single source of truth for the MD3 baseline palette: per-attr resolve fallback, and full
    // override when the resolved dynamic palette is internally inconsistent (see isColorDark).
    private fun baselinePalette(dark: Boolean): Map<String, String> {
        val p = HashMap<String, String>()
        p["primary"] = if (dark) "#d0bcff" else "#6750A4"
        p["primaryContainer"] = if (dark) "#4f378b" else "#e9ddff"
        p["secondary"] = if (dark) "#ccc2dc" else "#625b71"
        p["secondaryContainer"] = if (dark) "#4a4458" else "#e8def8"
        p["tertiary"] = if (dark) "#efb8c8" else "#7d5260"
        p["tertiaryContainer"] = if (dark) "#633b48" else "#ffd8e4"
        p["surface"] = if (dark) "#141218" else "#fbfafe"
        p["onSurface"] = if (dark) "#e6e1e5" else "#1d1b20"
        p["surfaceContainer"] = if (dark) "#211f26" else "#f3f4f9"
        p["surfaceContainerLow"] = if (dark) "#1d1b20" else "#f7f2fa"
        p["surfaceContainerHigh"] = if (dark) "#2b2930" else "#ece6f0"
        p["outline"] = if (dark) "#938f99" else "#79747e"
        // Roles needed so every CSS variable derives from one tonal scheme, not a hardcoded
        // baseline-purple fallback that clashes with a neutral dynamic palette.
        p["onPrimary"] = if (dark) "#381e72" else "#ffffff"
        p["onSecondaryContainer"] = if (dark) "#e8def8" else "#1d192b"
        p["onSurfaceVariant"] = if (dark) "#cac4d0" else "#49454f"
        p["outlineVariant"] = if (dark) "#49454f" else "#cac4d0"
        p["surfaceContainerHighest"] = if (dark) "#36343b" else "#e6e0e9"
        // MD3 baseline error tones - avoids hardcoded red hex that never adapted to dark mode.
        p["error"] = if (dark) "#f2b8b5" else "#b3261e"
        p["onError"] = if (dark) "#601410" else "#ffffff"
        p["errorContainer"] = if (dark) "#8c1d18" else "#f9dedc"
        p["onErrorContainer"] = if (dark) "#f9dedc" else "#410e0b"
        return p
    }

    @JvmStatic
    fun getThemeColors(context: Context, dark: Boolean): MutableMap<String, String> {
        val colors = HashMap<String, String>()
        val baseline = baselinePalette(dark)
        try {
            val config = Configuration(context.resources.configuration)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                (if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
            val themedContext = context.createConfigurationContext(config)
            themedContext.setTheme(R.style.Theme_LocalMediaServer)

            // Wrap context with dynamic colors if supported
            val wrappedContext = DynamicColors.wrapContextIfAvailable(themedContext)

            fun resolve(name: String, @AttrRes attr: Int) {
                colors[name] = getHexColor(wrappedContext, attr, baseline.getValue(name))
            }

            resolve("primary", MaterialR.attr.colorPrimary)
            resolve("primaryContainer", MaterialR.attr.colorPrimaryContainer)
            resolve("secondary", MaterialR.attr.colorSecondary)
            resolve("secondaryContainer", MaterialR.attr.colorSecondaryContainer)
            resolve("tertiary", MaterialR.attr.colorTertiary)
            resolve("tertiaryContainer", MaterialR.attr.colorTertiaryContainer)
            resolve("surface", MaterialR.attr.colorSurface)
            resolve("onSurface", MaterialR.attr.colorOnSurface)
            resolve("surfaceContainer", MaterialR.attr.colorSurfaceContainer)
            resolve("surfaceContainerLow", MaterialR.attr.colorSurfaceContainerLow)
            resolve("surfaceContainerHigh", MaterialR.attr.colorSurfaceContainerHigh)
            resolve("outline", MaterialR.attr.colorOutline)
            resolve("onPrimary", MaterialR.attr.colorOnPrimary)
            resolve("onSecondaryContainer", MaterialR.attr.colorOnSecondaryContainer)
            resolve("onSurfaceVariant", MaterialR.attr.colorOnSurfaceVariant)
            resolve("outlineVariant", MaterialR.attr.colorOutlineVariant)
            resolve("surfaceContainerHighest", MaterialR.attr.colorSurfaceContainerHighest)
            resolve("error", MaterialR.attr.colorError)
            resolve("onError", MaterialR.attr.colorOnError)
            resolve("errorContainer", MaterialR.attr.colorErrorContainer)
            resolve("onErrorContainer", MaterialR.attr.colorOnErrorContainer)

            val surface = colors["surface"]
            if (surface != null && dark != isColorDark(surface)) {
                colors.putAll(baseline)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return colors
    }

    private fun isColorDark(hexColorIn: String): Boolean {
        return try {
            val hexColor = if (hexColorIn.startsWith("#")) hexColorIn.substring(1) else hexColorIn
            val rgb = hexColor.toInt(16)
            val r = (rgb shr 16) and 0xFF
            val g = (rgb shr 8) and 0xFF
            val b = rgb and 0xFF
            val brightness = (r * 299 + g * 587 + b * 114) / 1000.0
            brightness < 128
        } catch (e: Exception) {
            false
        }
    }

    private fun getHexColor(context: Context, @AttrRes attr: Int, fallback: String): String {
        try {
            val typedValue = TypedValue()
            if (context.theme.resolveAttribute(attr, typedValue, true)) {
                val color = typedValue.data
                return String.format("#%06X", 0xFFFFFF and color)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return fallback
    }
}
