package com.example.jin.lockerinspection;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;
import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.appindexing.Thing;
import com.google.android.gms.common.api.GoogleApiClient;

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
        setButtonsVisibility(false, false, false);
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
                    connection = usbManager.openDevice(device);
                    serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection);
                    if (serialPort != null) {
                        if (serialPort.open()) { //Set Serial Connection Parameters.
                            serialPort.setBaudRate(9600);
                            serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                            serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                            serialPort.setParity(UsbSerialInterface.PARITY_NONE);
                            serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                            serialPort.read(new UsbSerialInterface.UsbReadCallback() {
                                @Override
                                public void onReceivedData(byte[] bytes) {
                                    String data;
                                    try {
                                        if (currentCommand >= 0) {
//                                            ignore if app is stopped
                                            data = new String(bytes, "ASCII");
                                            LockerAction currentAction = actions.get(currentCommand);
                                            if (data.equals("A")) {
                                                currentAction.setAcked(true);
                                            } else if (data.equals(String.format("E%s", currentAction.getDoorNumber()))) {
                                                currentAction.setWasEmpty(true);
                                                currentAction.setCompleted(true);
                                                setButtonsVisibility(false, true, true);
                                            } else if (data.equals(String.format("F%s", currentAction.getDoorNumber()))) {
                                                currentAction.setWasEmpty(false);
                                                currentAction.setCompleted(true);
                                                setButtonsVisibility(false, true, true);
                                            } else {
                                                // display unexpected data.
                                                setOperationMessage(String.format("接收到未想定消息:%s", data));
                                            }
                                        }
                                    } catch (UnsupportedEncodingException e) {
                                        setOperationMessage(e.getMessage());
                                    }
                                }
                            });
                            setOperationMessage("USB连接成功");
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
        setOperationMessage("USB连接断开");
        setButtonsVisibility(false, false, false);
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
        setButtonsVisibility(false, true, true);
    }

    private void performAction(LockerAction action) {
        if (!action.issued()) {
            String command = String.format("O%s%s", action.getDoorNumber(), action.isCheckIn() ? "T" : "R");
            serialPort.write(command.getBytes(Charset.forName("ASCII")));
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


