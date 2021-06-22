package me.ahoo.cosky.rest.rbac;

import java.util.Set;

/**
 * @author ahoo wang
 */
public class UserRoleBinding {

    private String userName;
    private Set<String> roleBinds;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Set<String> getRoleBinds() {
        return roleBinds;
    }

    public void setRoleBinds(Set<String> roleBinds) {
        this.roleBinds = roleBinds;
    }
}
