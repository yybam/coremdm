package com.core.mdm.vpn

import android.content.Context
import android.util.Log

/**
 * Where the grouped blocklists come from. Loading is kept apart from matching (DomainMatcher)
 * so the bundled lists can later be swapped for ones downloaded from coremdm.vercel.app by
 * writing another implementation — nothing else has to change.
 */
interface BlocklistSource {
    /** Group name → its entries (`example.com` or `*.example.com`). */
    fun load(): Map<String, Set<String>>
}

/**
 * Reads `assets/blocklists/<group>.txt`, built by tools/build-blocklists.mjs from
 * source/hosts-original.txt. One domain per line; blank lines and `#` comments are ignored.
 */
class AssetBlocklistSource(private val context: Context) : BlocklistSource {

    companion object {
        private const val DIR = "blocklists"
        private const val TAG = "AssetBlocklistSource"
    }

    override fun load(): Map<String, Set<String>> {
        val files = context.assets.list(DIR).orEmpty().filter { it.endsWith(".txt") }.sorted()
        return files.associate { file ->
            val entries = context.assets.open("$DIR/$file").bufferedReader().useLines { lines ->
                lines.map { it.substringBefore('#').trim().lowercase() }
                    .filter { it.isNotEmpty() }
                    .toHashSet()
            }
            file.removeSuffix(".txt") to entries
        }.also { groups ->
            Log.i(TAG, "Loaded ${groups.size} groups, ${groups.values.sumOf { it.size }} entries")
        }
    }
}
