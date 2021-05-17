package me.ahoo.govern.rest.dto;

/**
 * @author ahoo wang
 */
public class ImportResponse {
    private int total;
    private int succeeded;

    public ImportResponse() {
    }

    public ImportResponse(int total, int succeeded) {
        this.total = total;
        this.succeeded = succeeded;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getSucceeded() {
        return succeeded;
    }

    public void setSucceeded(int succeeded) {
        this.succeeded = succeeded;
    }

}
