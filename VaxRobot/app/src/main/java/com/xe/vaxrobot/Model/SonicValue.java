package com.xe.vaxrobot.Model;

import java.io.Serializable;

public class SonicValue implements Serializable {
    int left;
    int right;
    int front;

    public SonicValue(int left, int right, int front) {
        this.left = left;
        this.right = right;
        this.front = front;
    }


    public int getLeft() {
        return left;
    }

    public void setLeft(int left) {
        this.left = left;
    }

    public int getRight() {
        return right;
    }

    public void setRight(int right) {
        this.right = right;
    }

    public int getFront() {
        return front;
    }

    public void setFront(int front) {
        this.front = front;
    }
}
