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

import android.graphics.Color
import androidx.annotation.ColorInt

/**
 * Class used to configure various options for the [MentionSpan]. Instantiate using the
 * [MentionSpanConfig.Builder] class.
 */
class MentionSpanConfig internal constructor(@field:ColorInt @param:ColorInt val NORMAL_TEXT_COLOR: Int,
                                             @field:ColorInt @param:ColorInt val NORMAL_TEXT_BACKGROUND_COLOR: Int,
                                             @field:ColorInt @param:ColorInt val SELECTED_TEXT_COLOR: Int,
                                             @field:ColorInt @param:ColorInt val SELECTED_TEXT_BACKGROUND_COLOR: Int) {

    class Builder {
        // Default colors
        @ColorInt
        var normalTextColor = Color.parseColor("#00a0dc")

        @ColorInt
        var normalTextBackgroundColor = Color.TRANSPARENT

        @ColorInt
        var selectedTextColor = Color.WHITE

        @ColorInt
        private var selectedTextBackgroundColor = Color.parseColor("#0077b5")
        fun setMentionTextColor(@ColorInt normalTextColor: Int): Builder {
            if (normalTextColor != -1) {
                this.normalTextColor = normalTextColor
            }
            return this
        }

        fun setMentionTextBackgroundColor(@ColorInt normalTextBackgroundColor: Int): Builder {
            if (normalTextBackgroundColor != -1) {
                this.normalTextBackgroundColor = normalTextBackgroundColor
            }
            return this
        }

        fun setSelectedMentionTextColor(@ColorInt selectedTextColor: Int): Builder {
            if (selectedTextColor != -1) {
                this.selectedTextColor = selectedTextColor
            }
            return this
        }

        fun setSelectedMentionTextBackgroundColor(@ColorInt selectedTextBackgroundColor: Int): Builder {
            if (selectedTextBackgroundColor != -1) {
                this.selectedTextBackgroundColor = selectedTextBackgroundColor
            }
            return this
        }

        fun build(): MentionSpanConfig {
            return MentionSpanConfig(normalTextColor, normalTextBackgroundColor,
                selectedTextColor, selectedTextBackgroundColor)
        }
    }

}