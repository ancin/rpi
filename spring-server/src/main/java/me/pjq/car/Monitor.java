package me.pjq.car;

import com.aliyun.iot.demo.iothub.SimpleClient4IOT;
import com.google.gson.Gson;
import me.pjq.Constants;
import me.pjq.Utils.Log;
import me.pjq.model.CarAction;
import me.pjq.model.MotionDetect;
import me.pjq.model.SensorStatus;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public enum Monitor {
    instance;
    private static final String TAG = "Monitor";
    final ExecutorService executorService = new ThreadPoolExecutor(2,
            4, 600, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(100), new ThreadPoolExecutor.CallerRunsPolicy());
    long lastCommandTime;
    boolean relayOn = false;

    private Monitor() {
        lastCommandTime = System.currentTimeMillis();
        Log.log(TAG, "init");
        init();
        startSensorStatusMonitor();
        startMotionDetect();
    }

    public void init() {
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    long currentTime = System.currentTimeMillis();
                    // if no command for such interval, then need power off via relay control
                    if ((currentTime - lastCommandTime) > Constants.RELAY_OFF_INTERVAL) {
                        if (relayOn) {
                            CarAction action = new CarAction();
                            CarController.instance.relay(action, "off");
                            relayOn = false;
                        }
                    } else {
                        if (!relayOn) {
                            CarAction action = new CarAction();
                            CarController.instance.relay(action, "on");
                            relayOn = true;
                        }
                    }

                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    public void onCommand() {
        lastCommandTime = System.currentTimeMillis();
    }

    // Start the SensorStatus Monitor
    public void startSensorStatusMonitor() {
        Log.log(TAG, "startSensorStatusMonitor");
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    SensorStatus sensorStatus = CarController.instance.getSensorStatus();
                    try {
                        SimpleClient4IOT.INSTANCE.sendMessage(new Gson().toJson(sensorStatus));
                    } catch (MqttException e) {
                        e.printStackTrace();
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }

                    try {
                        Thread.sleep(Constants.SENSOR_STATUS_UPDATE_INTERVAL);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void startMotionDetect() {
        Log.log(TAG, "startMotionDetect");
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                int count = 0;
                while (true) {
                    MotionDetect motionDetect = CarController.instance.motionDetect();
                    if (null != motionDetect && motionDetect.isMotion_detected()) {
                        count++;
                        Log.log(TAG, "motion detected: " + count);
                        motionDetect = CarController.instance.motionDetect();
                        while (null != motionDetect && motionDetect.isMotion_detected()) {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        });
    }
}
