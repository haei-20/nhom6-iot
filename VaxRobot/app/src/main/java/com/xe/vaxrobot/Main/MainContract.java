package com.xe.vaxrobot.Main;

import android.bluetooth.BluetoothDevice;

import java.util.Set;

public interface MainContract {
    interface Presenter{
        void connectToDevice(String deviceAddress, String name);
        Set<BluetoothDevice> getPairedDevice();
        void sendCommand(String command);
        void setCommandSend(String commandSend);
        void disconnect();
        void init();
    }

    interface View{
        void showConnectionSuccess(String device);
        void showConnectionFailed();
        void showDisconnected();
        void showMessage(String message);
        void showError(String error);
        void showAlertDialog(String title, String message);

    }

}
