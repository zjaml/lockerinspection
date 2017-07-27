package com.example.jin.lockerinspection;

import android.bluetooth.BluetoothDevice;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;


import io.kiny.bluetooth.BluetoothClient;
import io.kiny.bluetooth.BluetoothClientInterface;
import io.kiny.bluetooth.Constants;
import io.kiny.bluetooth.FakeBTClient;


public class MainActivity extends AppCompatActivity {
    public static final int COLOR_SUCCESS = Color.rgb(72, 145, 116);
    public static final int COLOR_NORMAL = Color.BLACK;
    public static final int COLOR_FAILURE = Color.rgb(128, 45, 21);
    TextView operationText, logText, statusText;
    Button startButton, nextButton, stopButton;

    List<LockerAction> actions = new ArrayList<>();
    List<LockerAction> previousResult = null;

    //the current command index, -1 stands for testing stopped.
    int currentCommand = -1;

    private BluetoothClientInterface mBluetoothClient;
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Constants.MESSAGE_CONNECTION_LOST:
                    // reconnect since connection is lost
                    if (mBluetoothClient != null) {
                        mBluetoothClient.connect();
                    }
                    setUIConnected(false);
                    break;
                case Constants.MESSAGE_CONNECTED:
                    setUIConnected(true);
                    break;
                case Constants.MESSAGE_INCOMING_MESSAGE:
                    processMessage((String) msg.obj);
                    break;
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
        mBluetoothClient = new FakeBTClient(mHandler, false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBluetoothClient.disconnect();
        mBluetoothClient.getBluetoothBroadcastReceiver().safeUnregister(this);
        mBluetoothClient = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mBluetoothClient != null && mBluetoothClient.getState() == BluetoothClient.STATE_NONE) {
            mBluetoothClient.connect();
            mBluetoothClient.getBluetoothBroadcastReceiver()
                    .safeRegister(this, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
        }
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


    public boolean isBtConnected() {
        return mBluetoothClient != null && mBluetoothClient.getState() == BluetoothClientInterface.STATE_CONNECTED;
    }

    private void performAction(LockerAction action) {
        if (!action.issued()) {

            setOperationMessage(String.format("%s号门将开启，请%s物体\n", action.getDoorNumber(), action.isCheckIn() ? "存入" : "取出"), true);
            String command = String.format("O%s%s", action.getDoorNumber(), action.isCheckIn() ? "T" : "R");
            Log.d("Debug", command);
            if (isBtConnected()) {
                mBluetoothClient.sendCommand(command);
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


