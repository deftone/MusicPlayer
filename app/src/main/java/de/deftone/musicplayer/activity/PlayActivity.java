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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.florescu.android.rangeseekbar.RangeSeekBar;

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
import static de.deftone.musicplayer.service.MusicService.REPEAT_ALL;
import static de.deftone.musicplayer.service.MusicService.REPEAT_NONE;
import static de.deftone.musicplayer.service.MusicService.REPEAT_ONE;

//todo: play und pause zwar einheitlich in gui oder notification - notification weiss ich nicht wie, aber screen lock buttons sind jetzt synchron :)

/**
 * Created by deftone on 02.04.18.
 * https://github.com/anothem/android-range-seek-bar
 * https://mobikul.com/how-to-make-range-seekbar-or-dual-thumbs-seekbar-in-android/
 */

public class PlayActivity extends AppCompatActivity {

    private List<Song> songList;
    private int songId;
    private Handler seekHandler = new Handler();
    private ServiceConnection musicConnection;
    private MusicService musicService;
    private Intent musicServiceIntent;
    private boolean musicBound = false;
    private int fromTime;
    private int toTime;
    private static boolean innerLoopMode = false;
    private int songDuration;

    @BindView(R.id.play_pause_button)
    ImageButton playPauseButton;
    @BindView(R.id.repeatSong_button)
    ImageButton repeatSongButton;
    @BindView(R.id.shuffle_button)
    ImageButton shuffleButton;
    @BindView(R.id.play_previous_button)
    ImageButton prevButton;
    @BindView(R.id.play_next_button)
    ImageButton nexButton;
    @BindView(R.id.song_album)
    TextView albumTextView;
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
    @BindView(R.id.range_seek_bar)
    RangeSeekBar rangeSeekBar;
    @BindView(R.id.album_cover)
    ImageView albumCover;
//    @BindView(R.id.from_edit)
//    EditText fromEdit;
//    @BindView(R.id.to_edit)
//    EditText toEdit;
//    @BindView(R.id.from_text)
//    TextView fromText;
//    @BindView(R.id.to_text)
//    TextView toText;
//    @BindView(R.id.sec_text)
//    TextView secText;
//    @BindView(R.id.sec_text2)
//    TextView secText2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play);
        ButterKnife.bind(this);

        songList = new ArrayList<>((ArrayList<Song>) getIntent().getSerializableExtra(INTENT_SONGLIST));
        songId = getIntent().getIntExtra(INTENT_SONG_ID, 1);

        //init with artist in toolbartitle, songtitle and also cover
        songTextView.setText(songList.get(songId).getTitle());
        artistTextView.setText(songList.get(songId).getArtist());
        albumTextView.setText(songList.get(songId).getAlbum());
        songLengthTextView.setText(MusicService.convertMilliSecondsToMMSS(songList.get(songId).getSongLength()));
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

                //song direkt abspielen
                playPauseButton.setImageResource(R.drawable.icon_pause);
                musicService.playSong(songId, true);
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
                    songDuration = getDuration();

                    //eine stelle im lied unendlich loopen lassen:
                    if (innerLoopMode && getCurrentPosition() >= toTime) {
                        musicService.setToFrom(fromTime);
                    }
                }

                seekHandler.postDelayed(this, 1000);

            }
        });
        seekBar.setOnSeekBarChangeListener(new SeekbarChangeListener());

        //Intent an MusicService "hallo ich bin da, gib mir deinen service", wenn er noch nicht da ist, verbinde mich damit
        if (musicServiceIntent == null) {
            musicServiceIntent = new Intent(this, MusicService.class);
            bindService(musicServiceIntent, musicConnection, Context.BIND_AUTO_CREATE);
            startService(musicServiceIntent);
        }
        rangeSeekBar.setNotifyWhileDragging(true);
        rangeSeekBar.setRangeValues(0, 100);
        rangeSeekBar.setSelectedMinValue(20);
        rangeSeekBar.setSelectedMaxValue(80);
        toTime = songDuration;
        rangeSeekBar.setOnRangeSeekBarChangeListener(new RangeSeekBar.OnRangeSeekBarChangeListener<Integer>() {
            @Override
            public void onRangeSeekBarValuesChanged(RangeSeekBar<?> bar, Integer minValue, Integer maxValue) {
                fromTime = (int) (minValue / 100.0 * songDuration);
                toTime = (int) (maxValue / 100.0 * songDuration);
            }
        });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_play, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.inner_loop:
                innerLoopMode = !innerLoopMode;
                updateLayoutInnerLoop();
                if (isPlaying()) {
                    playPauseButton.setImageResource(R.drawable.icon_play);
                    musicService.pausePlayer();
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onStart() {
        super.onStart();
        if (isPlaying())
            playPauseButton.setImageResource(R.drawable.icon_pause);
        else
            playPauseButton.setImageResource(R.drawable.icon_play);
    }


    @Override
    protected void onDestroy() {
        stopService(musicServiceIntent);
        musicService = null;
        musicBound = false;
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (innerLoopMode) {
            innerLoopMode = false;
            updateLayoutInnerLoop();
        } else {
            super.onBackPressed();
        }
    }

    private void updateLayoutInnerLoop() {
        int visibility = innerLoopMode ? View.GONE : View.VISIBLE;
        repeatSongButton.setVisibility(visibility);
        shuffleButton.setVisibility(visibility);
        nexButton.setVisibility(visibility);
        prevButton.setVisibility(visibility);
        visibility = innerLoopMode ? View.VISIBLE : View.GONE;
        rangeSeekBar.setVisibility(visibility);

//        toEdit.setVisibility(visibility);
//        fromEdit.setVisibility(visibility);
//        toText.setVisibility(visibility);
//        fromText.setVisibility(visibility);
//        secText.setVisibility(visibility);
//        secText2.setVisibility(visibility);
    }


    /**
     * buttons
     **/

    @OnClick(R.id.play_next_button)
    void onPlayNextButton() {
        String songtitle = musicService.playNext(true);
        songTextView.setText(songtitle);
        //no matter what is was before, player ist im zustand playing
        playPauseButton.setImageResource(R.drawable.icon_pause);
    }

    @OnClick(R.id.play_previous_button)
    void onPlayPrevButton() {
        String songtitle = musicService.playPrev(true);
        songTextView.setText(songtitle);
        //no matter what is was before, player ist im zustand playing
        playPauseButton.setImageResource(R.drawable.icon_pause);
    }

    @OnClick(R.id.play_pause_button)
    void onPlayPauseButton() {
        if (isPlaying()) {
            //playback hat gespielt, d.h. jetzt pausieren
            playPauseButton.setImageResource(R.drawable.icon_play);
            musicService.pausePlayer();
        } else {
            //playback war pausiert, d.h. jetzt wieder abspielen
            playPauseButton.setImageResource(R.drawable.icon_pause);
            musicService.playSong(songId, true);
            if (innerLoopMode) {
                musicService.setToFrom(fromTime);
            }
//            if (innerLoopMode) {
//                try {
//                    toTime = Integer.parseInt(toEdit.getText().toString()) * 1000;
//                    fromTime = Integer.parseInt(fromEdit.getText().toString()) * 1000;
//                    if (toTime <= fromTime) {
//                        Toast toast = Toast.makeText(getApplicationContext(),
//                                "From must be smaller than To!",
//                                Toast.LENGTH_SHORT);
//                        toast.show();
//                    } else {
//                        //don't start player if to and from are wrong!
//                        playPauseButton.setImageResource(R.drawable.icon_pause);
//                        musicService.playSong(songId, true);
//                        musicService.setToFrom(fromTime);
//                    }
//                } catch (NumberFormatException e) {
//                    Toast toast = Toast.makeText(getApplicationContext(),
//                            "Please enter To and From seconds!",
//                            Toast.LENGTH_SHORT);
//                    toast.show();
//                }
//            } else {
//                //playback war pausiert, d.h. jetzt wieder abspielen
//                playPauseButton.setImageResource(R.drawable.icon_pause);
//                musicService.playSong(songId, true);
//            }
        }
    }

    @OnClick(R.id.shuffle_button)
    void onShuffleButton() {
        if (musicService.setShuffle()) {
            shuffleButton.setImageResource(R.drawable.icon_shuffle);
        } else {
            shuffleButton.setImageResource(R.drawable.icon_inorder);
        }
    }

    @OnClick(R.id.repeatSong_button)
    void onRepeatSongButton() {
        int stateRepeat = musicService.setRepeatSong();
        switch (stateRepeat) {
            case REPEAT_NONE:
                repeatSongButton.setImageResource(R.drawable.icon_repeat_none);
                break;
            case REPEAT_ONE:
                repeatSongButton.setImageResource(R.drawable.icon_repeat_one);
                break;
            case REPEAT_ALL:
                repeatSongButton.setImageResource(R.drawable.icon_repeat_all);
                break;
        }
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
            if (musicService != null && musicService.isPlaying() && fromUser) {
                musicService.seek(progress);
            }
        }
    }
}
