package de.deftone.musicplayer.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ListView;
import android.widget.SearchView;

import java.util.ArrayList;
import java.util.List;

import de.deftone.musicplayer.R;
import de.deftone.musicplayer.adapter.SongAdapter;
import de.deftone.musicplayer.model.Song;

import static de.deftone.musicplayer.activity.MainActivity.INTENT_SONGLIST;

/**
 * Created by deftone on 02.04.18.
 */


//todo: suche schliessen

    public class SearchActivity extends AppCompatActivity implements SearchView.OnQueryTextListener {

        private SearchView searchView;
        private List<Song> songList;
        private List<Song> songListBackup;
        private ListView songView;

        @Override
        protected void onCreate(Bundle savedInstanceState) {

            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_search);
            searchView = (SearchView) findViewById(R.id.searchView);

            songList = new ArrayList<>((ArrayList<Song>) getIntent().getSerializableExtra(INTENT_SONGLIST));
            // TODO: songs rauswerfen (id != -1)
            songListBackup = new ArrayList<>(songList);
            songView = (ListView) findViewById(R.id.song_list);
            //hier wird die songliste angezeigt, wird automatisch aktualisiert, muss nur hier einmal aufgerufen werden
            songView.setAdapter(new SongAdapter(this, songList));

            //searchVIew
            searchView.setOnQueryTextListener(this);
            //hier die methode 'on close' fuer search wie UIThread inline oder wie bei seekbar als innere klasse implementieren

        }

        //wird immer aufgerufen wenn auf den lupenbutton der tastatur getippt wird
        @Override
        public boolean onQueryTextSubmit(String query) {
            //wenn auf button geklickt wird suchen
            songList.clear();

            //jetzt filtern nach querry, dafuer neue Liste
            List<Song> temp = new ArrayList<>();
            temp.addAll(songListBackup);
            for (Song song : temp) {
                if (song.getTitle().contains(query)) {
                    songList.add(song);
                }
            }

            songView.setAdapter(new SongAdapter(this, songList));
            return true;
        }

        @Override
        public boolean onQueryTextChange(String newText) {
            return false;
        }


        //hier ueber die List verbunden, onClick hoert auf songPicked
        //d.h. wenn ein Song oder Ordner angeklickt wird, wird diese Methode aufgerufen
        public void songPicked(View view) {
            int position = Integer.parseInt(view.getTag().toString());
            Intent mainIntent = new Intent(this, MainActivity.class);
            mainIntent.putExtra("song", songList.get(position).getFile());
            startActivity(mainIntent);
        }
}
