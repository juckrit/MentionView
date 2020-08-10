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

/**
 * Class used to configure various parsing options for the [WordTokenizer]. Instantiate using the
 * [WordTokenizerConfig.Builder] class.
 */
class WordTokenizerConfig private constructor(val LINE_SEPARATOR: String,
        // Number of characters required in a word before returning a mention suggestion starting with the word
        // Note: These characters are required to be either letters or digits
                                              var THRESHOLD: Int,
        // Max number of words to consider as keywords in a query
                                              var MAX_NUM_KEYWORDS: Int,
        // Characters to use as explicit mention indicators
                                              val EXPLICIT_CHARS: String,
        // Characters to use to separate words
                                              val WORD_BREAK_CHARS: String) {

    class Builder {
        // Default values for configuration
        private var lineSeparator = System.getProperty("line.separator")
        private var threshold = 4
        private var maxNumKeywords = 1
        private var explicitChars = "@"
        private var wordBreakChars = " ." + System.getProperty("line.separator")
        fun setLineSeparator(lineSeparator: String): Builder {
            this.lineSeparator = lineSeparator
            return this
        }

        fun setThreshold(threshold: Int): Builder {
            this.threshold = threshold
            return this
        }

        fun setMaxNumKeywords(maxNumKeywords: Int): Builder {
            this.maxNumKeywords = maxNumKeywords
            return this
        }

        fun setExplicitChars(explicitChars: String): Builder {
            this.explicitChars = explicitChars
            return this
        }

        fun setWordBreakChars(wordBreakChars: String): Builder {
            this.wordBreakChars = wordBreakChars
            return this
        }

        fun build(): WordTokenizerConfig {
            return WordTokenizerConfig(lineSeparator!!, threshold, maxNumKeywords, explicitChars, wordBreakChars)
        }
    }

}