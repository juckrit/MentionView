package com.example.mentionview.mentions
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

import android.os.Parcel
import android.os.Parcelable
import android.text.Selection
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.CharacterStyle
import java.util.*

/**
 * Custom [Editable] containing methods specifically regarding mentions in a [Spanned] string object. Used
 * specifically within the [MentionsEditText].
 */
class MentionsEditable : SpannableStringBuilder, Parcelable {
    constructor(text: CharSequence) : super(text) {}
    constructor(text: CharSequence, start: Int, end: Int) : super(text, start, end) {}
    constructor(`in`: Parcel) : super(`in`.readString()) {
        val length = `in`.readInt()
        if (length > 0) {
            for (index in 0 until length) {
                val start = `in`.readInt()
                val end = `in`.readInt()
                val span = MentionSpan(`in`)
                setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    // --------------------------------------------------
    // Overrides
    // --------------------------------------------------
    override fun setSpan(what: Any, start: Int, end: Int, flags: Int) {
        // Do not add any spans that affect the character appearance of a mention (i.e. they overlap
        // with a MentionSpan). This helps prevent mentions from having a red underline due to the spell
        // checker. Note: SuggestionSpan was added in ICS, and different keyboards may use other kinds
        // of spans (i.e. the Motorola SpellCheckerMarkupSpan). Therefore, we cannot just filter out
        // SuggestionSpans, but rather, any span that would change the appearance of our MentionSpans.
        var start = start
        var end = end
        if (what is CharacterStyle) {
            val mentionSpans = getSpans(start, end, MentionSpan::class.java)
            if (mentionSpans != null && mentionSpans.size > 0) {
                return
            }
        }

        // Ensure that the start and end points are set at zero initially
        // Note: This issue was seen on a Gingerbread device (start and end were both -1) and
        // prevents the device from crashing.
        if ((what === Selection.SELECTION_START || what === Selection.SELECTION_END) && length == 0) {
            start = 0
            end = 0
        }
        super.setSpan(what, start, end, flags)
    }

    override fun replace(start: Int, end: Int, tb: CharSequence, tbstart: Int, tbend: Int): SpannableStringBuilder {
        // On certain software keyboards, the editor appears to append a word minus the last character when it is really
        // trying to just delete the last character. Until we can figure out the root cause of this issue, the following
        // code remaps this situation to do a proper delete.
        if (start == end && start - tbend - 1 >= 0 && tb.length > 1) {
            val insertString = tb.subSequence(tbstart, tbend).toString()
            val previousString = subSequence(start - tbend - 1, start - 1).toString()
            if (insertString == previousString) {
                // Delete a character
                return super.replace(start - 1, start, "", 0, 0)
            }
        }
        return super.replace(start, end, tb, tbstart, tbend)
    }
    // --------------------------------------------------
    // Custom Public Methods
    // --------------------------------------------------
    /**
     * Implementation of [String.trim] for an [Editable].
     *
     * @return a new [MentionsEditable] with whitespace characters removed from the beginning and the end
     */
    fun trim(): MentionsEditable {
        // Delete beginning spaces
        while (length > 0 && Character.isWhitespace(get(0))) {
            delete(0, 1)
        }
        // Delete ending spaces
        var len = length
        while (len > 0 && Character.isWhitespace(get(len - 1))) {
            delete(len - 1, len)
            len--
        }
        return MentionsEditable(this, 0, length)
    }

    val mentionSpans: List<MentionSpan>
        get() {
            val mentionSpans = getSpans(0, length, MentionSpan::class.java)
            return if (mentionSpans != null) Arrays.asList(*mentionSpans) else ArrayList()
        }

    /**
     * Given an integer offset, return the [MentionSpan] located at the offset in the text of the
     * [android.widget.EditText], if it exists. Otherwise, return null.
     *
     * @param index integer offset in text
     *
     * @return a [MentionSpan] located at index in text, or null
     */
    fun getMentionSpanAtOffset(index: Int): MentionSpan? {
        val spans = getSpans(index, index, MentionSpan::class.java)
        return if (spans != null && spans.size > 0) spans[0] else null
    }

    /**
     * Get the [MentionSpan] starting at the given index in the text, or null if there is no [MentionSpan]
     * starting at that index.
     *
     * @param index integer offset in text
     *
     * @return a [MentionSpan] starting at index in text, or null
     */
    fun getMentionSpanStartingAt(index: Int): MentionSpan? {
        val spans = getSpans(0, length, MentionSpan::class.java)
        if (spans != null) {
            for (span in spans) {
                if (getSpanStart(span) == index) {
                    return span
                }
            }
        }
        return null
    }

    /**
     * Get the [MentionSpan] ending at the given index in the text, or null if there is no [MentionSpan]
     * ending at that index.
     *
     * @param index integer offset in text
     *
     * @return a [MentionSpan] ending at index in text, or null
     */
    fun getMentionSpanEndingAt(index: Int): MentionSpan? {
        val spans = getSpans(0, length, MentionSpan::class.java)
        if (spans != null) {
            for (span in spans) {
                if (getSpanEnd(span) == index) {
                    return span
                }
            }
        }
        return null
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(toString())
        val length = mentionSpans.size
        dest.writeInt(length)
        if (length > 0) {
            for (index in 0 until length) {
                val span = mentionSpans[index]
                dest.writeInt(getSpanStart(span))
                dest.writeInt(getSpanEnd(span))
                span.writeToParcel(dest, flags)
            }
        }
    }

    companion object {
        val CREATOR: Parcelable.Creator<MentionsEditable?> = object : Parcelable.Creator<MentionsEditable?> {
            override fun createFromParcel(`in`: Parcel): MentionsEditable? {
                return MentionsEditable(`in`)
            }

            override fun newArray(size: Int): Array<MentionsEditable?> {
                return arrayOfNulls(size)
            }
        }
    }
}