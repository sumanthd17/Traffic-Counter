package org.tensorflow.lite.examples.detection;

public class SavedRidesDataModel {
    private int id;
    private String name;
    private String videoName;
    private String logName;
    private String username;
    private String date;

    public SavedRidesDataModel(int id, String name, String videoName, String logName, String username, String date) {
        this.id = id;
        this.name = name;
        this.videoName = videoName;
        this.logName = logName;
        this.username = username;
        this.date = date;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVideoName() {
        return videoName;
    }

    public void setVideoName(String videoName) {
        this.videoName = videoName;
    }

    public String getLogName() {
        return logName;
    }

    public void setLogName(String logName) {
        this.logName = logName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }
}
