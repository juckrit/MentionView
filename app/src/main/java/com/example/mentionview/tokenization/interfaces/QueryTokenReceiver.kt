package com.example.mentionview.tokenization.interfaces

import com.example.mentionview.tokenization.QueryToken

/**
 * Interface used to query an object with a [QueryToken]. The client is responsible for calling an instance of
 * [SuggestionsResultListener] with the results of the query once the query is complete.
 */
interface QueryTokenReceiver {
    /**
     * Called to the client, expecting the client to return a [SuggestionsResult] at a later time via the
     * [SuggestionsResultListener] interface. It returns a List of String that the adapter will use to determine
     * if there are any ongoing queries at a given time.
     *
     * @param queryToken the [QueryToken] to process
     * @return a List of String representing the buckets that will be used when calling [SuggestionsResultListener]
     */
    fun onQueryReceived(queryToken: QueryToken): List<String>
}