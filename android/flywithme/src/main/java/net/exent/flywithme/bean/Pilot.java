package net.exent.flywithme.bean;

/**
 * Created by canidae on 4/16/14.
 */
public class Pilot {
    private String name;
    private String phone;

    public Pilot(String name, String phone) {
        this.name = (name == null) ? "" : name;
        this.phone = (phone == null) ? "" : phone;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }
}

