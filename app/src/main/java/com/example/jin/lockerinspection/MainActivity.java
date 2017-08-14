package com.example.jin.lockerinspection;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.kiny.BoxStatus;
import io.kiny.LockerManager;


public class MainActivity extends AppCompatActivity {
    public static final String TARGET_DEVICE_NAME = "HC-06";
    private LockerManager mLockerManager;
    public static final int COLOR_SUCCESS = Color.rgb(72, 145, 116);
    public static final int COLOR_NORMAL = Color.BLACK;
    public static final int COLOR_FAILURE = Color.rgb(128, 45, 21);
    TextView operationText, logText, statusText;
    Button startButton, nextButton, stopButton;

    List<LockerAction> actions = new ArrayList<>();
    List<LockerAction> previousResult = null;
    //the current command index, -1 stands for testing stopped.
    int currentCommand = -1;

    private BroadcastReceiver lockerBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case LockerManager.ACTION_LOCKER_READY:
                    Log.d("LockerManager", "ACTION_LOCKER_READY");
                case LockerManager.ACTION_LOCKER_CONNECTED:
                    setUIConnected(true);
                    break;
                case LockerManager.ACTION_LOCKER_DISCONNECTED:
                    setUIConnected(false);
                    break;
                case LockerManager.ACTION_LOCKER_CHARGING:
                    break;
                case LockerManager.ACTION_LOCKER_DISCHARGING:
                    break;
                case LockerManager.ACTION_LOCKER_BOX_OPENED: {
                    break;
                }
                case LockerManager.ACTION_LOCKER_BOX_CLOSED: {
                    if (currentCommand >= 0) {
                        String boxNumber = intent.getStringExtra("box");
                        String boxStatus = intent.getStringExtra("status");
                        LockerAction currentAction = actions.get(currentCommand);
                        if (Objects.equals(boxNumber, currentAction.getDoorNumber())) {
                            currentAction.setWasEmpty(Objects.equals(boxStatus, BoxStatus.BOX_EMPTY));
                            currentAction.setCompleted(true);
                            setButtonStates(false, true, true);
                            boolean success = currentAction.isCheckIn() && !currentAction.wasEmpty();
                            setOperationMessage(String.format("%s号门已关闭，%s    (结果%s)\n",
                                    currentAction.getDoorNumber(),
                                    currentAction.wasEmpty() ? "未检测到物体" : "检测到物体",
                                    success ? "正常" : "异常"), success);
                        }
                    }
                    break;
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        statusText = (TextView) findViewById(R.id.statusText);
        logText = (TextView) findViewById(R.id.logText);
        logText.setMovementMethod(new ScrollingMovementMethod());
        operationText = (TextView) findViewById(R.id.operationText);
        startButton = (Button) findViewById(R.id.startButton);
        nextButton = (Button) findViewById(R.id.nextButton);
        stopButton = (Button) findViewById(R.id.stopButton);
        initActions();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setUIConnected(false);
        mLockerManager = new LockerManager(TARGET_DEVICE_NAME, getApplicationContext(), true);
        mLockerManager.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mLockerManager.stop();
        mLockerManager = null;
    }

    @Override
    protected void onResume() {
        super.onResume();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(LockerManager.ACTION_LOCKER_READY);
        intentFilter.addAction(LockerManager.ACTION_LOCKER_BOX_CLOSED);
        intentFilter.addAction(LockerManager.ACTION_LOCKER_BOX_OPENED);
        intentFilter.addAction(LockerManager.ACTION_LOCKER_CHARGING);
        intentFilter.addAction(LockerManager.ACTION_LOCKER_DISCHARGING);
        intentFilter.addAction(LockerManager.ACTION_LOCKER_CONNECTED);
        intentFilter.addAction(LockerManager.ACTION_LOCKER_DISCONNECTED);
        LocalBroadcastManager.getInstance(this).registerReceiver(lockerBroadcastReceiver, intentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(lockerBroadcastReceiver);
    }


    public void setUIConnected(boolean connected) {
        statusText.setText(connected ? "Connected" : "Disconnected");
        if (!connected)
            operationText.setText("点击开始按钮开始测试\n");
        statusText.setTextColor(connected ? COLOR_SUCCESS : COLOR_FAILURE);
        startButton.setEnabled(connected);
        stopButton.setEnabled(connected);
        nextButton.setEnabled(connected);
    }

    private void initActions() {
        //check in 6 then checkout 6, do 5 loops to check all 30 boxes.
        actions.clear();
        int baseDoorNumber = 1;
        boolean isCheckIn;
        for (int i = 0; i < 10; i++) {
            isCheckIn = i % 2 == 0;
            for (int j = 0; j < 6; j++) {
                int doorNumber = baseDoorNumber + j;
                actions.add(
                        new LockerAction(isCheckIn, String.format("%02d", doorNumber)));

            }
            if (!isCheckIn)
                baseDoorNumber += 6;
        }
    }

    private void processMessage(String message) {
        Log.d("Debug", "processing message: " + message);
        if (currentCommand >= 0) {
            LockerAction currentAction = actions.get(currentCommand);
            if (message.equals("A")) {
                currentAction.setAcked(true);
            } else if (message.equals(String.format("E%s", currentAction.getDoorNumber()))) {
                currentAction.setWasEmpty(true);
                currentAction.setCompleted(true);
                setButtonStates(false, true, true);
                boolean success = !currentAction.isCheckIn();
                setOperationMessage(String.format("%s号门已关闭，未检测到物体    (结果%s)\n",
                        currentAction.getDoorNumber(), success ? "正常" : "异常"), success);
            } else if (message.equals(String.format("F%s", currentAction.getDoorNumber()))) {
                currentAction.setWasEmpty(false);
                currentAction.setCompleted(true);
                setButtonStates(false, true, true);
                boolean success = currentAction.isCheckIn();
                setOperationMessage(String.format("%s号门已关闭，检测到物体   (结果%s)\n",
                        currentAction.getDoorNumber(), success ? "正常" : "异常"), success);
            } else {
                // display unexpected data.
                setOperationMessage(String.format("接收到未想定消息:%s\n", message), false);
            }
        }
    }

    private void setOperationMessage(final String message, final boolean succeed) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                operationText.setText(message);
                operationText.setTextColor(succeed ? COLOR_NORMAL : COLOR_FAILURE);
                logText.append(message);
            }
        });
    }

    private void setButtonStates(final boolean startButtonEnabled, final boolean nextButtonEnabled,
                                 final boolean stopButtonEnabled) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                startButton.setEnabled(startButtonEnabled);
                nextButton.setEnabled(nextButtonEnabled);
                stopButton.setEnabled(stopButtonEnabled);
            }
        });
    }

    public void onStartClick(View view) {
        currentCommand = 0;
        performAction(actions.get(currentCommand));
        setButtonStates(false, false, true);
    }

    private void performAction(LockerAction action) {
        if (!action.issued()) {
            setOperationMessage(String.format("%s号门将开启，请%s物体\n", action.getDoorNumber(), action.isCheckIn() ? "存入" : "取出"), true);
            if (action.isCheckIn()) {
                mLockerManager.requestToCheckIn(action.getDoorNumber());
            } else {
                mLockerManager.requestToCheckOut(action.getDoorNumber());
            }
            action.setIssued(true);
        }
    }

    public void onNextClick(View view) {
        currentCommand++;
        performAction(actions.get(currentCommand));
        //disable next button until the current action finishes.
        setButtonStates(false, false, true);
    }

    public void onStopClick(View view) {
        previousResult = new ArrayList<>(actions);
        currentCommand = -1;
        initActions();
        setButtonStates(true, false, false);
    }

}


