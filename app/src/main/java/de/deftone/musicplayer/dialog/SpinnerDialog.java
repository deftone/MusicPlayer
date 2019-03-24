package de.deftone.musicplayer.dialog;


import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import java.util.List;

import de.deftone.musicplayer.R;

public class SpinnerDialog extends Dialog {
    private List<String> mList;
    private Context mContext;
    private Spinner mSpinner;

    public interface DialogListener {
        public void clickOk(int n);

        public void cancelled();
    }

    private DialogListener mReadyListener;

    public SpinnerDialog(Context context, List<String> list, DialogListener readyListener) {
        super(context);
        mReadyListener = readyListener;
        mContext = context;
        mList = list;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.spinner_dialog);
        mSpinner = findViewById(R.id.dialog_spinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(mContext, android.R.layout.simple_spinner_dropdown_item, mList);
        mSpinner.setAdapter(adapter);

        Button buttonOK = findViewById(R.id.dialogOK);
        Button buttonCancel = findViewById(R.id.dialogCancel);
        buttonOK.setOnClickListener(new android.view.View.OnClickListener() {
            public void onClick(View v) {
                int n = mSpinner.getSelectedItemPosition();
                mReadyListener.clickOk(n);
                SpinnerDialog.this.dismiss();
            }
        });
        buttonCancel.setOnClickListener(new android.view.View.OnClickListener() {
            public void onClick(View v) {
                mReadyListener.cancelled();
                SpinnerDialog.this.dismiss();
            }
        });
    }
}
