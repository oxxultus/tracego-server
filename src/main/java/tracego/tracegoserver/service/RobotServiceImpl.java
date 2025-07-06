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
        // 작업 리스트가 비어있는 경우 (예: 아직 서버에서 데이터가 로드되지 않음)
        if (workingPaymentList == null) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT); // HTTP 204: 콘텐츠 없음
            return; // 더 이상 진행하지 않고 응답 종료
        }

        // 작업 리스트에서 UID가 일치하고 상태가 "미완"인 항목을 첫 번째로 하나만 찾음
        Optional<WorkingPaymnetListItem> match = workingPaymentList.getWorkingPaymnetListItem().stream()
                .filter(item -> item.getUid().equals(uid) && "미완".equals(item.getStatus()))
                .findFirst(); // 일치하는 항목이 없을 수도 있으므로 Optional 사용

        if (match.isEmpty()){
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            System.out.println("[서버][진열대 작업량 할당] 요청 UID: " + uid + " → 해당 작업 없음 (404 반환)");
            return;
        }

        // Jackson 라이브러리의 ObjectMapper를 이용해 JSON 객체 생성 준비
        ObjectMapper mapper = new ObjectMapper();
        byte[] jsonBytes = mapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(match.get());

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setContentLength(jsonBytes.length);
        response.getOutputStream().write(jsonBytes);

        System.out.println("[서버][진열대 작업량 할당] 요청 UID: " + uid
                + " → 작업 정보 전달 완료 (200 OK)");
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
        printWorkingPaymentList();


        try {
            String robotUrl = UriComponentsBuilder
                    .fromHttpUrl("http://oxxultus.kro.kr:8081/go")
                    .toUriString();

            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> goResponse = restTemplate.getForEntity(robotUrl, String.class);

            // ✅ /go 명령이 정상 전송된 경우에만
            if (goResponse.getStatusCode().is2xxSuccessful()) {
                System.out.println("[서버 → 로봇] /go 명령 전송 완료");

                /*
                // 비동기로 5초 후 /down-rfid 요청 (재시도 포함)
                new Thread(() -> {
                    try {
                        Thread.sleep(5000);

                        String downUrl = UriComponentsBuilder
                                .fromHttpUrl("http://oxxultus.kro.kr:8082/down-rfid")
                                .queryParam("uid", uid)
                                .toUriString();

                        RestTemplate downRestTemplate = new RestTemplate();

                        int attempt = 0;
                        int maxAttempts = 10;
                        boolean success = false;

                        while (attempt < maxAttempts) {
                            try {
                                ResponseEntity<String> downResponse = downRestTemplate.getForEntity(downUrl, String.class);

                                if (downResponse.getStatusCode().is2xxSuccessful()) {
                                    System.out.println("[서버 → 선반] /down-rfid 요청 성공 ✅ (시도 " + (attempt + 1) + "회): UID = " + uid);
                                    success = true;
                                    break;
                                } else {
                                    System.err.println("[서버 → 선반] /down-rfid 실패 (응답 코드: " + downResponse.getStatusCode() + ")");
                                }
                            } catch (Exception e) {
                                System.err.println("[서버 → 선반] /down-rfid 요청 예외: " + e.getMessage());
                            }

                            attempt++;
                            Thread.sleep(1000); // 1초 후 재시도
                        }

                        if (!success) {
                            System.err.println("[서버 → 선반] ❌ /down-rfid 요청 실패 (최대 재시도 초과): UID = " + uid);
                        }

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.err.println("RFID 요청 대기 중 인터럽트됨");
                    }
                }).start();
                */

            } else {
                System.err.println("[서버 → 로봇] /go 명령 응답 실패: HTTP " + goResponse.getStatusCode());
            }

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
