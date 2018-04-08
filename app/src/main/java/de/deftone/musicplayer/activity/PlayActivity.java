package de.deftone.musicplayer.activity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import de.deftone.musicplayer.R;
import de.deftone.musicplayer.model.Song;
import de.deftone.musicplayer.service.MusicService;

import static de.deftone.musicplayer.activity.MainActivity.INTENT_SONGLIST;
import static de.deftone.musicplayer.activity.MainActivity.INTENT_SONG_ID;

//todo: layout ueberarbeiten

// todo: play und pause zwar einheitlich in gui oder notification, aber nicht daziwschen synchronisiert :(
//todo: 04.07.17 next und prev im screenlock reparieren ???
//todo: suche schliessen ???

//todo: die anzeige ist falsch, nicht titel und band sondern wie vorher mit <unknown>
/**
 * Created by deftone on 02.04.18.
 */

public class PlayActivity extends AppCompatActivity {

    private List<Song> songList;
    private int songId;
    private Handler seekHandler = new Handler();
    private ServiceConnection musicConnection;
    private MusicService musicService;
    private Intent musicServiceIntent;
    private boolean musicBound = false;
    private boolean paused = false;
    private boolean playbackPaused = true;
    private boolean playbackStopped = false;

    private String TAG = "test: ";
    @BindView(R.id.play_pause_button)
    ImageButton playPauseButton;
    @BindView(R.id.repeatSong_button)
    ImageButton repeatSongBtton;
    @BindView(R.id.shuffle_button)
    ImageButton shuffleButton;
    @BindView(R.id.song_title)
    TextView songTextView;
    @BindView(R.id.song_position)
    TextView positionTextView;
    @BindView(R.id.seek_bar)
    SeekBar seekBar;
    @BindView(R.id.album_cover)
    ImageView albumCover;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play);
        ButterKnife.bind(this);

        songList = new ArrayList<>((ArrayList<Song>) getIntent().getSerializableExtra(INTENT_SONGLIST));
        songId = getIntent().getIntExtra(INTENT_SONG_ID, 1);

        //init with songtigle and "play" symbol
        songTextView.setText(songList.get(songId).getTitle());
        playPauseButton.setImageResource(R.drawable.ic_play_circle_outline_black_24dp);

        musicConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
                //get service
                musicService = binder.getService();
                //pass list
                musicService.setList(songList);
                musicService.setSongTextView(songTextView);
                musicService.setPositionTextView(positionTextView);
                musicService.setAlbumCoverImageView(albumCover);
                musicBound = true;
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                musicBound = false;
            }
        };

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
        seekBar.setOnSeekBarChangeListener(new SeekbarChangeListener());
    }

    @Override
    protected void onStart() {
        //todo: hier ist der ansatzpunkt den play/pause button zu aktualisieren!
        super.onStart();
        //Intent an MusicService "hallo ich bin da, gib mir deinen service", wenn er noch nicht da ist, verbinde mich damit
        if (musicServiceIntent == null) {
            musicServiceIntent = new Intent(this, MusicService.class);
            bindService(musicServiceIntent, musicConnection, Context.BIND_AUTO_CREATE);
            startService(musicServiceIntent);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        stopService(musicServiceIntent);
        musicService = null;
        musicBound = false;
        playbackStopped = false;
        super.onDestroy();
    }

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


    /**
     * buttons
     **/

    @OnClick(R.id.play_next_button)
    void onPlayNextButton() {
        String songtitle = musicService.playNext(true);
        songTextView.setText(songtitle);
        //no matter what is was before, player ist im zustand playing
        playbackPaused = false;
        playPauseButton.setImageResource(R.drawable.ic_pause_black_24dp);
    }

    @OnClick(R.id.play_previous_button)
    void onPlayPrevButton() {
        String songtitle = musicService.playPrev(true);
        songTextView.setText(songtitle);
        //no matter what is was before, player ist im zustand playing
        playbackPaused = false;
        playPauseButton.setImageResource(R.drawable.ic_pause_black_24dp);
    }


    @OnClick(R.id.play_pause_button)
    void onPlayPauseButton() {
        if (playbackPaused) {
            //playback war pausiert, d.h. jetzt wieder abspielen
            playbackPaused = false;
            playPauseButton.setImageResource(R.drawable.ic_pause_black_24dp);
            musicService.playSong(songId, true);
        } else {
            //playback hat gespielt, d.h. jetzt pausieren
            playbackPaused = true;
            playPauseButton.setImageResource(R.drawable.ic_play_circle_outline_black_24dp);
            musicService.pausePlayer();
        }
    }


    @OnClick(R.id.stop_button)
    void onStopButton() {
        playbackStopped = true;
        playbackPaused = true;
        musicService.pausePlayer();
        musicService.seek(0);
        //auch hier muss der Pause Button in ein Start Button umgewandelt werden
        playPauseButton.setImageResource(R.drawable.ic_play_circle_outline_black_24dp);
    }

    @OnClick(R.id.shuffle_button)
    void onShuffleButton() {
        if (musicService.setShuffle())
            shuffleButton.setImageResource(R.drawable.random_invers);
        else
            shuffleButton.setImageResource(R.drawable.random);
    }

    @OnClick(R.id.repeatSong_button)
    void onRepeatSongButton() {
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
            return musicService.getCurrentPositionInSong();
        } else {
            return 0;
        }
    }


    //ob Lied laeuft ebenfalls aus musicService
    public boolean isPlaying() {
        if (musicService != null && musicBound)
            return musicService.isPng();
        return false;
    }

    //innere Klasse fuer den seekbarChangeListener, da der musicService sonst null ist
    private class SeekbarChangeListener implements SeekBar.OnSeekBarChangeListener {

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
                musicService.seek(progress);
            }
        }
    }

}
