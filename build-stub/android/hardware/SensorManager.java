package android.hardware;

public abstract class SensorManager {
    public static final int SENSOR_DELAY_NORMAL = 3;
    public boolean registerListener(SensorEventListener listener, Sensor sensor, int samplingPeriodUs) { return false; }
    public boolean registerListener(SensorEventListener listener, Sensor sensor, int samplingPeriodUs, int maxReportLatencyUs) { return false; }
    public void unregisterListener(SensorEventListener listener, Sensor sensor) { }
}
