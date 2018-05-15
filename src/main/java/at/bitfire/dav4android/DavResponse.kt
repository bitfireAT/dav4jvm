package at.bitfire.dav4android

import at.bitfire.dav4android.property.SyncToken
import okhttp3.HttpUrl

/**
 * Immutable container for a WebDAV multistatus response. Note that property elements
 * are not immutable automatically.
 */
class DavResponse private constructor(

        /** resource this response is about */
        val url: HttpUrl,

        /** HTTP capabilities reported by an OPTIONS response */
        val capabilities: Set<String>,

        /** WebDAV properties of this resource */
        val properties: List<Property>,

        /** members of the requested collection */
        val members: List<DavResponse>,

        /** removed members of the requested collection (HTTP status 404) */
        val removedMembers: List<DavResponse>,

        /** information about resources which are not members of the requested collection */
        val related: List<DavResponse>,

        /** sync-token as returned by REPORT sync-collection */
        val syncToken: SyncToken?,

        /** whether further results are available (requested collection had HTTP status 507) */
        val furtherResults: Boolean

) {

    /**
     * Convenience method to get a certain property from the current response. Does't take
     * members or related resources into consideration.
     */
    operator fun<T: Property> get(clazz: Class<T>): T? {
        return properties.filterIsInstance(clazz).firstOrNull()
    }


    class Builder(
            val url: HttpUrl
    ) {

        private var capabilities: Set<String> = setOf()
        fun capabilities(newValue: Set<String>): Builder {
            capabilities = newValue
            return this
        }

        private var properties: List<Property> = listOf()
        fun properties(newValue: List<Property>): Builder {
            properties = newValue
            return this
        }

        private val members: MutableList<DavResponse.Builder> = mutableListOf()
        fun addMember(member: DavResponse.Builder): Builder {
            members += member
            return this
        }

        private val removedMembers: MutableList<DavResponse.Builder> = mutableListOf()
        fun addRemovedMember(member: DavResponse.Builder): Builder {
            removedMembers += member
            return this
        }

        private val related: MutableList<DavResponse.Builder> = mutableListOf()
        fun addRelated(related: DavResponse.Builder): Builder {
            this.related += related
            return this
        }

        private var syncToken: SyncToken? = null
        fun syncToken(newValue: SyncToken?): Builder {
            syncToken = newValue
            return this
        }

        private var furtherResults = false
        fun furtherResults(newValue: Boolean): Builder {
            furtherResults = newValue
            return this
        }

        fun build(): DavResponse = DavResponse(
                url,
                capabilities,
                properties,
                members.map { it.build() },
                removedMembers.map { it.build() },
                related.map { it.build() },
                syncToken,
                furtherResults
        )

    }

}
