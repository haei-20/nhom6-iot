package com.xe.vaxrobot.Setting;

import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog; // [THÊM] Import này
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.xe.vaxrobot.Main.MainPresenter;
import com.xe.vaxrobot.R;
import com.xe.vaxrobot.databinding.ActivitySettingBinding;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class SettingActivity extends AppCompatActivity {
    private ActivitySettingBinding binding;

    @Inject
    public MainPresenter presenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        binding.switchShow.setChecked(presenter.isShowSeekBaGroup());
        binding.switchCompass.setChecked(presenter.isSettingCompass());

        binding.switchShow.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                presenter.setIsShowSeekBarGroup(b);
            }
        });

        binding.switchCompass.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                presenter.setSettingCompass(b);
            }
        });

        binding.back.setOnClickListener( v-> {
            finish();
        });

        // [SỬA ĐOẠN NÀY] Gọi hàm hiển thị dialog thay vì gọi trực tiếp
        binding.deleteMap.setOnClickListener(v -> {
            showResetDialog();
        });
    }

    // [THÊM MỚI] Hàm hiển thị hộp thoại nhập tên khi Reset từ cài đặt
    private void showResetDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Đặt lại vị trí gốc");
        builder.setMessage("Xe đang ở đâu? (Tọa độ sẽ về 0,0)");

        final EditText input = new EditText(this);
        input.setHint("Nhập tên (VD: Bếp, Cửa...)");
        builder.setView(input);

        builder.setPositiveButton("Xác nhận", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập tên vị trí!", Toast.LENGTH_SHORT).show();
            } else {
                // Gọi hàm resetMap với tham số tên vừa nhập
                presenter.resetMap(name);
                Toast.makeText(this, "Đã reset map tại: " + name, Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Hủy", null);
        builder.show();
    }
}