package com.example.mentionview

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.mentionview.data.City
import com.example.mentionview.suggestions.SuggestionsResult
import com.example.mentionview.tokenization.QueryToken
import com.example.mentionview.tokenization.interfaces.QueryTokenReceiver
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), QueryTokenReceiver {

    private val BUCKET = "cities-memory"

    private var cities: City.CityLoader? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.simple_mentions)

        editor.displayTextCounter(false)
        editor.setQueryTokenReceiver(this)
        editor.setHint(resources.getString(R.string.type_city))
        cities = City.CityLoader(resources)
    }

    override fun onQueryReceived(queryToken: QueryToken): List<String> {
        val buckets =
            listOf<String>("bucket")
        val suggestions: List<City> = cities?.getSuggestions(queryToken)!!
        val result = SuggestionsResult(queryToken, suggestions)
        editor.onReceiveSuggestionsResult(
            result,
            "bucket"
        )
        return buckets
    }
}
