package com.example.ml_demo.main;

public class MenuItem {
  private String title;
  private Class<?> activityClass;

  public MenuItem(String title, Class<?> activityClass) {
    this.title = title;
    this.activityClass = activityClass;
  }

  public String getTitle() {
    return title;
  }

  public Class<?> getActivityClass() {
    return activityClass;
  }
}
