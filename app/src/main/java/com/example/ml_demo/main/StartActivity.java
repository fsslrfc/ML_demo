package com.example.ml_demo.main;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.example.ml_demo.R;

import java.util.ArrayList;
import java.util.List;

public class StartActivity extends Activity {
    private final String ITEM_U2_NET = "主体检测：U2-Net";
    private final String ITEM_MOBILE_SAM = "主体检测：MobileSAM（还没做）";
    private final String ITEM_LAMA = "主体检测：LAMA（还没做）";
    private final String ITEM_PATCH_MATCH = "图像修复：PatchMatch（超级慢，几分钟）";
    private final String ITEM_OPENCV = "图像修复：OpenCV（快很多，一秒左右）";

    private ListView lvMenu;
    private List<String> menuTitles;
    private List<MenuItem> menuItems;
    private ArrayAdapter<String> adapter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);
        
        initViews();
        initMenuData();
        setupListView();
    }
    
    private void initViews() {
        lvMenu = findViewById(R.id.lv_menu);
    }
    
    private void initMenuData() {
        menuItems = new ArrayList<>();

        // 添加菜单项
        menuItems.add(new MenuItem(ITEM_U2_NET, com.example.ml_demo.u2net.MainActivity.class));
        menuItems.add(new MenuItem(ITEM_MOBILE_SAM, com.example.ml_demo.mobilesam.MainActivity.class));
        menuItems.add(new MenuItem(ITEM_LAMA, com.example.ml_demo.lama.MainActivity.class));
        menuItems.add(new MenuItem(ITEM_PATCH_MATCH, com.example.ml_demo.patchmatch.MainActivity.class));
        menuItems.add(new MenuItem(ITEM_OPENCV, com.example.ml_demo.opencv.MainActivity.class));

        // 创建显示用的字符串列表
        menuTitles = new ArrayList<>();
        for (MenuItem item : menuItems) {
            menuTitles.add(item.getTitle());
        }
        
        // 创建适配器
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, menuTitles);
    }
    
    private void setupListView() {
        lvMenu.setAdapter(adapter);
        // 设置点击事件
        lvMenu.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                MenuItem selectedItem = menuItems.get(position);
                if (selectedItem.getActivityClass() != null) {
                    // 跳转到对应的Activity
                    startActivity(new Intent(StartActivity.this, selectedItem.getActivityClass()));
                } else {
                    Toast.makeText(StartActivity.this,
                        "功能 \"" + selectedItem.getTitle() + "\" 正在开发中", 
                        Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
