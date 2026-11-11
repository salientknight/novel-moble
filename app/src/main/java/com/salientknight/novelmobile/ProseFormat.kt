package com.salientknight.novelmobile

/**
 * Lossless editor envelope: preserve novelWriter metadata and its structural heading.
 * Complex markup remains in source mode until a rich-text round-trip is implemented.
 */
data class ProseDocument(
    val prefix: String,
    val prose: String,
    val suffix: String,
    val plainMode: Boolean
) {
    fun rebuild(text: String): String = prefix + text + suffix
}

object ProseFormat {
    private val header = Regex("\\A\\+\\+\\+\\r?\\n[\\s\\S]*?\\r?\\n\\+\\+\\+\\r?\\n")
    private val heading = Regex("\\A(#{1,6} [^\\r\\n]*\\r?\\n)")
    private val complex = Regex(
        "(?m)(^\\s*(?:#{1,6} |>|[-*+] |\\d+\\. |\\[|\\{|\\|)|" +
            "\\*\\*|__|(?<!\\w)\\*(?!\\s)|(?<!\\w)_(?!\\s)|" +
            "\\[br\\]|\\[vspace:|\\[field:|<<|>>|\\x60|!\\[|\\]\\(|<[^>]+>)"
    )

    fun parse(raw: String): ProseDocument {
        val metadata = header.find(raw) ?: return ProseDocument("", raw, "", false)
        val afterMetadata = raw.substring(metadata.value.length)
        val title = heading.find(afterMetadata)?.value ?: ""
        val prefix = metadata.value + title
        val body = raw.substring(prefix.length)
        return ProseDocument(prefix, body, "", !complex.containsMatchIn(body))
    }
}
