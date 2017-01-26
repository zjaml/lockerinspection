package com.example.jin.lockerinspection;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.io.UnsupportedEncodingException;
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

    int currentCommand = -1; //the current command index, -1 stands for testing stopped.
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
    }

    private void initActions() {
        //check in 6 then checkout 6, do 5 loops to check all 30 boxes.
        int baseDoorNumber = 1;
        boolean isCheckIn;
        for (int i = 0; i < 10; i++) {
            isCheckIn = i % 2 == 0;
            for (int j = 0; j < 6; j++) {
                int doorNumber = baseDoorNumber + j;
                actions.add(
                        new LockerAction(isCheckIn, String.format("%02d",doorNumber)));

            }
            if(!isCheckIn)
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
                                    String data = null;
                                    try {
                                        data = new String(bytes, "ASCII");
                                        data.concat("\n");
                                        tvAppend(logText, data);
                                    } catch (UnsupportedEncodingException e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                            tvAppend(logText, "Serial Connection Opened!\n");
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
        tvAppend(logText, "\nSerial Connection Closed! \n");
    }

    private void tvAppend(TextView tv, CharSequence text) {
        final TextView ftv = tv;
        final CharSequence ftext = text;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ftv.append(ftext);
            }
        });
    }

    public void onStartClick(View view) {

    }

    public void onNextClick(View view) {

    }

    public void onStopClick(View view) {

    }

    private void sendCommand(String commandFormatter) {
        if (connection != null) {
            //trim does the magic when door number is not specified for door/empty command.
//            String command = String.format(commandFormatter, doorNumberText.getText()).trim();
//            serialPort.write(command.getBytes(Charset.forName("ASCII")));
//            tvAppend(logText, String.format("\nCommand: %s \n", command));
        }
    }
}


