package de.deftone.musicplayer.activity;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import java.io.File;
import java.io.FileFilter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import de.deftone.musicplayer.R;
import de.deftone.musicplayer.adapter.SongAdapter;
import de.deftone.musicplayer.model.Song;

public class MainActivity extends AppCompatActivity {
    //todo: nach permission fragen!! so wie bei tanss app
    //todo: equalizer in settings hinzufuegen
    //todo: in der liste suchen, nicth neue activity zum suchen (mit recycler view einfacher??)

    public static final String INTENT_SONGLIST = "songlist";
    public static final String INTENT_SONG_ID = "song_id";
    public static final String NO_ALBUM_COVER = "no cover available";

    private List<Song> songList = new ArrayList<>();
    private AppCompatActivity mainActivity = this;
    private RecyclerView recyclerViewSongs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //defaultmaessig ist der Music folder der startfolder
        File musicMainFolder = Environment.getExternalStoragePublicDirectory("Music");
        if (getIntent().getSerializableExtra("song") == null) {
            getMusicContent(musicMainFolder);
        } else {
            File pickedFromSearch = (File) getIntent().getSerializableExtra("song");
            getMusicContent(pickedFromSearch);
        }

        //recyclerview stuff
        recyclerViewSongs = findViewById(R.id.recycler_view_song_list);

        // use this setting to improve performance if you know that changes
        // in content do not change the layout size of the RecyclerView
//        recyclerViewSongs.setHasFixedSize(true);

        // use a linear layout manager
        GridLayoutManager layoutManager = new GridLayoutManager(this, 1);
        recyclerViewSongs.setLayoutManager(layoutManager);

        setSongsOnRecyclerView();
    }

    private void setSongsOnRecyclerView() {
        // specify an adapter
        SongAdapter adapter = new SongAdapter(songList);
        recyclerViewSongs.setAdapter(adapter);
        adapter.setListener(new SongAdapter.Listener() {
            @Override
            public void onClick(int position) {
                if (isSong(position)) {
                    //open play activity
                    Intent playIntent = new Intent(mainActivity, PlayActivity.class);
                    playIntent.putExtra(INTENT_SONGLIST, (Serializable) songList);
                    playIntent.putExtra(INTENT_SONG_ID, position);
                    startActivity(playIntent);
                } else {
                    // sonst ist es ein Directory und wir müssen tiefer (oder hoeher) gehen
                    getMusicContent(songList.get(position).getFile());
                    // und die ganze recyclerview neu bauen
                    setSongsOnRecyclerView();
                }

            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                //todo: equalizer
                return true;
            case R.id.action_search:
                //bei klick auf seach symbol soll sich neue activity oeffnen onClickSearchButton oder so, dort auch searchView implenentieren
                Intent searchIntent = new Intent(this, SearchActivity.class);
                searchIntent.putExtra(INTENT_SONGLIST, (Serializable) songList);
                startActivity(searchIntent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onBackPressed() {
        getMusicContent(songList.get(0).getFile());
        // und die ganze recyclerview neu bauen
        setSongsOnRecyclerView();
    }

    //zuerst die Musik holen (d.h. die Liste mit Ordnern oder Liedern fuellen)
    //initialisieren in onCreate und aktualisieren onClick bzw. onBackPressed
    public void getMusicContent(File currentFolder) {

        //um zum uebergeordneten ordner zu wechseln - aber nur bis zum "obersten" (/storage/emulated)
        if (currentFolder.getAbsolutePath().equals("/storage/emulated"))
            return;

        //wenn nicht der oberste ordner, dann liste neu befuellen
        songList.clear();
        if (!currentFolder.getAbsolutePath().equals("/storage/emulated/0")) {
            songList.add(new Song(-1, "", "..", currentFolder.getParentFile(), "", NO_ALBUM_COVER, 0));
        }

        //alle ordner pfade aus aktuellem ordner holen
        File[] foldersInCurrentFolder = currentFolder.listFiles(new FileFilter() {
            @Override
            public boolean accept(File path) {
                return path.isDirectory();
            }
        });

        //diese ordner durchlaufen (dazu wird Song "missbraucht", id=-1 heisst es ist ein ordner)
        //und der songList hinzufuegen, damit alle ordner angezeigt werden
        for (File musicFolder : foldersInCurrentFolder) {
            String folderName = musicFolder.getName().toLowerCase().replace("_", " ");
            songList.add(new Song(-1L, folderName, "", musicFolder, folderName, NO_ALBUM_COVER, 0));
        }

        //jetzt alle Songs (bzw. Ordner) holen
        getSongList(currentFolder.getAbsolutePath());

        Collections.sort(songList, new Comparator<Song>() {
            //return a negative integer, zero, or a positive integer as the
            //first argument is less than, equal to, or greater than the second.
            @Override
            public int compare(Song s1, Song s2) {
                if (s1.getID() == -1 && s2.getID() != -1) {
                    // Wenn s1 ein Ordner ist, dann ist s1 < s2
                    return -1;
                } else if (s1.getID() != -1 && s2.getID() == -1) {
                    // Hier das ganze Umgekehrt
                    return 1;
                } else {
                    // Alles andere nach dem String den du willst sortieren
                    return s1.getFileName().compareTo(s2.getFileName());
                }
            }
        });
    }

    private String getAlbumCover(long albumId) {
        Cursor albumCurser = getContentResolver().query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM_ART},
                MediaStore.Audio.Albums._ID + "=?",
                new String[]{String.valueOf(albumId)},
                null);

        if (albumCurser.moveToFirst()) {
            String path = albumCurser.getString(albumCurser.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART));
            if (path != null)
                return path;
            else
                return NO_ALBUM_COVER;
        } else
            return NO_ALBUM_COVER;
    }

    //alle Songs holen (bzw. alle Ordner)
    public void getSongList(String folderPath) {
        ContentResolver musicResolver = getContentResolver();
        Uri musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Cursor musicCursor = musicResolver.query(musicUri,
                new String[]{MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.DATA,
                        MediaStore.Audio.Media.DISPLAY_NAME,
                        MediaStore.Audio.Media.ALBUM_ID,
                        MediaStore.Audio.Media.DURATION,
                },
                MediaStore.Audio.Media.DATA + " LIKE ? AND " + MediaStore.Audio.Media.DATA + " NOT LIKE ? ",
                new String[]{"%" + folderPath + "/%", "%" + folderPath + "/%/%"},
                null);


        if (musicCursor != null && musicCursor.moveToFirst()) {
            //get columns
            int titleColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
            int idColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media._ID);
            int artistColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
            int displayColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME);
            int albumIdColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
            int songLengthColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.DURATION);

            String albumCover = "";
            //add songs to list
            do {
                long thisId = musicCursor.getLong(idColumn);
                String thisTitle = musicCursor.getString(titleColumn);
                String thisArtist = musicCursor.getString(artistColumn);
                String displayName = musicCursor.getString(displayColumn);
                int songLength = musicCursor.getInt(songLengthColumn);
                //jeder song im ordner hat die selbe album id und damit auch das selbe cover, d.h. nur einmal abfragen
                if (albumCover.equals("")) {
                    albumCover = getAlbumCover(musicCursor.getLong(albumIdColumn));
                }
                songList.add(new Song(thisId, thisTitle, thisArtist, null, displayName, albumCover, songLength));
            } while (musicCursor.moveToNext());
            musicCursor.close();
        }
    }

    public boolean isSong(int position) {
        //Ordner haben die ID -1 und sind kein Song
        if (songList.get(position).getID() != -1) {
            return true;
        }
        return false;
    }
}
