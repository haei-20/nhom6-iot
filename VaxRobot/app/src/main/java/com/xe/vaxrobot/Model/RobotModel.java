package com.xe.vaxrobot.Model;


/*
x, y is index of robot in matrix
angle is the angle between robot and y-axis
 */
public class RobotModel {
    private float floatX;
    private float floatY;

    private float angle;

    private SonicValue sonicValue;

    private String action = "F";

    private float squareSize;
    private float distanceCm;

    public RobotModel() {
    }

    public RobotModel(float x, float y, float angle, float squareSize) {
        this.floatX =  x;
        this.floatY =  y;
        this.angle = angle;
        this.squareSize = squareSize;
        this.sonicValue = new SonicValue(0,0,0);
    }

    public float getAngle() {
        return angle;
    }

    public void setAngle(float angle) {
        this.angle = angle;
    }

    public float getXAxis(){
        return  (floatX*squareSize);
    }

    public float getYAxis(){
        return  (floatY*squareSize);
    }


    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public float getSquareSize() {
        return squareSize;
    }

    public void setSquareSize(float squareSize) {
        this.squareSize = squareSize;
    }

    public SonicValue getSonicValue() {
        return sonicValue;
    }

    public void setSonicValue(SonicValue sonicValue) {
        this.sonicValue = sonicValue;
    }

    public float getFloatX() {
        return floatX;
    }

    public void setFloatX(float floatX) {
        this.floatX = floatX;
    }

    public float getFloatY() {
        return floatY;
    }

    public void setFloatY(float floatY) {
        this.floatY = floatY;
    }

    public float getDistanceCm() {
        return distanceCm;
    }

    public void setDistanceCm(float distanceCm) {
        this.distanceCm = distanceCm;
    }
}
