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
package com.example.mentionview.ui

import android.content.*
import android.content.ClipboardManager
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.text.*
import android.text.method.ArrowKeyMovementMethod
import android.text.method.MovementMethod
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import androidx.annotation.MenuRes
import com.example.mentionview.tokenization.interfaces.TokenSource
import com.example.mentionview.R
import com.example.mentionview.mentions.MentionSpan
import com.example.mentionview.mentions.MentionSpanConfig
import com.example.mentionview.mentions.Mentionable
import com.example.mentionview.mentions.Mentionable.MentionDeleteStyle
import com.example.mentionview.mentions.Mentionable.MentionDisplayMode
import com.example.mentionview.mentions.MentionsEditable
import com.example.mentionview.suggestions.interfaces.SuggestionsVisibilityManager
import com.example.mentionview.tokenization.QueryToken
import com.example.mentionview.tokenization.interfaces.QueryTokenReceiver
import com.example.mentionview.tokenization.interfaces.Tokenizer
import java.util.*

/**
 * Class that overrides [EditText] in order to have more control over touch events and selection ranges for use in
 * the [RichEditorView].
 *
 *
 * **XML attributes**
 *
 *
 * See [Attributes][R.styleable.MentionsEditText]
 *
 * @attr ref R.styleable#MentionsEditText_mentionTextColor
 * @attr ref R.styleable#MentionsEditText_mentionTextBackgroundColor
 * @attr ref R.styleable#MentionsEditText_selectedMentionTextColor
 * @attr ref R.styleable#MentionsEditText_selectedMentionTextBackgroundColor
 */
class MentionsEditText : EditText, TokenSource {
    /**
     * @return the [Tokenizer] in use
     */
    /**
     * Sets the tokenizer used by this class. The tokenizer determines how [QueryToken] objects
     * are generated.
     *
     * @param tokenizer the [Tokenizer] to use
     */
    var tokenizer: Tokenizer? = null
    private var mQueryTokenReceiver: QueryTokenReceiver? = null
    private var mSuggestionsVisibilityManager: SuggestionsVisibilityManager? = null
    private val mMentionWatchers: MutableList<MentionWatcher> = ArrayList()
    private val mExternalTextWatchers: MutableList<TextWatcher> = ArrayList()
    private val mInternalTextWatcher = MyWatcher()
    private var mBlockCompletion = false
    private var mIsWatchingText = false
    private var mAvoidPrefixOnTap = false
    private var mAvoidedPrefix: String? = null
    private var mentionSpanFactory: MentionSpanFactory? = null
    private var mentionSpanConfig: MentionSpanConfig? = null
    private var isLongPressed = false
    private var longClickRunnable: CheckLongClickRunnable? = null

    constructor(context: Context) : super(context) {
        init(null, 0)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        init(attrs, defStyle)
    }

    /**
     * Initialization method called by all constructors.
     */
    private fun init(attrs: AttributeSet?, defStyleAttr: Int) {
        // Get the mention span config from custom attributes
        mentionSpanConfig = parseMentionSpanConfigFromAttributes(attrs, defStyleAttr)

        // Must set movement method in order for MentionSpans to be clickable
        movementMethod = MentionsMovementMethod.instance

        // Use MentionsEditable instead of default Editable
        setEditableFactory(MentionsEditableFactory.instance)

        // Start watching itself for text changes
        addTextChangedListener(mInternalTextWatcher)

        // Use default MentionSpanFactory initially
        mentionSpanFactory = MentionSpanFactory()
    }

    private fun parseMentionSpanConfigFromAttributes(attrs: AttributeSet?, defStyleAttr: Int): MentionSpanConfig {
        val context = context
        val builder = MentionSpanConfig.Builder()
        if (attrs == null) {
            return builder.build()
        }
        val attributes = context.theme.obtainStyledAttributes(attrs, R.styleable.MentionsEditText, defStyleAttr, 0)
        @ColorInt val normalTextColor = attributes.getColor(R.styleable.MentionsEditText_mentionTextColor, -1)
        builder.setMentionTextColor(normalTextColor)
        @ColorInt val normalBgColor = attributes.getColor(R.styleable.MentionsEditText_mentionTextBackgroundColor, -1)
        builder.setMentionTextBackgroundColor(normalBgColor)
        @ColorInt val selectedTextColor = attributes.getColor(R.styleable.MentionsEditText_selectedMentionTextColor, -1)
        builder.setSelectedMentionTextColor(selectedTextColor)
        @ColorInt val selectedBgColor = attributes.getColor(R.styleable.MentionsEditText_selectedMentionTextBackgroundColor, -1)
        builder.setSelectedMentionTextBackgroundColor(selectedBgColor)
        attributes.recycle()
        return builder.build()
    }
    // --------------------------------------------------
    // TokenSource Interface Implementation
    // --------------------------------------------------// Get the text and ensure a valid tokenizer is set

    // Use current text to determine token string
    /**
     * {@inheritDoc}
     */
    override val currentTokenString: String
        get() {
            // Get the text and ensure a valid tokenizer is set
            val text = text
            if (tokenizer == null || text == null) {
                return ""
            }

            // Use current text to determine token string
            val cursor = Math.max(selectionStart, 0)
            val start = tokenizer!!.findTokenStart(text, cursor)
            val end = tokenizer!!.findTokenEnd(text, cursor)
            val contentString = text.toString()
            return if (TextUtils.isEmpty(contentString)) "" else contentString.substring(start, end)
        }// Use current text to determine the start and end index of the token

    /**
     * {@inheritDoc}
     */
    override val queryTokenIfValid: QueryToken?
        get() {
            if (tokenizer == null) {
                return null
            }

            // Use current text to determine the start and end index of the token
            val text = mentionsText
            val cursor = Math.max(selectionStart, 0)
            val start = tokenizer!!.findTokenStart(text, cursor)
            val end = tokenizer!!.findTokenEnd(text, cursor)
            if (!tokenizer!!.isValidMention(text, start, end)) {
                return null
            }
            val tokenString = text.subSequence(start, end).toString()
            val firstChar = tokenString[0]
            val isExplicit = tokenizer!!.isExplicitChar(tokenString[0])
            return if (isExplicit) QueryToken(tokenString, firstChar) else QueryToken(tokenString)
        }
    // --------------------------------------------------
    // Touch Event Methods
    // --------------------------------------------------
    /**
     * Called whenever the user touches this [EditText]. This was one of the primary reasons for overriding
     * EditText in this library. This method ensures that when a user taps on a [MentionSpan] in this EditText,
     * [MentionSpan.onClick] is called before the onClick method of this [EditText].
     *
     * @param event the given [MotionEvent]
     *
     * @return true if the [MotionEvent] has been handled
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val touchedSpan = getTouchedSpan(event)

        // Android 6 occasionally throws a NullPointerException inside Editor.onTouchEvent()
        // for ACTION_UP when attempting to display (uninitialised) text handles.
        val superResult: Boolean
        superResult = if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M &&
                event.actionMasked == MotionEvent.ACTION_UP) {
            try {
                super.onTouchEvent(event)
            } catch (ignored: NullPointerException) {
                // Ignore this (see above) - since we're now in an unknown state let's clear all
                // selection (which is still better than an arbitrary crash that we can't control):
                clearFocus()
                true
            }
        } else {
            super.onTouchEvent(event)
        }
        if (event.action == MotionEvent.ACTION_UP) {
            // Don't call the onclick on mention if MotionEvent.ACTION_UP is for long click action,
            if (!isLongPressed && touchedSpan != null) {
                // Manually click span and show soft keyboard
                touchedSpan.onClick(this)
                val context = context
                if (context != null) {
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(this, 0)
                }
                return true
            }
        } else if (event.action == MotionEvent.ACTION_DOWN) {
            isLongPressed = false
            if (isLongClickable && touchedSpan != null) {
                if (longClickRunnable == null) {
                    longClickRunnable = CheckLongClickRunnable()
                }
                longClickRunnable!!.touchedSpan = touchedSpan
                removeCallbacks(longClickRunnable)
                postDelayed(longClickRunnable, ViewConfiguration.getLongPressTimeout().toLong())
            }
        } else if (event.action == MotionEvent.ACTION_CANCEL) {
            isLongPressed = false
        }

        // Check if user clicked on the EditText while showing the suggestions list
        // If so, avoid the current prefix
        if (mAvoidPrefixOnTap
                && mSuggestionsVisibilityManager != null && mSuggestionsVisibilityManager!!.isDisplayingSuggestions) {
            mSuggestionsVisibilityManager!!.displaySuggestions(false)
            val keywords = currentKeywordsString
            val words = keywords.split(" ").toTypedArray()
            if (words.size > 0) {
                val prefix = words[words.size - 1]
                // Note that prefix == "" when user types an explicit character and taps the EditText
                // We must not allow the user to avoid suggestions for the empty string prefix
                // Otherwise, explicit mentions would be broken, see MOB-38080
                if (prefix.length > 0) {
                    setAvoidedPrefix(prefix)
                }
            }
        }
        return superResult
    }

    override fun onTextContextMenuItem(@MenuRes id: Int): Boolean {
        val text = mentionsText
        var min = Math.max(0, selectionStart)
        val selectionEnd = selectionEnd
        val max = if (selectionEnd >= 0) selectionEnd else text.length
        // Ensuring that min is always less than or equal to max.
        min = Math.min(min, max)
        return when (id) {
            android.R.id.cut -> {
                // First copy the span and then remove it from the current EditText
                copy(min, max)
                val span = text.getSpans(min, max, MentionSpan::class.java)
                for (mentionSpan in span) {
                    text.removeSpan(mentionSpan)
                }
                text.delete(min, max)
                true
            }
            android.R.id.copy -> {
                copy(min, max)
                true
            }
            android.R.id.paste -> {
                paste(min, max)
                true
            }
            else -> super.onTextContextMenuItem(id)
        }
    }

    /**
     * Copy the text between start and end in clipboard.
     * If no span is present, text is saved as plain text but if span is present
     * save it in Clipboard using intent.
     */
    private fun copy(@IntRange(from = 0) start: Int, @IntRange(from = 0) end: Int) {
        val text = mentionsText
        val copiedText = text.subSequence(start, end) as SpannableStringBuilder
        val spans = text.getSpans(start, end, MentionSpan::class.java)
        var intent: Intent? = null
        if (spans.size > 0) {
            // Save MentionSpan and it's start offset.
            intent = Intent()
            intent.putExtra(KEY_MENTION_SPANS, spans)
            val spanStart = IntArray(spans.size)
            for (i in spans.indices) {
                spanStart[i] = copiedText.getSpanStart(spans[i])
            }
            intent.putExtra(KEY_MENTION_SPAN_STARTS, spanStart)
        }
        saveToClipboard(copiedText, intent)
    }

    /**
     * Paste clipboard content between min and max positions.
     * If clipboard content contain the MentionSpan, set the span in copied text.
     */
    private fun paste(@IntRange(from = 0) min: Int, @IntRange(from = 0) max: Int) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null) {
            for (i in 0 until clip.itemCount) {
                val item = clip.getItemAt(i)
                val selectedText = item.coerceToText(context).toString()
                val text = mentionsText
                val spans = text.getSpans(min, max, MentionSpan::class.java)
                /*
                 * We need to remove the span between min and max. This is required because in
                 * {@link SpannableStringBuilder#replace(int, int, CharSequence)} existing spans within
                 * the Editable that entirely cover the replaced range are retained, but any that
                 * were strictly within the range that was replaced are removed. In our case the existing
                 * spans are retained if the selection entirely covers the span. So, we just remove
                 * the existing span and replace the new text with that span.
                 */for (span in spans) {
                    if (text.getSpanEnd(span) == min) {
                        // We do not want to remove the span, when we want to paste anything just next
                        // to the existing span. In this case "text.getSpanEnd(span)" will be equal
                        // to min.
                        continue
                    }
                    text.removeSpan(span)
                }
                val intent = item.intent
                // Just set the plain text if we do not have mentions data in the intent/bundle
                if (intent == null) {
                    text.replace(min, max, selectedText)
                    continue
                }
                val bundle = intent.extras
                if (bundle == null) {
                    text.replace(min, max, selectedText)
                    continue
                }
                bundle.classLoader = context.classLoader
                val spanStart = bundle.getIntArray(KEY_MENTION_SPAN_STARTS)
                val parcelables = bundle.getParcelableArray(KEY_MENTION_SPANS)
                if (parcelables == null || parcelables.size <= 0 || spanStart == null || spanStart.size <= 0) {
                    text.replace(min, max, selectedText)
                    continue
                }

                // Set the MentionSpan in text.
                val s = SpannableStringBuilder(selectedText)
                for (j in parcelables.indices) {
                    val mentionSpan = parcelables[j] as MentionSpan
                    s.setSpan(mentionSpan, spanStart[j], spanStart[j] + mentionSpan.displayString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                text.replace(min, max, s)
            }
        }
    }

    /**
     * Save the selected text and intent in ClipboardManager
     */
    private fun saveToClipboard(selectedText: CharSequence, intent: Intent?) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val item = ClipData.Item(selectedText, intent, null)
        val clip = ClipData(null, arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN), item)
        clipboard.setPrimaryClip(clip)
    }

    /**
     * Gets the [MentionSpan] from the [MentionsEditText] that was tapped.
     *
     *
     * Note: Almost all of this code is taken directly from the Android source code, see:
     * [LinkMovementMethod.onTouchEvent]
     *
     * @param event the given (@link MotionEvent}
     *
     * @return the tapped [MentionSpan], or null if one was not tapped
     */
    fun getTouchedSpan(event: MotionEvent): MentionSpan? {
        val layout = layout
        // Note: Layout can be null if text or width has recently changed, see MOB-38193
        if (event == null || layout == null) {
            return null
        }
        var x = event.x.toInt()
        var y = event.y.toInt()
        x -= totalPaddingLeft
        y -= totalPaddingTop
        x += scrollX
        y += scrollY
        val line = layout.getLineForVertical(y)
        val off = layout.getOffsetForHorizontal(line, x.toFloat())
        val text = text
        if (text != null && off >= getText().length) {
            return null
        }

        // Get the MentionSpans in the area that the user tapped
        // If one exists, call the onClick method manually
        val spans = getText().getSpans(off, off, MentionSpan::class.java)
        return if (spans.size > 0) {
            spans[0]
        } else null
    }
    // --------------------------------------------------
    // Cursor & Selection Event Methods
    // --------------------------------------------------
    /**
     * Called whenever the selection within the [EditText] has changed.
     *
     * @param selStart starting position of the selection
     * @param selEnd   ending position of the selection
     */
    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        // Handle case where there is only one cursor (i.e. not selecting a range, just moving cursor)
        if (selStart == selEnd) {
            if (!onCursorChanged(selStart)) {
                super.onSelectionChanged(selStart, selEnd)
            }
            return
        } else {
            updateSelectionIfRequired(selStart, selEnd)
        }
        super.onSelectionChanged(selStart, selEnd)
    }

    /**
     * Don't allow user to set starting position or ending position of selection within the mention.
     */
    private fun updateSelectionIfRequired(selStart: Int, selEnd: Int) {
        val text = mentionsText
        val startMentionSpan = text.getMentionSpanAtOffset(selStart)
        val endMentionSpan = text.getMentionSpanAtOffset(selEnd)
        var selChanged = false
        var start = selStart
        var end = selEnd
        if (text.getSpanStart(startMentionSpan) < selStart && selStart < text.getSpanEnd(startMentionSpan)) {
            start = text.getSpanStart(startMentionSpan)
            selChanged = true
        }
        if (text.getSpanStart(endMentionSpan) < selEnd && selEnd < text.getSpanEnd(endMentionSpan)) {
            end = text.getSpanEnd(endMentionSpan)
            selChanged = true
        }
        if (selChanged) {
            setSelection(start, end)
        }
    }

    /**
     * Method to handle the cursor changing positions. Returns true if handled, false if it should
     * be passed to the super method.
     *
     * @param index int position of cursor within the text
     *
     * @return true if handled
     */
    private fun onCursorChanged(index: Int): Boolean {
        val text = text ?: return false
        val allSpans = text.getSpans(0, text.length, MentionSpan::class.java)
        for (span in allSpans) {
            // Deselect span if the cursor is not on the span.
            if (span.isSelected && (index < text.getSpanStart(span) || index > text.getSpanEnd(span))) {
                span.isSelected = false
                updateSpan(span)
            }
        }

        // Do not allow the user to set the cursor within a span. If the user tries to do so, select
        // move the cursor to the end of it.
        val currentSpans = text.getSpans(index, index, MentionSpan::class.java)
        if (currentSpans.size != 0) {
            val span = currentSpans[0]
            val start = text.getSpanStart(span)
            val end = text.getSpanEnd(span)
            if (index > start && index < end) {
                super.setSelection(end)
                return true
            }
        }
        return false
    }

    // --------------------------------------------------
    // TextWatcher Implementation
    // --------------------------------------------------
    private inner class MyWatcher : TextWatcher {
        /**
         * {@inheritDoc}
         */
        override fun beforeTextChanged(text: CharSequence, start: Int, before: Int, after: Int) {
            if (mBlockCompletion) {
                return
            }

            // Mark a span for deletion later if necessary
            val changed = markSpans(before, after)

            // If necessary, temporarily remove any MentionSpans that could potentially interfere with composing text
            if (!changed) {
                replaceMentionSpansWithPlaceholdersAsNecessary(text)
            }

            // Call any watchers for text changes
            sendBeforeTextChanged(text, start, before, after)
        }

        /**
         * {@inheritDoc}
         */
        override fun onTextChanged(text: CharSequence, start: Int, before: Int, count: Int) {
            if (mBlockCompletion || text !is Editable || tokenizer == null) {
                return
            }

            // If the editor tries to insert duplicated text, mark the duplicated text for deletion later
            val index = Selection.getSelectionStart(text)
            val tokenizer = tokenizer
            if (tokenizer != null) {
                markDuplicatedTextForDeletionLater(text, index, tokenizer)
            }

            // Call any watchers for text changes
            sendOnTextChanged(text, start, before, count)
        }

        /**
         * {@inheritDoc}
         */
        override fun afterTextChanged(text: Editable) {
            if (mBlockCompletion || text == null) {
                return
            }

            // Block text change handling while we're changing the text (otherwise, may cause infinite loop)
            mBlockCompletion = true

            // Text may have been marked to be removed in (before/on)TextChanged, remove that text now
            removeTextWithinDeleteSpans(text)

            // Some mentions may have been replaced by placeholders temporarily when altering the text, reinsert the
            // mention spans now
            replacePlaceholdersWithCorrespondingMentionSpans(text)

            // Ensure that the text in all the MentionSpans remains unchanged and valid
            ensureMentionSpanIntegrity(text)

            // Handle the change in text (can modify it freely here)
            handleTextChanged()

            // Allow class to listen for changes to the text again
            mBlockCompletion = false

            // Call any watchers for text changes after we have handled it
            sendAfterTextChanged(text)
        }

        /**
         * Notify external text watchers that the text is about to change.
         * See [TextWatcher.beforeTextChanged].
         */
        private fun sendBeforeTextChanged(text: CharSequence, start: Int, before: Int, after: Int) {
            val list: List<TextWatcher> = mExternalTextWatchers
            val count = list.size
            for (i in 0 until count) {
                val watcher = list[i]
                // Self check to avoid infinite loop
                if (watcher !== this) {
                    watcher.beforeTextChanged(text, start, before, after)
                }
            }
        }

        /**
         * Notify external text watchers that the text is changing.
         * See [TextWatcher.onTextChanged].
         */
        private fun sendOnTextChanged(text: CharSequence, start: Int, before: Int, after: Int) {
            val list: List<TextWatcher> = mExternalTextWatchers
            val count = list.size
            for (i in 0 until count) {
                val watcher = list[i]
                // Self check to avoid infinite loop
                if (watcher !== this) {
                    watcher.onTextChanged(text, start, before, after)
                }
            }
        }

        /**
         * Notify external text watchers that the text has changed.
         * See [TextWatcher.afterTextChanged].
         */
        private fun sendAfterTextChanged(text: Editable) {
            val list: List<TextWatcher> = mExternalTextWatchers
            val count = list.size
            for (i in 0 until count) {
                val watcher = list[i]
                // Self check to avoid infinite loop
                if (watcher !== this) {
                    watcher.afterTextChanged(text)
                }
            }
        }
    }

    /**
     * Marks a span for deletion later if necessary by checking if the last character in a MentionSpan
     * is deleted by this change. If so, mark the span to be deleted later when
     * [.ensureMentionSpanIntegrity] is called in [.handleTextChanged].
     *
     * @param count length of affected text before change starting at start in text
     * @param after length of affected text after change
     *
     * @return  true if there is a span before the cursor that is going to change state
     */
    private fun markSpans(count: Int, after: Int): Boolean {
        val cursor = selectionStart
        val text = mentionsText
        val prevSpan = text.getMentionSpanEndingAt(cursor)
        val isNeedToMarkSpan = (count == after + 1 || after == 0) && prevSpan != null
        if (isNeedToMarkSpan) {

            // Cursor was directly behind a span and was moved back one, so delete it if selected,
            // or select it if not already selected
            if (prevSpan!!.isSelected) {
                val mention = prevSpan.mention
                val deleteStyle = mention.deleteStyle
                val displayMode = prevSpan.displayMode
                // Determine new DisplayMode given previous DisplayMode and MentionDeleteStyle
                if (deleteStyle === MentionDeleteStyle.PARTIAL_NAME_DELETE
                        && displayMode === MentionDisplayMode.FULL) {
                    prevSpan.displayMode = MentionDisplayMode.PARTIAL
                } else {
                    prevSpan.displayMode = MentionDisplayMode.NONE
                }
            } else {
                // Span was not selected, so select it
                prevSpan.isSelected = true
            }
            return true
        }
        return false
    }

    /**
     * Temporarily remove MentionSpans that may interfere with composing text. Note that software keyboards are allowed
     * to place arbitrary spans over the text. This was resulting in several bugs in edge cases while handling the
     * MentionSpans while composing text (with different issues for different keyboards). The easiest solution for this
     * is to remove any MentionSpans that could cause issues while the user is changing text.
     *
     * Note: The MentionSpans are added again in [.replacePlaceholdersWithCorrespondingMentionSpans]
     *
     * @param text the current text before it changes
     */
    private fun replaceMentionSpansWithPlaceholdersAsNecessary(text: CharSequence) {
        val index = selectionStart
        val wordStart = findStartOfWord(text, index)
        val editable = getText()
        val mentionSpansInCurrentWord = editable.getSpans(wordStart, index, MentionSpan::class.java)
        for (span in mentionSpansInCurrentWord) {
            if (span.displayMode !== MentionDisplayMode.NONE) {
                val spanStart = editable.getSpanStart(span)
                val spanEnd = editable.getSpanEnd(span)
                editable.setSpan(PlaceholderSpan(span, spanStart, spanEnd),
                        spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
                editable.removeSpan(span)
            }
        }
    }

    /**
     * Helper utility to determine the beginning of a word using the current tokenizer.
     *
     * @param text  the text to examine
     * @param index index of the cursor in the text
     * @return  index of the beginning of the word in text (will be less than or equal to index)
     */
    private fun findStartOfWord(text: CharSequence, index: Int): Int {
        var wordStart = index
        while (wordStart > 0 && tokenizer != null && !tokenizer!!.isWordBreakingChar(text[wordStart - 1])) {
            wordStart--
        }
        return wordStart
    }

    /**
     * Mark text that was duplicated during text composition to delete it later.
     *
     * @param text          the given text
     * @param cursor        the index of the cursor in text
     * @param tokenizer     the [Tokenizer] to use
     */
    private fun markDuplicatedTextForDeletionLater(text: Editable, cursor: Int, tokenizer: Tokenizer) {
        var cursor = cursor
        while (cursor > 0 && tokenizer.isWordBreakingChar(text[cursor - 1])) {
            cursor--
        }
        val wordStart = findStartOfWord(text, cursor)
        val placeholderSpans = text.getSpans(wordStart, wordStart + 1, PlaceholderSpan::class.java)
        for (span in placeholderSpans) {
            val spanEnd = span.originalEnd
            val copyEnd = spanEnd + (spanEnd - wordStart)
            if (copyEnd > spanEnd && copyEnd <= text.length) {
                val endOfMention = text.subSequence(wordStart, spanEnd)
                val copyOfEndOfMentionText = text.subSequence(spanEnd, copyEnd)
                // Note: Comparing strings since we do not want to compare any other aspects of spanned strings
                if (endOfMention.toString() == copyOfEndOfMentionText.toString()) {
                    text.setSpan(DeleteSpan(),
                            spanEnd,
                            copyEnd,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
    }

    /**
     * Removes any [com.linkedin.android.spyglass.ui.MentionsEditText.DeleteSpan]s and the text within them from
     * the given text.
     *
     * @param text the editable containing DeleteSpans to remove
     */
    private fun removeTextWithinDeleteSpans(text: Editable) {
        val deleteSpans = text.getSpans(0, text.length, DeleteSpan::class.java)
        for (span in deleteSpans) {
            val spanStart = text.getSpanStart(span)
            val spanEnd = text.getSpanEnd(span)
            text.replace(spanStart, spanEnd, "")
            text.removeSpan(span)
        }
    }

    /**
     * Replaces any [com.linkedin.android.spyglass.ui.MentionsEditText.PlaceholderSpan] within the given text with
     * the [MentionSpan] it contains.
     *
     * Note: These PlaceholderSpans are added in [.replaceMentionSpansWithPlaceholdersAsNecessary]
     *
     * @param text the final version of the text after it was changed
     */
    private fun replacePlaceholdersWithCorrespondingMentionSpans(text: Editable) {
        val tempSpans = text.getSpans(0, text.length, PlaceholderSpan::class.java)
        for (span in tempSpans) {
            val spanStart = text.getSpanStart(span)
            val mentionDisplayString = span.holder.displayString
            val end = Math.min(spanStart + mentionDisplayString.length, text.length)
            text.replace(spanStart, end, mentionDisplayString)
            text.setSpan(span.holder, spanStart, spanStart + mentionDisplayString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.removeSpan(span)
        }
    }

    /**
     * Ensures that the text within each [MentionSpan] in the [Editable] correctly
     * matches what it should be outputting. If not, replace it with the correct value.
     *
     * @param text the [Editable] to examine
     */
    private fun ensureMentionSpanIntegrity(text: Editable?) {
        if (text == null) {
            return
        }
        val spans = text.getSpans(0, text.length, MentionSpan::class.java)
        var spanAltered = false
        for (span in spans) {
            val start = text.getSpanStart(span)
            val end = text.getSpanEnd(span)
            val spanText: CharSequence = text.subSequence(start, end).toString()
            val displayMode = span.displayMode
            when (displayMode) {
                MentionDisplayMode.PARTIAL, MentionDisplayMode.FULL -> {
                    val name = span.displayString
                    if (!name.contentEquals(spanText) && start >= 0 && start < end && end <= text.length) {
                        // Mention display name does not match what is being shown,
                        // replace text in span with proper display name
                        val cursor = selectionStart
                        val diff = cursor - end
                        text.removeSpan(span)
                        text.replace(start, end, name)
                        if (diff > 0 && start + end + diff < text.length) {
                            text.replace(start + end, start + end + diff, "")
                        }
                        if (name.length > 0) {
                            text.setSpan(span, start, start + name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        // Notify for partially deleted mentions.
                        if (mMentionWatchers.size > 0 && displayMode === MentionDisplayMode.PARTIAL) {
                            notifyMentionPartiallyDeletedWatchers(span.mention, name, start, end)
                        }
                        spanAltered = true
                    }
                }
                MentionDisplayMode.NONE -> {
                    // Mention with DisplayMode == NONE should be deleted from the text
                    val hasListeners = mMentionWatchers.size > 0
                    val textBeforeDelete = if (hasListeners) text.toString() else null
                    text.delete(start, end)
                    setSelection(start)
                    if (hasListeners) {
                        notifyMentionDeletedWatchers(span.mention, textBeforeDelete!!, start, end)
                    }
                    spanAltered = true
                }
                else -> {
                    val hasListeners = mMentionWatchers.size > 0
                    val textBeforeDelete = if (hasListeners) text.toString() else null
                    text.delete(start, end)
                    setSelection(start)
                    if (hasListeners) {
                        notifyMentionDeletedWatchers(span.mention, textBeforeDelete!!, start, end)
                    }
                    spanAltered = true
                }
            }
        }

        // Reset input method if spans have been changed (updates suggestions)
        if (spanAltered) {
            restartInput()
        }
    }

    /**
     * Called after the [Editable] text within the [EditText] has been changed. Note that
     * editing text in this function is guaranteed to be safe and not cause an infinite loop.
     */
    private fun handleTextChanged() {
        // Ignore requests if the last word in keywords is prefixed by the currently avoided prefix
        if (mAvoidedPrefix != null) {
            val keywords = currentKeywordsString.split(" ").toTypedArray()
            // Add null and length check to avoid the ArrayIndexOutOfBoundsException
            if (keywords.size == 0) {
                return
            }
            val lastKeyword = keywords[keywords.size - 1]
            if (lastKeyword.startsWith(mAvoidedPrefix!!)) {
                return
            } else {
                setAvoidedPrefix(null)
            }
        }

        // Request suggestions from the QueryClient
        val queryToken = queryTokenIfValid
        if (queryToken != null && mQueryTokenReceiver != null) {
            // Valid token, so send query to the app for processing
            mQueryTokenReceiver!!.onQueryReceived(queryToken)
        } else {
            // Ensure that the suggestions are hidden
            if (mSuggestionsVisibilityManager != null) {
                mSuggestionsVisibilityManager!!.displaySuggestions(false)
            }
        }
    }
    // --------------------------------------------------
    // Public Methods
    // --------------------------------------------------
    /**
     * Gets the keywords that the [Tokenizer] is currently considering for mention suggestions. Note that this is
     * the keywords string and will not include any explicit character, if present.
     *
     * @return a String representing current keywords in the [EditText]
     */
    val currentKeywordsString: String
        get() {
            var keywordsString = currentTokenString
            if (keywordsString.length > 0 && tokenizer!!.isExplicitChar(keywordsString[0])) {
                keywordsString = keywordsString.substring(1)
            }
            return keywordsString
        }

    /**
     * Resets the given [MentionSpan] in the editor, forcing it to redraw with its latest drawable state.
     *
     * @param span the [MentionSpan] to update
     */
    fun updateSpan(span: MentionSpan) {
        mBlockCompletion = true
        val text = text
        val start = text.getSpanStart(span)
        val end = text.getSpanEnd(span)
        if (start >= 0 && end > start && end <= text.length) {
            text.removeSpan(span)
            text.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        mBlockCompletion = false
    }

    /**
     * Deselects any spans in the editor that are currently selected.
     */
    fun deselectAllSpans() {
        mBlockCompletion = true
        val text = text
        val spans = text.getSpans(0, text.length, MentionSpan::class.java)
        for (span in spans) {
            if (span.isSelected) {
                span.isSelected = false
                updateSpan(span)
            }
        }
        mBlockCompletion = false
    }

    /**
     * Inserts a mention into the token being considered currently.
     *
     * @param mention [Mentionable] to insert a span for
     */
    fun insertMention(mention: Mentionable) {
        if (tokenizer == null) {
            return
        }

        // Setup variables and ensure they are valid
        val text = editableText
        val cursor = selectionStart
        val start = tokenizer!!.findTokenStart(text, cursor)
        val end = tokenizer!!.findTokenEnd(text, cursor)
        if (start < 0 || start >= end || end > text.length) {
            return
        }
        insertMentionInternal(mention, text, start, end)
    }

    /**
     * Inserts a mention. This will not take any token into consideration. This method is useful
     * when you want to insert a mention which doesn't have a token.
     *
     * @param mention [Mentionable] to insert a span for
     */
    fun insertMentionWithoutToken(mention: Mentionable) {
        // Setup variables and ensure they are valid
        val text = editableText
        var index = selectionStart
        index = if (index > 0) index else 0
        insertMentionInternal(mention, text, index, index)
    }

    private fun insertMentionInternal(mention: Mentionable, text: Editable, start: Int, end: Int) {
        // Insert the span into the editor
        val mentionSpan = mentionSpanFactory!!.createMentionSpan(mention, mentionSpanConfig)
        val name = mention.suggestiblePrimaryText
        mBlockCompletion = true
        text.replace(start, end, name)
        val endOfMention = start + name.length
        text.setSpan(mentionSpan, start, endOfMention, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        Selection.setSelection(text, endOfMention)
        ensureMentionSpanIntegrity(text)
        mBlockCompletion = false

        // Notify listeners of added mention
        if (mMentionWatchers.size > 0) {
            notifyMentionAddedWatchers(mention, text.toString(), start, endOfMention)
        }

        // Hide the suggestions and clear adapter
        if (mSuggestionsVisibilityManager != null) {
            mSuggestionsVisibilityManager!!.displaySuggestions(false)
        }

        // Reset input method since text has been changed (updates mention draw states)
        restartInput()
    }

    /**
     * Determines if the [Tokenizer] is looking at an explicit token right now.
     *
     * @return true if the [Tokenizer] is currently considering an explicit query
     */
    val isCurrentlyExplicit: Boolean
        get() {
            val tokenString = currentTokenString
            return tokenString.length > 0 && tokenizer != null && tokenizer!!.isExplicitChar(tokenString[0])
        }

    /**
     * Populate an [AccessibilityEvent] with information about this text view. Note that this implementation uses
     * a copy of the text that is explicitly not an instance of [MentionsEditable]. This is due to the fact that
     * AccessibilityEvent will use the default system classloader when unparcelling the data within the event. This
     * results in a ClassNotFoundException. For more details, see: https://github.com/linkedin/Spyglass/issues/10
     *
     * @param event the populated AccessibilityEvent
     */
    override fun onPopulateAccessibilityEvent(event: AccessibilityEvent) {
        super.onPopulateAccessibilityEvent(event)
        val textList = event.text
        val mentionLessText: CharSequence = textWithoutMentions
        for (i in textList.indices) {
            val text = textList[i]
            if (text is MentionsEditable) {
                textList[i] = mentionLessText
            }
        }
    }

    /**
     * Allows a class to watch for text changes. Note that adding this class to itself will add it
     * to the super class. Other instances of [TextWatcher] will be notified by this class
     * as appropriate (helps prevent infinite loops when text keeps changing).
     *
     * @param watcher the [TextWatcher] to add
     */
    override fun addTextChangedListener(watcher: TextWatcher) {
        if (watcher === mInternalTextWatcher) {
            if (!mIsWatchingText) {
                super.addTextChangedListener(mInternalTextWatcher)
                mIsWatchingText = true
            }
        } else {
            mExternalTextWatchers.add(watcher)
        }
    }

    /**
     * Removes a [TextWatcher] from this class. Note that this function servers as the
     * counterpart to [.addTextChangedListener]).
     *
     * @param watcher the [TextWatcher] to remove
     */
    override fun removeTextChangedListener(watcher: TextWatcher) {
        if (watcher === mInternalTextWatcher) {
            if (mIsWatchingText) {
                super.removeTextChangedListener(mInternalTextWatcher)
                mIsWatchingText = false
            }
        } else {
            // Other watchers are added
            mExternalTextWatchers.remove(watcher)
        }
    }

    /**
     * Register a [com.linkedin.android.spyglass.ui.MentionsEditText.MentionWatcher] in order to receive callbacks
     * when mentions are changed.
     *
     * @param watcher the [com.linkedin.android.spyglass.ui.MentionsEditText.MentionWatcher] to add
     */
    fun addMentionWatcher(watcher: MentionWatcher) {
        if (!mMentionWatchers.contains(watcher)) {
            mMentionWatchers.add(watcher)
        }
    }

    /**
     * Remove a [com.linkedin.android.spyglass.ui.MentionsEditText.MentionWatcher] from receiving anymore callbacks
     * when mentions are changed.
     *
     * @param watcher the [com.linkedin.android.spyglass.ui.MentionsEditText.MentionWatcher] to remove
     */
    fun removeMentionWatcher(watcher: MentionWatcher) {
        mMentionWatchers.remove(watcher)
    }

    // --------------------------------------------------
    // Private Helper Methods
    // --------------------------------------------------
    private fun restartInput() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm?.restartInput(this)
    }

    /**
     * @return the text as an [Editable] (note: not a [MentionsEditable] and does not contain mentions)
     */
    private val textWithoutMentions: Editable
        private get() {
            val text = text
            val sb = SpannableStringBuilder(text)
            val spans = sb.getSpans(0, sb.length, MentionSpan::class.java)
            for (span in spans) {
                sb.removeSpan(span)
            }
            return sb
        }

    private fun notifyMentionAddedWatchers(mention: Mentionable, text: String, start: Int, end: Int) {
        for (watcher in mMentionWatchers) {
            watcher.onMentionAdded(mention, text, start, end)
        }
    }

    private fun notifyMentionDeletedWatchers(mention: Mentionable, text: String, start: Int, end: Int) {
        for (watcher in mMentionWatchers) {
            watcher.onMentionDeleted(mention, text, start, end)
        }
    }

    private fun notifyMentionPartiallyDeletedWatchers(mention: Mentionable, text: String, start: Int, end: Int) {
        for (watcher in mMentionWatchers) {
            watcher.onMentionPartiallyDeleted(mention, text, start, end)
        }
    }
    // --------------------------------------------------
    // Private Classes
    // --------------------------------------------------
    /**
     * Simple class to hold onto a [MentionSpan] temporarily while the text is changing.
     */
    private inner class PlaceholderSpan internal constructor(val holder: MentionSpan, val originalStart: Int, val originalEnd: Int)

    /**
     * Simple class to mark a span of text to delete later.
     */
    private inner class DeleteSpan

    /**
     * Runnable which detects the long click action.
     */
    private inner class CheckLongClickRunnable : Runnable {
        var touchedSpan: MentionSpan? = null
        override fun run() {
            if (isPressed) {
                isLongPressed = true
                if (touchedSpan == null) {
                    return
                }
                val text = mentionsText
                // Set the selection anchor to start and end of the long clicked span and deselect all the span.
                setSelection(text.getSpanStart(touchedSpan), text.getSpanEnd(touchedSpan))
                deselectAllSpans()
            }
        }
    }
    // --------------------------------------------------
    // MentionsEditable Factory
    // --------------------------------------------------
    /**
     * Custom EditableFactory designed so that we can use the customized [MentionsEditable] in place of the
     * default [Editable].
     */
    class MentionsEditableFactory : Editable.Factory() {
        override fun newEditable(source: CharSequence): Editable {
            val text = MentionsEditable(source)
            Selection.setSelection(text, 0)
            return text
        }

        companion object {
            val instance = MentionsEditableFactory()
        }
    }
    // --------------------------------------------------
    // MentionSpan Factory
    // --------------------------------------------------
    /**
     * Custom factory used when creating a [MentionSpan].
     */
    class MentionSpanFactory {
        fun createMentionSpan(mention: Mentionable,
                              config: MentionSpanConfig?): MentionSpan {
            return config?.let { MentionSpan(mention, it) } ?: MentionSpan(mention)
        }
    }
    // --------------------------------------------------
    // MentionsMovementMethod Class
    // --------------------------------------------------
    /**
     * Custom [MovementMethod] for this class used to override specific behavior in [ArrowKeyMovementMethod].
     */
    class MentionsMovementMethod : ArrowKeyMovementMethod() {
        override fun initialize(widget: TextView, text: Spannable) {
            // Do nothing. Note that ArrowKeyMovementMethod calls setSelection(0) here, but we would
            // like to override that behavior (otherwise, cursor is set to beginning of EditText when
            // this method is called).
        }

        companion object {
            private var sInstance: MentionsMovementMethod? = null
            val instance: MovementMethod
                get() {
                    if (sInstance == null) sInstance = MentionsMovementMethod()
                    return sInstance!!
                }
        }
    }
    // --------------------------------------------------
    // Getters & Setters
    // --------------------------------------------------
    /**
     * Convenience method for getting the [MentionsEditable] associated with this class.
     *
     * @return the current text as a [MentionsEditable] specifically
     */
    val mentionsText: MentionsEditable
        get() {
            val text: CharSequence = super.getText()
            return if (text is MentionsEditable) text else MentionsEditable(text)
        }

    /**
     * Sets the receiver of query tokens used by this class. The query token receiver will use the
     * tokens to generate suggestions, which can then be inserted back into this edit text.
     *
     * @param queryTokenReceiver the [QueryTokenReceiver] to use
     */
    fun setQueryTokenReceiver(queryTokenReceiver: QueryTokenReceiver?) {
        mQueryTokenReceiver = queryTokenReceiver
    }

    /**
     * Sets the suggestions manager used by this class.
     *
     * @param suggestionsVisibilityManager the [SuggestionsVisibilityManager] to use
     */
    fun setSuggestionsVisibilityManager(suggestionsVisibilityManager: SuggestionsVisibilityManager?) {
        mSuggestionsVisibilityManager = suggestionsVisibilityManager
    }

    /**
     * Sets the factory used to create MentionSpans within this class.
     *
     * @param factory the [MentionSpanFactory] to use
     */
    fun setMentionSpanFactory(factory: MentionSpanFactory) {
        mentionSpanFactory = factory
    }

    /**
     * Sets the configuration options used when creating MentionSpans.
     *
     * @param config the [MentionSpanConfig] to use
     */
    fun setMentionSpanConfig(config: MentionSpanConfig) {
        mentionSpanConfig = config
    }

    /**
     * Sets the string prefix to avoid creating and displaying suggestions.
     *
     * @param prefix prefix to avoid suggestions
     */
    fun setAvoidedPrefix(prefix: String?) {
        mAvoidedPrefix = prefix
    }

    /**
     * Determines whether the edit text should avoid the current prefix if the user taps on it while
     * it is displaying suggestions (defaults to false).
     *
     * @param avoidPrefixOnTap true if the prefix should be avoided after a tap
     */
    fun setAvoidPrefixOnTap(avoidPrefixOnTap: Boolean) {
        mAvoidPrefixOnTap = avoidPrefixOnTap
    }

    // --------------------------------------------------
    // Save & Restore State
    // --------------------------------------------------
    override fun onSaveInstanceState(): Parcelable {
        val parcelable = super.onSaveInstanceState()
        return SavedState(parcelable, mentionsText)
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        val savedState = state
        super.onRestoreInstanceState(savedState.superState)
        text = savedState.mentionsEditable
    }

    /**
     * Convenience class to save/restore the MentionsEditable state.
     */
    protected class SavedState : BaseSavedState {
        var mentionsEditable: MentionsEditable?

        constructor(superState: Parcelable?, mentionsEditable: MentionsEditable) : super(superState) {
            this.mentionsEditable = mentionsEditable
        }

        private constructor(`in`: Parcel) : super(`in`) {
            mentionsEditable = `in`.readParcelable(MentionsEditable::class.java.classLoader)
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
            dest.writeParcelable(mentionsEditable, flags)
        }

        companion object {
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(`in`: Parcel): SavedState? {
                    return SavedState(`in`)
                }

                override fun newArray(size: Int): Array<SavedState?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }
    // --------------------------------------------------
    // MentionWatcher Interface & Simple Implementation
    // --------------------------------------------------
    /**
     * Interface to receive a callback for mention events.
     */
    interface MentionWatcher {
        /**
         * Callback for when a mention is added.
         *
         * @param mention   the [Mentionable] that was added
         * @param text      the text after the mention was added
         * @param start     the starting index of where the mention was added
         * @param end       the ending index of where the mention was added
         */
        fun onMentionAdded(mention: Mentionable, text: String, start: Int, end: Int)

        /**
         * Callback for when a mention is deleted.
         *
         * @param mention   the [Mentionable] that was deleted
         * @param text      the text before the mention was deleted
         * @param start     the starting index of where the mention was deleted
         * @param end       the ending index of where the mention was deleted
         */
        fun onMentionDeleted(mention: Mentionable, text: String, start: Int, end: Int)

        /**
         * Callback for when a mention is partially deleted.
         *
         * @param mention   the [Mentionable] that was deleted
         * @param text      the text after the mention was partially deleted
         * @param start     the starting index of where the partial mention starts
         * @param end       the ending index of where the partial mention ends
         */
        fun onMentionPartiallyDeleted(mention: Mentionable, text: String, start: Int, end: Int)
    }

    /**
     * Simple implementation of the [com.linkedin.android.spyglass.ui.MentionsEditText.MentionWatcher] interface
     * if you do not want to implement all methods.
     */
    inner class SimpleMentionWatcher : MentionWatcher {
        override fun onMentionAdded(mention: Mentionable, text: String, start: Int, end: Int) {}
        override fun onMentionDeleted(mention: Mentionable, text: String, start: Int, end: Int) {}
        override fun onMentionPartiallyDeleted(mention: Mentionable, text: String, start: Int, end: Int) {}
    }

    companion object {
        private const val KEY_MENTION_SPANS = "mention_spans"
        private const val KEY_MENTION_SPAN_STARTS = "mention_span_starts"
    }
}