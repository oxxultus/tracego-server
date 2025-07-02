package tracego.tracegoserver.service;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;

import java.io.IOException;

public interface RobotService {

    // 최신 결제내역(로봇용) 응답
    void getPaymentData(HttpServletResponse response) throws IOException;

    // 결제내역 기반으로 작업 리스트 생성
    ResponseEntity<String> setWorkingList();

    // 작업 리스트에 항목 추가
    ResponseEntity<String> addWorkingItem(String uid);

    // 작업 리스트 리셋 및 결제 상태 변경
    ResponseEntity<String> resetWorkingList();

    // 진열대에서 작업량 확인 (수량 반환)
    void checkWorkingList(String uid, HttpServletResponse response) throws IOException;

    // 진열대 작업 완료 처리
    ResponseEntity<String> endWorkingItem(String uid);

    // 현재 작업 리스트(메모리) 상태 콘솔 출력
    void printWorkingPaymentList();

    // 메모리 상태 초기화 (테스트용)
    ResponseEntity<String> clearMemoryData();
}
