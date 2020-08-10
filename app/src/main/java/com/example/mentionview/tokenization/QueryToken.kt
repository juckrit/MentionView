package com.example.mentionview.tokenization
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

import java.io.Serializable

/**
 * Class that represents a token from a [Tokenizer] that can be used to query for suggestions.
 *
 *
 * Note that if the query is explicit, the explicit character has not been removed from the start of the token string.
 * To get the string without any explicit character, use [.getKeywords].
 */
class QueryToken(
    /**
     * @return query as typed by the user and detected by the [Tokenizer]
     */
    // what the user typed, exactly, as detected by the tokenizer
    val tokenString: String) : Serializable {

    /**
     * @return the explicit character used in the query, or the null character if the query is implicit
     */
    // if the query was explicit, then this was the character the user typed (otherwise, null char)
    var explicitChar = 0.toChar()
        private set

    constructor(tokenString: String, explicitChar: Char) : this(tokenString) {
        this.explicitChar = explicitChar
    }

    /**
     * Returns a String that should be used to perform the query. It is equivalent to the token string without an explicit
     * character if it exists.
     *
     * @return one or more words that the [QueryTokenReceiver] should use for the query
     */
    val keywords: String
        get() = if (explicitChar.toInt() != 0) tokenString.substring(1) else tokenString

    /**
     * @return true if the query is explicit
     */
    val isExplicit: Boolean
        get() = explicitChar.toInt() != 0

    override fun equals(o: Any?): Boolean {
        val that = o as QueryToken?
        return tokenString != null && that != null && tokenString == that.tokenString
    }

    override fun hashCode(): Int {
        return tokenString.hashCode()
    }

}