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
package com.example.mentionview.ui.wrapper

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.mentionview.R
import com.example.mentionview.ui.RichEditorView

/**
 * Convenient fragment wrapper around a [RichEditorView].
 */
class RichEditorFragment : Fragment() {
    var richEditor: RichEditorView? = null
        private set
    private var mOnCreateViewListener: OnCreateViewListener? = null

    interface OnCreateViewListener {
        fun onFragmentCreateView(fragment: RichEditorFragment)
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        // Inflate the layout for this fragment
        val rootView = inflater.inflate(R.layout.editor_fragment, container, false) ?: return null
        richEditor = rootView.findViewById(R.id.rich_editor)
        if (mOnCreateViewListener != null) {
            mOnCreateViewListener!!.onFragmentCreateView(this)
        }
        return rootView
    }

    fun setOnCreateViewListener(listener: OnCreateViewListener?) {
        mOnCreateViewListener = listener
    }

    companion object {
        const val fragmentTag = "fragment_rich_editor"
        fun newInstance(args: Bundle?): RichEditorFragment {
            val fragment = RichEditorFragment()
            fragment.arguments = args
            return fragment
        }

        fun getInstance(fragmentManager: FragmentManager, args: Bundle?): RichEditorFragment {
            val instance: RichEditorFragment
            val fragment = fragmentManager.findFragmentByTag(fragmentTag)
            instance = if (fragment == null) {
                newInstance(args)
            } else {
                fragment as RichEditorFragment
            }
            return instance
        }
    }
}