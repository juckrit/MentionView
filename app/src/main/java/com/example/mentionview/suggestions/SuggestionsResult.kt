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
package com.example.mentionview.suggestions

import com.example.mentionview.suggestions.interfaces.Suggestible
import com.example.mentionview.tokenization.QueryToken


/**
 * Class representing the results of a query for suggestions.
 */
class SuggestionsResult(val queryToken: QueryToken,
                        val suggestions: MutableList<out Suggestible>) {
    /**
     * Get the [QueryToken] used to generate the mention suggestions.
     *
     * @return a [QueryToken]
     */
    /**
     * Get the list of mention suggestions corresponding to the [QueryToken].
     *
     * @return a List of [com.linkedin.android.spyglass.suggestions.interfaces.Suggestible] representing mention suggestions
     */

}