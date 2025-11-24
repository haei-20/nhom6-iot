package com.xe.vaxrobot.Main;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.xe.vaxrobot.DevicePicking.PickDeviceActivity;
import com.xe.vaxrobot.Model.MarkerModel;
import com.xe.vaxrobot.Model.RobotModel;
import com.xe.vaxrobot.R;
import com.xe.vaxrobot.Setting.SettingActivity;
import com.xe.vaxrobot.databinding.ActivityMainBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MainActivity extends AppCompatActivity implements MainContract.View{
    private ActivityMainBinding binding;
    private ActivityResultLauncher<Intent> devicePickerLauncher;
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;

    @Inject
    public MainPresenter presenter;

    private ArrayList<Pair<String, String>> deviceList = new ArrayList<>();
    private Button btnSelectRoute;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().getInsetsController().hide(WindowInsets.Type.statusBars());
        } else {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        presenter.setView(this);
        requestBluetoothPermissions();

        devicePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Intent data = result.getData();
                        assert data != null;
                        int index = data.getIntExtra(PickDeviceActivity.EXTRA_DEVICE_LIST + "index", 0);
                        Pair<String, String> selected = deviceList.get(index);
                        presenter.connectToDevice(selected.first, selected.second);
                    }
                }
        );

        setUpButton();
        addSelectRouteButton();
        updateModeUI(false);
    }

    private void addSelectRouteButton() {
        if (binding.tableList != null) binding.tableList.setVisibility(View.GONE);
        btnSelectRoute = new Button(this);
        btnSelectRoute.setText("CHỌN HÀNH TRÌNH");
        btnSelectRoute.setTextSize(16);
        btnSelectRoute.setBackgroundColor(ContextCompat.getColor(this, R.color.teal_700));
        btnSelectRoute.setTextColor(ContextCompat.getColor(this, R.color.white));

        if (binding.leftRight != null) binding.leftRight.addView(btnSelectRoute, 0);

        btnSelectRoute.setOnClickListener(v -> showRouteSelectionDialog());
        btnSelectRoute.setVisibility(View.GONE);
    }

    private void showRouteSelectionDialog() {
        ArrayList<String> points = presenter.getAvailablePoints();
        if (points.size() < 2) {
            toastMessage("Chưa đủ điểm mốc. Hãy dạy ít nhất 1 chặng!");
            return;
        }
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);

        TextView lblFrom = new TextView(this); lblFrom.setText("Đi từ:");
        Spinner spinFrom = new Spinner(this);
        TextView lblTo = new TextView(this); lblTo.setText("Đến:");
        Spinner spinTo = new Spinner(this);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, points);
        spinFrom.setAdapter(adapter);
        spinTo.setAdapter(adapter);

        layout.addView(lblFrom); layout.addView(spinFrom);
        layout.addView(lblTo); layout.addView(spinTo);

        new AlertDialog.Builder(this)
                .setTitle("Chọn Lộ Trình")
                .setView(layout)
                .setPositiveButton("ĐI THÔI", (dialog, which) -> {
                    String from = spinFrom.getSelectedItem().toString();
                    String to = spinTo.getSelectedItem().toString();
                    if (from.equals(to)) toastMessage("Điểm đi và đến giống nhau!");
                    else presenter.startNavigation(from, to);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showSaveTableDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Lưu chặng đường");
        builder.setMessage("Bạn vừa đi từ: " + presenter.getCurrentStartPoint() + "\nĐến đâu?");
        final EditText input = new EditText(this);
        input.setHint("Nhập tên điểm đến (VD: Bàn 1)");
        builder.setView(input);

        builder.setPositiveButton("Lưu", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) presenter.saveRoute(name);
            else toastMessage("Tên không được để trống");
        });
        builder.setNegativeButton("Hủy", null);
        builder.show();
    }

    // --- [LOGIC MỚI] MENU DELETE ---
    private void showDeleteOptions() {
        String[] options = {"Đặt lại vị trí xe (Reset)", "Xóa một điểm đã lưu"};
        new AlertDialog.Builder(this)
                .setTitle("Quản lý Bản đồ")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showResetDialog();
                    else showDeleteLocationDialog();
                })
                .show();
    }

    private void showResetDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Đặt lại vị trí gốc");
        builder.setMessage("Xe đang ở đâu? (Tọa độ sẽ về 0,0)");

        final EditText input = new EditText(this);
        input.setHint("Nhập tên (VD: Bếp, Cửa...)");
        builder.setView(input);

        builder.setPositiveButton("Xác nhận", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) toastMessage("Vui lòng nhập tên vị trí!");
            else presenter.resetMap(name);
        });
        builder.setNegativeButton("Hủy", null);
        builder.show();
    }

    private void showDeleteLocationDialog() {
        ArrayList<String> markerNames = presenter.getSavedMarkerNames();
        if (markerNames.isEmpty()) {
            toastMessage("Chưa có điểm nào được lưu!");
            return;
        }
        String[] namesArray = markerNames.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("Chọn điểm cần xóa")
                .setItems(namesArray, (dialog, which) -> {
                    String selectedName = namesArray[which];
                    new AlertDialog.Builder(this)
                            .setTitle("Xác nhận xóa")
                            .setMessage("Xóa '" + selectedName + "' và các đường đi liên quan?")
                            .setPositiveButton("Xóa", (d, w) -> presenter.deleteLocation(selectedName))
                            .setNegativeButton("Hủy", null)
                            .show();
                })
                .setNegativeButton("Đóng", null)
                .show();
    }
    // ------------------------------

    private void updateModeUI(boolean isRunMode) {
        if (isRunMode) {
            binding.up.setVisibility(View.GONE);
            binding.down.setVisibility(View.GONE);
            binding.left.setVisibility(View.GONE);
            binding.right.setVisibility(View.GONE);
            if(binding.btnSave != null) binding.btnSave.setVisibility(View.GONE);
            if(binding.delete != null) binding.delete.setVisibility(View.GONE);
            if(btnSelectRoute != null) btnSelectRoute.setVisibility(View.VISIBLE);
            toastMessage("Chế độ AUTO");
        } else {
            binding.up.setVisibility(View.VISIBLE);
            binding.down.setVisibility(View.VISIBLE);
            binding.left.setVisibility(View.VISIBLE);
            binding.right.setVisibility(View.VISIBLE);
            if(binding.btnSave != null) binding.btnSave.setVisibility(View.VISIBLE);
            if(binding.delete != null) binding.delete.setVisibility(View.VISIBLE);
            if(btnSelectRoute != null) btnSelectRoute.setVisibility(View.GONE);
            toastMessage("Chế độ DẠY: Đang ở " + presenter.getCurrentStartPoint());
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setUpButton(){
        binding.center.setOnClickListener(v -> binding.mapView.centerOnRobotPosition());
        binding.setting.setOnClickListener(v -> startActivity(new Intent(this, SettingActivity.class)));
        if (binding.modeSwitch != null) {
            binding.modeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                updateModeUI(isChecked);
                if (!isChecked) presenter.stopAutoMode();
            });
        }
        if (binding.btnSave != null) binding.btnSave.setOnClickListener(v -> showSaveTableDialog());
        binding.up.setOnTouchListener((v, e) -> { if(e.getAction()==MotionEvent.ACTION_DOWN) presenter.setUp(true); else if(e.getAction()==MotionEvent.ACTION_UP) presenter.setUp(false); return true; });
        binding.down.setOnTouchListener((v, e) -> { if(e.getAction()==MotionEvent.ACTION_DOWN) presenter.setDown(true); else if(e.getAction()==MotionEvent.ACTION_UP) presenter.setDown(false); return true; });
        binding.left.setOnTouchListener((v, e) -> { if(e.getAction()==MotionEvent.ACTION_DOWN) presenter.setLeft(true); else if(e.getAction()==MotionEvent.ACTION_UP) presenter.setLeft(false); return true; });
        binding.right.setOnTouchListener((v, e) -> { if(e.getAction()==MotionEvent.ACTION_DOWN) presenter.setRight(true); else if(e.getAction()==MotionEvent.ACTION_UP) presenter.setRight(false); return true; });

        // [SỬA] Nút Delete giờ gọi Menu tùy chọn
        binding.delete.setOnClickListener(v -> showDeleteOptions());

        binding.status.setOnClickListener(v -> presenter.processOnStatusClick());
        binding.measure.setOnClickListener(v -> binding.mapView.setCalculatingMode(!binding.mapView.isCalculatingMode()));
    }

    @Override
    public void updateMapMarkers(List<MarkerModel> markers) {
        binding.mapView.setMarkers(markers);
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN}, REQUEST_BLUETOOTH_PERMISSIONS);
            }
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
    public void startPickDeviceActivity(){
        deviceList = getPairedDevices();
        Intent intent = new Intent(this, PickDeviceActivity.class);
        ArrayList<String> deviceNameList = new ArrayList<>();
        for(Pair<String, String> p : deviceList) deviceNameList.add(p.second);
        intent.putExtra(PickDeviceActivity.EXTRA_DEVICE_LIST, deviceNameList);
        devicePickerLauncher.launch(intent);
    }
    private ArrayList<Pair<String, String>> getPairedDevices() {
        ArrayList<Pair<String, String>> res = new ArrayList<>();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return res;
        Set<BluetoothDevice> pairedDevices = presenter.getPairedDevice();
        if (pairedDevices != null) for (BluetoothDevice d : pairedDevices) res.add(new Pair<>(d.getAddress(), d.getName()));
        return res;
    }
    @Override public void showConnectionSuccess(String device) { binding.status.setImageResource(R.drawable.baseline_bluetooth_connected_24); Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show(); }
    @Override public void showConnectionFailed() { binding.status.setImageResource(R.drawable.baseline_bluetooth_disabled_24); Toast.makeText(this, "Connection Failed", Toast.LENGTH_SHORT).show(); }
    @Override public void showDisconnected() { binding.status.setImageResource(R.drawable.baseline_do_not_disturb_24); Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show(); }
    @Override public void showMessage(String message) { binding.messages.setText("Message: \n" + message); }
    @Override public void showError(String error) { binding.messages.setText("Error: " + error); }
    @Override public void showAlertDialog(String title, String message) { new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show(); }
    public void updateRobotModel(RobotModel r){ binding.mapView.updateRobotModel(r); }
    public void resetMap(){ binding.mapView.resetMap(); }
    public void setVisibleSeekBarGroup(boolean isShow){ binding.seekbarGroup.setVisibility(isShow ? View.VISIBLE : View.GONE); }
    public void toastMessage(String mess){ Toast.makeText(this, mess, Toast.LENGTH_SHORT).show(); }
}