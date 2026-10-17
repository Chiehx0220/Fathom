package io.github.aedev.flow.localserver

/** Fills the SPA HTML template (assets/web/app/index.html) with phone colours + sources. */
object WebShell {
    /** Complete HTML document: template with phone colours + sources. */
    fun appPage(): String {
        val services =
            LocalServerMedia.SUPPORTED_SERVICE_IDS.joinToString(",") { id ->
                "[$id,${HtmlRendererCommon.jsonString(HtmlRendererCommon.getServiceName(id))}]"
            }
        return WebAssets.appShell
            .replace("{{version}}", WebAssets.appVersion)
            .replace("{{deviceColors}}", WebTheme.deviceColors(":root[data-scheme=\"light\"]", ":root[data-scheme=\"dark\"]"))
            .replace("{{services}}", "[$services]")
            .replace("{{strings}}", WebAssets.appStrings)
    }
}
