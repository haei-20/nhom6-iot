package com.xe.vaxrobot.Model;

public class TableLocation {
    private String name;
    private float x;
    private float y;

    public TableLocation(String name, float x, float y) {
        this.name = name;
        this.x = x;
        this.y = y;
    }

    public String getName() { return name; }
    public float getX() { return x; }
    public float getY() { return y; }
}