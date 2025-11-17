package com.xe.vaxrobot.Model;

import android.util.Log;

public class MapModel {

    // Number of grid box
    public static final int numberGridBox =  1000;
    // Map size : mapShapeSize x mapShapeSize
    public static final float mapShapeSize = 100000f;
    // Size of each grid box in pixel
    public static final int squareSize = (int) mapShapeSize/numberGridBox;

    private float deltaPaint = 0.15F;
    // Initiate map
    private float[][] map = new float[numberGridBox][numberGridBox];
    // Each grid box size is 10 cm in real life
    public static final float squareSizeCm = 10;


    // Robot Model to save position -> index in map, angle
    private RobotModel robotModel;

    public MapModel() {
        robotModel = new RobotModel(
                (float) numberGridBox /2,    // 500
                (float) numberGridBox /2,        //500
                0,
                squareSize
        );
        robotModel.setSonicValue(new SonicValue(-1, -1, -1));
    }

    public void setRobotAngle(float angle){
        robotModel.setAngle(angle);
    }

    public void updateRobotModel(RobotModel newRobotModel){
        if(!newRobotModel.getAction().equals("S")) robotModel.setAction(newRobotModel.getAction());
        robotModel.setAngle(newRobotModel.getAngle());
        if(newRobotModel.getDistanceCm() != 0)moveCar(newRobotModel.getDistanceCm(), robotModel.getAction());
        processSonicValue(newRobotModel.getSonicValue());
    }

    public void moveCar(float distanceCm,  String action){
//        // TODO: Update map and set index to 1,2,3
        float[] oldPosition = new float[] { robotModel.getFloatX(), robotModel.getFloatY()};
        Log.i("fix_delta", "1.distanceCm " + distanceCm);
        Log.i("fix_delta", "2.oldPosition: " + robotModel.getFloatX() + " Y: " + robotModel.getFloatY());

        double[] newPosition = calculateNewPosition(
                robotModel.getFloatX(), robotModel.getFloatY(),
                robotModel.getAngle(), distanceCm, action);

        // TODO : cache decimal
        robotModel.setFloatX((float) newPosition[0]);
        robotModel.setFloatY((float) newPosition[1]);
        Log.i("fix_delta", "5.New float position x: " + newPosition[0] + " Y: " + newPosition[1]);

        drawLine(oldPosition[0], oldPosition[1],
                robotModel.getFloatX(), robotModel.getFloatY(),
                1);
        // call invalidate to update map view
    }

    // Call after call moveCar since FloatX and FloatY has yet re assigned
    public void processSonicValue(SonicValue sonicValue){
        robotModel.setSonicValue(sonicValue);
        // TODO: process sonic value
        // sonic left
        if(robotModel.getSonicValue().getLeft() != -1){
            float angle = (robotModel.getAngle() + 270) % 360;
            drawSpace(robotModel.getFloatX(), robotModel.getFloatY(),
                    angle, robotModel.getSonicValue().getLeft());
        }

        //sonic right
        if(robotModel.getSonicValue().getRight() != -1){
            float angle = (robotModel.getAngle() + 90) % 360;
            drawSpace(robotModel.getFloatX(), robotModel.getFloatY(),
                    angle, robotModel.getSonicValue().getRight());
        }

        // sonic front
        if(robotModel.getSonicValue().getFront() != -1){
            drawSpace(robotModel.getFloatX(), robotModel.getFloatY(),
                    robotModel.getAngle(), robotModel.getSonicValue().getFront());
        }
        // call invalidate to update map view
    }

    private void drawSpace(float x, float y,float angle, float distanceCm){
        float[] wall = calculateWallPosition(
                x, y,
                angle, distanceCm);
        // Draw space
        drawLine(x, y,
                wall[0], wall[1],2);

        // Wall
        float[] obstacle = calculateWallPosition(
                wall[0], wall[1],
                angle, squareSizeCm
        );
        // Wall + 100cm
        float[] obstacleAddHundred = calculateWallPosition(
                obstacle[0], obstacle[1],
                angle, 100
        );
        // Empty Wall + 100cm
        drawLine(obstacleAddHundred[0], obstacleAddHundred[1],
                obstacle[0], obstacle[1], 0);
    }


    public void resetMap(){
        for(int i = 0; i<map.length; i++){
            for(int j = 0; j<map[i].length; j++){
                map[i][j] = 0;
            }
        }
        robotModel = new RobotModel(
                numberGridBox/2,    // 500
                numberGridBox/2,        //500
                0,
                squareSize);
    }

    // calculate new position after move distanceCm cm to an angle
    private double[] calculateNewPosition(
            float oldX, float oldY,
            float angleDeg,
            float distanceCm,
            String action) {

        // Convert angle to radians
        double angleRad = Math.toRadians(angleDeg);

        // Calculate delta in cm
        double deltaCmX = (distanceCm * Math.sin(angleRad));
        double deltaCmY =  (distanceCm * Math.cos(angleRad));

        // Convert cm to grid index delta
        double deltaCellX =  (deltaCmX / MapModel.squareSizeCm); // 10cm per cell
        double deltaCellY = (deltaCmY / MapModel.squareSizeCm);

        Log.d("fix_delta", "3.deltaCmX: " + deltaCmX + " deltaCmY: " + deltaCmY);
        Log.d("fix_delta", "4.deltaCellX: " + deltaCellX + " deltaCellY: " + deltaCellY);

        // Y axis usually increases downward in arrays, so invert dy if needed
        double newX = 0, newY = 0;
        if(action.equals("F")){
            newX = oldX + deltaCellX;
            newY = oldY - deltaCellY; // subtract if y=0 is top of map
            Log.d("fix_delta", "4.1.newX: " + newX + " newY: " + newY);
            return new double[]{newX, newY};
        }else if(action.equals("B")){
            newX = oldX - deltaCellX;
            newY = oldY + deltaCellY; // subtract if y=0 is top of map
            Log.d("fix_delta", "4.1.newX: " + newX + " newY: " + newY);
            return new double[]{newX, newY};
        }else{
            Log.d("WHEN_ROLL", "action: " + action);
            return new double[]{oldX, oldY};
        }
    }

    // Calculating wall position base on sonic value
    private float[] calculateWallPosition(
            float x, float y,
            float angleDeg,
            float distanceCm) {
        // Convert angle to radians
        float angleRad = (float) Math.toRadians(angleDeg);

        // Calculate delta in cm
        float deltaCmX = (float) (distanceCm * Math.sin(angleRad));
        float deltaCmY = (float) (distanceCm * Math.cos(angleRad));

        // Convert cm to grid index delta
        int deltaCellX =  Math.round(deltaCmX / squareSizeCm); // 10cm per cell
        int deltaCellY =  Math.round(deltaCmY / squareSizeCm);


        // Y axis usually increases downward in arrays, so invert dy if needed
        float newX = 0, newY = 0;
        newX = x + deltaCellX;
        newY = y - deltaCellY; // subtract if y=0 is top of map
        return new float[]{newX, newY};
    }

    // drawLine is a function to mark in map value linePaint:
    // LinePaint = 1 -> robot has last run from
    // LinePaint = 2 -> space which is detect by sonic
    // LinePaint = 3 -> obstacle which is detect by sonic
    private void drawLine(float originX, float originY, float robotX, float robotY, int linePaint) {
        int x0 = (int) originX;
        int y0 = (int) originY;
        int x1 = (int) robotX;
        int y1 = (int) robotY;

        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;



        while (true) {
            if (x0 >= 0 && y0 >= 0 && x0 < map.length && y0 < map[0].length) {
                if(linePaint == 1){
                    map[x0][y0] = linePaint;
                    Log.e("VALIDATE_FLOAT", "x0: " + x0 + " y0: " + y0 + " line: " + linePaint);
                }else if(linePaint == 2){
                    if(map[x0][y0] != 1){
                        if(map[x0][y0] >= 2.0F && map[x0][y0] < 3.0F){
                            map[x0][y0] += deltaPaint;
                            if(map[x0][y0] > 3.0F) map[x0][y0] = 3.0F;
                        }else if(map[x0][y0] != 3.F ){
                            map[x0][y0] = 2.1F;
                        }
                    }
                }else if(linePaint == 0){
                  if(map[x0][y0] != 1){
                      if(map[x0][y0] > 4.1F && map[x0][y0] <= 5.0F) {
                          map[x0][y0] -= deltaPaint;
                      }else if(map[x0][y0] >= 2.0F && map[x0][y0] <3.0F){
                          map[x0][y0] -= deltaPaint;
                      }else{
//                          map[x0][y0] = 0;
                      }
                  }
                }
            }

            if (x0 == x1 && y0 == y1) break;

            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }



    /*
        GETTER and SETTER
     */
    public int getNumberGridBox() {
        return numberGridBox;
    }

    public float getMapShapeSize() {
        return mapShapeSize;
    }

    public int getSquareSize() {
        return squareSize;
    }

    public float[][] getMap() {
        return map;
    }

    public void setMap(float[][] map) {
        this.map = map;
    }

    public RobotModel getRobotModel() {
        return robotModel;
    }

    public void setRobotModel(RobotModel robotModel) {
        this.robotModel = robotModel;
    }
}
