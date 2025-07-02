package tracego.tracegoserver.service;

import tracego.tracegoserver.dto.PaymentListNumberResultMessage;
import tracego.tracegoserver.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class RobotServiceImpl implements RobotService {

    @Autowired
    PaymentService paymentService;

    // 서비스 내에서 상태 관리 (싱글톤 빈으로 관리됨)
    private PaymentList paymentList;
    private WorkingPaymentList workingPaymentList;

    @Override
    public void getPaymentData(HttpServletResponse response) throws IOException {
        List<PaymentList> paymentLists = paymentService.findAllPaymentsList().stream()
                .filter(p -> p.getPaymentStatus() == PaymentStatus.PAYMENT_SUCCESS)
                .sorted(Comparator.comparing(PaymentList::getId))
                .toList();

        if (paymentLists.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        paymentList = paymentLists.getFirst();
        System.out.println("[서버][결제내역 갱신] 결제내역 갱신 완료");

        String paymentId = paymentList.getUniqueNumber();
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("paymentId", paymentId);

        for (PaymentItem item : paymentList.getPaymentItems()) {
            String name = item.getItem().getName();
            String uid = item.getItem().getUniqueValue();
            int quantity = item.getQuantity();

            ArrayNode array = mapper.createArrayNode();
            array.add(uid);
            array.add(String.valueOf(quantity));
            root.set(name, array);
        }

        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

        response.setContentType("application/json");
        response.setContentLength(jsonBytes.length);
        response.getOutputStream().write(jsonBytes);
    }

    @Override
    public ResponseEntity<String> setWorkingList() {
        if (paymentList == null) {
            System.out.println("[서버][작업 리스트 생성] paymentList가 존재하지 않습니다.");
            return ResponseEntity.status(400).body("현재 paymentList가 설정되어 있지 않습니다.");
        }

        workingPaymentList = new WorkingPaymentList(
                paymentList.getId(),
                paymentList.getUniqueNumber(),
                paymentList.getPaymentStatus(),
                paymentList.getUser(),
                new ArrayList<>()
        );

        System.out.println("[서버][작업 리스트 생성] 초기 작업 리스트 생성 완료: " + workingPaymentList.getUniqueNumber());
        return ResponseEntity.ok("초기 작업 리스트 생성 완료");
    }

    @Override
    public ResponseEntity<String> addWorkingItem(String uid) {
        if (workingPaymentList == null) {
            System.out.println("[서버][작업 목록 추가] 작업 리스트가 먼저 설정되지 않았습니다.");
            return ResponseEntity.status(400).body("작업 리스트가 먼저 설정되지 않았습니다.");
        }
        if (paymentList == null) {
            System.out.println("[서버][작업 목록 추가] paymentList가 존재하지 않습니다.");
            return ResponseEntity.status(400).body("paymentList가 존재하지 않습니다.");
        }
        boolean alreadyExists = workingPaymentList.getWorkingPaymnetListItem().stream()
                .anyMatch(item -> item.getUid().equals(uid));
        if (alreadyExists) {
            System.out.println("[서버][작업 목록 추가] 이미 해당 UID가 작업 목록에 등록되어 있습니다.");
            return ResponseEntity.status(409).body("이미 해당 UID가 작업 목록에 등록되어 있습니다.");
        }
        Optional<PaymentItem> match = paymentList.getPaymentItems().stream()
                .filter(item -> item.getItem().getUniqueValue().equals(uid))
                .findFirst();

        if (match.isEmpty()) {
            System.out.println("[서버][작업 목록 추가] 해당 UID에 해당하는 상품을 찾을 수 없습니다.");
            return ResponseEntity.status(404).body("해당 UID에 해당하는 상품을 찾을 수 없습니다.");
        }
        String itemName = match.get().getItem().getName();
        Long itemCount = (long) match.get().getQuantity();

        WorkingPaymnetListItem newItem = new WorkingPaymnetListItem(uid, itemName, itemCount);
        workingPaymentList.getWorkingPaymnetListItem().add(newItem);

        System.out.println("[서버][작업 목록 추가] 작업 항목 추가됨: " + uid);
        return ResponseEntity.ok("작업 항목이 성공적으로 추가되었습니다.");
    }

    @Override
    public ResponseEntity<String> resetWorkingList() {
        if (workingPaymentList == null) {
            return ResponseEntity.status(400).body("현재 작업 리스트가 없습니다.");
        }
        if (workingPaymentList.getPaymentStatus() != PaymentStatus.DELIVERY_PROCESSING) {
            return ResponseEntity.status(400).body("작업 상태가 DELIVERY_PROCESSING이 아닙니다.");
        }
        String uniqueNumber = workingPaymentList.getUniqueNumber();
        PaymentListNumberResultMessage result = paymentService.updateStatus(uniqueNumber, PaymentStatus.DELIVERY_COMPLETE);

        if (result.getCode() == 500) {
            return ResponseEntity.status(500).body("상태 업데이트 실패: " + result.getMessage());
        }

        workingPaymentList = null;

        System.out.println("[서버][작업목록 재설정] 결제내역 " + uniqueNumber + "의 상태를 DELIVERY_COMPLETE로 변경하고 작업 리스트를 초기화했습니다.");
        return ResponseEntity.ok("결제 상태를 완료로 변경하고 작업 리스트를 초기화했습니다.");
    }

    @Override
    public void checkWorkingList(String uid, HttpServletResponse response) throws IOException {
        if (workingPaymentList == null) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        Optional<WorkingPaymnetListItem> match = workingPaymentList.getWorkingPaymnetListItem().stream()
                .filter(item -> item.getUid().equals(uid) && "미완".equals(item.getStatus()))
                .findFirst();

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode json = mapper.createObjectNode();

        if (match.isPresent()) {
            json.put("count", match.get().getCount());
            System.out.println("[서버][진열대 작업량 할당] 요청 진열대 UID: " + uid + " 의 값 " + match.get().getCount() + " 전달 완료.");
        } else {
            json.put("count", 0);
            System.out.println("[서버][진열대 작업량 할당] 요청 진열대 UID: " + uid + " 에 해당하는 작업이 없어 수량 0 을 전달 합니다");
        }
        byte[] jsonBytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(json);
        response.setContentType("application/json");
        response.setContentLength(jsonBytes.length);
        response.getOutputStream().write(jsonBytes);
    }

    @Override
    public ResponseEntity<String> endWorkingItem(String uid) {
        if (workingPaymentList == null) {
            return ResponseEntity.status(400).body("작업 리스트가 존재하지 않습니다.");
        }

        Optional<WorkingPaymnetListItem> match = workingPaymentList.getWorkingPaymnetListItem().stream()
                .filter(item -> item.getUid().equals(uid))
                .findFirst();

        if (match.isEmpty()) {
            return ResponseEntity.status(404).body("해당 UID를 가진 항목이 없습니다.");
        }

        match.get().setStatus("완료");
        System.out.println("[서버][진열대 작업완료] UID " + uid + " 작업 상태를 '완료'로 변경했습니다.");

        // (선택) 모든 항목 완료시 추가 로직도 서비스에서 구현 가능

        printWorkingPaymentList();

        try {
            String robotUrl = UriComponentsBuilder.fromHttpUrl("http://oxxultus-bot.kro.kr:8081/go")
                    .toUriString();
            RestTemplate restTemplate = new RestTemplate();
            restTemplate.getForEntity(robotUrl, String.class);
            System.out.println("[서버 → 로봇] /go 명령 전송 완료");
        } catch (Exception e) {
            System.err.println("[서버 → 로봇] /go 명령 전송 실패: " + e.getMessage());
        }

        return ResponseEntity.ok(uid + "에 해당하는 상품이 완료 상태로 변경되었습니다.");
    }

    @Override
    public void printWorkingPaymentList() {
        if (workingPaymentList == null) {
            System.out.println("[디버그] 현재 workingPaymentList가 null입니다.");
            return;
        }
        System.out.println("=== [작업 리스트 상태 출력] ===");
        System.out.println("결제 ID: " + workingPaymentList.getId());
        System.out.println("고유 번호: " + workingPaymentList.getUniqueNumber());
        System.out.println("결제 상태: " + workingPaymentList.getPaymentStatus());
        System.out.println("사용자: " + workingPaymentList.getUser().getId());

        System.out.println("→ 작업 항목 목록:");
        for (WorkingPaymnetListItem item : workingPaymentList.getWorkingPaymnetListItem()) {
            System.out.println(" - UID: " + item.getUid() +
                    ", 이름: " + item.getName() +
                    ", 수량: " + item.getCount() +
                    ", 상태: " + item.getStatus());
        }
        System.out.println("=== [작업 리스트 끝] ===");
    }

    @Override
    public ResponseEntity<String> clearMemoryData() {
        workingPaymentList = null;
        paymentList = null;
        System.out.println("[서버][초기화] 결제내역 및 작업공간 초기화 완료");
        return ResponseEntity.status(200).body("결제내역 및 작업공간 초기화 완료");
    }
}
