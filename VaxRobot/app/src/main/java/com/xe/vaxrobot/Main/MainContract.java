package com.xe.vaxrobot.Main;

import android.bluetooth.BluetoothDevice;
import com.xe.vaxrobot.Model.MarkerModel; // [QUAN TRỌNG] Import dòng này
import com.xe.vaxrobot.Model.RobotModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public interface MainContract {
    interface Presenter{
        void connectToDevice(String deviceAddress, String name);
        Set<BluetoothDevice> getPairedDevice();
        void sendCommand(String command);
        void setCommandSend(String commandSend);
        void disconnect();
        void init();
        void processOnStatusClick();

        void setUp(boolean up);
        void setDown(boolean down);
        void setLeft(boolean left);
        void setRight(boolean right);

        void saveRoute(String endPointName);
        void startNavigation(String start, String end);
        void stopAutoMode();
        void resetToKitchen();
        ArrayList<String> getAvailablePoints();
        String getCurrentStartPoint();
    }

    interface View{
        void showConnectionSuccess(String device);
        void showConnectionFailed();
        void showDisconnected();
        void showMessage(String message);
        void showError(String error);
        void showAlertDialog(String title, String message);
        void toastMessage(String mess);

        void startPickDeviceActivity();
        void setVisibleSeekBarGroup(boolean isShow);

        void updateRobotModel(RobotModel r);

        // [QUAN TRỌNG] Thêm hàm này để sửa lỗi báo đỏ ở Activity và Presenter
        void updateMapMarkers(List<MarkerModel> markers);

        void resetMap();
    }
}