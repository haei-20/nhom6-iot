package com.xe.vaxrobot.Model;

public class MarkerModel {
    public String name;
    public float x; // Tọa độ Grid
    public float y; // Tọa độ Grid

    public MarkerModel(String name, float x, float y) {
        this.name = name;
        this.x = x;
        this.y = y;
    }
}