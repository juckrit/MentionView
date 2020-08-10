package com.example.mentionview.suggestions.interfaces

import com.example.mentionview.suggestions.SuggestionsResult

/*
* Copyright 2015 LinkedIn Corp. All rights reserved.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*/

/**
 * Interface used to listen for the results of a mention suggestion query via a [QueryTokenReceiver].
 */
interface SuggestionsResultListener {
    /**
     * Callback to return a [SuggestionsResult] so that the suggestions it contains can be added to a
     * [SuggestionsAdapter] and rendered accordingly.
     *
     *
     * Note that for any given [QueryToken] that the [QueryTokenReceiver] handles, onReceiveSuggestionsResult
     * may be called multiple times. For example, if you can suggest both people and companies, the
     * [QueryTokenReceiver] will receive a single [QueryToken], but it should call onReceiveSuggestionsResult
     * twice (once with people suggestions and once with company suggestions), using a different bucket each time.
     *
     * @param result a [SuggestionsResult] representing the result of the query
     * @param bucket a string representing the type of mention (used for grouping in the [SuggestionsAdapter]
     */
    fun onReceiveSuggestionsResult(result: SuggestionsResult, bucket: String)
}