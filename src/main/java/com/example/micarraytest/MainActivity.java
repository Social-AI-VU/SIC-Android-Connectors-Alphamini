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
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        micArrayTest();
    }


    private void micArrayTest() {
        final MicArrayUtils micArrayUtils = new MicArrayUtils(this.getApplicationContext(),16000,16,512);
        //init
        micArrayUtils.init();
        //set Callback to receive audio data
        micArrayUtils.setDataCallback(new DataCallback() {
            @Override
            public void onAudioData(byte[] bytes) {
                // 6 channels data, 1-4: mic data, 5-6: ref data
                Log.d(TAG,"onAudioData---bytes.length = "+bytes.length);
                byte[][] spliteData = MicArrayUtils.spliteData(bytes);
            }
        });
        //start mic Array
        micArrayUtils.startRecord();
        //save pcm data in sdcard (path : /sdcard/micdata/)
        micArrayUtils.setSaveOriginalAudio(true);
        //stop mic Array
        //micArrayUtils.stopRecord();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
