package com.example.emergify

import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.example.emergify.ui.theme.EmergifyTheme
import java.util.Calendar

class MainActivity : ComponentActivity() {

    private lateinit var genreInput: EditText
    private lateinit var searchButton: Button
    private lateinit var resultsLabel: TextView
    private lateinit var resultsRecyclerView: RecyclerView

    private val CLIENT_ID = "8e3327c1ba844e66bc95192780973a1b"
    private val CLIENT_SECRET = "355e5c2ecf284097b8543d585b8b583c"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        enableEdgeToEdge()

        // starting interface's components
        genreInput = findViewById(R.id.genre_input)
        searchButton = findViewById(R.id.search_button)
        resultsLabel = findViewById(R.id.results_label)
        resultsRecyclerView = findViewById(R.id.results_recycler_view)

        resultsRecyclerView.layoutManager = GridLayoutManager(this, 2)
        resultsRecyclerView.adapter = ArtistAdapter(emptyList())



        // setting up the search button
        searchButton.setOnClickListener{
            val genre = genreInput.text.toString()
            getSpotifyAccessToken { accessToken ->
                if (accessToken != null) {
                    fetchArtistsByGenre(genre, accessToken)
                } else {
                    resultsLabel.text = "Failed to get access token"
                }
            }
        }
    }

    private fun searchArtists(genre: String) {
        resultsLabel.visibility = TextView.VISIBLE
        resultsLabel.text = getString(R.string.searching_for_artists, genre)
        resultsRecyclerView.visibility = RecyclerView.VISIBLE
    }

    private fun getSpotifyAccessToken(callback: (String?) -> Unit) {
        val url = "https://accounts.spotify.com/api/token"
        val queue = Volley.newRequestQueue(this)

        val authString = "$CLIENT_ID:$CLIENT_SECRET"
        val encodedAuthString = Base64.encodeToString(authString.toByteArray(), Base64.NO_WRAP)

        val requestBody = "grant_type=client_credentials"
        val request = object : JsonObjectRequest(
            Request.Method.POST, url, null,
            { response ->
                val accessToken = response.getString("access_token")
                callback(accessToken)
            },
            { error ->
                callback(null)
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Authorization"] = "Basic $encodedAuthString"
                headers["Content-Type"] = "application/x-www-form-urlencoded"
                return headers
            }

            override fun getBody(): ByteArray {
                return requestBody.toByteArray()
            }
        }

        queue.add(request)
    }

    private fun fetchArtistsByGenre(genre: String, accessToken: String) {
        val artistList = mutableListOf<String>()

        fun fetchPage(offset: Int) {
            val url = "https://api.spotify.com/v1/search?q=genre:$genre&type=artist&limit=50&offset=$offset"
            val queue = Volley.newRequestQueue(this)

            val request = object : JsonObjectRequest(
                Request.Method.GET, url, null,
                { response ->
                    val artists = response.getJSONObject("artists").getJSONArray("items")
                    Log.d("SpotifyAPI", "Received ${artists.length()} artists for offset $offset")

                    for (i in 0 until artists.length()) {
                        val artist = artists.getJSONObject(i)
                        val artistName = artist.getString("name")
                        val artistPopularity = artist.getInt("popularity")

                        // Filtra artistas por popularidade e adiciona à lista
                        if (artistPopularity >= 40 && artistPopularity < 75) {
                            artistList.add(artistName)
                        }
                    }

                    // Quando chegarmos no limite desejado, atualizamos a UI
                    if (offset >= 100 || artistList.size >= 150) {
                        updateUIWithArtists(artistList)
                    } else {
                        // Recarrega a próxima página se o limite ainda não foi atingido
                        fetchPage(offset + 50)
                    }
                },
                { error ->
                    error.printStackTrace()
                    resultsLabel.text = "Error fetching artists: ${error.message}"
                }
            ) {
                override fun getHeaders(): MutableMap<String, String> {
                    val headers = HashMap<String, String>()
                    headers["Authorization"] = "Bearer $accessToken"
                    return headers
                }
            }

            queue.add(request)
        }

        fetchPage(0)
    }

    private fun updateUIWithArtists(artists: List<String>) {
        resultsLabel.visibility = TextView.VISIBLE
        resultsLabel.text = "Results:"
        resultsRecyclerView.visibility = RecyclerView.VISIBLE
        resultsRecyclerView.adapter = ArtistAdapter(artists)
    }

    inner class ArtistAdapter(private val artists: List<String>) :
        RecyclerView.Adapter<ArtistAdapter.ArtistViewHolder>() {

        inner class ArtistViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val artistName: TextView = view.findViewById(R.id.artist_name)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArtistViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.artist_item, parent, false)
            return ArtistViewHolder(view)
        }

        override fun onBindViewHolder(holder: ArtistViewHolder, position: Int) {
            holder.artistName.text = artists[position]
        }

        override fun getItemCount(): Int = artists.size
    }

}
