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

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView.OnItemClickListener
import android.widget.ListView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.mentionview.R
import com.example.mentionview.mentions.MentionSpan
import com.example.mentionview.mentions.MentionSpanConfig
import com.example.mentionview.mentions.Mentionable
import com.example.mentionview.mentions.MentionsEditable
import com.example.mentionview.suggestions.SuggestionsAdapter
import com.example.mentionview.suggestions.SuggestionsResult
import com.example.mentionview.suggestions.impl.BasicSuggestionsListBuilder
import com.example.mentionview.suggestions.interfaces.OnSuggestionsVisibilityChangeListener
import com.example.mentionview.suggestions.interfaces.SuggestionsListBuilder
import com.example.mentionview.suggestions.interfaces.SuggestionsResultListener
import com.example.mentionview.suggestions.interfaces.SuggestionsVisibilityManager
import com.example.mentionview.tokenization.QueryToken
import com.example.mentionview.tokenization.impl.WordTokenizer
import com.example.mentionview.tokenization.impl.WordTokenizerConfig
import com.example.mentionview.tokenization.interfaces.QueryTokenReceiver
import com.example.mentionview.tokenization.interfaces.Tokenizer
import com.example.mentionview.ui.MentionsEditText.MentionSpanFactory
import com.example.mentionview.ui.MentionsEditText.MentionWatcher
import java.util.*

/**
 * Custom view for the RichEditor. Manages three subviews:
 *
 *
 * 1. EditText - contains text typed by user <br></br>
 * 2. TextView - displays count of the number of characters in the EditText <br></br>
 * 3. ListView - displays mention suggestions when relevant
 *
 *
 * **XML attributes**
 *
 *
 * See [Attributes][R.styleable.RichEditorView]
 *
 * @attr ref R.styleable#RichEditorView_mentionTextColor
 * @attr ref R.styleable#RichEditorView_mentionTextBackgroundColor
 * @attr ref R.styleable#RichEditorView_selectedMentionTextColor
 * @attr ref R.styleable#RichEditorView_selectedMentionTextBackgroundColor
 */
class RichEditorView : ConstraintLayout, TextWatcher, QueryTokenReceiver, SuggestionsResultListener,
    SuggestionsVisibilityManager {

    private lateinit var myList: ListView
    private var myAdapter: SuggestionsAdapter? = null

    fun setMyList(listView: ListView) {
        myList = listView
        myList.adapter = myAdapter
        myList.setOnItemClickListener(OnItemClickListener { parent, view, position, id ->
            val mention = mSuggestionsAdapter!!.getItem(position) as Mentionable
            if (mMentionsEditText != null) {
                mMentionsEditText!!.insertMention(mention)
                mSuggestionsAdapter!!.clear()
            }
        })
    }

    fun setMyAdapter(adapter: SuggestionsAdapter) {
    }


    private lateinit var mMentionsEditText: MentionsEditText
    private var mOriginalInputType = InputType.TYPE_CLASS_TEXT // Default to plain text
    private var mTextCounterView: TextView? = null
    private lateinit var mSuggestionsList: ListView
    private var mHostQueryTokenReceiver: QueryTokenReceiver? = null
    private var mSuggestionsAdapter: SuggestionsAdapter? = null
    private var mActionListener: OnSuggestionsVisibilityChangeListener? = null
    private var mPrevEditTextParams: ViewGroup.LayoutParams? = null
    private var mEditTextShouldWrapContent =
        false // Default to match parent in height
    private var mPrevEditTextBottomPadding = 0
    private var mTextCountLimit = -1
    private var mWithinCountLimitTextColor = Color.BLACK
    private var mBeyondCountLimitTextColor = Color.RED
    private var mWaitingForFirstResult = false
    private var mDisplayTextCount = true

    // --------------------------------------------------
    // Constructors & Initialization
    // --------------------------------------------------
    constructor(context: Context) : super(context) {
        init(context, null, 0)
    }

    constructor(
        context: Context,
        attrs: AttributeSet?
    ) : super(context, attrs) {
        init(context, attrs, 0)
    }

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr) {
        init(context, attrs, defStyleAttr)
    }

    fun init(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int
    ) {
        // Inflate view from XML layout file
        val inflater =
            context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.editor_view, this, true)

        // Get the inner views
        mMentionsEditText = findViewById(R.id.text_editor)
        mTextCounterView = findViewById(R.id.text_counter)
        mSuggestionsList = findViewById(R.id.suggestions_list)

        // Get the MentionSpanConfig from custom XML attributes and set it
        val mentionSpanConfig =
            parseMentionSpanConfigFromAttributes(attrs, defStyleAttr)
        mMentionsEditText.setMentionSpanConfig(mentionSpanConfig)

        // Create the tokenizer to use for the editor
        // TODO: Allow customization of configuration via XML attributes
        val tokenizerConfig =
            WordTokenizerConfig.Builder().build()
        val tokenizer = WordTokenizer(tokenizerConfig)
        mMentionsEditText.setTokenizer(tokenizer)

        // Set various delegates on the MentionEditText to the RichEditorView
        mMentionsEditText.setSuggestionsVisibilityManager(this)
        mMentionsEditText.addTextChangedListener(this)
        mMentionsEditText.setQueryTokenReceiver(this)
        mMentionsEditText.setAvoidPrefixOnTap(true)

        // Set the suggestions adapter
        val listBuilder: SuggestionsListBuilder = BasicSuggestionsListBuilder()
        mSuggestionsAdapter =
            SuggestionsAdapter(context, this, listBuilder)
        mSuggestionsList.setAdapter(mSuggestionsAdapter)
        myAdapter = mSuggestionsAdapter


        // Set the item click listener
        mSuggestionsList.setOnItemClickListener(OnItemClickListener { parent, view, position, id ->
            val mention = mSuggestionsAdapter!!.getItem(position) as Mentionable
            if (mMentionsEditText != null) {
                mMentionsEditText!!.insertMention(mention)
                mSuggestionsAdapter!!.clear()
            }
        })


        // Display and update the editor text counter (starts it at 0)
        updateEditorTextCount()

        // Wrap the EditText content height if necessary (ideally, allow this to be controlled via custom XML attribute)
        setEditTextShouldWrapContent(mEditTextShouldWrapContent)
        mPrevEditTextBottomPadding = mMentionsEditText.getPaddingBottom()
    }

    private fun parseMentionSpanConfigFromAttributes(
        attrs: AttributeSet?,
        defStyleAttr: Int
    ): MentionSpanConfig {
        val context = context
        val builder =
            MentionSpanConfig.Builder()
        if (attrs == null) {
            return builder.build()
        }
        val attributes = context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.RichEditorView,
            defStyleAttr,
            0
        )
        @ColorInt val normalTextColor =
            attributes.getColor(R.styleable.RichEditorView_mentionTextColor, -1)
        builder.setMentionTextColor(normalTextColor)
        @ColorInt val normalBgColor =
            attributes.getColor(R.styleable.RichEditorView_mentionTextBackgroundColor, -1)
        builder.setMentionTextBackgroundColor(normalBgColor)
        @ColorInt val selectedTextColor =
            attributes.getColor(R.styleable.RichEditorView_selectedMentionTextColor, -1)
        builder.setSelectedMentionTextColor(selectedTextColor)
        @ColorInt val selectedBgColor =
            attributes.getColor(R.styleable.RichEditorView_selectedMentionTextBackgroundColor, -1)
        builder.setSelectedMentionTextBackgroundColor(selectedBgColor)
        attributes.recycle()
        return builder.build()
    }
    // --------------------------------------------------
    // Public Span & UI Methods
    // --------------------------------------------------
    /**
     * Allows filters in the input element.
     *
     *
     * Example: obj.setInputFilters(new InputFilter[]{new InputFilter.LengthFilter(30)});
     *
     * @param filters the list of filters to apply
     */
    fun setInputFilters(vararg filters: InputFilter?) {
        mMentionsEditText!!.filters = filters
    }

    val mentionSpans: List<MentionSpan>
        get() = if (mMentionsEditText != null) mMentionsEditText!!.mentionsText
            .mentionSpans else ArrayList()

    /**
     * Determine whether the internal [EditText] should match the full height of the [RichEditorView]
     * initially or if it should wrap its content in height and expand to fill it as the user types.
     *
     *
     * Note: The [EditText] will always match the parent (i.e. the [RichEditorView] in width.
     * Additionally, the [ListView] containing mention suggestions will always fill the rest
     * of the height in the [RichEditorView].
     *
     * @param enabled true if the [EditText] should wrap its content in height
     */
    fun setEditTextShouldWrapContent(enabled: Boolean) {
        mEditTextShouldWrapContent = enabled
        if (mMentionsEditText == null) {
            return
        }
        mPrevEditTextParams = mMentionsEditText!!.layoutParams
        val wrap =
            if (enabled) LayoutParams.WRAP_CONTENT else LayoutParams.MATCH_PARENT
        if (mPrevEditTextParams != null && mPrevEditTextParams!!.height == wrap) {
            return
        }
        val newParams: ViewGroup.LayoutParams =
            LayoutParams(LayoutParams.MATCH_PARENT, wrap)
        mMentionsEditText!!.layoutParams = newParams
        requestLayout()
        invalidate()
    }

    /**
     * @return current line number of the cursor, or -1 if no cursor
     */
    val currentCursorLine: Int
        get() {
            val selectionStart = mMentionsEditText!!.selectionStart
            val layout = mMentionsEditText!!.layout
            return if (layout != null && selectionStart != -1) {
                layout.getLineForOffset(selectionStart)
            } else -1
        }

    /**
     * Show or hide the text counter view.
     *
     * @param display true to display the text counter view
     */
    fun displayTextCounter(display: Boolean) {
        mDisplayTextCount = display
        if (display) {
            mTextCounterView!!.visibility = TextView.VISIBLE
        } else {
            mTextCounterView!!.visibility = TextView.GONE
        }
    }

    /**
     * @return true if the text counter view is currently visible to the user
     */
    val isDisplayingTextCounter: Boolean
        get() = mTextCounterView != null && mTextCounterView!!.visibility == TextView.VISIBLE
    // --------------------------------------------------
    // TextWatcher Implementation
    // --------------------------------------------------
    /**
     * {@inheritDoc}
     */
    override fun beforeTextChanged(
        s: CharSequence,
        start: Int,
        count: Int,
        after: Int
    ) {
        // Do nothing
    }

    /**
     * {@inheritDoc}
     */
    override fun onTextChanged(
        s: CharSequence,
        start: Int,
        before: Int,
        count: Int
    ) {
        // Do nothing
    }

    /**
     * {@inheritDoc}
     */
    override fun afterTextChanged(s: Editable) {
        updateEditorTextCount()
    }
    // --------------------------------------------------
    // QueryTokenReceiver Implementation
    // --------------------------------------------------
    /**
     * {@inheritDoc}
     */
    override fun onQueryReceived(queryToken: QueryToken): List<String> {
        // Pass the query token to a host receiver
        if (mHostQueryTokenReceiver != null) {
            val buckets =
                mHostQueryTokenReceiver!!.onQueryReceived(queryToken)
            mSuggestionsAdapter!!.notifyQueryTokenReceived(queryToken, buckets)
        }
        return emptyList()
    }
    // --------------------------------------------------
    // SuggestionsResultListener Implementation
    // --------------------------------------------------
    /**
     * {@inheritDoc}
     */
    override fun onReceiveSuggestionsResult(
        result: SuggestionsResult,
        bucket: String
    ) {
        // Add the mentions and notify the editor/dropdown of the changes on the UI thread
        post {
            if (mSuggestionsAdapter != null) {
                mSuggestionsAdapter!!.addSuggestions(result, bucket, mMentionsEditText!!)
            }
            // Make sure the list is scrolled to the top once you receive the first query result
            if (mWaitingForFirstResult && mSuggestionsList != null) {
                mSuggestionsList!!.setSelection(0)
                mWaitingForFirstResult = false
            }

            if (myAdapter != null && myList != null) {
                myAdapter!!.addSuggestions(result, bucket, mMentionsEditText!!)
                myList!!.setSelection(0)
            }
        }
    }
    // --------------------------------------------------
    // SuggestionsManager Implementation
    // --------------------------------------------------
    /**
     * {@inheritDoc}
     */
    override fun displaySuggestions(display: Boolean) {

        // If nothing to change, return early
        if (display == isDisplayingSuggestions || mMentionsEditText == null) {
            return
        }

        // Change view depending on whether suggestions are being shown or not
        if (display) {
            disableSpellingSuggestions(true)
            mTextCounterView!!.visibility = View.GONE
            mSuggestionsList!!.visibility = View.VISIBLE
            myList!!.visibility = View.VISIBLE
            mPrevEditTextParams = mMentionsEditText!!.layoutParams
            mPrevEditTextBottomPadding = mMentionsEditText!!.paddingBottom
            mMentionsEditText!!.setPadding(
                mMentionsEditText!!.paddingLeft,
                mMentionsEditText!!.paddingTop,
                mMentionsEditText!!.paddingRight,
                mMentionsEditText!!.paddingTop
            )
            val height =
                mMentionsEditText!!.paddingTop + mMentionsEditText!!.lineHeight + mMentionsEditText!!.paddingBottom
            mMentionsEditText!!.layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                height
            )
            mMentionsEditText!!.isVerticalScrollBarEnabled = false
            val cursorLine = currentCursorLine
            val layout = mMentionsEditText!!.layout
            if (layout != null) {
                val lineTop = layout.getLineTop(cursorLine)
                mMentionsEditText!!.scrollTo(0, lineTop)
            }
            // Notify action listener that list was shown
            if (mActionListener != null) {
                mActionListener!!.onSuggestionsDisplayed()
            }
        } else {
            disableSpellingSuggestions(false)
            mTextCounterView!!.visibility = if (mDisplayTextCount) View.VISIBLE else View.GONE
            mSuggestionsList!!.visibility = View.GONE
            myList!!.visibility = View.GONE
            mMentionsEditText!!.setPadding(
                mMentionsEditText!!.paddingLeft,
                mMentionsEditText!!.paddingTop,
                mMentionsEditText!!.paddingRight,
                mPrevEditTextBottomPadding
            )
            if (mPrevEditTextParams == null) {
                mPrevEditTextParams = LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT
                )
            }
            mMentionsEditText!!.layoutParams = mPrevEditTextParams
            mMentionsEditText!!.isVerticalScrollBarEnabled = true
            // Notify action listener that list was hidden
            if (mActionListener != null) {
                mActionListener!!.onSuggestionsHidden()
            }
        }
        requestLayout()
        invalidate()
    }

    /**
     * {@inheritDoc}
     */
    override val isDisplayingSuggestions: Boolean
        get() =
            mSuggestionsList!!.visibility == View.VISIBLE &&
                    myList!!.visibility == View.VISIBLE


    /**
     * Disables spelling suggestions from the user's keyboard.
     * This is necessary because some keyboards will replace the input text with
     * spelling suggestions automatically, which changes the suggestion results.
     * This results in a confusing user experience.
     *
     * @param disable `true` if spelling suggestions should be disabled, otherwise `false`
     */
    private fun disableSpellingSuggestions(disable: Boolean) {
        // toggling suggestions often resets the cursor location, but we don't want that to happen
        val start = mMentionsEditText!!.selectionStart
        val end = mMentionsEditText!!.selectionEnd
        // -1 means there is no selection or cursor.
        if (start == -1 || end == -1) {
            return
        }
        if (disable) {
            // store the previous input type
            mOriginalInputType = mMentionsEditText!!.inputType
        }
        mMentionsEditText!!.setRawInputType(if (disable) InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS else mOriginalInputType)
        mMentionsEditText!!.setSelection(start, end)
    }
    // --------------------------------------------------
    // Private Methods
    // --------------------------------------------------
    /**
     * Updates the TextView counting the number of characters in the editor. Sets not only the content
     * of the TextView, but also the color of the text depending if the limit has been reached.
     */
    private fun updateEditorTextCount() {
        if (mMentionsEditText != null && mTextCounterView != null) {
            val textCount = mMentionsEditText!!.mentionsText.length
            mTextCounterView!!.text = textCount.toString()
            if (mTextCountLimit > 0 && textCount > mTextCountLimit) {
                mTextCounterView!!.setTextColor(mBeyondCountLimitTextColor)
            } else {
                mTextCounterView!!.setTextColor(mWithinCountLimitTextColor)
            }
        }
    }
    // --------------------------------------------------
    // Pass-Through Methods to the MentionsEditText
    // --------------------------------------------------
    /**
     * Convenience method for [MentionsEditText.getCurrentTokenString].
     *
     * @return a string representing currently being considered for a possible query, as the user typed it
     */
    val currentTokenString: String
        get() = if (mMentionsEditText == null) {
            ""
        } else mMentionsEditText.currentTokenString

    /**
     * Convenience method for [MentionsEditText.getCurrentKeywordsString].
     *
     * @return a String representing current keywords in the underlying [EditText]
     */
    val currentKeywordsString: String
        get() = if (mMentionsEditText == null) {
            ""
        } else mMentionsEditText!!.currentKeywordsString

    /**
     * Resets the given [MentionSpan] in the editor, forcing it to redraw with its latest drawable state.
     *
     * @param span the [MentionSpan] to update
     */
    fun updateSpan(span: MentionSpan) {
        if (mMentionsEditText != null) {
            mMentionsEditText!!.updateSpan(span)
        }
    }

    /**
     * Deselects any spans in the editor that are currently selected.
     */
    fun deselectAllSpans() {
        if (mMentionsEditText != null) {
            mMentionsEditText!!.deselectAllSpans()
        }
    }

    /**
     * Adds an [TextWatcher] to the internal [MentionsEditText].
     *
     * @param hostTextWatcher the {TextWatcher} to add
     */
    fun addTextChangedListener(hostTextWatcher: TextWatcher) {
        if (mMentionsEditText != null) {
            mMentionsEditText!!.addTextChangedListener(hostTextWatcher)
        }
    }

    /**
     * @return the [MentionsEditable] within the embedded [MentionsEditText]
     */
    /**
     * Sets the text being displayed within the [RichEditorView]. Note that this removes the
     * [TextWatcher] temporarily to avoid changing the text while listening to text changes
     * (which could result in an infinite loop).
     *
     * @param text the text to display
     */
    var text: MentionsEditable
        get() = if (mMentionsEditText != null) mMentionsEditText!!.text as MentionsEditable else MentionsEditable(
            ""
        )
        set(text) {
            if (mMentionsEditText != null) {
                mMentionsEditText!!.text = text
            }
        }

    /**
     * @return the [Tokenizer] in use
     */
    val tokenizer: Tokenizer?
        get() = if (mMentionsEditText != null) mMentionsEditText!!.tokenizer else null

    /**
     * Sets the text hint to use within the embedded [MentionsEditText].
     *
     * @param hint the text hint to use
     */
    fun setHint(hint: CharSequence) {
        if (mMentionsEditText != null) {
            mMentionsEditText!!.hint = hint
        }
    }

    /**
     * Sets the input type of the embedded [MentionsEditText].
     *
     * @param type the input type of the [MentionsEditText]
     */
    fun setInputType(type: Int) {
        if (mMentionsEditText != null) {
            mMentionsEditText!!.inputType = type
        }
    }

    /**
     * Sets the selection within the embedded [MentionsEditText].
     *
     * @param index the index of the selection within the embedded [MentionsEditText]
     */
    fun setSelection(index: Int) {
        if (mMentionsEditText != null) {
            mMentionsEditText!!.setSelection(index)
        }
    }

    /**
     * Sets the [Tokenizer] for the [MentionsEditText] to use.
     *
     * @param tokenizer the [Tokenizer] to use
     */
    fun setTokenizer(tokenizer: Tokenizer) {
        if (mMentionsEditText != null) {
            mMentionsEditText!!.tokenizer = tokenizer
        }
    }

    /**
     * Sets the factory used to create MentionSpans within this class.
     *
     * @param factory the [MentionsEditText.MentionSpanFactory] to use
     */
    fun setMentionSpanFactory(factory: MentionSpanFactory) {
        if (mMentionsEditText != null) {
            mMentionsEditText!!.setMentionSpanFactory(factory)
        }
    }

    /**
     * Register a [com.example.mentionview.ui.MentionsEditText.MentionWatcher] in order to receive callbacks
     * when mentions are changed.
     *
     * @param watcher the [com.example.mentionview.ui.MentionsEditText.MentionWatcher] to add
     */
    fun addMentionWatcher(watcher: MentionWatcher) {
        if (mMentionsEditText != null) {
            mMentionsEditText!!.addMentionWatcher(watcher)
        }
    }

    /**
     * Remove a [com.example.mentionview.ui.MentionsEditText.MentionWatcher] from receiving anymore callbacks
     * when mentions are changed.
     *
     * @param watcher the [com.example.mentionview.ui.MentionsEditText.MentionWatcher] to remove
     */
    fun removeMentionWatcher(watcher: MentionWatcher) {
        if (mMentionsEditText != null) {
            mMentionsEditText!!.removeMentionWatcher(watcher)
        }
    }
    // --------------------------------------------------
    // RichEditorView-specific Setters
    // --------------------------------------------------
    /**
     * Sets the limit on the maximum number of characters allowed to be entered into the
     * [MentionsEditText] before the text counter changes color.
     *
     * @param limit the maximum number of characters allowed before the text counter changes color
     */
    fun setTextCountLimit(limit: Int) {
        mTextCountLimit = limit
    }

    /**
     * Sets the color of the text within the text counter while the user has entered fewer than the
     * limit of characters.
     *
     * @param color the color of the text within the text counter below the limit
     */
    fun setWithinCountLimitTextColor(color: Int) {
        mWithinCountLimitTextColor = color
    }

    /**
     * Sets the color of the text within the text counter while the user has entered more text than
     * the current limit.
     *
     * @param color the color of the text within the text counter beyond the limit
     */
    fun setBeyondCountLimitTextColor(color: Int) {
        mBeyondCountLimitTextColor = color
    }

    /**
     * Sets the receiver of any tokens generated by the embedded [MentionsEditText]. The
     * receive should act on the queries as they are received and call
     * [.onReceiveSuggestionsResult] once the suggestions are ready.
     *
     * @param client the object that can receive [QueryToken] objects and generate suggestions from them
     */
    fun setQueryTokenReceiver(client: QueryTokenReceiver?) {
        mHostQueryTokenReceiver = client
    }

    /**
     * Sets a listener for anyone interested in specific actions of the [RichEditorView].
     *
     * @param listener the object that wants to listen to specific actions of the [RichEditorView]
     */
    fun setOnRichEditorActionListener(listener: OnSuggestionsVisibilityChangeListener?) {
        mActionListener = listener
    }

    /**
     * Sets the [SuggestionsVisibilityManager] to use (determines which and how the suggestions are displayed).
     *
     * @param suggestionsVisibilityManager the [SuggestionsVisibilityManager] to use
     */
    fun setSuggestionsManager(suggestionsVisibilityManager: SuggestionsVisibilityManager) {
        if (mMentionsEditText != null && mSuggestionsAdapter != null) {
            mMentionsEditText!!.setSuggestionsVisibilityManager(suggestionsVisibilityManager)
            mSuggestionsAdapter!!.setSuggestionsManager(suggestionsVisibilityManager)
        }
    }

    /**
     * Sets the [SuggestionsListBuilder] to use.
     *
     * @param suggestionsListBuilder the [SuggestionsListBuilder] to use
     */
    fun setSuggestionsListBuilder(suggestionsListBuilder: SuggestionsListBuilder) {
        if (mSuggestionsAdapter != null) {
            mSuggestionsAdapter!!.setSuggestionsListBuilder(suggestionsListBuilder)
        }
    }
}