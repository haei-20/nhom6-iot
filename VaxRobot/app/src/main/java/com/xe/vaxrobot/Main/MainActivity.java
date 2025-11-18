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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
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
import com.xe.vaxrobot.Model.RobotModel;
import com.xe.vaxrobot.Model.SonicValue;
import com.xe.vaxrobot.R;
import com.xe.vaxrobot.Setting.SettingActivity;
import com.xe.vaxrobot.databinding.ActivityMainBinding;

import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

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

    // Adapter cho danh sách bàn
    private ArrayAdapter<String> tableAdapter;
    private ArrayList<String> tableNames = new ArrayList<>();

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        // Inflate using Data Binding
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // Hide status bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().getInsetsController().hide(WindowInsets.Type.statusBars());
        } else {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        // Khởi tạo presenter
        presenter.setView(this);

        // Request permissions
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
                        Toast.makeText(this, "Name: " + selected.second + " Address: " + selected.first, Toast.LENGTH_SHORT).show();
                    }
                }
        );

        setUpButton();

        // Cài đặt danh sách bàn (ListView)
        setupTableList();

        // Mặc định ở chế độ Dạy (Teaching)
        updateModeUI(false);
    }

    // --- Cài đặt ListView hiển thị hành trình ---
    private void setupTableList() {
        if (binding.tableList != null) {
            tableAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, tableNames);
            binding.tableList.setAdapter(tableAdapter);

            // Sự kiện khi click vào tên bàn -> Xe chạy lại hành trình đó
            binding.tableList.setOnItemClickListener((parent, view, position, id) -> {
                presenter.startNavigationTo(position);
            });
        }
    }

    // --- [ĐÃ SỬA] Cập nhật danh sách dùng TablePath thay vì TableLocation ---
    private void refreshTableData() {
        tableNames.clear();
        // Sử dụng MainPresenter.TablePath (Logic mới)
        for (MainPresenter.TablePath path : presenter.getSavedTables()) {
            // Tính thời gian chạy ước lượng (số lệnh * 0.05s)
            float duration = path.commands.size() * 0.05f;
            tableNames.add(path.name + " (" + String.format("%.1f", duration) + "s)");
        }
        if (tableAdapter != null) {
            tableAdapter.notifyDataSetChanged();
        }
    }

    private ArrayList<Pair<String, String>> getPairedDevices() {
        ArrayList<Pair<String, String>> res = new ArrayList<>();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH_PERMISSIONS);
            Toast.makeText(this, "Cần cấp quyền Bluetooth trước khi kết nối", Toast.LENGTH_SHORT).show();
            return res;
        }

        Set<BluetoothDevice> pairedDevices = presenter.getPairedDevice();
        if (pairedDevices == null || pairedDevices.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy thiết bị đã ghép đôi. Hãy bật Bluetooth.", Toast.LENGTH_SHORT).show();
            return res;
        }

        for (BluetoothDevice device : pairedDevices) {
            res.add(new Pair<>(device.getAddress(), device.getName()));
        }
        return res;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        presenter.disconnect();
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        android.Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                }, REQUEST_BLUETOOTH_PERMISSIONS);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Bluetooth Permissions Granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Bluetooth Permissions Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }


    @SuppressLint("ClickableViewAccessibility")
    private void setUpButton(){
        binding.center.setOnClickListener(v -> {
            binding.mapView.centerOnRobotPosition();
        });

        binding.setting.setOnClickListener( v-> {
            Intent intent = new Intent(this, SettingActivity.class);
            startActivity(intent);
        });

        // --- Xử lý chuyển chế độ Dạy/Chạy ---
        if (binding.modeSwitch != null) {
            binding.modeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                // isChecked = true -> Run Mode (Chế độ Chạy)
                // isChecked = false -> Teaching Mode (Chế độ Dạy)
                updateModeUI(isChecked);
                if (!isChecked) {
                    presenter.stopAutoMode(); // Dừng chạy tự động khi chuyển về Dạy
                } else {
                    refreshTableData(); // Cập nhật danh sách bàn khi sang chế độ Chạy
                }
            });
        }

        // --- Xử lý nút Lưu vị trí (chỉ hiện ở chế độ Dạy) ---
        if (binding.btnSave != null) {
            binding.btnSave.setOnClickListener(v -> {
                showSaveTableDialog();
            });
        }

        // Control buttons
        binding.up.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        presenter.setUp(true);
                        binding.mapView.centerOnRobotPosition();
                        setOnTouchBackground(binding.up);
                        return true;
                    case MotionEvent.ACTION_UP:
                        presenter.setUp(false);
                        binding.mapView.centerOnRobotPosition();
                        setOnNotTouchBackground(binding.up);
                        return true;
                }
                return false;
            }
        });

        binding.down.setOnTouchListener((view, motionEvent) -> {
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    presenter.setDown(true);
                    binding.mapView.centerOnRobotPosition();
                    setOnTouchBackground(binding.down);
                    return true;
                case MotionEvent.ACTION_UP:
                    presenter.setDown(false);
                    binding.mapView.centerOnRobotPosition();
                    setOnNotTouchBackground(binding.down);
                    return true;
            }
            return false;
        });

        binding.left.setOnTouchListener((view, motionEvent) -> {
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    presenter.setLeft(true);
                    binding.mapView.centerOnRobotPosition();
                    setOnTouchBackground(binding.left);
                    return true;
                case MotionEvent.ACTION_UP:
                    presenter.setLeft(false);
                    binding.mapView.centerOnRobotPosition();
                    setOnNotTouchBackground(binding.left);
                    return true;
            }
            return false;
        });

        binding.right.setOnTouchListener((view, motionEvent) -> {
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    presenter.setRight(true);
                    binding.mapView.centerOnRobotPosition();
                    setOnTouchBackground(binding.right);
                    return true;
                case MotionEvent.ACTION_UP:
                    presenter.setRight(false);
                    binding.mapView.centerOnRobotPosition();
                    setOnNotTouchBackground(binding.right);
                    return true;
            }
            return false;
        });

        // Sửa logic nút Delete: Reset bộ ghi hành trình
        binding.delete.setOnClickListener(v -> {
            // Gọi hàm resetRecordingData thay vì chỉ gửi "D"
            presenter.resetRecordingData();
            presenter.sendCommand("D"); // Vẫn gửi lệnh xuống robot để reset encoder nếu cần
        });

        binding.seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                String res = "Speed " + String.valueOf(i);
                presenter.sendCommand(res);
                binding.seekbarValue.setText(i + "");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        binding.seekbarDelta.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                String res = "Delta_speed " + String.valueOf(i-50);
                presenter.sendCommand(res);
                binding.seekbarDeltaValue.setText("" +  String.valueOf(i-50));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        binding.status.setOnClickListener(v -> {
            presenter.processOnStatusClick();
        });

        binding.buttonMusic.setOnClickListener(v -> {
            presenter.sendCommand("music");
        });

        binding.measure.setOnClickListener(v -> {
            binding.mapView.setCalculatingMode(!binding.mapView.isCalculatingMode());
            if(binding.mapView.isCalculatingMode()){
                binding.measure.setAlpha(1.f);
            }else {
                binding.measure.setAlpha(0.5f);
            }
        });
    }

    // --- Hàm hiển thị Dialog lưu hành trình ---
    private void showSaveTableDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Lưu hành trình vừa đi");
        builder.setMessage("Đặt tên (VD: Bàn 1). Hãy chắc chắn bạn đã lái xe từ điểm xuất phát đến đây.");

        final EditText input = new EditText(this);
        builder.setView(input);

        builder.setPositiveButton("Lưu", (dialog, which) -> {
            String name = input.getText().toString();
            if (!name.isEmpty()) {
                presenter.saveCurrentPosition(name);
                refreshTableData();
            } else {
                toastMessage("Tên không được để trống");
            }
        });
        builder.setNegativeButton("Hủy", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    // --- Hàm ẩn hiện UI theo chế độ ---
    private void updateModeUI(boolean isRunMode) {
        if (isRunMode) {
            // Chế độ CHẠY: Ẩn nút điều khiển, Hiện list bàn
            binding.up.setVisibility(View.GONE);
            binding.down.setVisibility(View.GONE);
            binding.left.setVisibility(View.GONE);
            binding.right.setVisibility(View.GONE);
            if(binding.btnSave != null) binding.btnSave.setVisibility(View.GONE);

            if(binding.tableList != null) binding.tableList.setVisibility(View.VISIBLE);

            toastMessage("Chuyển sang chế độ CHẠY (Auto)");
        } else {
            // Chế độ DẠY: Hiện nút điều khiển, Ẩn list bàn
            binding.up.setVisibility(View.VISIBLE);
            binding.down.setVisibility(View.VISIBLE);
            binding.left.setVisibility(View.VISIBLE);
            binding.right.setVisibility(View.VISIBLE);
            if(binding.btnSave != null) binding.btnSave.setVisibility(View.VISIBLE);

            if(binding.tableList != null) binding.tableList.setVisibility(View.GONE);

            toastMessage("Chuyển sang chế độ DẠY (Manual)");
        }
    }

    public void startPickDeviceActivity(){
        deviceList = getPairedDevices();
        Intent intent = new Intent(this, PickDeviceActivity.class);
        ArrayList<String> deviceNameList = deviceList.stream()
                .map(pair -> pair.second)
                .collect(Collectors.toCollection(ArrayList::new));

        intent.putExtra(PickDeviceActivity.EXTRA_DEVICE_LIST, deviceNameList);
        devicePickerLauncher.launch(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1233) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Bluetooth Enabled", Toast.LENGTH_SHORT).show();
                presenter.init();
            } else {
                Toast.makeText(this, "Bluetooth not enabled", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void showConnectionSuccess(String device) {
        binding.status.setImageResource(R.drawable.baseline_bluetooth_connected_24);
        binding.status.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.greenColor));
        Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showConnectionFailed() {
        binding.status.setImageResource(R.drawable.baseline_bluetooth_disabled_24);
        binding.status.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.redColor));
        Toast.makeText(this, "Connection Failed", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showDisconnected(){
        binding.status.setImageResource(R.drawable.baseline_do_not_disturb_24);
        binding.status.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.greenColor));
        Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showMessage(String message){
        binding.messages.setText("Message: \n" + message);
    }

    @Override
    public void showError(String error){
        binding.messages.setText("Error: " + error);
    }

    @Override
    public void showAlertDialog(String title, String message ){
        AlertDialog alertDialog = new AlertDialog.Builder(getApplicationContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .create();
        alertDialog.show();
    }

    private void setOnTouchBackground(ImageView img){
        img.setBackgroundColor(getResources().getColor(R.color.lightBeigeColor));
    }

    private void setOnNotTouchBackground(ImageView img){
        img.setBackgroundColor(getResources().getColor(R.color.transparentColor));
    }

    public void setVisibleSeekBarGroup(boolean isShow){
        binding.seekbarGroup.setVisibility(isShow ? View.VISIBLE : View.GONE);
    }

    public void toastMessage(String mess){
        Toast.makeText(this, mess, Toast.LENGTH_SHORT).show();
    }

    public void moveRobotCar(float distanceCm,  String action){}

    public void setRobotAngle(float angle){}

    public void processSonicValue(SonicValue sonicValue){}

    public void updateRobotModel(RobotModel r){
        binding.mapView.updateRobotModel(r);
    }

    public void resetMap(){
        binding.mapView.resetMap();
    }
}