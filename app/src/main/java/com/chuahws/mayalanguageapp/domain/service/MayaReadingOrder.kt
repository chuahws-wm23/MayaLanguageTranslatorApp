package com.chuahws.mayalanguageapp.domain.service

import java.text.Normalizer
import java.util.Locale

/** Romanized reading order, with ch/ch' and tz/tz' as separate letters.
 * Classic Maya h is retained; ts spellings collate with tz. This is a
 * browsing convention for transcriptions, not an alphabet of hieroglyphs.
 */
object MayaReadingOrder : Comparator<String> {
    private val letters=listOf("a","b","ch","ch'","e","h","i","j","k","k'","l","m","n",
        "o","p","p'","s","t","t'","tz","tz'","u","w","x","y","'")
    private val tokens=letters.sortedByDescending { it.length }
    private fun key(value: String): List<Int> {
        val text=Normalizer.normalize(value.lowercase(Locale.ROOT),Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"),"").replace('’','\'').replace('ʼ','\'').replace('ʻ','\'')
            .replace("ts","tz").trim()
        val result=ArrayList<Int>();var i=0
        while(i<text.length) {
            val token=tokens.firstOrNull { text.startsWith(it,i) }
            if(token!=null) { result.add(letters.indexOf(token));i+=token.length }
            else { result.add(if(text[i].isWhitespace()) -1 else 1000+text[i].code);i++ }
        }
        return result
    }
    override fun compare(left: String,right: String): Int {
        val a=key(left);val b=key(right)
        for(i in 0 until minOf(a.size,b.size))if(a[i]!=b[i])return a[i].compareTo(b[i])
        return a.size.compareTo(b.size)
    }
}
