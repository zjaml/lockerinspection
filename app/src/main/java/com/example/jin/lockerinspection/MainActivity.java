package com.example.jin.lockerinspection;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.Spanned;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MainActivity extends AppCompatActivity {
    public final String ACTION_USB_PERMISSION = "com.example.jin.lockerinspection.USB_PERMISSION";
    UsbManager usbManager;
    UsbDeviceConnection connection;
    UsbSerialDevice serialPort;
    UsbDevice device;

    TextView operationText, logText;
    Button startButton, nextButton, stopButton;

    List<LockerAction> actions = new ArrayList<>();
    List<LockerAction> previousResult = null;

    //the current command index, -1 stands for testing stopped.
    int currentCommand = -1;

    StringBuilder messageBuffer = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        usbManager = (UsbManager) getSystemService(USB_SERVICE);
        logText = (TextView) findViewById(R.id.logText);
        logText.setMovementMethod(new ScrollingMovementMethod());
        operationText = (TextView) findViewById(R.id.operationText);
        startButton = (Button) findViewById(R.id.startButton);
        nextButton = (Button) findViewById(R.id.nextButton);
        stopButton = (Button) findViewById(R.id.stopButton);
        initActions();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        initUIState();
    }

    private void initUIState() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                operationText.setText("请连接储物柜的USB\n");
                logText.setText("");
                startButton.setVisibility(View.INVISIBLE);
                nextButton.setVisibility(View.INVISIBLE);
                stopButton.setVisibility(View.INVISIBLE);
            }
        });
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

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("Debug", "OnResume");
        if (serialPort == null) {
            requestConnectionPermission();
        }
        registerBroadCastEvent();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(broadcastReceiver);
    }

    private void registerBroadCastEvent() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(broadcastReceiver, filter);
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() { //Broadcast Receiver to automatically start and stop the Serial connection.
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_USB_PERMISSION)) {
                boolean granted = intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                if (granted) {
                    Log.d("Debug", "Trying to establish USB connection");
                    connection = usbManager.openDevice(device);
                    serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection);
                    if (serialPort != null) {
                        if (serialPort.open()) { //Set Serial Connection Parameters.
                            Log.d("Debug", "Serial port open");
                            serialPort.setBaudRate(9600);
                            serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                            serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                            serialPort.setParity(UsbSerialInterface.PARITY_NONE);
                            serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                            serialPort.read(
                                    new UsbSerialInterface.UsbReadCallback() {
                                        @Override
                                        public void onReceivedData(byte[] bytes) {
                                            String data;
                                            try {
                                                data = new String(bytes, "US-ASCII");
                                                for (char ch : data.toCharArray()) {
                                                    if (ch != '\n') {
                                                        messageBuffer.append(ch);
                                                    } else {
                                                        String message = messageBuffer.toString();
                                                        processMessage(message);
                                                        messageBuffer = new StringBuilder();
                                                    }
                                                }
                                            } catch (UnsupportedEncodingException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    });

                            setOperationMessage("USB连接成功,请准备5个小东西做存放检测，点击开始按钮开始测试\n");
                            setButtonsVisibility(true, false, false);
                        } else {
                            Log.d("SERIAL", "PORT NOT OPEN");
                        }
                    } else {
                        Log.d("SERIAL", "PORT IS NULL");
                    }
                } else {
                    Log.d("SERIAL", "PERM NOT GRANTED");
                }
            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                requestConnectionPermission();
            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                disconnect();
            }
        }
    };

    private void processMessage(String message) {
        if (message.trim().length() == 0) {
            Log.d("Debug", "empty message ignored");
            return;
        }
        Log.d("Debug", "processing message: " + message);
        if (currentCommand >= 0) {
            LockerAction currentAction = actions.get(currentCommand);
            if (message.equals("A")) {
                Log.d("Debug", "A");
                currentAction.setAcked(true);
            } else if (message.equals(String.format("E%s", currentAction.getDoorNumber()))) {
                currentAction.setWasEmpty(true);
                currentAction.setCompleted(true);
                setButtonsVisibility(false, true, true);
                boolean success = !currentAction.isCheckIn();
                setOperationMessage(String.format("%s号门已关闭，未检测到物体 \n 结果%s\n",
                        currentAction.getDoorNumber(), success ? "正常" : "异常"));
            } else if (message.equals(String.format("F%s", currentAction.getDoorNumber()))) {
                currentAction.setWasEmpty(false);
                currentAction.setCompleted(true);
                setButtonsVisibility(false, true, true);
                boolean success = currentAction.isCheckIn();
                setOperationMessage(String.format("%s号门已关闭，检测到物体 \n 结果%s\n",
                        currentAction.getDoorNumber(), success ? "正常" : "异常"));
            } else {
                // display unexpected data.
                setOperationMessage(String.format("接收到未想定消息:%s\n", message));
            }
        }
    }


    public void requestConnectionPermission() {
        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        if (!usbDevices.isEmpty()) {
            boolean keep = true;
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                device = entry.getValue();
                int deviceVID = device.getVendorId();
                if (deviceVID == 0x2341 || deviceVID == 0x10C4)//Arduino Vendor ID or CP2102
                {
                    PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
                    usbManager.requestPermission(device, pi);
                    keep = false;
                } else {
                    connection = null;
                    device = null;
                }
                if (!keep)
                    break;
            }
        }
    }

    public void disconnect() {
        if (serialPort != null) {
            serialPort.close();
        }
        connection = null;
        device = null;
        initUIState();
    }

    private void setOperationMessage(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                operationText.setText(message);
                logText.append(message);
            }
        });
    }

    private void appendOperationMessage(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                operationText.append(message);
                logText.append(message);
            }
        });
    }

    private void setButtonsVisibility(final boolean startButtonVisible, final boolean nextButtonVisible, final boolean stopButtonVisible) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                startButton.setVisibility(startButtonVisible ? View.VISIBLE : View.INVISIBLE);
                nextButton.setVisibility(nextButtonVisible ? View.VISIBLE : View.INVISIBLE);
                stopButton.setVisibility(stopButtonVisible ? View.VISIBLE : View.INVISIBLE);
            }
        });
    }

    public void onStartClick(View view) {
        currentCommand = 0;
        performAction(actions.get(currentCommand));
        setButtonsVisibility(false, false, true);
    }

    private void performAction(LockerAction action) {
        if (!action.issued()) {
            setOperationMessage(String.format("%s号门将开启，请%s物体\n", action.getDoorNumber(), action.isCheckIn() ? "存入" : "取出"));
            String command = String.format("O%s%s\n", action.getDoorNumber(), action.isCheckIn() ? "T" : "R");
            Log.d("Debug", command);
            serialPort.write(command.getBytes(Charset.forName("ASCII")));
            action.setIssued(true);
        }
    }

    public void onNextClick(View view) {
        currentCommand++;
        performAction(actions.get(currentCommand));
        //disable next button until the current action finishes.
        setButtonsVisibility(false, false, true);
    }

    public void onStopClick(View view) {
        previousResult = new ArrayList<>(actions);
        currentCommand = -1;
        initActions();
        setButtonsVisibility(true, false, false);
    }

}


