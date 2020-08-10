
package com.example.mentionview.suggestions

import android.content.Context
import android.content.res.Resources
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.example.mentionview.suggestions.interfaces.Suggestible
import com.example.mentionview.suggestions.interfaces.SuggestionsListBuilder
import com.example.mentionview.suggestions.interfaces.SuggestionsVisibilityManager
import com.example.mentionview.tokenization.QueryToken
import com.example.mentionview.tokenization.interfaces.TokenSource
import java.util.*

/**
 * Adapter class for displaying suggestions.
 */
class SuggestionsAdapter(private val mContext: Context?,
                         suggestionsVisibilityManager: SuggestionsVisibilityManager?,
                         suggestionsListBuilder: SuggestionsListBuilder?) : BaseAdapter() {
    private val mLock = Any()
    private val mResources: Resources?
    private val mInflater: LayoutInflater?
    private var mSuggestionsVisibilityManager: SuggestionsVisibilityManager?
    private var mSuggestionsListBuilder: SuggestionsListBuilder?
    private val mSuggestions: MutableList<Suggestible>?

    // Map from a given bucket (defined by a unique string) to the latest query result for that bucket
    // Example buckets: "Person-Database", "Person-Network", "Companies-Database", "Companies-Network"
    private val mResultMap: MutableMap<String?, SuggestionsResult?> = HashMap()
    private val mWaitingForResults: MutableMap<QueryToken, MutableSet<String>> = HashMap()
    // --------------------------------------------------
    // Public Methods
    // --------------------------------------------------
    /**
     * Method to notify the adapter that a new [QueryToken] has been received and that
     * suggestions will be added to the adapter once generated.
     *
     * @param queryToken the [QueryToken] that has been received
     * @param buckets    a list of string dictating which buckets the future query results will go into
     */
    fun notifyQueryTokenReceived(queryToken: QueryToken,
                                 buckets: List<String>) {
        synchronized(mLock) {
            var currentBuckets = mWaitingForResults[queryToken]
            if (currentBuckets == null) {
                currentBuckets = HashSet()
            }
            currentBuckets.addAll(buckets)
            mWaitingForResults.put(queryToken, currentBuckets)
        }
    }

    /**
     * Add mention suggestions to a given bucket in the adapter. The adapter tracks the latest result for every given
     * bucket, and passes this information to the SuggestionsManager to construct the list of suggestions in the
     * appropriate order.
     *
     *
     * Note: This should be called exactly once for every bucket returned from the query client.
     *
     * @param result a [SuggestionsResult] containing the suggestions to add
     * @param bucket a string representing the group to place the [SuggestionsResult] into
     * @param source the associated [TokenSource] to use for reference
     */
    fun addSuggestions(result: SuggestionsResult,
                       bucket: String,
                       source: TokenSource
    ) {
        // Add result to proper bucket and remove from waiting
        val query = result.queryToken
        synchronized(mLock) {
            mResultMap[bucket] = result
            val waitingForBuckets = mWaitingForResults[query]
            if (waitingForBuckets != null) {
                waitingForBuckets.remove(bucket)
                if (waitingForBuckets.size == 0) {
                    mWaitingForResults.remove(query)
                }
            }
        }

        // Rebuild the list of suggestions in the appropriate order
        val currentTokenString = source.currentTokenString
        synchronized(mLock) {
            mSuggestions?.clear()
            val suggestions = mSuggestionsListBuilder?.buildSuggestions(mResultMap, currentTokenString)

            // If we have suggestions, add them to the adapter and display them
            if (suggestions?.size!! > 0) {
                mSuggestions?.addAll(suggestions)
                mSuggestionsVisibilityManager!!.displaySuggestions(true)
            } else {
                hideSuggestionsIfNecessary(result.queryToken, source)
            }
        }
        notifyDataSetChanged()
    }

    /**
     * Clear all data from adapter.
     */
    fun clear() {
        mResultMap.clear()
        notifyDataSetChanged()
    }
    // --------------------------------------------------
    // Private Helper Methods
    // --------------------------------------------------
    /**
     * Hides the suggestions if there are no more incoming queries.
     *
     * @param currentQuery the most recent [QueryToken]
     * @param source       the associated [TokenSource] to use for reference
     */
    private fun hideSuggestionsIfNecessary(currentQuery: QueryToken,
                                           source: TokenSource) {
        val queryTS = currentQuery.tokenString
        val currentTS = source.currentTokenString
        if (!isWaitingForResults(currentQuery) && queryTS != null && queryTS == currentTS) {
            mSuggestionsVisibilityManager!!.displaySuggestions(false)
        }
    }

    /**
     * Determines if the adapter is still waiting for results for a given [QueryToken]
     *
     * @param currentQuery the [QueryToken] to check if waiting for results on
     *
     * @return true if still waiting for the results of the current query
     */
    private fun isWaitingForResults(currentQuery: QueryToken): Boolean {
        synchronized(mLock) {
            val buckets: Set<String>? = mWaitingForResults[currentQuery]
            return buckets != null && buckets.size > 0
        }
    }

    // --------------------------------------------------
    // BaseAdapter Overrides
    // --------------------------------------------------
    override fun getCount(): Int {
        return mSuggestions?.size!!
    }

    override fun getItem(position: Int): Suggestible {
        var mention: Suggestible? = null
        if (position >= 0 && position < mSuggestions?.size!!) {
            mention = mSuggestions[position]
        }
        return mention!!
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View, parent: ViewGroup): View {
        if (convertView == null){
            Log.e("test","convertView")
        }
        if (position == null){
            Log.e("test","0")
        }
        if (parent == null){
            Log.e("test","parent")
        }
        val suggestion = getItem(position)
        var v: View? = null
        if (mSuggestionsVisibilityManager != null) {
            v = mSuggestionsListBuilder?.getView(suggestion, convertView, parent, mContext!!, mInflater!!, mResources!!)
        }
        return v!!
    }
    // --------------------------------------------------
    // Setters
    // --------------------------------------------------
    /**
     * Sets the [com.linkedin.android.spyglass.suggestions.interfaces.SuggestionsVisibilityManager] to use.
     *
     * @param suggestionsVisibilityManager the [com.linkedin.android.spyglass.suggestions.interfaces.SuggestionsVisibilityManager] to use
     */
    fun setSuggestionsManager(suggestionsVisibilityManager: SuggestionsVisibilityManager) {
        mSuggestionsVisibilityManager = suggestionsVisibilityManager
    }

    /**
     * Sets the [SuggestionsListBuilder] to use.
     *
     * @param suggestionsListBuilder the [SuggestionsListBuilder] to use
     */
    fun setSuggestionsListBuilder(suggestionsListBuilder: SuggestionsListBuilder) {
        mSuggestionsListBuilder = suggestionsListBuilder
    }

    init {
        mResources = mContext?.resources
        mInflater = mContext?.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        mSuggestionsVisibilityManager = suggestionsVisibilityManager
        mSuggestionsListBuilder = suggestionsListBuilder
        mSuggestions = ArrayList()
    }
}