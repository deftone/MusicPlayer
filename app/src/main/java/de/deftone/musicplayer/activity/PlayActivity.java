package de.deftone.musicplayer.activity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

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
import static de.deftone.musicplayer.activity.MainActivity.NO_ALBUM_COVER;

//todo: play und pause zwar einheitlich in gui oder notification, aber nicht daziwschen synchronisiert :(
//todo: album name anzeigen - im titel?

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

    private String TAG = "test: ";
    @BindView(R.id.play_pause_button)
    ImageButton playPauseButton;
    @BindView(R.id.repeatSong_button)
    ImageButton repeatSongBtton;
    @BindView(R.id.shuffle_button)
    ImageButton shuffleButton;
    @BindView(R.id.song_artist)
    TextView artistTextView;
    @BindView(R.id.song_title)
    TextView songTextView;
    @BindView(R.id.song_position)
    TextView positionTextView;
    @BindView(R.id.song_length)
    TextView songLengthTextView;
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

        //init with songtigle and "play" symbol and also cover
        songTextView.setText(songList.get(songId).getTitle());
        artistTextView.setText(songList.get(songId).getArtist());
        songLengthTextView.setText(MusicService.convertMilliSecondsToMMSS(songList.get(songId).getSongLength()));
        playPauseButton.setImageResource(R.drawable.ic_play_white_65pd);
        showAlbumCover(songList);
        //diese zeilen code auch in MusicService, am besten hier eine funktion - ah, da muss man wieder saemtliches uebergeben, wenn statisch... erstmal so lassen
        Bitmap albumCoverBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.default_cover);
        if (!songList.get(songId).getAlbumCover().equals(NO_ALBUM_COVER))
            albumCoverBitmap = BitmapFactory.decodeFile(songList.get(songId).getAlbumCover());
        albumCover.setImageBitmap(albumCoverBitmap);

        musicConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
                //get service
                musicService = binder.getService();
                //pass list
                musicService.setList(songList);
                musicService.setSongPosnInList(songId);
                musicService.setSongTextView(songTextView);
                musicService.setArtistTextView(artistTextView);
                musicService.setPositionTextView(positionTextView);
                musicService.setSongLengthTextView(songLengthTextView);
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
                if (musicBound && musicService.isPlaying()) {
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

    private void showAlbumCover(List<Song> songList) {
    }

    @Override
    protected void onStart() {
        super.onStart();
        //Intent an MusicService "hallo ich bin da, gib mir deinen service", wenn er noch nicht da ist, verbinde mich damit
        if (musicServiceIntent == null) {
            musicServiceIntent = new Intent(this, MusicService.class);
            bindService(musicServiceIntent, musicConnection, Context.BIND_AUTO_CREATE);
            startService(musicServiceIntent);
        }
        //todo: hier ist der ansatzpunkt den play/pause button zu aktualisieren!
        //aber wieder die alten probleme mit play pause, dass uri fehlt und so...
//        if (isPlaying())
//            playPauseButton.setImageResource(R.drawable.ic_play_white_65pd);
//        else
//            playPauseButton.setImageResource(R.drawable.ic_pause_white_65pd);
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
        super.onDestroy();
    }


    /**
     * buttons
     **/

    @OnClick(R.id.play_next_button)
    void onPlayNextButton() {
        String songtitle = musicService.playNext(true);
        songTextView.setText(songtitle);
        //no matter what is was before, player ist im zustand playing
        playPauseButton.setImageResource(R.drawable.ic_pause_white_65pd);
    }

    @OnClick(R.id.play_previous_button)
    void onPlayPrevButton() {
        String songtitle = musicService.playPrev(true);
        songTextView.setText(songtitle);
        //no matter what is was before, player ist im zustand playing
        playPauseButton.setImageResource(R.drawable.ic_pause_white_65pd);
    }

    @OnClick(R.id.play_pause_button)
    void onPlayPauseButton() {
        if (isPlaying()) {
            //playback hat gespielt, d.h. jetzt pausieren
            playPauseButton.setImageResource(R.drawable.ic_play_white_65pd);
            musicService.pausePlayer();
        } else {
            //playback war pausiert, d.h. jetzt wieder abspielen
            playPauseButton.setImageResource(R.drawable.ic_pause_white_65pd);
            musicService.playSong(songId, true);
        }
    }

    @OnClick(R.id.shuffle_button)
    void onShuffleButton() {
        if (musicService.setShuffle()) {
            shuffleButton.setImageResource(R.drawable.random_invers);
            showCustomToast("Shuffle on");
        } else {
            shuffleButton.setImageResource(R.drawable.random);
            showCustomToast("Shuffle off");
        }
    }

    @OnClick(R.id.repeatSong_button)
    void onRepeatSongButton() {
        if (musicService.setRepeatSong()) {
            repeatSongBtton.setImageResource(R.drawable.repeat_invers);
            showCustomToast("Repeat one song on");
        } else {
            repeatSongBtton.setImageResource(R.drawable.repeat);
            showCustomToast("Reapeat one song off");
        }
    }

    private void showCustomToast(String toastText) {
        Context context = getApplicationContext();
        LayoutInflater inflater = getLayoutInflater();
        // Call toast.xml file for toast layout
        View toastview = inflater.inflate(R.layout.toast, null);
        //set text on toast dynamically
        TextView toastTextView = toastview.findViewById(R.id.custom_toast);
        toastTextView.setText(toastText);
        //create new toast
        Toast toast = new Toast(context);
        // Set layout to toast
        toast.setView(toastview);
        toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL,
                0, 0);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.show();
    }

    //laenge des Lieds, aus musicService (fuer seekbar)
    public int getDuration() {
        if (musicService != null && musicBound && musicService.isPlaying()) {
            return musicService.getDur();
        } else {
            return 0;
        }
    }

    //aktuelle Stelle des Songs? aus musicService (fuer seekbar)
    public int getCurrentPosition() {
        // hier war auch noch musicService != null && && musicService.isPlaying()
        if (musicBound && musicService.isPlaying()) {
            return musicService.getCurrentPositionInSong();
        } else {
            return 0;
        }
    }


    public boolean isPlaying() {
        if (musicService != null && musicBound)
            return musicService.isPlaying();
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
            if (musicService.isPlaying() && fromUser) {
                musicService.seek(progress);
            }
        }
    }

}
