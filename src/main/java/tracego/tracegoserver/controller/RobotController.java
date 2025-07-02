package tracego.tracegoserver.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tracego.tracegoserver.service.RobotService;

import java.io.IOException;
@RestController
public class RobotController {

    @Autowired
    private RobotService robotService;

    @GetMapping("/bot/payment")
    public void getPaymentData(HttpServletResponse response) throws IOException {
        robotService.getPaymentData(response);
    }

    @GetMapping("/bot/first-set-working-list")
    public ResponseEntity<String> setWorkingList() {
        return robotService.setWorkingList();
    }

    @GetMapping("/bot/add-working-list")
    public ResponseEntity<String> addWorkingItem(@RequestParam String uid) {
        return robotService.addWorkingItem(uid);
    }

    @GetMapping("/bot/reset-working-list")
    public ResponseEntity<String> resetWorkingList() {
        return robotService.resetWorkingList();
    }

    @GetMapping("/check/working-list")
    public void checkWorkingList(@RequestParam String uid, HttpServletResponse response) throws IOException {
        robotService.checkWorkingList(uid, response);
    }

    @GetMapping("/end/working-list")
    public ResponseEntity<String> endWorkingItem(@RequestParam String uid) {
        return robotService.endWorkingItem(uid);
    }

    @GetMapping("/clear-memory-data")
    public ResponseEntity<String> clear() {
        return robotService.clearMemoryData();
    }
}