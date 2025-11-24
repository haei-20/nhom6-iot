package com.xe.vaxrobot.Main;

import android.bluetooth.BluetoothDevice;
import com.xe.vaxrobot.Model.MarkerModel;
import com.xe.vaxrobot.Model.RobotModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public interface MainContract {
    interface View {
        void showConnectionSuccess(String device);
        void showConnectionFailed();
        void showDisconnected();
        void showMessage(String message);
        void showError(String error);
        void showAlertDialog(String title, String message);
        void updateRobotModel(RobotModel r);
        void updateMapMarkers(List<MarkerModel> markers);
        void resetMap();
        void setVisibleSeekBarGroup(boolean isShow);
        void toastMessage(String mess);
        void startPickDeviceActivity();
    }

    interface Presenter {
        void init();
        void connectToDevice(String deviceAddress, String name);
        Set<BluetoothDevice> getPairedDevice();
        void sendCommand(String command);
        void setCommandSend(String commandSend);
        void disconnect();
        void processOnStatusClick();
        void setUp(boolean up);
        void setDown(boolean down);
        void setLeft(boolean left);
        void setRight(boolean right);
        void setIsShowSeekBarGroup(boolean isShow);
        boolean isShowSeekBaGroup();
        void setSettingCompass(boolean settingCompass);
        boolean isSettingCompass();

        // --- CÁC HÀM MỚI & ĐÃ SỬA ---
        void resetMap(String startPointName); // Sửa: Nhận tên điểm xuất phát
        void deleteLocation(String name);     // Mới: Xóa điểm
        ArrayList<String> getSavedMarkerNames(); // Mới: Lấy danh sách điểm để hiện lên menu xóa
        // ----------------------------

        void saveRoute(String endPointName);
        void startNavigation(String start, String end);
        String getCurrentStartPoint();
        ArrayList<String> getAvailablePoints();
        void stopAutoMode();
    }
}