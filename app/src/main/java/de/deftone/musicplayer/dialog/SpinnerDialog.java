package de.deftone.musicplayer.dialog;


import android.app.Dialog;
import android.content.Context;
import android.media.audiofx.Equalizer;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import com.google.common.collect.BiMap;

import java.util.List;

import de.deftone.musicplayer.R;
import de.deftone.musicplayer.activity.PlayActivity;

public class SpinnerDialog extends Dialog {
    private short oldValue;
    private List<String> equalizerList;
    private Context mContext;
    private BiMap<Short, String> equalizerMap;
    private Equalizer equalizer;
    private Spinner mSpinner;

    public SpinnerDialog(Context context, PlayActivity activity) {
        super(context);
        mContext = context;
        equalizerList = activity.getEqualizerList();
        equalizerMap = activity.getEqualizerMap();
        equalizer = activity.getEqualizer();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.spinner_dialog);
        mSpinner = findViewById(R.id.dialog_spinner);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(mContext, R.layout.spinner_item_layout, equalizerList);
        mSpinner.setAdapter(adapter);
        mSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String eqName = equalizerList.get(i);
                short eqBand = equalizerMap.inverse().get(eqName);
                equalizer.usePreset(eqBand);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                //do nothing
            }
        });
        Button buttonOK = findViewById(R.id.dialogOK);
        Button buttonCancel = findViewById(R.id.dialogCancel);
        buttonOK.setOnClickListener(v -> {
            //do nothing and dismiss
            SpinnerDialog.this.dismiss();
        });
        buttonCancel.setOnClickListener(v -> {
            //reset to old value
            equalizer.usePreset(oldValue);
            SpinnerDialog.this.dismiss();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        oldValue = equalizer.getCurrentPreset();
        mSpinner.setSelection(oldValue);
    }
}
