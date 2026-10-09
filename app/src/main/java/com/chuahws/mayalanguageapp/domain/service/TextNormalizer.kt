package com.chuahws.mayalanguageapp.domain.service

import java.text.Normalizer
import java.util.Locale

object TextNormalizer {
    fun normalize(value: String): String =
        Normalizer.normalize(value.trim(), Normalizer.Form.NFC)
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
}
