package com.example.ml_demo.common;

import android.app.Activity;
import android.util.Log;

import com.example.ml_demo.R;

public class EmptyActivity extends Activity {
  private static final String TAG = "活动EmptyActivity";
  @Override
  protected void onCreate(android.os.Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_empty);
    findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    Log.d(TAG, "onCreate");
  }
}
