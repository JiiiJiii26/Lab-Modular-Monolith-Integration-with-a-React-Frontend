package edu.cit.pena.inventory.dto;

public class ReservationResult {
    private boolean success;
    private String message;
    private int remainingStock;

    public ReservationResult() {
    }

    public ReservationResult(boolean success, String message, int remainingStock) {
        this.success = success;
        this.message = message;
        this.remainingStock = remainingStock;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean success;
        private String message;
        private int remainingStock;

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder remainingStock(int remainingStock) {
            this.remainingStock = remainingStock;
            return this;
        }

        public ReservationResult build() {
            return new ReservationResult(success, message, remainingStock);
        }
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getRemainingStock() {
        return remainingStock;
    }

    public void setRemainingStock(int remainingStock) {
        this.remainingStock = remainingStock;
    }
}
