package com.example.xiao.yuyin;

import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class FileActivity extends AppCompatActivity {

    @BindView(R.id.tv_file_log)
    private TextView mFileLogTV;

    @BindView(R.id.btn_file_say)
    private Button mSayBtn;

    private ExecutorService mService;
    private MediaRecorder mMediaRecorder;
    private File mAudioFile;

    private long mStartRecordTime,mStopRecordTime;

    private static final int RECORD_FAIL = 0x01;


    private Handler mMainThreadHandler;
    private volatile boolean mIsPlaying;
    private MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file);
        ButterKnife.bind(this);

        //录音JNI函数不具备线程安全性，所以使用单线程
        mService = Executors.newSingleThreadExecutor();
        mMainThreadHandler = new Handler(Looper.getMainLooper());

        mSayBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                int action = motionEvent.getAction();
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        startRecord();
                        break;
                    case MotionEvent.ACTION_CANCEL:

                    case MotionEvent.ACTION_UP:
                        stopRecord();
                        break;
                    default:
                        break;
                }

                return true;
            }
        });
    }

    @OnClick(R.id.btn_file_play)
    private void play(){
        //检查当前状态，放在重复播放
        if(mAudioFile != null && !mIsPlaying){
            //设置当前播放状态
            mIsPlaying = true;

            //提交后台任务，开始播放
            mService.submit(new Runnable() {
                @Override
                public void run() {
                    doPlay(mAudioFile);
                }
            });
            
        }
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();

        //activity销毁时，停止后台任务
        if (mService != null) {
            mService.shutdownNow();
        }
        releaseRecorder();
        stopPlay();
    }


    /**
     * 开始记录
     */
    private void startRecord() {
        //改变UI状态
        mSayBtn.setText("正在说话");

        //提交后台任务，执行录音逻辑
        mService.submit(new Runnable() {
            @Override
            public void run() {
                //释放之前录音的record
                releaseRecorder();

                //执行录音逻辑，如果失败，则提示用户
                if (!doStart()) {
                    recordFail();
                }

            }
        });
    }


    /**
     * 停止记录
     */
    private void stopRecord() {
        //改变UI状态
        mSayBtn.setText("按下说话");

        //提交后台任务，执行录音逻辑
        mService.submit(new Runnable() {
            @Override
            public void run() {
                //执行停止录音逻辑
                if (!doStop()) {
                    recordFail();
                }

                //释放MediaRecord
                releaseRecorder();
            }
        });
    }


    /**
     * 启动录音逻辑
     *
     * @return
     */
    private boolean doStart() {

        try {
            //创建MediaRecorder
            mMediaRecorder = new MediaRecorder();

            //创建录音文件
            mAudioFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath()
                    + "/yuyin/" + System.currentTimeMillis() + ".m4a");
            mAudioFile.getParentFile().mkdirs();
            mAudioFile.createNewFile();

            //配置MediaRecorder
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);//从麦克风中获取声音数据
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);//保存文件格式为mp4
            mMediaRecorder.setAudioSamplingRate(44100);//设置声音采样频率，所有安卓都支持的频率
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);//设置编码格式AAC
            mMediaRecorder.setAudioEncodingBitRate(96000);//设置编码的频率
            mMediaRecorder.setOutputFile(mAudioFile.getAbsolutePath());//设置录音文件位置

            //开始录音
            mMediaRecorder.prepare();
            mMediaRecorder.start();

            //记录开始录音的时间，用于统计时长
            mStartRecordTime = System.currentTimeMillis();

        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            //捕获异常返回false
            return false;
        }



        //录音成功，返回true
        return true;
    }

    /**
     * 停止录音逻辑
     *
     * @return
     */
    private boolean doStop() {
        try{
            //停止录音
            mMediaRecorder.stop();
            //记录停止时间，统计时长
            mStopRecordTime = System.currentTimeMillis();
            //只接受超过3s的录音
            final long totalTime = (mStopRecordTime - mStartRecordTime)/1000;
            if(totalTime>3){
                mMainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mFileLogTV.setText(mFileLogTV.getText()+"\n"+"录音成功，时长："+totalTime+"秒");
                    }
                });
            }

        }catch (RuntimeException e){
            e.printStackTrace();
            recordFail();
            return false;
        }



        return true;
    }


    /**
     * 释放之前的录音的MediaRecord
     */
    private void releaseRecorder() {
        //检查MediaRecorder不为空
        if(mMediaRecorder != null){
            mMediaRecorder.release();
            mMediaRecorder = null;
        }


    }

    /**
     * 录音失败的处理
     */
    private void recordFail() {
        mAudioFile = null;
        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(FileActivity.this,"录音失败",Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * 实际播放的逻辑
     * @param file
     */
    private void doPlay(File file) {

        try{
            //配置播放器MediaPlayer
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(file.getAbsolutePath());
            //设置监听回调
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    //释放播放器
                    stopPlay();
                }
            });
            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    //提示用户
                    playFail();

                    //释放播放器
                    stopPlay();

                    //错误已经处理，返回true
                    return true;
                }
            });
            //设置音量，释放循环播放
            mediaPlayer.setVolume(1,1);
            mediaPlayer.setLooping(false);

            //准备开始播放
            mediaPlayer.prepare();
            mediaPlayer.start();

        }catch (RuntimeException |IOException e){
            e.printStackTrace();
            //异常处理，防止闪退
            playFail();
            //释放播放器
            stopPlay();
        }


    }



    /**
     * 停止播放
     */
    private void stopPlay() {
        //重置播放状态
        mIsPlaying = false;

        //释放播放器
        if(mediaPlayer!= null){
            //重置监听器，防止内存泄露
            mediaPlayer.setOnErrorListener(null);
            mediaPlayer.setOnCompletionListener(null);

            mediaPlayer.stop();
            mediaPlayer.reset();
            mediaPlayer.release();
            mediaPlayer = null;

        }
    }

    /**
     * 播放失败
     */
    private void playFail() {

        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(FileActivity.this,"播放失败",Toast.LENGTH_LONG).show();
            }
        });
    }





}
