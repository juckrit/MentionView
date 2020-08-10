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
package com.example.mentionview.tokenization.impl

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import com.example.mentionview.mentions.MentionSpan
import com.example.mentionview.tokenization.interfaces.Tokenizer

/**
 * Tokenizer class used to determine the keywords to be used when querying for mention suggestions.
 */
class WordTokenizer @JvmOverloads constructor(private val mConfig: WordTokenizerConfig = WordTokenizerConfig.Builder().build()) :
    Tokenizer {
    // --------------------------------------------------
    // Tokenizer Interface Implementation
    // --------------------------------------------------
    /**
     * {@inheritDoc}
     */
    override fun findTokenStart(text: Spanned, cursor: Int): Int {
        val start = getSearchStartIndex(text, cursor)
        var i = cursor

        // If it is explicit, return the index of the first explicit character
        return if (isExplicit(text, cursor)) {
            i--
            while (i >= start) {
                val currentChar = text[i]
                if (isExplicitChar(currentChar)) {
                    if (i == 0 || isWordBreakingChar(text[i - 1])) {
                        return i
                    }
                }
                i--
            }
            // Could not find explicit character before the cursor
            // Note: This case should never happen (means that isExplicit
            // returned true when it should have been false)
            -1
        } else {

            // For implicit tokens, we need to go back a certain number of words to find the start
            // of the token (with the max number of words to go back defined in the config)
            val maxNumKeywords = mConfig.MAX_NUM_KEYWORDS

            // Go back to the start of the word that the cursor is currently in
            while (i > start && !isWordBreakingChar(text[i - 1])) {
                i--
            }

            // Cursor is at beginning of current word, go back MaxNumKeywords - 1 now
            for (j in 0 until maxNumKeywords - 1) {
                // Decrement through only one word-breaking character, if it exists
                if (i > start && isWordBreakingChar(text[i - 1])) {
                    i--
                }
                // If there is more than one word-breaking space, break out now
                // Do not consider queries with words separated by more than one word-breaking char
                if (i > start && isWordBreakingChar(text[i - 1])) {
                    break
                }
                // Decrement until the next space
                while (i > start && !isWordBreakingChar(text[i - 1])) {
                    i--
                }
            }

            // Ensures that text.char(i) is not a word-breaking or explicit char (i.e. cursor must have a
            // word-breaking char in front of it and a non-word-breaking char behind it)
            while (i < cursor && (isWordBreakingChar(text[i]) || isExplicitChar(text[i]))) {
                i++
            }
            i
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun findTokenEnd(text: Spanned, cursor: Int): Int {
        var i = cursor
        val end = getSearchEndIndex(text, cursor)

        // Starting from the cursor, increment i until it reaches the first word-breaking char
        while (i >= 0 && i < end) {
            if (isWordBreakingChar(text[i])) {
                return i
            } else {
                i++
            }
        }
        return i
    }

    /**
     * {@inheritDoc}
     */
    override fun isValidMention(text: Spanned, start: Int, end: Int): Boolean {
        // Get the token
        val token = text.subSequence(start, end)

        // Null or empty string is not a valid mention
        if (TextUtils.isEmpty(token)) {
            return false
        }

        // Handle explicit mentions first, then implicit mentions
        val threshold = mConfig.THRESHOLD
        val multipleWords = containsWordBreakingChar(token)
        val containsExplicitChar = containsExplicitChar(token)
        if (!multipleWords && containsExplicitChar) {

            // If it is one word and has an explicit char, the explicit char must be the first char
            if (!isExplicitChar(token[0])) {
                return false
            }

            // Ensure that the character before the explicit character is a word-breaking character
            // Note: Checks the explicit character closest in front of the cursor
            if (!hasWordBreakingCharBeforeExplicitChar(text, end)) {
                return false
            }

            // Return true if string is just an explicit character
            return if (token.length == 1) {
                true
            } else Character.isLetterOrDigit(token[1])

            // If input has length greater than one, the second character must be a letter or digit
            // Return true if and only if second character is a letter or digit, i.e. "@d"
        } else if (token.length >= threshold) {

            // Change behavior depending on if keywords is one or more words
            return if (!multipleWords) {
                // One word, no explicit characters
                // input is only one word, i.e. "u41"
                onlyLettersOrDigits(token, threshold, 0)
            } else if (containsExplicitChar) {
                // Multiple words, has explicit character
                // Must have a space, the explicit character, then a letter or digit
                (hasWordBreakingCharBeforeExplicitChar(text, end)
                        && isExplicitChar(token[0])
                        && Character.isLetterOrDigit(token[1]))
            } else {
                // Multiple words, no explicit character
                // Either the first or last couple of characters must be letters/digits
                val firstCharactersValid = onlyLettersOrDigits(token, threshold, 0)
                val lastCharactersValid = onlyLettersOrDigits(token, threshold, token.length - threshold)
                firstCharactersValid || lastCharactersValid
            }
        }
        return false
    }

    /**
     * {@inheritDoc}
     */
    override fun terminateToken(text: Spanned): Spanned {
        // Note: We do not need to modify the text to terminate it
        return text
    }

    /**
     * {@inheritDoc}
     */
    override fun isExplicitChar(c: Char): Boolean {
        val explicitChars = mConfig.EXPLICIT_CHARS
        for (i in 0 until explicitChars.length) {
            val explicitChar = explicitChars[i]
            if (c == explicitChar) {
                return true
            }
        }
        return false
    }

    /**
     * {@inheritDoc}
     */
    override fun isWordBreakingChar(c: Char): Boolean {
        val wordBreakChars = mConfig.WORD_BREAK_CHARS
        for (i in 0 until wordBreakChars.length) {
            val wordBreakChar = wordBreakChars[i]
            if (c == wordBreakChar) {
                return true
            }
        }
        return false
    }
    // --------------------------------------------------
    // Public Methods
    // --------------------------------------------------
    /**
     * Returns true if and only if there is an explicit character before the cursor
     * but after any previous mentions. There must be a word-breaking character before the
     * explicit character.
     *
     * @param text   String to determine if it is explicit or not
     * @param cursor position of the cursor in text
     *
     * @return true if the current keywords are explicit (i.e. explicit character typed before cursor)
     */
    fun isExplicit(text: CharSequence, cursor: Int): Boolean {
        return getExplicitChar(text, cursor) != 0.toChar()
    }

    /**
     * Returns the explicit character if appropriate (i.e. within the keywords).
     * If not currently explicit, then returns the null character (i.e. '/0').
     *
     * @param text   String to get the explicit character from
     * @param cursor position of the cursor in text
     *
     * @return the current explicit character or the null character if not currently explicit
     */
    fun getExplicitChar(text: CharSequence, cursor: Int): Char {
        if (cursor < 0 || cursor > text.length) {
            return 0.toChar()
        }
        val ssb = SpannableStringBuilder(text)
        val start = getSearchStartIndex(ssb, cursor)
        var i = cursor - 1
        var numWordBreakingCharsSeen = 0
        while (i >= start) {
            val currentChar = text[i]
            if (isExplicitChar(currentChar)) {
                // Explicit character must have a word-breaking character before it
                return if (i == 0 || isWordBreakingChar(text[i - 1])) {
                    currentChar
                } else {
                    // Otherwise, explicit character is not in a valid position, return null char
                    0.toChar()
                }
            } else if (isWordBreakingChar(currentChar)) {
                // Do not allow the explicit mention to exceed
                numWordBreakingCharsSeen++
                if (numWordBreakingCharsSeen == mConfig.MAX_NUM_KEYWORDS) {
                    // No explicit char in maxNumKeywords, so return null char
                    return 0.toChar()
                }
            }
            i--
        }
        return 0.toChar()
    }

    /**
     * Returns true if the input string contains an explicit character.
     *
     * @param input a [CharSequence] to test
     *
     * @return true if input contains an explicit character
     */
    fun containsExplicitChar(input: CharSequence): Boolean {
        if (!TextUtils.isEmpty(input)) {
            for (i in 0 until input.length) {
                val c = input[i]
                if (isExplicitChar(c)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Returns true if the input string contains a word-breaking character.
     *
     * @param input a [CharSequence] to test
     *
     * @return true if input contains a word-breaking character
     */
    fun containsWordBreakingChar(input: CharSequence): Boolean {
        if (!TextUtils.isEmpty(input)) {
            for (i in 0 until input.length) {
                val c = input[i]
                if (isWordBreakingChar(c)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Given a string and starting index, return true if the first "numCharsToCheck" characters at
     * the starting index are either a letter or a digit.
     *
     * @param input           a [CharSequence] to test
     * @param numCharsToCheck number of characters to examine at starting position
     * @param start           starting position within the input string
     *
     * @return true if the first "numCharsToCheck" at the starting index are either letters or digits
     */
    fun onlyLettersOrDigits(input: CharSequence, numCharsToCheck: Int, start: Int): Boolean {

        // Starting position must be within the input string
        if (start < 0 || start > input.length) {
            return false
        }

        // Check the first "numCharsToCheck" characters to ensure they are a letter or digit
        for (i in 0 until numCharsToCheck) {
            val positionToCheck = start + i
            // Return false if we would throw an Out-of-Bounds exception
            if (positionToCheck >= input.length) {
                return false
            }
            // Return false early if current character is not a letter or digit
            val charToCheck = input[positionToCheck]
            if (!Character.isLetterOrDigit(charToCheck)) {
                return false
            }
        }

        // First "numCharsToCheck" characters are either letters or digits, so return true
        return true
    }
    // --------------------------------------------------
    // Protected Helper Methods
    // --------------------------------------------------
    /**
     * Returns the index of the end of the last span before the cursor or
     * the start of the current line if there are no spans before the cursor.
     *
     * @param text   the [Spanned] to examine
     * @param cursor position of the cursor in text
     *
     * @return the furthest in front of the cursor to search for the current keywords
     */
    fun getSearchStartIndex(text: Spanned, cursor: Int): Int {
        var cursor = cursor
        if (cursor < 0 || cursor > text.length) {
            cursor = 0
        }

        // Get index of the end of the last span before the cursor (or 0 if does not exist)
        val spans = text.getSpans(0, text.length, MentionSpan::class.java)
        var closestToCursor = 0
        for (span in spans) {
            val end = text.getSpanEnd(span)
            if (end > closestToCursor && end <= cursor) {
                closestToCursor = end
            }
        }

        // Get the index of the start of the line
        val textString = text.toString().substring(0, cursor)
        var lineStartIndex = 0
        if (textString.contains(mConfig.LINE_SEPARATOR)) {
            lineStartIndex = textString.lastIndexOf(mConfig.LINE_SEPARATOR) + 1
        }

        // Return whichever is closer before to the cursor
        return Math.max(closestToCursor, lineStartIndex)
    }

    /**
     * Returns the index of the beginning of the first span after the cursor or
     * length of the text if there are no spans after the cursor.
     *
     * @param text   the [Spanned] to examine
     * @param cursor position of the cursor in text
     *
     * @return the furthest behind the cursor to search for the current keywords
     */
    fun getSearchEndIndex(text: Spanned, cursor: Int): Int {
        var cursor = cursor
        if (cursor < 0 || cursor > text.length) {
            cursor = 0
        }

        // Get index of the start of the first span after the cursor (or text.length() if does not exist)
        val spans = text.getSpans(0, text.length, MentionSpan::class.java)
        var closestAfterCursor = text.length
        for (span in spans) {
            val start = text.getSpanStart(span)
            if (start < closestAfterCursor && start >= cursor) {
                closestAfterCursor = start
            }
        }

        // Get the index of the end of the line
        val textString = text.toString().substring(cursor, text.length)
        var lineEndIndex = text.length
        if (textString.contains(mConfig.LINE_SEPARATOR)) {
            lineEndIndex = cursor + textString.indexOf(mConfig.LINE_SEPARATOR)
        }

        // Return whichever is closest after the cursor
        return Math.min(closestAfterCursor, lineEndIndex)
    }

    /**
     * Ensure the the character before the explicit character is a word-breaking character.
     * Note that the function only uses the input string to determine which explicit character was
     * typed. It uses the complete contents of the [EditText] to determine if there is a
     * word-breaking character before the explicit character, as the input string may start with an
     * explicit character, i.e. with an input string of "@John Doe" and an [EditText] containing
     * the string "Hello @John Doe", this should return true.
     *
     * @param text   the [Spanned] to check for a word-breaking character before the explicit character
     * @param cursor position of the cursor in text
     *
     * @return true if there is a space before the explicit character, false otherwise
     */
    fun hasWordBreakingCharBeforeExplicitChar(text: Spanned, cursor: Int): Boolean {
        val beforeCursor = text.subSequence(0, cursor)
        // Get the explicit character closest before the cursor and make sure it
        // has a word-breaking character in front of it
        var i = cursor - 1
        while (i >= 0 && i < beforeCursor.length) {
            val c = beforeCursor[i]
            if (isExplicitChar(c)) {
                return i == 0 || isWordBreakingChar(beforeCursor[i - 1])
            }
            i--
        }
        return false
    }

}