package com.thanics.andrew.glassesandroid;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.provider.Telephony;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;

import kotlin.text.Charsets;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothManager bluetoothManager;
    private BluetoothLeAdvertiser advertiser;
    private BluetoothGattCharacteristic messageChar;
    private AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);

            pairButton.setText("Pairing...");
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);

            Log.e(TAG, "Advertise Error: " + errorCode);
        }
    };

    boolean timeServiceAdded = false;

    private BluetoothGattServer server;
    private BluetoothDevice mDevice;

    private MessageManager messageManager;

    private Button pairButton;
    private TextView statusText;

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int SMS_PERMISSION_CODE = 0;
    private static final int CONTACTS_PERMISSION_CODE = 2;
    private static final int NOTIFICATION_PERMISSION_CODE = 3;
    private final String TAG = "Glasses";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        pairButton = findViewById(R.id.pairButton);
        statusText = findViewById(R.id.statusText);

        checkForPermissions();

        LocalBroadcastManager.getInstance(this).registerReceiver(onNotice, new IntentFilter("Msg"));

        messageManager = new MessageManager();
        registerReceiver(messageManager, new IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION));
        messageManager.setListener(new MessageManager.Listener() {
            @Override
            public void onTextReceived(String smsSender, String text) {
                Log.i("Glasses", "Received Message! " + MessageManager.getContactName(getApplicationContext(), smsSender) + " sent: " + text);

//                byte[] bytes = {0x01, 0x03};
//                messageChar.setValue(bytes);
                if(mDevice != null && messageChar != null)
                {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    try {
//                        outputStream.write(128);
                        outputStream.write(MessageManager.getContactName(getApplicationContext(), smsSender).getBytes(Charsets.UTF_8));
                        outputStream.write(0x1D);
                        outputStream.write(text.getBytes(Charsets.UTF_8));
                    }
                    catch (IOException e)
                    {
                        Log.e("Glasses", "IOException: " + e.toString());
                    }
                    messageChar.setValue(outputStream.toByteArray());

                    server.notifyCharacteristicChanged(mDevice, messageChar, false);
                }
                else
                {
                    Log.w(TAG, "Device not ready to receive messages");
                }
            }
        });

        bluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothAdapter.setName("Glasses");

        if(bluetoothAdapter == null || !bluetoothAdapter.isEnabled())
        {
            Intent enableBt = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBt, REQUEST_ENABLE_BT);
        }

        pairButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pairButton.setClickable(false);
                startAdvertising();
                startServer();
            }
        });
    }

    private void checkForPermissions()
    {
        if(!hasReadSmsPermission())
        {
            ActivityCompat.requestPermissions(MainActivity.this, new
                    String[]{Manifest.permission.READ_SMS,
                    Manifest.permission.RECEIVE_SMS}, SMS_PERMISSION_CODE);
        }

        if(!hasReadContactsPermission())
        {
            ActivityCompat.requestPermissions(MainActivity.this, new
                    String[]{Manifest.permission.READ_CONTACTS}, CONTACTS_PERMISSION_CODE);
        }
        if(!hasNotificationPermission())
        {
            ActivityCompat.requestPermissions(MainActivity.this, new
                    String[]{Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE},
                    NOTIFICATION_PERMISSION_CODE);
            Intent intent = new Intent(
                    "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            startActivity(intent);
        }
    }

    private boolean hasReadSmsPermission() {
        return ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(MainActivity.this,
                        Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasReadContactsPermission() {
        return ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasNotificationPermission() {
        return ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE) == PackageManager.PERMISSION_GRANTED;
    }

    private void startAdvertising()
    {
        advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(true)
                .addServiceUuid(new ParcelUuid(TimeManager.serviceUUID))
                .addServiceUuid(new ParcelUuid(MessageManager.serviceUUID))
                .build();

        Log.i(TAG, "Advertise data: " + data.toString());
        advertiser.startAdvertising(settings, data, advertiseCallback);
    }

    private void startServer()
    {
        server = bluetoothManager.openGattServer(this, new BluetoothGattServerCallback() {
            @Override
            public void onConnectionStateChange(final BluetoothDevice device, int status, int newState) {
                super.onConnectionStateChange(device, status, newState);
                if(status == BluetoothGatt.GATT_SUCCESS)
                {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.i(TAG, "device connected: " + device);
                        mDevice = device;
                        BluetoothDevice dev = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(device.getAddress());
                        server.connect(dev, false);
                        advertiser.stopAdvertising(advertiseCallback);

                        statusText.setText("Connected");
                        statusText.setTextColor(Color.GREEN);
                        pairButton.setText("Disconnect");
                        pairButton.setClickable(true);
                        pairButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                server.cancelConnection(mDevice);
                            }
                        });
                    }
                    else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.i(TAG, "device disconnected: " + device);
                        mDevice = null;
                        statusText.setText("Not Connected");
                        statusText.setTextColor(Color.RED);
                        pairButton.setText("Pair");
                        pairButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                pairButton.setClickable(false);
                                startAdvertising();
                                startServer();
                            }
                        });
                    }
                }
            }

            @Override
            public void onServiceAdded(int status, BluetoothGattService service) {
                super.onServiceAdded(status, service);
                Log.i("GattGlasses", "ServiceAdded: " + service.getUuid());
                Log.i("GattGlasses", "Characteristics: ");
                for (BluetoothGattCharacteristic x:
                     service.getCharacteristics()) {
                    Log.i("GattGlasses", x.getUuid().toString());
                }
                if(!timeServiceAdded)
                {
                    server.addService(TimeManager.createTimeService());
                    timeServiceAdded = true;
                }

            }

            @Override
            public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                    BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
                Log.i("Gatt", "Device trying to read char: " + characteristic.getUuid());
                Log.i("Gatt", "Value: " + characteristic.getValue());

                if(TimeManager.currentTimeUUID.equals(characteristic.getUuid()))
                {
                    Log.i(TAG, "reading current time");
                    server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
                            0, TimeManager.getExactTime());
                }
                else if (MessageManager.messageUUID.equals(characteristic.getUuid()))
                {
                    messageChar = characteristic;
                    Log.i(TAG, "reading message");
                    server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
                            0, messageManager.getCurrentMessage());
//                    String str = "hello hello hello hello hello hello hello hello hello hello hello hello hello hello hello hello hello hello hello hello hello12";
//                    byte[] bytes = str.getBytes(Charsets.UTF_8);
//                    server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
//                            0, bytes);
                }
                else
                {
                    Log.w(TAG, "invalid characteristic read: " + characteristic.getUuid());
                    server.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE,
                            0 , null);
                }
            }

            @Override
            public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset,
                                                BluetoothGattDescriptor descriptor) {
                super.onDescriptorReadRequest(device, requestId, offset, descriptor);
//                if (TimeManager.clientConfigUUID.equals(descriptor.getUuid())) {
                if (TimeManager.clientConfigUUID.equals(descriptor.getUuid()) ||
                        MessageManager.clientConfigUUID.equals(descriptor.getUuid())) {


                    Log.i(TAG, "config descriptor read");
                    byte[] returnValue;
                    if (mDevice == device) {
                        returnValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
                    } else {
                        returnValue = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
                    }
                    server.sendResponse(device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            returnValue);
                } else {
                    Log.w(TAG, "Unknown descriptor read request");
                    server.sendResponse(device,
                            requestId,
                            BluetoothGatt.GATT_FAILURE,
                            0,
                            null);
                }
            }

            @Override
            public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                                                 BluetoothGattDescriptor descriptor,
                                                 boolean preparedWrite, boolean responseNeeded,
                                                 int offset, byte[] value) {
                super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
//                if(TimeManager.clientConfigUUID.equals(descriptor.getUuid())) {
                if(TimeManager.clientConfigUUID.equals(descriptor.getUuid()) ||
                        MessageManager.clientConfigUUID.equals(descriptor.getUuid())) {
                    if (Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, value)) {
                        Log.d(TAG, "Subscribing device to notifications: " + device);
                        mDevice = device;
                    } else if (Arrays.equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, value)) {
                        Log.d(TAG, "Unsub from notifications: " + device);
                        mDevice = null;
                    }

                    if (responseNeeded) {
                        server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
                                0, null);
                    }
                }
                else {
                    Log.w(TAG, "Unknown descriptor write request");
                    if (responseNeeded) {
                        server.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE,
                                0, null);
                    }
                }
            }
        });
        if (server == null) {
            Log.w(TAG, "Unable to create GATT server");
            return;
        }

        server.clearServices();

        server.addService(MessageManager.createMessageService());

    }

    private void stopServer() {
        if (server == null) return;

        server.close();
    }

    private BroadcastReceiver onNotice = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            // String pack = intent.getStringExtra("package");
            String title = intent.getStringExtra("title");
            String text = intent.getStringExtra("text");
            Log.i("Glasses","Title2: " + title);
            Log.i("Glasses","Text2: " + text);
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();

        stopServer();
        unregisterReceiver(messageManager);
    }
}
