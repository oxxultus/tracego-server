package tracego.tracegoserver.service;

import tracego.tracegoserver.dto.PaymentListNumberResultMessage;
import tracego.tracegoserver.dto.ResultMessage;
import tracego.tracegoserver.entity.*;
import tracego.tracegoserver.repository.CartListRepository;
import tracego.tracegoserver.repository.PaymentItemRepository;
import tracego.tracegoserver.repository.PaymentListRepository;
import tracego.tracegoserver.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentListRepository paymentListRepository;
    private final PaymentItemRepository paymentItemRepository;
    private final CartListRepository cartListRepository;
    private final UserRepository userRepository;

    @Autowired
    public PaymentServiceImpl(PaymentListRepository paymentListRepository, PaymentItemRepository paymentItemRepository, CartListRepository cartListRepository,  UserRepository userRepository) {
        this.paymentListRepository = paymentListRepository;
        this.paymentItemRepository = paymentItemRepository;
        this.cartListRepository = cartListRepository;
        this.userRepository = userRepository;
    }

    // 결제 추가
    @Override
    public PaymentListNumberResultMessage addPayment(User user, CartList cartList) {
        // 장바구니 아이템들을 PaymentItem으로 변환하여 PaymentList에 추가
        List<CartItem> cartItems = cartList.getCartItems();
        PaymentList paymentList = new PaymentList();
        long totalAmount = 0;

        for (CartItem cartItem : cartItems) {
            PaymentItem paymentItem = new PaymentItem();
            paymentItem.setQuantity(cartItem.getQuantity());
            paymentItem.setTotalPrice(cartItem.getTotalPrice());
            paymentItem.setItem(cartItem.getItem());
            paymentList.addPaymentItems(paymentItem);

            totalAmount += cartItem.getTotalPrice(); // 총 금액 계산
        }

        // 결제 상태 설정 (예: 결제 완료 상태)
        paymentList.setPaymentStatus(PaymentStatus.PAYMENT_SUCCESS);
        paymentList.setTotalAmount(totalAmount);

        // 랜덤한 고유 번호를 생성하고, 중복된 번호가 없는지 확인
        String uniqueNumber = generateUniqueNumber();
        while (paymentListRepository.existsByUniqueNumber(uniqueNumber)) {
            uniqueNumber = generateUniqueNumber(); // 중복된 경우 새로운 번호 생성
        }
        paymentList.setUniqueNumber(uniqueNumber);

        // 사용자에게 결제 내역 추가
        user.addPaymentList(paymentList);

        // 결제 리스트 저장
        paymentListRepository.save(paymentList);


        /* TODO:
            - 결제 내역 추가가 되면 결제내역의 고유번호와 상품 정보를 아두이노 모듈로 전달.
            - 추가적인 로직은 아두이노 모듈을 관리하는 서비스에서 구현 해야 합니다.
         */

        return new PaymentListNumberResultMessage(201, "결제 리스트가 생성되었습니다.", uniqueNumber);
    }

    // 랜덤 고유 번호를 생성하는 메서드
    private String generateUniqueNumber() {
        // UUID 생성
        UUID uuid = UUID.randomUUID();
        // UUID에서 하이픈을 제거하고 반환
        return uuid.toString();
    }

    // 결제 취소
    @Override
    public ResultMessage undoPayment(User user, String paymentListUniqueNumber) {
        // 사용자의 결제 리스트에서 uniqueNumber에 해당하는 결제 내역을 찾는다.
        Optional<PaymentList> paymentListOptional = user.getPaymentLists().stream()
                .filter(paymentList -> paymentList.getUniqueNumber().equals(paymentListUniqueNumber))
                .findFirst();

        if (paymentListOptional.isEmpty()) {
            return new ResultMessage(404, "결제 내역을 찾을 수 없습니다.");
        }

        PaymentList paymentList = paymentListOptional.get();
        List<PaymentList> paymentLists = user.getPaymentLists();

        // User의 paymentLists에서 해당 결제 내역 제거
        paymentLists.removeIf(checkpaymentList -> checkpaymentList.getUniqueNumber().equals(paymentListUniqueNumber));
        paymentListRepository.delete(paymentList); // 리포지토리에서 삭제
        userRepository.save(user);

        return new ResultMessage(200, "결제 내역이 삭제되었습니다.");
    }

    // 사용자의 결제 내역 조회
    @Override
    public List<PaymentList> findPaymentsForUser(User user) {
        return paymentListRepository.findByUser(user);
    }

    // 결제 상태 업데이트
    @Override
    public PaymentListNumberResultMessage updateStatus(String paymentListUniqueNumber, PaymentStatus paymentStatus) {
        // 결제 상태 변경을 위한 PaymentList 찾기
        Optional<PaymentList> paymentListOptional = paymentListRepository.findByUniqueNumber(paymentListUniqueNumber);

        // PaymentList가 존재하지 않으면 처리할 방법 추가
        if (paymentListOptional.isEmpty()) {
            return new PaymentListNumberResultMessage(404, "결제 내역을 찾을 수 없습니다.", "고유키가 존재하지 않음");
        }

        // 결제 상태 업데이트
        PaymentList paymentList = paymentListOptional.get(); // Optional에서 실제 객체 가져오기
        paymentList.setPaymentStatus(paymentStatus); // 결제 상태 업데이트

        // 결제 리스트 저장
        paymentListRepository.save(paymentList);

        return new PaymentListNumberResultMessage(200, "결제 상태가 변경되었습니다.", paymentListUniqueNumber);
    }

    // 모든 결제 리스트 조회
    @Override
    public List<PaymentList> findAllPaymentsList() {
        return paymentListRepository.findAll();
    }
}

