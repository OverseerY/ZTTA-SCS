package xyz.yaroslav.securitycontrolsystem;

import java.util.HashMap;
import java.util.Map;

public class TagItem {
    private String uid;
    private String payload;
    private String sTime;

    public TagItem() {}

    public TagItem(String uid, String payload, String sTime) {
        this.uid = uid;
        this.payload = payload;
        this.sTime = sTime;
    }

    public String getUid() {
        return uid;
    }

    public String getPayload() {
        return payload;
    }

    public String getsTime() {
        return sTime;
    }

    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();

        result.put("tag_id", getUid());
        result.put("tag_name", getPayload());
        result.put("tag_time", getsTime());

        return result;
    }
}
