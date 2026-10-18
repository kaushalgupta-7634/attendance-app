package com.example.attendance.model;

public class UserProfileDTO {

    private Long id;
    private String name;
    private String username;
    private String email;
    private Role role;
    private String className;
    private Boolean mustChangePassword;

    public UserProfileDTO() {
    }

    public UserProfileDTO(Long id, String name, String username, String email, Role role, String className) {
        this.id = id;
        this.name = name;
        this.username = username;
        this.email = email;
        this.role = role;
        this.className = className;
        this.mustChangePassword = false;
    }

    public UserProfileDTO(Long id, String name, String username, String email, Role role, String className, Boolean mustChangePassword) {
        this.id = id;
        this.name = name;
        this.username = username;
        this.email = email;
        this.role = role;
        this.className = className;
        this.mustChangePassword = mustChangePassword;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public Boolean getMustChangePassword() {
        return Boolean.TRUE.equals(mustChangePassword);
    }

    public void setMustChangePassword(Boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword != null ? mustChangePassword : false;
    }
}
