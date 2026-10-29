package io.github.aedev.flow.ui.screens.settings.backup

/**
 * A readable name for a folder picked through the system picker. Tree document ids look like
 * `primary:Documents/Backups`; the part after the volume is what the user chose.
 */
internal fun folderDisplayName(treeDocumentId: String?): String? =
    treeDocumentId
        ?.substringAfter(':', treeDocumentId)
        ?.trim('/')
        ?.takeIf { it.isNotBlank() }
