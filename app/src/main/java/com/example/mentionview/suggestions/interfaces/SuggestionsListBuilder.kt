package com.example.mentionview.suggestions.interfaces

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

import android.content.Context
import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.mentionview.suggestions.SuggestionsResult

/**
 * Interface that defines the list of suggestions to display and how to display them.
 */
interface SuggestionsListBuilder {
    /**
     * Create the list of suggestions from the newest [SuggestionsResult] received for every bucket. This
     * allows you to control the exact order of the suggestions.
     *
     * @param latestResults      newest [SuggestionsResult] for every bucket
     * @param currentTokenString the most recent token, as typed by the user
     *
     * @return a list of [Suggestible] representing the suggestions in proper order
     */
    fun buildSuggestions(latestResults: MutableMap<String?, SuggestionsResult?>,
                         currentTokenString: String): MutableList<Suggestible>

    /**
     * Build a basic view for the given object.
     *
     * @param suggestion  object implementing [Suggestible] to build a view for
     * @param convertView the old view to reuse, if possible
     * @param parent      parent view
     * @param context     current [android.content.Context] within the adapter
     * @param inflater    [android.view.LayoutInflater] to use
     * @param resources   [android.content.res.Resources] to use
     *
     * @return a view for the corresponding [Suggestible] object in the adapter
     */
    fun getView(suggestion: Suggestible,
                convertView: View?,
                parent: ViewGroup?,
                context: Context,
                inflater: LayoutInflater,
                resources: Resources): View
}