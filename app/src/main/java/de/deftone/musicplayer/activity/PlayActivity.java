package de.deftone.musicplayer.activity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import de.deftone.musicplayer.R;
import de.deftone.musicplayer.model.Song;
import de.deftone.musicplayer.service.MusicService;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

// einen listener auf den player machen, der
// todo: play und pause zwar einheitlich in gui oder notification, aber nicht daziwschen synchronisiert :(
//todo: 04.07.17 next und prev im screenlock reparieren
//todo: suche schliessen
//todo: ganz am ende: playactivity neue activity machen wenn ein lied angeklickt wird, dort dann seekbar, albumcover etc
//todo: albumcover hinzufuegen

/**
 * Created by deftone on 02.04.18.
 */

class PlayActivity extends AppCompatActivity {

    private List<Song> songList;
    private ListView songView;
    private TextView songTextView;
    private TextView positionTextView;
    private ImageView albumCover;
    private SeekBar seekBar;
    private Handler seekHandler = new Handler();
    private MusicService musicService;
    private Intent playIntent;
    private boolean musicBound = false;
    private boolean paused = false;
    private boolean playbackPaused = false;
    private boolean playbackStopped = false;

    private File musicMainFolder;

    private ImageButton playPauseButton;
    private ImageButton repeatSongBtton;
    private ImageButton shuffleButton;

    //connect to the service
    private ServiceConnection musicConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            //get service
            musicService = binder.getService();
            //pass list
            musicService.setList(songList);
            //
            musicService.setSongTextView(songTextView);
            musicService.setPositionTextView(positionTextView);
            musicBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play);
        ButterKnife.bind(this);
        songView = (ListView) findViewById(R.id.song_list);
        songTextView = (TextView) findViewById(R.id.song_title);
        positionTextView = (TextView) findViewById(R.id.song_position);
        seekBar = (SeekBar) findViewById(R.id.seek_bar);
        albumCover = (ImageView) findViewById(R.id.cover);
        playPauseButton = (ImageButton) findViewById(R.id.playPauseButton);
        repeatSongBtton = (ImageButton) findViewById(R.id.action_repeatSong);
        shuffleButton = (ImageButton) findViewById(R.id.action_shuffle);
        songList = new ArrayList<>();

        //hier der zusaetzliche thread, der seekbar nutzt, kann erst hier aufgerufen werden
        //zyklische hintergrundprozesse sollten immer im on create starten (laeuft im hintergrund weiter)
        // man koennte das Runnable auch als innere klasse MyRunnable implementieren, sowie mit OnSeekBarChangeListener, oder hier lassen
        PlayActivity.this.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                if (musicBound && musicService.isPng() || playbackStopped) {
                    //wenn playback paused ist, dann soll seekbar nicht aktualisiert werden
                    seekBar.setMax(getDuration());
                    seekBar.setProgress(getCurrentPosition());
                    positionTextView.setText(musicService.getSongPosnAnzeige());
                }
                seekHandler.postDelayed(this, 1000);

            }
        });
        //Objekt der inneren Klasse erzeugen und nutzen
        seekBar.setOnSeekBarChangeListener(new MySeekbarChangeListener());

    }

    //innere Klasse fuer den seekbarChangeListener, alternativ auch richtige eigene klasse machen (zu umstaendlich)
    //der Listener fuehrt onProgressChanged automatisch auf, wenn event gefeuert wird
    private class MySeekbarChangeListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        //diese methode wird immer dann ausgefuehrt, wenn finger auf seekbar spielt
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            //nur playing evtl mal rausnehmen
            if (musicService.isPng() && fromUser) {
                seekTo(progress);
            }
        }
    }

    //zusaetzlich die Menueleiste oben einbinden
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_play, menu);
        return true;
    }

    //und in dieser diese zusaetzlichen Buttons
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_search:
                //bei klick auf seach symbol soll sich neue activity oeffnen onClickSearchButton oder so, dort auch searchView implenentieren
                Intent searchIntent = new Intent(this, SearchActivity.class);
                searchIntent.putExtra("list", (Serializable) songList);
                startActivity(searchIntent);
                break;
            case R.id.action_end:
                stopService(playIntent);
                musicService = null;
                System.exit(0);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    // Diese Methoden sind für das Starten, Stoppen, Zerstören und Wiederherstellen der ACTIVITY!
    //onCreate setzt Activity auf, dann andere... und dann onStart, ruft die erzeugte Activity auf
    //hier sind alle variablen aus onCreate initialisiert
    @Override
    protected void onStart() {
        super.onStart();
        //Intent an MusicService "hallo ich bin da, gib mir deinen service", wenn er noch nicht da ist, verbinde mich damit
        if (playIntent == null) {
            playIntent = new Intent(this, MusicService.class);
            bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
            startService(playIntent);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        //aufraeumen
        stopService(playIntent);
        musicService = null;
        super.onDestroy();
    }

    //paused von der acitvity, nicht vom player!!
    @Override
    protected void onResume() {
        super.onResume();
        if (paused) {
            paused = false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        paused = true;
    }


    public void onPlayNextButton(View view) {
        String songtitle = musicService.playNext(true);
        if (playbackPaused) {
            playbackPaused = false;
        }
        //jetzt wird der Play-Button in eine Pause-Button umgewandelt
        playPauseButton.setImageResource(R.drawable.ic_pause_black_24dp);
        playPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPauseButton(v);
            }
        });
    }

    public void onPlayPrevButton(View view) {
        String songtitle = musicService.playPrev(true);
        if (playbackPaused) {
            playbackPaused = false;
        }
        //jetzt wird der Play-Button in eine Pause-Button umgewandelt
        playPauseButton.setImageResource(R.drawable.ic_pause_black_24dp);
        playPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPauseButton(v);
            }
        });
    }

    public void onStartButton(View view) {
        playbackStopped = false;
        String songtitle = musicService.go();
        //jetzt wird der Play-Button in ein Pause-Button umgewandelt: icon und listener
        playPauseButton.setImageResource(R.drawable.ic_pause_black_24dp);
        playPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPauseButton(v);
            }
        });
    }

    @OnClick(R.id.playPauseButton)
    void onPlayPauseButton() {
        playbackPaused = true;
        musicService.pausePlayer();
        //jetzt wird der Pause-Button in eine Play-Button umgewandelt
        playPauseButton.setImageResource(R.drawable.ic_play_circle_outline_black_24dp);
        playPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onStartButton(v);
            }
        });
    }

    public void onPauseButton(View view) {
        playbackPaused = true;
        musicService.pausePlayer();
        //jetzt wird der Pause-Button in eine Play-Button umgewandelt
        playPauseButton.setImageResource(R.drawable.ic_play_circle_outline_black_24dp);
        playPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onStartButton(v);
            }
        });
    }

    public void onStopButton(View view) {
        playbackStopped = true;
        musicService.pausePlayer();
        musicService.seek(0);
        //auch hier muss der Pause Button in ein Start Button umgewandelt werden
        playPauseButton.setImageResource(R.drawable.ic_play_circle_outline_black_24dp);
        playPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onStartButton(v);
            }
        });
    }

    public void onShuffleButton(View view) {
        if (musicService.setShuffle())
            shuffleButton.setImageResource(R.drawable.random_invers);
        else
            shuffleButton.setImageResource(R.drawable.random);
    }

    public void onRepeatSongButton(View view) {
        if (musicService.setRepeatSong())
            repeatSongBtton.setImageResource(R.drawable.repeat_invers);
        else
            repeatSongBtton.setImageResource(R.drawable.repeat);
    }

    //laenge des Lieds, aus musicService (fuer seekbar)
    public int getDuration() {
        if (musicService != null && musicBound && musicService.isPng()) {
            return musicService.getDur();
        } else {
            return 0;
        }
    }

    //aktuelle Stelle des Songs? aus musicService (fuer seekbar)
    public int getCurrentPosition() {
        // hier war auch noch musicService != null && && musicService.isPng()
        if (musicBound && musicService.isPng()) {
            return musicService.getPosn();
        } else {
            return 0;
        }
    }

    //fuer seekbar scrollen
    public void seekTo(int progress) {
        musicService.seek(progress);
    }

    //ob Lied laeuft ebenfalls aus musicService
    public boolean isPlaying() {
        if (musicService != null && musicBound)
            return musicService.isPng();
        return false;
    }


}
