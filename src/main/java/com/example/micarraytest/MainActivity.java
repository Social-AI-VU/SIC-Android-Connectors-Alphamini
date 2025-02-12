package com.example.micarraytest;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

import com.ubt.speecharray.DataCallback;
import com.ubt.speecharray.MicArrayUtils;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    // Bewaar de MicArrayUtils instance en de opname status als veld
    private MicArrayUtils micArrayUtils;
    private boolean isRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Initialiseer de MicArrayUtils
        micArrayUtils = new MicArrayUtils(this.getApplicationContext(), 16000, 16, 512);
        micArrayUtils.init();
        micArrayUtils.setDataCallback(new DataCallback() {
            @Override
            public void onAudioData(byte[] bytes) {
                // 6-kanalen data: 1-4: microfoondata, 5-6: referentiesignalen
                Log.d(TAG, "onAudioData - bytes.length = " + bytes.length);
                byte[][] spliteData = MicArrayUtils.spliteData(bytes);
            }
        });
        // Zorg dat de PCM-data wordt opgeslagen op sdcard (pad: /sdcard/micdata/)
        micArrayUtils.setSaveOriginalAudio(true);

        // Gebruik de FloatingActionButton als toggle-knop
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isRecording) {
                    // Start de opname als deze nog niet bezig is
                    micArrayUtils.startRecord();
                    isRecording = true;
                    Snackbar.make(view, "Opname gestart", Snackbar.LENGTH_SHORT).show();
                } else {
                    // Stop de opname als er al wordt opgenomen
                    micArrayUtils.stopRecord();
                    isRecording = false;
                    Snackbar.make(view, "Opname gestopt", Snackbar.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Voeg menu-items toe als dat nodig is
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handel acties af vanuit de action bar
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}