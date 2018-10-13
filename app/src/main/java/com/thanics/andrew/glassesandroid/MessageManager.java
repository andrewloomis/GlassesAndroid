package com.thanics.andrew.glassesandroid;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import kotlin.text.Charsets;

public class MessageManager extends BroadcastReceiver {
    private static final String TAG = "MessageManager";

    public static UUID serviceUUID = UUID.fromString("0349f3e8-ce6e-11e8-a8d5-f2801f1b9fd1");
    public static UUID messageUUID = UUID.fromString("0349f744-ce6e-11e8-a8d5-f2801f1b9fd1");
    public static UUID clientConfigUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private class Message {
        String name;
        String body;
    }
    private Message currentMessage = new Message();

    private Listener listener;

    public byte[] getCurrentMessage()
    {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            outputStream.write(currentMessage.name.getBytes(Charsets.UTF_8));
            outputStream.write(currentMessage.body.getBytes(Charsets.UTF_8));
        }
        catch (IOException e)
        {
            Log.e("Glasses", "IOException: " + e.toString());
        }

        return outputStream.toByteArray();
    }

    public static BluetoothGattService createMessageService()
    {
        BluetoothGattService service = new BluetoothGattService(serviceUUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);
        BluetoothGattCharacteristic messageChar = new BluetoothGattCharacteristic(messageUUID,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(clientConfigUUID,
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        messageChar.addDescriptor(descriptor);
        service.addCharacteristic(messageChar);

        return service;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)) {
            String smsSender = "";
            String smsBody = "";
            for (SmsMessage smsMessage : Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                smsSender = smsMessage.getDisplayOriginatingAddress();
                smsBody += smsMessage.getMessageBody();
            }
            listener.onTextReceived(smsSender, smsBody);
        }
    }

    public static String getContactName(Context context, String number) {
        Uri uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number));
        Cursor cur = context.getContentResolver().query(
                uri,
                new String[] { ContactsContract.PhoneLookup.DISPLAY_NAME,
                        ContactsContract.PhoneLookup.NUMBER,
                        ContactsContract.PhoneLookup._ID }, null, null, null);
        String contactName = "";
        if (cur.moveToNext()) {
            int name = cur
                    .getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);

            contactName = cur.getString(name);

        }
        cur.close();
        return contactName;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    interface Listener {
        void onTextReceived(String smsSender, String text);
    }
}
