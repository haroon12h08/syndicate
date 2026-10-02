package com.syndicate.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The app's routes live in the browser, so any non-API path that is not a real file has to be
 * answered with the app shell and resolved client-side.
 */
@RestController
public class SpaForwardingController {

    @GetMapping({"/", "/{path:[^.]*}", "/{path:^(?!api$).*}/{sub:[^.]*}", "/{path:^(?!api$).*}/{sub}/{leaf:[^.]*}"})
    public org.springframework.web.servlet.ModelAndView forward() {
        return new org.springframework.web.servlet.ModelAndView("forward:/index.html");
    }
}
