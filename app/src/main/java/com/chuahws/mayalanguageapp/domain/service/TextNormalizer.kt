package com.chuahws.mayalanguageapp.domain.service

import java.text.Normalizer
import java.util.Locale

object TextNormalizer {
    fun normalize(value: String): String =
        Normalizer.normalize(
            value
                .trim()
                .replace('\'', 'ʼ')
                .replace('’', 'ʼ')
                .replace('ʻ', 'ʼ')
                .replace('ʹ', 'ʼ'),
            Normalizer.Form.NFC,
        )
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
}
