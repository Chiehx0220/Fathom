package io.github.aedev.flow.localserver

/** Fills the single-page app's HTML template (assets/web/app/index.html) with this phone's colours and sources. */
object WebShell {
    /** A complete HTML document: the template with this phone's colours and sources filled in. */
    fun appPage(): String {
        val services =
            LocalHttpServer.SUPPORTED_SERVICE_IDS.joinToString(",") { id ->
                "[$id,${HtmlRendererCommon.jsonString(HtmlRendererCommon.getServiceName(id))}]"
            }
        return WebAssets.appShell
            .replace("{{version}}", WebAssets.appVersion)
            .replace("{{deviceColors}}", WebTheme.deviceColors(":root[data-scheme=\"light\"]", ":root[data-scheme=\"dark\"]"))
            .replace("{{services}}", "[$services]")
    }
}
