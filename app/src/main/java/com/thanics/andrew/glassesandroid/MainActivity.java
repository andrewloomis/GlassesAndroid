package com.thanics.andrew.glassesandroid;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothManager bluetoothManager;
    private BluetoothGattServer server;
    private BluetoothDevice mDevice;

    private Button pairButton;
    private TextView statusText;

    static final int REQUEST_ENABLE_BT = 1;
    private final String TAG = "Glasses";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        pairButton = findViewById(R.id.pairButton);
        statusText = findViewById(R.id.statusText);


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

    private void startAdvertising()
    {
        BluetoothLeAdvertiser advertiser =
                bluetoothAdapter.getBluetoothLeAdvertiser();
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(true)
                .addServiceUuid(new ParcelUuid(TimeManager.serviceUUID))
                .build();

        advertiser.startAdvertising(settings, data, new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                super.onStartSuccess(settingsInEffect);

                pairButton.setText("Pairing...");
            }
        });
    }

    private void startServer()
    {
        server = bluetoothManager.openGattServer(this, new BluetoothGattServerCallback() {
            @Override
            public void onConnectionStateChange(final BluetoothDevice device, int status, int newState) {
                super.onConnectionStateChange(device, status, newState);

                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "device connected: " + device);
                    statusText.setText("Connected");
                    statusText.setTextColor(Color.GREEN);
                    pairButton.setText("Disconnect");
                    pairButton.setClickable(true);
                    pairButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            server.cancelConnection(device);
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

            @Override
            public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                    BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
                if(TimeManager.currentTimeUUID.equals(characteristic.getUuid()))
                {
                    Log.i(TAG, "reading current time");
                    server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
                            0, TimeManager.getExactTime());
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
                if (TimeManager.clientConfigUUID.equals(descriptor.getUuid())) {
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
                if(TimeManager.clientConfigUUID.equals(descriptor.getUuid())) {
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
        server.addService(TimeManager.createTimeService());
    }

    private void stopServer() {
        if (server == null) return;

        server.close();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopServer();
    }
}
