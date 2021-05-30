package me.ahoo.cosky.rest.dto;

/**
 * @author ahoo wang
 */
public class ErrorResponse {
    public static final String ERROR_CODE = "0001";
    private String code = ERROR_CODE;
    private String msg;

    public ErrorResponse(String code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public static ErrorResponse of(String code, String msg) {
        return new ErrorResponse(code, msg);
    }

    public static ErrorResponse of(String msg) {
        return new ErrorResponse(ERROR_CODE, msg);
    }
}
