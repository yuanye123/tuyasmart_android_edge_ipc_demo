package com.tuya.ai.ipcsdkdemo;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //涂鸦配网
        findViewById(R.id.btn_tuya).setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, TuyaActivity.class));
        });

        //非涂鸦配网
        findViewById(R.id.btn_no_tuya).setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, NoTuyaActivity.class));
        });
    }
}
