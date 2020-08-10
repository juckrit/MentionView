package com.example.mentionview.mentions

import com.example.mentionview.suggestions.interfaces.Suggestible

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
 * Interface for a model to implement in order for it to be able to be mentioned. This is specifically
 * used by the [MentionsEditText]. Note that all mentions, by definition, are suggestible.
 */
interface Mentionable : Suggestible {
    /**
     * Various display modes that change the text for the mention.
     */
    enum class MentionDisplayMode {
        FULL, PARTIAL, NONE
    }

    /**
     * What action to take when the span is deleted
     */
    enum class MentionDeleteStyle {
        // Clear the underlying text (remove the whole span).
        FULL_DELETE,  // First clear everything but the first name. On a second delete, delete the first name.
        PARTIAL_NAME_DELETE
    }

    /**
     * Get the string representing what the mention should currently be displaying, depending on the given
     * [MentionDisplayMode].
     *
     * @param mode the [MentionDisplayMode] tp ise
     *
     * @return the current text to display to the user
     */
    fun getTextForDisplayMode(mode: MentionDisplayMode): String

    /**
     * Determines how the mention should be handled by a MentionSpan as it is being deleted.
     *
     * @return the proper [MentionDeleteStyle]
     */
    val deleteStyle: MentionDeleteStyle
}