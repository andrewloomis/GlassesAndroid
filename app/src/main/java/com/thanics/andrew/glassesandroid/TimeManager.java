package com.thanics.andrew.glassesandroid;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import java.util.Calendar;
import java.util.UUID;

public class TimeManager {
    public static UUID serviceUUID = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb");
    public static UUID currentTimeUUID = UUID.fromString("00002a2b-0000-1000-8000-00805f9b34fb");
    public static UUID clientConfigUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public static BluetoothGattService createTimeService()
    {
        BluetoothGattService service = new BluetoothGattService(serviceUUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);
        BluetoothGattCharacteristic currentTime = new BluetoothGattCharacteristic(currentTimeUUID,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(clientConfigUUID,
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        currentTime.addDescriptor(descriptor);
        service.addCharacteristic(currentTime);

        return service;
    }

    public static byte[] getExactTime() {
        Calendar time = Calendar.getInstance();
        time.setTimeInMillis(System.currentTimeMillis());

        byte[] field = new byte[10];

        // Year
        int year = time.get(Calendar.YEAR);
        field[0] = (byte) (year & 0xFF);
        field[1] = (byte) ((year >> 8) & 0xFF);
        // Month
        field[2] = (byte) (time.get(Calendar.MONTH) + 1);
        // Day
        field[3] = (byte) time.get(Calendar.DATE);
        // Hours
        field[4] = (byte) time.get(Calendar.HOUR_OF_DAY);
        // Minutes
        field[5] = (byte) time.get(Calendar.MINUTE);
        // Seconds
        field[6] = (byte) time.get(Calendar.SECOND);
        // Day of Week (1-7)
        field[7] = getDayOfWeekCode(time.get(Calendar.DAY_OF_WEEK));
        // Fractions256
        field[8] = (byte) (time.get(Calendar.MILLISECOND) / 256);

        field[9] = 0;

        return field;
    }

    private static final byte DAY_UNKNOWN = 0;
    private static final byte DAY_MONDAY = 1;
    private static final byte DAY_TUESDAY = 2;
    private static final byte DAY_WEDNESDAY = 3;
    private static final byte DAY_THURSDAY = 4;
    private static final byte DAY_FRIDAY = 5;
    private static final byte DAY_SATURDAY = 6;
    private static final byte DAY_SUNDAY = 7;

    private static byte getDayOfWeekCode(int dayOfWeek) {
        switch (dayOfWeek) {
            case Calendar.MONDAY:
                return DAY_MONDAY;
            case Calendar.TUESDAY:
                return DAY_TUESDAY;
            case Calendar.WEDNESDAY:
                return DAY_WEDNESDAY;
            case Calendar.THURSDAY:
                return DAY_THURSDAY;
            case Calendar.FRIDAY:
                return DAY_FRIDAY;
            case Calendar.SATURDAY:
                return DAY_SATURDAY;
            case Calendar.SUNDAY:
                return DAY_SUNDAY;
            default:
                return DAY_UNKNOWN;
        }
    }
}
