package com.example.jin.lockertest;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;


public class MainActivity extends AppCompatActivity {
    public final String ACTION_USB_PERMISSION = "com.example.jin.lockertest.USB_PERMISSION";
    UsbManager usbManager;
    UsbDeviceConnection connection;
    UsbSerialDevice serialPort;
    UsbDevice device;

    TextView doorNumberText, logText;
    Button checkInButton, checkOutButton, doorButton, emptyButton, clearButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        doorNumberText = (TextView) findViewById(R.id.doorNumberText);
        logText = (TextView) findViewById(R.id.logText);
        logText.setMovementMethod(new ScrollingMovementMethod());
        checkInButton = (Button) findViewById(R.id.checkInButton);
        checkOutButton = (Button) findViewById(R.id.checkoutButton);
        doorButton = (Button) findViewById(R.id.doorButton);
        emptyButton = (Button) findViewById(R.id.emptyButton);
        clearButton = (Button) findViewById(R.id.clearButton);

        setUiEnabled(false);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(broadcastReceiver, filter);
    }

    UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() { //Defining a Callback which triggers whenever data is read.
        @Override
        public void onReceivedData(byte[] bytes) {
            String data = null;
            try {
                data = new String(bytes, "ASCII");
                data.concat("/n");
                tvAppend(logText, data);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    };

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
                            setUiEnabled(true);
                            serialPort.setBaudRate(9600);
                            serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                            serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                            serialPort.setParity(UsbSerialInterface.PARITY_NONE);
                            serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                            serialPort.read(mCallback);
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
        setUiEnabled(false);
        serialPort.close();
        connection = null;
        device = null;
        tvAppend(logText,"\nSerial Connection Closed! \n");
    }

    public void setUiEnabled(boolean bool) {
        checkInButton.setEnabled(bool);
        checkOutButton.setEnabled(bool);
        doorButton.setEnabled(bool);
        emptyButton.setEnabled(bool);
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

    private void sendCommand(String commandFormatter){
        if(connection != null){
            //trim does the magic when door number is not specified for door/empty command.
            String command = String.format(commandFormatter, doorNumberText.getText()).trim();
            serialPort.write(command.getBytes(Charset.forName("ASCII")));
            tvAppend(logText, String.format("Command: %s \n", command));
        }
    }

    public void onCheckInClicked(View view) {
        sendCommand("O%2sT");
    }

    public void onCheckOutClicked(View view) {
        sendCommand("O%2sR");
    }

    public void onDoorClicked(View view) {
        sendCommand("D%2s");
    }

    public void onEmptyClicked(View view) {
        sendCommand("E%2s");
    }

    public void onClearClicked(View view) {
        logText.setText("");
    }
}


