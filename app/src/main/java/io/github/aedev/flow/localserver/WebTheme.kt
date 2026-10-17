package io.github.aedev.flow.localserver

/**
 * Material You palette → web UI. Stylesheet defines every colour as a role (`--md-primary`, `--md-surface`, ...) with default;
 * this overrides roles with the device scheme. Fixed accent presets (purple, green, ...): plain CSS in themes.css.
 */
object WebTheme {
    // CSS variable → scheme-map key (from ServerService), per role.
    private val ROLES =
        listOf(
            "md-primary" to "primary",
            "md-on-primary" to "onPrimary",
            "md-secondary-container" to "secondaryContainer",
            "md-on-secondary-container" to "onSecondaryContainer",
            "md-surface" to "surface",
            "md-surface-container" to "surfaceContainer",
            "md-surface-container-low" to "surfaceContainerLow",
            "md-surface-container-high" to "surfaceContainerHigh",
            "md-on-surface" to "onSurface",
            "md-on-surface-variant" to "onSurfaceVariant",
            "md-outline" to "outline",
            "md-outline-variant" to "outlineVariant",
            "md-error" to "error",
            "md-on-error" to "onError",
            "md-error-container" to "errorContainer",
            "md-on-error-container" to "onErrorContainer",
        )

    /** `<style>` body: light scheme on `:root`, dark under `[data-theme="dark"]`. Empty if app gave none. */
    fun deviceColors(
        lightSelector: String = ":root",
        darkSelector: String = "[data-theme=\"dark\"]",
    ): String {
        val sb = StringBuilder()
        if (HtmlRenderer.lightColors.isNotEmpty()) sb.append(block(lightSelector, HtmlRenderer.lightColors))
        if (HtmlRenderer.darkColors.isNotEmpty()) sb.append(block(darkSelector, HtmlRenderer.darkColors))
        return sb.toString()
    }

    private fun block(
        selector: String,
        scheme: Map<String, String>,
    ): String {
        val sb = StringBuilder("$selector {\n")
        for ((variable, key) in ROLES) {
            scheme[key]?.let { sb.append("  --$variable: $it;\n") }
        }
        // Hover/pressed overlays = text colour at 8%/12%; computed here (TV browser may lack color-mix()).
        scheme["onSurface"]?.let {
            sb.append("  --md-state-hover: ${rgba(it, 0.08)};\n")
            sb.append("  --md-state-press: ${rgba(it, 0.12)};\n")
        }
        scheme["onPrimary"]?.let {
            // Controls on a primary fill ("remote connected" banner).
            sb.append("  --md-on-primary-state: ${rgba(it, 0.16)};\n")
            sb.append("  --md-on-primary-outline: ${rgba(it, 0.3)};\n")
        }
        return sb.append("}\n").toString()
    }

    /** #RRGGBB → rgba(r, g, b, alpha). */
    private fun rgba(
        hex: String,
        alpha: Double,
    ): String {
        val h = hex.trim().removePrefix("#")
        if (h.length == 6) {
            val r = h.substring(0, 2).toIntOrNull(16)
            val g = h.substring(2, 4).toIntOrNull(16)
            val b = h.substring(4, 6).toIntOrNull(16)
            if (r != null && g != null && b != null) return "rgba($r, $g, $b, $alpha)"
        }
        return "rgba(127, 127, 127, $alpha)"
    }
}
