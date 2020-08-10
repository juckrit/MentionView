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
package com.example.mentionview.tokenization.interfaces

import android.text.Spanned

/**
 * An interface representing a tokenizer. Similar to [android.widget.MultiAutoCompleteTextView.Tokenizer], but
 * it operates on [Spanned] objects instead of [CharSequence] objects.
 */
interface Tokenizer {
    /**
     * Returns the start of the token that ends at offset cursor within text.
     *
     * @param text   the [Spanned] to find the token in
     * @param cursor position of the cursor in text
     *
     * @return index of the first character in the token
     */
    fun findTokenStart(text: Spanned, cursor: Int): Int

    /**
     * Returns the end of the token that begins at offset cursor within text.
     *
     * @param text   the [Spanned] to find the token in
     * @param cursor position of the cursor in text
     *
     * @return index after the last character in the token
     */
    fun findTokenEnd(text: Spanned, cursor: Int): Int

    /**
     * Return true if the given text is a valid token (either explicit or implicit).
     *
     * @param text  the [Spanned] to check for a valid token
     * @param start index of the first character in the token (see [.findTokenStart])
     * @param end   index after the last character in the token (see (see [.findTokenEnd])
     *
     * @return true if input is a valid mention
     */
    fun isValidMention(text: Spanned, start: Int, end: Int): Boolean

    /**
     * Returns text, modified, to ensure that it ends with a token terminator if necessary.
     *
     * @param text the given [Spanned] object to modify if necessary
     *
     * @return the modified version of the text
     */
    fun terminateToken(text: Spanned): Spanned

    /**
     * Determines if given character is an explicit character according to the current settings of the tokenizer.
     *
     * @param c character to test
     *
     * @return true if c is an explicit character
     */
    fun isExplicitChar(c: Char): Boolean

    /**
     * Determines if given character is an word-breaking character according to the current settings of the tokenizer.
     *
     * @param c character to test
     *
     * @return true if c is an word-breaking character
     */
    fun isWordBreakingChar(c: Char): Boolean
}