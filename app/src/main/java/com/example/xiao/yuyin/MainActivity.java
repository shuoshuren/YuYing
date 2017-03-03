package com.example.xiao.yuyin;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);
    }

    /**
     * 文件模式
     */
    @OnClick(R.id.btn_file_mode)
    public void fileMode(){
        startActivity(new Intent(MainActivity.this,FileActivity.class));

    }

    /**
     * 字节流模式
     */
    @OnClick(R.id.btn_stream_mode)
    public void streamMode(){
        startActivity(new Intent(MainActivity.this, StreamActivity.class));
    }
}
