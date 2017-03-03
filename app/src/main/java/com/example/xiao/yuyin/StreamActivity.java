package com.example.xiao.yuyin;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static com.google.common.io.Closeables.closeQuietly;

public class StreamActivity extends AppCompatActivity {

    @BindView(R.id.btn_stream_say)
    private Button mSayBtn;

    @BindView(R.id.tv_stream_log)
    private TextView mLogTv;

    //当前录音的状态
    private volatile boolean mIsRecording;
    private ExecutorService mExecutorService;
    private Handler mMainThreadHandler;

    private File mAudioFile;
    private long mStartRecordTime;
    private long mStopRecordTime;
    private static final int BUFFER_SIZE = 2048;
    private byte[] mBuffer;
    private FileOutputStream mOutputStream;
    private AudioRecord mAudioRecord;
    private volatile boolean mIsPlaying;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stream);
        ButterKnife.bind(this);

        mExecutorService = Executors.newSingleThreadExecutor();
        mMainThreadHandler = new Handler(Looper.getMainLooper());
        mBuffer = new byte[BUFFER_SIZE];


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mExecutorService.shutdownNow();
    }

    @OnClick(R.id.btn_stream_say)
    private void start() {
        //根据当前的状态，改变UI，执行开始或停止的逻辑
        if (mIsRecording) {
            mSayBtn.setText("开始录音");
            //停止录音
            mIsRecording = false;

        } else {
            mSayBtn.setText("停止录音");
            //开始录音
            mIsRecording = true;
            //提交后台任务，执行录音逻辑
            mExecutorService.submit(new Runnable() {
                @Override
                public void run() {
                    //执行录音逻辑，失败提示用户
                    if (!startRecord()) {
                        recordFail();
                    }
                }
            });


        }

    }

    @OnClick(R.id.btn_stream_play)
    private void play(){
        //检查当前状态，放在重复播放
        if(mAudioFile != null && !mIsPlaying){
            //设置当前播放状态
            mIsPlaying = true;

            //提交后台任务，开始播放
            mExecutorService.submit(new Runnable() {
                @Override
                public void run() {
                    doPlay(mAudioFile);
                }
            });

        }
    }




    /**
     * 开始录音逻辑
     *
     * @return
     */
    private boolean startRecord() {

        try {
            //创建一个录音文件
            mAudioFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath()
                    + "/yuyin/" + System.currentTimeMillis() + ".pcm");
            mAudioFile.getParentFile().mkdirs();
            mAudioFile.createNewFile();

            //创建一个文件输出流
            mOutputStream = new FileOutputStream(mAudioFile);

            //配置AudioRecorder
            int audioSource = MediaRecorder.AudioSource.MIC;
            int sampleRate = 44100;
            int channelConfig = AudioFormat.CHANNEL_IN_MONO;//单声道输入
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;//
            int minBufferSize = AudioRecord.getMinBufferSize(sampleRate,channelConfig,audioFormat);
            //bufferSize 不能小于系统最小的bufferSize，也不能小于定义的bufferSize
            mAudioRecord = new AudioRecord(audioSource,sampleRate,channelConfig,
                    audioFormat,Math.max(minBufferSize,BUFFER_SIZE));


            //开始录音
            mAudioRecord.startRecording();

            //记录开始录音时间，统计时长
            mStartRecordTime = System.currentTimeMillis();

            //循环读取数据，写在输出流中
            while(mIsRecording){
                //只要还在录音就一直读取
                int read = mAudioRecord.read(mBuffer,0,mBuffer.length);
                if(read>0){
                    mOutputStream.write(mBuffer,0,read);

                }else{
                    //读取失败
                    return false;
                }

            }

            //退出循环，停止录音，释放资源
            return stopRecord();
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            return false;
        } finally {
            //释放AudioRecord
            if(mAudioRecord != null){
                mAudioRecord.release();
            }
        }
    }

    /**
     * 停止录音
     *
     * @return
     */
    private boolean stopRecord() {

        try {
            //停止录音,关闭文件输出流
            mAudioRecord.stop();
            mAudioRecord.release();
            mAudioRecord = null;
            mOutputStream.close();

            //记录结束时间，统计录音时长
            mStopRecordTime = System.currentTimeMillis();
            final int totalTime = (int) ((mStopRecordTime - mStartRecordTime)/1000);
            //大于3s才算成功，在主线程中改变ＵＩ显示
            if(totalTime>3){
                mMainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mLogTv.setText(mLogTv.getText()+"\n"+"录音成功，时长："+totalTime+"秒");
                    }
                });
            }

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }





        return true;
    }


    /**
     * 录音失败
     */
    private void recordFail() {
        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(StreamActivity.this,"录音失败",Toast.LENGTH_LONG).show();
                //重置录音状态
                mIsRecording = false;
                mSayBtn.setText("开始录音");
            }
        });
    }

    /**
     * 执行播放逻辑
     * @param file
     */
    private void doPlay(File file) {
        //配置播放器
        int streamType = AudioManager.STREAM_MUSIC;//音乐类型，扬声器播放
        int sampleRate = 44100; // 录音采用的采样频率，播放时采用同样的采样频率
        int channelConfig = AudioFormat.CHANNEL_OUT_MONO;//单声道
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int mode = AudioTrack.MODE_STREAM;//流模式
        int minBufferSize = AudioTrack.getMinBufferSize(sampleRate,channelConfig,
                audioFormat);

        //构造AudioTrack
        AudioTrack audioTrack = new AudioTrack(streamType,sampleRate,channelConfig,audioFormat,
                Math.max(minBufferSize,BUFFER_SIZE),mode);

        //从文件流中读取数据
        FileInputStream inputStream = null;
        try{
            inputStream = new FileInputStream(file.getAbsolutePath());

            //循环读取数据，写到播放器进行播放
            int read;
            while((read = inputStream.read(mBuffer))>0){
                int ret = audioTrack.write(mBuffer,0,read);
                //检查返回值，进行错误处理
                switch (ret){
                    case AudioTrack.ERROR_INVALID_OPERATION:
                    case AudioTrack.ERROR_BAD_VALUE:
                    case AudioManager.ERROR_DEAD_OBJECT:
                        playFail();
                        return;
                    default:
                        break;
                }

            }

        }catch (RuntimeException | IOException e){
            e.printStackTrace();
            //错误处理
            playFail();

        }finally {
            mIsPlaying = false;
            //关闭文件输入流
            closeQuietly(inputStream);

            //释放播放器
            resetQuietly(audioTrack);

        }

    }

    /**
     * 释放播放器
     * @param audioTrack
     */
    private void resetQuietly(AudioTrack audioTrack) {
        if(audioTrack!= null){
            audioTrack.stop();
            audioTrack.release();
        }
    }

    /**
     * 停止播放
     */
    private void stopPlay(){

    }

    /**
     * 播放失败
     */
    private void playFail(){

    }
}
