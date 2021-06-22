package me.ahoo.cosky.rest.user;

import me.ahoo.cosky.core.Consts;

/**
 * @author ahoo wang
 */
public class User {
    public static String SUPER_USER = Consts.COSKY;

    private String userName;
    private String pwd;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPwd() {
        return pwd;
    }

    public void setPwd(String pwd) {
        this.pwd = pwd;
    }

    @Override
    public String toString() {
        return "User{" +
                "userName='" + userName + '\'' +
                '}';
    }
}
