package com.example.ml_demo;

import android.app.Activity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class EmptyActivity extends Activity {
  @Override
  protected void onCreate(android.os.Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    LinearLayout linearLayout = new LinearLayout(this);
    TextView textView = new TextView(this);
    textView.setText("这里什么也没有，请回吧！\n😊");
    textView.setTextSize(30);
    textView.setGravity(android.view.Gravity.CENTER);
    Button button = new Button(this);
    button.setText("回去吧");
    button.setOnClickListener(v -> finish());
    linearLayout.setOrientation(LinearLayout.VERTICAL);
    linearLayout.setGravity(android.view.Gravity.CENTER);
    linearLayout.addView(textView);
    linearLayout.addView(button);
    setContentView(linearLayout);
  }
}
