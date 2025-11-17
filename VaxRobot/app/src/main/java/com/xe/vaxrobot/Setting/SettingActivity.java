package com.xe.vaxrobot.Setting;

import android.os.Bundle;
import android.widget.CompoundButton;

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

        binding.deleteMap.setOnClickListener(v -> {
            presenter.resetMap();

        });


    }
}