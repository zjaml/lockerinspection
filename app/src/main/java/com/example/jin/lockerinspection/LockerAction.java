package com.example.jin.lockerinspection;

/**
 * Created by JZhao on 1/26/2017.
 */

public class LockerAction {
    private boolean isCheckIn;
    private String doorNumber;
    private boolean issued;
    private boolean acked;
    private boolean completed;
    private boolean wasEmpty;

    public LockerAction(boolean isCheckIn, String doorNumber) {
        this.isCheckIn = isCheckIn;
        this.doorNumber = doorNumber;
    }

    public boolean isCheckIn() {
        return isCheckIn;
    }

    public void setCheckIn(boolean checkIn) {
        isCheckIn = checkIn;
    }

    public String getDoorNumber() {
        return doorNumber;
    }

    public void setDoorNumber(String doorNumber) {
        this.doorNumber = doorNumber;
    }

    public boolean isAcked() {
        return acked;
    }

    public void setAcked(boolean acked) {
        this.acked = acked;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public boolean wasEmpty() {
        return wasEmpty;
    }

    public void setWasEmpty(boolean wasEmpty) {
        this.wasEmpty = wasEmpty;
    }

    public boolean issued() {
        return issued;
    }

    public void setIssued(boolean issued) {
        this.issued = issued;
    }
}
