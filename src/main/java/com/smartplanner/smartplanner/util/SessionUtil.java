package com.smartplanner.smartplanner.util;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class SessionUtil {
    private SessionUtil() {}

    public static Integer requireUserId(HttpSession session) {
        Object id = session.getAttribute("USER_ID");
        if (id == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        return (Integer) id;
    }
}
